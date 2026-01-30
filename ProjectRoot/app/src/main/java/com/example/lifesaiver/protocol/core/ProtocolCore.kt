package com.example.lifesaiver.protocol.core

import com.example.lifesaiver.protocol.codec.PacketDecoder
import com.example.lifesaiver.protocol.codec.PacketEncoder
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.FileTransferAckPayload
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketHeader
import com.example.lifesaiver.protocol.model.PacketType
import com.example.lifesaiver.protocol.pipeline.PacketPipeline
import com.example.lifesaiver.protocol.relay.PacketRelayManager
import com.example.lifesaiver.protocol.relay.PacketRelayManagerDelegate
import com.example.lifesaiver.protocol.relay.PeerDirectory
import com.example.lifesaiver.protocol.relay.RoutedPacket
import com.example.lifesaiver.protocol.security.SignatureManager
import com.example.lifesaiver.protocol.storeforward.StoreForwardManager
import com.example.lifesaiver.protocol.transport.Transport
import com.example.lifesaiver.protocol.util.sha256Hex
import com.example.lifesaiver.protocol.util.toHexString
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

class ProtocolCore(
    private val encoder: PacketEncoder,
    private val decoder: PacketDecoder,
    private val pipeline: PacketPipeline = PacketPipeline(encoder, decoder),
    private val myPeerId: ByteArray,
    private val signatureManager: SignatureManager? = null
) {
    private var transport: Transport? = null
    private var onPacket: ((Packet) -> Unit)? = null
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

    fun setOnPacketReceived(handler: (Packet) -> Unit) {
        onPacket = handler
    }

    fun send(packet: Packet) {
        val signedPacket = signIfNeeded(packet)
        val recipientId = signedPacket.header.recipientId
        if (recipientId != null) {
            val peerId = recipientId.toHexString()
            if (signedPacket.header.type == PacketType.FILE_ACK) {
                storeForwardScope.launch { sendPacketToPeer(peerId, signedPacket) }
                return
            }
            if (signedPacket.header.type == PacketType.FILE_TRANSFER) {
                storeForwardScope.launch {
                    val delivered = sendFileTransferWithAck(peerId, signedPacket)
                    if (!delivered) {
                        storeForwardManager.cache(signedPacket)
                    }
                }
                return
            }
            storeForwardScope.launch {
                val delivered = sendPacketToPeer(peerId, signedPacket)
                if (!delivered) {
                    storeForwardManager.cache(signedPacket)
                }
            }
            return
        }
        if (signedPacket.header.type == PacketType.FILE_TRANSFER) {
            sendFileTransfer(signedPacket, isBroadcast = false)
            return
        }
        val packets = pipeline.prepareOutbound(signedPacket)
        packets.forEach { transport?.send(encoder.encode(it)) }
    }

    fun broadcast(packet: Packet) {
        val signedPacket = signIfNeeded(packet)
        if (signedPacket.header.type == PacketType.FILE_TRANSFER) {
            sendFileTransfer(signedPacket, isBroadcast = true)
            return
        }
        val packets = pipeline.prepareOutbound(signedPacket)
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
        if (candidateForVerification != null && !verifyIfNeeded(candidateForVerification)) {
            return
        }
        inbound.packetForRelay?.let {
            relayManager.handlePacketRelay(RoutedPacket(it, peerId, relayAddress))
        }
        inbound.packetForApp?.let { onPacket?.invoke(it) }
    }

    private fun sendFileTransfer(packet: Packet, isBroadcast: Boolean) {
        val signedPacket = signIfNeeded(packet)
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
        val signedPacket = signIfNeeded(packet)
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
            val delivered = sendPacketToPeer(peerId, refreshed)
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
        storeForwardScope.launch { sendPacketToPeer(peerId, packet) }
    }

    private fun signIfNeeded(packet: Packet): Packet {
        val manager = signatureManager ?: return packet
        if (packet.header.signature != null) return packet
        return manager.sign(packet)
    }

    private fun verifyIfNeeded(packet: Packet): Boolean {
        val manager = signatureManager ?: return true
        return manager.verify(packet)
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
