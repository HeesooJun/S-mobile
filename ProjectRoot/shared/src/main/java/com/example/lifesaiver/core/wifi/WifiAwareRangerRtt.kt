package com.example.lifesaiver.core.wifi

import android.annotation.SuppressLint
import android.net.wifi.rtt.RangingRequest
import android.net.wifi.rtt.RangingResult
import android.net.wifi.rtt.RangingResultCallback
import android.util.Log
import com.example.lifesaiver.core.log.ConnectionLog

// =========================================================================
// 3. RTT 거리 측정 루프
// =========================================================================
@SuppressLint("MissingPermission")
internal fun WifiAwareRanger.startRangingLoop() {
    if (!isOnMainThread()) {
        runOnMain { startRangingLoop() }
        return
    }
    if (!rttEnabled || !isRanging || currentTargetPeer == null || wifiRttManager == null) {
        Log.d(
            "WifiAware",
            "RTT loop skipped enabled=$rttEnabled ranging=$isRanging target=${currentTargetPeer != null} manager=${wifiRttManager != null}"
        )
        return
    }
    if (rttRequestInFlight) return
    if (isCurrentPeerRttBlocked()) {
        isRanging = false
        _rttDistance.value = null
        ConnectionLog.add("Aware", "RTT skipped: peer blocked by cache")
        return
    }
    val now = System.currentTimeMillis()
    // NDP 직후에는 RTT가 흔들릴 수 있어 워밍업 대기
    if (_isConnectionReady.value && now < rttWarmupUntilMs) {
        val waitMs = (rttWarmupUntilMs - now).coerceAtMost(2_000L)
        handler.postDelayed({ startRangingLoop() }, waitMs)
        return
    }
    val targetPeer = currentTargetPeer ?: run {
        Log.d("WifiAware", "RTT loop skipped: target became null")
        return
    }
    val request = runCatching {
        RangingRequest.Builder()
            .addWifiAwarePeer(targetPeer)
            .build()
    }.getOrElse { error ->
        Log.e("WifiAware", "RTT request build failed", error)
        ConnectionLog.add("Aware", "RTT request build failed")
        scheduleNextRanging()
        return
    }

    ConnectionLog.add("Aware", "ranging request")
    Log.d("WifiAware", "RTT ranging request target=$targetPeer")
    rttRequestInFlight = true
    clearRttWatchdog()
    // 콜백 미수신 시 재시도하도록 워치독 타이머 운용
    val watchdog = Runnable {
        if (!isRanging || !rttRequestInFlight) return@Runnable
        rttRequestInFlight = false
        rttFailureStreak = (rttFailureStreak + 1).coerceAtMost(10)
        Log.w("WifiAware", "RTT callback timeout -> retry")
        ConnectionLog.add("Aware", "RTT callback timeout -> retry")
        scheduleNextRanging()
    }
    rttWatchdogRunnable = watchdog
    handler.postDelayed(watchdog, rttCallbackTimeoutMs)
    try {
        wifiRttManager?.startRanging(request, rttExecutor, rttCallback)
    } catch (e: Exception) {
        clearRttWatchdog()
        rttRequestInFlight = false
        rttFailureStreak = (rttFailureStreak + 1).coerceAtMost(10)
        Log.e("WifiAware", "RTT startRanging failed", e)
        ConnectionLog.add("Aware", "RTT start failed: ${e.javaClass.simpleName}")
        scheduleNextRanging()
    }
}

