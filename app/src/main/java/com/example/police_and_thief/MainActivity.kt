
package com.example.police_and_thief

// [필수 라이브러리 임포트]
// 안드로이드 기본 기능, Compose UI 도구, Firebase, 날짜 처리 등을 가져오는 부분이야.
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// [메인 액티비티 클래스]
// 앱이 실행되면 가장 먼저 켜지는 화면(Activity)이야.
// ComponentActivity를 상속받아서 Compose를 사용할 수 있어.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // setContent: 여기서부터 Compose의 세계야. 기존 xml 대신 코드로 UI를 그리지.
        setContent {
            MaterialTheme {
                MainScreen() // 메인 화면을 그리는 함수 호출
            }
        }
    }
}

// [메인 화면 UI 구성 함수]
// @Composable 어노테이션이 붙은 함수는 UI를 그리는 블록이야.
@Composable
fun MainScreen() {
    // 1. 기본 설정 및 Firebase 초기화
    val context = LocalContext.current // 현재 화면의 문맥(Context)을 가져와. 토스트 띄우거나 화면 이동할 때 필요해.
    val db = Firebase.firestore // 데이터베이스(Firestore) 접근 객체
    val auth = Firebase.auth // 인증(로그인) 관리 객체
    val currentUser = auth.currentUser // 현재 로그인된 유저 정보

    // 2. [상태 변수들 (State)]
    // remember { mutableStateOf(...) }: 화면이 다시 그려져도(Recomposition) 값이 날아가지 않게 기억하는 변수들이야.

    // 팝업이 떠있는지 여부를 저장하는 변수
    var showRulePopup by remember { mutableStateOf(false) }
    var showAttendancePopup by remember { mutableStateOf(false) }

    // 유저 정보를 저장할 변수들 (초기값 설정)
    var nickName by remember { mutableStateOf("닉네임 로딩 중...") }
    var level by remember { mutableIntStateOf(1) }
    var exp by remember { mutableIntStateOf(0) }
    var mannerTemp by remember { mutableDoubleStateOf(50.0) }

    // 아바타 이미지 리소스 ID 저장 (기본값: 경찰 이미지)
    var avatarResId by remember { mutableIntStateOf(R.drawable.img_avatar_police) }
    // 착용 중인 악세사리들의 ID 목록
    val accResIds = remember { mutableStateListOf<Int>() }

    var randomQuote by remember { mutableStateOf("조언을 불러오는 중...") }

    // 3. [애니메이션 효과]
    // 팝업이 뜨면 뒷배경을 흐리게(Blur) 만들기 위한 애니메이션 값이야.
    // 팝업이 true면 15dp만큼 흐리게, 아니면 0dp(선명하게) 부드럽게 변해.
    val blurRadius by animateDpAsState(
        targetValue = if (showRulePopup || showAttendancePopup) 15.dp else 0.dp,
        label = "blur"
    )

    // 4. [Lifecycle Effect: 명언 불러오기]
    // 화면이 켜지거나(Resume) 다시 돌아올 때마다 실행되는 로직이야.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // res/raw/quotes.txt 파일에서 명언을 읽어와서 랜덤으로 하나 뽑음
                try {
                    val inputStream = context.resources.openRawResource(R.raw.quotes)
                    val lines = BufferedReader(InputStreamReader(inputStream)).use { it.readLines() }

                    if (lines.isNotEmpty()) {
                        randomQuote = lines.random().replace("\\n", "\n") // 줄바꿈 문자 처리
                    }
                } catch (e: Exception) {
                    randomQuote = "명언 파일(res/raw/quotes.txt)을 만들어주세요!"
                }
            }
        }
        // 생명주기 관찰자 등록
        lifecycleOwner.lifecycle.addObserver(observer)

        // 이 화면이 사라질 때 관찰자 제거 (메모리 누수 방지)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 5. [Firebase Listener: 실시간 유저 정보 동기화]
    // DB의 값이 바뀌면 앱에도 즉시 반영되도록 리스너를 달아주는 거야.
    DisposableEffect(Unit) {
        val listener = if (currentUser != null) {
            // 'users' 컬렉션에서 내 UID 문서를 감시함
            db.collection("users").document(currentUser.uid)
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener // 에러나면 중단

                    if (snapshot != null && snapshot.exists()) {
                        // DB에서 가져온 데이터를 상태 변수에 넣어줌 -> 화면이 자동으로 갱신됨!
                        nickName = snapshot.getString("nickname") ?: "이름 없음"
                        level = snapshot.getLong("level")?.toInt() ?: 1
                        exp = snapshot.getLong("exp")?.toInt() ?: 0
                        mannerTemp = snapshot.getDouble("mannerTemp") ?: 50.0

                        // 아바타 이미지 이름(String)을 리소스 ID(Int)로 변환
                        val avatarStr = snapshot.getString("avatarId") ?: "img_avatar_police"
                        val tempAvatarId = context.resources.getIdentifier(avatarStr, "drawable", context.packageName)
                        avatarResId = if (tempAvatarId != 0) tempAvatarId else R.drawable.img_avatar_police

                        // 악세사리 목록 처리
                        accResIds.clear()
                        val savedAccIds = snapshot.get("accIds") as? List<String>
                        savedAccIds?.forEach { idStr ->
                            val resId = context.resources.getIdentifier(idStr, "drawable", context.packageName)
                            if (resId != 0) accResIds.add(resId)
                        }
                    }
                }
        } else null

        // 화면이 꺼지면 리스너 연결 끊기 (데이터 낭비 방지)
        onDispose { listener?.remove() }
    }

    // 6. [팝업 표시 로직]
    // 상태 변수가 true일 때만 해당 Composable 함수를 실행해서 팝업을 띄워.
    if (showRulePopup) {
        RulePopupDialog(onDismiss = { showRulePopup = false })
    }
    if (showAttendancePopup) {
        AttendancePopupDialog(onDismiss = { showAttendancePopup = false })
    }

    // 7. [화면 레이아웃 구성: Scaffold]
    // Scaffold는 앱의 기본 뼈대(상단바, 하단바, 본문 등)를 잡아주는 컴포넌트야.
    Scaffold(
        modifier = Modifier.blur(blurRadius), // 위에서 만든 blur 애니메이션 적용
        bottomBar = {
            // [하단 네비게이션 바]
            NavigationBar(containerColor = Color.White) {
                // 각 아이템(버튼) 정의
                // 1. 홈 (현재 화면)
                NavigationBarItem(
                    icon = { Image(painterResource(R.drawable.ic_home), contentDescription = "Home", modifier = Modifier.size(28.dp)) },
                    label = { Text("홈") },
                    selected = true, // 항상 선택된 상태로 표시
                    onClick = { showAttendancePopup = false }
                )
                // 2. 지도 (MapActivity로 이동)
                NavigationBarItem(
                    icon = { Image(painterResource(R.drawable.ic_map), contentDescription = "Map", modifier = Modifier.size(28.dp)) },
                    label = { Text("지도") },
                    selected = false,
                    onClick = {
                        val intent = Intent(context, MapActivity::class.java)
                        context.startActivity(intent)
                    }
                )
                // 3. 출석 (팝업 띄우기)
                NavigationBarItem(
                    icon = {
                        Image(
                            painterResource(R.drawable.ic_attendance),
                            contentDescription = "Attendance",
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    label = { Text("출석") },
                    selected = showAttendancePopup, // 팝업이 떠있으면 선택된 것으로 표시
                    onClick = { showAttendancePopup = true }
                )

                // 4. 내 모임 (MyGroupActivity로 이동)
                NavigationBarItem(
                    icon = {
                        Image(
                            painterResource(R.drawable.ic_my_group),
                            contentDescription = "My Group",
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    label = { Text("내 모임") },
                    selected = false,
                    onClick = {
                        val intent = Intent(context, MyGroupActivity::class.java)
                        context.startActivity(intent)
                    }
                )

                // 5. 마이페이지 (MyPageActivity로 이동)
                NavigationBarItem(
                    icon = { Image(painterResource(R.drawable.ic_mypage), contentDescription = "MyPage", modifier = Modifier.size(28.dp)) },
                    label = { Text("마이페이지") },
                    selected = false,
                    onClick = {
                        val intent = Intent(context, MyPageActivity::class.java)
                        context.startActivity(intent)
                    }
                )
            }
        }
    ) { innerPadding ->
        // [본문 내용]
        // Column: 요소들을 세로로 배치
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding) // 하단바에 가려지지 않게 패딩 적용
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // (1) 상단 타이틀 이미지
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_title_text),
                    contentDescription = "타이틀",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(0.75f).padding(top = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp)) // 여백

            // (2) 중앙 아바타 영역 (Box는 겹쳐서 배치 가능)
            Box(contentAlignment = Alignment.Center, modifier = Modifier.weight(1f).fillMaxSize()) {
                // 기본 아바타
                Image(
                    painter = painterResource(id = avatarResId),
                    contentDescription = "Avatar",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().clickable {
                        // 클릭 시 아바타 꾸미기 화면으로 이동
                        context.startActivity(Intent(context, AvatarActivity::class.java))
                    }
                )
                // 악세사리들 (반복문으로 겹쳐서 그림)
                accResIds.forEach { resId ->
                    Image(
                        painter = painterResource(id = resId),
                        contentDescription = "Acc",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().clickable {
                            context.startActivity(Intent(context, AvatarActivity::class.java))
                        }
                    )
                }
            }

            // (3) 하단 정보 영역 (Row: 가로 배치)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
                    .height(IntrinsicSize.Min), // 자식들 높이에 맞춤
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 왼쪽: 유저 정보 (닉네임, 레벨, 경험치, 신용도)
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // ★ [수정됨] 닉네임 부분을 클릭하면 전적 화면(HistoryActivity)으로 이동!
                    Column(
                        modifier = Modifier
                            .clickable {
                                val intent = Intent(context, HistoryActivity::class.java)
                                context.startActivity(intent)
                            }
                    ) {
                        // 닉네임과 클릭 유도 화살표(>)를 같이 배치
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(nickName, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(">", fontSize = 24.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Lv. $level", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        val maxExp = level * 100
                        val expPercent = if (maxExp > 0) (exp.toFloat() / maxExp) * 100 else 0f

                        // 소수점 1자리까지만 예쁘게 표시 (예: Exp 50.0%)
                        Text("Exp ${String.format("%.1f", expPercent)}%", fontSize = 15.sp)
                    }
                    Text(text = "신용도 ${mannerTemp}", fontSize = 15.sp)

                }

                // 오른쪽: 게임 규칙 버튼 & 명언
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Bottom,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .width(230.dp)
                        .fillMaxHeight()
                ) {
                    // 게임 규칙 버튼
                    Surface(
                        color = Color(0xFFEEEEEE),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .clickable { showRulePopup = true } // 클릭 시 팝업 띄움
                            .padding(bottom = 12.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_rulebook), contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.Unspecified)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("게임 규칙", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    // 명언 텍스트
                    Text(
                        text = "\"$randomQuote\"",
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color.DarkGray,
                        textAlign = TextAlign.End,
                        lineHeight = 24.sp,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

// [규칙 팝업 다이얼로그]
@Composable
fun RulePopupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    // 텍스트 파일 읽어오기
    val rulesText = remember {
        try {
            val inputStream = context.resources.openRawResource(R.raw.rules)
            BufferedReader(InputStreamReader(inputStream)).use { it.readText() }
        } catch (e: Exception) { "규칙 파일을 불러올 수 없습니다." }
    }

    // Dialog 컴포넌트 사용
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(0.95f).height(700.dp).padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // 헤더 (제목 + 닫기 버튼)
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text("게임 규칙", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterStart))
                    Icon(Icons.Default.Close, contentDescription = "닫기", modifier = Modifier.align(Alignment.CenterEnd).clickable { onDismiss() })
                }
                Spacer(modifier = Modifier.height(16.dp))
                // 본문 (스크롤 가능하게 설정)
                Box(modifier = Modifier.weight(1f).background(Color(0xFFF8F8F8), RoundedCornerShape(8.dp)).padding(12.dp)) {
                    Text(rulesText, fontSize = 16.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
                }
                Spacer(modifier = Modifier.height(16.dp))
                // 하단 확인 버튼
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally), colors = ButtonDefaults.buttonColors(containerColor = Color.Black)) {
                    Text("확인", color = Color.White)
                }
            }
        }
    }
}

