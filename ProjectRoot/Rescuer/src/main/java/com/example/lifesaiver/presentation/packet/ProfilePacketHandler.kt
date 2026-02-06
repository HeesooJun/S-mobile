package com.example.lifesaiver.presentation.packet

import com.example.lifesaiver.core.database.dao.ProfileDao
import com.example.lifesaiver.core.database.entity.ProfileEntity
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketType
import com.example.lifesaiver.protocol.profile.ProfileSyncLogEntry
import com.example.lifesaiver.protocol.profile.ProfileSyncLogResult
import com.example.lifesaiver.protocol.profile.ProfileTlv
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ProfilePacketHandler(
    private val profileDao: ProfileDao,
    private val scope: CoroutineScope,
    private val logSink: (ProfileSyncLogEntry) -> Unit,
    private val onPublicPacketSeen: (Packet) -> Unit
) {
    fun handle(packet: Packet, result: ProfileTlv.DecodeResult, pathLabel: String) {
        val peerHex = bytesToHex(packet.header.senderId)
        val payloadSize = packet.payload.size
        when (result) {
            is ProfileTlv.DecodeResult.Failure -> {
                logSink(
                    ProfileSyncLogEntry(
                        timestamp = System.currentTimeMillis(),
                        peerId = peerHex,
                        packetType = packet.header.type,
                        result = ProfileSyncLogResult.TLV_PARSE_FAILED,
                        detail = "len=$payloadSize path=$pathLabel reason=${result.reason}"
                    )
                )
            }
            is ProfileTlv.DecodeResult.Success -> {
                val decoded = result.decoded
                val updatedAt = decoded.updatedAt
                val kind = decoded.kind
                val schemaVersion = decoded.schemaVersion
                val isRescuer = (packet.header.flags and ProtocolConstants.Capabilities.RESCUER) != 0
                if (kind == null || updatedAt == null) {
                    logSink(
                        ProfileSyncLogEntry(
                            timestamp = System.currentTimeMillis(),
                            peerId = peerHex,
                            packetType = packet.header.type,
                            result = ProfileSyncLogResult.MISSING_FIELD,
                            detail = "len=$payloadSize path=$pathLabel missing=${missingFields(kind, updatedAt)}"
                        )
                    )
                    return
                }
                if (isRescuer) {
                    logSink(
                        ProfileSyncLogEntry(
                            timestamp = System.currentTimeMillis(),
                            peerId = peerHex,
                            packetType = packet.header.type,
                            result = ProfileSyncLogResult.UPSERT_SKIPPED,
                            detail = "path=$pathLabel rescuer profile"
                        )
                    )
                    scope.launch(Dispatchers.IO) {
                        runCatching { profileDao.deleteByPeerId(peerHex) }
                    }
                    return
                }
                if (schemaVersion != null && schemaVersion != 1) {
                    logSink(
                        ProfileSyncLogEntry(
                            timestamp = System.currentTimeMillis(),
                            peerId = peerHex,
                            packetType = packet.header.type,
                            result = ProfileSyncLogResult.UPSERT_SKIPPED,
                            detail = "len=$payloadSize path=$pathLabel unsupported schema=$schemaVersion"
                        )
                    )
                    return
                }
                logSink(
                    ProfileSyncLogEntry(
                        timestamp = System.currentTimeMillis(),
                        peerId = peerHex,
                        packetType = packet.header.type,
                        result = ProfileSyncLogResult.RECEIVED,
                        detail = "len=$payloadSize path=$pathLabel kind=$kind updatedAt=$updatedAt"
                    )
                )
                if (kind == ProfileTlv.KIND_REQUEST) {
                    logSink(
                        ProfileSyncLogEntry(
                            timestamp = System.currentTimeMillis(),
                            peerId = peerHex,
                            packetType = packet.header.type,
                            result = ProfileSyncLogResult.UPSERT_SKIPPED,
                            detail = "path=$pathLabel request packet"
                        )
                    )
                    return
                }
                val entity = ProfileEntity(
                    peerId = peerHex,
                    name = decoded.name.orEmpty(),
                    gender = decoded.gender?.toString().orEmpty(),
                    birthDate = decoded.birthDate.orEmpty(),
                    notes = decoded.notes.orEmpty(),
                    updatedAt = updatedAt,
                    sourcePeerId = peerHex,
                    lastSeenAt = System.currentTimeMillis()
                )
                scope.launch(Dispatchers.IO) {
                    runCatching {
                        profileDao.upsertIfNewer(entity)
                    }.onSuccess { applied ->
                        logSink(
                            ProfileSyncLogEntry(
                                timestamp = System.currentTimeMillis(),
                                peerId = peerHex,
                                packetType = packet.header.type,
                                result = if (applied) {
                                    ProfileSyncLogResult.UPSERT_OK
                                } else {
                                    ProfileSyncLogResult.UPSERT_SKIPPED
                                },
                                detail = if (applied) {
                                    "path=$pathLabel updatedAt=$updatedAt"
                                } else {
                                    "path=$pathLabel stale updatedAt=$updatedAt"
                                }
                            )
                        )
                    }.onFailure { err ->
                        logSink(
                            ProfileSyncLogEntry(
                                timestamp = System.currentTimeMillis(),
                                peerId = peerHex,
                                packetType = packet.header.type,
                                result = ProfileSyncLogResult.UPSERT_FAILED,
                                detail = "path=$pathLabel db error: ${err.message}"
                            )
                        )
                    }
                }
                if (packet.header.recipientId == null) {
                    onPublicPacketSeen(packet)
                }
            }
        }
    }

    private fun missingFields(kind: Int?, updatedAt: Long?): String {
        val missing = mutableListOf<String>()
        if (kind == null) missing.add("kind")
        if (updatedAt == null) missing.add("updatedAt")
        return missing.joinToString(",")
    }

    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
