package com.example.lifesaivior.core.wifi

import android.net.wifi.aware.PeerHandle
import android.util.Log
import com.example.lifesaivior.core.log.ConnectionLog
import kotlinx.coroutines.flow.update

// =========================================================================
// 6. 종료 및 정리
// =========================================================================
internal fun WifiAwareRanger.stopInternal() {
    if (!isOnMainThread()) {
        runOnMain { stopInternal() }
        return
    }
    // 즉시 재시작 제한을 위해 종료 시각을 기록
    lastStopAtMs = System.currentTimeMillis()
    attachRequestToken += 1
    isRanging = false
    rttRequestInFlight = false
    clearRttWatchdog()
    rttFailureStreak = 0
    rttStatusFailStreak = 0
    rttWarmupUntilMs = 0L
    attachFailureStreak = 0
    attachBlockedUntilMs = 0L
    lastAttachAttemptAtMs = 0L
    isPeerWifiSupported = false // 상태 초기화
    cancelAttachRetry()
    pendingRestartRunnable?.let { handler.removeCallbacks(it) }
    pendingRestartRunnable = null
    pendingConnectRunnable?.let { handler.removeCallbacks(it) }
    pendingConnectRunnable = null
    receiveJob?.cancel(true)
    closeSocket()
    try {
        subscribeSession?.close()
    } catch (_: Exception) {
    }
    subscribeSession = null
    try {
        publishSession?.close()
    } catch (_: Exception) {
    }
    publishSession = null
    try {
        session?.close()
    } catch (_: Exception) {
    }
    session = null
    _isConnectionReady.value = false
    _rttDistance.value = null
    _debugStats.update {
        it.copy(
            isReady = false,
            lastSendError = null,
            lastRecvError = null
        )
    }
    peerAddress = null
    peerPort = null
    setCurrentTargetPeer(null)
    peerRequestedNdp = false
    ndpAckReceived = false
    foundPeers.clear()
    isStarting = false
    ndpUnavailableCount = 0
    Log.d("WifiAware", "Stopped.")
    ConnectionLog.add("Aware", "stopped")
}

/**
 * Call attempt용 소프트 리셋.
 * 세션(Publish/Subscribe)은 유지하고, 데이터 경로/RTT/연결 상태만 초기화합니다.
 * stop()처럼 attach cooldown을 유발하지 않도록 합니다.
 */
internal fun WifiAwareRanger.resetForCallAttemptInternal(reason: String? = null) {
    if (!isOnMainThread()) {
        runOnMain { resetForCallAttemptInternal(reason) }
        return
    }
    // NDP 연결이 진행 중이면 소프트 리셋이 연결을 깨므로 건너뜀
    if (_isConnectionReady.value || isConnecting) {
        ConnectionLog.add(
            "Aware",
            "soft reset skipped: ndp in-flight"
        )
        return
    }
    val reasonSuffix = reason?.let { " ($it)" }.orEmpty()
    ConnectionLog.add("Aware", "soft reset$reasonSuffix")
    isRanging = false
    rttRequestInFlight = false
    clearRttWatchdog()
    rttFailureStreak = 0
    rttStatusFailStreak = 0
    rttWarmupUntilMs = 0L
    isConnecting = false
    pendingConnectRunnable?.let { handler.removeCallbacks(it) }
    pendingConnectRunnable = null
    closeSocket()
    _isConnectionReady.value = false
    _rttDistance.value = null
    _debugStats.update { it.copy(isReady = false) }
    peerAddress = null
    peerPort = null
    setCurrentTargetPeer(null)
    peerRequestedNdp = false
    ndpAckReceived = false
    ndpUnavailableCount = 0
}

