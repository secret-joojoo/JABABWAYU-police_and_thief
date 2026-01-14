package com.example.police_and_thief

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class AttendanceParticipantActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val meetingId = intent.getStringExtra("meetingId") ?: ""

        setContent {
            MaterialTheme {
                AttendanceParticipantScreen(meetingId, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceParticipantScreen(meetingId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    var meetingTitle by remember { mutableStateOf("모임") }

    // 데이터 상태
    var totalParticipants by remember { mutableIntStateOf(0) }
    var checkedInCount by remember { mutableIntStateOf(0) }

    // 게임 설정 상태
    var policeCount by remember { mutableIntStateOf(1) }
    var gameTime by remember { mutableIntStateOf(15) }

    // 내 출석 여부
    var isMeCheckedIn by remember { mutableStateOf(false) }


    // QR 스캐너 설정
    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        if (result.contents != null) {
            val scannedId = result.contents
            if (scannedId == meetingId && currentUser != null) {

                // 1. 모임 문서에 출석 체크
                db.collection("meetings").document(meetingId)
                    .update("checkedInUids", FieldValue.arrayUnion(currentUser.uid))
                    .addOnSuccessListener {

                        val userRef = db.collection("users").document(currentUser.uid)

                        db.runTransaction { transaction ->
                            val snapshot = transaction.get(userRef)

                            // 현재 정보 가져오기
                            val currentLevel = snapshot.getLong("level")?.toInt() ?: 1
                            val currentExp = snapshot.getLong("exp")?.toInt() ?: 0
                            val currentManner = snapshot.getDouble("mannerTemp") ?: 36.5

                            // 보상 설정 (출석 보상: 경험치 10)
                            val earnedExp = 10

                            // ============================================================
                            // ★ [수정됨] LevelManager를 사용하여 깔끔하게 교체! ★
                            // ============================================================
                            // 기존의 복잡한 while문 로직을 다 지우고 이 한 줄만 쓰면 됩니다.
                            val (newLevel, newExp) = LevelManager.calculateNewStats(currentLevel, currentExp, earnedExp)
                            // ============================================================

                            // DB 업데이트
                            transaction.update(userRef, "level", newLevel)
                            transaction.update(userRef, "exp", newExp)
                            transaction.update(userRef, "mannerTemp", currentManner + 0.5)

                            // 리턴값 (레벨업 여부 확인)
                            if (newLevel > currentLevel) "LEVEL_UP" else "OK"

                        }.addOnSuccessListener { resultMsg ->
                            if (resultMsg == "LEVEL_UP") {
                                Toast.makeText(context, "출석 완료! 레벨 업!! 🎉", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "출석 완료! (경험치 +10, 매너 +0.5)", Toast.LENGTH_SHORT).show()
                            }
                        }.addOnFailureListener {
                            Toast.makeText(context, "보상 지급 실패", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    // ★ [핵심] 실시간 감시 (종료 로직 최우선)
    LaunchedEffect(meetingId) {
        if (meetingId.isNotEmpty() && currentUser != null) {
            db.collection("meetings").document(meetingId)
                .addSnapshotListener { snapshot, e ->
                    // 에러 혹은 문서 없음 처리
                    if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                    // 1. 모임 상태 확인 (가장 먼저!)
                    val meetingStatus = snapshot.getString("meetingStatus")
                    if (meetingStatus == "ENDED") {
                        Toast.makeText(context, "방장이 모임을 종료했습니다.", Toast.LENGTH_LONG).show()
                        // 액티비티 즉시 종료
                        (context as? Activity)?.finish()
                        return@addSnapshotListener
                    }

                    // 2. 6시간 타임아웃 확인 (가장 먼저!)
                    val meetingDate = snapshot.getTimestamp("date")?.toDate()
                    if (meetingDate != null) {
                        val sixHoursInMillis = 6 * 60 * 60 * 1000
                        val endTime = meetingDate.time + sixHoursInMillis
                        val now = System.currentTimeMillis()

                        if (now > endTime) {
                            Toast.makeText(context, "모임 시간이 만료되어 종료됩니다.", Toast.LENGTH_LONG).show()
                            (context as? Activity)?.finish()
                            return@addSnapshotListener
                        }
                    }

                    // --- 종료 조건이 아닐 때만 아래 데이터 갱신 ---
                    meetingTitle = snapshot.getString("title") ?: "모임"
                    val pIds = (snapshot.get("participantIds") as? List<String>) ?: emptyList()
                    val cIds = (snapshot.get("checkedInUids") as? List<String>) ?: emptyList()

                    totalParticipants = pIds.size
                    checkedInCount = cIds.size
                    isMeCheckedIn = cIds.contains(currentUser.uid)

                    val savedPolice = snapshot.getLong("policeCount")?.toInt()
                    if (savedPolice != null) policeCount = savedPolice

                    val savedTime = snapshot.getLong("gameTime")?.toInt()
                    if (savedTime != null) gameTime = savedTime

                    val gameStatus = snapshot.getString("gameStatus")
                    val winner = snapshot.getString("winner")

                    // 게임 시작 감지 (승자가 없고 진행 중일 때만 이동)
                    if (gameStatus == "PLAYING" && winner.isNullOrEmpty()) {
                        val intent = Intent(context, GameActivity::class.java)
                        intent.putExtra("meetingId", meetingId)
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    }
                }
        }
    }

    val thiefCount = (totalParticipants - policeCount).coerceAtLeast(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(meetingTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(context, ChatActivity::class.java)
                            intent.putExtra("meetingId", meetingId)
                            intent.putExtra("meetingTitle", meetingTitle)
                            context.startActivity(intent)
                        },
                        border = BorderStroke(1.dp, Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Black),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Email, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("채팅", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // [1] 게임 정보
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F4F6)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("게임정보", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("👮‍♂️ 경찰", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${policeCount}명", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E88E5))
                        }
                        Divider(modifier = Modifier.height(30.dp).width(1.dp), color = Color.LightGray)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("💰 도둑", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${thiefCount}명", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE53935))
                        }
                        Divider(modifier = Modifier.height(30.dp).width(1.dp), color = Color.LightGray)

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("⏳ 시간", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("${gameTime}분", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFA000))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // [2] QR 스캔 영역
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .size(220.dp)
                    .clickable {
                        if (!isMeCheckedIn) {
                            val options = ScanOptions()
                            options.setPrompt("방장의 QR 코드를 스캔하세요")
                            options.setBeepEnabled(false)
                            options.setOrientationLocked(false)
                            scanLauncher.launch(options)
                        }
                    }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(16.dp)
                ) {
                    if (isMeCheckedIn) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF43A047),
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("출석 완료!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF43A047))
                            Text("게임 시작 대기 중...", fontSize = 12.sp, color = Color.Gray)
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                painter = painterResource(id = android.R.drawable.ic_menu_camera),
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(50.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("QR 코드 스캔하기", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("(터치하여 카메라 켜기)", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isMeCheckedIn) {
                Text("방장이 게임을 시작할 때까지 대기해주세요", fontSize = 14.sp, color = Color.Gray)
            } else {
                Text("방장의 QR 코드를 스캔하여 출석하세요", fontSize = 14.sp, color = Color.Gray)
            }

            Spacer(modifier = Modifier.weight(1f))

            // [3] 출석 현황
            Text("현재 출석 현황", fontSize = 16.sp, color = Color.Gray)
            Text(
                text = "$checkedInCount / $totalParticipants",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if(checkedInCount == totalParticipants && totalParticipants > 0) Color(0xFF43A047) else Color.Black
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(56.dp))
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}