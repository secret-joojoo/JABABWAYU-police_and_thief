package com.example.police_and_thief

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
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
import androidx.core.content.ContextCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue // ★ 추가됨
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.kakao.vectormap.*
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.Label
import com.kakao.vectormap.label.LabelLayerOptions
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.label.OrderingType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale

class MapActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MapScreen(onBack = { finish() })
            }
        }
    }
}

// [유틸리티 함수 1] 비트맵 리사이징
fun resizeBitmapFromDrawable(context: Context, drawableId: Int, width: Int, height: Int): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

// [유틸리티 함수 2] 좌표 -> 주소 변환
fun getAddressFromLatLng(context: Context, lat: Double, lng: Double): String {
    return try {
        val geocoder = Geocoder(context, Locale.KOREA)
        val addresses = geocoder.getFromLocation(lat, lng, 1)
        if (!addresses.isNullOrEmpty()) {
            addresses[0].getAddressLine(0)
        } else {
            "주소 정보 없음"
        }
    } catch (e: Exception) {
        e.printStackTrace()
        "주소 변환 실패"
    }
}

@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    // 권한 관련 상태
    var hasPermission by remember { mutableStateOf(false) }

    // 선택된 모임 데이터 상태
    var selectedSpotName by remember { mutableStateOf("") }
    var selectedMeetingId by remember { mutableStateOf<String?>(null) }

    // ★ [추가] 팝업에 띄울 상세 모임 정보 & 성공 팝업 상태
    var fullSelectedMeeting by remember { mutableStateOf<MeetingItem?>(null) }
    var showSuccessPopup by remember { mutableStateOf<MeetingItem?>(null) }
    var isFetchingDetail by remember { mutableStateOf(false) }

    // 지도 제어용 상태
    var kakaoMapRef by remember { mutableStateOf<KakaoMap?>(null) }
    var isSelectingLocation by remember { mutableStateOf(false) }
    var isConvertingAddress by remember { mutableStateOf(false) }

    // 권한 요청 런처
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    // 초기 권한 확인
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            hasPermission = true
        } else {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    // ★ [추가] 알람 예약 함수 (JoinMeetingActivity와 동일 로직)
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
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.White)) {

        // ==========================================
        // [1] 상단 헤더
        // ==========================================
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = Color.Black)
            }
            Text(
                if (isSelectingLocation) "위치 선택 중..." else "지도",
                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black
            )
        }

        // ==========================================
        // [2] 지도 영역
        // ==========================================
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        start(object : MapLifeCycleCallback() {
                            override fun onMapDestroy() {}
                            override fun onMapError(error: Exception?) {
                                Log.e("KakaoMap", "Error: ${error?.message}")
                            }
                        }, object : KakaoMapReadyCallback() {
                            // MapActivity.kt의 onMapReady 부분

                            override fun onMapReady(kakaoMap: KakaoMap) {
                                kakaoMapRef = kakaoMap
                                val kaistLat = 36.3721
                                val kaistLng = 127.3604
                                kakaoMap.moveCamera(CameraUpdateFactory.newCenterPosition(LatLng.from(kaistLat, kaistLng)))

                                val labelManager = kakaoMap.labelManager
                                val layerOptions = LabelLayerOptions.from("meetingLayer")
                                    .setOrderingType(OrderingType.Rank)
                                val layer = labelManager?.addLayer(layerOptions)

                                val normalIcon = resizeBitmapFromDrawable(context, R.drawable.ic_map_pin, 100, 100)
                                val selectedIcon = resizeBitmapFromDrawable(context, R.drawable.ic_map_pin_selected, 150, 150)

                                if (normalIcon != null && selectedIcon != null) {
                                    val normalStyle = LabelStyles.from(LabelStyle.from(normalIcon))
                                    val selectedStyle = LabelStyles.from(LabelStyle.from(selectedIcon))

                                    // ★ [수정] 시간 조건 없이, 상태가 'RECRUITING'이면 무조건 가져옵니다.
                                    // (주의: DB 필드명이 'status'인지 'meetingStatus'인지 꼭 확인하세요! 다른 파일들은 'meetingStatus'를 쓰고 있습니다.)
                                    db.collection("meetings")
                                        .whereEqualTo("status", "recruiting")
                                        .get()
                                        .addOnSuccessListener { result ->
                                            result.documents.forEach { doc ->
                                                val lat = doc.getDouble("latitude")
                                                val lng = doc.getDouble("longitude")
                                                val id = doc.id

                                                // 좌표가 유효하기만 하면 핀을 찍습니다.
                                                if (lat != null && lng != null && lat != 0.0 && lng != 0.0) {
                                                    val options = LabelOptions.from(LatLng.from(lat, lng))
                                                        .setStyles(normalStyle)
                                                        .setTag(id) // 태그에 모임 ID 저장

                                                    layer?.addLabel(options)
                                                }
                                            }
                                        }

                                    var currentSelectedLabel: Label? = null

                                    kakaoMap.setOnLabelClickListener { _, _, label ->
                                        // (이 아래 클릭 리스너 코드는 기존과 동일하게 유지)
                                        if (!isSelectingLocation) {
                                            currentSelectedLabel?.apply {
                                                changeStyles(normalStyle)
                                                rank = 0
                                            }
                                            label.apply {
                                                changeStyles(selectedStyle)
                                                rank = 100000
                                            }
                                            currentSelectedLabel = label

                                            val clickedMeetingId = label.tag as? String
                                            if (clickedMeetingId != null) {
                                                selectedMeetingId = clickedMeetingId
                                                db.collection("meetings").document(clickedMeetingId).get()
                                                    .addOnSuccessListener { doc ->
                                                        val title = doc.getString("title") ?: "알 수 없는 모임"
                                                        val dateStr = doc.getString("dateString") ?: ""
                                                        selectedSpotName = "$title ($dateStr)"
                                                    }
                                            }
                                        }
                                        true
                                    }
                                }
                            }
                        })
                    }
                }
            )

            if (isSelectingLocation) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "Center Pin",
                    modifier = Modifier.size(48.dp).align(Alignment.Center).padding(bottom = 24.dp),
                    tint = Color.Red
                )
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = "지도를 움직여 모임 장소를 정해주세요",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 14.sp
                    )
                }
            }

            if (isConvertingAddress || isFetchingDetail) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha=0.3f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        // ==========================================
        // [3] 하단 컨트롤 영역
        // ==========================================
        if (isSelectingLocation) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = { isSelectingLocation = false },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) { Text("취소") }

                Button(
                    onClick = {
                        val centerPos = kakaoMapRef?.cameraPosition?.position
                        if (centerPos != null) {
                            isConvertingAddress = true
                            CoroutineScope(Dispatchers.IO).launch {
                                val addressStr = getAddressFromLatLng(context, centerPos.latitude, centerPos.longitude)
                                withContext(Dispatchers.Main) {
                                    isConvertingAddress = false
                                    val intent = Intent(context, CreateMeetingActivity::class.java).apply {
                                        putExtra("placeName", addressStr)
                                        putExtra("latitude", centerPos.latitude)
                                        putExtra("longitude", centerPos.longitude)
                                    }
                                    context.startActivity(intent)
                                    isSelectingLocation = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                ) { Text("이 위치로 설정") }
            }
        } else {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .clickable(enabled = selectedMeetingId != null) {
                            // ★ [수정] 텍스트 클릭 시에도 팝업 띄우기 로직 실행
                            if (selectedMeetingId != null) {
                                isFetchingDetail = true
                                db.collection("meetings").document(selectedMeetingId!!).get()
                                    .addOnSuccessListener { doc ->
                                        // MeetingItem 변환 (JoinMeetingActivity와 동일한 파싱)
                                        val item = try {
                                            MeetingItem(
                                                id = doc.id,
                                                title = doc.getString("title") ?: "",
                                                placeName = doc.getString("placeName") ?: "",
                                                dateString = doc.getString("dateString") ?: "",
                                                hostUid = doc.getString("hostUid") ?: "",
                                                currentCount = (doc.get("participantIds") as? List<String>)?.size ?: 0,
                                                maxParticipants = (doc.getLong("maxParticipants")?.toInt()) ?: 0,
                                                minAge = (doc.getLong("minAge")?.toInt()) ?: 0,
                                                maxAge = (doc.getLong("maxAge")?.toInt()) ?: 100,
                                                hasAfterParty = doc.getBoolean("hasAfterParty") ?: false,
                                                mannerTempCutline = (doc.getDouble("mannerTempCutline")?.toFloat()) ?: 50.0f,
                                                gameTime = (doc.getLong("gameTimePerRound")?.toInt()) ?: 15,
                                                totalRounds = (doc.getLong("totalRounds")?.toInt()) ?: 3,
                                                participantIds = doc.get("participantIds") as? List<String> ?: emptyList()
                                            )
                                        } catch (e: Exception) { null }
                                        fullSelectedMeeting = item
                                        isFetchingDetail = false
                                    }
                                    .addOnFailureListener { isFetchingDetail = false }
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (selectedSpotName.isNotEmpty()) {
                            Text(text = "선택된 모임", fontSize = 12.sp, color = Color.Gray)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = selectedSpotName,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        } else {
                            Text(text = "지도의 핀을 눌러 정보를 확인하세요", fontSize = 14.sp, color = Color.Gray)
                        }
                    }

                    if (selectedSpotName.isNotEmpty() && selectedMeetingId != null) {
                        IconButton(
                            onClick = {
                                // ★ [수정] 화살표 버튼 클릭 시 -> DB에서 상세 정보 가져와서 팝업 띄우기
                                isFetchingDetail = true
                                db.collection("meetings").document(selectedMeetingId!!).get()
                                    .addOnSuccessListener { doc ->
                                        val item = try {
                                            MeetingItem(
                                                id = doc.id,
                                                title = doc.getString("title") ?: "",
                                                placeName = doc.getString("placeName") ?: "",
                                                dateString = doc.getString("dateString") ?: "",
                                                hostUid = doc.getString("hostUid") ?: "",
                                                currentCount = (doc.get("participantIds") as? List<String>)?.size ?: 0,
                                                maxParticipants = (doc.getLong("maxParticipants")?.toInt()) ?: 0,
                                                minAge = (doc.getLong("minAge")?.toInt()) ?: 0,
                                                maxAge = (doc.getLong("maxAge")?.toInt()) ?: 100,
                                                hasAfterParty = doc.getBoolean("hasAfterParty") ?: false,
                                                mannerTempCutline = (doc.getDouble("mannerTempCutline")?.toFloat()) ?: 50.0f,
                                                gameTime = (doc.getLong("gameTimePerRound")?.toInt()) ?: 15,
                                                totalRounds = (doc.getLong("totalRounds")?.toInt()) ?: 3,
                                                participantIds = doc.get("participantIds") as? List<String> ?: emptyList()
                                            )
                                        } catch (e: Exception) { null }
                                        fullSelectedMeeting = item
                                        isFetchingDetail = false
                                    }
                                    .addOnFailureListener { isFetchingDetail = false }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "더 보기",
                                modifier = Modifier.size(32.dp),
                                tint = Color.Black
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = { isSelectingLocation = true },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
                    ) {
                        Text("모임 만들기", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val intent = Intent(context, JoinMeetingActivity::class.java)
                            context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Color.Black)
                    ) {
                        Text("모임 리스트 보기", color = Color.Black, fontWeight = FontWeight.Bold) // 문구 수정 (참가하기 -> 리스트 보기)
                    }
                }
            }
        }
    }

    // ==========================================
    // [4] 상세 정보 팝업 (JoinMeetingActivity와 UI 통일)
    // ==========================================
    if (fullSelectedMeeting != null) {
        val meeting = fullSelectedMeeting!!
        val isJoined = meeting.participantIds.contains(currentUser?.uid)

        Dialog(onDismissRequest = { fullSelectedMeeting = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    // [기본 정보 헤더]
                    Text(meeting.title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("📍  ${meeting.placeName}", fontSize = 15.sp)
                    Text("📅  ${meeting.dateString}", fontSize = 15.sp)

                    Spacer(modifier = Modifier.height(20.dp))
                    Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(20.dp))

                    // [상세 정보 리스트] - 네가 원하던 정보들을 깔끔하게 정리했어
                    DetailInfoRow("참가 인원", "${meeting.currentCount} / ${meeting.maxParticipants}명")
                    DetailInfoRow("참가 연령", "${meeting.minAge}세 ~ ${meeting.maxAge}세")
                    DetailInfoRow("뒷풀이", if (meeting.hasAfterParty) "있음 🍻" else "없음")
                    DetailInfoRow("신용도", "${meeting.mannerTempCutline} 이상")

                    val roundsText = if (meeting.totalRounds == -1) "미정" else "${meeting.totalRounds}라운드"
                    val timeText = if (meeting.gameTime == -1) "미정" else "${meeting.gameTime}분"
                    DetailInfoRow("게임 설정", "$roundsText / $timeText")

                    Spacer(modifier = Modifier.height(24.dp))

                    // [버튼 영역]
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Button(
                            onClick = { fullSelectedMeeting = null },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F0F0)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("닫기", color = Color.Gray)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                if (currentUser == null) {
                                    Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (isJoined) {
                                    Toast.makeText(context, "이미 참가한 모임입니다.", Toast.LENGTH_SHORT).show()
                                } else {
                                    db.collection("meetings").document(meeting.id)
                                        .update("participantIds", FieldValue.arrayUnion(currentUser.uid))
                                        .addOnSuccessListener {
                                            scheduleMeetingAlarm(meeting)
                                            fullSelectedMeeting = null
                                            showSuccessPopup = meeting
                                        }
                                }
                            },
                            enabled = !isJoined,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            modifier = Modifier.weight(2f)
                        ) {
                            Text(if(isJoined) "이미 참가함" else "참가하기", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // [5] 참가 성공 팝업
    // ==========================================
    if (showSuccessPopup != null) {
        val meeting = showSuccessPopup!!
        AlertDialog(
            onDismissRequest = { showSuccessPopup = null },
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
                Button(onClick = { showSuccessPopup = null }) { Text("확인") }
            }
        )
    }
}


// ★ [추가된 컴포저블] 상세 정보를 한 줄씩 예쁘게 보여주는 함수야.
// MapActivity 파일의 가장 아래쪽(MapScreen 함수 밖)에 붙여넣으면 돼.
@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(80.dp) // 라벨 너비 고정해서 정렬 맞춤
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.Black,
            fontWeight = FontWeight.SemiBold
        )
    }
}