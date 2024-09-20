package com.example.sj_voiceguard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
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

    // API 키 설
    private val chatGPTApiKey = "" // 실제 ChatGPT API 키로 교체하세요
    private val upstageApiKey = "" // 실제 Upstage API 키로 교체하세요
    private val anthropicApiKey = "" // 실제 Anthropic API 키로 교체하세요
    private val geminiApiKey = "" // Gemini AI API 키 추가

    // 코루틴 관련 변수
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var analysisJob: Job? = null
    private var alertDialog: AlertDialog? = null

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
        }  //TEST
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
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
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

        val intent = Intent(this, AgreeAct::class.java)
        startActivity(intent)
        finish()
    }

    // RecognitionListener 생성 메서드
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { result -> handleFinalResult(result) }
                if (isListening) {
                    speechRecognizer.startListening(getRecognizerIntent())
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
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

    // 음성 결과를 처리하기 위한 변수들
    private var accumulatedText = "" // 누적된 텍스트
    private var currentPartialText = "" // 현재 부분 결과

    // 부분 결과 처리 메서드
    private fun handlePartialResult(result: String) {
        runOnUiThread {
            speechResultText.text = accumulatedText + " " + result
            val scrollView: ScrollView = findViewById(R.id.scrollView)
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    } // tes

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
                """
                    Log.d("AnalysisResult", "prompt: $prompt")
                    val chatGPTAnalysis = analyzeTextWithChatGPT(prompt)
                    Log.d("AnalysisResult", "chatGPTAnalysis: $chatGPTAnalysis")
                    val upstageAnalysis = analyzeTextWithUpstage(prompt)
                    Log.d("AnalysisResult", "upstageAnalysis: $chatGPTAnalysis")
                    val claudeAnalysis = analyzeTextWithClaude(prompt)
                    Log.d("AnalysisResult", "claudeAnalysis: $chatGPTAnalysis")
                    val geminiAnalysis = analyzeTextWithGemini(prompt)
                    Log.d("AnalysisResult", "geminiAnalysis: $geminiAnalysis")

                    // 평균 점수 계산
                    val gptScore = extractScoreFromAnalysis(chatGPTAnalysis)
                    Log.d("AnalysisResult", "gptScore: $gptScore")
                    val upstageScore = extractScoreFromAnalysis(upstageAnalysis)
                    Log.d("AnalysisResult", "upstageScore: $upstageScore")
                    val claudeScore = extractScoreFromAnalysis(claudeAnalysis)
                    Log.d("AnalysisResult", "claudeScore: $claudeScore")
                    val geminiScore = extractScoreFromAnalysis(geminiAnalysis)
                    Log.d("AnalysisResult", "geminiScore: $geminiScore")
                    val averageScore = (gptScore + upstageScore + claudeScore + geminiScore) / 4.0
                    Log.d("AnalysisResult", "averageScore: $averageScore")

                    withContext(Dispatchers.Main) {
                        showAnalysisResult(chatGPTAnalysis, averageScore)
                    }
                }
            }
        }
    }

    private lateinit var vibrator: Vibrator
    private lateinit var ringtone: Ringtone

    // GPT 분석 결과만 경고창에 표시하는 메서드
    private fun showAnalysisResult(gptAnalysis: String, averageScore: Double) {
        alertDialog?.dismiss()
        Log.d("AnalysisResult", "평균 점수 : $averageScore")

        // 평균 점수가 7 이상일 때만 경고창을 표시
        if (averageScore >= 1) {
            // 진동 실행
            vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect = VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }

            // 알람 소리 실행
            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmSound)
            ringtone.play()

            alertDialog = AlertDialog.Builder(this)
                .setTitle("AI 분석 경고")
                .setMessage("경고: 보이스피싱 위험이 있습니다!\n\nGPT 분석 내용:\n$gptAnalysis")
                .setPositiveButton("확인") { dialog, _ ->
                    dialog.dismiss()
                    ringtone.stop() // 알람 소리 중지
                }
                .setCancelable(false)
                .create()

            alertDialog?.show()
        } else {
            // 점수가 낮으면 경고창을 표시하지 않음
            Log.d("AnalysisResult", "보이스피싱 위험이 낮음. 경고창을 표시하지 않습니다.")
        }
    }

    /*
    // 세 AI 모델을 사용하여 텍스트 분석
    private suspend fun analyzeText(text: String): String {


        val chatGPTAnalysis = analyzeTextWithChatGPT(prompt)
        val upstageAnalysis = analyzeTextWithUpstage(prompt)
        val claudeAnalysis = analyzeTextWithClaude(prompt)

        // 각 분석 결과에서 최종 점수 추출
        val gptScore = extractScoreFromAnalysis(chatGPTAnalysis)
        val upstageScore = extractScoreFromAnalysis(upstageAnalysis)
        val claudeScore = extractScoreFromAnalysis(claudeAnalysis)

        // 점수 평균 계산
        val averageScore = (gptScore + upstageScore + claudeScore) / 3.0

        return """
           GPT 분석 결과:
            $chatGPTAnalysis
            upstage 분석 결과:
            $upstageAnalysis
            claude 분석 결과:
            $claudeAnalysis
        """.trimIndent()
    }
    */

    // 분석 결과에서 "최종 점수: X" 형식의 점수를 추출하는 메서드
    private fun extractScoreFromAnalysis(analysis: String): Int {
        val regex = "(?:최종|총)\\s*(?:점수|스코어)\\s*:?\\s*(\\d+)".toRegex(RegexOption.IGNORE_CASE)
        val matchResult = regex.findAll(analysis).lastOrNull()
        val score = matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0
        Log.d("AnalysisResult", "추출된 점수: $score, 원본 텍스트: ${matchResult?.value}")
        return score
    }


    // ChatGPT API 통신 함수
    private suspend fun analyzeTextWithChatGPT(text: String): String {
        val client = OkHttpClient()
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
            .connectTimeout(30, TimeUnit.SECONDS) // 연결 타임아웃 설정
            .writeTimeout(30, TimeUnit.SECONDS)   // 쓰기 타임아웃 설정
            .readTimeout(30, TimeUnit.SECONDS)    // 읽기 타임아웃 설정
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

    // Gemini AI 통신 메서드 추가
    private suspend fun analyzeTextWithGemini(text: String): String {
        val client = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS) // 타임아웃 설정
            .writeTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val url = "https://api.gemini.com/v1/generative-models/completions" // Gemini API 엔드포인트

        val jsonObject = JSONObject().apply {
            put("model", "gemini-pro") // 사용하려는 모델 이름
            put("prompt", text) // 분석할 텍스트
            put("temperature", 0.9) // 설정 값
            put("max_tokens", 2048) // 최대 토큰 수
        }

        val body: RequestBody = RequestBody.create(
            "application/json; charset=utf-8".toMediaType(), jsonObject.toString()
        )

        val request: Request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $geminiApiKey") // Gemini API 키 추가
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    val responseData = response.body?.string()

                    if (!response.isSuccessful) {
                        val errorJson = JSONObject(responseData ?: "")
                        val errorMessage = errorJson.optJSONObject("error")?.optString("message")
                            ?: "Gemini AI 오류 발생"
                        return@withContext "Gemini AI 요청 실패: $errorMessage"
                    }

                    val jsonResponse = JSONObject(responseData ?: "")
                    val messageContent = jsonResponse.optString("content", "결과 없음")

                    return@withContext messageContent
                }
            } catch (e: Exception) {
                return@withContext "Gemini AI 통신 오류: ${e.message}"
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
