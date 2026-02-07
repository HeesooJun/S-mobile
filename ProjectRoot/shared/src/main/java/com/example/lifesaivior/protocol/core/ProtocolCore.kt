package com.example.lifesaivior.protocol.core

import com.example.lifesaivior.core.log.ConnectionLog
import com.example.lifesaivior.protocol.codec.PacketDecoder
import com.example.lifesaivior.protocol.codec.PacketEncoder
import com.example.lifesaivior.protocol.core.ProtocolConstants
import com.example.lifesaivior.protocol.model.FileTransferAckPayload
import com.example.lifesaivior.protocol.model.Packet
import com.example.lifesaivior.protocol.model.PacketHeader
import com.example.lifesaivior.protocol.model.PacketType
import com.example.lifesaivior.protocol.pipeline.PacketPipeline
import com.example.lifesaivior.protocol.relay.PacketRelayManager
import com.example.lifesaivior.protocol.relay.PacketRelayManagerDelegate
import com.example.lifesaivior.protocol.relay.PeerDirectory
import com.example.lifesaivior.protocol.relay.RoutedPacket
import com.example.lifesaivior.protocol.security.SignatureManager
import com.example.lifesaivior.protocol.storeforward.StoreForwardManager
import com.example.lifesaivior.protocol.transport.Transport
import com.example.lifesaivior.protocol.util.sha256Hex
import com.example.lifesaivior.protocol.util.toHexString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class ProtocolCore(
    private val encoder: PacketEncoder,
    private val decoder: PacketDecoder,
    private val pipeline: PacketPipeline = PacketPipeline(encoder, decoder),
    private val myPeerId: ByteArray,
    private val signatureManager: SignatureManager? = null,
    private val routePlanner: ((String, String) -> List<String>?)? = null
) {
    private val myPeerIdHex = myPeerId.toHexString()
    private var transport: Transport? = null
    private var onPacket: ((Packet, String?) -> Unit)? = null
    private val peerDirectory = PeerDirectory()
    private val relayManager = PacketRelayManager(myPeerId)
    private val fileTransferScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val storeForwardScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val storeForwardManager = StoreForwardManager()
    private val fileTransferJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val storeForwardJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
    private val fileAckWaiters = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    private val inboundScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val inboundActors = ConcurrentHashMap<String, SendChannel<InboundEnvelope>>()

    private data class InboundEnvelope(
        val packet: Packet,
        val relayAddress: String?
    )

    init {
        relayManager.delegate = object : PacketRelayManagerDelegate {
            override fun getNetworkSize(): Int {
                return transport?.getNetworkSize() ?: 1
            }

            override fun broadcastPacket(routed: RoutedPacket) {
                val encoded = encoder.encode(routed.packet)
                transport?.broadcast(encoded, routed.relayAddress)
            }

            override fun sendToPeer(peerId: String, routed: RoutedPacket): Boolean {
                val address = peerDirectory.getAddress(peerId) ?: return false
                val encoded = encoder.encode(routed.packet)
                return transport?.sendToAddress(address, encoded) ?: false
            }
        }
    }

    fun attachTransport(transport: Transport) {
        this.transport = transport
        transport.setOnReceive { bytes, address -> onBytesReceived(bytes, address) }
    }

    fun setOnPacketReceived(handler: (Packet, String?) -> Unit) {
        onPacket = handler
    }

    fun send(packet: Packet) {
        val recipientId = packet.header.recipientId
        if (recipientId != null) {
            val recipientHex = recipientId.toHexString()
            if (packet.header.type == PacketType.FILE_ACK) {
                storeForwardScope.launch {
                    sendAddressedPacket(recipientHex, packet, allowBroadcastFallback = true)
                }
                return
            }
            if (packet.header.type == PacketType.FILE_TRANSFER) {
                storeForwardScope.launch {
                    val delivered = sendFileTransferWithAck(recipientHex, packet)
                    if (!delivered) {
                        storeForwardManager.cache(packet)
                    }
                }
                return
            }
            storeForwardScope.launch {
                val delivered = sendAddressedPacket(recipientHex, packet, allowBroadcastFallback = true)
                if (!delivered) {
                    storeForwardManager.cache(packet)
                }
            }
            return
        }
        val prepared = prepareOutbound(packet)
        if (prepared.header.type == PacketType.FILE_TRANSFER) {
            sendFileTransfer(prepared, isBroadcast = false)
            return
        }
        val packets = pipeline.prepareOutbound(prepared)
        packets.forEach { transport?.send(encoder.encode(it)) }
    }

    fun broadcast(packet: Packet) {
        val prepared = prepareOutbound(packet)
        if (prepared.header.type == PacketType.FILE_TRANSFER) {
            sendFileTransfer(prepared, isBroadcast = true)
            return
        }
        val packets = pipeline.prepareOutbound(prepared)
        packets.forEach { transport?.broadcast(encoder.encode(it)) }
    }

    fun cancelFileTransfer(transferId: String) {
        fileTransferJobs.remove(transferId)?.cancel()
    }

    fun onBytesReceived(bytes: ByteArray, relayAddress: String?) {
        val packet = decoder.decode(bytes) ?: return
        val peerId = packet.header.senderId.toHexString()
        val actor = getOrCreateInboundActor(peerId)
        inboundScope.launch {
            try {
                actor.send(InboundEnvelope(packet, relayAddress))
            } catch (_: Exception) {
                handleInbound(peerId, packet, relayAddress)
            }
        }
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun getOrCreateInboundActor(peerId: String): SendChannel<InboundEnvelope> {
        return inboundActors.getOrPut(peerId) {
            inboundScope.actor(capacity = Channel.UNLIMITED) {
                for (envelope in channel) {
                    handleInbound(peerId, envelope.packet, envelope.relayAddress)
                }
            }
        }
    }

    private fun handleInbound(peerId: String, packet: Packet, relayAddress: String?) {
        if (relayAddress != null && packet.header.ttl >= ProtocolConstants.MESSAGE_TTL_HOPS) {
            peerDirectory.record(packet.header.senderId, relayAddress)
            drainStoreForward(peerId)
        }

        if (packet.header.type == PacketType.FILE_ACK) {
            handleFileAck(packet)
            return
        }

        val inbound = pipeline.handleInbound(packet)
        val candidateForVerification = inbound.packetForApp ?: inbound.packetForRelay
        if (candidateForVerification != null &&
            !verifyIfNeeded(candidateForVerification, relayAddress)
        ) {
            return
        }
        inbound.packetForRelay?.let {
            relayManager.handlePacketRelay(RoutedPacket(it, peerId, relayAddress))
        }
        inbound.packetForApp?.let { onPacket?.invoke(it, relayAddress) }
    }

    private fun sendFileTransfer(packet: Packet, isBroadcast: Boolean) {
        val signedPacket = prepareOutbound(packet)
        val transferId = signedPacket.payload.sha256Hex()
        fileTransferJobs.remove(transferId)?.cancel()
        val job = fileTransferScope.launch {
            val delayMs = ProtocolConstants.FileTransfer.FRAGMENT_DELAY_MS
            val sender: (Packet) -> Boolean = { fragment ->
                if (isBroadcast) {
                    transport?.broadcast(encoder.encode(fragment))
                    true
                } else {
                    transport?.send(encoder.encode(fragment))
                    true
                }
            }
            sendFragments(signedPacket, delayMs, sender)
        }
        fileTransferJobs[transferId] = job
        job.invokeOnCompletion { fileTransferJobs.remove(transferId) }
    }

    private suspend fun sendPacketToPeer(peerId: String, packet: Packet): Boolean {
        val address = peerDirectory.getAddress(peerId) ?: return false
        val signedPacket = prepareOutbound(packet)
        if (signedPacket.header.type == PacketType.FILE_TRANSFER) {
            val delayMs = ProtocolConstants.FileTransfer.FRAGMENT_DELAY_MS
            return sendFragments(signedPacket, delayMs) { fragment ->
                transport?.sendToAddress(address, encoder.encode(fragment)) ?: false
            }
        }
        val packets = pipeline.prepareOutbound(signedPacket)
        for (fragment in packets) {
            val sent = transport?.sendToAddress(address, encoder.encode(fragment)) ?: false
            if (!sent) return false
        }
        return true
    }

    private suspend fun sendFileTransferWithAck(peerId: String, packet: Packet): Boolean {
        val transferIdHex = packet.payload.sha256Hex()
        val ackKey = "$peerId:$transferIdHex"
        val attempts = ProtocolConstants.FileTransfer.MAX_RETRY_ATTEMPTS
        repeat(attempts) { attempt ->
            val wait = CompletableDeferred<Boolean>()
            fileAckWaiters[ackKey]?.cancel()
            fileAckWaiters[ackKey] = wait

            val refreshed = packet.copy(
                header = packet.header.copy(timestamp = System.currentTimeMillis())
            )
            val delivered = sendAddressedPacket(peerId, refreshed, allowBroadcastFallback = true)
            if (!delivered) {
                fileAckWaiters.remove(ackKey)
                return false
            }

            val acked = withTimeoutOrNull(ProtocolConstants.FileTransfer.ACK_TIMEOUT_MS) {
                wait.await()
            } ?: false

            if (acked) {
                fileAckWaiters.remove(ackKey)
                return true
            }

            fileAckWaiters.remove(ackKey)
            if (attempt < attempts - 1) {
                delay(ProtocolConstants.FileTransfer.ACK_TIMEOUT_MS / 2)
            }
        }
        return false
    }

    fun sendFileAck(recipientId: ByteArray, transferId: ByteArray) {
        if (transferId.size != FileTransferAckPayload.TRANSFER_ID_SIZE) return
        val payload = FileTransferAckPayload(transferId = transferId).encode()
        if (payload.isEmpty()) return
        val packet = Packet(
            header = PacketHeader(
                version = 2,
                type = PacketType.FILE_ACK,
                ttl = ProtocolConstants.SYNC_TTL_HOPS,
                flags = 0,
                length = payload.size,
                timestamp = System.currentTimeMillis(),
                senderId = myPeerId,
                recipientId = recipientId
            ),
            payload = payload
        )
        val peerId = recipientId.toHexString()
        storeForwardScope.launch { sendAddressedPacket(peerId, packet, allowBroadcastFallback = true) }
    }

    private fun prepareOutbound(packet: Packet): Packet {
        val routed = attachRouteIfNeeded(packet)
        return signForSend(routed)
    }

    private fun attachRouteIfNeeded(packet: Packet): Packet {
        val recipientId = packet.header.recipientId ?: return packet
        if (packet.header.route != null) return packet
        val planner = routePlanner ?: return packet
        val recipientHex = recipientId.toHexString()
        val path = planner.invoke(myPeerIdHex, recipientHex) ?: return packet
        if (path.size < 3) return packet
        val intermediates = path.subList(1, path.size - 1)
        if (intermediates.isEmpty()) return packet
        val hops = intermediates.map { hexToBytesFixed(it, myPeerId.size) }
        val nextVersion = max(packet.header.version, 2)
        return packet.copy(header = packet.header.copy(route = hops, version = nextVersion))
    }

    private suspend fun sendAddressedPacket(
        recipientPeerId: String,
        packet: Packet,
        allowBroadcastFallback: Boolean
    ): Boolean {
        val prepared = prepareOutbound(packet)
        val deliveryPeerId = resolveDeliveryPeerId(prepared, recipientPeerId)
        var delivered = sendPacketToPeer(deliveryPeerId, prepared)
        if (!delivered && allowBroadcastFallback) {
            val networkSize = transport?.getNetworkSize() ?: 0
            if (networkSize > 0) {
                ConnectionLog.add(
                    "Send",
                    "direct failed -> broadcast fallback peer=$recipientPeerId link=$networkSize"
                )
                broadcastRouted(prepared)
                delivered = true
            } else {
                ConnectionLog.add(
                    "Send",
                    "direct failed -> no link (cache) peer=$recipientPeerId"
                )
            }
        }
        return delivered
    }

    private fun resolveDeliveryPeerId(packet: Packet, recipientPeerId: String): String {
        val route = packet.header.route
        return if (!route.isNullOrEmpty()) {
            route.first().toHexString()
        } else {
            recipientPeerId
        }
    }

    private fun broadcastRouted(packet: Packet) {
        val packets = pipeline.prepareOutbound(packet)
        packets.forEach { transport?.broadcast(encoder.encode(it)) }
    }

    private fun signForSend(packet: Packet): Packet {
        val manager = signatureManager ?: return packet
        return manager.sign(packet)
    }

    private fun hexToBytesFixed(hex: String, size: Int): ByteArray {
        val clean = hex.lowercase().filter { it in '0'..'9' || it in 'a'..'f' }
        val result = ByteArray(size)
        var idx = 0
        var out = 0
        while (idx + 1 < clean.length && out < size) {
            val byteStr = clean.substring(idx, idx + 2)
            result[out++] = byteStr.toIntOrNull(16)?.toByte() ?: 0
            idx += 2
        }
        return result
    }

    private fun verifyIfNeeded(packet: Packet, relayAddress: String?): Boolean {
        val manager = signatureManager ?: return true
        return manager.verify(packet, pathLabelFor(packet, relayAddress))
    }

    private fun pathLabelFor(packet: Packet, relayAddress: String?): String? {
        if (relayAddress == null) return null
        val baseTtl = when (packet.header.type) {
            PacketType.REQUEST_SYNC,
            PacketType.FILE_ACK -> ProtocolConstants.SYNC_TTL_HOPS
            else -> ProtocolConstants.MESSAGE_TTL_HOPS
        }
        return if (packet.header.ttl >= baseTtl) "direct" else "mesh"
    }

    private fun handleFileAck(packet: Packet) {
        val payload = FileTransferAckPayload.decode(packet.payload) ?: return
        if (payload.status != FileTransferAckPayload.STATUS_OK) return
        val senderId = packet.header.senderId.toHexString()
        val transferIdHex = payload.transferId.toHexString()
        val key = "$senderId:$transferIdHex"
        fileAckWaiters[key]?.complete(true)
    }

    private suspend fun sendFragments(
        packet: Packet,
        delayMs: Long,
        sender: (Packet) -> Boolean
    ): Boolean {
        val packets = pipeline.prepareOutbound(packet)
        packets.forEachIndexed { index, fragment ->
            val sent = sender(fragment)
            if (!sent) return false
            if (index < packets.lastIndex) {
                delay(delayMs)
            }
        }
        return true
    }

    private fun drainStoreForward(peerId: String) {
        val existing = storeForwardJobs[peerId]
        if (existing?.isActive == true) return
        val job = storeForwardScope.launch {
            storeForwardManager.drainForPeer(peerId) { packet ->
                sendPacketToPeer(peerId, packet)
            }
        }
        storeForwardJobs[peerId] = job
        job.invokeOnCompletion { storeForwardJobs.remove(peerId) }
    }
}
