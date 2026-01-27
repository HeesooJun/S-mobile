package com.example.rescuer.protocol.relay

import com.example.rescuer.protocol.model.Packet

data class RoutedPacket(
    val packet: Packet,
    val peerId: String?,
    val relayAddress: String? = null
)
