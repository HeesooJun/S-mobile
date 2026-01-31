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
import androidx.core.content.ContextCompat
import com.example.lifesaiver.R
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

private val ColorBackground = Color(0xFF0D0F11)
private val ColorCard = Color(0xFF1D2124)
private val ColorTextMain = Color(0xFFFFFFFF)
private val ColorDivider = Color(0x80FFFFFF)
private val ColorSwitchOn = Color(0xFFFF4D4D)
private val ColorSwitchOff = Color(0xFF8E8E93)

@Composable
fun SettingsScreen(
    isVoiceOn: Boolean,
    isShockOn: Boolean,
    onVoiceToggle: (Boolean) -> Unit,
    onShockToggle: (Boolean) -> Unit,
    onBack: () -> Unit = {},
    onEditProfile: () -> Unit = {}
) {
    val scale = LocalAppScale.current
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.RECORD_AUDIO] == true) {
            onVoiceToggle(true)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(ColorBackground)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = scaledDp(16, scale)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HeaderSection(scale, onBack)
            UserInfoCard(scale, "홍길동", "남성", "2000.01.01", "제가 특이해요", onEditProfile)
            Spacer(modifier = Modifier.height(scaledDp(20, scale)))

            SettingsControlCard(
                scale = scale,
                isVoiceOn = isVoiceOn,
                isShockOn = isShockOn,
                onVoiceToggle = { shouldEnable ->
                    if (shouldEnable) {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            onVoiceToggle(true)
                        } else {
                            permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                        }
                    } else {
                        onVoiceToggle(false)
                    }
                },
                onShockToggle = { shouldEnable ->
                    onShockToggle(shouldEnable)
                }
            )
        }
    }
}

@Composable
private fun HeaderSection(scale: Float, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(top = scaledDp(40, scale), bottom = scaledDp(20, scale))) {
        Icon(painter = painterResource(id = R.drawable.ic_back), contentDescription = "Back", tint = ColorTextMain, modifier = Modifier.size(scaledDp(24, scale)).align(Alignment.CenterStart).clickable { onBack() })
        Text(text = "설정", color = ColorTextMain, fontSize = scaledSp(20, scale), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
    }
}

@Composable
private fun UserInfoCard(scale: Float, name: String, gender: String, birthDate: String, note: String, onEditClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(ColorCard, RoundedCornerShape(scaledDp(13, scale))).padding(horizontal = scaledDp(16, scale), vertical = scaledDp(20, scale))) {
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = scaledDp(20, scale))) {
            Text(text = "사용자 정보", color = ColorTextMain, fontSize = scaledSp(16, scale), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            Icon(painter = painterResource(id = R.drawable.ic_modify), contentDescription = "Edit", tint = ColorTextMain, modifier = Modifier.size(scaledDp(20, scale)).align(Alignment.CenterEnd).clickable { onEditClick() })
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
