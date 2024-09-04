package com.example.sj_voiceguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false

    private lateinit var recordButton: Button
    private lateinit var speechResultText: TextView

    private val apiKey = "api_key"
    private val model = GenerativeModel(
        modelName = "gemini-pro",
        apiKey = apiKey,
        generationConfig = generationConfig {
            temperature = 0.9f
            topK = 1
            topP = 1f
            maxOutputTokens = 2048
        }
    )

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var analysisJob: Job? = null
    private var alertDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recordButton = findViewById(R.id.recordButton)
        speechResultText = findViewById(R.id.speechResultText)

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)

        recordButton.setOnClickListener {
            toggleListening()
        }
    }

    private fun toggleListening() {
        if (isListening) {
            stopListening()
        } else {
            startListening()
        }
    }

    private fun startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            isListening = true
            speechResultText.text = "" // 녹음 시작 시 결과 초기화
            recordButton.text = "중지"
            val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR") // 한국어 설정
            }
            speechRecognizer.setRecognitionListener(createRecognitionListener())
            speechRecognizer.startListening(recognizerIntent)

            // 실시간 분석을 위한 코루틴 시작
            startRealtimeAnalysis()
        }
    }

    private fun stopListening() {
        isListening = false
        recordButton.text = "시작"
        speechRecognizer.stopListening()
        analysisJob?.cancel() // 실시간 분석 중지
        alertDialog?.dismiss() // 열려있는 AlertDialog 닫기
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { result ->
                    handleSpeechResult(result)
                }
                if (isListening) {
                    speechRecognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                matches?.get(0)?.let { result ->
                    handleSpeechResult(result)
                }
            }

            // 다른 RecognitionListener 메서드들...
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (isListening) {
                    speechRecognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH))
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    private fun handleSpeechResult(result: String) {
        runOnUiThread {
            speechResultText.text = result
        }
    }

    private fun startRealtimeAnalysis() {
        analysisJob = scope.launch {
            while (isActive) {
                delay(10000) // 10초마다 분석 수행
                val currentText = speechResultText.text.toString()
                if (currentText.isNotEmpty()) {
                    val analysis = analyzeText(currentText)
                    withContext(Dispatchers.Main) {
                        showAnalysisResult(analysis)
                    }
                }
            }
        }
    }

    private fun showAnalysisResult(analysis: String) {
        alertDialog?.dismiss() // 이전 AlertDialog가 있다면 닫기

        alertDialog = AlertDialog.Builder(this)
            .setTitle("보이스피싱 분석 결과")
            .setMessage(analysis)
            .setPositiveButton("확인") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("통화 끊기") { _, _ ->
                stopListening()
            }
            .setCancelable(false)
            .create()

        alertDialog?.show()
    }

    private suspend fun analyzeText(text: String): String {
        val prompt = """
        두 사람 간의 전화 통화 스크립트가 주어질거야. 이 통화 스크립트를 분석해서 이 대화가 보이스 피싱 대화일 가능성을 0에서 100사이의 정수로 대답해줘.

        아래의 평가기준을 정확히 이해해줘. 평가기준의 내용이 포함된 경우 가능성이 높은 거야.
        
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
        
        ==================
        대답은 점수를 1번째 줄에 넣고, 이후의 설명은 3번째 줄부터 작성해줘.
        
        1. 만약 점수가 0~40점 사이면, 점수만 1번째 줄에 넣어줘.
        2. 만약 점수가 41~70점 사이면, 점수를 1번째 줄에 넣고 3번째 줄부터 현 상황에 맞는 보이스피싱 주의 설명을 작성해줘.
        3. 만약 점수가 71~100점 사이면, 점수를 1번째 줄에 넣고 3번째 줄부터 보이스피싱으로 판단한 이유를 보고서 형식으로 3~4줄 정도 작성해줘.
        
        이제 이 아래의 대화를 판단해줘
        
        ==================
        [$text]
        """
        return withContext(Dispatchers.IO) {
            try {
                val response = model.generateContent(prompt)
                response.text ?: "분석 결과를 얻지 못했습니다."
            } catch (e: Exception) {
                "오류가 발생했습니다: ${e.message}"
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer.destroy()
        scope.cancel()
        alertDialog?.dismiss()
    }
}