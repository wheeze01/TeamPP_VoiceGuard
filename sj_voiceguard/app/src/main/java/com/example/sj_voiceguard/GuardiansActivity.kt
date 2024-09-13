package com.example.sj_voiceguard

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class GuardiansActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var guardianListView: ListView
    private lateinit var guardianListAdapter: ArrayAdapter<String>
    private val guardianList = mutableListOf<String>()
    private val maxGuardians = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardians)

        // SharedPreferences 초기화
        sharedPrefs = getSharedPreferences("GuardiansPrefs", Context.MODE_PRIVATE)

        // 리스트뷰 및 버튼 참조
        guardianListView = findViewById(R.id.list_guardians)
        val addGuardianButton: Button = findViewById(R.id.btn_add_guardian)
        val testGuardianButton: Button = findViewById(R.id.btn_test_guardian)

        // 보호자 리스트 불러오기
        loadGuardians()

        // 리스트뷰 어댑터 설정
        guardianListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, guardianList)
        guardianListView.adapter = guardianListAdapter

        // 보호자 추가 버튼 클릭 시
        addGuardianButton.setOnClickListener {
            if (guardianList.size >= maxGuardians) {
                Toast.makeText(this, "최대 3개의 전화번호까지 추가 가능합니다.", Toast.LENGTH_SHORT).show()
            } else {
                showAddGuardianDialog()
            }
        }

        // 리스트뷰 아이템 롱클릭 시 삭제 팝업 표시
        guardianListView.setOnItemLongClickListener { _, _, position, _ ->
            showDeleteGuardianDialog(position)
            true
        }

        // 보호자 기능 테스트 버튼 클릭 시
        testGuardianButton.setOnClickListener {
            sendDangerMessageToGuardians()
        }
    }

    // 보호자에게 메시지 전송하는 함수
    private fun sendDangerMessageToGuardians() {
        val savedGuardians = sharedPrefs.getStringSet("guardians", emptySet())
        if (savedGuardians.isNullOrEmpty()) {
            Toast.makeText(this, "저장된 보호자 전화번호가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val smsManager = SmsManager.getDefault()
        for (guardianInfo in savedGuardians) {
            val phone = guardianInfo.split(":")[1].trim() // 전화번호만 추출
            try {
                smsManager.sendTextMessage(phone, null, "위험", null, null)
                Toast.makeText(this, "$phone 에 메시지를 보냈습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "$phone 에 메시지를 보내지 못했습니다.", Toast.LENGTH_SHORT).show()
                e.printStackTrace() // 예외 메시지 출력
            }
        }
    }

    // 보호자 추가 다이얼로그 표시
    private fun showAddGuardianDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_guardian, null)
        val guardianNameInput = dialogView.findViewById<EditText>(R.id.edit_guardian_name)
        val guardianPhoneInput = dialogView.findViewById<EditText>(R.id.edit_guardian_phone)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<Button>(R.id.btn_add).setOnClickListener {
            val name = guardianNameInput.text.toString().trim()
            val phone = guardianPhoneInput.text.toString().trim()

            if (name.isNotEmpty() && phone.isNotEmpty()) {
                addGuardian("$name : $phone")
                dialog.dismiss()
            } else {
                Toast.makeText(this, "이름과 전화번호를 입력해주세요.", Toast.LENGTH_SHORT).show()
            }
        }

        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    // 보호자 추가 함수
    private fun addGuardian(guardianInfo: String) {
        guardianList.add(guardianInfo)
        guardianListAdapter.notifyDataSetChanged()
        saveGuardians()
    }

    // 보호자 삭제 다이얼로그 표시
    private fun showDeleteGuardianDialog(position: Int) {
        AlertDialog.Builder(this)
            .setMessage("정말 보호자 전화번호를 삭제하시겠습니까?")
            .setPositiveButton("확인") { _, _ ->
                guardianList.removeAt(position)
                guardianListAdapter.notifyDataSetChanged()
                saveGuardians()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // 보호자 리스트 저장 함수
    private fun saveGuardians() {
        val editor = sharedPrefs.edit()
        editor.putStringSet("guardians", guardianList.toSet())
        editor.apply()
    }

    // 보호자 리스트 불러오기 함수
    private fun loadGuardians() {
        val savedGuardians = sharedPrefs.getStringSet("guardians", emptySet())
        guardianList.clear()
        savedGuardians?.let {
            guardianList.addAll(it)
        }
    }
}
