package com.example.police_and_thief

import com.google.firebase.firestore.AggregateSource
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay

class GameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val meetingId = intent.getStringExtra("meetingId")

        if (meetingId.isNullOrEmpty()) {
            finish()
            return
        }

        setContent {
            MaterialTheme {
                GameScreen(meetingId)
            }
        }
    }
}

@Composable
fun GameScreen(meetingId: String) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val currentUser = Firebase.auth.currentUser

    // 게임 상태
    var myRole by remember { mutableStateOf("LOADING") }
    var hostUid by remember { mutableStateOf("") }
    var winnerTeam by remember { mutableStateOf("") }

    // ★ [추가] 기록 저장을 위해 전체 역할 맵을 기억해둠
    var currentRolesMap by remember { mutableStateOf(emptyMap<String, String>()) }

    // 타이머 & 인원
    var remainingTimeText by remember { mutableStateOf("00:00") }
    var policeCount by remember { mutableIntStateOf(0) }
    var thiefCount by remember { mutableIntStateOf(0) }

    // UI 상태
    var showEndGameDialog by remember { mutableStateOf(false) }

    // [1] 데이터 로드
    LaunchedEffect(meetingId) {
        if (meetingId.isNotEmpty() && currentUser != null) {
            db.collection("meetings").document(meetingId)
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                    val rolesMap = snapshot.get("roles") as? Map<String, String> ?: emptyMap()
                    currentRolesMap = rolesMap // ★ 저장용으로 백업

                    hostUid = snapshot.getString("hostUid") ?: ""
                    winnerTeam = snapshot.getString("winner") ?: ""

                    myRole = rolesMap[currentUser.uid] ?: "SPECTATOR"
                    policeCount = rolesMap.values.count { it == "POLICE" }
                    thiefCount = rolesMap.values.count { it == "THIEF" }
                }
        }
    }

    // [2] 타이머 로직
    LaunchedEffect(meetingId, winnerTeam, hostUid) {
        while (winnerTeam.isEmpty()) {
            delay(1000L)
            db.collection("meetings").document(meetingId).get()
                .addOnSuccessListener { doc ->
                    if (doc != null && doc.exists()) {
                        val startTime = doc.getTimestamp("gameStartTime")?.toDate()
                        val durationMin = doc.getLong("gameTime")?.toInt() ?: 15

                        if (startTime != null) {
                            val endTime = startTime.time + (durationMin * 60 * 1000)
                            val now = System.currentTimeMillis()
                            val diff = endTime - now

                            if (diff > 0) {
                                val min = diff / (1000 * 60)
                                val sec = (diff / 1000) % 60
                                remainingTimeText = String.format("%02d:%02d", min, sec)
                            } else {
                                remainingTimeText = "00:00"
                                if (currentUser != null && currentUser.uid == hostUid && !showEndGameDialog) {
                                    showEndGameDialog = true
                                }
                            }
                        }
                    }
                }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // --- 1층: 게임 화면 ---
        Scaffold(containerColor = Color(0xFFF5F5F5)) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TopInfoBar(policeCount, thiefCount, remainingTimeText)
                Spacer(modifier = Modifier.height(30.dp))

                if (myRole == "LOADING") {
                    CircularProgressIndicator(color = Color(0xFFFF6F00))
                    Text("로딩 중...", modifier = Modifier.padding(top = 16.dp), color = Color.Gray)
                } else {
                    RoleCard(role = myRole)
                }

                if (currentUser != null && currentUser.uid == hostUid) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showEndGameDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                        modifier = Modifier.fillMaxWidth(0.8f).height(56.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "게임 끝",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // --- 2층: 결과 오버레이 ---
        if (winnerTeam.isNotEmpty()) {
            GameResultOverlay(
                myRole = myRole,
                winnerTeam = winnerTeam,
                onDismiss = {
                    val targetActivity = if (currentUser?.uid == hostUid) {
                        AttendanceHostActivity::class.java
                    } else {
                        AttendanceParticipantActivity::class.java
                    }
                    val intent = Intent(context, targetActivity)
                    intent.putExtra("meetingId", meetingId)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    context.startActivity(intent)
                    (context as? Activity)?.finish()
                }
            )
        }
    }

    // ★ [수정] 방장용 다이얼로그: 승패 결정 시 기록 저장 + 종료 처리
    if (showEndGameDialog) {
        Dialog(onDismissRequest = { showEndGameDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "게임 종료!",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.Red
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("이번 라운드의 승자는 누구인가요?", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(24.dp))

                    // ★★★ [GameActivity.kt] 게임 종료 함수 복구 ★★★
                    fun finishGame(winner: String) {
                        // 1. 시작 시간 가져와서 플레이 시간 계산
                        db.collection("meetings").document(meetingId).get()
                            .addOnSuccessListener { doc ->
                                val startTime = doc.getDate("gameStartedAt") ?: java.util.Date()
                                val endTime = java.util.Date()
                                val diffMillis = endTime.time - startTime.time
                                var diffMinutes = (diffMillis / (1000 * 60)).toInt()
                                if (diffMinutes < 1) diffMinutes = 1

                                // 2. 몇 번째 라운드인지 확인
                                db.collection("meetings").document(meetingId)
                                    .collection("game_history").count().get(AggregateSource.SERVER)
                                    .addOnSuccessListener { task ->
                                        val roundNum = task.count + 1

                                        // 3. 전적(History) 저장 (실제 시간 포함)
                                        val historyData = hashMapOf(
                                            "winner" to winner,
                                            "roles" to currentRolesMap,
                                            "playedAt" to FieldValue.serverTimestamp(),
                                            "actualPlayTime" to diffMinutes
                                        )

                                        db.collection("meetings").document(meetingId)
                                            .collection("game_history")
                                            .add(historyData)

                                        // ★★★ 4. 채팅방에 결과 카드 전송 (이게 있어야 뜹니다!) ★★★
                                        sendGameResultToChat(
                                            meetingId = meetingId,
                                            winnerTeam = winner,
                                            roles = currentRolesMap,
                                            round = roundNum.toInt()
                                        )

                                        // 5. 유저 경험치 정산
                                        currentRolesMap.forEach { (uid, role) ->
                                            val isWin = (role == winner)
                                            val earnedExp = if (isWin) 50 else 10 // 획득 경험치

                                            db.collection("users").document(uid).get().addOnSuccessListener { uDoc ->
                                                if (uDoc.exists()) {
                                                    // ★ [중요] DB 필드명 "exp"로 수정됨
                                                    val cLevel = uDoc.getLong("level")?.toInt() ?: 1
                                                    val cExp = uDoc.getLong("exp")?.toInt() ?: 0 // 'xp' -> 'exp'

                                                    // 계산 함수 호출
                                                    val (nLevel, nExp) = calculateNewLevelData(cLevel, cExp, earnedExp)

                                                    // ★ [중요] 업데이트할 때도 "exp"로 저장
                                                    db.collection("users").document(uid).update(
                                                        mapOf(
                                                            "level" to nLevel,
                                                            "exp" to nExp
                                                        )
                                                    )
                                                }
                                            }
                                        }

                                        // 6. 게임 종료 상태 업데이트
                                        val updates = mapOf(
                                            "winner" to winner,
                                            "gameStatus" to "FINISHED"
                                        )
                                        db.collection("meetings").document(meetingId).update(updates)
                                        showEndGameDialog = false
                                    }
                            }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Button(
                            onClick = { finishGame("POLICE") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        ) {
                            Text("경찰 승리!", fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { finishGame("THIEF") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                            modifier = Modifier.weight(1f).padding(start = 8.dp)
                        ) {
                            Text("도둑 승리!", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ... (Overlay, TopInfoBar, RoleCard 등 나머지 UI 코드는 기존과 동일하므로 생략하지 않고 그대로 유지) ...
@Composable
fun GameResultOverlay(myRole: String, winnerTeam: String, onDismiss: () -> Unit) {
    val isVictory = (myRole == winnerTeam)
    val titleText = if (isVictory) "VICTORY!" else "DEFEAT"
    val subText = if (isVictory) "승리했습니다!" else "패배했습니다..."
    val textColor = if (isVictory) Color(0xFFFFD700) else Color(0xFFB0BEC5)

    val imageRes = if (isVictory) {
        if (myRole == "POLICE") R.drawable.ic_police else R.drawable.ic_map_pin
    } else {
        android.R.drawable.ic_delete
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painter = painterResource(id = imageRes), contentDescription = null, modifier = Modifier.size(120.dp))
            Spacer(modifier = Modifier.height(24.dp))
            Text(titleText, fontSize = 48.sp, fontWeight = FontWeight.Black, color = textColor, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text(subText, fontSize = 20.sp, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(40.dp))
            Text("화면을 터치하면 대기실로 돌아갑니다", fontSize = 14.sp, color = Color.Gray)
        }
    }
}

@Composable
fun TopInfoBar(policeCount: Int, thiefCount: Int, timeText: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .background(Color.White)
            .padding(bottom = 24.dp, top = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("👮‍♂️ 경찰 ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("$policeCount", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF1E88E5))
                Spacer(modifier = Modifier.width(16.dp))
                Text("|", color = Color.LightGray)
                Spacer(modifier = Modifier.width(16.dp))
                Text("💰 도둑 ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("$thiefCount", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFE53935))
            }
            Spacer(modifier = Modifier.height(16.dp))
            Box(
                modifier = Modifier.clip(RoundedCornerShape(50.dp)).background(Color(0xFFFFECB3)).padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⏰", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(timeText, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFF6F00))
                }
            }
        }
    }
}

@Composable
fun RoleCard(role: String) {
    val isPolice = (role == "POLICE")
    val cardColor = if (isPolice) Color(0xFF1E88E5) else Color(0xFFE53935)
    val roleTitle = if (isPolice) "경찰 (Police)" else "도둑 (Thief)"
    val roleDesc = if (isPolice) "도둑을 찾아 검거하세요!" else "경찰을 피해 숨으세요!"
    val imageRes = if (isPolice) R.drawable.ic_police else R.drawable.ic_map_pin

    Card(
        modifier = Modifier.fillMaxWidth(0.85f).aspectRatio(0.75f),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("이번 게임에서 당신은...", fontSize = 16.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            Text(roleTitle, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = cardColor)
            Spacer(modifier = Modifier.height(24.dp))
            Box(modifier = Modifier.size(200.dp).background(cardColor.copy(alpha = 0.15f), RoundedCornerShape(100.dp)), contentAlignment = Alignment.Center) {
                Image(painter = painterResource(id = imageRes), contentDescription = null, modifier = Modifier.size(150.dp))
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(roleDesc, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black.copy(alpha = 0.8f))
        }
    }
}

// [GameActivity] 게임 종료 시 채팅방에 결과를 쏘는 함수 예시
fun sendGameResultToChat(meetingId: String, winnerTeam: String, roles: Map<String, String>, round: Int) {
    val db = Firebase.firestore

    val winnerKr = if (winnerTeam == "POLICE") "경찰팀" else "도둑팀"
    val messageText = "$round 라운드 종료!! 승자 : $winnerKr"

    val msgData = hashMapOf(
        "type" to "GAME_RESULT", // 타입 중요!
        "senderUid" to "SYSTEM",
        "senderName" to "SYSTEM",
        "message" to messageText,
        "timestamp" to FieldValue.serverTimestamp(),
        "winnerTeam" to winnerTeam,
        "roles" to roles
    )

    db.collection("meetings").document(meetingId)
        .collection("messages").add(msgData)
}

// [GameActivity.kt] 파일 맨 아래에 이 함수를 붙여넣으세요!

// [GameActivity.kt 맨 아래]

// 레벨별 필요한 최대 경험치를 구하는 함수
fun getMaxExpForLevel(level: Int): Int {
    // 공식: 레벨 * 100 (Lv.1=100, Lv.2=200, Lv.10=1000 ...)
    // 원하는 난이도에 따라 숫자를 조절하세요 (예: level * 200)
    return level * 100
}

// 획득한 경험치를 반영하여 레벨업을 계산하는 함수
fun calculateNewLevelData(currentLevel: Int, currentExp: Int, earnedExp: Int): Pair<Int, Int> {
    var newLevel = currentLevel
    var newExp = currentExp + earnedExp

    // 현재 레벨의 최대 경험치 가져오기
    var maxExp = getMaxExpForLevel(newLevel)

    // 경험치 통이 넘치면 레벨업 (한 번에 2업 이상도 가능하도록 while 사용)
    while (newExp >= maxExp) {
        newExp -= maxExp    // 경험치 차감 (남은 경험치는 다음 레벨로 이월)
        newLevel++          // 레벨 상승
        maxExp = getMaxExpForLevel(newLevel) // 다음 레벨통 크기 갱신
    }

    return Pair(newLevel, newExp)
}