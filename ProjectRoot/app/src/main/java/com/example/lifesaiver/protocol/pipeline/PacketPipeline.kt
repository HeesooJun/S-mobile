package com.example.lifesaiver.protocol.pipeline

import com.example.lifesaiver.protocol.codec.PacketDecoder
import com.example.lifesaiver.protocol.codec.PacketEncoder
import com.example.lifesaiver.protocol.core.ProtocolConstants
import com.example.lifesaiver.protocol.model.Packet
import com.example.lifesaiver.protocol.model.PacketType

class PacketPipeline(
    encoder: PacketEncoder,
    private val decoder: PacketDecoder,
    private val deduplicator: PacketDeduplicator = PacketDeduplicator(),
    private val fragmentManager: FragmentManager = FragmentManager(encoder)
) {
    fun prepareOutbound(packet: Packet): List<Packet> {
        return fragmentManager.createFragments(packet)
    }

    fun handleInbound(packet: Packet): Packet? {
        if (!deduplicator.shouldProcess(packet)) return null

        if (packet.header.type == PacketType.FRAGMENT) {
            val reassembled = fragmentManager.handleFragment(packet) ?: return null
            val decoded = decoder.decode(reassembled) ?: return null
            val suppressed = decoded.copy(header = decoded.header.copy(ttl = ProtocolConstants.SYNC_TTL_HOPS))
            return if (deduplicator.shouldProcess(suppressed)) suppressed else null
        }

        return packet
    }
}
