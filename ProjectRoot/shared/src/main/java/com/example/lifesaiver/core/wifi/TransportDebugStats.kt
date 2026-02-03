package com.example.lifesaiver.core.wifi

data class TransportDebugStats(
    val name: String,
    val isReady: Boolean = false,
    val sendCount: Long = 0,
    val sendFailCount: Long = 0,
    val lastSendAt: Long? = null,
    val lastSendSize: Int? = null,
    val lastSendError: String? = null,
    val recvCount: Long = 0,
    val recvFailCount: Long = 0,
    val lastRecvAt: Long? = null,
    val lastRecvSize: Int? = null,
    val lastRecvError: String? = null,
    val lastPeerAddress: String? = null
)
