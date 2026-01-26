package com.example.lifesaiver

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.lifesaiver.presentation.AppViewModel
import com.example.lifesaiver.presentation.UiEvent
import com.example.lifesaiver.ui.theme.LifesaiverTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            val errorMsg = "?ㅻ쪟: ${throwable.message}"
            Log.e("CRASH_HANDLER", errorMsg, throwable)
            runOnUiThread {
                Toast.makeText(applicationContext, errorMsg, Toast.LENGTH_SHORT).show()
            }
        }

        viewModel.refreshPermissions()
        if (!viewModel.uiState.value.hasPermissions) {
            requestPermissions()
        }

        setContent {
            LifesaiverTheme(darkTheme = true, dynamicColor = false) {
                val uiState by viewModel.uiState.collectAsState()

                LifesaiverApp(
                    hasPermissions = uiState.hasPermissions,
                    batteryLevel = uiState.batteryLevel,
                    isConnected = uiState.isConnected,
                    connectedCount = uiState.connectedCount,
                    meshPeerCount = uiState.meshPeerCount,
                    isMicOn = uiState.isMicOn,
                    isDisconnecting = uiState.isDisconnecting,
                    isRescueSignalActive = uiState.isRescueSignalActive,
                    messages = uiState.messages,
                    onRequestPermissions = { requestPermissions() },
                    onStartAutoConnect = { viewModel.onStartAutoConnect() },
                    onStopAutoConnect = { viewModel.onStopAutoConnect() },
                    onMicPress = { viewModel.onMicPress() },
                    onMicRelease = { viewModel.onMicRelease() },
                    onSendMessage = { text -> viewModel.onSendMessage(text) },
                    onDisconnect = { viewModel.onDisconnect() },
                    onStartRescueSignal = { viewModel.startRescueSignal() },
                    onStopRescueSignal = { viewModel.stopRescueSignal() }
                )
            }
        }

        lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is UiEvent.Toast -> toast(event.message)
                }
            }
        }
    }

    private fun toast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, viewModel.requiredPermissions, 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 1) return

        val granted = grantResults.isNotEmpty() && grantResults.all {
            it == PackageManager.PERMISSION_GRANTED
        }
        viewModel.onPermissionsResult(granted)
    }
}
