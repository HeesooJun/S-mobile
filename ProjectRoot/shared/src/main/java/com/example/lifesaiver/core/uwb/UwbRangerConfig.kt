package com.example.lifesaiver.core.uwb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal fun UwbRanger.prepareControllerOfferBlockingInternal(): UwbRanger.ControllerOffer? {
    if (!isSupported() || !hasPermission()) return null
    if (!isRuntimeAvailableBlocking()) return null
    synchronized(lock) {
        val cachedOffer = controllerOffer
        if (cachedOffer != null && controllerScope != null) {
            return cachedOffer
        }
    }
    val offer = synchronized(scopeCreationLock) {
        synchronized(lock) {
            val cachedOffer = controllerOffer
            if (cachedOffer != null && controllerScope != null) {
                return@synchronized cachedOffer
            }
        }
        val scope = runBlocking(Dispatchers.IO) {
            runCatching {
                uwbManager.controllerSessionScope()
            }.onFailure { throwable ->
                logUwb("controller scope create failed", throwable)
            }.getOrNull()
        } ?: return@synchronized null
        val preparedOffer = UwbRanger.ControllerOffer(
            controllerAddress = normalizeAddress(scope.localAddress.toString()) ?: return@synchronized null,
            channel = scope.uwbComplexChannel.channel,
            preambleIndex = scope.uwbComplexChannel.preambleIndex,
            sessionId = buildSessionId()
        )
        synchronized(lock) {
            val cachedOffer = controllerOffer
            if (cachedOffer != null && controllerScope != null) {
                return@synchronized cachedOffer
            }
            controllerScope = scope
            controllerOffer = preparedOffer
            sessionRole = UwbRanger.SessionRole.CONTROLLER
            peerAddressHex = null
            remoteControllerAddressHex = null
            remoteControllerChannel = null
            remoteControllerPreambleIndex = null
            remoteSessionId = null
        }
        preparedOffer
    } ?: return null
    logUwb(
        "controller offer ready addr=${offer.controllerAddress} ch=${offer.channel} " +
            "preamble=${offer.preambleIndex} sessionId=${offer.sessionId}"
    )
    restartSessionIfTracking()
    return offer
}

internal fun UwbRanger.prepareControleeAddressBlockingInternal(): String? {
    if (!isSupported() || !hasPermission()) return null
    if (!isRuntimeAvailableBlocking()) return null
    synchronized(lock) {
        val cachedAddress = controleeAddressHex
        if (cachedAddress != null && controleeScope != null) {
            return cachedAddress
        }
    }
    return synchronized(scopeCreationLock) {
        synchronized(lock) {
            val cachedAddress = controleeAddressHex
            if (cachedAddress != null && controleeScope != null) {
                return@synchronized cachedAddress
            }
        }
        val scope = synchronized(lock) { controleeScope } ?: runBlocking(Dispatchers.IO) {
            runCatching {
                uwbManager.controleeSessionScope()
            }.onFailure { throwable ->
                logUwb("controlee scope create failed", throwable)
            }.getOrNull()
        } ?: return@synchronized null
        val address = normalizeAddress(scope.localAddress.toString()) ?: return@synchronized null
        synchronized(lock) {
            if (controleeScope == null) {
                controleeScope = scope
            }
            controleeAddressHex = address
        }
        logUwb("controlee address ready addr=$address")
        address
    }
}

internal fun UwbRanger.configureControllerSessionInternal(peerAddress: String?) {
    val normalizedPeerAddress = normalizeAddress(peerAddress)
    var shouldRestart = false
    synchronized(lock) {
        val roleChanged = sessionRole != UwbRanger.SessionRole.CONTROLLER
        val currentPeer = peerAddressHex
        val peerChanged = currentPeer != normalizedPeerAddress

        // UWB sync 재전송 과정에서 null peer 업데이트가 반복되면 세션이 재시작되며 scope가 소진될 수 있다.
        // 명시적 종료는 endSession()/stop()에서 처리하고, 여기서는 유효한 peer가 있을 때만 갱신한다.
        if (!roleChanged && normalizedPeerAddress == null && currentPeer != null) {
            return
        }
        if (!roleChanged && !peerChanged) {
            return
        }

        sessionRole = UwbRanger.SessionRole.CONTROLLER
        peerAddressHex = normalizedPeerAddress
        shouldRestart = trackingEnabled && normalizedPeerAddress != null
    }
    if (shouldRestart) {
        restartSessionIfTracking()
    }
    logUwb("configure controller peer=$normalizedPeerAddress restart=$shouldRestart")
}

internal fun UwbRanger.configureControleeSessionInternal(
    controllerAddress: String?,
    channel: Int?,
    preambleIndex: Int?,
    sessionId: Int?
) {
    val normalizedControllerAddress = normalizeAddress(controllerAddress)
    var shouldRestart = false
    synchronized(lock) {
        val roleChanged = sessionRole != UwbRanger.SessionRole.CONTROLEE
        val addressChanged = remoteControllerAddressHex != normalizedControllerAddress
        val channelChanged = remoteControllerChannel != channel
        val preambleChanged = remoteControllerPreambleIndex != preambleIndex
        val sessionChanged = remoteSessionId != sessionId
        if (!roleChanged && !addressChanged && !channelChanged && !preambleChanged && !sessionChanged) {
            return
        }
        sessionRole = UwbRanger.SessionRole.CONTROLEE
        remoteControllerAddressHex = normalizedControllerAddress
        remoteControllerChannel = channel
        remoteControllerPreambleIndex = preambleIndex
        remoteSessionId = sessionId
        shouldRestart = trackingEnabled &&
            normalizedControllerAddress != null &&
            channel != null &&
            preambleIndex != null &&
            sessionId != null
    }
    if (shouldRestart) {
        restartSessionIfTracking()
    }
    logUwb(
        "configure controlee addr=$normalizedControllerAddress ch=$channel " +
            "preamble=$preambleIndex sessionId=$sessionId restart=$shouldRestart"
    )
}
