package com.example.lifesaivior.core.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.example.lifesaivior.core.log.ConnectionLog
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.actor
import java.nio.charset.Charset
import java.util.UUID

object Constants {
    val SERVICE_UUID: UUID = UUID.fromString("0000AAAA-0000-1000-8000-00805f9b34fb")
    val CHAR_UUID: UUID = UUID.fromString("0000BBBB-0000-1000-8000-00805f9b34fb")
    val PROTOCOL_CHAR_UUID: UUID = UUID.fromString("0000CCCC-0000-1000-8000-00805f9b34fb")
    const val TYPE_AUDIO: Byte = 0x01
    const val TYPE_TEXT: Byte = 0x02
}

data class BleDebugSnapshot(
    val scanRssiAvg: Int?,
    val scanRssiCount: Int,
    val connectionRssiAvg: Int?,
    val connectionRssiCount: Int,
    val pendingCount: Int,
    val attemptTracked: Int,
    val maxAttempts: Int
)

@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context,
    private val logCallback: (String) -> Unit,
    private val audioCallback: (ByteArray) -> Unit,
    private val textCallback: (String) -> Unit,
    private var protocolCallback: (ByteArray, String?) -> Unit,
    private val connectionCallback: (Boolean, Int) -> Unit
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter = bluetoothManager?.adapter

    private val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner

    private val advertiser: BluetoothLeAdvertiser?
        get() = adapter?.bluetoothLeAdvertiser

    private val handler = Handler(Looper.getMainLooper())

    // 코루틴 제어를 위한 Job 및 Scope
    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.Main + job)
    private val ioScope = CoroutineScope(Dispatchers.IO + job)
    private val deviceMonitor = DeviceMonitoringManager(
        scope = ioScope,
        disconnectCallback = { address -> disconnectAddress(address) },
        log = logCallback
    )
    private val clientManager = BleClientManager()
    private val serverManager = BleServerManager()

    var isHost = false
    private var isConnected = false
    private val connectedPeers = mutableSetOf<String>()
    private val clientConnections = mutableMapOf<String, BluetoothGatt>()
    private val pendingClientConnections = mutableMapOf<String, BluetoothGatt>()
    private val pendingConnections = mutableMapOf<String, Long>()
    private val inFlightConnections = mutableMapOf<String, BluetoothGatt>()
    private val addressPeerMap = mutableMapOf<String, String>()
    private val pendingPeerIds = mutableMapOf<String, String>()
    private val pendingServerConnections = mutableSetOf<String>()
    private val cccdRetryJobs = mutableMapOf<String, Job>()
    private val cccdRetryCounts = mutableMapOf<String, Int>()
    private var gattServer: BluetoothGattServer? = null
    @Volatile private var lastServerNotifyAtMs: Long = 0L
    private var gattServerCloseJob: Job? = null
    private val gattServerCloseDelayMs = 300L
    private var localPeerId: ByteArray? = null

    private var currentAdvertisingSet: AdvertisingSet? = null
    private var autoConnectActive: Boolean = false

    // Discovery/connection tuning knobs.
    // Permissive default to reduce connection gating (bitchat-style).
    private var rssiThresholdDbm: Int = -120
    private val scanRateLimitMs: Long = 5_000L
    private var lastScanStartTimeMs: Long = 0L
    @Volatile
    private var isScanning: Boolean = false
    @Volatile
    private var scanRestartJob: Job? = null

    private val connectionBackoffBaseMs: Long = 5_000L
    private val connectionBackoffMaxMs: Long = 5_000L
    private val connectionAttemptResetMs: Long = 10_000L
    private val connectionAttemptCleanupIntervalMs: Long = 30_000L
    private val maxConnectionAttempts: Int = 3
    private val connectionAttempts = mutableMapOf<String, ConnectionAttempt>()
    private var connectionAttemptCleanupJob: Job? = null
    private val pendingConnectionTimeoutMs: Long = 5_000L
    private val pendingConnectionCleanupIntervalMs: Long = 1_000L
    private var pendingConnectionCleanupJob: Job? = null

    private val baseRssiUpdateIntervalMs: Long = 10_000L
    private val activeRssiUpdateIntervalMs: Long = 3_000L
    private val idleRssiUpdateIntervalMs: Long = 15_000L
    private val rssiDataStaleMs: Long = 20_000L
    private val rssiDataExpireMs: Long = 60_000L
    @Volatile
    private var rssiActiveModeEnabled: Boolean = false
    private val scanRssi = mutableMapOf<String, Int>()
    private val scanRssiUpdatedAtMs = mutableMapOf<String, Long>()
    private val connectionRssi = mutableMapOf<String, Int>()
    private val connectionRssiUpdatedAtMs = mutableMapOf<String, Long>()
    private val scanRssiFilters = mutableMapOf<String, RssiKalmanFilter>()
    private val connectionRssiFilters = mutableMapOf<String, RssiKalmanFilter>()
    private val pendingRssiReadAddresses = mutableSetOf<String>()
    private val pendingRssiReadRequestedAtMs = mutableMapOf<String, Long>()
    private val rssiReadPendingTimeoutMs: Long = 8_000L
    private var rssiMonitorJob: Job? = null

    private data class ConnectionAttempt(
        var attempts: Int,
        var lastAttemptMs: Long
    )

    private sealed class SendRequest {
        data class Broadcast(
            val characteristicUuid: UUID,
            val data: ByteArray,
            val excludeAddress: String?
        ) : SendRequest()

        data class Direct(
            val address: String,
            val characteristicUuid: UUID,
            val data: ByteArray
        ) : SendRequest()
    }

    private enum class NotifySetupResult {
        STARTED,
        RETRY,
        MISSING
    }

    // Serialize outbound GATT operations to avoid write/notify races.
    @OptIn(ObsoleteCoroutinesApi::class)
    private val sendActor = ioScope.actor<SendRequest>(capacity = Channel.UNLIMITED) {
        for (request in channel) {
            when (request) {
                is SendRequest.Broadcast -> sendRawInternal(
                    request.characteristicUuid,
                    request.data,
                    request.excludeAddress
                )

                is SendRequest.Direct -> sendToAddressInternal(
                    request.address,
                    request.characteristicUuid,
                    request.data
                )
            }
        }
    }

    // 구조대가 연결되었을 때 실행할 콜백 (사이렌 울리기용)
    var onRescueConnected: (() -> Unit)? = null
    // UI에 상태 메시지를 전달하기 위한 콜백 (토스트용)
    var onModeChange: ((String) -> Unit)? = null

    fun setProtocolCallback(listener: (ByteArray, String?) -> Unit) {
        protocolCallback = listener
    }

    fun setLocalPeerId(peerId: ByteArray) {
        localPeerId = peerId.copyOf()
    }

    fun setRssiThresholdDbm(threshold: Int) {
        rssiThresholdDbm = threshold
    }

    fun setRssiActiveMode(enabled: Boolean) {
        rssiActiveModeEnabled = enabled
    }

    fun isLongRangeSupported(): Boolean {
        if (adapter == null || !adapter.isEnabled) return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                adapter.isLeCodedPhySupported && adapter.isLeExtendedAdvertisingSupported
            } else false
        } catch (e: Exception) {
            false
        }
    }

    // --------------------------------------------------------------------------
    // [구조 신호] SOS 기능 (72시간 생존 모드)
    // --------------------------------------------------------------------------

    fun startEmergencyAdvertising() {
        if (adapter == null || !adapter.isEnabled) {
            logCallback("Bluetooth is off.")
            return
        }

        // 1. 기존 연결 정리
        disconnect()
        isHost = true
        setupGattServer()

        // [수정] 배터리 절약을 위해 로그 및 토스트 알림 제거
        // logCallback("🚨 SOS Mode...")  <- 삭제됨
        // onModeChange?.invoke("...")    <- 삭제됨

        // 2. 타이머 없이 바로 '절전형 고출력' 모드로 시작
        // 전략: Interval은 느리게(배터리 절약), TxPower는 강하게(장거리)
        startMaintenanceJobs()
        startAdvertisingInternal(isEmergencyMode = true)
        // SOS 모드에서도 스캔을 유지해 약한 링크에서의 상호 발견 확률을 올린다.
        startScan()
    }

    fun pulseEmergencyAdvertising() {
        if (adapter == null || !adapter.isEnabled) {
            logCallback("Bluetooth is off.")
            return
        }
        isHost = true
        if (gattServer == null) {
            setupGattServer()
        }
        stopAdvertising()
        startAdvertisingInternal(isEmergencyMode = true)
        startMaintenanceJobs()
        startScan()
    }

    // --------------------------------------------------------------------------
    // [자동 연결] Auto Connect 기능
    // --------------------------------------------------------------------------

    fun startAutoConnect() {
        if (adapter == null || !adapter.isEnabled) {
            logCallback("Bluetooth is off.")
            return
        }

        // Multiple screens call this; avoid dropping live connections.
        if (autoConnectActive) {
            ensureAutoConnectRunning()
            return
        }

        if (isConnected) {
            logCallback("Auto connect start (already connected).")
            isHost = true
            setupGattServer()
            startAdvertisingInternal(isEmergencyMode = false)
            startMaintenanceJobs()
            startScan()
            autoConnectActive = true
            return
        }

        disconnect()
        isConnected = false
        connectionCallback(false, 0)
        isHost = true

        logCallback("Auto connect start (mesh mode).")
        setupGattServer()
        startAdvertisingInternal(isEmergencyMode = false)
        startMaintenanceJobs()
        startScan()
        autoConnectActive = true
    }

    private fun ensureAutoConnectRunning() {
        if (adapter == null || !adapter.isEnabled) return
        // Keep existing connections; only restore missing pieces.
        isHost = true
        setupGattServer()
        if (currentAdvertisingSet == null) {
            startAdvertisingInternal(isEmergencyMode = false)
        }
        startMaintenanceJobs()
        if (!isScanning) {
            startScan()
        }
    }

    private fun startMaintenanceJobs() {
        startConnectionAttemptCleanup()
        startPendingConnectionCleanup()
        startRssiMonitoring()
    }

    private fun stopMaintenanceJobs() {
        connectionAttemptCleanupJob?.cancel()
        connectionAttemptCleanupJob = null
        pendingConnectionCleanupJob?.cancel()
        pendingConnectionCleanupJob = null
        rssiMonitorJob?.cancel()
        rssiMonitorJob = null
    }

    private fun startConnectionAttemptCleanup() {
        if (connectionAttemptCleanupJob?.isActive == true) return
        connectionAttemptCleanupJob = ioScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                synchronized(connectionAttempts) {
                    val expired = connectionAttempts.filterValues { isAttemptExpired(it, now) }.keys
                    expired.forEach { connectionAttempts.remove(it) }
                }
                delay(connectionAttemptCleanupIntervalMs)
            }
        }
    }

    private fun startPendingConnectionCleanup() {
        if (pendingConnectionCleanupJob?.isActive == true) return
        pendingConnectionCleanupJob = ioScope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                val expiredAddresses = synchronized(pendingConnections) {
                    pendingConnections
                        .filterValues { startedAt -> now - startedAt >= pendingConnectionTimeoutMs }
                        .keys
                        .toList()
                }
                expiredAddresses.forEach { address ->
                    handlePendingTimeout(address)
                }
                delay(pendingConnectionCleanupIntervalMs)
            }
        }
    }

    private fun handlePendingTimeout(address: String) {
        val gatt = synchronized(inFlightConnections) {
            inFlightConnections.remove(address)
        }
        synchronized(pendingConnections) {
            pendingConnections.remove(address)
        }
        synchronized(pendingPeerIds) {
            pendingPeerIds.remove(address)
        }
        if (gatt != null) {
            try {
                gatt.disconnect()
            } catch (_: Exception) {
            }
            try {
                gatt.close()
            } catch (_: Exception) {
            }
        }
        logCallback("Pending connection timeout: $address")
    }

    private fun startRssiMonitoring() {
        if (rssiMonitorJob?.isActive == true) return
        rssiMonitorJob = ioScope.launch {
            while (isActive) {
                clearStalePendingRssiReads()
                val snapshot = synchronized(clientConnections) { clientConnections.toMap() }
                snapshot.forEach { (address, gatt) ->
                    if (!markRssiReadPending(address)) return@forEach
                    try {
                        val requested = gatt.readRemoteRssi()
                        if (!requested) {
                            clearRssiReadPending(address)
                        }
                    } catch (_: Exception) {
                        clearRssiReadPending(address)
                    }
                }
                delay(currentRssiPollingIntervalMs(snapshot.size))
            }
        }
    }

    private fun currentRssiPollingIntervalMs(connectionCount: Int): Long {
        if (connectionCount <= 0) return idleRssiUpdateIntervalMs
        return if (rssiActiveModeEnabled) activeRssiUpdateIntervalMs else baseRssiUpdateIntervalMs
    }

    private fun markRssiReadPending(address: String): Boolean {
        synchronized(pendingRssiReadAddresses) {
            if (pendingRssiReadAddresses.contains(address)) return false
            pendingRssiReadAddresses.add(address)
            pendingRssiReadRequestedAtMs[address] = SystemClock.elapsedRealtime()
            return true
        }
    }

    private fun clearRssiReadPending(address: String) {
        synchronized(pendingRssiReadAddresses) {
            pendingRssiReadAddresses.remove(address)
            pendingRssiReadRequestedAtMs.remove(address)
        }
    }

    private fun clearStalePendingRssiReads() {
        val now = SystemClock.elapsedRealtime()
        synchronized(pendingRssiReadAddresses) {
            val staleAddresses = pendingRssiReadRequestedAtMs
                .filterValues { requestedAt -> now - requestedAt >= rssiReadPendingTimeoutMs }
                .keys
                .toList()
            staleAddresses.forEach { address ->
                pendingRssiReadAddresses.remove(address)
                pendingRssiReadRequestedAtMs.remove(address)
            }
        }
    }

    private fun clearRssiState(address: String) {
        synchronized(scanRssi) {
            scanRssi.remove(address)
        }
        synchronized(scanRssiUpdatedAtMs) {
            scanRssiUpdatedAtMs.remove(address)
        }
        synchronized(scanRssiFilters) {
            scanRssiFilters.remove(address)
        }
        synchronized(connectionRssi) {
            connectionRssi.remove(address)
        }
        synchronized(connectionRssiUpdatedAtMs) {
            connectionRssiUpdatedAtMs.remove(address)
        }
        synchronized(connectionRssiFilters) {
            connectionRssiFilters.remove(address)
        }
        clearRssiReadPending(address)
    }

    private fun clearAllRssiState() {
        synchronized(scanRssi) {
            scanRssi.clear()
        }
        synchronized(scanRssiUpdatedAtMs) {
            scanRssiUpdatedAtMs.clear()
        }
        synchronized(scanRssiFilters) {
            scanRssiFilters.clear()
        }
        synchronized(connectionRssi) {
            connectionRssi.clear()
        }
        synchronized(connectionRssiUpdatedAtMs) {
            connectionRssiUpdatedAtMs.clear()
        }
        synchronized(connectionRssiFilters) {
            connectionRssiFilters.clear()
        }
        synchronized(pendingRssiReadAddresses) {
            pendingRssiReadAddresses.clear()
            pendingRssiReadRequestedAtMs.clear()
        }
    }

    private fun updateKalmanRssi(
        address: String,
        rssi: Int,
        filters: MutableMap<String, RssiKalmanFilter>
    ): Int {
        synchronized(filters) {
            return filters
                .getOrPut(address) { RssiKalmanFilter() }
                .update(rssi)
        }
    }

    private fun enterScanMode() {
        logCallback("Scan mode: searching for peers.")
        startScan()
    }

    private fun enterAdvertiseMode() {
        isHost = true
        setupGattServer()
        startAdvertisingInternal(isEmergencyMode = false)
    }

    // --------------------------------------------------------------------------
    // [내부 로직] 광고 시작 및 중단
    // --------------------------------------------------------------------------

    private val advertisingCallback = object : AdvertisingSetCallback() {
        override fun onAdvertisingSetStarted(
            advertisingSet: AdvertisingSet?,
            txPower: Int,
            status: Int
        ) {
            if (status == AdvertisingSetCallback.ADVERTISE_SUCCESS) {
                currentAdvertisingSet = advertisingSet
            } else {
                logCallback("Advertising failed: $status")
            }
        }
    }

    private fun startAdvertisingInternal(isEmergencyMode: Boolean): Boolean =
        serverManager.startAdvertisingInternal(isEmergencyMode)

    /**
     * 광고 시작 내부 함수
     * @param isEmergencyMode
     * - true (SOS): Interval High (느림=절전), TxPower High (강함=최대거리) -> 72시간 생존
     * - false (일반): Interval High (느림=절전), TxPower High (강함=일반)
     */
    private fun startAdvertisingInternalImpl(isEmergencyMode: Boolean): Boolean {
        return try {
            // Prefer legacy advertising for cross-device compatibility.
            val parameters = AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setConnectable(true) // 구조대가 연결할 수 있어야 함 (필수)
                .setScannable(true)
                // 발견률을 올리기 위해 광고 간격은 빠르게 유지
                .setInterval(AdvertisingSetParameters.INTERVAL_LOW)
                // [핵심 전략] 신호 세기는 무조건 최대(High)로 하여 도달 거리 확보
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
                .setSecondaryPhy(BluetoothDevice.PHY_LE_1M)
                .build()

            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(android.os.ParcelUuid(Constants.SERVICE_UUID))
                .apply {
                    val peerId = localPeerId
                    if (peerId != null && peerId.size == 8) {
                        addServiceData(ParcelUuid(Constants.SERVICE_UUID), peerId)
                    }
                }
                .build()

            val scanResponse = AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build()

            advertiser?.startAdvertisingSet(
                parameters,
                advertiseData,
                scanResponse,
                null,
                null,
                advertisingCallback
            )
            true
        } catch (e: Exception) {
            logCallback("Advertising error: ${e.message}")
            false
        }
    }

    fun stopAdvertising() = serverManager.stopAdvertising()

    private fun stopAdvertisingImpl() {
        try {
            advertiser?.stopAdvertisingSet(advertisingCallback)
            currentAdvertisingSet = null
        } catch (e: Exception) {
        }
    }

    // --------------------------------------------------------------------------
    // 스캔 및 연결 콜백
    // --------------------------------------------------------------------------

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return
            val address = device.address
            if (deviceMonitor.isBlocked(address)) return
            val filteredRssi = updateKalmanRssi(address, result.rssi, scanRssiFilters)
            synchronized(scanRssi) {
                scanRssi[address] = filteredRssi
            }
            synchronized(scanRssiUpdatedAtMs) {
                scanRssiUpdatedAtMs[address] = SystemClock.elapsedRealtime()
            }
            val peerId = extractPeerId(result)
            if (peerId != null && isPeerConnected(peerId)) return
            if (isConnectionKnown(address)) return
            if (!isConnectionAttemptAllowed(address)) return
            if (!markPending(address)) return
            if (!recordConnectionAttempt(address)) {
                clearPending(address)
                return
            }
            if (peerId != null) {
                recordPendingPeerId(address, peerId)
            }

            logCallback("Peer found: ${device.address}")
            connectToPeer(device)
        }

        override fun onScanFailed(errorCode: Int) {
            logCallback("Scan failed ($errorCode)")
            isScanning = false
            val retryDelayMs = if (errorCode == ScanCallback.SCAN_FAILED_SCANNING_TOO_FREQUENTLY) {
                10_000L
            } else {
                5_000L
            }
            restartScan(retryDelayMs)
        }
    }

    private fun startScan(): Boolean = clientManager.startScan()

    private fun startScanImpl(): Boolean {
        return try {
            val now = System.currentTimeMillis()
            if (isScanning) return true
            val elapsed = now - lastScanStartTimeMs
            if (elapsed in 0 until scanRateLimitMs) {
                val waitMs = scanRateLimitMs - elapsed
                scanRestartJob?.cancel()
                scanRestartJob = scope.launch {
                    delay(waitMs)
                    if (!isScanning) startScan()
                }
                return false
            }
            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(Constants.SERVICE_UUID)).build()
            )
            val builder = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setReportDelay(0)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                builder.setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                    .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            }
            val settings = builder.build()
            scanner?.startScan(filters, settings, scanCallback)
            lastScanStartTimeMs = now
            isScanning = true
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun stopScan() = clientManager.stopScan()

    private fun stopScanImpl() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
        }
        isScanning = false
        scanRestartJob?.cancel()
        scanRestartJob = null
    }

    private fun restartScan(delayMs: Long = 1_000L) = clientManager.restartScan(delayMs)

    private fun restartScanImpl(delayMs: Long = 1_000L) {
        scanRestartJob?.cancel()
        scanRestartJob = scope.launch {
            delay(delayMs)
            if (!isScanning) {
                startScan()
            }
        }
    }

    private fun connectToPeer(device: BluetoothDevice) = clientManager.connectToPeer(device)

    private fun connectToPeerImpl(device: BluetoothDevice) {
        try {
            if (deviceMonitor.isBlocked(device.address)) {
                clearPending(device.address)
                return
            }
            if (isInFlight(device.address)) {
                clearPending(device.address)
                return
            }
            val phyMask = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isLongRangeSupported()) {
                BluetoothDevice.PHY_LE_1M_MASK or BluetoothDevice.PHY_LE_CODED_MASK
            } else BluetoothDevice.PHY_LE_1M_MASK

            val gatt = device.connectGatt(
                context,
                false,
                gattClientCallback,
                BluetoothDevice.TRANSPORT_LE,
                phyMask
            )
            if (gatt == null) {
                clearPending(device.address)
                return
            }
            synchronized(inFlightConnections) {
                inFlightConnections[device.address] = gatt
            }
        } catch (e: Exception) {
            logCallback("Connect error: ${e.message}")
            clearPending(device.address)
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            val wasPending = isPending(address)
            val hadInFlight = removeInFlight(address)
            if (!wasPending && !hadInFlight) {
                try {
                    gatt.disconnect()
                } catch (_: Exception) {
                }
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logCallback("Connected. Requesting MTU...")
                synchronized(pendingClientConnections) {
                    pendingClientConnections[address] = gatt
                }
                clearConnectionAttempt(address)
                attachPendingPeerId(address)
                clearPending(address)
                try {
                    gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                } catch (_: Exception) {
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isLongRangeSupported()) {
                    try {
                        gatt.setPreferredPhy(
                            BluetoothDevice.PHY_LE_CODED,
                            BluetoothDevice.PHY_LE_CODED,
                            BluetoothDevice.PHY_OPTION_NO_PREFERRED
                        )
                    } catch (_: Exception) {
                    }
                }
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                logCallback("Disconnected.")
                synchronized(clientConnections) {
                    clientConnections.remove(address)
                }
                synchronized(pendingClientConnections) {
                    pendingClientConnections.remove(address)
                }
                synchronized(cccdRetryCounts) {
                    cccdRetryCounts.remove(address)
                }
                synchronized(cccdRetryJobs) {
                    cccdRetryJobs.remove(address)?.cancel()
                }
                clearRssiState(address)
                clearPeerId(address)
                clearPending(address)
                notifyConnectionState()
                deviceMonitor.onDeviceDisconnected(address, status != BluetoothGatt.GATT_SUCCESS)
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            logCallback("MTU changed: $mtu bytes")
            gatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val address = gatt.device.address
            val protocolResult = enableNotifications(
                gatt,
                Constants.PROTOCOL_CHAR_UUID,
                address,
                required = true
            )
            if (protocolResult == NotifySetupResult.MISSING) {
                logCallback("Service setup failed for $address, disconnecting.")
                try {
                    gatt.disconnect()
                } catch (_: Exception) {
                }
                return
            }
            if (protocolResult == NotifySetupResult.RETRY) {
                scheduleProtocolCccdRetry(address, gatt)
                return
            }
            // Best-effort legacy notification (do not gate readiness)
            enableNotifications(
                gatt,
                Constants.CHAR_UUID,
                address,
                required = false
            )
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            val address = gatt.device.address
            clearRssiReadPending(address)
            if (status != BluetoothGatt.GATT_SUCCESS) return
            val filteredRssi = updateKalmanRssi(address, rssi, connectionRssiFilters)
            synchronized(connectionRssi) {
                connectionRssi[address] = filteredRssi
            }
            synchronized(connectionRssiUpdatedAtMs) {
                connectionRssiUpdatedAtMs[address] = SystemClock.elapsedRealtime()
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            deviceMonitor.onAnyPacketReceived(gatt.device.address)
            if (characteristic.uuid == Constants.PROTOCOL_CHAR_UUID) {
                protocolCallback(characteristic.value, gatt.device.address)
            } else {
                handleReceivedData(characteristic.value)
            }
        }

        override fun onServiceChanged(gatt: BluetoothGatt) {
            logCallback("Service changed: ${gatt.device.address}")
            try {
                gatt.discoverServices()
            } catch (_: Exception) {
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            val address = gatt.device.address
            val characteristic = descriptor.characteristic
            if (characteristic.uuid != Constants.PROTOCOL_CHAR_UUID) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                logCallback("CCCD write failed for $address status=$status")
                scheduleProtocolCccdRetry(address, gatt)
                return
            }
            markClientReady(address, gatt)
            // Best-effort legacy notification after readiness
            enableNotifications(
                gatt,
                Constants.CHAR_UUID,
                address,
                required = false
            )
        }
    }

    private fun enableNotifications(
        gatt: BluetoothGatt,
        characteristicUuid: UUID,
        address: String,
        required: Boolean
    ): NotifySetupResult {
        val service = gatt.getService(Constants.SERVICE_UUID)
        if (service == null) {
            if (required) {
                logCallback("Required service missing for $address")
                return NotifySetupResult.MISSING
            }
            return NotifySetupResult.STARTED
        }
        val char = service.getCharacteristic(characteristicUuid)
        if (char == null) {
            if (required) {
                logCallback("Required characteristic missing for $address")
                return NotifySetupResult.MISSING
            }
            return NotifySetupResult.STARTED
        }
        val notified = gatt.setCharacteristicNotification(char, true)
        if (!notified && required) {
            logCallback("setCharacteristicNotification failed for $address")
            return NotifySetupResult.MISSING
        }
        val descriptor = char.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        )
        if (descriptor == null) {
            if (required) {
                logCallback("CCCD missing for $address")
                return NotifySetupResult.MISSING
            }
            return NotifySetupResult.STARTED
        }
        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        val wrote = gatt.writeDescriptor(descriptor)
        if (!wrote) {
            if (required) {
                logCallback("CCCD write failed for $address")
                return NotifySetupResult.RETRY
            }
            return NotifySetupResult.STARTED
        }
        return NotifySetupResult.STARTED
    }

    private fun setupGattServer() = serverManager.setupGattServer()

    private fun setupGattServerImpl() {
        cancelGattServerClose()
        if (gattServer != null) return
        try {
            gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(
                Constants.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            fun createCccd(): BluetoothGattDescriptor {
                return BluetoothGattDescriptor(
                    cccdUuid,
                    BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
                )
            }
            val legacyChar = BluetoothGattCharacteristic(
                Constants.CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            legacyChar.addDescriptor(createCccd())
            val protocolChar = BluetoothGattCharacteristic(
                Constants.PROTOCOL_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            protocolChar.addDescriptor(createCccd())
            service.addCharacteristic(legacyChar)
            service.addCharacteristic(protocolChar)
            gattServer?.addService(service)
        } catch (e: Exception) {
            logCallback("GATT server setup failed.")
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(
            device: BluetoothDevice,
            status: Int,
            newState: Int
        ) {
            // [핵심] 구조대 접속 감지 (사이렌 발생 조건)
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                if (deviceMonitor.isBlocked(device.address)) {
                    try {
                        gattServer?.cancelConnection(device)
                    } catch (_: Exception) {
                    }
                    return
                }
                logCallback("✅ Peer joined (pending notify): ${device.address}")
                synchronized(pendingServerConnections) {
                    pendingServerConnections.add(device.address)
                }
                handler.removeCallbacksAndMessages(null)
                // wait for CCCD enable before marking established
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                synchronized(connectedPeers) {
                    connectedPeers.remove(device.address)
                }
                synchronized(pendingServerConnections) {
                    pendingServerConnections.remove(device.address)
                }
                clearRssiState(device.address)
                clearPeerId(device.address)
                notifyConnectionState()
                deviceMonitor.onDeviceDisconnected(device.address, status != BluetoothGatt.GATT_SUCCESS)
            }
        }

        override fun onNotificationSent(device: BluetoothDevice, status: Int) {
            logCallback("notify sent to=${device.address} status=$status")
        }

        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
            logCallback("Peer MTU changed: $mtu")
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            deviceMonitor.onAnyPacketReceived(device.address)
            if (characteristic.uuid == Constants.PROTOCOL_CHAR_UUID) {
                protocolCallback(value, device.address)
            } else {
                handleReceivedData(value)
                relayData(device.address, value, characteristic.uuid)
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray
        ) {
            val address = device.address
            if (descriptor.characteristic.uuid == Constants.PROTOCOL_CHAR_UUID) {
                val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) ||
                    value.contentEquals(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE)
                if (enabled) {
                    markServerReady(address)
                } else if (value.contentEquals(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE)) {
                    synchronized(connectedPeers) {
                        connectedPeers.remove(address)
                    }
                    notifyConnectionState()
                }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    private fun handleReceivedData(data: ByteArray?) {
        if (data == null || data.isEmpty()) return
        if (data[0] == Constants.TYPE_AUDIO) {
            audioCallback(data.copyOfRange(1, data.size))
        } else if (data[0] == Constants.TYPE_TEXT) {
            textCallback(String(data.copyOfRange(1, data.size), Charset.forName("UTF-8")))
        }
    }

    fun sendAudio(data: ByteArray) {
        sendPacket(Constants.TYPE_AUDIO, data)
    }

    fun sendText(msg: String) {
        sendPacket(Constants.TYPE_TEXT, msg.toByteArray(Charset.forName("UTF-8")))
    }

    fun sendProtocol(data: ByteArray) {
        sendRaw(Constants.PROTOCOL_CHAR_UUID, data)
    }

    fun broadcastProtocol(data: ByteArray, excludeAddress: String? = null) {
        sendRaw(Constants.PROTOCOL_CHAR_UUID, data, excludeAddress)
    }

    fun sendProtocolTo(address: String, data: ByteArray): Boolean {
        if (!isConnected) return false
        if (!isAddressConnected(address)) return false
        val activeAddresses = getSystemConnectedAddresses()
        if (!activeAddresses.isNullOrEmpty() && !activeAddresses.contains(address)) {
            ConnectionLog.add("BleSend", "direct skipped (stale) addr=$address")
            return false
        }
        val queued = sendActor.trySend(
            SendRequest.Direct(address, Constants.PROTOCOL_CHAR_UUID, data)
        ).isSuccess
        if (!queued) {
            if (hostSendTo(address, Constants.PROTOCOL_CHAR_UUID, data)) return true
            return clientSendTo(address, Constants.PROTOCOL_CHAR_UUID, data)
        }
        return true
    }

    private fun isAddressConnected(address: String): Boolean {
        synchronized(connectedPeers) {
            if (connectedPeers.contains(address)) return true
        }
        synchronized(clientConnections) {
            if (clientConnections.containsKey(address)) return true
        }
        return false
    }

    private fun sendPacket(type: Byte, payload: ByteArray) {
        val packet = ByteArray(payload.size + 1)
        packet[0] = type
        System.arraycopy(payload, 0, packet, 1, payload.size)
        sendRaw(Constants.CHAR_UUID, packet)
    }

    private fun sendRaw(characteristicUuid: UUID, data: ByteArray, excludeAddress: String? = null) {
        if (!isConnected) {
            ConnectionLog.add("BleSend", "broadcast skipped (no active link)")
            return
        }
        if (characteristicUuid == Constants.PROTOCOL_CHAR_UUID) {
            val addrCount = getAllConnectedAddresses().size
            val peerCount = getConnectedPeerCount()
            ConnectionLog.add(
                "BleSend",
                "protocol broadcast len=${data.size} addrCount=$addrCount peerCount=$peerCount"
            )
        }
        val queued = sendActor.trySend(
            SendRequest.Broadcast(characteristicUuid, data, excludeAddress)
        ).isSuccess
        if (!queued) {
            sendRawInternal(characteristicUuid, data, excludeAddress)
        }
    }

    private fun sendRawInternal(characteristicUuid: UUID, data: ByteArray, excludeAddress: String?) {
        // Notify connected GATT clients first (server role), then also write via
        // any active client connections to avoid missing peers when notify fails.
        hostBroadcast(characteristicUuid, data, excludeAddress)
        clientBroadcast(characteristicUuid, data, excludeAddress, emptySet())
    }

    private fun sendToAddressInternal(address: String, characteristicUuid: UUID, data: ByteArray) {
        if (hostSendTo(address, characteristicUuid, data)) return
        clientSendTo(address, characteristicUuid, data)
    }

    private fun hostBroadcast(
        characteristicUuid: UUID,
        data: ByteArray,
        excludeAddress: String?
    ): Set<String> {
        val char = gattServer?.getService(Constants.SERVICE_UUID)
            ?.getCharacteristic(characteristicUuid) ?: return emptySet()
        val addresses = synchronized(connectedPeers) { connectedPeers.toSet() }
        if (addresses.isEmpty()) return emptySet()
        val devices = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT) ?: return addresses

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            char.value = data
        }

        for (dev in devices) {
            if (!addresses.contains(dev.address)) continue
            if (excludeAddress != null && dev.address == excludeAddress) continue
            try {
                lastServerNotifyAtMs = SystemClock.elapsedRealtime()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gattServer?.notifyCharacteristicChanged(dev, char, false, data)
                } else {
                    gattServer?.notifyCharacteristicChanged(dev, char, false)
                }
            } catch (e: Exception) {
            }
        }
        return addresses
    }

    private fun clientSend(gatt: BluetoothGatt, characteristicUuid: UUID, data: ByteArray) {
        val char = gatt.getService(Constants.SERVICE_UUID)
            ?.getCharacteristic(characteristicUuid) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(
                    char,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
            } else {
                char.value = data
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                gatt.writeCharacteristic(char)
            }
        } catch (_: Exception) {
        }
    }

    private fun clientBroadcast(
        characteristicUuid: UUID,
        data: ByteArray,
        excludeAddress: String?,
        skipAddresses: Set<String>
    ) {
        val connections = synchronized(clientConnections) { clientConnections.toMap() }
        connections.forEach { (address, gatt) ->
            if (excludeAddress != null && address == excludeAddress) return@forEach
            if (skipAddresses.contains(address)) return@forEach
            clientSend(gatt, characteristicUuid, data)
        }
    }

    private fun hostSendTo(address: String, characteristicUuid: UUID, data: ByteArray): Boolean {
        val char = gattServer?.getService(Constants.SERVICE_UUID)
            ?.getCharacteristic(characteristicUuid) ?: return false
        val serverAddresses = synchronized(connectedPeers) { connectedPeers.toSet() }
        if (!serverAddresses.contains(address)) return false
        val devices = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT) ?: return false
        val target = devices.firstOrNull { it.address == address } ?: return false

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            char.value = data
        }

        return try {
            lastServerNotifyAtMs = SystemClock.elapsedRealtime()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gattServer?.notifyCharacteristicChanged(target, char, false, data)
            } else {
                gattServer?.notifyCharacteristicChanged(target, char, false)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun clientSendTo(address: String, characteristicUuid: UUID, data: ByteArray): Boolean {
        val gatt = synchronized(clientConnections) { clientConnections[address] } ?: return false
        clientSend(gatt, characteristicUuid, data)
        return true
    }

    private fun getAllConnectedAddresses(): Set<String> {
        val serverSet = synchronized(connectedPeers) { connectedPeers.toSet() }
        val clientSet = synchronized(clientConnections) { clientConnections.keys.toSet() }
        return serverSet + clientSet
    }

    private fun notifyConnectionState() {
        val activeAddresses = getSystemConnectedAddresses()
        val removedAddresses = mutableSetOf<String>()
        if (!activeAddresses.isNullOrEmpty()) {
            synchronized(connectedPeers) {
                val iterator = connectedPeers.iterator()
                while (iterator.hasNext()) {
                    val address = iterator.next()
                    if (!activeAddresses.contains(address)) {
                        removedAddresses.add(address)
                        iterator.remove()
                    }
                }
            }
            synchronized(clientConnections) {
                val iterator = clientConnections.keys.iterator()
                while (iterator.hasNext()) {
                    val address = iterator.next()
                    if (!activeAddresses.contains(address)) {
                        removedAddresses.add(address)
                        iterator.remove()
                    }
                }
            }
            synchronized(addressPeerMap) {
                val iterator = addressPeerMap.keys.iterator()
                while (iterator.hasNext()) {
                    val address = iterator.next()
                    if (!activeAddresses.contains(address)) {
                        removedAddresses.add(address)
                        iterator.remove()
                    }
                }
            }
        }
        removedAddresses.forEach { address -> clearRssiState(address) }
        val count = getAllConnectedAddresses().size
        val peerCount = getConnectedPeerCount()
        isConnected = count > 0
        logCallback("notifyConnectionState addrCount=$count peerCount=$peerCount isConnected=$isConnected")
        connectionCallback(isConnected, peerCount)
    }

    private fun scheduleProtocolCccdRetry(address: String, gatt: BluetoothGatt) {
        synchronized(cccdRetryJobs) {
            if (cccdRetryJobs[address]?.isActive == true) return
        }
        val attempt = synchronized(cccdRetryCounts) {
            val next = (cccdRetryCounts[address] ?: 0) + 1
            cccdRetryCounts[address] = next
            next
        }
        if (attempt > 6) {
            logCallback("CCCD retry exceeded for $address, disconnecting.")
            try {
                gatt.disconnect()
            } catch (_: Exception) {
            }
            return
        }
        val delayMs = (200L * attempt).coerceAtMost(1_500L)
        val job = scope.launch {
            delay(delayMs)
            synchronized(cccdRetryJobs) {
                cccdRetryJobs.remove(address)
            }
            val stillPending = synchronized(pendingClientConnections) {
                pendingClientConnections.containsKey(address)
            } || synchronized(clientConnections) {
                clientConnections.containsKey(address)
            }
            if (!stillPending) return@launch
            val result = enableNotifications(
                gatt,
                Constants.PROTOCOL_CHAR_UUID,
                address,
                required = true
            )
            when (result) {
                NotifySetupResult.STARTED -> Unit
                NotifySetupResult.RETRY -> scheduleProtocolCccdRetry(address, gatt)
                NotifySetupResult.MISSING -> {
                    logCallback("Service setup failed for $address, disconnecting.")
                    try {
                        gatt.disconnect()
                    } catch (_: Exception) {
                    }
                }
            }
        }
        synchronized(cccdRetryJobs) {
            cccdRetryJobs[address] = job
        }
    }

    private fun markClientReady(address: String, gatt: BluetoothGatt) {
        var added = false
        synchronized(clientConnections) {
            if (!clientConnections.containsKey(address)) {
                clientConnections[address] = gatt
                added = true
            }
        }
        synchronized(pendingClientConnections) {
            pendingClientConnections.remove(address)
        }
        synchronized(cccdRetryCounts) {
            cccdRetryCounts.remove(address)
        }
        synchronized(cccdRetryJobs) {
            cccdRetryJobs.remove(address)?.cancel()
        }
        if (added) {
            logCallback("Client ready (CCCD enabled): $address")
            notifyConnectionState()
            deviceMonitor.onConnectionEstablished(address)
        }
    }

    private fun markServerReady(address: String) {
        var added = false
        synchronized(connectedPeers) {
            if (!connectedPeers.contains(address)) {
                connectedPeers.add(address)
                added = true
            }
        }
        synchronized(pendingServerConnections) {
            pendingServerConnections.remove(address)
        }
        if (added) {
            logCallback("Server ready (CCCD enabled): $address")
            notifyConnectionState()
            Handler(Looper.getMainLooper()).post {
                onRescueConnected?.invoke()
            }
            deviceMonitor.onConnectionEstablished(address)
        }
    }

    private fun getSystemConnectedAddresses(): Set<String>? {
        val manager = bluetoothManager ?: return null
        val addresses = mutableSetOf<String>()
        try {
            manager.getConnectedDevices(BluetoothProfile.GATT)
                ?.mapTo(addresses) { it.address }
        } catch (_: Exception) {
        }
        try {
            manager.getConnectedDevices(BluetoothProfile.GATT_SERVER)
                ?.mapTo(addresses) { it.address }
        } catch (_: Exception) {
        }
        return addresses.takeIf { it.isNotEmpty() }
    }

    private fun isConnectionKnown(address: String): Boolean {
        synchronized(connectedPeers) {
            if (connectedPeers.contains(address)) return true
        }
        synchronized(clientConnections) {
            if (clientConnections.containsKey(address)) return true
        }
        synchronized(pendingClientConnections) {
            if (pendingClientConnections.containsKey(address)) return true
        }
        synchronized(pendingConnections) {
            if (pendingConnections.containsKey(address)) return true
        }
        synchronized(inFlightConnections) {
            if (inFlightConnections.containsKey(address)) return true
        }
        return false
    }

    private fun markPending(address: String): Boolean {
        synchronized(pendingConnections) {
            if (pendingConnections.containsKey(address)) return false
            pendingConnections[address] = SystemClock.elapsedRealtime()
            return true
        }
    }

    private fun clearPending(address: String) {
        synchronized(pendingConnections) {
            pendingConnections.remove(address)
        }
        synchronized(pendingPeerIds) {
            pendingPeerIds.remove(address)
        }
    }

    private fun isPending(address: String): Boolean {
        synchronized(pendingConnections) {
            return pendingConnections.containsKey(address)
        }
    }

    private fun isInFlight(address: String): Boolean {
        synchronized(inFlightConnections) {
            return inFlightConnections.containsKey(address)
        }
    }

    private fun removeInFlight(address: String): Boolean {
        synchronized(inFlightConnections) {
            return inFlightConnections.remove(address) != null
        }
    }

    private fun isAttemptExpired(attempt: ConnectionAttempt, now: Long = System.currentTimeMillis()): Boolean {
        return now - attempt.lastAttemptMs >= connectionAttemptResetMs
    }

    private fun isConnectionAttemptAllowed(address: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(connectionAttempts) {
            val attempt = connectionAttempts[address] ?: return true
            if (isAttemptExpired(attempt, now)) {
                connectionAttempts.remove(address)
                return true
            }
            if (attempt.attempts >= maxConnectionAttempts) {
                return false
            }
            val elapsed = now - attempt.lastAttemptMs
            val required = requiredBackoffMs(attempt.attempts)
            return elapsed >= required
        }
    }

    private fun recordConnectionAttempt(address: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(connectionAttempts) {
            val attempt = connectionAttempts[address]
            if (attempt != null && !isAttemptExpired(attempt, now) && attempt.attempts >= maxConnectionAttempts) {
                return false
            }
            val nextAttempts = if (attempt == null || isAttemptExpired(attempt, now)) {
                1
            } else {
                (attempt.attempts + 1).coerceAtMost(maxConnectionAttempts)
            }
            connectionAttempts[address] = ConnectionAttempt(nextAttempts, now)
            return true
        }
    }

    private fun clearConnectionAttempt(address: String) {
        synchronized(connectionAttempts) {
            connectionAttempts.remove(address)
        }
    }

    private fun requiredBackoffMs(attempts: Int): Long {
        return connectionBackoffBaseMs
    }

    fun getDebugSnapshot(): BleDebugSnapshot {
        val scanValues = synchronized(scanRssi) { scanRssi.values.toList() }
        val connectionValues = synchronized(connectionRssi) { connectionRssi.values.toList() }
        val pendingCount = synchronized(pendingConnections) { pendingConnections.size }
        val attemptTracked = synchronized(connectionAttempts) { connectionAttempts.size }
        return BleDebugSnapshot(
            scanRssiAvg = averageRssi(scanValues),
            scanRssiCount = scanValues.size,
            connectionRssiAvg = averageRssi(connectionValues),
            connectionRssiCount = connectionValues.size,
            pendingCount = pendingCount,
            attemptTracked = attemptTracked,
            maxAttempts = maxConnectionAttempts
        )
    }

    private fun averageRssi(values: List<Int>): Int? {
        if (values.isEmpty()) return null
        return values.sum() / values.size
    }

    private fun pickPeerRssi(
        connectionValue: Int?,
        connectionUpdatedAtMs: Long?,
        scanValue: Int?,
        scanUpdatedAtMs: Long?,
        nowMs: Long
    ): Int? {
        fun isWithin(updatedAtMs: Long?, thresholdMs: Long): Boolean {
            if (updatedAtMs == null) return false
            return nowMs - updatedAtMs <= thresholdMs
        }

        val hasFreshConnection = connectionValue != null && isWithin(connectionUpdatedAtMs, rssiDataStaleMs)
        val hasFreshScan = scanValue != null && isWithin(scanUpdatedAtMs, rssiDataStaleMs)
        if (hasFreshConnection && hasFreshScan) {
            return if ((connectionUpdatedAtMs ?: 0L) >= (scanUpdatedAtMs ?: 0L)) connectionValue else scanValue
        }
        if (hasFreshConnection) return connectionValue
        if (hasFreshScan) return scanValue

        val hasConnection = connectionValue != null && isWithin(connectionUpdatedAtMs, rssiDataExpireMs)
        val hasScan = scanValue != null && isWithin(scanUpdatedAtMs, rssiDataExpireMs)
        return when {
            hasConnection && hasScan -> if ((connectionUpdatedAtMs ?: 0L) >= (scanUpdatedAtMs ?: 0L)) connectionValue else scanValue
            hasConnection -> connectionValue
            hasScan -> scanValue
            else -> null
        }
    }

    private fun pickLastKnownRssi(
        connectionValue: Int?,
        connectionUpdatedAtMs: Long?,
        scanValue: Int?,
        scanUpdatedAtMs: Long?
    ): Int? {
        if (connectionValue == null && scanValue == null) return null
        return when {
            connectionValue != null && scanValue != null ->
                if ((connectionUpdatedAtMs ?: 0L) >= (scanUpdatedAtMs ?: 0L)) connectionValue else scanValue

            connectionValue != null -> connectionValue
            else -> scanValue
        }
    }

    fun getPeerRssiSnapshot(): Map<String, Int> {
        val connectedAddresses = getAllConnectedAddresses()
        val addressToPeer = synchronized(addressPeerMap) { addressPeerMap.toMap() }
        val connection = synchronized(connectionRssi) { connectionRssi.toMap() }
        val connectionUpdatedAt = synchronized(connectionRssiUpdatedAtMs) { connectionRssiUpdatedAtMs.toMap() }
        val scan = synchronized(scanRssi) { scanRssi.toMap() }
        val scanUpdatedAt = synchronized(scanRssiUpdatedAtMs) { scanRssiUpdatedAtMs.toMap() }
        val now = SystemClock.elapsedRealtime()
        val result = mutableMapOf<String, Int>()
        addressToPeer.forEach { (address, peerId) ->
            val rssi = pickPeerRssi(
                connectionValue = connection[address],
                connectionUpdatedAtMs = connectionUpdatedAt[address],
                scanValue = scan[address],
                scanUpdatedAtMs = scanUpdatedAt[address],
                nowMs = now
            ) ?: if (connectedAddresses.contains(address)) {
                pickLastKnownRssi(
                    connectionValue = connection[address],
                    connectionUpdatedAtMs = connectionUpdatedAt[address],
                    scanValue = scan[address],
                    scanUpdatedAtMs = scanUpdatedAt[address]
                )
            } else {
                null
            } ?: return@forEach
            val existing = result[peerId]
            if (existing == null || rssi > existing) {
                result[peerId] = rssi
            }
        }
        return result
    }

    fun getConnectedPeerCount(): Int {
        val addresses = getAllConnectedAddresses()
        val peerIds = mutableSetOf<String>()
        synchronized(addressPeerMap) {
            addresses.forEach { address ->
                val peerId = addressPeerMap[address]
                if (peerId != null) {
                    peerIds.add(peerId)
                }
            }
        }
        return peerIds.size
    }

    fun getConnectedPeerIds(): List<String> {
        val addresses = getAllConnectedAddresses()
        val peerIds = mutableSetOf<String>()
        synchronized(addressPeerMap) {
            addresses.forEach { address ->
                val peerId = addressPeerMap[address]
                if (peerId != null) {
                    peerIds.add(peerId)
                }
            }
        }
        return peerIds.toList().sorted()
    }

    fun bindPeerIdForAddress(address: String, peerIdHex: String) {
        synchronized(addressPeerMap) {
            addressPeerMap[address] = peerIdHex
        }
    }

    fun onAnnounceReceived(address: String) {
        deviceMonitor.onAnnounceReceived(address)
    }

    fun clearDeviceMonitoring() {
        deviceMonitor.clearAll()
    }

    fun clearAllConnectionsAndMappings() {
        deviceMonitor.clearAll()
        synchronized(clientConnections) {
            clientConnections.values.forEach { gatt ->
                try {
                    gatt.disconnect()
                } catch (_: Exception) {
                }
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
            }
            clientConnections.clear()
        }
        synchronized(pendingClientConnections) {
            pendingClientConnections.values.forEach { gatt ->
                try {
                    gatt.disconnect()
                } catch (_: Exception) {
                }
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
            }
            pendingClientConnections.clear()
        }
        val serverDevices = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT).orEmpty()
        serverDevices.forEach { device ->
            try {
                gattServer?.cancelConnection(device)
            } catch (_: Exception) {
            }
        }
        synchronized(connectedPeers) {
            connectedPeers.clear()
        }
        synchronized(pendingServerConnections) {
            pendingServerConnections.clear()
        }
        synchronized(cccdRetryCounts) {
            cccdRetryCounts.clear()
        }
        synchronized(cccdRetryJobs) {
            cccdRetryJobs.values.forEach { it.cancel() }
            cccdRetryJobs.clear()
        }
        synchronized(addressPeerMap) {
            addressPeerMap.clear()
        }
        synchronized(pendingPeerIds) {
            pendingPeerIds.clear()
        }
        synchronized(pendingConnections) {
            pendingConnections.clear()
        }
        synchronized(connectionAttempts) {
            connectionAttempts.clear()
        }
        clearInFlightConnections()
        clearAllRssiState()
        notifyConnectionState()
    }

    private fun disconnectAddress(address: String) {
        clearRssiReadPending(address)
        synchronized(clientConnections) {
            clientConnections[address]
        }?.let { gatt ->
            try {
                gatt.disconnect()
            } catch (_: Exception) {
            }
        }
        try {
            val device = bluetoothManager?.adapter?.getRemoteDevice(address)
            if (device != null) {
                gattServer?.cancelConnection(device)
            }
        } catch (_: Exception) {
        }
    }

    private fun extractPeerId(result: ScanResult): String? {
        val data = result.scanRecord?.getServiceData(ParcelUuid(Constants.SERVICE_UUID)) ?: return null
        if (data.size < 8) return null
        return data.copyOfRange(0, 8).toHexString()
    }

    private fun isPeerConnected(peerIdHex: String): Boolean {
        synchronized(addressPeerMap) {
            if (addressPeerMap.values.any { it == peerIdHex }) return true
        }
        return false
    }

    private fun recordPendingPeerId(address: String, peerId: String) {
        synchronized(pendingPeerIds) {
            pendingPeerIds[address] = peerId
        }
    }

    private fun attachPendingPeerId(address: String) {
        val peerId = synchronized(pendingPeerIds) { pendingPeerIds.remove(address) } ?: return
        synchronized(addressPeerMap) {
            addressPeerMap[address] = peerId
        }
    }

    private fun clearPeerId(address: String) {
        synchronized(addressPeerMap) {
            addressPeerMap.remove(address)
        }
    }

    private fun ByteArray.toHexString(): String {
        return joinToString("") { "%02x".format(it) }
    }

    private fun relayData(sender: String, data: ByteArray, characteristicUuid: UUID) {
        val char = gattServer?.getService(Constants.SERVICE_UUID)
            ?.getCharacteristic(characteristicUuid) ?: return
        val devices = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT) ?: return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            char.value = data
        }

        for (dev in devices) {
            if (dev.address != sender) {
                try {
                    lastServerNotifyAtMs = SystemClock.elapsedRealtime()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        gattServer?.notifyCharacteristicChanged(dev, char, false, data)
                    } else {
                        gattServer?.notifyCharacteristicChanged(dev, char, false)
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    fun disconnect() {
        autoConnectActive = false
        handler.removeCallbacksAndMessages(null)
        stopScan()
        stopAdvertising()
        stopMaintenanceJobs()
        synchronized(clientConnections) {
            clientConnections.values.forEach { gatt ->
                try {
                    gatt.disconnect()
                } catch (_: Exception) {
                }
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
            }
            clientConnections.clear()
        }
        synchronized(pendingClientConnections) {
            pendingClientConnections.values.forEach { gatt ->
                try {
                    gatt.disconnect()
                } catch (_: Exception) {
                }
                try {
                    gatt.close()
                } catch (_: Exception) {
                }
            }
            pendingClientConnections.clear()
        }
        synchronized(pendingConnections) {
            pendingConnections.clear()
        }
        synchronized(connectionAttempts) {
            connectionAttempts.clear()
        }
        clearInFlightConnections()
        clearAllRssiState()
        scheduleGattServerClose()
        isConnected = false
        synchronized(connectedPeers) {
            connectedPeers.clear()
        }
        synchronized(pendingServerConnections) {
            pendingServerConnections.clear()
        }
        synchronized(cccdRetryCounts) {
            cccdRetryCounts.clear()
        }
        synchronized(cccdRetryJobs) {
            cccdRetryJobs.values.forEach { it.cancel() }
            cccdRetryJobs.clear()
        }
        connectionCallback(false, 0)
    }

    private fun cancelGattServerClose() {
        gattServerCloseJob?.cancel()
        gattServerCloseJob = null
    }

    private fun scheduleGattServerClose() {
        val server = gattServer ?: return
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - lastServerNotifyAtMs
        val delayMs = if (elapsed >= gattServerCloseDelayMs) 0L else gattServerCloseDelayMs - elapsed
        gattServerCloseJob?.cancel()
        gattServerCloseJob = scope.launch {
            if (delayMs > 0) {
                delay(delayMs)
            }
            if (gattServer !== server) return@launch
            val since = SystemClock.elapsedRealtime() - lastServerNotifyAtMs
            if (since < gattServerCloseDelayMs) {
                scheduleGattServerClose()
                return@launch
            }
            try {
                server.close()
            } catch (_: Exception) {
            }
            if (gattServer === server) {
                gattServer = null
            }
        }
    }

    // 리소스 정리 함수 (앱 종료 시 호출)
    fun release() {
        try {
            autoConnectActive = false
            stopAdvertising()
            stopScan()
            stopMaintenanceJobs()
            gattServer?.close()
            synchronized(clientConnections) {
                clientConnections.values.forEach { gatt ->
                    try {
                        gatt.disconnect()
                    } catch (_: Exception) {
                    }
                    try {
                        gatt.close()
                    } catch (_: Exception) {
                    }
                }
                clientConnections.clear()
            }
            synchronized(connectionAttempts) {
                connectionAttempts.clear()
            }
            clearInFlightConnections()
            clearAllRssiState()
            // 코루틴 취소 (메모리 릭 방지)
            job.cancel()
            logCallback("BleManager resources released.")
        } catch (e: Exception) {
            logCallback("Error releasing resources: ${e.message}")
        }
    }

    private fun clearInFlightConnections() {
        val inflight = synchronized(inFlightConnections) {
            val snapshot = inFlightConnections.values.toList()
            inFlightConnections.clear()
            snapshot
        }
        inflight.forEach { gatt ->
            try {
                gatt.disconnect()
            } catch (_: Exception) {
            }
            try {
                gatt.close()
            } catch (_: Exception) {
            }
        }
    }

    private inner class BleClientManager {
        fun startScan(): Boolean = startScanImpl()
        fun stopScan() = stopScanImpl()
        fun restartScan(delayMs: Long = 1_000L) = restartScanImpl(delayMs)
        fun connectToPeer(device: BluetoothDevice) = connectToPeerImpl(device)
    }

    private inner class BleServerManager {
        fun startAdvertisingInternal(isEmergencyMode: Boolean): Boolean =
            startAdvertisingInternalImpl(isEmergencyMode)

        fun stopAdvertising() = stopAdvertisingImpl()
        fun setupGattServer() = setupGattServerImpl()
    }
}