internal fun WifiAwareRanger.scheduleAttachRetry(reason: String, delayMsOverride: Long? = null) {
    if (!isOnMainThread()) {
        runOnMain { scheduleAttachRetry(reason, delayMsOverride) }
        return
    }
    // 현재 세션이 살아있으면 재시도 불필요
    if (!isPeerWifiSupported || _isConnectionReady.value || session != null || isStarting) return
    attachRetryRunnable?.let { handler.removeCallbacks(it) }
    val now = System.currentTimeMillis()
    val blockedDelay = (attachBlockedUntilMs - now).takeIf { it > 0L }
    val exponentialDelay = (attachRetryBaseMs * (1L shl attachFailureStreak.coerceIn(0, 4)))
        .coerceAtMost(attachRetryMaxMs)
    val delayMs = (delayMsOverride ?: blockedDelay ?: exponentialDelay).coerceAtLeast(500L)
    val runnable = Runnable {
        if (!isPeerWifiSupported || _isConnectionReady.value || session != null || isStarting) return@Runnable
        ConnectionLog.add("Aware", "retry attach (${reason}) delay=${delayMs}ms")
        startIfReady()
    }
    attachRetryRunnable = runnable
    handler.postDelayed(runnable, delayMs)
}

internal fun WifiAwareRanger.cancelAttachRetry() {
    if (!isOnMainThread()) {
        runOnMain { cancelAttachRetry() }
        return
    }
    attachRetryRunnable?.let { handler.removeCallbacks(it) }
    attachRetryRunnable = null
}

internal fun WifiAwareRanger.scheduleFallbackConnect(reason: String) {
    if (!isOnMainThread()) {
        runOnMain { scheduleFallbackConnect(reason) }
        return
    }
    // 데이터 경로가 없고 타겟이 유효할 때만 fallback 시도
    if (_isConnectionReady.value || isConnecting || currentTargetPeer == null) return
    if (!isNdpInitiator && !peerRequestedNdp) {
        ConnectionLog.add("Aware", "fallback skipped: waiting initiator request")
        return
    }
    pendingConnectRunnable?.let { handler.removeCallbacks(it) }
    val runnable = Runnable {
        if (_isConnectionReady.value || isConnecting || currentTargetPeer == null) return@Runnable
        if (!isNdpInitiator && !peerRequestedNdp) return@Runnable
        Log.w("WifiAware", "Fallback connect triggered ($reason)")
        ConnectionLog.add("Aware", "fallback connect ($reason)")
        connectToCurrentPeer()
    }
    pendingConnectRunnable = runnable
    val delay = (1000L * ndpUnavailableCount.coerceIn(1, 5))
    handler.postDelayed(runnable, delay)
}

internal fun WifiAwareRanger.scheduleAwareSessionRestart(reason: String) {
    if (!isOnMainThread()) {
        runOnMain { scheduleAwareSessionRestart(reason) }
        return
    }
    // 이미 준비되었거나 시작 중이면 재시작 지연
    if (_isConnectionReady.value || isStarting) return
    pendingRestartRunnable?.let { handler.removeCallbacks(it) }
    val runnable = Runnable {
        if (_isConnectionReady.value || isStarting) return@Runnable
        ConnectionLog.add("Aware", "restart session ($reason)")
        try {
            subscribeSession?.close()
        } catch (_: Exception) {
        }
        subscribeSession = null
        try {
            publishSession?.close()
        } catch (_: Exception) {
        }
        publishSession = null
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        closeSocket()
        _isConnectionReady.value = false
        _debugStats.update { it.copy(isReady = false) }
        isConnecting = false
        isStarting = false
        isRanging = false
        rttRequestInFlight = false
        clearRttWatchdog()
        ndpUnavailableCount = 0
        startIfReady()
    }
    pendingRestartRunnable = runnable
    handler.postDelayed(runnable, 2_500L)
}

internal fun WifiAwareRanger.closeSocket() {
    // 소켓 정리 및 NetworkCallback 해제
    try {
        socket?.close()
    } catch (_: Exception) {
    }
    socket = null
    if (networkCallback != null) {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback!!)
        } catch (_: Exception) {
        }
        networkCallback = null
    }
    isConnecting = false
    peerAddress = null
    peerPort = null
}

internal fun WifiAwareRanger.setCurrentTargetPeer(peer: PeerHandle?) {
    if (currentTargetPeer != peer) {
        rttStatusFailStreak = 0
    }
    currentTargetPeer = peer
}
