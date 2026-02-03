package com.example.lifesaiver.core.call

import com.example.lifesaiver.core.audio.AudioDebugStats
import com.example.lifesaiver.core.wifi.TransportDebugStats

data class CallDebugState(
    val activeTransport: CallTransportType = CallTransportType.NONE,
    val isTransmitting: Boolean = false,
    val wifiAware: TransportDebugStats = TransportDebugStats(name = "Wi-Fi Aware"),
    val wifiDirect: TransportDebugStats = TransportDebugStats(name = "Wi-Fi Direct"),
    val audio: AudioDebugStats = AudioDebugStats(),
    val lastDecision: String? = null
)
