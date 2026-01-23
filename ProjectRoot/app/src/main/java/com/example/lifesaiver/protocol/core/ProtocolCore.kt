package com.example.lifesaiver.protocol.core

import com.example.lifesaiver.protocol.codec.PacketDecoder
import com.example.lifesaiver.protocol.codec.PacketEncoder
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.pipeline.PacketPipeline
import com.example.lifesaiver.protocol.transport.Transport

class ProtocolCore(
    private val encoder: PacketEncoder,
    private val decoder: PacketDecoder,
    private val pipeline: PacketPipeline = PacketPipeline(encoder, decoder)
) {
    private var transport: Transport? = null
    private var onPacket: ((Packet) -> Unit)? = null

    fun attachTransport(transport: Transport) {
        this.transport = transport
        transport.setOnReceive { bytes -> onBytesReceived(bytes) }
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

    fun onBytesReceived(bytes: ByteArray) {
        val packet = decoder.decode(bytes) ?: return
        pipeline.handleInbound(packet)?.let { onPacket?.invoke(it) }
    }
}
