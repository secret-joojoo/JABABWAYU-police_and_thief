package com.example.police_and_thief.model

// 거점 정보 (운영자가 정한 위치)
data class GameBase(
    val id: String = "",        // 거점 고유 ID (예: "base_001")
    val name: String = "",      // 거점 이름 (예: "한강공원 입구")
    val lat: Double = 0.0,      // 위도
    val lng: Double = 0.0,      // 경도
    val address: String = ""    // 주소 (선택)
)

// 모임 정보
data class GameMeeting(
    val id: String = "",             // 모임 고유 ID
    val baseId: String = "",         // 어디서 모이는지 (GameBase의 id)
    val hostUid: String = "",        // 만든 사람 UID
    val title: String = "",          // 모임 이름
    val date: Long = 0,              // 날짜 및 시간 (Timestamp)
    val maxMembers: Int = 10,        // 최대 인원
    val options: Map<String, Any> = emptyMap() // 나이대, 뒷풀이 여부 등 옵션
)