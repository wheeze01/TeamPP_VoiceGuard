package com.example.sj_voiceguard

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 스플래시 화면 레이아웃 설정
        setContentView(R.layout.activity_splash)

        // 2초 후 메인 액티비티로 이동
        val splashScreenDuration = 2000L // 2초
        window.decorView.postDelayed({
            startActivity(Intent(this, TermsActivity::class.java))
            finish() // SplashActivity 종료
        }, splashScreenDuration)
    }
}
