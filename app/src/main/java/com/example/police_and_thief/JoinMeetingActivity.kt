package com.example.police_and_thief

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Locale

// [데이터 모델]
data class MeetingItem(
    val id: String,
    val title: String,
    val placeName: String,
    val dateString: String,
    val hostUid: String,
    val currentCount: Int,
    val maxParticipants: Int,
    val minAge: Int,
    val maxAge: Int,
    val hasAfterParty: Boolean,
    val mannerTempCutline: Float,
    val gameTime: Int,
    val totalRounds: Int,
    val participantIds: List<String>
)

// [데이터] 대한민국 행정구역 데이터 (기존과 동일하여 생략 가능하지만 안전하게 포함)
val koreaRegionData = mapOf(
    "전체" to emptyList(),
    "서울특별시" to listOf("전체", "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구", "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구", "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구"),
    "경기도" to listOf("전체", "수원시", "고양시", "용인시", "성남시", "부천시", "화성시", "안산시", "남양주시", "안양시", "평택시", "시흥시", "파주시", "의정부시", "김포시", "광주시", "광명시", "군포시", "하남시", "오산시", "양주시", "이천시", "구리시", "안성시", "포천시", "의왕시", "양평군", "여주시", "동두천시", "가평군", "과천시", "연천군"),
    "부산광역시" to listOf("전체", "중구", "서구", "동구", "영도구", "부산진구", "동래구", "남구", "북구", "해운대구", "사하구", "금정구", "강서구", "연제구", "수영구", "사상구", "기장군"),
    "인천광역시" to listOf("전체", "중구", "동구", "미추홀구", "연수구", "남동구", "부평구", "계양구", "서구", "강화군", "옹진군"),
    "대구광역시" to listOf("전체", "중구", "동구", "서구", "남구", "북구", "수성구", "달서구", "달성군", "군위군"),
    "대전광역시" to listOf("전체", "동구", "중구", "서구", "유성구", "대덕구"),
    "광주광역시" to listOf("전체", "동구", "서구", "남구", "북구", "광산구"),
    "울산광역시" to listOf("전체", "중구", "남구", "동구", "북구", "울주군"),
    "세종특별자치시" to listOf("전체"),
    "강원특별자치도" to listOf("전체", "춘천시", "원주시", "강릉시", "동해시", "태백시", "속초시", "삼척시", "홍천군", "횡성군", "영월군", "평창군", "정선군", "철원군", "화천군", "양구군", "인제군", "고성군", "양양군"),
    "충청북도" to listOf("전체", "청주시", "충주시", "제천시", "보은군", "옥천군", "영동군", "증평군", "진천군", "괴산군", "음성군", "단양군"),
    "충청남도" to listOf("전체", "천안시", "공주시", "보령시", "아산시", "서산시", "논산시", "계룡시", "당진시", "금산군", "부여군", "서천군", "청양군", "홍성군", "예산군", "태안군"),
    "전북특별자치도" to listOf("전체", "전주시", "군산시", "익산시", "정읍시", "남원시", "김제시", "완주군", "진안군", "무주군", "장수군", "임실군", "순창군", "고창군", "부안군"),
    "전라남도" to listOf("전체", "목포시", "여수시", "순천시", "나주시", "광양시", "담양군", "곡성군", "구례군", "고흥군", "보성군", "화순군", "장흥군", "강진군", "해남군", "영암군", "무안군", "함평군", "영광군", "장성군", "완도군", "진도군", "신안군"),
    "경상북도" to listOf("전체", "포항시", "경주시", "김천시", "안동시", "구미시", "영주시", "영천시", "상주시", "문경시", "경산시", "의성군", "청송군", "영양군", "영덕군", "청도군", "고령군", "성주군", "칠곡군", "예천군", "봉화군", "울진군", "울릉군"),
    "경상남도" to listOf("전체", "창원시", "진주시", "통영시", "사천시", "김해시", "밀양시", "거제시", "양산시", "의령군", "함안군", "창녕군", "고성군", "남해군", "하동군", "산청군", "함양군", "거창군", "합천군"),
    "제주특별자치도" to listOf("전체", "제주시", "서귀포시")
)

class JoinMeetingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // intent에서 placeName을 못 가져오면 빈 문자열("")
        val targetPlace = intent.getStringExtra("placeName") ?: ""
        setContent {
            MaterialTheme {
                JoinMeetingScreen(targetPlace = targetPlace, onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JoinMeetingScreen(targetPlace: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val db = Firebase.firestore
    // ★ 수정: 'auth' 변수 선언 후 사용하지 않아 삭제하고 바로 currentUser만 가져옴
    val currentUser = Firebase.auth.currentUser

    // [상태 변수]
    var originalList by remember { mutableStateOf(emptyList<MeetingItem>()) }
    var displayedList by remember { mutableStateOf(emptyList<MeetingItem>()) }
    var isLoading by remember { mutableStateOf(true) }

    // 정렬 옵션
    var sortOption by remember { mutableIntStateOf(0) }
    var showSortMenu by remember { mutableStateOf(false) }

    // 필터 관련 상태
    var showFilterDialog by remember { mutableStateOf(false) }
    var filterRegion by remember { mutableStateOf("") }
    var filterAfterParty by remember { mutableStateOf(false) }
    var filterMinManner by remember { mutableFloatStateOf(50.0f) }

    // 팝업용
    var selectedMeeting by remember { mutableStateOf<MeetingItem?>(null) }
    var showSuccessPopup by remember { mutableStateOf<MeetingItem?>(null) }

// [1] 데이터 불러오기 (강제 노출 모드)
    LaunchedEffect(Unit) {
        // ★ 중요: 쿼리 조건 없이 일단 다 가져옵니다 (필터링은 코드에서 직접!)
        db.collection("meetings")
            .get()
            .addOnSuccessListener { result ->
                val list = result.documents.mapNotNull { doc ->
                    try {
                        MeetingItem(
                            id = doc.id,
                            title = doc.getString("title") ?: "제목 없음",
                            placeName = doc.getString("placeName") ?: "",
                            dateString = doc.getString("dateString") ?: "",
                            hostUid = doc.getString("hostUid") ?: "",
                            currentCount = (doc.get("participantIds") as? List<String>)?.size ?: 0,
                            maxParticipants = (doc.getLong("maxParticipants")?.toInt()) ?: 0,
                            minAge = (doc.getLong("minAge")?.toInt()) ?: 0,
                            maxAge = (doc.getLong("maxAge")?.toInt()) ?: 100,
                            hasAfterParty = doc.getBoolean("hasAfterParty") ?: false,
                            mannerTempCutline = (doc.getDouble("mannerTempCutline")?.toFloat()) ?: 0.0f,
                            gameTime = (doc.getLong("gameTimePerRound")?.toInt()) ?: 15,
                            totalRounds = (doc.getLong("totalRounds")?.toInt()) ?: 3,
                            participantIds = doc.get("participantIds") as? List<String> ?: emptyList()
                        )
                    } catch (e: Exception) {
                        // 데이터 변환 중 에러나면 로그 찍기
                        android.util.Log.e("DEBUG_MEETING", "변환 에러(${doc.id}): ${e.message}")
                        null
                    }
                }

                // ★ 디버깅용 로그: 왜 안 뜨는지 확인
                android.util.Log.d("DEBUG_MEETING", "=== [필터링 시작] ===")
                android.util.Log.d("DEBUG_MEETING", "넘어온 타겟 장소: '$targetPlace'")

                val filteredList = list.filter { item ->
                    // 1. 상태 확인 (DB 값을 직접 가져와서 확인)
                    val dbStatus = result.documents.find { it.id == item.id }?.getString("status") ?: ""

                    // 공백 제거 후 비교 (오타 방지)
                    val isRecruiting = dbStatus.trim() == "recruiting"

                    // 2. 장소 확인 (일단 무조건 통과시키되, 로그로 확인)
                    val placeMatch = if (targetPlace.isNotBlank()) {
                        item.placeName.replace(" ", "").contains(targetPlace.replace(" ", ""))
                    } else {
                        true
                    }

                    // 로그 출력
                    if (!isRecruiting) android.util.Log.d("DEBUG_MEETING", "탈락(상태): ${item.title} / status=$dbStatus")
                    if (!placeMatch) android.util.Log.d("DEBUG_MEETING", "탈락(장소): ${item.title} / DB장소=${item.placeName}")

                    // ★ [강제 노출] 상태가 recruiting이기만 하면 장소 상관없이 무조건 보여줍니다.
                    isRecruiting
                }

                // 날짜순 정렬
                originalList = filteredList.sortedBy { it.dateString }
                displayedList = originalList
                isLoading = false

                android.util.Log.d("DEBUG_MEETING", "최종 표시 개수: ${originalList.size}")
            }
            .addOnFailureListener {
                Toast.makeText(context, "로드 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                isLoading = false
            }
    }
    // [2] 정렬 및 필터 적용 (옵션이 바뀔 때마다 자동 실행)
    LaunchedEffect(originalList, sortOption, filterRegion, filterAfterParty, filterMinManner) {
        // 1. 원본에서 필터링
        var temp = originalList.filter { item ->
            val regionMatch = if (filterRegion.isBlank()) true else item.placeName.contains(filterRegion)
            val partyMatch = if (filterAfterParty) item.hasAfterParty else true
            val mannerMatch = item.mannerTempCutline >= filterMinManner

            regionMatch && partyMatch && mannerMatch
        }

        // 2. 정렬
        temp = when (sortOption) {
            0 -> temp.sortedBy { it.dateString }
            1 -> temp.sortedByDescending { it.currentCount }
            2 -> temp.sortedByDescending { it.mannerTempCutline }
            3 -> temp.sortedBy { it.minAge }
            else -> temp
        }
        displayedList = temp
    }

    // ★ [알람 예약 함수]
    fun scheduleMeetingAlarm(meeting: MeetingItem) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        try {
            val date = sdf.parse(meeting.dateString) ?: return
            val meetingTime = date.time

            data class AlarmInfo(val time: Long, val title: String, val content: String, val type: String)

            val triggerList = listOf(
                AlarmInfo(meetingTime - 24 * 60 * 60 * 1000, "내일 모임이 있어요!", "내일 봬요!", "GUEST_24H"),
                AlarmInfo(meetingTime - 1 * 60 * 60 * 1000, "1시간 뒤 모임 시작!", "늦지 마세요!", "GUEST_1H")
            )

            triggerList.forEachIndexed { index, info ->
                if (info.time > System.currentTimeMillis()) {
                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        putExtra("title", info.title)
                        putExtra("message", info.content)
                        putExtra("meetingId", meeting.id)
                        putExtra("ALARM_TYPE", info.type)
                    }
                    val requestCode = meeting.id.hashCode() + index
                    val pendingIntent = PendingIntent.getBroadcast(
                        context, requestCode, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (alarmManager.canScheduleExactAlarms()) {
                                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, info.time, pendingIntent)
                            } else {
                                alarmManager.set(AlarmManager.RTC_WAKEUP, info.time, pendingIntent)
                            }
                        } else {
                            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, info.time, pendingIntent)
                        }
                    } catch (e: SecurityException) {
                        alarmManager.set(AlarmManager.RTC_WAKEUP, info.time, pendingIntent)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "알림 예약 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    // [화면 UI]
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
    ) {
        // === 1. 상단 헤더 ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
            }

            Text(
                if (targetPlace.isEmpty()) "모임 리스트" else targetPlace,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Divider(color = Color(0xFFEEEEEE))

        // === 2. 컨트롤 바 (필터 & 정렬) ===
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(30.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // (1) 필터 버튼
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { showFilterDialog = true }
                    .padding(4.dp)
            ) {
                // 이미지가 없다면 기본 아이콘 사용 (painterResource 대신 Icons 사용 가능)
                Image(
                    painter = painterResource(id = R.drawable.ic_filter),
                    contentDescription = "필터",
                    modifier = Modifier.size(24.dp)
                )
                Text("필터", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.width(24.dp))

            // (2) 정렬 버튼
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { showSortMenu = true }
                        .padding(4.dp)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_sort),
                        contentDescription = "정렬",
                        modifier = Modifier.size(24.dp)
                    )
                    Text("정렬", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }

                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    listOf("날짜 빠른 모임 순", "현재 신청자 많은 순", "신용도 기준 높은 순", "연령 낮은 순").forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label, fontWeight = if(sortOption==index) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                sortOption = index // 정렬 옵션 변경 -> LaunchedEffect 실행됨
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        Divider(color = Color(0xFFEEEEEE))

        // === 3. 리스트 ===
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(displayedList) { meeting ->
                    EnhancedMeetingCard(meeting) { selectedMeeting = meeting }
                }
            }
        }
    }

    // === [팝업 1] 필터 다이얼로그 ===
    if (showFilterDialog) {
        FilterDialog(
            currentRegion = filterRegion,
            currentParty = filterAfterParty,
            currentManner = filterMinManner,
            onDismiss = { showFilterDialog = false },
            onApply = { region, party, manner ->
                filterRegion = region
                filterAfterParty = party
                filterMinManner = manner // 필터 변경 -> LaunchedEffect 실행됨
                showFilterDialog = false
            }
        )
    }

    // === [팝업 2] 모임 상세 및 참가 ===
    if (selectedMeeting != null) {
        val meeting = selectedMeeting!!
        val isJoined = meeting.participantIds.contains(currentUser?.uid)

        Dialog(onDismissRequest = { selectedMeeting = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(meeting.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("📍  ${meeting.placeName}", fontSize = 14.sp)
                    Text("📅  ${meeting.dateString}", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(20.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(onClick = { selectedMeeting = null }, colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray)) {
                            Text("뒤로 가기", color = Color.Black)
                        }
                        Button(
                            onClick = {
                                if (currentUser == null) return@Button
                                db.collection("meetings").document(meeting.id)
                                    .update("participantIds", FieldValue.arrayUnion(currentUser.uid))
                                    .addOnSuccessListener {
                                        scheduleMeetingAlarm(meeting)
                                        selectedMeeting = null
                                        showSuccessPopup = meeting
                                    }
                            },
                            enabled = !isJoined,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                        ) {
                            Text(if(isJoined) "이미 참가함" else "참가하기")
                        }
                    }
                }
            }
        }
    }

    // === [팝업 3] 참가 확정 팝업 ===
    if (showSuccessPopup != null) {
        val meeting = showSuccessPopup!!
        AlertDialog(
            onDismissRequest = {
                showSuccessPopup = null
                onBack() // 리스트 갱신 등을 위해 뒤로가거나 머무를 수 있음 (여기선 onBack 호출)
            },
            title = { Text("🎉 참가 확정!") },
            text = {
                Column {
                    Text("모임 참가가 완료되었습니다.\n아래 일정을 잊지 마세요!")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("모임명: ${meeting.title}", fontWeight = FontWeight.Bold)
                    Text("장소: ${meeting.placeName}")
                    Text("일시: ${meeting.dateString}")
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("(알림이 예약되었습니다.)", fontSize = 12.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(onClick = {
                    showSuccessPopup = null
                    onBack()
                }) { Text("확인") }
            }
        )
    }
}

// [UI 컴포넌트] 리뉴얼된 카드
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EnhancedMeetingCard(meeting: MeetingItem, onClick: () -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meeting.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = Color(0xFFF5F5F5), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "${meeting.currentCount}/${meeting.maxParticipants}명",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HashTag("#${meeting.dateString}")
                HashTag("#${meeting.placeName}")
                HashTag("#${meeting.minAge}~${meeting.maxAge}세")
                if(meeting.hasAfterParty) HashTag("#뒷풀이O") else HashTag("#뒷풀이X")
                HashTag("#신용도${meeting.mannerTempCutline}↑")

                val roundsText = if (meeting.totalRounds == -1) "라운드미정" else "${meeting.totalRounds}라운드"
                HashTag("#$roundsText")

                val gameTimeText = if (meeting.gameTime == -1) "시간미정" else "${meeting.gameTime}분"
                HashTag("#$gameTimeText")
            }
        }
    }
}

