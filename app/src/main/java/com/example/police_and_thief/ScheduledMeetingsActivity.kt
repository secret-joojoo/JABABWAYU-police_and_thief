package com.example.police_and_thief

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// [데이터 모델] 리스트에 보여줄 정보
data class ScheduledMeeting(
    val id: String,
    val title: String,
    val dateString: String, // "2026-01-13 14:30" 형식
    val placeName: String,
    val hostUid: String,
    val currentMembers: Int,
    val maxMembers: Int
)

class ScheduledMeetingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                ScheduledMeetingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun ScheduledMeetingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    // [상태 변수]
    var meetingList by remember { mutableStateOf(emptyList<ScheduledMeeting>()) }
    var isLoading by remember { mutableStateOf(true) }

    // 팝업용 선택된 모임 (null이면 팝업 안 뜸)
    var selectedMeeting by remember { mutableStateOf<ScheduledMeeting?>(null) }

    // 배경 블러 애니메이션 (팝업이 뜨면 15.dp, 아니면 0.dp)
    val blurRadius by animateDpAsState(
        targetValue = if (selectedMeeting != null) 15.dp else 0.dp,
        label = "blur"
    )

    // [데이터 불러오기]
    LaunchedEffect(Unit) {
        if (currentUser != null) {
            // "participantIds" 배열에 내 uid가 포함된 모임만 찾기
            db.collection("meetings")
                .whereArrayContains("participantIds", currentUser.uid)
                .get()
                .addOnSuccessListener { result ->
                    val list = result.documents.mapNotNull { doc ->
                        val title = doc.getString("title") ?: ""
                        val date = doc.getString("dateString") ?: ""
                        val place = doc.getString("placeName") ?: ""
                        val host = doc.getString("hostUid") ?: ""
                        val participants = doc.get("participantIds") as? List<String> ?: emptyList()
                        val max = doc.getLong("maxParticipants")?.toInt() ?: 0

                        // 현재 날짜보다 지난 모임은 제외할 수도 있지만, 일단은 다 보여줌
                        ScheduledMeeting(
                            id = doc.id,
                            title = title,
                            dateString = date,
                            placeName = place,
                            hostUid = host,
                            currentMembers = participants.size,
                            maxMembers = max
                        )
                    }
                    // ★ 날짜순 정렬 (String 비교로도 "YYYY-MM-DD HH:MM" 포맷은 정렬 가능)
                    meetingList = list.sortedBy { it.dateString }
                    isLoading = false
                }
                .addOnFailureListener {
                    Toast.makeText(context, "데이터를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                    isLoading = false
                }
        }
    }

    // === [UI 구성] Box를 써서 팝업을 위에 겹칠 준비 ===
    Box(modifier = Modifier.fillMaxSize()) {

        // 1. 메인 컨텐츠 (블러 효과 적용 대상)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius) // ★ 여기가 핵심! 선택되면 흐려짐
                .background(Color.White)
                .systemBarsPadding()
        ) {
            // (1) 헤더
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = Color.Black)
                }
                Text("예정된 모임 보기", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Divider(color = Color.LightGray)

            // (2) 리스트 영역
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.Black)
                }
            } else if (meetingList.isEmpty()) {
                // 모임이 없을 때 (회색 글씨)
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("예정된 모임이 없습니다.", color = Color.Gray, fontSize = 16.sp)
                }
            } else {
                // 모임 리스트
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(meetingList) { meeting ->
                        ScheduledMeetingItem(
                            meeting = meeting,
                            onClick = { selectedMeeting = meeting } // 클릭 시 팝업 변수에 할당
                        )
                    }
                }
            }
        }

        // 2. 팝업 레이어 (선택된 모임이 있을 때만 화면 중앙에 뜸)
        if (selectedMeeting != null) {
            // 반투명 검은 배경 (클릭 시 닫힘)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.2f)) // 살짝 어둡게
                    .clickable { selectedMeeting = null }, // 바깥 누르면 닫기
                contentAlignment = Alignment.Center
            ) {
                // 팝업 내용물 (클릭 이벤트 전파 방지)
                MeetingDetailPopup(
                    meeting = selectedMeeting!!,
                    onClose = { selectedMeeting = null }
                )
            }
        }
    }
}

// [리스트 아이템 디자인] - 그림에 있는 긴 네모 박스
@Composable
fun ScheduledMeetingItem(meeting: ScheduledMeeting, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.Black), // 그림처럼 검은 테두리
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick) // 클릭 감지
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // 날짜와 시간
                Text(
                    text = meeting.dateString,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 모임 이름
                Text(
                    text = meeting.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            // 장소 (오른쪽 끝)
            Text(
                text = meeting.placeName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.DarkGray
            )
        }
    }
}

// [상세 팝업 디자인]
@Composable
fun MeetingDetailPopup(meeting: ScheduledMeeting, onClose: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxWidth(0.85f) // 화면 너비의 85% 차지
            .clickable(enabled = false) {} // 팝업 내부 클릭 시 닫히지 않게 막음
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // 헤더: 제목 + 닫기 버튼
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("모임 상세 정보", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "닫기")
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 정보들
            DetailRow(icon = Icons.Default.DateRange, text = meeting.dateString)
            Spacer(modifier = Modifier.height(12.dp))
            DetailRow(icon = Icons.Default.Place, text = meeting.placeName)

            Spacer(modifier = Modifier.height(24.dp))

            // 인원 현황 바
            Text("참여 인원 (${meeting.currentMembers}/${meeting.maxMembers})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = meeting.currentMembers / meeting.maxMembers.toFloat(),
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Color.Black,
                trackColor = Color.LightGray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 확인 버튼
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
            ) {
                Text("확인", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// 아이콘 + 텍스트 한 줄 helper
@Composable
fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, fontSize = 16.sp)
    }
}