package com.example.lifesaiver.protocol.relay

import com.example.lifesaiver.protocol.model.Packet

data class RoutedPacket(
    val packet: Packet,
    val peerId: String?,
    val relayAddress: String? = null
)
