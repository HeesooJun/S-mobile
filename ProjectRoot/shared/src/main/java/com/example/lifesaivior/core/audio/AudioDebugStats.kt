package com.example.lifesaivior.core.audio

data class AudioDebugStats(
    val isStreaming: Boolean = false,
    val useOpus: Boolean = false,
    val speakerphoneEnabled: Boolean = true,
    val encodedFrames: Long = 0,
    val decodedFrames: Long = 0,
    val encodeFailCount: Long = 0,
    val decodeFailCount: Long = 0,
    val playFailCount: Long = 0,
    val lastEncodeAt: Long? = null,
    val lastDecodeAt: Long? = null,
    val lastEncodeSize: Int? = null,
    val lastDecodeSize: Int? = null,
    val lastEncodeError: String? = null,
    val lastDecodeError: String? = null,
    val lastPlayError: String? = null,
    val lastStartError: String? = null
)
