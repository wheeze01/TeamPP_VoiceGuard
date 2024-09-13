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
        setContentView(R.layout.activity_agree)

        // "테스트 하기" 버튼 참조
        val testButton: Button = findViewById(R.id.test_button)
        // "보호자 전화번호 추가" 버튼 참조
        val guardianTestButton: Button = findViewById(R.id.guardian_test_button)

        // "테스트 하기" 버튼 클릭 시 다음 화면으로 이동
        testButton.setOnClickListener {
            val intent = Intent(this, CallAndDisconnect::class.java)
            startActivity(intent)
        }

        // "보호자 전화번호 추가" 버튼 클릭 시 GuardiansActivity로 이동
        guardianTestButton.setOnClickListener {
            val intent = Intent(this, GuardiansActivity::class.java)  // 여기서 GuardiansActivity로 이동
            startActivity(intent)
        }
    }

    // 뒤로가기 버튼 처리
    override fun onBackPressed() {
        if (backPressedOnce) {
            super.onBackPressed()
            finishAffinity() // 모든 액티비티 종료
        } else {
            backPressedOnce = true
            Toast.makeText(this, "뒤로가기 버튼을 한 번 더 누르면 앱이 종료됩니다.", Toast.LENGTH_SHORT).show()
            Handler().postDelayed({ backPressedOnce = false }, 2000)
        }
    }
}
