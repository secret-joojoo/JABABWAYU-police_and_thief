package com.example.police_and_thief

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        const val CHANNEL_ID = "meeting_notification_channel"
        const val CHANNEL_NAME = "모임 알림"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // 0. [검문소] 설정 확인부터! (제일 중요)
        // SharedPreferences에서 사용자 설정을 불러온다.
        val prefs = context.getSharedPreferences("noti_settings", Context.MODE_PRIVATE)
        val alarmType = intent.getStringExtra("ALARM_TYPE") ?: "GENERAL"

        // 이 알람 타입이 켜져 있는지 확인 (기본값은 true)
        val isEnabled = when (alarmType) {
            "HOST_24H" -> prefs.getBoolean("host_24h", true)
            "HOST_1H" -> prefs.getBoolean("host_1h", true)
            "HOST_30M" -> prefs.getBoolean("host_30m", true)
            "GUEST_24H" -> prefs.getBoolean("guest_24h", true)
            "GUEST_1H" -> prefs.getBoolean("guest_1h", true)
            else -> true // 타입이 명시되지 않은 일반 알림은 그냥 보냄
        }

        // 설정이 꺼져있다면? 여기서 함수 종료! (알림 안 띄움)
        if (!isEnabled) {
            return
        }

        // ==========================================
        // 설정이 켜져있을 때만 아래 코드 실행
        // ==========================================

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 1. 인텐트에서 데이터 꺼내기
        val title = intent.getStringExtra("title") ?: "모임 알림"
        val message = intent.getStringExtra("message") ?: "곧 모임이 시작됩니다!"
        val meetingId = intent.getStringExtra("meetingId") ?: ""

        // 2. 알림 채널 생성 (Android 8.0 이상 필수)
        createNotificationChannel(notificationManager)

        // 3. 알림 클릭 시 이동할 화면 설정
        val contentIntent = Intent(context, MapActivity::class.java).apply {
            putExtra("targetMeetingId", meetingId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            meetingId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 4. 알림 구성
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // ★ 앱 아이콘으로 꼭 바꿔!
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        // 5. 알림 표시
        // 알람 타입별로 ID를 다르게 줘서 겹치지 않게 함
        val notificationId = meetingId.hashCode() + alarmType.hashCode()
        notificationManager.notify(notificationId, builder.build())
    }

    private fun createNotificationChannel(manager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "모임 관련 알림"
                    enableVibration(true)
                }
                manager.createNotificationChannel(channel)
            }
        }
    }
}