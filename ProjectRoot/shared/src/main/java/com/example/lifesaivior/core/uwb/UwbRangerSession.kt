package com.example.lifesaivior.core.uwb

import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbDevice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal fun UwbRanger.startInternal() {
    val supported = isSupported()
    val permission = hasPermission()
    val available = isRuntimeAvailableCachedInternal()
    logUwb("start request supported=$supported permission=$permission available=$available role=$sessionRole")
    if (!supported || !permission) {
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
    logUwb("stop request")
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
    logUwb("end session")
}

internal fun UwbRanger.releaseInternal() {
    endSessionInternal()
    clearAvailabilityCallback()
    rangerScope.cancel()
    logUwb("release")
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
    logUwb("session start role=${snapshot?.role}")
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
    if (!trackingEnabled) {
        logSnapshotSkip("tracking disabled")
        return null
    }
    if (!isSupported()) {
        logSnapshotSkip("not supported")
        return null
    }
    if (!hasPermission()) {
        logSnapshotSkip("no permission")
        return null
    }
    return when (sessionRole) {
        UwbRanger.SessionRole.CONTROLLER -> {
            val offer = controllerOffer ?: run {
                logSnapshotSkip("no controller offer")
                return null
            }
            val scope = controllerScope ?: run {
                logSnapshotSkip("no controller scope")
                return null
            }
            val peerAddress = parseAddress(peerAddressHex) ?: run {
                logSnapshotSkip("no peer address")
                return null
            }
            UwbRanger.SessionSnapshot.Controller(
                scope = scope,
                peerAddress = peerAddress,
                sessionId = offer.sessionId
            )
        }

        UwbRanger.SessionRole.CONTROLEE -> {
            val scope = controleeScope ?: run {
                logSnapshotSkip("no controlee scope")
                return null
            }
            val controllerAddress = parseAddress(remoteControllerAddressHex) ?: run {
                logSnapshotSkip("no controller address")
                return null
            }
            val controllerChannel = remoteControllerChannel ?: run {
                logSnapshotSkip("no controller channel")
                return null
            }
            val controllerPreambleIndex = remoteControllerPreambleIndex ?: run {
                logSnapshotSkip("no controller preamble")
                return null
            }
            val sessionId = remoteSessionId ?: run {
                logSnapshotSkip("no controller sessionId")
                return null
            }
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
