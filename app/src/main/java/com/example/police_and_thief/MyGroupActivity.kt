package com.example.police_and_thief

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import java.util.Calendar
import java.util.Date
import java.util.Locale

// [데이터 모델]
data class MeetingData(
    val docId: String,
    val name: String,
    val dateString: String,
    val ageRange: String,
    val maxMember: Int,
    val participants: List<String>,
    val hasAfterParty: Boolean,
    val mannerCutoff: Double,
    val gameTime: Int,
    val totalRounds: Int,
    val hostUid: String,
    val isLive: Boolean // ★ [추가] 진행 중(시작 시간 지남) 여부
) {
    val currentMember: Int
        get() = participants.size
}

data class ParticipantData(
    val nickname: String,
    val level: Int,
    val avatarResId: Int,
    val accResIds: List<Int>
)

class MyGroupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MyGroupScreen()
            }
        }
    }
}

@Composable
fun MyGroupScreen() {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("생성한 모임", "참가할 모임")

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.White,
                contentColor = Color.Black,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = Color.Black
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                when (selectedTabIndex) {
                    0 -> CreatedGroupsContent()
                    1 -> ParticipatingGroupsContent()
                }
            }
        }
    }
}

@Composable
fun CreatedGroupsContent() {
    val db = Firebase.firestore
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    val meetings = remember { mutableStateListOf<MeetingData>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (currentUser != null) {
            db.collection("meetings")
                .whereEqualTo("hostUid", currentUser.uid)
                .get()
                .addOnSuccessListener { result ->
                    meetings.clear()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val now = Date()

                    for (document in result) {
                        val dateStr = document.getString("dateString") ?: ""
                        val meetingDate = try { sdf.parse(dateStr) } catch (e: Exception) { null }
                        val status = document.getString("meetingStatus")
                        if (status == "ENDED") continue

                        if (meetingDate != null) {
                            // ★ [수정] 12시간 뒤 시간 계산
                            val cal = Calendar.getInstance()
                            cal.time = meetingDate
                            cal.add(Calendar.HOUR_OF_DAY, 12)
                            val endTime = cal.time

                            // ★ [수정] 현재 시간이 (시작시간 + 12시간) 이전이면 표시
                            if (now.before(endTime)) {
                                val minAge = document.getLong("minAge")?.toInt() ?: 0
                                val maxAge = document.getLong("maxAge")?.toInt() ?: 99

                                // ★ 진행 중인지 확인 (시작시간 지났으면 true)
                                val isLive = now.after(meetingDate)

                                val data = MeetingData(
                                    docId = document.id,
                                    name = document.getString("title") ?: "제목 없음",
                                    dateString = dateStr,
                                    ageRange = "${minAge}~${maxAge}세",
                                    maxMember = document.getLong("maxParticipants")?.toInt() ?: 4,
                                    participants = (document.get("participantIds") as? List<String>) ?: emptyList(),
                                    hasAfterParty = document.getBoolean("hasAfterParty") ?: false,
                                    mannerCutoff = document.getDouble("mannerTempCutline") ?: 50.0,
                                    gameTime = document.getLong("gameTimePerRound")?.toInt() ?: 15,
                                    totalRounds = document.getLong("totalRounds")?.toInt() ?: 3,
                                    hostUid = document.getString("hostUid") ?: "",
                                    isLive = isLive // 상태 저장
                                )
                                meetings.add(data)
                            }
                        }
                    }
                    meetings.sortBy { it.dateString }
                    isLoading = false
                }
                .addOnFailureListener { isLoading = false }
        } else {
            isLoading = false
        }
    }

    if (isLoading) {
        CircularProgressIndicator(color = Color.Black)
    } else if (meetings.isEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top=50.dp)) {
            Text("예정된 모임이 없습니다.", color = Color.Gray)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(meetings) { meeting ->
                MeetingCard(meeting)
            }
        }
    }
}

@Composable
fun ParticipatingGroupsContent() {
    val db = Firebase.firestore
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    val meetings = remember { mutableStateListOf<MeetingData>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        if (currentUser != null) {
            db.collection("meetings")
                .whereArrayContains("participantIds", currentUser.uid)
                .get()
                .addOnSuccessListener { result ->
                    meetings.clear()
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val now = Date()

                    for (document in result) {
                        val hostUid = document.getString("hostUid") ?: ""

                        if (hostUid != currentUser.uid) {
                            val status = document.getString("meetingStatus")
                            if (status == "ENDED") continue

                            val dateStr = document.getString("dateString") ?: ""
                            val meetingDate = try { sdf.parse(dateStr) } catch (e: Exception) { null }

                            if (meetingDate != null) {
                                // ★ [수정] 12시간 뒤 시간 계산
                                val cal = Calendar.getInstance()
                                cal.time = meetingDate
                                cal.add(Calendar.HOUR_OF_DAY, 12)
                                val endTime = cal.time

                                // ★ [수정] 현재 시간이 (시작시간 + 12시간) 이전이면 표시
                                if (now.before(endTime)) {
                                    val minAge = document.getLong("minAge")?.toInt() ?: 0
                                    val maxAge = document.getLong("maxAge")?.toInt() ?: 99

                                    // ★ 진행 중인지 확인
                                    val isLive = now.after(meetingDate)

                                    val data = MeetingData(
                                        docId = document.id,
                                        name = document.getString("title") ?: "제목 없음",
                                        dateString = dateStr,
                                        ageRange = "${minAge}~${maxAge}세",
                                        maxMember = document.getLong("maxParticipants")?.toInt() ?: 4,
                                        participants = (document.get("participantIds") as? List<String>) ?: emptyList(),
                                        hasAfterParty = document.getBoolean("hasAfterParty") ?: false,
                                        mannerCutoff = document.getDouble("mannerTempCutline") ?: 50.0,
                                        gameTime = document.getLong("gameTimePerRound")?.toInt() ?: 15,
                                        totalRounds = document.getLong("totalRounds")?.toInt() ?: 3,
                                        hostUid = hostUid,
                                        isLive = isLive // 상태 저장
                                    )
                                    meetings.add(data)
                                }
                            }
                        }
                    }
                    meetings.sortBy { it.dateString }
                    isLoading = false
                }
                .addOnFailureListener { isLoading = false }
        } else {
            isLoading = false
        }
    }

    if (isLoading) {
        CircularProgressIndicator(color = Color.Black)
    } else if (meetings.isEmpty()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top=50.dp)) {
            Text("참가 신청한 예정된 모임이 없습니다.", color = Color.Gray)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(meetings) { meeting ->
                MeetingCard(meeting)
            }
        }
    }
}

