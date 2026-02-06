package com.example.lifesaivior.protocol.profile

import com.example.lifesaivior.protocol.model.PacketType

enum class ProfileSyncLogResult {
    RECEIVED,
    TLV_PARSE_FAILED,
    MISSING_FIELD,
    UPSERT_OK,
    UPSERT_SKIPPED,
    UPSERT_FAILED
}

data class ProfileSyncLogEntry(
    val timestamp: Long,
    val peerId: String,
    val packetType: PacketType,
    val result: ProfileSyncLogResult,
    val detail: String
)