@Composable
fun HashTag(text: String, containerColor: Color = Color(0xFFF0F0F0), contentColor: Color = Color.Gray) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(text, fontSize = 11.sp, color = contentColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

// [UI 컴포넌트] 필터 다이얼로그
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    currentRegion: String,
    currentParty: Boolean,
    currentManner: Float,
    onDismiss: () -> Unit,
    onApply: (String, Boolean, Float) -> Unit
) {
    // 필터 초기화 값 세팅
    val splitRegion = currentRegion.split(" ")
    var selectedDo by remember { mutableStateOf(if (splitRegion.isNotEmpty()) splitRegion[0] else "") }
    var selectedSi by remember { mutableStateOf(if (splitRegion.size > 1) splitRegion[1] else "") }

    var checkAfterParty by remember { mutableStateOf(currentParty) }
    var minManner by remember { mutableFloatStateOf(currentManner) }

    var expandedDo by remember { mutableStateOf(false) }
    var expandedSi by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("상세 검색 필터", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Text("지역 선택", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. 시/도 선택
                    ExposedDropdownMenuBox(
                        expanded = expandedDo,
                        onExpandedChange = { expandedDo = !expandedDo },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = if(selectedDo.isEmpty()) "시/도" else selectedDo,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDo) },
                            modifier = Modifier.menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = expandedDo,
                            onDismissRequest = { expandedDo = false },
                            modifier = Modifier.background(Color.White).heightIn(max = 200.dp)
                        ) {
                            koreaRegionData.keys.forEach { regionName ->
                                DropdownMenuItem(
                                    text = { Text(regionName) },
                                    onClick = {
                                        selectedDo = regionName
                                        selectedSi = ""
                                        expandedDo = false
                                    }
                                )
                            }
                        }
                    }

                    // 2. 시/군/구 선택
                    ExposedDropdownMenuBox(
                        expanded = expandedSi,
                        onExpandedChange = { expandedSi = !expandedSi },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = if(selectedSi.isEmpty()) "시/군/구" else selectedSi,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSi) },
                            modifier = Modifier.menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.White,
                                unfocusedContainerColor = Color.White
                            ),
                            enabled = selectedDo.isNotEmpty() && selectedDo != "전체"
                        )
                        ExposedDropdownMenu(
                            expanded = expandedSi,
                            onDismissRequest = { expandedSi = false },
                            modifier = Modifier.background(Color.White).heightIn(max = 200.dp)
                        ) {
                            val siList = koreaRegionData[selectedDo] ?: emptyList()
                            siList.forEach { siName ->
                                DropdownMenuItem(
                                    text = { Text(siName) },
                                    onClick = {
                                        selectedSi = siName
                                        expandedSi = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = checkAfterParty, onCheckedChange = { checkAfterParty = it })
                    Text("뒷풀이 있는 모임만 보기")
                }
                Spacer(modifier = Modifier.height(10.dp))

                Text("최소 신용도: ${String.format("%.1f", minManner)}")
                Slider(
                    value = minManner,
                    onValueChange = { minManner = kotlin.math.round(it * 2) / 2f },
                    valueRange = 0f..100f
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val finalRegion = if (selectedDo == "전체" || selectedDo.isEmpty()) {
                            ""
                        } else if (selectedSi == "전체" || selectedSi.isEmpty()) {
                            selectedDo
                        } else {
                            "$selectedDo $selectedSi"
                        }
                        onApply(finalRegion, checkAfterParty, minManner)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) {
                    Text("적용하기")
                }
            }
        }
    }
}