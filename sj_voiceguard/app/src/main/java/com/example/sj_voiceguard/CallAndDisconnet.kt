package com.example.sj_voiceguard
import android.content.Intent
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.example.sj_voiceguard.MainActivity
import com.example.sj_voiceguard.R

class CallAndDisconnect : AppCompatActivity() {

    private var ringtone: Ringtone? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.callmain)

        // 통화 버튼과 통화 종료 버튼 참조
        val answerCallButton: ImageButton? = findViewById(R.id.answer_call)
        val rejectCallButton: ImageButton? = findViewById(R.id.reject_call)

        if (answerCallButton == null || rejectCallButton == null) {
            // 버튼이 null인 경우 로그를 출력하고 종료
            android.util.Log.e("CallAndDisconnect", "Buttons not found!")
            return
        }

        // 전화 벨소리 재생
        playRingtone()

        // 통화 받기 버튼 클릭 시 다음 화면으로 이동
        answerCallButton.setOnClickListener {
            stopRingtone()
            // 다음 화면으로 이동
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        // 통화 거절 버튼 클릭 시 이전 화면으로 돌아감
        rejectCallButton.setOnClickListener {
            stopRingtone()
            finish()
        }
    }

    // 벨소리 재생 메서드
    private fun playRingtone() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
        } catch (e: Exception) {
            android.util.Log.e("CallAndDisconnect", "Failed to play ringtone: ${e.message}")
        }
    }

    // 벨소리 중지 메서드
    private fun stopRingtone() {
        ringtone?.let {
            if (it.isPlaying) {
                it.stop()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRingtone()
    }
}
