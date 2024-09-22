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

class MainActivity : AppCompatActivity() {

    // 음성 인식 관련 변수
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false

    // UI 요소
    private lateinit var recordButton: ImageButton
    private lateinit var speechResultText: TextView

    // API 키 (실제 키로 교체하세요)
    private val chatGPTApiKey = "" // 실제 ChatGPT API 키로 교체하세요
    private val upstageApiKey = "" // 실제 Upstage API 키로 교체하세요
    private val anthropicApiKey = "" // 실제 Anthropic API 키로 교체하세요
    private val geminiApiKey = "" // Gemini AI API 키 추가

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // UI 요소 초기화
        recordButton = findViewById(R.id.recordButton)
        speechResultText = findViewById(R.id.speechResultText)

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
            speechResultText.text = "" // 이전 결과 초기화
            recordButton.setImageResource(R.drawable.call_out) // 중지 이미지로 변경

            val recognizerIntent = getRecognizerIntent()
            speechRecognizer.setRecognitionListener(createRecognitionListener())
            speechRecognizer.startListening(recognizerIntent)

            // 실시간 분석 코루틴 시작
            startRealtimeAnalysis()
        }
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
            speechResultText.text = accumulatedText + " " + result
            val scrollView: ScrollView = findViewById(R.id.scrollView)
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    // 최종 결과 처리 메서드
    private fun handleFinalResult(result: String) {
        runOnUiThread {
            accumulatedText += if (accumulatedText.isEmpty()) result else " $result"
            speechResultText.text = accumulatedText
            currentPartialText = ""

            val scrollView: ScrollView = findViewById(R.id.scrollView)
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
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
                    주어진 통화 텍스트 데이터를 분석하여 해당 통화가 보이스피싱인지 판단하고, 보이스피싱 위험 점수를 산출하세요. 분석은 각 보이스피싱 유형별 판단 기준에 따라 이루어지며, Chain of Thought 방식을 사용하여 단계별로 판단합니다. 최종 결과는 지정된 형식으로 출력하세요.
---
분석 방법:
1. 통화 데이터 분석: 통화 내용을 자세히 읽고, 중요한 정보와 키워드를 파악합니다.
2. 보이스피싱 유형 판단: 아래에 제시된 보이스피싱 유형별 판단 기준에 따라 통화 내용이 어떤 유형에 해당하는지 판단합니다.
3. 판단 기준별 점수 부여:
 - 해당 사항 없음: 0점
 - 일부 해당하거나 애매함: 1점
 - 명확하게 해당함: 2점
4. 총점 계산: 해당 유형의 판단 기준별 점수를 합산하여 총점을 계산합니다. 최대 점수는 10점입니다.
5. 보이스피싱 가능성 판단:
 - 총점이 7점 이상이면 보이스피싱 가능성이 높음
 - 총점이 7점 미만이면 보이스피싱 가능성이 낮음
6. Chain of Thought 서술: 판단 과정과 이유를 단계별로 상세히 서술합니다.
7. 결과 출력: 최종 결과를 지정된 형식에 맞게 1번만 출력합니다.
---
보이스피싱 유형별 판단 기준:
1. 대출사기형 보이스피싱 판단 기준
 - C1: 민감한 개인정보(이름, 주소, 주민등록번호 등)를 요구했는가?
 - C2: 비정상적인 대출 조건(초저금리, 무담보 등)을 제시했는가?
 - C3: 대출 승인 전에 수수료나 비용을 요구했는가?
 - C4: 개인 계좌로 송금을 요청했는가?
 - C5: 금융기관을 사칭했는가?
2. 수사기관 사칭형 보이스피싱 판단 기준
 - C1: 경찰, 검찰 등 수사기관을 사칭했는가?
 - C2: 사용자가 범죄에 연루되었다고 주장했는가?
 - C3: 개인정보(이름, 주민등록번호 등)를 요구했는가?
 - C4: 자금 이체나 송금을 요구했는가?
 - C5: 공공기관에서 제공할 수 없는 서비스를 언급했는가?
3. 결제사칭형 보이스피싱 판단 기준
 - C1: 사용자가 결제하지 않은 상품 또는 서비스에 대한 결제 알림을 보냈는가?
 - C2: 민감한 개인정보(이름, 생년월일, 주소 등)를 확인하려고 했는가?
 - C3: 결제가 이미 진행되었거나 곧 진행될 것이라며 긴급 대응을 요구했는가?
 - C4: 사용자의 명의가 도용되었거나 해킹되었음을 암시했는가?
 - C5: 결제 취소, 신고 접수, 수사기관 관련 조치를 제안했는가?
4. 현금인출책 모집 보이스피싱 판단 기준
 - C1: 현금 전달 또는 인출을 요구했는가?
 - C2: 비정상적으로 높은 수익 또는 수당을 약속했는가?
 - C3: 민감한 계좌 정보나 비밀번호를 요구했는가?
 - C4: 사무실 출근이 아닌 유동적인 장소에서의 업무를 요구했는가?
 - C5: 법적으로 의심스러운 자금 이동(도박, 불법 거래 등)과 관련된 업무를 제안했는가?
5. 대포통장 모집 보이스피싱 판단 기준
 - C1: 의심스러운 환전, 현금 이체, 또는 계좌 관리를 제안했는가?
 - C2: 비정상적인 고액 수익 또는 보상을 약속했는가?
 - C3: 민감한 개인정보(계좌 정보, 비밀번호 등)를 요구했는가?
 - C4: 본인 소유 계좌 또는 카드 사용을 요청했는가?
 - C5: 불법 행위 또는 금융권 관련 위험한 제안을 했는가?
---
출력 형식:
1. 분석: 분석 이유를 여기에 서술합니다.
2. 통화 내역 유형: 보이스피싱 유형을 여기에 명시합니다.
3. 판단 기준별 점수:
 - C1: 점수
 - C2: 점수
 - C3: 점수
 - C4: 점수
 - C5: 점수
4. 최종 점수: 총점
---
유의사항:
- Chain of Thought: 판단 과정에서 통화 내용의 어떤 부분이 어떤 판단 기준에 해당하는지 구체적으로 서술하세요.
- 통화 내역 유형: 가장 높은 점수를 받은 보이스피싱 유형을 기재하세요. 만약 동일한 점수를 받은 유형이 여러 개일 경우, 모두 명시하세요.
- 점수 부여: 각 판단 기준에 대해 0점, 1점, 2점 중 하나의 점수를 부여하세요.
- 최종 점수 계산: 판단 기준별 점수를 모두 합산하여 최종 점수를 계산하세요.
- 보이스피싱 가능성: 최종 점수에 따라 높음 또는 낮음으로 표시하세요.
---
예시:
통화 내용: 검찰입니다. 고객님이 연루된 사건이 있어 확인이 필요합니다. 안전한 계좌로 자금을 이동해 주시기 바랍니다.
1. 분석: 수사기관 사칭형 보이스피싱으로 판단됩니다.
2. 판단 기준별 점수 부여:
 - C1: 2점
 - C2: 2점
 - C3: 1점
 - C4: 2점
 - C5: 0점
3. 최종 점수: 7점
---
아래에 주어진 통화 데이터를 분석하여 위의 지침에 따라 판단하고, 결과를 지정된 형식으로 출력하세요.
---
                    [$textToAnalyze]
                    """.trimIndent()

                    // 모든 모델을 동시에 호출
                    val (chatGPTAnalysis, upstageAnalysis, claudeAnalysis, geminiAnalysis) = analyzeTextWithAllModels(prompt)
                    Log.d("AnalysisResult", "GPT 분석 결과 : $chatGPTAnalysis")
                    Log.d("AnalysisResult", "Upstage 분석 결과 : $upstageAnalysis")
                    Log.d("AnalysisResult", "Claude 분석 결과 : $claudeAnalysis")
                    Log.d("AnalysisResult", "Gemini 분석 결과 : $geminiAnalysis")

                    // 점수 추출
                    val gptScore = extractScoreFromAnalysis(chatGPTAnalysis)
                    val upstageScore = extractScoreFromAnalysis(upstageAnalysis)
                    val claudeScore = extractScoreFromAnalysis(claudeAnalysis)
                    val geminiScore = extractScoreFromAnalysis(geminiAnalysis)

                    // 보이스피싱 유형 추출
                    val gptCallType = extractCallType(chatGPTAnalysis)
                    val upstageCallType = extractCallType(chatGPTAnalysis)
                    val claudeCallType = extractCallType(chatGPTAnalysis)
                    val geminiCallType = extractCallType(geminiAnalysis)

                    val averageScore = (gptScore + upstageScore + claudeScore + geminiScore) / 4.0

                    // 가장 높은 점수를 가진 AI의 통화 유형 선택
                    val highestScoringCallType = listOf(
                        Pair(gptScore, gptCallType),
                        Pair(upstageScore, upstageCallType),
                        Pair(claudeScore, claudeCallType),
                        Pair(geminiScore, geminiCallType)
                    ).maxByOrNull { it.first }?.second ?: "알 수 없음"

                    withContext(Dispatchers.Main) {
                        showAnalysisResult(chatGPTAnalysis, averageScore, highestScoringCallType)
                    }
                }
            }
        }
    }

    // 모든 모델을 동시에 호출하는 함수
    private suspend fun analyzeTextWithAllModels(prompt: String): List<String> = coroutineScope {
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
    private fun extractScoreFromAnalysis(analysis: String): Int {
        val regex = Regex(
            pattern = """(?:\d+\.\s*)?(?:\*\*)?(?:최종|총)\s*(?:점수|스코어)(?:\*\*)?\s*[:：]?\s*(\d+)\s*점?""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val matchResult = regex.find(analysis)
        val score = matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0
        Log.d("AnalysisResult", "추출된 점수: $score, 원본 텍스트: ${matchResult?.value}")
        return score
    }

    // 통화 내역 유형을 추출하는 메서드
    private fun extractCallType(analysis: String): String {
        val regex = Regex(
            pattern = """(?:\d+\.\s*)?(?:\*\*)?(?:통화\s*(?:내역\s*)?유형)(?:\*\*)?\s*[:：]?\s*(.+)""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
        val matchResult = regex.find(analysis)
        return matchResult?.groupValues?.get(1)?.trim() ?: "알 수 없음"
    }

    // 분석 결과를 보여주는 함수
    private fun showAnalysisResult(gptAnalysis: String, averageScore: Double, callType: String) {
        alertDialog?.dismiss()
        Log.d("AnalysisResult", "평균 점수 : $averageScore")

        // 평균 점수가 7 이상일 때만 경고창을 표시
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
            val callType = extractCallType(gptAnalysis)
            val formattedMessage = Html.fromHtml(
                "의심스러운 통화 발견!<br>" +
                        "이 통화는 보이스피싱일 가능성이 높습니다.<br><br>" +
                        "위험 점수: <font color='#FF0000'>${"%.2f".format(averageScore)}</font><br>" +
                        "통화 내역 유형: <font color='#FF0000'>$callType</font>",
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
                    showDetailedAnalysis(gptAnalysis, averageScore, callType)
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
    private suspend fun analyzeTextWithChatGPT(text: String): String {
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
                        return@withContext "API 요청 실패: $errorMessage"
                    }

                    val jsonResponse = JSONObject(responseData ?: "")
                    val choicesArray = jsonResponse.optJSONArray("choices")

                    if (choicesArray == null || choicesArray.length() == 0) {
                        return@withContext "응답에 'choices' 필드가 없거나 비어 있습니다."
                    }

                    val messageContent = choicesArray.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    return@withContext messageContent
                }
            } catch (e: Exception) {
                return@withContext "오류가 발생했습니다: ${e.message}"
            }
        }
    }

    // Upstage API 통신 함수
    private suspend fun analyzeTextWithUpstage(text: String): String {
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
                        return@withContext "Upstage API 요청 실패: $errorMessage"
                    }

                    val jsonResponse = JSONObject(responseData ?: "")
                    val choicesArray = jsonResponse.optJSONArray("choices")

                    if (choicesArray == null || choicesArray.length() == 0) {
                        return@withContext "Upstage 응답이 비어 있습니다."
                    }

                    val messageContent = choicesArray.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")

                    return@withContext messageContent
                }
            } catch (e: Exception) {
                return@withContext "Upstage 통신 오류: ${e.message}"
            }
        }
    }

    // Claude API 통신 함수
    private suspend fun analyzeTextWithClaude(text: String): String {
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
                        return@withContext "응답 데이터가 비어 있습니다."
                    }

                    if (!response.isSuccessful) {
                        val errorJson = JSONObject(responseData ?: "")
                        val errorMessage = errorJson.optJSONObject("error")?.optString("message")
                            ?: "API 요청 실패"
                        return@withContext "API 요청 실패: $errorMessage"
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
                return@withContext "오류 발생: ${e.message}"
            }
        }
    }

    //gemini 통신 함수 추가
    private suspend fun analyzeTextWithGemini(text: String): String {
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
                        return@withContext "Gemini API 요청 실패: $errorMessage"
                    }

                    val jsonResponse = JSONObject(responseData ?: "")
                    val candidates = jsonResponse.optJSONArray("candidates")

                    if (candidates == null || candidates.length() == 0) {
                        return@withContext "Gemini 응답에 'candidates' 필드가 없거나 비어 있습니다."
                    }

                    val content = candidates.getJSONObject(0)
                        .getJSONObject("content")
                        .getJSONArray("parts")
                        .getJSONObject(0)
                        .getString("text")

                    return@withContext content
                }
            } catch (e: Exception) {
                return@withContext "Gemini 오류가 발생했습니다: ${e.message}"
            }
        }
    }

    // 권한 요청 결과 처리
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults) // 추가된 부분

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
        scope.cancel()
        alertDialog?.dismiss()
        if (::ringtone.isInitialized && ringtone.isPlaying) {
            ringtone.stop()
        }
    }
}
