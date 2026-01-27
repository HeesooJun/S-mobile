package com.example.lifesaiver.protocol.core

object ProtocolConstants {
    const val MESSAGE_TTL_HOPS: Int = 7
    const val SYNC_TTL_HOPS: Int = 0
    object Mesh {
        const val ANNOUNCE_INTERVAL_MS: Long = 30_000L
        const val ANNOUNCE_INITIAL_DELAY_MS: Long = 200L
        const val PEER_TIMEOUT_MS: Long = 180_000L
        const val PEER_CLEANUP_INTERVAL_MS: Long = 60_000L
    }

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

    object FileTransfer {
        const val FRAGMENT_DELAY_MS: Long = 200L
    }

    object StoreForward {
        const val MESSAGE_CACHE_TIMEOUT_MS: Long = 43_200_000L
        const val MAX_CACHED_MESSAGES: Int = 100
        const val CLEANUP_INTERVAL_MS: Long = 600_000L
    }
}
