package com.example.lifesaiver.core.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConnectionLog {
    private const val MAX_LINES = 200
    private const val TIME_PATTERN = "HH:mm:ss.SSS"
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs = _logs.asStateFlow()

    fun add(tag: String, message: String) {
        val timestamp = SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date())
        val line = "$timestamp [$tag] $message"
        val updated = (_logs.value + line).takeLast(MAX_LINES)
        _logs.value = updated
    }
}
