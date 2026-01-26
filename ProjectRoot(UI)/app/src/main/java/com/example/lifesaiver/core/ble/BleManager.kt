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
import java.nio.charset.Charset
import java.util.UUID
import kotlin.random.Random

object Constants {
    val SERVICE_UUID: UUID = UUID.fromString("0000AAAA-0000-1000-8000-00805f9b34fb")
    val CHAR_UUID: UUID = UUID.fromString("0000BBBB-0000-1000-8000-00805f9b34fb")
    const val TYPE_AUDIO: Byte = 0x01
    const val TYPE_TEXT: Byte = 0x02
}

@SuppressLint("MissingPermission")
class BleManager(
    private val context: Context,
    private val logCallback: (String) -> Unit,
    private val audioCallback: (ByteArray) -> Unit,
    private val textCallback: (String) -> Unit,
    private val connectionCallback: (Boolean) -> Unit
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter = bluetoothManager?.adapter

    private val scanner: BluetoothLeScanner?
        get() = adapter?.bluetoothLeScanner

    private val advertiser: BluetoothLeAdvertiser?
        get() = adapter?.bluetoothLeAdvertiser

    private val handler = Handler(Looper.getMainLooper())

    var isHost = false
    private var isConnected = false
    private var gattServer: BluetoothGattServer? = null
    private var hostGatt: BluetoothGatt? = null

    private var currentAdvertisingSet: AdvertisingSet? = null

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

    fun startAutoConnect() {
        if (adapter == null || !adapter.isEnabled) {
            logCallback("Bluetooth is off.")
            return
        }

        disconnect()
        isConnected = false
        connectionCallback(false)
        isHost = false

        logCallback("Auto connect start.")
        enterScanMode()
    }

    private fun enterScanMode() {
        if (isConnected) return

        stopAdvertise()
        isHost = false

        val scanDuration = Random.nextLong(4000, 7000)
        logCallback("Scan mode: searching for host (${scanDuration / 1000.0}s).")

        if (startScan()) {
            handler.postDelayed({
                if (!isConnected) {
                    stopScan()
                    enterAdvertiseMode()
                }
            }, scanDuration)
        } else {
            enterAdvertiseMode()
        }
    }

    private fun enterAdvertiseMode() {
        if (isConnected) return

        stopScan()

        val advDuration = Random.nextLong(10000, 15000)
        logCallback("Advertise mode: becoming host (${advDuration / 1000.0}s).")

        if (startAdvertise()) {
            isHost = true
            setupGattServer()

            handler.postDelayed({
                val connectedDevs = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT)
                if (!isConnected && connectedDevs.isNullOrEmpty()) {
                    logCallback("No peers -> back to scan.")
                    stopAdvertise()
                    enterScanMode()
                }
            }, advDuration)
        } else {
            enterScanMode()
        }
    }

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

    private fun startAdvertise(): Boolean {
        return try {
            val parameters = AdvertisingSetParameters.Builder()
                .setLegacyMode(true)
                .setConnectable(true)
                .setScannable(true)
                .setInterval(AdvertisingSetParameters.INTERVAL_HIGH)
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

    private fun stopAdvertise() {
        try {
            advertiser?.stopAdvertisingSet(advertisingCallback)
            currentAdvertisingSet = null
        } catch (e: Exception) {
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device ?: return

            if (isConnected || hostGatt != null) return

            if (result.rssi > -90) {
                logCallback("Host found: ${device.address}")
                handler.removeCallbacksAndMessages(null)
                stopScan()
                stopAdvertise()
                connectToHost(device)
            }
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

    private fun connectToHost(device: BluetoothDevice) {
        try {
            val phyMask = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isLongRangeSupported()) {
                BluetoothDevice.PHY_LE_1M_MASK or BluetoothDevice.PHY_LE_CODED_MASK
            } else BluetoothDevice.PHY_LE_1M_MASK

            hostGatt = device.connectGatt(
                context,
                false,
                gattClientCallback,
                BluetoothDevice.TRANSPORT_LE,
                phyMask
            )
        } catch (e: Exception) {
            logCallback("Connect error: ${e.message}")
            enterScanMode()
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logCallback("Connected. Requesting MTU...")
                isConnected = true
                connectionCallback(true)
                isHost = false
                handler.removeCallbacksAndMessages(null)

                gatt.requestMtu(512)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                logCallback("Disconnected.")
                isConnected = false
                connectionCallback(false)
                hostGatt?.close()
                hostGatt = null
                startAutoConnect()
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            logCallback("MTU changed: $mtu bytes")
            gatt?.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val char = gatt.getService(Constants.SERVICE_UUID)?.getCharacteristic(Constants.CHAR_UUID)
            if (char != null) {
                gatt.setCharacteristicNotification(char, true)
                val descriptor = char.getDescriptor(
                    UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                )
                if (descriptor != null) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    gatt.writeDescriptor(descriptor)
                }
                logCallback("Ready to chat.")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleReceivedData(characteristic.value)
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
            val char = BluetoothGattCharacteristic(
                Constants.CHAR_UUID,
                BluetoothGattCharacteristic.PROPERTY_READ or
                    BluetoothGattCharacteristic.PROPERTY_WRITE or
                    BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                    BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
                BluetoothGattCharacteristic.PERMISSION_READ or
                    BluetoothGattCharacteristic.PERMISSION_WRITE
            )
            service.addCharacteristic(char)
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
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                logCallback("Peer joined (${device.address}).")
                isConnected = true
                connectionCallback(true)
                handler.removeCallbacksAndMessages(null)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                isConnected = false
                connectionCallback(false)
                startAutoConnect()
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
            handleReceivedData(value)
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            relayData(device.address, value)
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

    private fun sendPacket(type: Byte, payload: ByteArray) {
        if (!isConnected) return
        val packet = ByteArray(payload.size + 1)
        packet[0] = type
        System.arraycopy(payload, 0, packet, 1, payload.size)
        if (isHost) hostBroadcast(packet) else clientSend(packet)
    }

    private fun hostBroadcast(data: ByteArray) {
        val char = gattServer?.getService(Constants.SERVICE_UUID)
            ?.getCharacteristic(Constants.CHAR_UUID) ?: return
        val devices = bluetoothManager?.getConnectedDevices(BluetoothProfile.GATT) ?: return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            char.value = data
        }

        for (dev in devices) {
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

    private fun clientSend(data: ByteArray) {
        val char = hostGatt?.getService(Constants.SERVICE_UUID)
            ?.getCharacteristic(Constants.CHAR_UUID) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hostGatt?.writeCharacteristic(
                    char,
                    data,
                    BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                )
            } else {
                char.value = data
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                hostGatt?.writeCharacteristic(char)
            }
        } catch (e: Exception) {
        }
    }

    private fun relayData(sender: String, data: ByteArray) {
        val char = gattServer?.getService(Constants.SERVICE_UUID)
            ?.getCharacteristic(Constants.CHAR_UUID) ?: return
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
        stopAdvertise()
        hostGatt?.close()
        hostGatt = null
        gattServer?.close()
        gattServer = null
        isConnected = false
        connectionCallback(false)
    }
}
