package com.example.lifesaiver.protocol.sync

import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
import com.example.lifesaiver.protocol.model.RequestSyncPayload
import com.example.lifesaiver.protocol.util.toHexString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class GossipSyncManager(
    private val myPeerId: ByteArray,
    private val scope: CoroutineScope,
    private val sender: Sender,
    private val configProvider: ConfigProvider = DefaultConfig
) {
    interface Sender {
        fun broadcast(packet: Packet)
        fun sendToPeer(peerId: ByteArray, packet: Packet)
    }

    interface ConfigProvider {
        fun seenCapacity(): Int
        fun gcsMaxBytes(): Int
        fun gcsTargetFpr(): Double
    }

    private object DefaultConfig : ConfigProvider {
        override fun seenCapacity(): Int = 256
        override fun gcsMaxBytes(): Int = SyncDefaults.DEFAULT_FILTER_BYTES
        override fun gcsTargetFpr(): Double = SyncDefaults.DEFAULT_FPR_PERCENT / 100.0
    }

    private val messages = LinkedHashMap<String, Packet>()
    private val latestAnnouncementByPeer = ConcurrentHashMap<String, Pair<String, Packet>>()
    private var periodicJob: Job? = null
    private var cleanupJob: Job? = null

    fun start() {
        periodicJob?.cancel()
        periodicJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(30_000L)
                sendRequestSync()
            }
        }

        cleanupJob?.cancel()
        cleanupJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(ProtocolConstants.Mesh.PEER_CLEANUP_INTERVAL_MS)
                pruneStaleAnnouncements()
            }
        }
    }

    fun stop() {
        periodicJob?.cancel()
        periodicJob = null
        cleanupJob?.cancel()
        cleanupJob = null
    }

    fun scheduleInitialSync(delayMs: Long = 5_000L) {
        scope.launch(Dispatchers.IO) {
            delay(delayMs)
            sendRequestSync()
        }
    }

    fun scheduleInitialSyncToPeer(peerId: ByteArray, delayMs: Long = 5_000L) {
        scope.launch(Dispatchers.IO) {
            delay(delayMs)
            sendRequestSyncToPeer(peerId)
        }
    }

    fun onPublicPacketSeen(packet: Packet) {
        val type = packet.header.type
        val isBroadcastMessage = type == PacketType.MESSAGE && packet.header.recipientId == null
        val isAnnouncement = type == PacketType.ANNOUNCE
        if (!isBroadcastMessage && !isAnnouncement) return

        val id = PacketIdUtil.computeIdHex(packet)
        if (isBroadcastMessage) {
            synchronized(messages) {
                messages[id] = packet
                val cap = configProvider.seenCapacity().coerceAtLeast(1)
                while (messages.size > cap) {
                    val it = messages.entries.iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    } else {
                        break
                    }
                }
            }
            return
        }

        val age = System.currentTimeMillis() - packet.header.timestamp
        if (age > ProtocolConstants.Mesh.PEER_TIMEOUT_MS) return
        val senderHex = packet.header.senderId.toHexString()
        latestAnnouncementByPeer[senderHex] = id to packet
        val cap = configProvider.seenCapacity().coerceAtLeast(1)
        while (latestAnnouncementByPeer.size > cap) {
            val it = latestAnnouncementByPeer.entries.iterator()
            if (it.hasNext()) {
                it.next()
                it.remove()
            } else {
                break
            }
        }
    }

    fun handleRequestSync(fromPeerId: ByteArray, request: RequestSyncPayload) {
        val sorted = GcsFilter.decodeToSortedSet(request.p, request.m, request.data)
        val toSendAnnouncements = latestAnnouncementByPeer.values.toList()
        toSendAnnouncements.forEach { (_, packet) ->
            val idBytes = PacketIdUtil.computeIdBytes(packet)
            if (!mightContain(sorted, request.m, idBytes)) {
                val toSend = packet.copy(
                    header = packet.header.copy(
                        ttl = ProtocolConstants.SYNC_TTL_HOPS,
                        recipientId = fromPeerId
                    )
                )
                sender.sendToPeer(fromPeerId, toSend)
            }
        }

        val toSendMessages = synchronized(messages) { messages.values.toList() }
        toSendMessages.forEach { packet ->
            val idBytes = PacketIdUtil.computeIdBytes(packet)
            if (!mightContain(sorted, request.m, idBytes)) {
                val toSend = packet.copy(
                    header = packet.header.copy(
                        ttl = ProtocolConstants.SYNC_TTL_HOPS,
                        recipientId = fromPeerId
                    )
                )
                sender.sendToPeer(fromPeerId, toSend)
            }
        }
    }

    private fun sendRequestSync() {
        val payload = buildGcsPayload()
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.REQUEST_SYNC,
                ttl = ProtocolConstants.SYNC_TTL_HOPS,
                flags = 0,
                length = payload.size,
                timestamp = System.currentTimeMillis(),
                senderId = myPeerId
            ),
            payload = payload
        )
        sender.broadcast(packet)
    }

    private fun sendRequestSyncToPeer(peerId: ByteArray) {
        val payload = buildGcsPayload()
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.REQUEST_SYNC,
                ttl = ProtocolConstants.SYNC_TTL_HOPS,
                flags = 0,
                length = payload.size,
                timestamp = System.currentTimeMillis(),
                senderId = myPeerId,
                recipientId = peerId
            ),
            payload = payload
        )
        sender.sendToPeer(peerId, packet)
    }

    private fun pruneStaleAnnouncements() {
        val cutoff = System.currentTimeMillis() - ProtocolConstants.Mesh.PEER_TIMEOUT_MS
        val stale = latestAnnouncementByPeer.filterValues { (_, packet) ->
            packet.header.timestamp < cutoff
        }.keys
        if (stale.isEmpty()) return
        for (peerId in stale) {
            latestAnnouncementByPeer.remove(peerId)
        }
    }

    private fun buildGcsPayload(): ByteArray {
        val ids = ArrayList<ByteArray>()
        latestAnnouncementByPeer.values.forEach { (_, packet) ->
            ids.add(PacketIdUtil.computeIdBytes(packet))
        }
        synchronized(messages) {
            messages.values.forEach { packet ->
                ids.add(PacketIdUtil.computeIdBytes(packet))
            }
        }
        val params = GcsFilter.buildFilter(
            ids = ids,
            maxBytes = configProvider.gcsMaxBytes(),
            targetFpr = configProvider.gcsTargetFpr()
        )
        return RequestSyncPayload(
            p = params.p,
            m = params.m,
            data = params.data
        ).encode()
    }

    private fun mightContain(sorted: LongArray, m: Long, idBytes: ByteArray): Boolean {
        if (sorted.isEmpty()) return false
        val candidate = GcsFilter.mapToRange(idBytes, m)
        return GcsFilter.contains(sorted, candidate)
    }
}
