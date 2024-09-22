package com.example.sj_voiceguard

import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog

class GuardiansActivity : AppCompatActivity() {

    private lateinit var sharedPrefs: SharedPreferences
    private lateinit var guardianAdapter: GuardianAdapter
    private val guardianList = mutableListOf<String>()
    private val maxGuardians = 3
    private val SMS_PERMISSION_CODE = 100 // 권한 요청 코드

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guardians)

        // SharedPreferences 초기화
        sharedPrefs = getSharedPreferences("GuardiansPrefs", Context.MODE_PRIVATE)

        // RecyclerView 설정
        val recyclerView: RecyclerView = findViewById(R.id.recycler_guardians)
        recyclerView.layoutManager = LinearLayoutManager(this)

        guardianAdapter = GuardianAdapter(guardianList) { position ->
            confirmGuardianRemoval(position)
        }

        recyclerView.adapter = guardianAdapter

        // 보호자 추가 버튼
        val addGuardianButton: Button = findViewById(R.id.btn_add_guardian)
        addGuardianButton.setOnClickListener {
            if (guardianList.size < maxGuardians) {
                if (checkSmsPermission()) {
                    showAddGuardianDialog()
                } else {
                    requestSmsPermission()
                }
            } else {
                Toast.makeText(this, "최대 3명의 보호자만 추가할 수 있습니다.", Toast.LENGTH_SHORT).show()
            }
        }

        // 보호자 기능 테스트 버튼
        val testGuardianButton: Button = findViewById(R.id.btn_test_guardian)
        testGuardianButton.setOnClickListener {
            sendDangerMessageToGuardians()
        }

        // 보호자 리스트 불러오기
        loadGuardians()
    }

    // 권한 확인 함수
    private fun checkSmsPermission(): Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS)
        return permissionCheck == PackageManager.PERMISSION_GRANTED
    }

    // 권한 요청 함수
    private fun requestSmsPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.SEND_SMS)) {
            // 권한 설명이 필요할 때 메시지 표시
            Toast.makeText(this, "SMS 전송 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.SEND_SMS), SMS_PERMISSION_CODE)
    }

    // 권한 요청 결과 처리 함수
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            SMS_PERMISSION_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // 권한이 승인되었으면 보호자 추가 다이얼로그 표시
                    showAddGuardianDialog()
                } else {
                    // 권한이 거부되었을 때 처리
                    Toast.makeText(this, "SMS 전송 권한이 필요합니다. 보호자 추가 기능을 사용할 수 없습니다.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // 보호자 삭제 확인 다이얼로그 함수
    private fun confirmGuardianRemoval(position: Int) {
        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle("보호자 삭제")
            .setMessage("정말로 삭제하시겠습니까?")
            .setCancelable(false)
            .setPositiveButton("삭제") { _, _ ->
                removeGuardian(position)
            }
            .setNegativeButton("취소") { dialog, _ ->
                dialog.dismiss()
            }

        val alertDialog = dialogBuilder.create()
        alertDialog.show()
    }

    // 보호자 삭제 함수
    private fun removeGuardian(position: Int) {
        guardianList.removeAt(position)
        guardianAdapter.notifyItemRemoved(position)
        saveGuardians()
        Toast.makeText(this, "보호자가 삭제되었습니다.", Toast.LENGTH_SHORT).show()
    }

    // 보호자 추가 다이얼로그
    private fun showAddGuardianDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_guardian, null)

        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("보호자 추가")
            .setCancelable(false)

        val alertDialog = dialogBuilder.create()

        val nameEditText = dialogView.findViewById<EditText>(R.id.edit_guardian_name)
        val phoneEditText = dialogView.findViewById<EditText>(R.id.edit_guardian_phone)
        val addButton = dialogView.findViewById<Button>(R.id.btn_add)
        val cancelButton = dialogView.findViewById<Button>(R.id.btn_cancel)

        addButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val phone = phoneEditText.text.toString().trim()

            if (isPhoneDuplicate(phone)) {
                Toast.makeText(this, "이미 추가된 전화번호입니다.", Toast.LENGTH_SHORT).show()
            } else if (name.isNotEmpty() && phone.isNotEmpty()) {
                val guardianInfo = "$name: $phone"
                guardianList.add(guardianInfo)

                guardianAdapter.notifyItemInserted(guardianList.size - 1)
                saveGuardians()

                alertDialog.dismiss()
            } else {
                Toast.makeText(this, "이름과 전화번호를 입력하세요.", Toast.LENGTH_SHORT).show()
            }
        }

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    // 전화번호 중복 확인 함수
    private fun isPhoneDuplicate(phone: String): Boolean {
        for (guardian in guardianList) {
            val existingPhone = guardian.split(":")[1].trim()
            if (existingPhone == phone) {
                return true
            }
        }
        return false
    }

    // 보호자 저장 함수
    private fun saveGuardians() {
        val editor = sharedPrefs.edit()
        editor.putStringSet("guardians", guardianList.toSet())
        editor.apply()
    }

    // 보호자 불러오기 함수
    private fun loadGuardians() {
        val savedGuardians = sharedPrefs.getStringSet("guardians", emptySet())
        guardianList.clear()
        savedGuardians?.let {
            guardianList.addAll(it)
        }
        guardianAdapter.notifyDataSetChanged()
    }

    // 보호자에게 경고 메시지 전송
    private fun sendDangerMessageToGuardians() {
        val savedGuardians = sharedPrefs.getStringSet("guardians", emptySet())
        if (savedGuardians.isNullOrEmpty()) {
            Toast.makeText(this, "저장된 보호자 전화번호가 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }

        val smsManager = SmsManager.getDefault()
        for (guardianInfo in savedGuardians) {
            val guardianDetails = guardianInfo.split(":")
            val name = guardianDetails[0].trim()
            val phone = guardianDetails[1].trim()

            try {
                smsManager.sendTextMessage(phone, null, "[VoiceGuard 발신] 보호자 경고 기능 테스트 메세지입니다.", null, null)
                Toast.makeText(this, "$name 에게 메시지를 보냈습니다.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "$name 에게 메시지를 보내지 못했습니다.", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    // RecyclerView 어댑터
    inner class GuardianAdapter(
        private val guardians: MutableList<String>,
        private val onDeleteClick: (Int) -> Unit
    ) : RecyclerView.Adapter<GuardianAdapter.GuardianViewHolder>() {

        inner class GuardianViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val guardianInfo: TextView = view.findViewById(R.id.tv_guardian_info)
            val deleteButton: ImageButton = view.findViewById(R.id.btn_delete_guardian)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GuardianViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_guardian, parent, false)
            return GuardianViewHolder(view)
        }

        override fun onBindViewHolder(holder: GuardianViewHolder, position: Int) {
            val guardianInfo = guardians[position]
            holder.guardianInfo.text = guardianInfo

            holder.deleteButton.setOnClickListener {
                onDeleteClick(position)
            }
        }

        override fun getItemCount(): Int = guardians.size
    }
}
