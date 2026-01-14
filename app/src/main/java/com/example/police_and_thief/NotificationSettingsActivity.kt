package com.example.police_and_thief

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class NotificationSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NotificationSettingsScreen(onBack = { finish() })
            }
        }
    }
}

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    // 설정 저장소 (SharedPreferences)
    val prefs = remember { context.getSharedPreferences("noti_settings", Context.MODE_PRIVATE) }

    // [상태 변수들] - 기본값은 true
    var host24h by remember { mutableStateOf(prefs.getBoolean("host_24h", true)) }
    var host1h by remember { mutableStateOf(prefs.getBoolean("host_1h", true)) }
    var host30m by remember { mutableStateOf(prefs.getBoolean("host_30m", true)) }

    var guest24h by remember { mutableStateOf(prefs.getBoolean("guest_24h", true)) }
    var guest1h by remember { mutableStateOf(prefs.getBoolean("guest_1h", true)) }

    // 값을 저장하는 함수
    fun savePref(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
    ) {
        // 1. 헤더
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
            Text("알림 설정", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Divider(color = Color(0xFFEEEEEE))

        Column(modifier = Modifier.padding(24.dp)) {

            // 2. 모임 생성했을 때 (방장)
            Text("모임 생성했을 때", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            SwitchRow("24시간 전 알림", host24h) {
                host24h = it
                savePref("host_24h", it)
            }
            SwitchRow("1시간 전 알림", host1h) {
                host1h = it
                savePref("host_1h", it)
            }
            SwitchRow("30분 전 알림", host30m) {
                host30m = it
                savePref("host_30m", it)
            }

            Spacer(modifier = Modifier.height(40.dp))

            // 3. 모임 참가했을 때 (참가자)
            Text("모임 참가했을 때", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))

            SwitchRow("24시간 전 알림", guest24h) {
                guest24h = it
                savePref("guest_24h", it)
            }
            SwitchRow("1시간 전 알림", guest1h) {
                guest1h = it
                savePref("guest_1h", it)
            }
        }
    }
}

// 스위치 한 줄 디자인 (재사용 컴포넌트)
@Composable
fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 16.sp, color = Color.Black)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color.Black,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Color.LightGray
            )
        )
    }
}