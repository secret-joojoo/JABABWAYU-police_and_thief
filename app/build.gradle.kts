import java.util.Properties // 이거 맨 위에 추가

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.gms.google.services)
}

// local.properties 파일 읽어오는 로직
val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}



android {
    namespace = "com.example.police_and_thief"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.example.police_and_thief"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"


    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
         targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    defaultConfig {
        // ... (minSdk 등등)

        val kakaoKey = localProperties.getProperty("kakao.api.key") ?: ""

        // 1. 매니페스트용 (아까 한 거)
        manifestPlaceholders["KAKAO_APP_KEY"] = kakaoKey

        // ★ 2. 코틀린 코드용 (이걸 추가해야 해!)
        // "String" 타입의 "KAKAO_APP_KEY"라는 변수를 BuildConfig 파일에 만들어라!
        // (주의: 값 부분에 따옴표(\")가 양쪽에 있어야 문자열로 인식됨)
        buildConfigField("String", "KAKAO_APP_KEY", "\"$kakaoKey\"")
    }

    // ★ 중요: 최신 안드로이드 스튜디오에서는 이걸 켜줘야 BuildConfig가 생성돼
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.identity.android.legacy)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Import the Firebase BoM
    implementation(platform("com.google.firebase:firebase-bom:33.1.0"))
    // Add the dependencies for any other desired Firebase products
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth") // 로그인
    implementation("com.google.firebase:firebase-firestore") // 유저 정보/전적 저장
    implementation("com.google.android.gms:play-services-auth:20.7.0") // 구글 로그인 UI

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")


    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")

    implementation("com.google.android.gms:play-services-location:21.0.1")

    implementation("com.google.firebase:firebase-firestore-ktx:24.10.3")

    implementation("androidx.core:core-splashscreen:1.0.1")

    implementation("com.kakao.maps.open:android:2.12.8") // 카카오 맵 V2 SDK
    implementation("com.kakao.sdk:v2-all:2.20.0") // 카카오 로그인, 지도 등 통합 SDK

    implementation("io.coil-kt:coil-compose:2.4.0")
    implementation("com.google.zxing:core:3.5.2")
    implementation("androidx.camera:camera-camera2:1.3.0")
    implementation("androidx.camera:camera-lifecycle:1.3.0")
    implementation("androidx.camera:camera-view:1.3.0")
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.2")
}