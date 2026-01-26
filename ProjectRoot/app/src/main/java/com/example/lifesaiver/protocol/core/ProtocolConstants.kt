package com.example.lifesaiver.protocol.core

object ProtocolConstants {
    const val MESSAGE_TTL_HOPS: Int = 7
    const val SYNC_TTL_HOPS: Int = 0

    object Fragmentation {
        const val FRAGMENT_SIZE_THRESHOLD: Int = 512
        const val MAX_FRAGMENT_SIZE: Int = 469
        const val FRAGMENT_TIMEOUT_MS: Long = 30_000L
        const val CLEANUP_INTERVAL_MS: Long = 10_000L
    }

    object Dedup {
        const val MESSAGE_TIMEOUT_MS: Long = 300_000L
        const val MAX_PROCESSED_MESSAGES: Int = 10_000
    }
}
