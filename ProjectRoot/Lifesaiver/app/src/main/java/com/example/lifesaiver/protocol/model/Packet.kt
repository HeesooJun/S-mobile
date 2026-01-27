package com.example.lifesaiver.protocol.model

data class Packet(
    val header: PacketHeader,
    val payload: ByteArray
)
