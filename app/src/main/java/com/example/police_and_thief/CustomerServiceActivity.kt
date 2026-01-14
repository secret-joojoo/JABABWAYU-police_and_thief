package com.example.police_and_thief

// [도구 상자]
// 안드로이드 화면 구성, 디자인, 그리고 파이어베이스 통신을 위한 도구들을 가져옵니다.
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// [액티비티]
// 고객 문의 화면 전체를 담당하는 관리자 클래스입니다.
class CustomerServiceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // "이제부터 내가 만든 화면(CustomerServiceScreen)을 보여줄게!"
        setContent {
            MaterialTheme {
                // 뒤로가기 버튼을 누르면 'finish()'(화면 닫기)를 실행하도록 설정
                CustomerServiceScreen(onBack = { finish() })
            }
        }
    }
}

// [화면 그리기 함수]
// 실제 눈에 보이는 디자인과 기능을 만드는 곳입니다.
// @OptIn: 드롭다운 메뉴 기능이 아직 실험적 기능이라서 경고를 끄는 용도입니다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerServiceScreen(onBack: () -> Unit) {
    // 1. 필요한 도구들 준비
    val context = LocalContext.current // 토스트 메시지 띄울 때 필요
    val db = Firebase.firestore        // 데이터베이스 도구
    val auth = Firebase.auth           // 로그인 정보 도구

    // ==========================================
    // [2. 상태 변수] (화면이 기억해야 할 값들)
    // ==========================================

    // 선택지 목록 (사유들)
    val reasons = listOf("추가 기능 요청", "앱 버그 제보", "비매너 유저 신고", "개발자에 대한 칭찬", "기타 문의")

    // 드롭다운 메뉴가 펼쳐졌는지? (true: 펼쳐짐, false: 닫힘)
    var expanded by remember { mutableStateOf(false) }

    // 사용자가 선택한 사유 (초기값은 "" 빈 문자열)
    var selectedReason by remember { mutableStateOf("") }

    // 사용자가 입력한 내용
    var contentText by remember { mutableStateOf("") }

    // 전송 버튼 눌렀을 때 로딩 중인지? (true: 로딩 중, false: 대기 중)
    var isSubmitting by remember { mutableStateOf(false) }

    // ==========================================
    // [3. 전체 화면 구성]
    // ==========================================
    Column(
        modifier = Modifier
            .fillMaxSize() // 화면 꽉 채우기
            .background(Color.White) // 배경 흰색
            .systemBarsPadding() // 배터리바, 하단바와 겹치지 않게 여백 자동 추가
            .padding(16.dp) // 전체적으로 안쪽 여백 주기
    ) {
        // [3-1] 상단 헤더 (뒤로가기 버튼 + 제목)
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentAlignment = Alignment.Center // 내용물 가운데 정렬
        ) {
            // 뒤로가기 버튼 (왼쪽 끝)
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = Color.Black)
            }
            // 제목 (가운데)
            Text("고객 문의", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }

        Spacer(modifier = Modifier.height(20.dp)) // 간격 띄우기

        // [3-2] 사유 선택 (드롭다운 메뉴 영역)
        Text("문의 사유", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        // 드롭다운 박스 컨테이너
        ExposedDropdownMenuBox(
            expanded = expanded, // 현재 펼쳐진 상태인가?
            onExpandedChange = { expanded = !expanded }, // 누를 때마다 상태 반전 (열기/닫기)
            modifier = Modifier.fillMaxWidth()
        ) {
            // (1) 선택된 값이 보이는 텍스트 필드
            OutlinedTextField(
                value = selectedReason, // 현재 선택된 값 보여주기
                onValueChange = {}, // 직접 타이핑 금지 (선택만 가능하게)
                readOnly = true, // 읽기 전용 모드
                // 값이 비어있을 때 보여줄 회색 안내 문구 (Placeholder)
                placeholder = {
                    Text("문의 사유를 선택해 주세요", color = Color.Gray)
                },
                // 오른쪽 화살표 아이콘 (상태에 따라 위/아래 바뀜)
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(), // 메뉴가 이 박스에 붙도록 설정
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedBorderColor = Color.Black,
                    unfocusedBorderColor = Color.Gray
                )
            )
            // (2) 펼쳐졌을 때 나오는 선택지 리스트
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }, // 바깥 누르면 닫기
                modifier = Modifier.background(Color.White)
            ) {
                // 리스트(reasons)에 있는 항목들을 하나씩 메뉴 아이템으로 만듦
                reasons.forEach { item ->
                    DropdownMenuItem(
                        text = { Text(text = item) },
                        onClick = {
                            selectedReason = item // 선택한 값 저장
                            expanded = false // 메뉴 닫기
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // [3-3] 내용 입력 (큰 텍스트 박스)
        Text("내용", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = contentText,
            onValueChange = { contentText = it }, // 글자 칠 때마다 변수에 저장
            placeholder = { Text("문의하실 내용을 자세히 적어주세요.") },
            modifier = Modifier.fillMaxWidth().weight(1f), // 남은 공간을 꽉 채우기 (크게 만들기)
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Color.Black,
                unfocusedBorderColor = Color.Gray
            ),
            singleLine = false, // 여러 줄 입력 허용
            maxLines = 20 // 최대 20줄까지
        )

        Spacer(modifier = Modifier.height(16.dp))

        // [3-4] 제출 버튼
        Button(
            onClick = {
                // === [검증 로직] 빈 칸이 있는지 확인 ===
                if (selectedReason.isBlank()) {
                    Toast.makeText(context, "문의 사유를 선택해주세요.", Toast.LENGTH_SHORT).show()
                    return@Button // 함수 종료 (전송 안 함)
                }

                if (contentText.isBlank()) {
                    Toast.makeText(context, "내용을 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@Button // 함수 종료
                }

                // === [전송 로직] ===
                isSubmitting = true // 로딩 시작 (버튼 비활성화)

                // 1. 보낼 데이터를 상자(HashMap)에 포장하기
                val inquiryData = hashMapOf(
                    "uid" to (auth.currentUser?.uid ?: "anonymous"), // 내 아이디 (없으면 익명)
                    "email" to (auth.currentUser?.email ?: "unknown"), // 내 이메일
                    "reason" to selectedReason, // 선택한 사유
                    "content" to contentText, // 입력한 내용
                    "timestamp" to FieldValue.serverTimestamp(), // 서버 시간 (지금)
                    "status" to "open" // 처리 상태 (아직 안 읽음)
                )

                // 2. 파이어베이스 'inquiries' 폴더에 택배 보내기
                db.collection("inquiries")
                    .add(inquiryData)
                    .addOnSuccessListener {
                        // 성공했을 때
                        isSubmitting = false // 로딩 끝
                        Toast.makeText(context, "문의가 접수되었습니다.", Toast.LENGTH_SHORT).show()
                        onBack() // 성공했으니 화면 닫고 뒤로가기
                    }
                    .addOnFailureListener {
                        // 실패했을 때 (인터넷 끊김 등)
                        isSubmitting = false // 로딩 끝
                        Toast.makeText(context, "오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black), // 검은 버튼
            enabled = !isSubmitting // 로딩 중일 때는 클릭 못하게 막음
        ) {
            // 버튼 내부 모양
            if (isSubmitting) {
                // 로딩 중이면 뱅글뱅글 도는 아이콘 보여주기
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                // 평소에는 '제출' 글씨 보여주기
                Text("제출", fontSize = 18.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}