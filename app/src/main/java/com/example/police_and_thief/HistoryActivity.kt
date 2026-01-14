package com.example.police_and_thief

import android.util.Log
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.*

// [데이터 모델] 전적 정보
data class GameHistory(
    val id: String,
    val meetingTitle: String,
    val playedAt: Date,
    val myRole: String,    // "POLICE" or "THIEF"
    val winnerTeam: String,// "POLICE" or "THIEF"
    val isWin: Boolean,
    val gameDuration: Int, // 게임 시간(분)
    val policeCount: Int,  // 경찰 수
    val thiefCount: Int,   // 도둑 수
    val fullRoles: Map<String, String> // 전체 역할 정보 (상세보기용)
)

class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HistoryScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit) {
    val db = Firebase.firestore
    val currentUser = Firebase.auth.currentUser
    val historyList = remember { mutableStateListOf<GameHistory>() }
    var isLoading by remember { mutableStateOf(true) }

    // 상세보기 다이얼로그 상태
    var selectedHistory by remember { mutableStateOf<GameHistory?>(null) }

// ★ [추가됨] 내 프로필 정보를 담을 변수들
    var myNickname by remember { mutableStateOf("로딩중...") }
    var myLevel by remember { mutableIntStateOf(1) }
    var myAvatarId by remember { mutableStateOf("img_avatar_police") }
    var myAccIds by remember { mutableStateOf<List<String>>(emptyList()) }
    // 전적 통계 계산
    val totalGames = historyList.size
    val winCount = historyList.count { it.isWin }
    val loseCount = totalGames - winCount
    val winRate = if (totalGames > 0) (winCount.toFloat() / totalGames * 100).toInt() else 0

