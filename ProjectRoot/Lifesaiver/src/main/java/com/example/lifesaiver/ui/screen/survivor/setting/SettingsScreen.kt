package com.example.lifesaiver.ui.screen.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

private val ColorBackground = Color(0xFF0D0F11)
private val ColorCard = Color(0xFF1D2124)
private val ColorTextMain = Color(0xFFFFFFFF)
private val ColorTextSub = Color(0xFF8E8E93)
private val ColorDivider = Color(0x80FFFFFF)
private val ColorSwitchOn = Color(0xFFFF4D4D)
private val ColorSwitchOff = Color(0xFF8E8E93)

@Composable
fun SettingsScreen(
    isVoiceOn: Boolean,
    isShockOn: Boolean,
    profileName: String = "",
    profileGender: String = "",
    profileBirthDate: String = "",
    profileNotes: String = "",
    onVoiceToggle: (Boolean) -> Unit,
    onShockToggle: (Boolean) -> Unit,
    onBack: () -> Unit = {},
    onEditProfile: () -> Unit = {}
) {
    val scale = LocalAppScale.current
    val context = LocalContext.current

    // ▼▼▼ [수정 1] 영구 저장을 위한 SharedPreferences 불러오기 ▼▼▼
    val prefs = remember { context.getSharedPreferences("app_settings", Context.MODE_PRIVATE) }

    // ▼▼▼ [수정 2] 저장된 값을 불러와서 초기값으로 설정 (앱 껐다 켜도 기억함) ▼▼▼
    var voiceDontShowAgain by remember {
        mutableStateOf(prefs.getBoolean("voice_dont_show", false))
    }
    var shockDontShowAgain by remember {
        mutableStateOf(prefs.getBoolean("shock_dont_show", false))
    }

    var showVoiceDialog by remember { mutableStateOf(false) }
    var showShockDialog by remember { mutableStateOf(false) }


    // --- [Logic] 권한 및 음성 켜기 처리 ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            onVoiceToggle(true)
        }
    }

    val activateVoice = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            onVoiceToggle(true)
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(ColorBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = scaledDp(16, scale)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderSection(scale)
            UserInfoCard(scale, profileName, profileGender, profileBirthDate, profileNotes, onEditProfile)
            Spacer(modifier = Modifier.height(scaledDp(20, scale)))

            SettingsControlCard(
                scale = scale,
                isVoiceOn = isVoiceOn,
                isShockOn = isShockOn,
                onVoiceToggle = { shouldEnable ->
                    if (shouldEnable) {
                        // '다시 보지 않기'가 체크 안 되어 있으면(false) 팝업 띄움
                        if (!voiceDontShowAgain) {
                            showVoiceDialog = true
                        } else {
                            activateVoice()
                        }
                    } else {
                        onVoiceToggle(false)
                    }
                },
                onShockToggle = { shouldEnable ->
                    if (shouldEnable) {
                        if (!shockDontShowAgain) {
                            showShockDialog = true
                        } else {
                            onShockToggle(true)
                        }
                    } else {
                        onShockToggle(false)
                    }
                }
            )
        }

        // --- [Dialog] 음성 감지 설명 팝업 ---
        if (showVoiceDialog) {
            ExplanationDialog(
                scale = scale,
                title = "음성 감지 기능",
                description = "인터넷과 기지국 통신이 모두 끊기면 감지 모드가 시작됩니다.\n'살려주세요'와 같은 구조 요청을 인식하여 자동으로 주변에 신호를 보냅니다.",
                onDismiss = { showVoiceDialog = false },
                onConfirm = { dontShowAgain ->
                    // ▼▼▼ [수정 3] 사용자가 '다시 보지 않기'를 체크했다면 내부 저장소에 저장 ▼▼▼
                    if (dontShowAgain) {
                        prefs.edit().putBoolean("voice_dont_show", true).apply()
                        voiceDontShowAgain = true
                    }
                    showVoiceDialog = false
                    activateVoice()
                }
            )
        }

        // --- [Dialog] 충격 감지 설명 팝업 ---
        if (showShockDialog) {
            ExplanationDialog(
                scale = scale,
                title = "충격 감지 기능",
                description = "통신이 두절된 고립 상황에서\n강한 충격(낙상, 사고 등)이 감지되면 즉시 구조 모드로 전환되어 주변에 알립니다.",
                onDismiss = { showShockDialog = false },
                onConfirm = { dontShowAgain ->
                    // ▼▼▼ [수정 4] 충격 감지도 동일하게 저장 ▼▼▼
                    if (dontShowAgain) {
                        prefs.edit().putBoolean("shock_dont_show", true).apply()
                        shockDontShowAgain = true
                    }
                    showShockDialog = false
                    onShockToggle(true)
                }
            )
        }
    }
}