// [출석 팝업 다이얼로그] - 여기가 로직이 꽤 복잡해!
@Composable
fun AttendancePopupDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val db = Firebase.firestore
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    // 팝업에 표시할 모임 제목과 ID
    var targetMeetingTitle by remember { mutableStateOf<String?>(null) }
    var targetMeetingId by remember { mutableStateOf<String?>(null) }

    // 로딩 중인지, 팝업을 보여줄지 결정하는 변수
    var isLoading by remember { mutableStateOf(true) }
    var shouldShowDialog by remember { mutableStateOf(true) }

    // [출석 가능한 모임 찾기 로직]
    // LaunchedEffect(Unit): 컴포넌트가 처음 생성될 때 딱 한 번 실행됨.
    LaunchedEffect(Unit) {
        if (currentUser != null) {
            // 내가 참여자로 등록된 모임들 검색
            db.collection("meetings")
                .whereArrayContains("participantIds", currentUser.uid)
                .get()
                .addOnSuccessListener { result ->
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                    val now = Date() // 현재 시간

                    var foundTitle: String? = null
                    var foundId: String? = null

                    var instantEnter = false // 바로 입장할지 여부
                    var enterIntent: Intent? = null

                    // 검색된 모임들을 하나씩 확인
                    for (document in result) {
                        // 이미 종료된 모임은 패스
                        val meetingStatus = document.getString("meetingStatus")
                        if (meetingStatus == "ENDED") {
                            continue
                        }

                        // 데이터 가져오기
                        val title = document.getString("title") ?: "제목 없음"
                        val dateStr = document.getString("dateString") ?: ""
                        val hostUid = document.getString("hostUid") ?: ""
                        val checkedInUids = (document.get("checkedInUids") as? List<String>) ?: emptyList()

                        try {
                            // 모임 시간 파싱 및 전후 30분 계산
                            val startTime = sdf.parse(dateStr)
                            if (startTime != null) {
                                val cal = Calendar.getInstance()

                                cal.time = startTime
                                cal.add(Calendar.MINUTE, -30)
                                val checkInStart = cal.time // 출석 시작 가능 시간 (30분 전)

                                cal.time = startTime
                                cal.add(Calendar.MINUTE, 30)
                                val checkInEnd = cal.time // 출석 마감 시간 (30분 후)

                                // 현재 시간이 출석 가능 시간 내라면?
                                if (now.after(checkInStart) && now.before(checkInEnd)) {
                                    val isMeHost = (hostUid == currentUser.uid)
                                    val isAlreadyCheckedIn = checkedInUids.contains(currentUser.uid)

                                    // 내가 방장이거나, 이미 출석체크를 했다면 -> 팝업 없이 바로 입장 시도
                                    if (isMeHost || isAlreadyCheckedIn) {
                                        instantEnter = true

                                        if (isMeHost) {
                                            // 방장 화면으로 이동
                                            enterIntent = Intent(context, AttendanceHostActivity::class.java).apply {
                                                putExtra("meetingId", document.id)
                                            }
                                        } else {
                                            // 참여자 화면으로 이동
                                            enterIntent = Intent(context, AttendanceParticipantActivity::class.java).apply {
                                                putExtra("meetingId", document.id)
                                            }
                                        }
                                        break // 하나 찾았으면 반복문 종료
                                    }
                                    else {
                                        // 출석 안 했으면 팝업에 띄울 정보 저장
                                        foundTitle = title
                                        foundId = document.id
                                        break
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Attendance", "Date parsing error", e)
                        }
                    }

                    // 결과 처리
                    if (instantEnter && enterIntent != null) {
                        shouldShowDialog = false // 팝업 안 띄움
                        context.startActivity(enterIntent) // 화면 이동
                        onDismiss() // 팝업 닫기
                    } else {
                        targetMeetingTitle = foundTitle
                        targetMeetingId = foundId
                        isLoading = false // 로딩 끝
                    }
                }
                .addOnFailureListener {
                    isLoading = false
                }
        } else {
            isLoading = false
        }
    }

    // 팝업 UI (shouldShowDialog가 true일 때만 그림)
    if (shouldShowDialog) {
        Dialog(onDismissRequest = onDismiss) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isLoading) {
                        // 로딩 중일 때
                        CircularProgressIndicator(color = Color.Black)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("모임 확인 중...", fontSize = 14.sp)
                    }
                    else if (targetMeetingTitle != null && targetMeetingId != null) {
                        // 출석 가능한 모임이 있을 때
                        Text("🔔 출석 체크", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0066FF))
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("@${targetMeetingTitle}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                        Text("모임에 출석하시겠습니까?", fontSize = 16.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                // 출석 버튼 클릭 시 참여자 화면으로 이동
                                val intent = Intent(context, AttendanceParticipantActivity::class.java)
                                intent.putExtra("meetingId", targetMeetingId)
                                context.startActivity(intent)
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Black),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("출석하기!", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    } else {
                        // 출석 가능한 모임이 없을 때
                        Text("현재 출석 가능한 모임이 없습니다.", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("(모임 시간 전 30분부터 가능)", fontSize = 13.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEEEEEE)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("확인", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
