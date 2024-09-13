package com.example.sj_voiceguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class AgreeAct : AppCompatActivity() {
    private var backPressedOnce = false
    private val RECORD_AUDIO_PERMISSION_CODE = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_agree) // terms 화면 레이아웃 설정

        // "테스트 하기" 버튼 참조
        val testButton: Button = findViewById(R.id.test_button)

        // 버튼 클릭 시 권한 확인 후 다음 화면으로 이동
        testButton.setOnClickListener {
            if (checkAudioPermission()) {
                startCallAndDisconnectActivity()
            } else {
                requestAudioPermission()
            }
        }
    }

    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            RECORD_AUDIO_PERMISSION_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCallAndDisconnectActivity()
            } else {
                Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCallAndDisconnectActivity() {
        val intent = Intent(this, CallAndDisconnect::class.java)
        startActivity(intent)
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
