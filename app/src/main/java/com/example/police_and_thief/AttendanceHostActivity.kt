package com.example.police_and_thief

import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AttendanceHostActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val meetingId = intent.getStringExtra("meetingId") ?: ""

        setContent {
            MaterialTheme {
                AttendanceHostScreen(meetingId, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceHostScreen(meetingId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var meetingTitle by remember { mutableStateOf("모임") }
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 데이터 상태
    var totalParticipants by remember { mutableIntStateOf(0) }
    var checkedInCount by remember { mutableIntStateOf(0) }

    // 게임 설정 상태
    var policeCount by remember { mutableIntStateOf(1) }
    var gameTime by remember { mutableIntStateOf(15) }

    // UI 상태
    var showSettingDialog by remember { mutableStateOf(false) }

    // [1] 데이터 리스닝 & 화면 이동 & 종료 로직
    LaunchedEffect(meetingId) {
        if (meetingId.isNotEmpty()) {
            // QR 생성
            try {
                val multiFormatWriter = MultiFormatWriter()
                val bitMatrix: BitMatrix = multiFormatWriter.encode(meetingId, BarcodeFormat.QR_CODE, 400, 400)
                val barcodeEncoder = BarcodeEncoder()
                qrBitmap = barcodeEncoder.createBitmap(bitMatrix)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            db.collection("meetings").document(meetingId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                    meetingTitle = snapshot.getString("title") ?: "모임"
                    val pIds = (snapshot.get("participantIds") as? List<String>) ?: emptyList()
                    val cIds = (snapshot.get("checkedInUids") as? List<String>) ?: emptyList()

                    totalParticipants = pIds.size
                    checkedInCount = cIds.size

                    // 경찰 수 & 시간 불러오기
                    val savedPolice = snapshot.getLong("policeCount")?.toInt()
                    if (savedPolice != null) policeCount = savedPolice
                    else if (policeCount == 1 && totalParticipants > 0) policeCount = (totalParticipants / 3).coerceAtLeast(1)

                    val savedTime = snapshot.getLong("gameTime")?.toInt()
                    if (savedTime != null) gameTime = savedTime

                    // ★ [1] 게임 시작 감지 (PLAYING)
                    val gameStatus = snapshot.getString("gameStatus")
                    if (gameStatus == "PLAYING") {
                        val intent = Intent(context, GameActivity::class.java)
                        intent.putExtra("meetingId", meetingId)
                        context.startActivity(intent)
                    }

                    // ★ [2] 모임 종료 감지 (ENDED) -> 액티비티 닫기
                    val meetingStatus = snapshot.getString("meetingStatus")
                    if (meetingStatus == "ENDED") {
                        Toast.makeText(context, "모임이 종료되었습니다.", Toast.LENGTH_LONG).show()
                        (context as? android.app.Activity)?.finish()
                    }

                    // ★ [3] 6시간 타임아웃 체크 (Start Time + 6h)
                    val meetingDate = snapshot.getTimestamp("date")?.toDate()
                    if (meetingDate != null) {
                        val sixHoursInMillis = 6 * 60 * 60 * 1000
                        val endTime = meetingDate.time + sixHoursInMillis
                        val now = System.currentTimeMillis()

                        if (now > endTime) {
                            // 6시간 지났으면 강제 종료 처리
                            db.collection("meetings").document(meetingId).update("meetingStatus", "ENDED")
                            Toast.makeText(context, "모임 시간이 초과되어 종료됩니다.", Toast.LENGTH_LONG).show()
                            (context as? android.app.Activity)?.finish()
                        }
                    }
                }
        }
    }

    // [수정] 현재 출석한 인원을 기준으로 도둑 수 계산 (경찰 수가 인원보다 많으면 자동 조절)
    val actualPoliceCount = if (checkedInCount < 2) policeCount else policeCount.coerceAtMost(checkedInCount - 1)
    val thiefCount = (checkedInCount - actualPoliceCount).coerceAtLeast(0)

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
                    // ★ [추가됨] 모임 종료 버튼 (빨간 배경)
                    Button(
                        onClick = {
                            // 1. 현재 모임 정보 가져오기 (정산을 위해)
                            db.collection("meetings").document(meetingId).get()
                                .addOnSuccessListener { doc ->
                                    if (doc.exists()) {
                                        val hostUid = doc.getString("hostUid") ?: ""
                                        val participantIds = (doc.get("participantIds") as? List<String>) ?: emptyList()
                                        val checkedInUids = (doc.get("checkedInUids") as? List<String>) ?: emptyList()

                                        // [로직 1] 방장(Host) 보상: 성실한 운영 (+1.0도)
                                        if (hostUid.isNotEmpty()) {
                                            db.collection("users").document(hostUid)
                                                .update("mannerTemp", FieldValue.increment(1.0))
                                        }

                                        // [로직 2] 결석자(No-Show) 처벌: 신청은 했으나 안 온 사람 (-3.0도)
                                        // (방장은 출석체크 안 해도 결석 처리 안 되게 제외)
                                        val absentees = participantIds.filter { !checkedInUids.contains(it) && it != hostUid }

                                        for (absenteeUid in absentees) {
                                            db.collection("users").document(absenteeUid)
                                                .update("mannerTemp", FieldValue.increment(-3.0))
                                        }

                                        // [로직 3] 모임 상태 종료로 변경 (기존 코드)
                                        db.collection("meetings").document(meetingId)
                                            .update("meetingStatus", "ENDED")
                                            .addOnSuccessListener {
                                                Toast.makeText(context, "모임 종료! 매너온도가 정산되었습니다.", Toast.LENGTH_LONG).show()
                                            }
                                    }
                                }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)), // 빨간색
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(36.dp).padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("모임 종료", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // 기존 채팅 버튼
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
                        modifier = Modifier.height(36.dp).padding(end = 8.dp)
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
            // [1] 게임 설정 카드
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF2F4F6)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showSettingDialog = true }
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("게임 설정 (인원 및 시간)", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(Icons.Default.Settings, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
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

            // [2] QR 코드
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.size(220.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap!!.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier.fillMaxSize().padding(12.dp)
                        )
                    } else {
                        CircularProgressIndicator(color = Color.Black)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("참여자들에게 QR 코드를 보여주세요", fontSize = 13.sp, color = Color.Gray)

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

            // [4] 게임 시작 버튼
            Button(
                onClick = {
                    if (checkedInCount < 2) {
                        Toast.makeText(context, "최소 2명 이상 출석해야 합니다.", Toast.LENGTH_SHORT).show()

                    } else {
                        db.collection("meetings").document(meetingId).get().addOnSuccessListener { doc ->
                            val checkedInUsers = (doc.get("checkedInUids") as? List<String>) ?: emptyList()
                            val shuffledUsers = checkedInUsers.shuffled()
                            val previousWinner = doc.getString("winnerTeam")

                            if (previousWinner != null) {
                                val historyData = hashMapOf(
                                    "winnerTeam" to previousWinner,
                                    "mvpUid" to (doc.getString("mvpUid") ?: ""),
                                    "gameResult" to (doc.getString("gameResult") ?: ""),
                                    "roles" to (doc.get("roles") ?: emptyMap<String, String>()), // 누가 무슨 역할이었는지
                                    "playedAt" to FieldValue.serverTimestamp() // 언제 했던 게임인지
                                )
                                db.collection("meetings").document(meetingId)
                                    .collection("history")
                                    .add(historyData)
                            }

                            val rolesMap = mutableMapOf<String, String>()
                            val policeNum = policeCount.coerceAtMost(shuffledUsers.size - 1)

                            for (i in 0 until policeNum) rolesMap[shuffledUsers[i]] = "POLICE"
                            for (i in policeNum until shuffledUsers.size) rolesMap[shuffledUsers[i]] = "THIEF"

                            val updates = hashMapOf<String, Any>(
                                "gameStatus" to "PLAYING",
                                "roles" to rolesMap,
                                "gameStartTime" to FieldValue.serverTimestamp(),
                                "gameStartedAt" to FieldValue.serverTimestamp(),
                                // ★ 중요: 기록은 history에 옮겼으니, 메인에서는 지워야 새 게임이 깨끗하게 켜짐
                                "winner" to FieldValue.delete(),
                                "gameResult" to FieldValue.delete(),
                                "mvpUid" to FieldValue.delete()
                            )

                            db.collection("meetings").document(meetingId)
                                .update(updates)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "게임을 시작합니다!", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("게임 시작", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // 설정 다이얼로그
    if (showSettingDialog) {
        Dialog(onDismissRequest = { showSettingDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("게임 설정 변경", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(20.dp))

                    Text("경찰 인원", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilledIconButton(
                            onClick = { if (policeCount > 1) policeCount-- },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFEEEEEE)),
                            modifier = Modifier.size(40.dp)
                        ) { Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
                        Spacer(modifier = Modifier.width(20.dp))
                        Text("$policeCount", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                        Spacer(modifier = Modifier.width(20.dp))
                        FilledIconButton(
                            onClick = { if (policeCount < totalParticipants - 1) policeCount++ },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFEEEEEE)),
                            modifier = Modifier.size(40.dp)
                        ) { Icon(Icons.Default.Add, contentDescription = null) }
                    }
                    Text("도둑: ${totalParticipants - policeCount}명", fontSize = 13.sp, color = Color.Gray)

                    Divider(modifier = Modifier.padding(vertical = 16.dp))

                    Text("게임 시간", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        FilledIconButton(
                            onClick = { if (gameTime > 5) gameTime -= 5 },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFFF3E0)),
                            modifier = Modifier.size(40.dp)
                        ) { Text("-", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF6C00)) }
                        Spacer(modifier = Modifier.width(20.dp))
                        Text("${gameTime}분", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFA000))
                        Spacer(modifier = Modifier.width(20.dp))
                        FilledIconButton(
                            onClick = { if (gameTime < 120) gameTime += 5 },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFFFF3E0)),
                            modifier = Modifier.size(40.dp)
                        ) { Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFFEF6C00)) }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            val updates = hashMapOf<String, Any>(
                                "policeCount" to policeCount,
                                "gameTime" to gameTime
                            )
                            db.collection("meetings").document(meetingId)
                                .update(updates)
                                .addOnSuccessListener {
                                    Toast.makeText(context, "설정이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                                    showSettingDialog = false
                                }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("설정 완료", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}