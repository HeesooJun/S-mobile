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
import com.example.lifesaiver.protocol.storeforward.StoreForwardManager
import com.example.lifesaiver.protocol.transport.Transport
import com.example.lifesaiver.protocol.util.sha256Hex
import com.example.lifesaiver.protocol.util.toHexString
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

class ProtocolCore(
    private val encoder: PacketEncoder,
    private val decoder: PacketDecoder,
    private val pipeline: PacketPipeline = PacketPipeline(encoder, decoder),
    private val myPeerId: ByteArray
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
        val recipientId = packet.header.recipientId
        if (recipientId != null) {
            val peerId = recipientId.toHexString()
            if (packet.header.type == PacketType.FILE_ACK) {
                storeForwardScope.launch { sendPacketToPeer(peerId, packet) }
                return
            }
            if (packet.header.type == PacketType.FILE_TRANSFER) {
                storeForwardScope.launch {
                    val delivered = sendFileTransferWithAck(peerId, packet)
                    if (!delivered) {
                        storeForwardManager.cache(packet)
                    }
                }
                return
            }
            storeForwardScope.launch {
                val delivered = sendPacketToPeer(peerId, packet)
                if (!delivered) {
                    storeForwardManager.cache(packet)
                }
            }
            return
        }
        if (packet.header.type == PacketType.FILE_TRANSFER) {
            sendFileTransfer(packet, isBroadcast = false)
            return
        }
        val packets = pipeline.prepareOutbound(packet)
        packets.forEach { transport?.send(encoder.encode(it)) }
    }

    fun broadcast(packet: Packet) {
        if (packet.header.type == PacketType.FILE_TRANSFER) {
            sendFileTransfer(packet, isBroadcast = true)
            return
        }
        val packets = pipeline.prepareOutbound(packet)
        packets.forEach { transport?.broadcast(encoder.encode(it)) }
    }

    fun cancelFileTransfer(transferId: String) {
        fileTransferJobs.remove(transferId)?.cancel()
    }

    fun onBytesReceived(bytes: ByteArray, relayAddress: String?) {
        val packet = decoder.decode(bytes) ?: return
        if (relayAddress != null && packet.header.ttl >= ProtocolConstants.MESSAGE_TTL_HOPS) {
            peerDirectory.record(packet.header.senderId, relayAddress)
            val peerId = packet.header.senderId.toHexString()
            drainStoreForward(peerId)
        }

        if (packet.header.type == PacketType.FILE_ACK) {
            handleFileAck(packet)
            return
        }

        val inbound = pipeline.handleInbound(packet)
        inbound.packetForRelay?.let {
            val peerId = packet.header.senderId.toHexString()
            relayManager.handlePacketRelay(RoutedPacket(it, peerId, relayAddress))
        }
        inbound.packetForApp?.let { onPacket?.invoke(it) }
    }

    private fun sendFileTransfer(packet: Packet, isBroadcast: Boolean) {
        val transferId = packet.payload.sha256Hex()
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
            sendFragments(packet, delayMs, sender)
        }
        fileTransferJobs[transferId] = job
        job.invokeOnCompletion { fileTransferJobs.remove(transferId) }
    }

    private suspend fun sendPacketToPeer(peerId: String, packet: Packet): Boolean {
        val address = peerDirectory.getAddress(peerId) ?: return false
        if (packet.header.type == PacketType.FILE_TRANSFER) {
            val delayMs = ProtocolConstants.FileTransfer.FRAGMENT_DELAY_MS
            return sendFragments(packet, delayMs) { fragment ->
                transport?.sendToAddress(address, encoder.encode(fragment)) ?: false
            }
        }
        val packets = pipeline.prepareOutbound(packet)
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
