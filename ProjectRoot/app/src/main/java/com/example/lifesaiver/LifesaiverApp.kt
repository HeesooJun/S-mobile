package com.example.lifesaiver

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.lifesaiver.core.model.ChatMessage
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lifesaiver.presentation.screen.PermissionViewModel
import com.example.lifesaiver.ui.navigation.AppNavHost
import com.example.lifesaiver.ui.theme.AppColors
import com.example.lifesaiver.ui.theme.LocalAppScale
import com.example.lifesaiver.ui.theme.rememberAppScale
import com.example.lifesaiver.ui.theme.scaledDp
import com.example.lifesaiver.ui.theme.scaledSp

@Composable
fun LifesaiverApp(
    hasPermissions: Boolean,
    batteryLevel: Int,
    isConnected: Boolean,
    isMicOn: Boolean,
    isDisconnecting: Boolean,
    messages: List<ChatMessage>,
    onRequestPermissions: () -> Unit,
    onStartAutoConnect: () -> Unit,
    onMicPress: () -> Unit,
    onMicRelease: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDisconnect: () -> Unit
) {
    val scale = rememberAppScale()

    CompositionLocalProvider(LocalAppScale provides scale) {
        if (!hasPermissions) {
            val permissionViewModel: PermissionViewModel = viewModel()
            val permissionState by permissionViewModel.uiState.collectAsState()
            PermissionRequiredScreen(
                uiState = permissionState,
                onRequestPermissions = onRequestPermissions
            )
            return@CompositionLocalProvider
        }

        AppNavHost(
            batteryLevel = batteryLevel,
            isConnected = isConnected,
            isMicOn = isMicOn,
            isDisconnecting = isDisconnecting,
            messages = messages,
            onStartAutoConnect = onStartAutoConnect,
            onMicPress = onMicPress,
            onMicRelease = onMicRelease,
            onSendMessage = onSendMessage,
            onDisconnect = onDisconnect
        )
    }
}

@Composable
private fun PermissionRequiredScreen(
    uiState: com.example.lifesaiver.presentation.screen.PermissionUiState,
    onRequestPermissions: () -> Unit
) {
    val scale = LocalAppScale.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Black)
            .padding(scaledDp(24, scale)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = uiState.title,
            color = AppColors.White,
            fontSize = scaledSp(20, scale),
            fontWeight = FontWeight.Bold
        )
        Text(
            text = uiState.description,
            color = AppColors.Gray500,
            fontSize = scaledSp(14, scale),
            modifier = Modifier.padding(top = scaledDp(8, scale), bottom = scaledDp(16, scale))
        )
        Button(onClick = onRequestPermissions) {
            Text(text = uiState.actionLabel)
        }
    }
}
