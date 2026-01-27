package com.example.rescuer.protocol.model

data class Packet(
    val header: PacketHeader,
    val payload: ByteArray
)
