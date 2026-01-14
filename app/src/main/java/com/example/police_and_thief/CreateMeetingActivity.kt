package com.example.police_and_thief

import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class CreateMeetingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val placeName = intent.getStringExtra("placeName") ?: "주소 정보 없음"
                val lat = intent.getDoubleExtra("latitude", 0.0)
                val lng = intent.getDoubleExtra("longitude", 0.0)

                CreateMeetingScreen(
                    onBack = { finish() },
                    initialPlaceName = placeName,
                    latitude = lat,
                    longitude = lng
                )
            }
        }
    }
}

// [공통 컴포넌트] 안드로이드 NumberPicker를 래핑한 WheelPicker
@Composable
fun WheelPicker(
    items: List<String>,
    initialIndex: Int,
    onSelectionChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.width(100.dp), // 너비 고정
        factory = { context ->
            NumberPicker(context).apply {
                minValue = 0
                maxValue = items.size - 1
                displayedValues = items.toTypedArray()
                value = initialIndex
                wrapSelectorWheel = false // 끝에서 처음으로 돌아가지 않게 설정
                setOnValueChangedListener { _, _, newVal ->
                    onSelectionChanged(newVal)
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateMeetingScreen(
    onBack: () -> Unit,
    initialPlaceName: String,
    latitude: Double,
    longitude: Double
) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val auth = Firebase.auth

    val placeNameInput by remember { mutableStateOf(initialPlaceName) }
    var title by remember { mutableStateOf("") }

    // 날짜/시간 관련 상태
    val calendar = Calendar.getInstance()
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }

    // 다이얼로그 상태 제어
    var showTimePicker by remember { mutableStateOf(false) }
    var showParticipantsPicker by remember { mutableStateOf(false) }
    var showGameTimePicker by remember { mutableStateOf(false) }
    var showTotalRoundsPicker by remember { mutableStateOf(false) }

    // 시간 선택용 상태 (WheelPicker용 인덱스)
    var selectedHourIndex by remember { mutableIntStateOf(12) }
    var selectedMinuteIndex by remember { mutableIntStateOf(0) }

    // 옵션 상태
    var ageRange by remember { mutableStateOf(20f..30f) }
    var hasAfterParty by remember { mutableStateOf(true) }
    var mannerTempCutline by remember { mutableFloatStateOf(50.0f) }

    // 다이얼 선택 값 (표시용 및 저장용)
    var maxParticipantsStr by remember { mutableStateOf("10") }
    var gameTimeStr by remember { mutableStateOf("15") }
    var totalRoundsStr by remember { mutableStateOf("3") }

    var isSubmitting by remember { mutableStateOf(false) }

    // [1] 날짜 선택 다이얼로그 설정 (지난 날짜 막기)
    val datePickerDialog = DatePickerDialog(
        context,
        { _, y, m, d ->
            dateText = String.format("%d-%02d-%02d", y, m + 1, d)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )
    datePickerDialog.datePicker.minDate = System.currentTimeMillis() - 1000

    // [데이터 리스트 생성]
    val hours = (0..23).map { String.format("%02d", it) }
    val minutes = (0..55 step 5).map { String.format("%02d", it) }
    val participantsList = (0..100 step 5).map { it.toString() }
    val gameTimeList = listOf("미정") + (5..60 step 5).map { it.toString() }
    val totalRoundsList = listOf("미정") + (1..10).map { it.toString() }

    // ★ [알람 예약 함수] 방장용 (24시간 전, 1시간 전, 30분 전) - 타입 추가됨!
    fun scheduleHostAlarm(meetingId: String, meetingTitle: String, meetingDateString: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        try {
            val date = sdf.parse(meetingDateString) ?: return
            val meetingTime = date.time

            // 알람 정보를 담을 데이터 클래스 (시간, 제목, 내용, ★타입)
            data class AlarmInfo(val time: Long, val title: String, val content: String, val type: String)

            val triggerList = listOf(
                AlarmInfo(meetingTime - 24 * 60 * 60 * 1000, "내일 주최한 모임이 있어요!", "참가자들을 위해 미리 준비해주세요.", "HOST_24H"),
                AlarmInfo(meetingTime - 1 * 60 * 60 * 1000, "모임 1시간 전입니다!", "잊으신 건 없나요?", "HOST_1H"),
                AlarmInfo(meetingTime - 30 * 60 * 1000, "모임 30분 전!", "곧 시작됩니다. 먼저 도착해서 기다려주세요!", "HOST_30M")
            )

            triggerList.forEachIndexed { index, info ->
                if (info.time > System.currentTimeMillis()) {
                    val intent = Intent(context, AlarmReceiver::class.java).apply {
                        putExtra("title", info.title)
                        putExtra("message", info.content)
                        putExtra("meetingId", meetingId)
                        putExtra("ALARM_TYPE", info.type) // ★ 중요: 리시버에서 구분할 타입 전달
                    }

                    // 방장 알람은 참가자 알람과 겹치지 않게 RequestCode 구분
                    val requestCode = meetingId.hashCode() + index + 10000

                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        requestCode,
                        intent,
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
            Toast.makeText(context, "알람 예약 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
    ) {
        // [헤더]
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = Color.Black)
            }
            Text("모임 만들기", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.padding(16.dp)) {
            // [장소] (읽기 전용)
            OutlinedTextField(
                value = placeNameInput,
                onValueChange = { },
                label = { Text("모임 장소 (주소)") },
                leadingIcon = { Icon(Icons.Default.Place, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF5F5F5),
                    unfocusedContainerColor = Color(0xFFF5F5F5)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // [모임 이름]
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("모임 이름") },
                placeholder = { Text("예: 20대 경찰과 도둑 한 판!") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // [날짜 및 시간 선택 버튼]
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 날짜 버튼
                OutlinedTextField(
                    value = dateText,
                    onValueChange = {},
                    label = { Text("날짜") },
                    placeholder = { Text("YYYY-MM-DD") },
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    modifier = Modifier.weight(1f).clickable { datePickerDialog.show() },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = Color.Black, disabledBorderColor = Color.Gray,
                        disabledLabelColor = Color.Gray, disabledTrailingIconColor = Color.Black
                    )
                )

                // 시간 버튼
                OutlinedTextField(
                    value = timeText,
                    onValueChange = {},
                    label = { Text("시간") },
                    placeholder = { Text("HH:MM") },
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Default.Notifications, contentDescription = null) },
                    modifier = Modifier.weight(1f).clickable { showTimePicker = true },
                    enabled = false,
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = Color.Black, disabledBorderColor = Color.Gray,
                        disabledLabelColor = Color.Gray, disabledTrailingIconColor = Color.Black
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
            Divider()
            Spacer(modifier = Modifier.height(24.dp))

            // [나이 설정]
            Text("참여 가능 나이: ${ageRange.start.toInt()}세 ~ ${ageRange.endInclusive.toInt()}세", fontWeight = FontWeight.Bold)
            RangeSlider(value = ageRange, onValueChange = { ageRange = it }, valueRange = 10f..60f, steps = 49)
            Spacer(modifier = Modifier.height(16.dp))

            // [모집 정원]
            Text("모집 정원", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                    .clickable { showParticipantsPicker = true }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(if (maxParticipantsStr.isEmpty()) "인원 선택" else "${maxParticipantsStr}명", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))

            // [뒷풀이 여부]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("뒷풀이 여부", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Switch(checked = hasAfterParty, onCheckedChange = { hasAfterParty = it })
                Text(if (hasAfterParty) " 있음" else " 없음", modifier = Modifier.padding(start = 8.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))

            // [신용도]
            Text("신용도 커트라인: ${String.format("%.1f", mannerTempCutline)} 이상", fontWeight = FontWeight.Bold)
            Slider(value = mannerTempCutline, onValueChange = { mannerTempCutline = kotlin.math.round(it * 2) / 2f }, valueRange = 0f..99f)
            Spacer(modifier = Modifier.height(16.dp))

            // [게임 설정]
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 게임 시간
                Column(modifier = Modifier.weight(1f)) {
                    Text("게임 시간(분)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                            .clickable { showGameTimePicker = true }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(if (gameTimeStr == "미정") "미정" else "${gameTimeStr}분", fontSize = 16.sp)
                    }
                }

                // 총 라운드
                Column(modifier = Modifier.weight(1f)) {
                    Text("총 라운드 수", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .border(1.dp, Color.Gray, RoundedCornerShape(4.dp))
                            .clickable { showTotalRoundsPicker = true }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(if (totalRoundsStr == "미정") "미정" else "${totalRoundsStr}판", fontSize = 16.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // [개설하기 버튼]
            Button(
                onClick = {
                    if (title.isBlank() || dateText.isBlank() || timeText.isBlank()) {
                        Toast.makeText(context, "모임 이름, 날짜, 시간을 모두 입력해주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (maxParticipantsStr.isEmpty() || gameTimeStr.isEmpty() || totalRoundsStr.isEmpty()) {
                        Toast.makeText(context, "옵션을 모두 선택해주세요.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    isSubmitting = true
                    val hostUid = auth.currentUser?.uid ?: "anonymous"
                    val finalDateString = "$dateText $timeText"
                    val formattedMannerTemp = String.format("%.1f", mannerTempCutline).toDouble()

                    val gameTimeInt = if (gameTimeStr == "미정") -1 else gameTimeStr.toInt()
                    val totalRoundsInt = if (totalRoundsStr == "미정") -1 else totalRoundsStr.toInt()

                    val meetingData = hashMapOf(
                        "hostUid" to hostUid,
                        "participantIds" to listOf(hostUid),
                        "placeName" to placeNameInput,
                        "latitude" to latitude,
                        "longitude" to longitude,
                        "title" to title,
                        "dateString" to finalDateString,
                        "minAge" to ageRange.start.toInt(),
                        "maxAge" to ageRange.endInclusive.toInt(),
                        "maxParticipants" to maxParticipantsStr.toInt(),
                        "hasAfterParty" to hasAfterParty,
                        "mannerTempCutline" to formattedMannerTemp,
                        "gameTimePerRound" to gameTimeInt,
                        "totalRounds" to totalRoundsInt,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "status" to "recruiting",
                        "checkedInUids" to listOf(hostUid)
                    )

                    db.collection("meetings")
                        .add(meetingData)
                        .addOnSuccessListener { documentReference ->
                            // ★ 성공 시 알람 예약 함수 호출
                            scheduleHostAlarm(documentReference.id, title, finalDateString)

                            isSubmitting = false
                            Toast.makeText(context, "모임이 개설되었습니다! 알림이 설정됩니다.", Toast.LENGTH_SHORT).show()
                            onBack()
                        }
                        .addOnFailureListener {
                            isSubmitting = false
                            Toast.makeText(context, "개설 실패: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                enabled = !isSubmitting
            ) {
                if (isSubmitting) CircularProgressIndicator(color = Color.White)
                else Text("모임 개설하기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // ==========================================
    // [다이얼로그 구현부]
    // ==========================================

    if (showTimePicker) {
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("시간 선택", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WheelPicker(items = hours, initialIndex = selectedHourIndex, onSelectionChanged = { selectedHourIndex = it })
                        Text(":", fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                        WheelPicker(items = minutes, initialIndex = selectedMinuteIndex, onSelectionChanged = { selectedMinuteIndex = it })
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { timeText = "${hours[selectedHourIndex]}:${minutes[selectedMinuteIndex]}"; showTimePicker = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) {
                        Text("확인")
                    }
                }
            }
        }
    }

    if (showParticipantsPicker) {
        Dialog(onDismissRequest = { showParticipantsPicker = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("모집 정원", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    WheelPicker(items = participantsList, initialIndex = participantsList.indexOf(maxParticipantsStr).coerceAtLeast(0), onSelectionChanged = { maxParticipantsStr = participantsList[it] })
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { showParticipantsPicker = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("확인") }
                }
            }
        }
    }

    if (showGameTimePicker) {
        Dialog(onDismissRequest = { showGameTimePicker = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("게임 시간", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    WheelPicker(items = gameTimeList, initialIndex = gameTimeList.indexOf(gameTimeStr).coerceAtLeast(0), onSelectionChanged = { gameTimeStr = gameTimeList[it] })
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { showGameTimePicker = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("확인") }
                }
            }
        }
    }

    if (showTotalRoundsPicker) {
        Dialog(onDismissRequest = { showTotalRoundsPicker = false }) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("총 라운드", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    WheelPicker(items = totalRoundsList, initialIndex = totalRoundsList.indexOf(totalRoundsStr).coerceAtLeast(0), onSelectionChanged = { totalRoundsStr = totalRoundsList[it] })
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = { showTotalRoundsPicker = false }, colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) { Text("확인") }
                }
            }
        }
    }
}