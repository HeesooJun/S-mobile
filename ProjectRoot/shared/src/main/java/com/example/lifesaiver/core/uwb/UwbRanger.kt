package com.example.lifesaiver.core.uwb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UwbRanger(private val context: Context) {
    private val _distanceMeters = MutableStateFlow<Float?>(null)
    val distanceMeters = _distanceMeters.asStateFlow()

    private var isRunning = false

    fun isSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)
    }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.UWB_RANGING
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun start() {
        if (!isSupported() || !hasPermission()) {
            _distanceMeters.value = null
            isRunning = false
            return
        }
        isRunning = true
    }

    fun stop() {
        isRunning = false
        _distanceMeters.value = null
    }

    // UWB 세션 연동 지점(향후 실제 ranging 이벤트 값을 주입).
    fun updateDistanceMeters(distanceMeters: Float?) {
        if (isRunning) {
            _distanceMeters.value = distanceMeters
        }
    }
}
