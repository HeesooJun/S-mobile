package com.example.lifesaiver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifesaiver.core.model.ChatMessage
import com.example.lifesaiver.ui.navigation.AppNavHost
import com.example.lifesaiver.ui.theme.AppColors

@Composable
fun LifesaiverApp(
    hasPermissions: Boolean,
    batteryLevel: Int,
    isConnected: Boolean,
    isMicOn: Boolean,
    messages: List<ChatMessage>,
    onRequestPermissions: () -> Unit,
    onStartAutoConnect: () -> Unit,
    onToggleMic: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    if (!hasPermissions) {
        PermissionRequiredScreen(onRequestPermissions = onRequestPermissions)
        return
    }

    AppNavHost(
        batteryLevel = batteryLevel,
        isConnected = isConnected,
        isMicOn = isMicOn,
        messages = messages,
        onStartAutoConnect = onStartAutoConnect,
        onToggleMic = onToggleMic,
        onSendMessage = onSendMessage,
        onDisconnect = onDisconnect
    )
}

@Composable
private fun PermissionRequiredScreen(onRequestPermissions: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Black)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "권한이 필요합니다",
            color = AppColors.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "BLE 및 마이크 권한을 허용해주세요.",
            color = AppColors.Gray500,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
        )
        Button(onClick = onRequestPermissions) {
            Text(text = "권한 요청")
        }
    }
}
