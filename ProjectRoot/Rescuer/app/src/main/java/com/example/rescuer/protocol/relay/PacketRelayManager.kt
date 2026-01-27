package com.example.rescuer.protocol.relay

import com.example.rescuer.protocol.model.Packet
import com.example.rescuer.protocol.util.toHexString
import kotlin.random.Random

class PacketRelayManager(private val myPeerId: ByteArray) {
    private val myPeerIdHex = myPeerId.toHexString()

    var delegate: PacketRelayManagerDelegate? = null

    fun handlePacketRelay(routed: RoutedPacket) {
        val packet = routed.packet
        val peerId = routed.peerId ?: "unknown"

        if (isPacketAddressedToMe(packet)) {
            return
        }

        if (peerId == myPeerIdHex) {
            return
        }

        if (packet.header.ttl <= 0) {
            return
        }

        val relayPacket = packet.copy(
            header = packet.header.copy(ttl = packet.header.ttl - 1)
        )

        val route = relayPacket.header.route
        if (!route.isNullOrEmpty()) {
            if (route.map { it.toHexString() }.toSet().size < route.size) {
                return
            }

            val index = route.indexOfFirst { it.contentEquals(myPeerId) }
            if (index >= 0) {
                val nextHop = if (index + 1 < route.size) {
                    route[index + 1]
                } else {
                    relayPacket.header.recipientId
                }
                val nextHopHex = nextHop?.toHexString()
                if (nextHopHex != null) {
                    val success = delegate?.sendToPeer(
                        nextHopHex,
                        RoutedPacket(relayPacket, peerId, routed.relayAddress)
                    ) ?: false
                    if (success) {
                        return
                    }
                }
            }
        }

        val shouldRelay = shouldRelayPacket(relayPacket)
        if (shouldRelay) {
            delegate?.broadcastPacket(RoutedPacket(relayPacket, peerId, routed.relayAddress))
        }
    }

    internal fun isPacketAddressedToMe(packet: Packet): Boolean {
        val recipientId = packet.header.recipientId ?: return false
        return recipientId.contentEquals(myPeerId)
    }

    private fun shouldRelayPacket(packet: Packet): Boolean {
        if (packet.header.ttl >= 4) {
            return true
        }

        val networkSize = delegate?.getNetworkSize() ?: 1
        if (networkSize <= 3) {
            return true
        }

        val relayProb = when {
            networkSize <= 10 -> 1.0
            networkSize <= 30 -> 0.85
            networkSize <= 50 -> 0.7
            networkSize <= 100 -> 0.55
            else -> 0.4
        }

        return Random.nextDouble() < relayProb
    }
}

interface PacketRelayManagerDelegate {
    fun getNetworkSize(): Int
    fun broadcastPacket(routed: RoutedPacket)
    fun sendToPeer(peerId: String, routed: RoutedPacket): Boolean
}
