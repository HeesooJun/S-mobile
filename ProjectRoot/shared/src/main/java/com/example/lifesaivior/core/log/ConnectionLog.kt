package com.example.lifesaivior.core.log

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.Date
import java.util.Locale

object ConnectionLog {
    private const val MAX_LINES = 200
    private const val TIME_PATTERN = "HH:mm:ss.SSS"
    private const val LOG_TAG = "ConnLog"
    private const val LOGCAT_THROTTLE_MS = 1000L
    private val lastLogcatAtByTag = ConcurrentHashMap<String, Long>()
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    fun add(tag: String, message: String) {
        val now = System.currentTimeMillis()
        val timestamp = SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(now))
        val line = "$timestamp [$tag] $message"
        val updated = (_logs.value + line).takeLast(MAX_LINES)
        _logs.value = updated
        if (shouldLogToLogcat(tag, message, now)) {
            Log.d(LOG_TAG, "[$tag] $message")
        }
    }

    private fun shouldLogToLogcat(tag: String, message: String, now: Long): Boolean {
        if (shouldBypassThrottle(message)) return true
        val last = lastLogcatAtByTag[tag] ?: 0L
        if (now - last < LOGCAT_THROTTLE_MS) return false
        lastLogcatAtByTag[tag] = now
        return true
    }

    private fun shouldBypassThrottle(message: String): Boolean {
        val lowered = message.lowercase(Locale.US)
        return lowered.contains("error") ||
            lowered.contains("fail") ||
            lowered.contains("timeout") ||
            lowered.contains("unavailable") ||
            lowered.contains("disabled") ||
            lowered.contains("exception")
    }
}
