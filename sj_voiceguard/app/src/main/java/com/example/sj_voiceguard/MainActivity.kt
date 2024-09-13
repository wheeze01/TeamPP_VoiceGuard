package com.example.sj_voiceguard

import android.media.RingtoneManager
import android.os.Build
import android.os.Vibrator
import android.os.VibrationEffect
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.Ringtone
import android.os.Bundle
import android.os.VibratorManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    // 음성 인식 관련 변수
    private lateinit var speechRecognizer: SpeechRecognizer
    private var isListening = false

    // UI 요소
    private lateinit var recordButton: ImageButton
    private lateinit var speechResultText: TextView

    // AI 모델 관련 설정 (API 키와 모델 초기화)
    private val apiKey = "YOUR_API_KEY" // 실제 API 키로 교체하세요
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

    // 코루틴 관련 변수
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
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            isListening = true
            speechResultText.text = "" // 이전 결과 초기화
            recordButton.setImageResource(R.drawable.call_out) // 중지 이미지로 변경

            // getRecognizerIntent() 사용
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

        // AgreeAct로 이동
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
                matches?.get(0)?.let { result ->
                    handleFinalResult(result)
                }
                // 올바른 Intent로 다시 리스닝 시작
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
                    // 올바른 Intent로 다시 리스닝 시작
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

            // 스크롤을 항상 맨 아래로 이동
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

            // 현재 부분 결과 초기화
            currentPartialText = ""

            // 스크롤을 항상 맨 아래로 이동
            val scrollView: ScrollView = findViewById(R.id.scrollView)
            scrollView.post {
                scrollView.fullScroll(View.FOCUS_DOWN)
            }
        }
    }

    private fun startRealtimeAnalysis() {
        analysisJob = scope.launch {
            while (isActive) {
                delay(10000) // 10초마다 분석 수행
                if (accumulatedText.isNotEmpty()) {
                    val textToAnalyze = accumulatedText
                    val analysis = analyzeText(textToAnalyze)

                    withContext(Dispatchers.Main) {
                        showAnalysisResult(analysis)
                    }
                }
            }
        }
    }

    private lateinit var ringtone: Ringtone

    private fun showAnalysisResult(analysis: String) {
        alertDialog?.dismiss()

        val analysisLines = analysis.split("\n")
        val score = analysisLines[0].toIntOrNull() ?: 0

        val analysisWithoutScore = analysisLines.drop(1).joinToString("\n")

        // API 레벨에 따른 진동 처리
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        // 점수가 40점 이상일 경우 경고 알림
        if (score >= 40) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val vibrationEffect =
                    VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
                vibrator.vibrate(vibrationEffect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(500)
            }

            val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ringtone = RingtoneManager.getRingtone(applicationContext, alarmSound)
            ringtone.play()

            alertDialog = AlertDialog.Builder(this)
                .setTitle("⚠️주의")
                .setMessage("위험 점수: $score\n위험 내용: $analysisWithoutScore")
                .setPositiveButton("확인") { dialog, _ ->
                    ringtone.stop()
                    dialog.dismiss()
                }
                .setNegativeButton("통화 끊기") { _, _ ->
                    ringtone.stop()
                    stopListening()
                }
                .setCancelable(false)
                .create()

            alertDialog?.show()
        }
    }

    private suspend fun analyzeText(text: String): String {
        val newModel = GenerativeModel(
            modelName = "gemini-pro",
            apiKey = apiKey,
            generationConfig = generationConfig {
                temperature = 0.9f
                topK = 1
                topP = 1f
                maxOutputTokens = 2048
            }
        )

        val prompt = """
        [분석을 위한 기존의 프롬프트 내용]
        """
        return withContext(Dispatchers.IO) {
            try {
                val response = newModel.generateContent(prompt)
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
        if (::ringtone.isInitialized && ringtone.isPlaying) {
            ringtone.stop()
        }
    }
}
