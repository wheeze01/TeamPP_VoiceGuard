package com.example.sj_voiceguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.SmsManager
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import java.util.logging.Handler

class MainActivity : AppCompatActivity() {

    // 음성 인식 관련 변수
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false

    // UI 요소
    private lateinit var recordButton: ImageButton
    //private lateinit var speechResultText: TextView

    // API 키 (실제 키로 교체하세요)
    private val chatGPTApiKey = "" // 실제 ChatGPT API 키로 교체하세요
    private val upstageApiKey = "" // 실제 Upstage API 키로 교체하세요
    private val anthropicApiKey = "" // 실제 Anthropic API 키로 교체하세요
    private val geminiApiKey = "" // 실제 Gemini API 키로 교체하세요

    // 코루틴 관련 변수
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var analysisJob: Job? = null
    private var alertDialog: AlertDialog? = null

    // 진동 및 알람 소리
    private lateinit var vibrator: Vibrator
    private lateinit var ringtone: Ringtone

    // 음성 결과를 처리하기 위한 변수들
    private var accumulatedText = "" // 누적된 텍스트
    private var currentPartialText = "" // 현재 부분 결과

    //타이머 관련 변수
    private lateinit var timerTextView: TextView
    private var timerSeconds = 0
    private var timerHandler: android.os.Handler = android.os.Handler(Looper.getMainLooper())
    private lateinit var timerRunnable: Runnable

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        timerTextView = findViewById(R.id.timerTextView)
        initializeTimer()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // UI 요소 초기화
        recordButton = findViewById(R.id.recordButton)
        //speechResultText = findViewById(R.id.speechResultText)

