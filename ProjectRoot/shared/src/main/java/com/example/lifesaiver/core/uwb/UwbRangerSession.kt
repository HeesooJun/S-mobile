package com.example.lifesaiver.core.uwb

import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal fun UwbRanger.startInternal() {
    if (!isSupported() || !hasPermission()) {
        _distanceMeters.value = null
        synchronized(lock) { trackingEnabled = false }
        return
    }
    synchronized(lock) { trackingEnabled = true }
    startSessionIfReady()
}

internal fun UwbRanger.stopInternal() {
    synchronized(lock) {
        trackingEnabled = false
        cancelSessionLocked()
    }
    _distanceMeters.value = null
}

internal fun UwbRanger.endSessionInternal() {
    synchronized(lock) {
        trackingEnabled = false
        cancelSessionLocked()
        controllerScope = null
        controleeScope = null
        controllerOffer = null
        controleeAddressHex = null
        peerAddressHex = null
        remoteControllerAddressHex = null
        remoteControllerChannel = null
        remoteControllerPreambleIndex = null
        remoteSessionId = null
    }
    _distanceMeters.value = null
}

internal fun UwbRanger.releaseInternal() {
    endSessionInternal()
    clearAvailabilityCallback()
    rangerScope.cancel()
}

internal fun UwbRanger.restartSessionIfTracking() {
    val shouldTrack = synchronized(lock) { trackingEnabled }
    if (!shouldTrack) return
    synchronized(lock) { cancelSessionLocked() }
    startSessionIfReady()
}

internal fun UwbRanger.startSessionIfReady() {
    var snapshot: UwbRanger.SessionSnapshot? = null
    var token = 0L
    synchronized(lock) {
        if (!trackingEnabled || sessionJob != null) return
        snapshot = buildSnapshotLocked() ?: return
        sessionToken += 1L
        token = sessionToken
        sessionJob = rangerScope.launch {
            runSession(snapshot ?: return@launch, token)
        }
    }
}

internal suspend fun UwbRanger.runSession(snapshot: UwbRanger.SessionSnapshot, token: Long) {
    runCatching {
        when (snapshot) {
            is UwbRanger.SessionSnapshot.Controller -> collectController(snapshot)
            is UwbRanger.SessionSnapshot.Controlee -> collectControlee(snapshot)
        }
    }.onFailure { throwable ->
        if (throwable is IllegalStateException &&
            throwable.message?.contains("Ranging has already started", ignoreCase = true) == true
        ) {
            logUwb("session scope already used (${snapshot.role}) - recreate required")
            synchronized(lock) {
                when (snapshot.role) {
                    UwbRanger.SessionRole.CONTROLLER -> {
                        controllerScope = null
                        controllerOffer = null
                    }
                    UwbRanger.SessionRole.CONTROLEE -> {
                        controleeScope = null
                        controleeAddressHex = null
                    }
                }
            }
            emitDistanceIfTracking(null)
            return@onFailure
        }
        if (throwable is CancellationException) {
            logUwb("session cancelled (${snapshot.role})")
        } else {
            logUwb("session failed (${snapshot.role})", throwable)
            emitDistanceIfTracking(null)
        }
    }
    synchronized(lock) {
        if (sessionToken == token) {
            sessionJob = null
        }
    }
}

internal suspend fun UwbRanger.collectController(snapshot: UwbRanger.SessionSnapshot.Controller) {
    val sessionKeyInfo = buildStaticStsSessionKey(snapshot.sessionId)
    val parameters = RangingParameters(
        uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
        sessionId = snapshot.sessionId,
        subSessionId = 0,
        sessionKeyInfo = sessionKeyInfo,
        subSessionKeyInfo = null,
        complexChannel = null,
        peerDevices = listOf(UwbDevice(UwbAddress(snapshot.peerAddress))),
        updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
    )
    snapshot.scope.prepareSession(parameters).collect { result ->
        onRangingResult(result)
    }
}

internal suspend fun UwbRanger.collectControlee(snapshot: UwbRanger.SessionSnapshot.Controlee) {
    val sessionKeyInfo = buildStaticStsSessionKey(snapshot.sessionId)
    val parameters = RangingParameters(
        uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
        sessionId = snapshot.sessionId,
        subSessionId = 0,
        sessionKeyInfo = sessionKeyInfo,
        subSessionKeyInfo = null,
        complexChannel = UwbComplexChannel(
            snapshot.controllerChannel,
            snapshot.controllerPreambleIndex
        ),
        peerDevices = listOf(UwbDevice(UwbAddress(snapshot.controllerAddress))),
        updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
    )
    snapshot.scope.prepareSession(parameters).collect { result ->
        onRangingResult(result)
    }
}

internal fun UwbRanger.onRangingResult(result: RangingResult) {
    when (result) {
        is RangingResult.RangingResultPosition -> {
            emitDistanceIfTracking(result.position.distance?.value)
        }

        is RangingResult.RangingResultPeerDisconnected -> {
            emitDistanceIfTracking(null)
        }

        is RangingResult.RangingResultInitialized -> Unit

        is RangingResult.RangingResultFailure -> {
            emitDistanceIfTracking(null)
        }
    }
}

internal fun UwbRanger.emitDistanceIfTracking(distance: Float?) {
    val enabled = synchronized(lock) { trackingEnabled }
    if (enabled) {
        _distanceMeters.value = distance
    }
}

internal fun UwbRanger.buildSnapshotLocked(): UwbRanger.SessionSnapshot? {
    if (!trackingEnabled || !isSupported() || !hasPermission()) return null
    return when (sessionRole) {
        UwbRanger.SessionRole.CONTROLLER -> {
            val offer = controllerOffer ?: return null
            val scope = controllerScope ?: return null
            val peerAddress = parseAddress(peerAddressHex) ?: return null
            UwbRanger.SessionSnapshot.Controller(
                scope = scope,
                peerAddress = peerAddress,
                sessionId = offer.sessionId
            )
        }

        UwbRanger.SessionRole.CONTROLEE -> {
            val scope = controleeScope ?: return null
            val controllerAddress = parseAddress(remoteControllerAddressHex) ?: return null
            val controllerChannel = remoteControllerChannel ?: return null
            val controllerPreambleIndex = remoteControllerPreambleIndex ?: return null
            val sessionId = remoteSessionId ?: return null
            UwbRanger.SessionSnapshot.Controlee(
                scope = scope,
                controllerAddress = controllerAddress,
                controllerChannel = controllerChannel,
                controllerPreambleIndex = controllerPreambleIndex,
                sessionId = sessionId
            )
        }
    }
}

internal fun UwbRanger.cancelSessionLocked() {
    sessionToken += 1L
    sessionJob?.cancel()
    sessionJob = null
}
