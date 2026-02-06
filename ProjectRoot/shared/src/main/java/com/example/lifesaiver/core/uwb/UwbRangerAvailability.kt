package com.example.lifesaiver.core.uwb

import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.uwb.UwbAvailabilityCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

internal fun UwbRanger.isRuntimeAvailableCachedInternal(): Boolean? {
    if (!isSupported() || !hasPermission()) return false
    ensureAvailabilityCallbackRegistered()
    return runtimeAvailableCache
}

internal fun UwbRanger.ensureAvailabilityCallbackRegistered() {
    if (!isSupported()) return
    synchronized(lock) {
        if (availabilityCallbackRegistered) return
        runCatching {
            uwbManager.setUwbAvailabilityCallback(
                ContextCompat.getMainExecutor(context),
                availabilityCallback
            )
            availabilityCallbackRegistered = true
        }.onFailure { throwable ->
            logUwb("availability callback register failed", throwable)
        }
    }
}

internal fun UwbRanger.clearAvailabilityCallback() {
    synchronized(lock) {
        if (!availabilityCallbackRegistered) return
        runCatching {
            uwbManager.clearUwbAvailabilityCallback()
        }.onFailure { throwable ->
            logUwb("availability callback clear failed", throwable)
        }
        availabilityCallbackRegistered = false
    }
}

internal fun UwbRanger.isRuntimeAvailableBlocking(forceRefresh: Boolean = false): Boolean {
    if (!isSupported() || !hasPermission()) return false
    ensureAvailabilityCallbackRegistered()
    val now = SystemClock.elapsedRealtime()
    val cached = runtimeAvailableCache
    if (!forceRefresh && cached != null && now - lastAvailabilityCheckAtMs < UwbRanger.AVAILABILITY_CACHE_TTL_MS) {
        return cached
    }
    val available = runBlocking(Dispatchers.IO) {
        runCatching { uwbManager.isAvailable() }
            .onFailure { throwable ->
                logUwb("availability query failed", throwable)
            }
            .getOrDefault(false)
    }
    runtimeAvailableCache = available
    lastAvailabilityCheckAtMs = now
    if (!available && now - lastUnavailableLogAtMs >= UwbRanger.UNAVAILABLE_LOG_THROTTLE_MS) {
        lastUnavailableLogAtMs = now
        logUwb("runtime unavailable")
    }
    return available
}

internal fun UwbRanger.availabilityReasonToText(reason: Int): String {
    return when (reason) {
        UwbAvailabilityCallback.STATE_CHANGE_REASON_SYSTEM_POLICY -> "system_policy"
        UwbAvailabilityCallback.STATE_CHANGE_REASON_COUNTRY_CODE_ERROR -> "country_code_error"
        UwbAvailabilityCallback.STATE_CHANGE_REASON_UNKNOWN -> "unknown"
        else -> "code_$reason"
    }
}
