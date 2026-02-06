package com.example.lifesaiver.core.uwb

import android.os.SystemClock
import android.util.Log
import com.example.lifesaiver.core.log.ConnectionLog
import java.nio.ByteBuffer

private const val SNAPSHOT_LOG_THROTTLE_MS = 1000L

internal fun UwbRanger.logSnapshotSkip(reason: String) {
    val now = SystemClock.elapsedRealtime()
    val lastReason = lastSnapshotLogReason
    val lastAt = lastSnapshotLogAtMs
    if (lastReason == reason && now - lastAt < SNAPSHOT_LOG_THROTTLE_MS) return
    lastSnapshotLogReason = reason
    lastSnapshotLogAtMs = now
    logUwb("snapshot skip: $reason")
}

internal fun UwbRanger.parseAddress(rawAddress: String?): ByteArray? {
    val normalized = rawAddress?.trim()?.lowercase()?.replace('-', ':') ?: return null
    val parts = if (normalized.contains(':')) {
        normalized.split(':').filter { it.isNotBlank() }
    } else {
        if (normalized.length % 2 != 0) return null
        normalized.chunked(2)
    }
    if (parts.isEmpty()) return null
    if (parts.any { part ->
            (part.length != 1 && part.length != 2) ||
                part.any { ch -> ch !in '0'..'9' && ch !in 'a'..'f' }
        }
    ) {
        return null
    }
    return ByteArray(parts.size) { index ->
        parts[index].padStart(2, '0').toInt(16).toByte()
    }
}

internal fun UwbRanger.normalizeAddress(rawAddress: String?): String? {
    val bytes = parseAddress(rawAddress) ?: return null
    return bytes.joinToString(":") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
}

internal fun UwbRanger.buildSessionId(): Int {
    val value = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
    return if (value == 0) 1 else value
}

internal fun UwbRanger.buildStaticStsSessionKey(sessionId: Int): ByteArray {
    return ByteBuffer.allocate(UwbRanger.STATIC_STS_SESSION_KEY_LENGTH)
        .putInt(sessionId)
        .putInt(sessionId xor UwbRanger.STATIC_STS_KEY_SALT)
        .array()
}

internal fun UwbRanger.logUwb(message: String, throwable: Throwable? = null) {
    val formatted = if (throwable != null) {
        "$message: ${throwable.message ?: throwable::class.java.simpleName}"
    } else {
        message
    }
    ConnectionLog.add("UWB", formatted)
    if (throwable != null) {
        Log.w("UwbRanger", message, throwable)
    } else {
        Log.d("UwbRanger", message)
    }
}