internal fun WifiAwareRanger.handleRangingResultsOnMain(results: List<RangingResult>) {
    clearRttWatchdog()
    rttRequestInFlight = false
    val best = results.firstOrNull { it.status == RangingResult.STATUS_SUCCESS }
    if (best != null) {
        rttFailureStreak = 0
        rttStatusFailStreak = 0
        val dist = best.distanceMm / 1000f
        Log.d("WifiAware", "RTT distance=${dist}m")
        ConnectionLog.add("Aware", "RTT distance=${dist}m")
        _rttDistance.value = dist
        if (currentTargetPeer == null) {
            if (!requiresAckBeforeConnect()) {
                setCurrentTargetPeer(best.peerHandle)
            }
        } else if (currentTargetPeer != best.peerHandle) {
            if (isNdpInitiator) {
                ConnectionLog.add("Aware", "ignore RTT from non-target peer")
            }
            if (isRanging) handler.postDelayed({ startRangingLoop() }, 1000)
            return
        }

        // [연결 트리거 로직]
        // 30m 이내이고, 아직 연결 안됐고, 연결 시도 중이 아니면 -> 연결
        if (isNdpInitiator &&
            shouldUseDataPathForCall() &&
            dist < connectDistanceMeters &&
            !_isConnectionReady.value &&
            !isConnecting
        ) {
            Log.i("WifiAware", "Peer close ($dist m). Requesting NDP connection...")
            ConnectionLog.add("Aware", "distance<$connectDistanceMeters -> connect")
            currentTargetPeer?.let { sendNdpRequestToPeer(it) }
            if (dataPathEnabled) {
                connectToCurrentPeer()
            } else {
                ConnectionLog.add("Aware", "data path disabled -> skip connect")
            }
        }
    } else {
        rttFailureStreak = (rttFailureStreak + 1).coerceAtMost(10)
        val statusLabels = results.joinToString(",") { rangingStatusLabel(it.status) }
        Log.w(
            "WifiAware",
            "RTT no success results=${results.size} statuses=${statusLabels.ifBlank { "none" }}"
        )
        if (statusLabels.isNotBlank()) {
            ConnectionLog.add("Aware", "RTT no success statuses=$statusLabels")
        }
    val hasUnsupportedStatus = results.any { isRttPeerUnsupportedStatus(it.status) }
    val onlyGenericFail = results.isNotEmpty() && results.all { it.status == RangingResult.STATUS_FAIL }
    if (hasUnsupportedStatus) {
        markCurrentPeerRttUnsupported("result=${statusLabels.ifBlank { "unsupported" }}")
    } else if (onlyGenericFail) {
        rttStatusFailStreak += 1
            val failThreshold = if (_isConnectionReady.value) {
                rttStatusFailDisableThreshold
            } else {
                4
            }
            ConnectionLog.add(
                "Aware",
                "RTT generic fail streak=$rttStatusFailStreak/$failThreshold"
            )
            if (rttStatusFailStreak >= failThreshold) {
                val reason = if (_isConnectionReady.value) {
                    "repeated STATUS_FAIL"
                } else {
                    "repeated STATUS_FAIL(no-call)"
                }
                markCurrentPeerRttUnsupported(reason)
            }
        } else if (results.isNotEmpty()) {
            rttStatusFailStreak = 0
        }
    }
    scheduleNextRanging()
}

internal fun WifiAwareRanger.handleRangingFailureOnMain(code: Int) {
    clearRttWatchdog()
    rttRequestInFlight = false
    val codeLabel = rangingFailureCodeLabel(code)
    Log.w("WifiAware", "RTT failure code=$codeLabel")
    ConnectionLog.add("Aware", "RTT failure code=$codeLabel")
    val bump = if (code == RangingResultCallback.STATUS_CODE_FAIL) 2 else 1
    rttFailureStreak = (rttFailureStreak + bump).coerceAtMost(10)
    if (_isConnectionReady.value && code == RangingResultCallback.STATUS_CODE_FAIL) {
        rttStatusFailStreak += 1
        ConnectionLog.add(
            "Aware",
            "RTT callback fail streak=$rttStatusFailStreak/${rttStatusFailDisableThreshold}"
        )
        if (rttStatusFailStreak >= rttStatusFailDisableThreshold) {
            markCurrentPeerRttUnsupported("callback STATUS_CODE_FAIL")
        }
    } else if (code != RangingResultCallback.STATUS_CODE_FAIL) {
        rttStatusFailStreak = 0
    }
    scheduleNextRanging()
}

