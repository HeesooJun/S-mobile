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

    fun handleInbound(packet: Packet): InboundResult {
        if (!deduplicator.shouldProcess(packet)) {
            return InboundResult(packetForApp = null, packetForRelay = null)
        }

        if (packet.header.type == PacketType.FRAGMENT) {
            val reassembled = fragmentManager.handleFragment(packet)
            if (reassembled == null) {
                return InboundResult(packetForApp = null, packetForRelay = packet)
            }

            val decoded = decoder.decode(reassembled)
                ?: return InboundResult(packetForApp = null, packetForRelay = packet)
            val suppressed = decoded.copy(
                header = decoded.header.copy(ttl = ProtocolConstants.SYNC_TTL_HOPS)
            )
            val forApp = if (deduplicator.shouldProcess(suppressed)) suppressed else null
            return InboundResult(packetForApp = forApp, packetForRelay = packet)
        }

        return InboundResult(packetForApp = packet, packetForRelay = packet)
    }
}
