package com.example.sj_voiceguard

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class AgreeAct : AppCompatActivity() {
    private var backPressedOnce = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agree) // terms 화면 레이아웃 설정

        // "테스트 하기" 버튼 참조
        val testButton: Button = findViewById(R.id.test_button)

        // 버튼 클릭 시 다음 화면으로 이동
        testButton.setOnClickListener {
            val intent = Intent(this, CallAndDisconnect::class.java) // 다음 액티비티로 이동 (NextActivity는 변경 가능)
            startActivity(intent)
        }
    }
    override fun onBackPressed() {
        if (backPressedOnce) {
            super.onBackPressed()
            finishAffinity() // 모든 액티비티 종료
        } else {
            backPressedOnce = true
            // 사용자에게 뒤로가기 버튼을 다시 눌러서 앱을 종료하도록 안내
            Toast.makeText(this, "뒤로가기 버튼을 한 번 더 누르면 앱이 종료됩니다.", Toast.LENGTH_SHORT).show()
            // 2초 후에 backPressedOnce 플래그를 false로 리셋
            Handler().postDelayed({ backPressedOnce = false }, 2000)
        }
    }
}