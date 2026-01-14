package com.example.police_and_thief

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// ==========================================
// [데이터 클래스: 아이템 정보 설계도]
// 아바타나 장비 하나가 가져야 할 필수 정보들을 정의해 둔 곳이야.
// ==========================================
data class AvatarItem(
    val id: String,         // DB 저장용 고유 ID (예: "img_police_lv3")
    val resId: Int,         // 실제 이미지 파일 주소 (R.drawable.xxx)
    val unlockLevel: Int,   // 해금 레벨 (이 레벨보다 낮으면 못 입음)
    val name: String,       // 화면에 보여줄 이름 (예: "경찰 모자")
    val type: String        // 아이템 종류 (avatar, police_gear 등 호환성 검사용)
)

// ==========================================
// [액티비티: 화면 관리자]
// 앱에서 이 화면을 켤 때 가장 먼저 실행되는 곳이지.
// ==========================================
class AvatarActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // "이제부터 Compose로 만든 AvatarScreen을 화면에 그려라!"
        setContent {
            MaterialTheme {
                // onBack: 뒤로가기 버튼을 누르면 finish()를 실행해서 화면을 끄도록 전달
                AvatarScreen(onBack = { finish() })
            }
        }
    }
}

// ==========================================
// [메인 화면 UI 함수]
// 여기서부터 실제 화면 디자인과 로직이 시작돼.
// ==========================================
@Composable
fun AvatarScreen(onBack: () -> Unit) {
    // [0] 기본 도구 준비 (Context, DB, 로그인 정보)
    val context = LocalContext.current
    val db = Firebase.firestore
    val auth = Firebase.auth
    val currentUser = auth.currentUser

    // ==========================================
    // [1. 아이템 목록 정의] (메뉴판 만들기)
    // 실제 앱에선 DB에서 불러올 수도 있지만, 지금은 고정된 리스트로 관리해.
    // ==========================================

    // 1-1. 아바타(몸통) 목록
    val avatars = listOf(
        AvatarItem("img_avatar_police", R.drawable.img_avatar_police, 0, "경찰 토끼 (Lv.1)", "avatar"),
        AvatarItem("img_avatar_thief", R.drawable.img_avatar_thief, 10, "도둑 토끼 (Lv.10)", "avatar"),
        AvatarItem("img_avatar_eulachacha", R.drawable.img_avatar_eulachacha, 21, "으라차차 토끼 (Lv.21)", "avatar"),
        AvatarItem("img_avatar_santa", R.drawable.img_avatar_santa, 51, "산타 토끼 (Lv.51)", "avatar"),
        AvatarItem("img_avatar_magician", R.drawable.img_avatar_magician, 61, "마술사 토끼 (Lv.61)", "avatar"),
        AvatarItem("img_avatar_judge", R.drawable.img_avatar_judge, 81, "판사 토끼 (Lv.81)", "avatar")
    )

    // 1-2. 경찰 장비 리스트
    val policeAccs = listOf(
        AvatarItem("img_police_lv3", R.drawable.img_police_lv3, 3, "경찰 모자 (Lv.3)", "police_gear"),
        AvatarItem("img_police_lv7", R.drawable.img_police_lv7, 7, "경찰 복장 (Lv.7)", "police_gear")
    )

    // 1-3. 도둑 장비 리스트
    val thiefAccs = listOf(
        AvatarItem("img_thief_lv20", R.drawable.img_thief_lv20, 20, "도둑 모자 (Lv.20)", "thief_gear"),
        AvatarItem("img_thief_lv30", R.drawable.img_thief_lv30, 30, "도둑 복면 (Lv.30)", "thief_gear"),
        AvatarItem("img_thief_lv40", R.drawable.img_thief_lv40, 40, "도둑 신발 (Lv.40)", "thief_gear"),
        AvatarItem("img_thief_lv50", R.drawable.img_thief_lv50, 50, "전설의 도둑 (Lv.50)", "thief_gear")
    )

    // 1-4. 으라차차 장비 리스트
    val eulachachaAccs = listOf(
        AvatarItem("img_eulachacha_lv25", R.drawable.img_eulachacha_lv25, 25, "으라차차 부츠 (Lv.25)", "eulachacha_gear"),
        AvatarItem("img_eulachacha_lv32", R.drawable.img_eulachacha_lv32, 32, "으라차차 마크 (Lv.32)", "eulachacha_gear"),
        AvatarItem("img_eulachacha_lv40", R.drawable.img_eulachacha_lv40, 40, "으라차차 망토 (Lv.40)", "eulachacha_gear"),
        AvatarItem("img_eulachacha_lv45", R.drawable.img_eulachacha_lv45, 45, "으라차차 가면 (Lv.45)", "eulachacha_gear")
    )

    // 1-5. 산타 장비 리스트
    val santaAccs = listOf(
        AvatarItem("img_santa_lv58", R.drawable.img_santa_lv58, 58, "산타 신발 (Lv.58)", "santa_gear"),
        AvatarItem("img_santa_lv66", R.drawable.img_santa_lv66, 66, "산타 옷 (Lv.66)", "santa_gear"),
        AvatarItem("img_santa_lv74", R.drawable.img_santa_lv74, 74, "풍성한 수염 (Lv.74)", "santa_gear"),
        AvatarItem("img_santa_lv80", R.drawable.img_santa_lv80, 80, "산타 모자 (Lv.80)", "santa_gear")
    )

    // 1-6. 마술사 장비 리스트
    val magicianAccs = listOf(
        AvatarItem("img_magician_lv65", R.drawable.img_magician_lv65, 65, "마술사 연미복 (Lv.65)", "magician_gear"),
        AvatarItem("img_magician_lv70", R.drawable.img_magician_lv70, 70, "나비 넥타이 (Lv.70)", "magician_gear"),
        AvatarItem("img_magician_lv75", R.drawable.img_magician_lv75, 75, "광택 구두 (Lv.75)", "magician_gear"),
        AvatarItem("img_magician_lv80", R.drawable.img_magician_lv80, 80, "마술 지팡이 (Lv.80)", "magician_gear"),
        AvatarItem("img_magician_lv85", R.drawable.img_magician_lv85, 85, "비둘기 친구 (Lv.85)", "magician_gear"),
        AvatarItem("img_magician_lv90", R.drawable.img_magician_lv90, 90, "미스터리 모자 (Lv.90)", "magician_gear")
    )

    // 1-7. 판사 장비 리스트
    val judgeAccs = listOf(
        AvatarItem("img_judge_lv87", R.drawable.img_judge_lv87, 87, "판사 법복 (Lv.87)", "judge_gear"),
        AvatarItem("img_judge_lv94", R.drawable.img_judge_lv94, 94, "정의의 망치 (Lv.94)", "judge_gear"),
        AvatarItem("img_judge_lv100", R.drawable.img_judge_lv100, 100, "대법관의 가발 (Lv.100)", "judge_gear")
    )

    // ==========================================
    // [2. 상태 변수 및 로직 함수]
    // 화면이 기억하고 있어야 할 값들(State)이야. 값이 바뀌면 화면이 다시 그려져.
    // ==========================================

    var userLevel by remember { mutableIntStateOf(1) }           // 내 레벨
    var selectedAvatar by remember { mutableStateOf(avatars[0]) } // 선택된 몸통
    val selectedAccs = remember { mutableStateListOf<AvatarItem>() } // 선택된 장비들 (리스트)
    var selectedTabIndex by remember { mutableIntStateOf(0) }    // 현재 보고 있는 탭 번호
    var showExitDialog by remember { mutableStateOf(false) }     // 종료 팝업 표시 여부

    // [도우미 함수] 특정 아바타 ID가 입을 수 있는 장비 타입이 뭔지 알려주는 역할
    // 예: "img_avatar_police"를 넣으면 -> "police_gear"를 뱉어냄.
    fun getAllowedGearType(avatarId: String): String {
        return when (avatarId) {
            "img_avatar_police" -> "police_gear"
            "img_avatar_thief" -> "thief_gear"
            "img_avatar_eulachacha" -> "eulachacha_gear"
            "img_avatar_santa" -> "santa_gear"
            "img_avatar_magician" -> "magician_gear"
            "img_avatar_judge" -> "judge_gear"
            else -> "none"
        }
    }

    // ==========================================
    // [3. 데이터 로딩] (앱 켜질 때 1번 실행)
    // ==========================================
    LaunchedEffect(Unit) {
        if (currentUser != null) {
            // Firestore 'users' 컬렉션에서 내 정보를 가져옴
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        userLevel = document.getLong("level")?.toInt() ?: 1

                        // 저장되어 있던 몸통 불러와서 적용
                        val savedAvatarId = document.getString("avatarId")
                        avatars.find { it.id == savedAvatarId }?.let { selectedAvatar = it }

                        // 저장되어 있던 장비들 불러와서 리스트에 추가
                        val savedAccIds = document.get("accIds") as? List<String>
                        if (savedAccIds != null) {
                            // 모든 장비를 합친 큰 리스트에서 검색
                            val allAccs = policeAccs + thiefAccs + eulachachaAccs + santaAccs + magicianAccs + judgeAccs
                            savedAccIds.forEach { id ->
                                allAccs.find { it.id == id }?.let { selectedAccs.add(it) }
                            }
                        }
                    }
                }
        }
    }

    // [뒤로가기 핸들러]
    // 폰의 뒤로가기 버튼을 누르면 바로 꺼지지 않고 팝업을 띄움
    BackHandler(enabled = true) { showExitDialog = true }

    // ==========================================
    // [4. UI 화면 그리기 시작]
    // ==========================================

    // [종료 확인 팝업] (showExitDialog가 true일 때만 화면에 나타남)
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text(text = "알림") },
            text = { Text(text = "현재 모습이 저장되지 않았습니다!\n홈 화면으로 돌아가시겠습니까?") },
            confirmButton = {
                TextButton(onClick = { showExitDialog = false; onBack() }) { // "예" 누르면 종료
                    Text("예", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) { // "아니오" 누르면 팝업만 닫힘
                    Text("아니오", color = Color.Black)
                }
            },
            containerColor = Color.White
        )
    }

    // 전체 화면 레이아웃 (세로 배치)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding() // 상태바(배터리 표시 등) 가리지 않게 패딩
            .background(Color.White)
    ) {
        // [A. 상단 헤더] (제목 + X 버튼)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("아바타 꾸미기", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Black)

            // 닫기(X) 버튼
            IconButton(
                onClick = { showExitDialog = true },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(Icons.Default.Close, contentDescription = "닫기", tint = Color.Black)
            }
        }

        // [B. 캐릭터 미리보기 영역] (회색 박스)
        Box(
            modifier = Modifier
                .weight(1f) // 남은 공간을 얘가 차지함
                .fillMaxWidth()
                .background(Color(0xFFF0F0F0)), // 회색 배경
            contentAlignment = Alignment.Center
        ) {
            // 1. 몸통 그리기 (제일 밑바닥 레이어)
            Image(
                painter = painterResource(id = selectedAvatar.resId),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(250.dp)
            )

            // 2. 장비 그리기 (몸통 위에 겹치기)
            // ★ 중요: 레벨 순서대로 정렬(sortedBy)해서 그린다!
            // 이렇게 해야 레벨 낮은 옷(속옷/기본옷)이 먼저 그려지고, 레벨 높은 옷(망토/겉옷)이 그 위에 덮임.
            selectedAccs.sortedBy { it.unlockLevel }.forEach { acc ->
                Image(
                    painter = painterResource(id = acc.resId),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(250.dp)
                )
            }
        }

        // [C. 탭 메뉴] (스크롤 가능한 탭)
        val tabs = listOf("아바타", "경찰 장비", "도둑 장비", "으라차차 장비", "산타 장비", "마술사 장비", "판사 장비")

        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = Color.White,
            contentColor = Color.Black,
            edgePadding = 0.dp // 왼쪽 불필요한 여백 제거
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index }, // 탭 누르면 selectedTabIndex 변경
                    text = {
                        // 선택된 탭은 글씨를 굵게
                        Text(title, fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal)
                    }
                )
            }
        }

        // [D. 아이템 리스트 (그리드)]
        Box(modifier = Modifier.weight(1f)) {
            // 현재 선택된 탭 번호에 맞춰서 보여줄 리스트를 갈아끼움
            val currentList = when (selectedTabIndex) {
                0 -> avatars
                1 -> policeAccs
                2 -> thiefAccs
                3 -> eulachachaAccs
                4 -> santaAccs
                5 -> magicianAccs
                6 -> judgeAccs
                else -> emptyList()
            }

            // 격자 무늬(Grid)로 아이템 배치 (가로 3칸)
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(currentList) { item ->
                    // 1. 잠금 여부 (내 레벨이 해금 레벨보다 낮으면 잠김)
                    val isLocked = userLevel < item.unlockLevel

                    // 2. 선택 여부 (내가 지금 입고 있는 건가?)
                    val isSelected = if (item.type == "avatar") {
                        selectedAvatar.id == item.id
                    } else {
                        selectedAccs.contains(item)
                    }

                    // 아이템 카드 하나 디자인
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .border( // 선택되면 파란 테두리
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) Color.Blue else Color.LightGray,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .background(if (isSelected) Color(0xFFE3F2FD) else Color.White) // 배경색 변경
                            .clickable {
                                // === [클릭 시 실행되는 로직] ===

                                // (1) 잠겨있으면 안내 메시지 띄우고 끝
                                if (isLocked) {
                                    Toast.makeText(context, "Lv.${item.unlockLevel}에 해금됩니다!", Toast.LENGTH_SHORT).show()
                                    return@clickable
                                }

                                // (2) 몸통(아바타) 클릭 시
                                if (item.type == "avatar") {
                                    selectedAvatar = item // 몸통 교체

                                    // 직업이 바뀌었으니, 호환 안 되는 장비는 다 벗김
                                    val allowedType = getAllowedGearType(item.id)
                                    selectedAccs.removeIf { acc -> acc.type != allowedType }
                                }
                                // (3) 장비 클릭 시
                                else {
                                    val allowedType = getAllowedGearType(selectedAvatar.id)

                                    // 호환성 검사: 내 직업에 맞는 장비인가?
                                    if (item.type != allowedType) {
                                        val msg = when(allowedType) {
                                            "police_gear" -> "경찰 토끼는 경찰 장비만 착용 가능합니다!"
                                            "thief_gear" -> "도둑 토끼는 도둑 장비만 착용 가능합니다!"
                                            "eulachacha_gear" -> "으라차차 토끼는 으라차차 장비만 착용 가능합니다!"
                                            "santa_gear" -> "산타 토끼는 산타 장비만 착용 가능합니다!"
                                            "magician_gear" -> "마술사 토끼는 마술사 장비만 착용 가능합니다!"
                                            "judge_gear" -> "판사 토끼는 판사 장비만 착용 가능합니다!"
                                            else -> "이 아바타는 착용할 수 있는 장비가 없습니다."
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        return@clickable
                                    }

                                    // 입었다 벗었다 (Toggle) 기능
                                    if (selectedAccs.contains(item)) {
                                        selectedAccs.remove(item) // 이미 있으면 제거
                                    } else {
                                        selectedAccs.add(item)    // 없으면 추가
                                    }
                                }
                            }
                            .padding(8.dp)
                    ) {
                        // 아이템 이미지 + 자물쇠 아이콘
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = item.resId),
                                contentDescription = item.name,
                                // 잠겨있으면 흐리게(투명도 0.3)
                                modifier = Modifier.size(60.dp).alpha(if (isLocked) 0.3f else 1f)
                            )
                            if (isLocked) {
                                Icon(Icons.Default.Lock, contentDescription = "Locked", tint = Color.Gray, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        // 아이템 이름 텍스트
                        Text(
                            text = item.name,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,   // 가운데 정렬
                            maxLines = 2,                   // 최대 2줄까지 허용
                            overflow = TextOverflow.Ellipsis, // 넘치면 ... 처리
                            lineHeight = 14.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(35.dp) // ★ 텍스트 박스 높이 고정! (줄 안 맞아서 삐뚤빼뚤해지는 것 방지)
                                .wrapContentHeight(Alignment.CenterVertically) // 박스 안에서 수직 중앙 정렬
                        )
                    }
                }
            }
        }

        // [E. 저장 버튼]
        Button(
            onClick = {
                if (currentUser != null) {
                    // ★ 중요: 저장할 때도 레벨 순서대로 정렬해서 저장한다!
                    // 그래야 나중에 불러올 때 순서가 뒤섞이지 않음.
                    val sortedAccIds = selectedAccs
                        .sortedBy { it.unlockLevel } // 레벨 오름차순 정렬
                        .map { it.id }               // ID만 쏙쏙 뽑아서 리스트 만들기

                    val data = hashMapOf(
                        "avatarId" to selectedAvatar.id,
                        "accIds" to sortedAccIds
                    )

                    // DB 업데이트
                    db.collection("users").document(currentUser.uid)
                        .update(data as Map<String, Any>)
                        .addOnSuccessListener {
                            Toast.makeText(context, "저장되었습니다!", Toast.LENGTH_SHORT).show()
                            onBack() // 저장 성공하면 화면 닫기
                        }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
        ) {
            Text("현재 모습 저장하기", fontSize = 18.sp, color = Color.White)
        }
    }
}