@Composable
fun MeetingCard(meeting: MeetingData) {
    val context = LocalContext.current
    var showParticipantDialog by remember { mutableStateOf(false) }

    if (showParticipantDialog) {
        ParticipantListDialog(participantUids = meeting.participants) {
            showParticipantDialog = false
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showParticipantDialog = true }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // [상단] 제목, 초록점, 인원수
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // [좌측] 제목 + 진행 중 표시
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(meeting.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    if (meeting.isLive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Green)
                        )
                    }
                }

                // [우측] 인원수 (버튼 제거됨)
                Text(
                    text = "${meeting.currentMember}/${meeting.maxMember}명",
                    fontSize = 16.sp,
                    color = Color(0xFF0066FF),
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.LightGray, thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // [정보 영역 1] 일시 + 채팅 버튼 (같은 줄 배치)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 좌측: 일시 정보
                InfoRow("일시", meeting.dateString)

                // 우측: 채팅 버튼 (여기로 이동!)
                Button(
                    onClick = {
                        // ★ [수정] ChatActivity로 이동하는 코드 추가
                        val intent = android.content.Intent(context, ChatActivity::class.java)
                        intent.putExtra("meetingId", meeting.docId)     // 어떤 모임인지 ID 전달
                        intent.putExtra("meetingTitle", meeting.name)   // 상단바에 띄울 제목 전달
                        context.startActivity(intent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("채팅", fontSize = 12.sp, color = Color.White)
                }
            }

            // [정보 영역 2] 나머지 정보들
            InfoRow("참여 가능", meeting.ageRange)
            InfoRow("게임 설정", "라운드당 ${meeting.gameTime}분 / 총 ${meeting.totalRounds}라운드")

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                InfoRow("뒷풀이", if (meeting.hasAfterParty) "있음 O" else "없음 X")
                InfoRow("신용도", "${meeting.mannerCutoff} 이상")
            }
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.padding(bottom = 4.dp)) {
        Text(text = "$label : ", fontSize = 14.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
        Text(text = value, fontSize = 14.sp, color = Color.Black)
    }
}

// [다이얼로그] 참가자 리스트
@Composable
fun ParticipantListDialog(participantUids: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val db = Firebase.firestore

    val participantsDetails = remember { mutableStateListOf<ParticipantData>() }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(participantUids) {
        participantsDetails.clear()
        if (participantUids.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }

        var fetchCount = 0
        participantUids.forEach { uid ->
            db.collection("users").document(uid).get().addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val nickname = doc.getString("nickname") ?: "알 수 없음"
                    val level = doc.getLong("level")?.toInt() ?: 1
                    val avatarStr = doc.getString("avatarId") ?: "img_avatar_police"

                    val resId = context.resources.getIdentifier(avatarStr, "drawable", context.packageName)
                    val avatarResId = if (resId != 0) resId else R.drawable.img_avatar_police

                    val accList = mutableListOf<Int>()
                    val savedAccIds = doc.get("accIds") as? List<String>
                    savedAccIds?.forEach { idStr ->
                        val accResId = context.resources.getIdentifier(idStr, "drawable", context.packageName)
                        if (accResId != 0) accList.add(accResId)
                    }

                    participantsDetails.add(ParticipantData(nickname, level, avatarResId, accList))
                }
                fetchCount++
                if (fetchCount == participantUids.size) {
                    isLoading = false
                }
            }.addOnFailureListener {
                fetchCount++
                if (fetchCount == participantUids.size) isLoading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("현재 참가자 목록", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(30.dp), color = Color.Black)
                } else if (participantsDetails.isEmpty()) {
                    Text("참가자가 아직 없습니다.", color = Color.Gray)
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.height(200.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(participantsDetails) { person ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(60.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = person.avatarResId),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    person.accResIds.forEach { accId ->
                                        Image(
                                            painter = painterResource(id = accId),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(person.nickname, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text("Lv.${person.level}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text("닫기", color = Color.White)
                }
            }
        }
    }
}