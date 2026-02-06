package com.example.lifesaivior.protocol.relay

import com.example.lifesaivior.protocol.model.Packet

data class RoutedPacket(
    val packet: Packet,
    val peerId: String?,
    val relayAddress: String? = null
)
