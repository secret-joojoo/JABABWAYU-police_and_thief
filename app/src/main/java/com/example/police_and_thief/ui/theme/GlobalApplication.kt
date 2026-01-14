package com.example.police_and_thief

import android.app.Application
import com.kakao.vectormap.KakaoMapSdk

class GlobalApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // ▼▼▼ 여기에 본인의 네이티브 앱 키를 문자열로 넣으세요 ▼▼▼
        KakaoMapSdk.init(this, BuildConfig.KAKAO_APP_KEY)
    }
}