package com.example.sj_voiceguard
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
                showAddGuardianDialog()
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

    // 보호자 삭제 확인 다이얼로그 함수
    private fun confirmGuardianRemoval(position: Int) {
        // AlertDialog 생성
        val dialogBuilder = AlertDialog.Builder(this)
            .setTitle("보호자 삭제")
            .setMessage("정말로 삭제하시겠습니까?")
            .setCancelable(false)
            .setPositiveButton("삭제") { _, _ ->
                // 사용자가 삭제를 선택하면 실행되는 코드
                removeGuardian(position)
            }
            .setNegativeButton("취소") { dialog, _ ->
                // 사용자가 취소를 선택하면 다이얼로그 닫기
                dialog.dismiss()
            }

        // 다이얼로그 표시
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
        // 커스텀 레이아웃 inflate
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_guardian, null)

        // AlertDialog 생성
        val dialogBuilder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setTitle("보호자 추가")
            .setCancelable(false)

        val alertDialog = dialogBuilder.create()

        // EditText와 버튼 초기화
        val nameEditText = dialogView.findViewById<EditText>(R.id.edit_guardian_name)
        val phoneEditText = dialogView.findViewById<EditText>(R.id.edit_guardian_phone)
        val addButton = dialogView.findViewById<Button>(R.id.btn_add)
        val cancelButton = dialogView.findViewById<Button>(R.id.btn_cancel)

        // 추가 버튼 클릭 처리
        addButton.setOnClickListener {
            val name = nameEditText.text.toString().trim()
            val phone = phoneEditText.text.toString().trim()

            // 전화번호 중복 확인
            if (isPhoneDuplicate(phone)) {
                Toast.makeText(this, "이미 추가된 전화번호입니다.", Toast.LENGTH_SHORT).show()
            } else if (name.isNotEmpty() && phone.isNotEmpty()) {
                // 보호자 리스트에 추가 (이름:전화번호 형식)
                val guardianInfo = "$name: $phone"
                guardianList.add(guardianInfo)

                // RecyclerView 업데이트 및 보호자 저장
                guardianAdapter.notifyItemInserted(guardianList.size - 1)
                saveGuardians()

                // 다이얼로그 닫기
                alertDialog.dismiss()
            } else {
                Toast.makeText(this, "이름과 전화번호를 입력하세요.", Toast.LENGTH_SHORT).show()
            }
        }

        // 취소 버튼 클릭 처리
        cancelButton.setOnClickListener {
            alertDialog.dismiss()
        }

        // 다이얼로그 표시
        alertDialog.show()
    }

    // 전화번호 중복 확인 함수
    private fun isPhoneDuplicate(phone: String): Boolean {
        // 기존 보호자 리스트에서 전화번호만 추출하여 중복 여부 확인
        for (guardian in guardianList) {
            val existingPhone = guardian.split(":")[1].trim() // "이름:전화번호"에서 전화번호 부분만 추출
            if (existingPhone == phone) {
                return true // 중복된 전화번호가 있으면 true 반환
            }
        }
        return false // 중복된 전화번호가 없으면 false 반환
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

            // 삭제 버튼 클릭 처리
            holder.deleteButton.setOnClickListener {
                onDeleteClick(position)
            }
        }


        override fun getItemCount(): Int = guardians.size
    }
}
