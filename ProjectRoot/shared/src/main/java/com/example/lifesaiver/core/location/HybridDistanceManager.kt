package com.example.lifesaiver.core.location

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import com.example.lifesaiver.core.ble.BleRSSILocating
import com.example.lifesaiver.core.call.CallTransportType
import com.example.lifesaiver.core.uwb.UwbRanger
import com.example.lifesaiver.core.wifi.WifiAwareRanger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

data class HybridLocationState(
    val distanceMeters: Float? = null,
    val measurementSource: DistanceMeasurementSource = DistanceMeasurementSource.NONE,
    val isPrecisionMode: Boolean = false,
    val statusMessage: String = "Initializing..."
)

class HybridDistanceManager(
    private val context: Context,
    private val bleLocating: BleRSSILocating,
    private val wifiRanger: WifiAwareRanger,
    private val uwbRanger: UwbRanger = UwbRanger(context)
) {
    private val wifiAwareEnabled = true
    private val hasBleHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    private val hasRttHardware = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)
    private val hasUwbHardware =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)

    private val activeTransport = MutableStateFlow(CallTransportType.NONE)
    private val localSupportsUwb = MutableStateFlow(hasUwbHardware)
    private val peerSupportsUwb = MutableStateFlow(false)
    private var isStarted = false
    private var bleTrackingActive = false
    private var rttTrackingActive = false
    private var uwbTrackingActive = false

    private val sensorState = combine(
        bleLocating.distance.onStart { emit(null) },
        if (wifiAwareEnabled) wifiRanger.rttDistance.onStart { emit(null) } else flowOf(null),
        uwbRanger.distanceMeters.onStart { emit(null) }
    ) { bleDist, rttDist, uwbDist ->
        SensorSnapshot(bleDist = bleDist, rttDist = rttDist, uwbDist = uwbDist)
    }

    val locationState: Flow<HybridLocationState> = combine(
        sensorState,
        activeTransport,
        localSupportsUwb,
        peerSupportsUwb
    ) { sensors, transport, localUwb, peerUwb ->
        val uwbReady = localUwb && hasUwbHardware
        val uwbPeerKnown = peerUwb
        val primary = when {
            uwbReady && sensors.uwbDist != null -> MeasurementSelection(
                distance = sensors.uwbDist,
                source = DistanceMeasurementSource.UWB
            )

            else -> selectTransportMeasurement(
                transport = transport,
                bleDist = sensors.bleDist,
                rttDist = sensors.rttDist
            )
        }

        val statusMessage = when (primary.source) {
            DistanceMeasurementSource.UWB -> "Tracking via UWB"
            DistanceMeasurementSource.RTT -> "Tracking via Wi-Fi RTT"
            DistanceMeasurementSource.RSSI -> "Tracking via Wi-Fi Direct RSSI"
            DistanceMeasurementSource.NONE -> when {
                uwbReady && uwbPeerKnown -> "UWB ranging is initializing..."
                uwbReady -> "UWB available (waiting for peer session)..."
                transport == CallTransportType.WIFI_AWARE -> "Searching RTT signal..."
                transport == CallTransportType.WIFI_DIRECT -> "Searching RSSI signal..."
                else -> "Searching for signal..."
            }
        }

        HybridLocationState(
            distanceMeters = primary.distance,
            measurementSource = primary.source,
            isPrecisionMode = primary.source == DistanceMeasurementSource.RTT ||
                primary.source == DistanceMeasurementSource.UWB,
            statusMessage = statusMessage
        )
    }

    fun setTargetPeerId(peerIdHex: String?) {
        bleLocating.setTargetPeerId(peerIdHex)
    }

    fun setActiveTransport(transport: CallTransportType) {
        activeTransport.value = transport
        refreshTrackingState()
    }

    fun setUwbCapability(localSupported: Boolean, peerSupported: Boolean) {
        localSupportsUwb.value = localSupported && hasUwbHardware
        peerSupportsUwb.value = peerSupported
        refreshTrackingState()
    }

    fun start() {
        isStarted = true
        refreshTrackingState()
    }

    fun stop() {
        isStarted = false
        setBleTracking(false)
        setRttTracking(false)
        setUwbTracking(false)
    }

    private fun isBluetoothEnabled(): Boolean {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        return adapter?.isEnabled == true
    }

    private fun isWifiEnabled(): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        return wifiManager?.isWifiEnabled == true
    }

    private fun refreshTrackingState() {
        if (!isStarted) return

        val transport = activeTransport.value
        val runRssi = hasBleHardware &&
            isBluetoothEnabled() &&
            (transport == CallTransportType.WIFI_DIRECT || transport == CallTransportType.NONE)
        val runRtt = wifiAwareEnabled &&
            hasRttHardware &&
            isWifiEnabled() &&
            (transport == CallTransportType.WIFI_AWARE || transport == CallTransportType.NONE)
        val runUwb = hasUwbHardware &&
            localSupportsUwb.value &&
            uwbRanger.isSupported() &&
            uwbRanger.hasPermission()

        setBleTracking(runRssi)
        setRttTracking(runRtt)
        setUwbTracking(runUwb)
    }

    private fun setBleTracking(shouldRun: Boolean) {
        if (shouldRun == bleTrackingActive) return
        bleTrackingActive = shouldRun
        if (shouldRun) {
            bleLocating.startTracking()
        } else {
            bleLocating.stopTracking()
        }
    }

    private fun setRttTracking(shouldRun: Boolean) {
        if (shouldRun == rttTrackingActive) return
        rttTrackingActive = shouldRun
        if (shouldRun) {
            wifiRanger.start()
        } else {
            wifiRanger.stop()
        }
    }

    private fun setUwbTracking(shouldRun: Boolean) {
        if (shouldRun == uwbTrackingActive) return
        uwbTrackingActive = shouldRun
        if (shouldRun) {
            uwbRanger.start()
        } else {
            uwbRanger.stop()
        }
    }

    private fun selectTransportMeasurement(
        transport: CallTransportType,
        bleDist: Float?,
        rttDist: Float?
    ): MeasurementSelection {
        return when (transport) {
            CallTransportType.WIFI_DIRECT -> MeasurementSelection(
                distance = bleDist,
                source = if (bleDist != null) {
                    DistanceMeasurementSource.RSSI
                } else {
                    DistanceMeasurementSource.NONE
                }
            )

            CallTransportType.WIFI_AWARE -> MeasurementSelection(
                distance = rttDist,
                source = if (rttDist != null) {
                    DistanceMeasurementSource.RTT
                } else {
                    DistanceMeasurementSource.NONE
                }
            )

            CallTransportType.NONE -> when {
                rttDist != null -> MeasurementSelection(rttDist, DistanceMeasurementSource.RTT)
                bleDist != null -> MeasurementSelection(bleDist, DistanceMeasurementSource.RSSI)
                else -> MeasurementSelection(null, DistanceMeasurementSource.NONE)
            }
        }
    }

    private data class MeasurementSelection(
        val distance: Float?,
        val source: DistanceMeasurementSource
    )

    private data class SensorSnapshot(
        val bleDist: Float?,
        val rttDist: Float?,
        val uwbDist: Float?
    )
}
