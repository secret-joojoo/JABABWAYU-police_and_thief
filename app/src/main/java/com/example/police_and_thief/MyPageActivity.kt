package com.example.police_and_thief

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MyPageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MyPageScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun MyPageScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 전체 화면 구성
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding() // 상태바에 가려지지 않게
    ) {
        // === [1] 헤더 (뒤로가기 + 제목) ===
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = Color.Black
                )
            }

            Text(
                text = "마이페이지",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
        }

        Divider(color = Color(0xFFEEEEEE), thickness = 1.dp)

        // === [2] 메뉴 리스트 ===
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // [삭제됨] 모임 출석하기
            // [삭제됨] 모임 출석 QR 확인하기
            // [삭제됨] 예정된 모임 보기

            // 1. 알림 설정
            MyPageMenuItem(
                iconResId = R.drawable.ic_notification, // 알림 아이콘
                text = "알림 설정",
                onClick = {
                    val intent = Intent(context, NotificationSettingsActivity::class.java)
                    context.startActivity(intent)
                }
            )

            // 2. 고객 문의
            MyPageMenuItem(
                iconResId = R.drawable.ic_cs, // 문의 아이콘
                text = "고객 문의",
                onClick = {
                    val intent = Intent(context, CustomerServiceActivity::class.java)
                    context.startActivity(intent)
                }
            )
        }
    }
}

// [메뉴 아이템 디자인 컴포넌트] (재사용)
@Composable
fun MyPageMenuItem(
    iconResId: Int,
    text: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // (1) 아이콘 이미지
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            // (2) 메뉴 텍스트
            Text(
                text = text,
                fontSize = 16.sp,
                color = Color.Black,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            // (3) 오른쪽 화살표
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = Color.LightGray,
                modifier = Modifier.size(24.dp)
            )
        }

        // (4) 하단 구분선
        Divider(color = Color(0xFFF5F5F5), thickness = 1.dp)
    }
}