internal fun WifiAwareRanger.scheduleNextRanging() {
    if (!isOnMainThread()) {
        runOnMain { scheduleNextRanging() }
        return
    }
    if (!isRanging) return
    val base = if (_isConnectionReady.value) {
        rangingIntervalNdpActiveMs
    } else {
        rangingIntervalBaseMs
    }
    val extra = when {
        rttFailureStreak <= 0 -> 0L
        rttFailureStreak == 1 -> 400L
        rttFailureStreak == 2 -> 900L
        else -> ((rttFailureStreak - 2) * 700L + 900L)
    }
    // 실패가 누적될수록 backoff 증가
    val delayMs = (base + extra).coerceAtMost(rangingIntervalMaxMs)
    handler.postDelayed({ startRangingLoop() }, delayMs)
}

internal fun WifiAwareRanger.clearRttWatchdog() {
    rttWatchdogRunnable?.let { handler.removeCallbacks(it) }
    rttWatchdogRunnable = null
}

internal fun WifiAwareRanger.isCurrentPeerRttBlocked(now: Long = System.currentTimeMillis()): Boolean {
    val peerKey = resolveRttPeerCacheKey() ?: return false
    cleanupExpiredRttPeerBlocks(now)
    val blockedUntil = peerRttBlockedUntilMs[peerKey] ?: return false
    return blockedUntil > now
}

internal fun WifiAwareRanger.cleanupExpiredRttPeerBlocks(now: Long) {
    if (peerRttBlockedUntilMs.isEmpty()) return
    val iterator = peerRttBlockedUntilMs.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        if (entry.value <= now) {
            iterator.remove()
        }
    }
}

internal fun WifiAwareRanger.markCurrentPeerRttUnsupported(reason: String) {
    val peerKey = resolveRttPeerCacheKey() ?: return
    val now = System.currentTimeMillis()
    cleanupExpiredRttPeerBlocks(now)
    val alreadyBlocked = peerRttBlockedUntilMs[peerKey]?.let { it > now } == true
    // 일정 시간 동안 RTT 시도 차단
    peerRttBlockedUntilMs[peerKey] = now + rttUnsupportedCacheMs
    isRanging = false
    rttRequestInFlight = false
    clearRttWatchdog()
    _rttDistance.value = null
    rttStatusFailStreak = 0
    if (!alreadyBlocked) {
        val message = "RTT disabled for peer=$peerKey reason=$reason"
        Log.w("WifiAware", message)
        ConnectionLog.add("Aware", message)
    }
}

internal fun WifiAwareRanger.resolveRttPeerCacheKey(): String? {
    val expectedPeer = expectedPeerIdForCall
    if (!expectedPeer.isNullOrBlank()) return expectedPeer
    val currentPeer = currentTargetPeer ?: return null
    return "aware_peer_${currentPeer.hashCode()}"
}

internal fun WifiAwareRanger.isRttPeerUnsupportedStatus(status: Int): Boolean {
    return status == RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC || status == 3
}

internal fun WifiAwareRanger.rangingStatusLabel(status: Int): String {
    return when (status) {
        RangingResult.STATUS_SUCCESS -> "SUCCESS(0)"
        RangingResult.STATUS_FAIL -> "FAIL(1)"
        RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC -> "RESPONDER_NO_11MC(2)"
        3 -> "RESPONDER_NON_COMPLIANT(3)"
        else -> "UNKNOWN($status)"
    }
}

internal fun WifiAwareRanger.rangingFailureCodeLabel(code: Int): String {
    return when (code) {
        RangingResultCallback.STATUS_CODE_FAIL -> "FAIL(1)"
        RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE -> "RTT_NOT_AVAILABLE(2)"
        else -> "UNKNOWN($code)"
    }
}