// --- [Component] 설명 팝업 (기존과 동일) ---
@Composable
fun ExplanationDialog(
    scale: Float,
    title: String,
    description: String,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    var isDontShowAgainChecked by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(scaledDp(320, scale))
                .clip(RoundedCornerShape(scaledDp(16, scale)))
                .background(ColorCard)
                .padding(scaledDp(24, scale)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                color = ColorTextMain,
                fontSize = scaledSp(18, scale),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(scaledDp(12, scale)))

            Text(
                text = description,
                color = ColorTextSub,
                fontSize = scaledSp(14, scale),
                lineHeight = scaledSp(20, scale),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(scaledDp(20, scale)))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isDontShowAgainChecked = !isDontShowAgainChecked },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isDontShowAgainChecked,
                    onCheckedChange = { isDontShowAgainChecked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = ColorSwitchOn,
                        uncheckedColor = ColorSwitchOff,
                        checkmarkColor = Color.White
                    )
                )
                Text(
                    text = "다시 보지 않기",
                    color = ColorTextMain,
                    fontSize = scaledSp(14, scale),
                    modifier = Modifier.padding(start = scaledDp(4, scale))
                )
            }

            Spacer(modifier = Modifier.height(scaledDp(20, scale)))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaledDp(48, scale))
                    .clip(RoundedCornerShape(scaledDp(8, scale)))
                    .background(ColorSwitchOn)
                    .clickable { onConfirm(isDontShowAgainChecked) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "확인",
                    color = Color.White,
                    fontSize = scaledSp(16, scale),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// --- (나머지 하위 컴포저블들은 기존 코드와 동일) ---
@Composable
private fun HeaderSection(scale: Float) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = scaledDp(40, scale), bottom = scaledDp(20, scale))) {
        Text(text = "설정", color = ColorTextMain, fontSize = scaledSp(20, scale), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun UserInfoCard(scale: Float, name: String, gender: String, birthDate: String, note: String, onEditClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(ColorCard, RoundedCornerShape(scaledDp(13, scale))).padding(horizontal = scaledDp(16, scale), vertical = scaledDp(20, scale))) {
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = scaledDp(20, scale))) {
            Text(text = "사용자 정보", color = ColorTextMain, fontSize = scaledSp(16, scale), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            Icon(painter = painterResource(id = R.drawable.ic_settings_edit_profile), contentDescription = "Edit", tint = ColorTextMain, modifier = Modifier.size(scaledDp(20, scale)).align(Alignment.CenterEnd).clickable { onEditClick() })
        }
        InfoRow(scale, "이름", name, true)
        InfoRow(scale, "성별", gender, true)
        InfoRow(scale, "생년월일", birthDate, true)
        InfoRow(scale, "특이사항", note, false)
    }
}

@Composable
private fun InfoRow(scale: Float, label: String, value: String, showDivider: Boolean) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = scaledDp(14, scale)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = ColorTextMain, fontSize = scaledSp(16, scale))
            Text(text = value, color = ColorTextMain, fontSize = scaledSp(16, scale))
        }
        if (showDivider) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ColorDivider))
    }
}

@Composable
private fun SettingsControlCard(scale: Float, isVoiceOn: Boolean, isShockOn: Boolean, onVoiceToggle: (Boolean) -> Unit, onShockToggle: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(ColorCard, RoundedCornerShape(scaledDp(13, scale))).padding(horizontal = scaledDp(16, scale))) {
        SettingToggleRow(scale, "음성 감지", isVoiceOn, onVoiceToggle, true)
        SettingToggleRow(scale, "충격 감지", isShockOn, onShockToggle, false)
    }
}

@Composable
private fun SettingToggleRow(scale: Float, label: String, isChecked: Boolean, onCheckedChange: (Boolean) -> Unit, showDivider: Boolean) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = scaledDp(12, scale)), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = label, color = ColorTextMain, fontSize = scaledSp(16, scale))
            CustomToggleSwitch(scale, isChecked, onCheckedChange)
        }
        if (showDivider) Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(ColorDivider))
    }
}

@Composable
private fun CustomToggleSwitch(scale: Float, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val width = scaledDp(52, scale)
    val height = scaledDp(32, scale)
    val thumbSize = scaledDp(28, scale)
    val padding = scaledDp(2, scale)
    val backgroundColor by animateColorAsState(targetValue = if (checked) ColorSwitchOn else ColorSwitchOff, animationSpec = tween(300), label = "BgColor")
    val thumbOffset by animateDpAsState(targetValue = if (checked) width - thumbSize - padding else padding, animationSpec = tween(300), label = "ThumbOffset")

    Box(
        modifier = Modifier.size(width, height).clip(RoundedCornerShape(100)).background(backgroundColor).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onCheckedChange(!checked) }
    ) {
        Box(modifier = Modifier.size(thumbSize).offset(x = thumbOffset).align(Alignment.CenterStart).background(Color.White, CircleShape))
    }
}
