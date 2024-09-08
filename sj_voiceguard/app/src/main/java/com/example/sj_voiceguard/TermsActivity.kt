package com.example.sj_voiceguard

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class TermsActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private var backPressedOnce = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reportcheck)  // 레이아웃 파일 로드

        // SharedPreferences 초기화
        sharedPrefs = getSharedPreferences("AppPrefs", MODE_PRIVATE)

        // 동의 상태 확인
        val isAgreed = sharedPrefs.getBoolean("isAgreed", false)
        if (isAgreed) {
            // 이미 동의한 경우, 다음 화면으로 이동
            navigateToNextScreen()
            return
        }

        // CheckBox와 Button 참조
        val checkboxAgree = findViewById<CheckBox>(R.id.checkbox_agree)
        val buttonStart = findViewById<Button>(R.id.button_start)

        // 처음에는 버튼 비활성화
        buttonStart.isEnabled = false
        buttonStart.backgroundTintList = ContextCompat.getColorStateList(this, R.color.colorDisabled)

        // 체크박스 상태에 따라 버튼 활성화/비활성화
        checkboxAgree.setOnCheckedChangeListener { _, isChecked ->
            buttonStart.isEnabled = isChecked
            buttonStart.backgroundTintList = if (isChecked) {
                ContextCompat.getColorStateList(this, R.color.colorEnabled)  // 체크박스 선택 시 버튼 활성화 색상
            } else {
                ContextCompat.getColorStateList(this, R.color.colorDisabled) // 체크박스 해제 시 비활성화 색상
            }
        }

        // '시작하기' 버튼 클릭 시 처리
        buttonStart.setOnClickListener {
            if (checkboxAgree.isChecked) {
                // 동의 상태를 SharedPreferences에 저장
                val editor = sharedPrefs.edit()
                editor.putBoolean("isAgreed", true)
                editor.apply()

                navigateToNextScreen()
            }
        }
    }

    private fun navigateToNextScreen() {
        val intent = Intent(this, AgreeAct::class.java)
        startActivity(intent)
        finish()  // 현재 액티비티 종료
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
