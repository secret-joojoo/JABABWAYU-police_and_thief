package com.example.police_and_thief

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

// 화면 상태를 정의하는 열거형 (로딩중, 로그인필요, 닉네임입력필요)
enum class LoginScreenState {
    LOADING, LOGIN, NICKNAME_INPUT
}

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = Firebase.auth
        db = Firebase.firestore

        // 구글 로그인 설정
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        setContent {
            MaterialTheme {
                // 현재 어떤 화면을 보여줄지 결정하는 상태 변수
                var currentScreen by remember { mutableStateOf(LoginScreenState.LOADING) }

                // [핵심] 앱 켜지자마자 딱 한 번 실행되는 자동 로그인 로직
                LaunchedEffect(Unit) {
                    val currentUser = auth.currentUser
                    if (currentUser != null) {
                        // 1. 이미 로그인된 상태라면 -> DB 확인
                        db.collection("users").document(currentUser.uid).get()
                            .addOnSuccessListener { document ->
                                if (document.exists() && document.getString("nickname") != null) {
                                    // 2. 닉네임까지 있으면 -> 바로 메인으로 이동
                                    moveToMain()
                                } else {
                                    // 3. 로그인은 됐는데 닉네임이 없으면 -> 입력 화면으로
                                    currentScreen = LoginScreenState.NICKNAME_INPUT
                                }
                            }
                            .addOnFailureListener {
                                // DB 조회 실패하면 안전하게 로그인 화면으로
                                currentScreen = LoginScreenState.LOGIN
                            }
                    } else {
                        // 4. 로그인 안 된 상태면 -> 로그인 화면으로
                        currentScreen = LoginScreenState.LOGIN
                    }
                }

                // 상태에 따라 다른 화면 보여주기
                when (currentScreen) {
                    LoginScreenState.LOADING -> LoadingScreen() // 로딩 화면
                    LoginScreenState.LOGIN -> LoginScreen(
                        onGoogleSignInClick = { signInGoogle() }
                    )
                    LoginScreenState.NICKNAME_INPUT -> NicknameInputScreen(
                        onConfirm = { nickname, birthYear ->
                            saveNewUser(nickname, birthYear)
                        }
                    )
                }
            }
        }
    }

    // 구글 로그인 결과 처리 런처
    // 주의: 여기서 state를 직접 바꿀 수 없으므로, 로그인이 성공하면 다시 DB 체크 로직을 타게 하거나
    // 콜백을 통해 화면을 갱신해야 하는데, 여기선 간단하게 Activity를 다시 로드하거나 checkUser logic을 호출
    private val launcher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(this, "구글 로그인 실패: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signInGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        launcher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // 로그인 성공 시 다시 DB 체크 로직 수행
                    // (Compose 상태를 갱신하기 위해 onCreate의 로직을 함수화하거나,
                    // 간단하게 닉네임 체크를 여기서 다시 수행)
                    checkUserAfterLogin()
                } else {
                    Toast.makeText(this, "파이어베이스 인증 실패", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // 로그인 성공 직후 호출되는 함수
    private fun checkUserAfterLogin() {
        val currentUser = auth.currentUser ?: return
        db.collection("users").document(currentUser.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists() && document.getString("nickname") != null) {
                    moveToMain()
                } else {
                    // 닉네임 없으면 UI를 닉네임 입력창으로 강제 전환
                    // (Compose라 여기서 state 변경이 까다로우니, 편법으로 setContent를 다시 호출하거나
                    // Activity를 재시작하는 방법이 있는데, 여기선 setContent 재호출 방식으로 처리)
                    setContent {
                        MaterialTheme {
                            NicknameInputScreen(
                                onConfirm = { nick, year -> saveNewUser(nick, year) }
                            )
                        }
                    }
                }
            }
    }

    private fun saveNewUser(nickname: String, birthYear: String) {
        val currentUser = auth.currentUser ?: return

        val userMap = hashMapOf(
            "uid" to currentUser.uid,
            "email" to currentUser.email,
            "nickname" to nickname,
            "birthYear" to birthYear.toInt(),
            "level" to 1,
            "exp" to 0,
            "mannerTemp" to 50.0,
            "avatarId" to "img_avatar_police",
            "accIds" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp()
        )

        db.collection("users").document(currentUser.uid)
            .set(userMap)
            .addOnSuccessListener {
                moveToMain()
            }
            .addOnFailureListener {
                Toast.makeText(this, "저장 실패: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun moveToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish() // 뒤로가기 눌러도 로그인 화면으로 안 오게 종료
    }
}

// [0] 로딩 화면 (자동 로그인 체크 중일 때 보여줌)
@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.Black)
    }
}

// [1] 로그인 버튼 화면
@Composable
fun LoginScreen(onGoogleSignInClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("경찰과 도둑", fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(50.dp))

            Button(
                onClick = onGoogleSignInClick,
                modifier = Modifier.fillMaxWidth(0.7f).height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray)
            ) {
                Text("Google 계정으로 로그인", color = Color.Black)
            }
        }
    }
}

// [2] 닉네임 & 생년월일 입력 화면 (변경 없음)
@Composable
fun NicknameInputScreen(onConfirm: (String, String) -> Unit) {
    val context = LocalContext.current
    val db = Firebase.firestore

    var nickname by remember { mutableStateOf("") }
    var birthYear by remember { mutableStateOf("") }
    var isChecking by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("환영합니다!", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(10.dp))
        Text("기본 정보를 설정해주세요.", fontSize = 16.sp, color = Color.Gray)

        Spacer(modifier = Modifier.height(30.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { if (it.length <= 10) nickname = it },
            label = { Text("닉네임 (최대 10자)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = birthYear,
            onValueChange = { newValue ->
                if (newValue.length <= 4 && newValue.all { it.isDigit() }) {
                    birthYear = newValue
                }
            },
            label = { Text("태어난 년도 (YYYY)") },
            placeholder = { Text("예: 2008") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(30.dp))

        Button(
            onClick = {
                if (nickname.isBlank() || birthYear.length != 4) {
                    Toast.makeText(context, "정보를 정확히 입력해주세요.", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isChecking = true

                db.collection("users")
                    .whereEqualTo("nickname", nickname)
                    .get()
                    .addOnSuccessListener { result ->
                        if (result.isEmpty) {
                            onConfirm(nickname, birthYear)
                        } else {
                            isChecking = false
                            Toast.makeText(context, "이미 존재하는 닉네임입니다.", Toast.LENGTH_SHORT).show()
                        }
                    }
            },
            enabled = !isChecking,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
        ) {
            if (isChecking) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("시작하기", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}