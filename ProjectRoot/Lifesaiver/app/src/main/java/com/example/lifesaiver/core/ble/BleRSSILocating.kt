package com.example.lifesaiver.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow

// [수정] 생성자에서 targetAddress(찾을 기기의 MAC 주소)를 받습니다.
class BleRSSILocating(
    private val context: Context,
    private val targetAddress: String? = null
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _distance = MutableStateFlow<Float?>(null)
    val distance = _distance.asStateFlow()

    // BleManager에 있는 UUID를 가져옵니다 (하드코딩 방지)
    // 만약 Constants 접근이 안되면 "0000AAAA-0000-1000-8000-00805f9b34fb"를 직접 넣으세요
    private val SERVICE_UUID = Constants.SERVICE_UUID

    private val TX_POWER = -59
    private val PATH_LOSS_EXPONENT = 2.0

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // [이중 잠금]
            // 1. UUID는 필터에서 거르지만 혹시 모르니 확인
            // 2. targetAddress가 있다면 그 기기가 맞는지 확인
            if (targetAddress != null && result.device.address != targetAddress) return

            val rssi = result.rssi
            _distance.value = calculateDistance(rssi)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            _distance.value = null
        }
    }

    private fun calculateDistance(rssi: Int): Float {
        return 10.0.pow((TX_POWER - rssi) / (10 * PATH_LOSS_EXPONENT)).toFloat()
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        // [핵심] 필터 설정
        val filterBuilder = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID)) // 1차: 우리 앱 서비스 ID만 찾기

        if (targetAddress != null) {
            filterBuilder.setDeviceAddress(targetAddress) // 2차: 특정 기기만 찾기 (가장 확실)
        }

        val filters = listOf(filterBuilder.build())

        scanner?.startScan(filters, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopTracking() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _distance.value = null
    }
}
