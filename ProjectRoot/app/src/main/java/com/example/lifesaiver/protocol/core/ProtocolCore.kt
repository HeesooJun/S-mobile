package com.example.lifesaiver.protocol.core

import com.example.lifesaiver.protocol.codec.PacketDecoder
import com.example.lifesaiver.protocol.codec.PacketEncoder
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.pipeline.PacketPipeline
import com.example.lifesaiver.protocol.relay.PacketRelayManager
import com.example.lifesaiver.protocol.relay.PacketRelayManagerDelegate
import com.example.lifesaiver.protocol.relay.PeerDirectory
import com.example.lifesaiver.protocol.relay.RoutedPacket
import com.example.lifesaiver.protocol.transport.Transport
import com.example.lifesaiver.protocol.util.toHexString

class ProtocolCore(
    private val encoder: PacketEncoder,
    private val decoder: PacketDecoder,
    private val pipeline: PacketPipeline = PacketPipeline(encoder, decoder),
    myPeerId: ByteArray
) {
    private var transport: Transport? = null
    private var onPacket: ((Packet) -> Unit)? = null
    private val peerDirectory = PeerDirectory()
    private val relayManager = PacketRelayManager(myPeerId)

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
        val packets = pipeline.prepareOutbound(packet)
        packets.forEach { transport?.send(encoder.encode(it)) }
    }

    fun broadcast(packet: Packet) {
        val packets = pipeline.prepareOutbound(packet)
        packets.forEach { transport?.broadcast(encoder.encode(it)) }
    }

    fun onBytesReceived(bytes: ByteArray, relayAddress: String?) {
        val packet = decoder.decode(bytes) ?: return
        if (relayAddress != null && packet.header.ttl >= ProtocolConstants.MESSAGE_TTL_HOPS) {
            peerDirectory.record(packet.header.senderId, relayAddress)
        }

        val inbound = pipeline.handleInbound(packet)
        inbound.packetForRelay?.let {
            val peerId = packet.header.senderId.toHexString()
            relayManager.handlePacketRelay(RoutedPacket(it, peerId, relayAddress))
        }
        inbound.packetForApp?.let { onPacket?.invoke(it) }
    }
}
