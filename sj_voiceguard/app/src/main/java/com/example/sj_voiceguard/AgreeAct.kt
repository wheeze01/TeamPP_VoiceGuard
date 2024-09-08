package com.example.sj_voiceguard

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class AgreeAct : AppCompatActivity() {
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
}
