package com.example.police_and_thief

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
// 채팅 메시지 데이터 모델
data class ChatMessage(
    val senderUid: String,
    val senderName: String,
    val message: String,
    val timestamp: Date,
    val type: String = "TALK",
    val winnerTeam: String? = null, // "POLICE" or "THIEF"
    val roles: Map<String, String>? = null // { "uid1": "POLICE", "uid2": "THIEF" }
)

data class ChatUser(
    val uid: String,
    val nickname: String,
    val avatarId: String,   // DB의 "img_avatar_santa" 대응
    val accIds: List<String>, // DB의 ["img_santa_lv58", ...] 대응
    val isHost: Boolean = false
)
class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val meetingId = intent.getStringExtra("meetingId") ?: ""
        val meetingTitle = intent.getStringExtra("meetingTitle") ?: "채팅"

        setContent {
            MaterialTheme {
                ChatScreen(meetingId, meetingTitle, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(meetingId: String, meetingTitle: String, onBack: () -> Unit) {
    val db = Firebase.firestore
    val auth = Firebase.auth
    val currentUser = auth.currentUser
    var selectedResultMsg by remember { mutableStateOf<ChatMessage?>(null) }

    // --- [기존 상태 변수들] ---
    var messageText by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var myNickname by remember { mutableStateOf("익명") }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val participants = remember { mutableStateMapOf<String, ChatUser>() }
    // --- [추가된 상태 변수: 검색 관련] ---
    var isSearchMode by remember { mutableStateOf(false) } // 검색창이 켜졌는지
    var searchQuery by remember { mutableStateOf("") }     // 검색어 내용

    // --- [기존 로직들: 닉네임, 메시지 수신 등은 그대로 유지] ---
    LaunchedEffect(Unit) {
        if (currentUser != null) {
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { myNickname = it.getString("nickname") ?: "익명" }
        }
    }
    // [수정] 메시지 수신 리스너 (type, winnerTeam, roles 추가 파싱)

    // [1] 채팅 메시지 수신 (messages 컬렉션 감시)
    LaunchedEffect(meetingId) {
        if (meetingId.isNotEmpty()) {
            db.collection("meetings").document(meetingId)
                .collection("messages")
                .orderBy("timestamp")
                .addSnapshotListener { snapshot, e ->
                    if (e != null || snapshot == null) return@addSnapshotListener

                    messages.clear()
                    for (doc in snapshot.documents) {
                        val senderUid = doc.getString("senderUid") ?: ""
                        val senderName = doc.getString("senderName") ?: "알 수 없음"
                        val msg = doc.getString("message") ?: ""
                        val timestamp = doc.getDate("timestamp") ?: Date()
                        val type = doc.getString("type") ?: "TALK"
                        val winnerTeam = doc.getString("winnerTeam")
                        val roles = doc.get("roles") as? Map<String, String>

                        messages.add(ChatMessage(senderUid, senderName, msg, timestamp, type, winnerTeam, roles))
                    }

                    // 새 메시지가 오면 스크롤 내리기
                    if (messages.isNotEmpty()) {
                        scope.launch { listState.animateScrollToItem(messages.size - 1) }
                    }
                }
        }
    }

    // [2] 참여자 정보 및 역할 갱신 (meetings 문서 감시) - ★ 여기가 분리된 핵심 부분
    LaunchedEffect(meetingId) {
        if (meetingId.isNotEmpty()) {
            db.collection("meetings").document(meetingId)
                .addSnapshotListener { snapshot, e ->
                    // snapshot은 이제 'DocumentSnapshot'이라서 exists(), getString() 사용 가능!
                    if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                    // 1. 기본 참여자 명단
                    val pIds = (snapshot.get("participantIds") as? List<String>) ?: emptyList()

                    // 2. 역할 배정 명단 (게임 참가자)
                    val rolesMap = (snapshot.get("roles") as? Map<String, String>) ?: emptyMap()
                    val gameUserIds = rolesMap.keys.toList()

                    // 3. 두 명단 합치기
                    val allUids = (pIds + gameUserIds).distinct()
                    val hostUid = snapshot.getString("hostUid") ?: ""

                    // 4. 유저 정보 가져오기
                    for (uid in allUids) {
                        db.collection("users").document(uid).get().addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                val nick = userDoc.getString("nickname") ?: "알 수 없음"
                                val avName = userDoc.getString("avatarId") ?: ""
                                val rawAcc = userDoc.get("accIds")
                                val accList = when (rawAcc) {
                                    is List<*> -> rawAcc.map { it.toString() }
                                    else -> emptyList()
                                }

                                participants[uid] = ChatUser(
                                    uid = uid,
                                    nickname = nick,
                                    avatarId = avName,
                                    accIds = accList,
                                    isHost = (uid == hostUid)
                                )
                            }
                        }
                    }
                }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) { listState.animateScrollToItem(messages.size - 1) }
    }

    // ★★★ [여기서부터 중요: 오른쪽 드로어를 위한 방향 설정] ★★★
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                // 드로어 내부 내용은 다시 왼쪽에서 오른쪽(Ltr)으로 나오게 설정
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    ModalDrawerSheet(
                        modifier = Modifier.fillMaxWidth(0.7f),
                        drawerContainerColor = Color.White
                    ) {
                        // [드로어 디자인: 게임 로그 & 참여자 목록]
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                                .verticalScroll(rememberScrollState()) // ★ 스크롤 기능 추가
                                .padding(16.dp)
                        ) {
                            Text("채팅방 메뉴", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                            // 1. 게임 로그 섹션
                            Text("📜 게임 로그", modifier = Modifier.padding(vertical = 8.dp), fontWeight = FontWeight.Bold)

                            val logs = messages.filter { it.type == "SYSTEM" || it.type == "GAME_RESULT" }
                            if (logs.isEmpty()) {
                                Text("기록된 로그가 없습니다.", fontSize = 13.sp, color = Color.Gray)
                            } else {
                                // LazyColumn 대신 forEach 사용 (드로어 안에서는 이게 안전함)
                                logs.forEach { log ->
                                    Text(
                                        "• ${log.message}",
                                        fontSize = 13.sp,
                                        color = Color.DarkGray,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(24.dp))

                            // 2. 참여자 목록 섹션
                            Text("👥 참여자 목록", modifier = Modifier.padding(vertical = 8.dp), fontWeight = FontWeight.Bold)

                            participants.values.forEach { user ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                ) {
                                    SafeUserAvatar(user.avatarId, user.accIds, 40.dp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = user.nickname,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black
                                    )
                                    if (user.isHost) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("👑", fontSize = 14.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            // 실제 화면 내용도 다시 왼쪽에서 오른쪽(Ltr)으로 설정
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Scaffold(
                    topBar = {
                        // [상단바 로직: 검색 모드일 때와 아닐 때 구분]
                        if (isSearchMode) {
                            TopAppBar(
                                title = {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("대화 내용 검색") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent
                                        )
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { isSearchMode = false; searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "검색 닫기")
                                    }
                                }
                            )
                        } else {
                            TopAppBar(
                                title = { Text(meetingTitle, fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                                navigationIcon = {
                                    IconButton(onClick = onBack) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                                    }
                                },
                                actions = {
                                    IconButton(onClick = { isSearchMode = true }) {
                                        Icon(Icons.Default.Search, contentDescription = "검색")
                                    }
                                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                        Icon(Icons.Default.Menu, contentDescription = "메뉴")
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize().imePadding()
                ) { innerPadding ->
                    // --- [기존 Column, LazyColumn, Row 코드는 동일하게 유지] ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(Color(0xFFF2F4F6))
                    ) {
                        // [수정] 메시지 리스트 (타입별 분기 처리)
                        // [수정 3] 채팅 리스트 (타입에 따라 다르게 그리기)
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val filteredMessages = if (searchQuery.isEmpty()) messages
                            else messages.filter { it.message.contains(searchQuery) }

                            items(filteredMessages) { msg ->
                                when (msg.type) {
                                    "SYSTEM" -> SystemMessageBubble(msg.message)

                                    // ★★★ [이 부분이 있어야 결과 카드가 보입니다!] ★★★
                                    "GAME_RESULT" -> GameResultBubble(msg) { selectedResultMsg = msg }

                                    else -> {
                                        val isMe = (msg.senderUid == currentUser?.uid)
                                        MessageBubble(msg, isMe)
                                    }
                                }
                            }
                        }// [추가] 상세 결과 다이얼로그 (Scaffold 내부, Column 밖)
                        if (selectedResultMsg != null) {
                            GameResultDetailDialog(
                                message = selectedResultMsg!!,
                                participants = participants,
                                onDismiss = { selectedResultMsg = null }
                            )
                        }

                        // 입력창 부분 (기존과 동일)
                        Row(
                            modifier = Modifier.fillMaxWidth().background(Color.White).padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextField(
                                value = messageText,
                                onValueChange = { messageText = it },
                                placeholder = { Text("메시지를 입력하세요") },
                                modifier = Modifier.weight(1f),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent
                                )
                            )
                            IconButton(
                                onClick = {
                                    if (messageText.isNotBlank() && currentUser != null) {
                                        val inputMsg = messageText
                                        messageText = ""
                                        val msgData = hashMapOf(
                                            "senderUid" to currentUser.uid,
                                            "senderName" to myNickname,
                                            "message" to inputMsg,
                                            "timestamp" to FieldValue.serverTimestamp()
                                        )
                                        db.collection("meetings").document(meetingId)
                                            .collection("messages").add(msgData)
                                    }
                                },
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .background(Color(0xFFFFD700), shape = RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "전송", tint = Color.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun MessageBubble(msg: ChatMessage, isMe: Boolean) {
    val sdf = SimpleDateFormat("a h:mm", Locale.getDefault()) // 예: 오후 3:15
    val timeStr = sdf.format(msg.timestamp)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
    ) {
        // 상대방 이름 (내가 아닐 때만 표시)
        if (!isMe) {
            Text(
                text = msg.senderName,
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
        }

        Row(
            verticalAlignment = Alignment.Bottom
        ) {
            // 내가 보낸 메시지면 시간 먼저 표시 (왼쪽)
            if (isMe) {
                Text(
                    text = timeStr,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }

            // 말풍선
            Box(
                modifier = Modifier
                    .background(
                        color = if (isMe) Color(0xFFFFE082) else Color.White, // 나: 노란색, 상대: 흰색
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                            bottomStart = if (isMe) 12.dp else 0.dp,
                            bottomEnd = if (isMe) 0.dp else 12.dp
                        )
                    )
                    .padding(10.dp)
                    .widthIn(max = 260.dp) // 말풍선 최대 너비 제한
            ) {
                Text(msg.message, fontSize = 15.sp, color = Color.Black)
            }

            // 상대방 메시지면 시간 나중에 표시 (오른쪽)
            if (!isMe) {
                Text(
                    text = timeStr,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SafeUserAvatar(avatarName: String, accNames: List<String>, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current

    // Box는 내부의 요소들을 겹쳐서 보여줍니다.
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFFE0E0E0)),
        contentAlignment = Alignment.Center
    ) {
        // 1. 밑바탕: 아바타 몸체 (예: img_avatar_santa)
        val avatarRes = getSafeDrawableId(context, avatarName)
        if (avatarRes != 0) {
            Image(
                painter = painterResource(id = avatarRes),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. 그 위에 겹치기: 악세사리들 리스트를 돌면서 하나씩 위에 쌓음
        accNames.forEach { accName ->
            val accRes = getSafeDrawableId(context, accName)
            if (accRes != 0) {
                Image(
                    painter = painterResource(id = accRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// 파일 이름을 숫자로 바꿔주는 도우미 함수 (절대 튕기지 않게 방어)
@SuppressLint("DiscouragedApi")
fun getSafeDrawableId(context: Context, name: String): Int {
    if (name.isEmpty()) return 0
    return try {
        // 이 한 줄이 "img_avatar_santa"라는 글자를 안드로이드 리소스 ID로 바꿔줍니다.
        context.resources.getIdentifier(name, "drawable", context.packageName)
    } catch (e: Exception) { 0 }
}

// ---------------------------------------------------------
// [UI 컴포넌트 1] 시스템 메시지 (가운데 회색 텍스트)
// ---------------------------------------------------------
@Composable
fun SystemMessageBubble(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier
                .background(Color(0xFFE0E0E0), shape = RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

// ---------------------------------------------------------
// [UI 컴포넌트 2] 게임 결과 버블 (클릭 가능한 카드)
// ---------------------------------------------------------
@Composable
fun GameResultBubble(msg: ChatMessage, onClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎮 게임 결과 알림", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(4.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(0.8f).clickable { onClick() }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(msg.message, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.Black)
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
            }
        }
    }
}

// ---------------------------------------------------------
// [UI 컴포넌트 3] 상세 결과 다이얼로그 (탭 기능 포함)
// ---------------------------------------------------------
@Composable
fun GameResultDetailDialog(
    message: ChatMessage,
    participants: Map<String, ChatUser>,
    onDismiss: () -> Unit
) {
    var showWinnerTeam by remember { mutableStateOf(true) }

    val winnerTeamCode = message.winnerTeam ?: "POLICE"
    val loserTeamCode = if (winnerTeamCode == "POLICE") "THIEF" else "POLICE"
    val currentTeamCode = if (showWinnerTeam) winnerTeamCode else loserTeamCode

    // 현재 탭에 해당하는 유저 리스트 필터링
    val teamUsers = participants.values.filter { user ->
        val userRole = message.roles?.get(user.uid)
        userRole == currentTeamCode
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(500.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("게임 상세 결과", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // [탭 버튼]
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { showWinnerTeam = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showWinnerTeam) Color(0xFFFFD700) else Color(0xFFF0F0F0),
                            contentColor = if (showWinnerTeam) Color.Black else Color.Gray
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp, topEnd = 0.dp, bottomEnd = 0.dp)
                    ) { Text("🏆 승리팀") }

                    Button(
                        onClick = { showWinnerTeam = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!showWinnerTeam) Color.Gray else Color(0xFFF0F0F0),
                            contentColor = if (!showWinnerTeam) Color.White else Color.Gray
                        ),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp)
                    ) { Text("패배팀") }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 팀 이름
                val teamNameKr = if (currentTeamCode == "POLICE") "경찰팀 (Police)" else "도둑팀 (Thief)"
                val teamColor = if (currentTeamCode == "POLICE") Color(0xFF1E88E5) else Color(0xFFE53935)

                Text(teamNameKr, color = teamColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // [유저 리스트]
                LazyColumn {
                    items(teamUsers) { user ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
                        ) {
                            SafeUserAvatar(user.avatarId, user.accIds, 40.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(user.nickname, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (teamUsers.isEmpty()) {
                        item {
                            Text("해당 팀 정보가 없습니다.", modifier = Modifier.padding(16.dp), color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}