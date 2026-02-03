package com.example.lifesaiver.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import java.util.UUID
import kotlin.math.pow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BleRSSILocating(
    private val context: Context,
    targetPeerIdHex: String? = null
) {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

    private val _distance = MutableStateFlow<Float?>(null)
    val distance = _distance.asStateFlow()

    private val _trackedPeerId = MutableStateFlow<String?>(null)
    val trackedPeerId = _trackedPeerId.asStateFlow()

    private var targetPeerId: String? = targetPeerIdHex?.lowercase()
    private val peerRssi = mutableMapOf<String, Int>()
    private val peerLastSeen = mutableMapOf<String, Long>()
    private val peerTimeoutMs = 6_000L

    private val serviceUuid: UUID = Constants.SERVICE_UUID
    private val txPower = -59
    private val pathLossExponent = 2.0

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val peerId = extractPeerId(result) ?: return
            val rssi = result.rssi
            val now = System.currentTimeMillis()
            peerRssi[peerId] = rssi
            peerLastSeen[peerId] = now

            val selected = targetPeerId
            if (selected != null) {
                if (peerId != selected) return
                _trackedPeerId.value = selected
                _distance.value = calculateDistance(rssi)
                return
            }

            val candidate = selectClosestPeer(now)
            _trackedPeerId.value = candidate
            if (candidate == null) {
                _distance.value = null
                return
            }
            val candidateRssi = peerRssi[candidate] ?: return
            _distance.value = calculateDistance(candidateRssi)
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            _distance.value = null
            _trackedPeerId.value = null
        }
    }

    private fun selectClosestPeer(now: Long): String? {
        val cutoff = now - peerTimeoutMs
        val candidates = peerRssi
            .filterKeys { peerLastSeen[it]?.let { last -> last >= cutoff } == true }
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull { it.value }?.key
    }

    private fun calculateDistance(rssi: Int): Float {
        return 10.0.pow((txPower - rssi) / (10 * pathLossExponent)).toFloat()
    }

    fun setTargetPeerId(peerIdHex: String?) {
        targetPeerId = peerIdHex?.lowercase()
        if (targetPeerId == null) {
            _trackedPeerId.value = null
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner ?: return

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(serviceUuid))
            .build()

        scanner.startScan(listOf(filter), settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopTracking() {
        bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _distance.value = null
        _trackedPeerId.value = null
    }

    private fun extractPeerId(result: ScanResult): String? {
        val data = result.scanRecord?.getServiceData(ParcelUuid(serviceUuid)) ?: return null
        if (data.size < 8) return null
        return data.copyOfRange(0, 8).toHexString()
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }
}
