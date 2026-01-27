package com.example.lifesaiver.core.ble

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
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.*
import java.nio.charset.Charset
import java.util.UUID

object Constants {
    val SERVICE_UUID: UUID = UUID.fromString("0000AAAA-0000-1000-8000-00805f9b34fb")
    val CHAR_UUID: UUID = UUID.fromString("0000BBBB-0000-1000-8000-00805f9b34fb")
    val PROTOCOL_CHAR_UUID: UUID = UUID.fromString("0000CCCC-0000-1000-8000-00805f9b34fb")
    const val TYPE_AUDIO: Byte = 0x01
    const val TYPE_TEXT: Byte = 0x02
}

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

    var isHost = false
    private var isConnected = false
    private val connectedPeers = mutableSetOf<String>()
    private val clientConnections = mutableMapOf<String, BluetoothGatt>()
    private val pendingConnections = mutableSetOf<String>()
    private var gattServer: BluetoothGattServer? = null

    private var currentAdvertisingSet: AdvertisingSet? = null

    // 구조대가 연결되었을 때 실행할 콜백 (사이렌 울리기용)
    var onRescueConnected: (() -> Unit)? = null
    // UI에 상태 메시지를 전달하기 위한 콜백 (토스트용)
    var onModeChange: ((String) -> Unit)? = null

    fun setProtocolCallback(listener: (ByteArray, String?) -> Unit) {
        protocolCallback = listener
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
        startAdvertisingInternal(isEmergencyMode = true)
    }

    // --------------------------------------------------------------------------
    // [자동 연결] Auto Connect 기능
    // --------------------------------------------------------------------------

    fun startAutoConnect() {
        if (adapter == null || !adapter.isEnabled) {
            logCallback("Bluetooth is off.")
            return
        }

        disconnect()
        isConnected = false
        connectionCallback(false, 0)
        isHost = true

        logCallback("Auto connect start (mesh mode).")
        setupGattServer()
        startAdvertisingInternal(isEmergencyMode = false)
        startScan()
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

    /**
     * 광고 시작 내부 함수
     * @param isEmergencyMode
     * - true (SOS): Interval High (느림=절전), TxPower High (강함=최대거리) -> 72시간 생존
     * - false (일반): Interval High (느림=절전), TxPower High (강함=일반)
     */
    private fun startAdvertisingInternal(isEmergencyMode: Boolean): Boolean {
        return try {
            val parameters = AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setConnectable(true) // 구조대가 연결할 수 있어야 함 (필수)
                .setScannable(true)
                // [핵심 전략] SOS 모드여도 배터리를 위해 Interval은 HIGH(느림, 약 1초) 사용
                .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
                // [핵심 전략] 신호 세기는 무조건 최대(High)로 하여 도달 거리 확보
                .setTxPowerLevel(AdvertisingSetParameters.TX_POWER_HIGH)
                .setPrimaryPhy(BluetoothDevice.PHY_LE_1M)
                .setSecondaryPhy(BluetoothDevice.PHY_LE_1M)
                .build()

            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(android.os.ParcelUuid(Constants.SERVICE_UUID))
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

    fun stopAdvertising() {
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
            if (result.rssi <= -90) return
            if (isConnectionKnown(address)) return
            if (!markPending(address)) return

            logCallback("Peer found: ${device.address}")
            connectToPeer(device)
        }

        override fun onScanFailed(errorCode: Int) {
            logCallback("Scan failed ($errorCode)")
        }
    }

    private fun startScan(): Boolean {
        return try {
            val filters = listOf(
                ScanFilter.Builder().setServiceUuid(android.os.ParcelUuid(Constants.SERVICE_UUID)).build()
            )
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setLegacy(true)
                .build()
            scanner?.startScan(filters, settings, scanCallback)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun stopScan() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {
        }
    }

    private fun connectToPeer(device: BluetoothDevice) {
        try {
            val phyMask = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isLongRangeSupported()) {
                BluetoothDevice.PHY_LE_1M_MASK or BluetoothDevice.PHY_LE_CODED_MASK
            } else BluetoothDevice.PHY_LE_1M_MASK

            device.connectGatt(
                context,
                false,
                gattClientCallback,
                BluetoothDevice.TRANSPORT_LE,
                phyMask
            )
        } catch (e: Exception) {
            logCallback("Connect error: ${e.message}")
            clearPending(device.address)
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val address = gatt.device.address
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logCallback("Connected. Requesting MTU...")
                synchronized(clientConnections) {
                    clientConnections[address] = gatt
                }
                clearPending(address)
                notifyConnectionState()
                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                logCallback("Disconnected.")
                synchronized(clientConnections) {
                    clientConnections.remove(address)
                }
                clearPending(address)
                notifyConnectionState()
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
            enableNotifications(gatt, Constants.CHAR_UUID)
            enableNotifications(gatt, Constants.PROTOCOL_CHAR_UUID)
            logCallback("Ready to chat.")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == Constants.PROTOCOL_CHAR_UUID) {
                protocolCallback(characteristic.value, gatt.device.address)
            } else {
                handleReceivedData(characteristic.value)
            }
        }
    }

    private fun enableNotifications(gatt: BluetoothGatt, characteristicUuid: UUID) {
        val char = gatt.getService(Constants.SERVICE_UUID)?.getCharacteristic(characteristicUuid)
        if (char == null) return
        gatt.setCharacteristicNotification(char, true)
        val descriptor = char.getDescriptor(
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        )
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun setupGattServer() {
        if (gattServer != null) return
        try {
            gattServer = bluetoothManager?.openGattServer(context, gattServerCallback)
            val service = BluetoothGattService(
                Constants.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY
            )
            val legacyChar = BluetoothGattCharacteristic(
                Constants.CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            val protocolChar = BluetoothGattCharacteristic(
                Constants.PROTOCOL_CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
            )
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
                logCallback("✅ Peer joined (구조대 접속!): ${device.address}")

                // 구조대가 접속하면 ViewModel에 알림 (사이렌 울리기)
                Handler(Looper.getMainLooper()).post {
                    onRescueConnected?.invoke()
                }

                synchronized(connectedPeers) {
                    connectedPeers.add(device.address)
                }
                notifyConnectionState()
                handler.removeCallbacksAndMessages(null)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                synchronized(connectedPeers) {
                    connectedPeers.remove(device.address)
                }
                notifyConnectionState()
            }
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
        if (hostSendTo(address, Constants.PROTOCOL_CHAR_UUID, data)) return true
        return clientSendTo(address, Constants.PROTOCOL_CHAR_UUID, data)
    }

    private fun sendPacket(type: Byte, payload: ByteArray) {
        val packet = ByteArray(payload.size + 1)
        packet[0] = type
        System.arraycopy(payload, 0, packet, 1, payload.size)
        sendRaw(Constants.CHAR_UUID, packet)
    }

    private fun sendRaw(characteristicUuid: UUID, data: ByteArray, excludeAddress: String? = null) {
        if (!isConnected) return
        val serverAddresses = hostBroadcast(characteristicUuid, data, excludeAddress)
        clientBroadcast(characteristicUuid, data, excludeAddress, serverAddresses)
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
        if (!activeAddresses.isNullOrEmpty()) {
            synchronized(connectedPeers) {
                connectedPeers.retainAll(activeAddresses)
            }
            synchronized(clientConnections) {
                val iterator = clientConnections.keys.iterator()
                while (iterator.hasNext()) {
                    val address = iterator.next()
                    if (!activeAddresses.contains(address)) {
                        iterator.remove()
                    }
                }
            }
        }
        val count = getAllConnectedAddresses().size
        isConnected = count > 0
        connectionCallback(isConnected, count)
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
        synchronized(pendingConnections) {
            if (pendingConnections.contains(address)) return true
        }
        return false
    }

    private fun markPending(address: String): Boolean {
        synchronized(pendingConnections) {
            if (pendingConnections.contains(address)) return false
            pendingConnections.add(address)
            return true
        }
    }

    private fun clearPending(address: String) {
        synchronized(pendingConnections) {
            pendingConnections.remove(address)
        }
    }

    fun getConnectedPeerCount(): Int {
        return getAllConnectedAddresses().size
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
        handler.removeCallbacksAndMessages(null)
        stopScan()
        stopAdvertising()
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
        synchronized(pendingConnections) {
            pendingConnections.clear()
        }
        gattServer?.close()
        gattServer = null
        isConnected = false
        synchronized(connectedPeers) {
            connectedPeers.clear()
        }
        connectionCallback(false, 0)
    }

    // 리소스 정리 함수 (앱 종료 시 호출)
    fun release() {
        try {
            stopAdvertising()
            stopScan()
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
            // 코루틴 취소 (메모리 릭 방지)
            job.cancel()
            logCallback("BleManager resources released.")
        } catch (e: Exception) {
            logCallback("Error releasing resources: ${e.message}")
        }
    }
}