        // SpeechRecognizer 초기화
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        // 녹음 버튼 클릭 시 처리
        recordButton.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }

        // START_RECORDING 인텐트 확인
        if (intent.getBooleanExtra("START_RECORDING", false)) {
            startListening()
        }
    }

    private fun initializeTimer() {
        timerRunnable = object : Runnable {
            override fun run() {
                timerSeconds++
                updateTimerDisplay()
                timerHandler.postDelayed(this, 1000)
            }

            private fun updateTimerDisplay() {
                val minutes = timerSeconds / 60
                val seconds = timerSeconds % 60
                timerTextView.text = String.format("%02d:%02d", minutes, seconds)
            }
        }
    }

    private fun toggleListening() {
        if (isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    // 필요한 설정이 포함된 RecognizerIntent 생성 메서드
    private fun getRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR") // 한국어 설정
        }
    }

    // 음성 인식 시작 메서드
    private fun startListening() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 권한 요청
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                1
            )
        } else {
            isListening = true
            //speechResultText.text = "" // 이전 결과 초기화
            recordButton.setImageResource(R.drawable.call_out) // 중지 이미지로 변경

            val recognizerIntent = getRecognizerIntent()
            speechRecognizer.setRecognitionListener(createRecognitionListener())
            speechRecognizer.startListening(recognizerIntent)

            // 실시간 분석 코루틴 시작
            startRealtimeAnalysis()
        }
        isListening = true
        recordButton.setImageResource(R.drawable.call_out)

        // 타이머 시작
        timerSeconds = 0
        updateTimerDisplay()
        timerHandler.post(timerRunnable)
    }

    private fun updateTimerDisplay() {
        val minutes = timerSeconds / 60
        val seconds = timerSeconds % 60
        timerTextView.text = String.format("%02d:%02d", minutes, seconds)
    }

    // 음성 인식 중지 메서드
    private fun stopListening() {
        isListening = false
        recordButton.setImageResource(R.drawable.call_out)
        speechRecognizer.stopListening()
        speechRecognizer.cancel()
        analysisJob?.cancel()
        alertDialog?.dismiss()

        if (::ringtone.isInitialized && ringtone.isPlaying) {
            ringtone.stop()
        }

        val intent = Intent(this, AgreeAct::class.java)
        startActivity(intent)
        finish()
    }

    // RecognitionListener 생성 메서드
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches =
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { result -> handleFinalResult(result) }
                if (isListening) {
                    speechRecognizer.startListening(getRecognizerIntent())
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches =
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { result ->
                    if (result != currentPartialText) {
                        currentPartialText = result
                        handlePartialResult(result)
                    }
                }
            }

            override fun onError(error: Int) {
                if (isListening) {
                    speechRecognizer.startListening(getRecognizerIntent())
                }
            }

            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                currentPartialText = ""
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    // 부분 결과 처리 메서드
    private fun handlePartialResult(result: String) {
        runOnUiThread {
            //speechResultText.text = accumulatedText + " " + result
            //val scrollView: ScrollView = findViewById(R.id.scrollView)
//            scrollView.post {
//                scrollView.fullScroll(View.FOCUS_DOWN)
//            }
        }
    }

    // 최종 결과 처리 메서드
    private fun handleFinalResult(result: String) {
        runOnUiThread {
            accumulatedText += if (accumulatedText.isEmpty()) result else " $result"
            //speechResultText.text = accumulatedText
            currentPartialText = ""

            //val scrollView: ScrollView = findViewById(R.id.scrollView)
//            scrollView.post {
//                scrollView.fullScroll(View.FOCUS_DOWN)
//            }
        }
    }

    // 실시간 분석 코루틴
    private fun startRealtimeAnalysis() {
        analysisJob = scope.launch {
            while (isActive) {
                delay(10000) // 10초마다 분석 수행
                if (accumulatedText.isNotEmpty()) {
                    val textToAnalyze = accumulatedText
                    val prompt = """
                    두 사람 간의 전화 통화 스크립트가 주어질거야. 이 통화 스크립트를 분석해서 이 대화가 보이스 피싱 대화일 가능성을 0에서 10사이의 정수로 판단해줘. Chain of Thought 방식을 사용하여 단계별로 판단합니다. 최종 결과는 지정된 형식으로 출력하세요.

-- 보이스피싱 평가기준 --
- 기존의 대출을 상환하면 새 대출의 한도가 올라가는 이유로 입금을 유도하는 내용
- ATM기로 이동하여 돈을 입금 또는 출금할 것을 지시
- 통장 계좌나 통장을 빌려주면 임대료 또는 돈을 준다는 내용
- 1금융의 은행 대행 업무를 해 줌
- 대위 변제(subrogation reimbursement)용 계좌로 상환하라는 내용
- 원격제어 프로그램 또는 인증용 앱 다운로드 안내
- 가족 또는 지인에게 긴급한 문제의 해결을 위해 돈을 요구
- 개인정보 유출 또는 범죄 사건 연루 내용
- 계좌보호 조치 또는 범죄혐의 탈피 등 명분하에 특정 계좌로 이체 유도
- 주민 번호 열세자리를 확인, 요청, 불러달라는 내용
- 녹취를 위하여 조용한 장소로 이동하라는 내용
- 특정 인터넷 IP주소를 검색하라는 내용
- 카드나 통장을 퀵서비스나 택배로 보내라는 내용
- 대포통장과 관련된 내용
- 은행 직원에게 비밀로 하거나 거짓말을 요구
- 신뢰를 유도하는 정보 또는 긴급성을 부각하는 내용
- 자신을 검사, 경찰 직원이라고 얘기하는 내용
- 편법이나 조작으로 신용평점 또는 대출한도를 상향시키는 방법 안내
- 불확실성과 혼란을 조성하는 내용
---
유의사항:
- Chain of Thought: 판단 과정에서 통화 내용의 어떤 부분이 어떤 판단 기준에 해당하는지 구체적으로 서술하세요.
- 통화 내역 유형: 가장 확률이 높은 보이스피싱 유형을 기재하세요
- 보이스피싱 가능성: 최종 점수에 따라 높음 또는 낮음으로 표시하세요.
---
출력 형식:
1. 분석: 분석 이유를 여기에 서술합니다.
2. 통화 내역 유형: 보이스피싱 유형을 여기에 명시합니다.
3. 최종 점수: 총점
---
아래에 주어진 통화 데이터를 분석하여 위의 지침에 따라 판단하고, 결과를 무조건 지정된 형식으로 출력하세요.
---
                    [$textToAnalyze]
                    """.trimIndent()

                    Log.d("AnalysisResult", "생성된 프롬프트:\n$prompt")

                    // 모든 모델을 동시에 호출
                    val analyses = analyzeTextWithAllModels(prompt)
                    Log.d("AnalysisResult", "GPT 분석 결과 : ${analyses[0]}")
                    Log.d("AnalysisResult", "Upstage 분석 결과 : ${analyses[1]}")
                    Log.d("AnalysisResult", "Claude 분석 결과 : ${analyses[2]}")
                    Log.d("AnalysisResult", "Gemini 분석 결과 : ${analyses[3]}")

                    // 점수 추출 및 null 값 필터링
                    val scores = analyses.map { extractScoreFromAnalysis(it) }.filterNotNull()

                    // 평균 점수 계산 (성공한 API 개수로 나눔)
                    val averageScore = if (scores.isNotEmpty()) {
                        scores.sum().toDouble() / scores.size
                    } else {
                        0.0
                    }

                    // 보이스피싱 유형 추출 (점수와 함께)
                    val callTypeList = analyses.mapIndexed { index, analysis ->
                        val score = extractScoreFromAnalysis(analysis)
                        val callType = extractCallType(analysis)
                        Pair(score, callType)
                    }.filter { it.first != null }

                    // 가장 높은 점수를 가진 AI의 통화 유형 선택
                    val highestScoringCallType = callTypeList.maxByOrNull { it.first!! }?.second ?: "알 수 없음"

                    withContext(Dispatchers.Main) {
                        showAnalysisResult(analyses[0], averageScore, highestScoringCallType)
                    }
                }
            }
        }
    }

    // 모든 모델을 동시에 호출하는 함수
    private suspend fun analyzeTextWithAllModels(prompt: String): List<String?> = coroutineScope {
        val gptDeferred = async { analyzeTextWithChatGPT(prompt) }
        val upstageDeferred = async { analyzeTextWithUpstage(prompt) }
        val claudeDeferred = async { analyzeTextWithClaude(prompt) }
        val geminiDeferred = async { analyzeTextWithGemini(prompt) }

        listOf(
            gptDeferred.await(),
            upstageDeferred.await(),
            claudeDeferred.await(),
            geminiDeferred.await()
        )
    }

    // 분석 결과에서 점수 추출
    private fun extractScoreFromAnalysis(analysis: String?): Int? {
        if (analysis == null || analysis.isEmpty()) {
            return null
        }
        val regex = Regex(
            pattern = """(?:\d+\.\s*)?(?:\*\*)?(?:최종|총)\s*(?:점수|스코어)(?:\*\*)?\s*[:：]?\s*(\d+)\s*점?""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val matchResult = regex.find(analysis)
        val score = matchResult?.groupValues?.get(1)?.toIntOrNull()
        Log.d("AnalysisResult", "추출된 점수: $score, 원본 텍스트: ${matchResult?.value}")
        return score
    }

    // 통화 내역 유형을 추출하는 메서드
    private fun extractCallType(analysis: String?): String {
        if (analysis == null || analysis.isEmpty()) {
            return "알 수 없음"
        }
        val regex = Regex(
            pattern = """(?:\d+\.\s*)?(?:\*\*)?(?:통화\s*(?:내역\s*)?유형)(?:\*\*)?\s*[:：]?\s*(.+)""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val matchResult = regex.find(analysis)
        return matchResult?.groupValues?.get(1)?.trim() ?: "알 수 없음"
    }

    // 분석 결과를 보여주는 함수
    private fun showAnalysisResult(gptAnalysis: String?, averageScore: Double, callType: String) {
        alertDialog?.dismiss()
        Log.d("AnalysisResult", "평균 점수 : $averageScore")

        // 평균 점수가 7.0 이상일 때만 경고창을 표시
        if (averageScore >= 7.0) {
            // 진동 실행
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)
            }

            // 보호자에게 경고 메시지 SMS로 보내기
            sendDangerMessageToGuardians()

            // 알람 소리 실행
            val customSound = Uri.parse("android.resource://$packageName/${R.raw.beepbeep}")
            ringtone = RingtoneManager.getRingtone(applicationContext, customSound)

            // 볼륨 설정 (선택사항)
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.setStreamVolume(
                AudioManager.STREAM_ALARM, audioManager.getStreamMaxVolume(
                    AudioManager.STREAM_ALARM) / 2, 0)

            ringtone.play()

            // 통화 내역 유형 추출
            val callTypeExtracted = callType
            val formattedMessage = Html.fromHtml(
                "의심스러운 통화 발견!<br>" +
                        "이 통화는 보이스피싱일 가능성이 높습니다.<br><br>" +
                        "위험 점수: <font color='#FF0000'>${"%.2f".format(averageScore)}</font><br>" +
                        "통화 내역 유형: <font color='#FF0000'>$callTypeExtracted</font>",
                Html.FROM_HTML_MODE_LEGACY
            )

            alertDialog = AlertDialog.Builder(this)
                .setTitle(Html.fromHtml("<b>경고</b>", Html.FROM_HTML_MODE_LEGACY))
                .setIcon(R.drawable.ic_warning)
                .setMessage(formattedMessage)
                .setNegativeButton("통화 끊기") { _, _ ->
                    ringtone.stop()
                    stopListening()
                }
                .setNeutralButton("자세히 보기") { dialog, _ ->
                    dialog.dismiss()
                    ringtone.stop() // 알람 소리 중지
                    showDetailedAnalysis(gptAnalysis ?: "분석 결과 없음", averageScore, callTypeExtracted)
                }
                .setCancelable(false)
                .create()

            alertDialog?.show()
        } else {
            // 점수가 낮으면 경고창을 표시하지 않음
            Log.d("AnalysisResult", "보이스피싱 위험이 낮음. 경고창을 표시하지 않습니다.")
        }
    }

    // 자세히 보기
    private fun showDetailedAnalysis(analysis: String, averageScore: Double, callType: String) {
        val scrollView = ScrollView(this)
        val textView = TextView(this)
        textView.text = "평균 점수: ${"%.2f".format(averageScore)}\n통화 내역 유형: $callType\n\n$analysis"
        textView.setPadding(20, 20, 20, 20)
        scrollView.addView(textView)

        AlertDialog.Builder(this)
            .setTitle("상세 분석 결과")
            .setView(scrollView)
            .setNegativeButton("통화 끊기") { _, _ ->
                stopListening()
            }
            .setNeutralButton("확인", null)
            .show()
    }

    // 보호자에게 SMS로 보내는 경고창
    private fun sendDangerMessageToGuardians() {
        // SMS 권한 확인
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), 1)
        } else {
            // 권한이 허용된 경우
            val sharedPrefs = getSharedPreferences("GuardiansPrefs", Context.MODE_PRIVATE)
            val savedGuardians = sharedPrefs.getStringSet("guardians", emptySet())

            // 보호자 정보가 비어있을 경우
            if (savedGuardians.isNullOrEmpty()) {
                Toast.makeText(this, "저장된 보호자 전화번호가 없습니다.", Toast.LENGTH_SHORT).show()
                return
            }

            val smsManager = SmsManager.getDefault()

            // 보호자 정보 순회
            for (guardianInfo in savedGuardians) {
                val guardianDetails = guardianInfo.split(":") // "이름 : 전화번호" 형식으로 저장된 보호자 정보에서 나눔
                if (guardianDetails.size < 2) {
                    // 잘못된 형식의 보호자 정보 처리
                    Log.e("SMS_SEND", "잘못된 보호자 정보: $guardianInfo")
                    continue
                }

                val name = guardianDetails[0].trim() // 이름 추출
                var phone = guardianDetails[1].trim() // 전화번호 추출

                // 전화번호 형식 정리 (공백 및 대시 제거)
                phone = phone.replace(" ", "").replace("-", "")

                if (phone.isEmpty()) {
                    Log.e("SMS_SEND", "전화번호가 비어 있습니다: $guardianInfo")
                    continue
                }

                try {
                    // 메시지 내용
                    val message = "[VoiceGuard 발신] 경고: 피보호자가 보이스피싱 전화를 받고 있습니다. 즉시 확인이 필요합니다! "
                    // 메시지 전송
                    smsManager.sendTextMessage(phone, null, message, null, null)
                    Toast.makeText(this, "$name 에게 경고 메시지를 보냈습니다.", Toast.LENGTH_SHORT).show()
                    Log.d("SMS_SEND", "$name ($phone) 에게 메시지가 성공적으로 전송되었습니다.")

                } catch (e: Exception) {
                    // 전송 실패 시 처리
                    Toast.makeText(this, "$name 에게 메시지를 보내지 못했습니다.", Toast.LENGTH_SHORT).show()
                    Log.e("SMS_SEND_ERROR", "메시지 전송 실패 ($phone): ${e.message}")
                    e.printStackTrace()
                }
            }
        }
    }


    // ChatGPT API 통신 함수
    private suspend fun analyzeTextWithChatGPT(text: String): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // 타임아웃 설정
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val url = "https://api.openai.com/v1/chat/completions"

        val jsonObject = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))
            put("max_tokens", 2048)
            put("temperature", 0.9)
        }

        val body: RequestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(), jsonObject.toString()
        )

        val request: Request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $chatGPTApiKey")
            .addHeader("Content-Type", "application/json") // 추가된 부분
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseData = response.body?.string()

                    // 응답 데이터를 로그에 출력
                    Log.d("AnalysisResult", "응답 데이터:\n$responseData")

                    if (!response.isSuccessful) {
                        val errorJson = JSONObject(responseData ?: "")
                        val errorMessage = errorJson.optJSONObject("error")?.optString("message")
                            ?: "알 수 없는 오류가 발생했습니다."
                        Log.e("ChatGPT_Error", "API 요청 실패: $errorMessage")
                        return@withContext null
                    }

                    val jsonResponse = JSONObject(responseData ?: "")
                    val choicesArray = jsonResponse.optJSONArray("choices")

                    if (choicesArray == null || choicesArray.length() == 0) {
                        Log.e("ChatGPT_Error", "응답에 'choices' 필드가 없거나 비어 있습니다.")
                        return@withContext null
                    }

                    val messageContent = choicesArray.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    return@withContext messageContent
                }
            } catch (e: Exception) {
                Log.e("ChatGPT_Error", "오류가 발생했습니다: ${e.message}")
                return@withContext null
            }
        }
    }

    // Upstage API 통신 함수
    private suspend fun analyzeTextWithUpstage(text: String): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // 타임아웃 설정
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val url = "https://api.upstage.ai/v1/solar/chat/completions"
        val jsonObject = JSONObject().apply {
            put("model", "solar-1-mini-chat")
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))
            put("stream", false)
        }

        val body: RequestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(), jsonObject.toString()
        )

        val request: Request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $upstageApiKey")
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseData = response.body?.string()

                    if (!response.isSuccessful) {
                        val errorJson = JSONObject(responseData ?: "")
                        val errorMessage = errorJson.optJSONObject("error")?.optString("message")
                            ?: "Upstage API 오류 발생"
                        Log.e("Upstage_Error", "Upstage API 요청 실패: $errorMessage")
                        return@withContext null
                    }

                    val jsonResponse = JSONObject(responseData ?: "")
                    val choicesArray = jsonResponse.optJSONArray("choices")

                    if (choicesArray == null || choicesArray.length() == 0) {
                        Log.e("Upstage_Error", "Upstage 응답이 비어 있습니다.")
                        return@withContext null
                    }

                    val messageContent = choicesArray.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    return@withContext messageContent
                }
            } catch (e: Exception) {
                Log.e("Upstage_Error", "Upstage 통신 오류: ${e.message}")
                return@withContext null
            }
        }
    }

    // Claude API 통신 함수
    private suspend fun analyzeTextWithClaude(text: String): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val url = "https://api.anthropic.com/v1/messages"

        // JSON payload 생성
        val jsonPayload = JSONObject().apply {
            put("model", "claude-3-5-sonnet-20240620")
            put("max_tokens", 4000)
            put("temperature", 0.9)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", text)))
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", anthropicApiKey)
            .addHeader("content-type", "application/json")
            .addHeader("anthropic-version", "2023-06-01")
            .post(RequestBody.create("application/json".toMediaType(), jsonPayload))
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseData = response.body?.string()

                    // 응답 데이터 확인
                    println("응답 코드: ${response.code}")
                    println("응답 데이터: $responseData")

                    if (responseData.isNullOrEmpty()) {
                        Log.e("Claude_Error", "응답 데이터가 비어 있습니다.")
                        return@withContext null
                    }

                    if (!response.isSuccessful) {
                        val errorJson = JSONObject(responseData ?: "")
                        val errorMessage = errorJson.optJSONObject("error")?.optString("message")
                            ?: "API 요청 실패"
                        Log.e("Claude_Error", "API 요청 실패: $errorMessage")
                        return@withContext null
                    }

                    // 응답 데이터 파싱
                    val jsonResponse = JSONObject(responseData)
                    val contentArray = jsonResponse.optJSONArray("content")

                    // content 배열에서 텍스트 추출
                    val textContent = StringBuilder()
                    if (contentArray != null) {
                        for (i in 0 until contentArray.length()) {
                            val contentItem = contentArray.getJSONObject(i)
                            if (contentItem.optString("type") == "text") {
                                textContent.append(contentItem.optString("text"))
                            }
                        }
                    }

                    // 추출한 텍스트 반환
                    return@withContext textContent.toString()
                }
            } catch (e: Exception) {
                Log.e("Claude_Error", "오류 발생: ${e.message}")
                return@withContext null
            }
        }
    }

    // Gemini 통신 함수
    private suspend fun analyzeTextWithGemini(text: String): String? {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent?key=$geminiApiKey"

        val jsonObject = JSONObject().apply {
            put("contents", JSONArray().put(
                JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", text)
                ))
            ))
        }

        val body: RequestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(), jsonObject.toString()
        )

        val request: Request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseData = response.body?.string()

                    if (!response.isSuccessful) {
                        val errorJson = JSONObject(responseData ?: "")
                        val errorMessage = errorJson.optJSONObject("error")?.optString("message")
                            ?: "알 수 없는 오류가 발생했습니다."
                        Log.e("Gemini_Error", "Gemini API 요청 실패: $errorMessage")
                        return@withContext null
                    }

                    val jsonResponse = JSONObject(responseData ?: "")
                    val candidates = jsonResponse.optJSONArray("candidates")

                    if (candidates == null || candidates.length() == 0) {
                        Log.e("Gemini_Error", "Gemini 응답에 'candidates' 필드가 없거나 비어 있습니다.")
                        return@withContext null
                    }

                    val content = candidates.getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    return@withContext content
                }
            } catch (e: Exception) {
                Log.e("Gemini_Error", "Gemini 오류가 발생했습니다: ${e.message}")
                return@withContext null
            }
        }
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            1 -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // 권한 허용됨, 음성 인식 시작
                    startListening()
                } else {
                    // 권한 거부됨, 사용자에게 설명 및 재요청
                    AlertDialog.Builder(this)
                        .setTitle("권한 필요")
                        .setMessage("이 앱은 정상적인 작동을 위해 마이크 접근 권한이 필요합니다.")
                        .setPositiveButton("권한 허용") { _, _ ->
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.RECORD_AUDIO),
                                1
                            )
                        }
                        .setNegativeButton("취소") { dialog, _ ->
                            dialog.dismiss()
                        }
                        .setCancelable(false)
                        .create()
                        .show()
                }
            }
            else -> {
                // 다른 요청 코드 처리
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        timerHandler.removeCallbacks(timerRunnable)
        scope.cancel()
        alertDialog?.dismiss()
        if (::ringtone.isInitialized && ringtone.isPlaying) {
            ringtone.stop()
        }
    }
}
