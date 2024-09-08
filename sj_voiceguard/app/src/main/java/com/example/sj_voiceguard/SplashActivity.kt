package com.example.sj_voiceguard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.sj_voiceguard.TermsActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 일정 시간 이후 메인 액티비티로 이동
        val splashScreenDuration = 2000L // 2초
        android.os.Handler().postDelayed({
            startActivity(Intent(this, TermsActivity::class.java))
            finish() // SplashActivity 종료
        }, splashScreenDuration)
    }
}