    // 데이터 로드
    // 데이터 로드
    // 데이터 로드
    LaunchedEffect(Unit) {
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { doc ->
                    if (doc.exists()) {
                        myNickname = doc.getString("nickname") ?: "알 수 없음"
                        myLevel = doc.getLong("level")?.toInt() ?: 1

                        // 아바타 정보 (문자열로 가져오기!)
                        myAvatarId = doc.getString("avatarId") ?: "img_avatar_police"
                        val rawAcc = doc.get("accIds")
                        myAccIds = when (rawAcc) {
                            is List<*> -> rawAcc.map { it.toString() }
                            else -> emptyList()
                        }

                        db.collection("meetings")
                            .whereArrayContains("participantIds", currentUser.uid)
                            .get()
                            .addOnSuccessListener { meetingSnapshots ->
                                if (meetingSnapshots.isEmpty) {
                                    isLoading = false
                                    return@addOnSuccessListener
                                }

                                var processedCount = 0
                                val totalMeetings = meetingSnapshots.size()

                                for (meetingDoc in meetingSnapshots) {
                                    val meetingTitle = meetingDoc.getString("title") ?: "모임"
                                    // 방장이 설정했던 최대 시간
                                    val hostSetTime = meetingDoc.getLong("gameTime")?.toInt() ?: 15

                                    db.collection("meetings").document(meetingDoc.id)
                                        .collection("game_history")
                                        .get()
                                        .addOnSuccessListener { historySnaps ->
                                            for (gameDoc in historySnaps) {
                                                val roles =
                                                    gameDoc.get("roles") as? Map<String, String>
                                                        ?: emptyMap()
                                                val myRole = roles[currentUser.uid] ?: "SPECTATOR"
                                                val winner = gameDoc.getString("winner") ?: ""
                                                val playedAt = gameDoc.getDate("playedAt") ?: Date()

                                                // [1] 실제 플레이 시간 가져오기
                                                val actualPlayTime =
                                                    gameDoc.getLong("actualPlayTime")?.toInt()

                                                // [2] 실제 시간이 있으면 그걸 쓰고, 없으면(옛날 기록) 방장 설정 시간을 씀
                                                val finalDuration = actualPlayTime ?: hostSetTime

                                                if (myRole == "POLICE" || myRole == "THIEF") {
                                                    val isWin = (myRole == winner)
                                                    val pCount =
                                                        roles.values.count { it == "POLICE" }
                                                    val tCount =
                                                        roles.values.count { it == "THIEF" }

                                                    historyList.add(
                                                        GameHistory(
                                                            id = gameDoc.id,
                                                            meetingTitle = meetingTitle,
                                                            playedAt = playedAt,
                                                            myRole = myRole,
                                                            winnerTeam = winner,
                                                            isWin = isWin,

                                                            // ★ [수정됨] 무조건 여기엔 finalDuration을 넣어야 합니다!
                                                            gameDuration = finalDuration,

                                                            policeCount = pCount,
                                                            thiefCount = tCount,
                                                            fullRoles = roles
                                                        )
                                                    )
                                                }
                                            }

                                            processedCount++
                                            if (processedCount == totalMeetings) {
                                                historyList.sortByDescending { it.playedAt }
                                                isLoading = false
                                            }
                                        }
                                        .addOnFailureListener {
                                            processedCount++
                                            if (processedCount == totalMeetings) isLoading = false
                                        }
                                }
                            }
                            .addOnFailureListener {
                                isLoading = false
                            }
                    }
                }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("나의 전적", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        containerColor = Color(0xFFEBEEF1) // OP.GG 스타일 연한 회색 배경
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize().padding(16.dp)) {

            // [1] 상단 요약 카드 (OP.GG 스타일)
            if (!isLoading) {
                ProfileSummaryCard(
                    // 1. 프로필 정보 (DB에서 가져온 변수들)
                    nickname = myNickname,
                    level = myLevel,
                    avatarId = myAvatarId,
                    accIds = myAccIds,

                    // 2. 전적 통계 정보 (계산된 숫자 변수들)
                    total = totalGames,
                    win = winCount,
                    lose = loseCount,
                    rate = winRate
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // [2] 리스트
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (historyList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("기록이 없습니다.", color = Color.Gray) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(historyList) { history ->
                        GameHistoryItem(history) { selectedHistory = history }
                    }
                }
            }
        }

        // [3] 상세 다이얼로그
        if (selectedHistory != null) {
            HistoryDetailDialog(selectedHistory!!) { selectedHistory = null }
        }
    }
}

// ---------------------------------------------------------
// [UI 1] 상단 요약 카드 (도넛 차트 느낌의 텍스트 배치)
// ---------------------------------------------------------
// ---------------------------------------------------------
// [UI 1] 상단 프로필 & 전적 요약 카드 (업그레이드 버전)
// ---------------------------------------------------------
@Composable
fun ProfileSummaryCard(
    nickname: String,
    level: Int,
    avatarId: String,
    accIds: List<String>,
    total: Int,
    win: Int,
    lose: Int,
    rate: Int
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // [왼쪽] 아바타 & 레벨 & 닉네임
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(end = 16.dp)
            ) {
                // 아바타 (기존에 만들어둔 함수 사용)
                historyUserAvatar(avatarId, accIds, 70.dp)

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Lv.$level",
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .background(Color.Black, RoundedCornerShape(8.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = nickname,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // [가운데] 구분선
            Divider(
                modifier = Modifier
                    .width(1.dp)
                    .height(80.dp),
                color = Color(0xFFEEEEEE)
            )

            // [오른쪽] 전적 & 승률
            Column(
                modifier = Modifier.weight(1f).padding(start = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("승률", fontSize = 12.sp, color = Color.Gray)

                // 승률 텍스트 (승률이 높으면 파란색, 낮으면 빨간색)
                Text(
                    text = "$rate%",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Black,
                    color = if(rate >= 50) Color(0xFF5383E8) else Color(0xFFE84057)
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 상세 전적
                Text(
                    text = "${total}전 ${win}승 ${lose}패",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.DarkGray
                )
            }
        }
    }
}

// ---------------------------------------------------------
// [UI 2] 리스트 아이템 (OP.GG 스타일 - 왼쪽 컬러바 + 정보)
// ---------------------------------------------------------
@Composable
fun GameHistoryItem(history: GameHistory, onClick: () -> Unit) {
    // 승리: 파랑(5383E8), 패배: 빨강(E84057)
    val mainColor = if (history.isWin) Color(0xFF5383E8) else Color(0xFFE84057)
    val bgColor = if (history.isWin) Color(0xFFECF2FF) else Color(0xFFFFF1F3)
    val resultText = if (history.isWin) "승리" else "패배"

    val sdf = SimpleDateFormat("MM-dd", Locale.getDefault())
    val roleIcon = if(history.myRole == "POLICE") R.drawable.ic_police else R.drawable.ic_map_pin // 아이콘 없으면 수정 필요

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        shape = RoundedCornerShape(4.dp), // 각진 느낌
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 1. 왼쪽 컬러바 (승패 표시)
            Box(modifier = Modifier.width(6.dp).fillMaxHeight().background(mainColor))

            // 2. 내용물
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // [승패 & 날짜]
                Column(modifier = Modifier.width(60.dp)) {
                    Text(resultText, fontWeight = FontWeight.Bold, color = mainColor, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(sdf.format(history.playedAt), fontSize = 11.sp, color = Color.Gray)
                }

                // [역할 아이콘 & 모임명]
                Box(
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Image(painter = painterResource(id = roleIcon), contentDescription = null, modifier = Modifier.size(28.dp))
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(history.meetingTitle, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                    Text("${history.gameDuration}분 게임", fontSize = 12.sp, color = Color.Gray)
                }

                // [인원 비율]
                Column(horizontalAlignment = Alignment.End) {
                    Text("경찰 ${history.policeCount} : 도둑 ${history.thiefCount}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("총 ${history.policeCount + history.thiefCount}명", fontSize = 11.sp, color = Color.Gray)
                }
            }
        }
    }
}

// ---------------------------------------------------------
// [UI 3] 상세 다이얼로그 (유저 정보 실시간 로딩)
// ---------------------------------------------------------
@Composable
fun HistoryDetailDialog(history: GameHistory, onDismiss: () -> Unit) {
    val db = Firebase.firestore
    var showWinnerTeam by remember { mutableStateOf(true) }

    // 유저 정보를 불러와야 하므로 상태 관리
    var userList by remember { mutableStateOf<List<ChatUser>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    val winnerTeamCode = history.winnerTeam
    val targetTeamCode = if (showWinnerTeam) winnerTeamCode else (if (winnerTeamCode == "POLICE") "THIEF" else "POLICE")

    // 탭이 바뀔 때마다 해당 팀 유저 정보 로딩
    LaunchedEffect(targetTeamCode) {
        isLoading = true
        userList = emptyList()

        // 현재 팀에 해당하는 UID들만 추출
        val targetUids = history.fullRoles.filterValues { it == targetTeamCode }.keys.toList()

        if (targetUids.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }

        // Firestore에서 유저 정보 가져오기
        val tempList = mutableListOf<ChatUser>()
        var loadedCount = 0

        for (uid in targetUids) {
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val nick = doc.getString("nickname") ?: "알 수 없음"
                    val avName = doc.getString("avatarId") ?: ""
                    val rawAcc = doc.get("accIds")
                    val accList = when (rawAcc) {
                        is List<*> -> rawAcc.map { it.toString() }
                        else -> emptyList()
                    }
                    tempList.add(ChatUser(uid, nick, avName, accList))
                }
                loadedCount++
                if (loadedCount == targetUids.size) {
                    userList = tempList
                    isLoading = false
                }
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(500.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("게임 상세 결과", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(16.dp))

                // 탭 버튼
                Row(modifier = Modifier.fillMaxWidth()) {
                    TabButton("🏆 승리팀", showWinnerTeam) { showWinnerTeam = true }
                    TabButton("패배팀", !showWinnerTeam) { showWinnerTeam = false }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // 팀 타이틀
                val teamName = if (targetTeamCode == "POLICE") "경찰팀" else "도둑팀"
                val teamColor = if (targetTeamCode == "POLICE") Color(0xFF5383E8) else Color(0xFFE84057)
                Text(teamName, color = teamColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))

                Divider(modifier = Modifier.padding(vertical = 12.dp))

                // 유저 리스트
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn {
                        items(userList) { user ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                                historyUserAvatar(user.avatarId, user.accIds, 40.dp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(user.nickname, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RowScope.TabButton(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isSelected) Color(0xFF5383E8) else Color(0xFFF2F4F6),
            contentColor = if (isSelected) Color.White else Color.Gray
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
    ) {
        Text(text)
    }
}

// ---------------------------------------------------------
// [Helper] 아바타 함수 (ChatActivity에서 복사해와서 쓰거나 공통으로 빼도 됨)
// ---------------------------------------------------------
@Composable
fun historyUserAvatar(avatarName: String, accNames: List<String>, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(Color(0xFFE0E0E0)),
        contentAlignment = Alignment.Center
    ) {
        val avatarRes = historyDrawableId(context, avatarName)
        if (avatarRes != 0) Image(painter = painterResource(avatarRes), null, Modifier.fillMaxSize())
        accNames.forEach {
            val accRes = historyDrawableId(context, it)
            if (accRes != 0) Image(painter = painterResource(accRes), null, Modifier.fillMaxSize())
        }
    }
}

@SuppressLint("DiscouragedApi")
fun historyDrawableId(context: Context, name: String): Int {
    if (name.isEmpty()) return 0
    return try {
        context.resources.getIdentifier(name, "drawable", context.packageName)
    } catch (e: Exception) { 0 }
}