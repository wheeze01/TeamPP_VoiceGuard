package com.example.sj_voiceguard
import android.text.Html
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.sj_voiceguard.MainActivity
import com.example.sj_voiceguard.R

class TermsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.reportcheck)  // activity_terms.xml 로드

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
                // 체크박스가 체크된 경우에만 다음 화면으로 이동
                val intent = Intent(this, AgreeAct::class.java)
                startActivity(intent)
            }
        }
    }
}
