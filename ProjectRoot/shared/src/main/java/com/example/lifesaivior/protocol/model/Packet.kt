package com.example.lifesaivior.protocol.model

data class Packet(
    val header: PacketHeader,
    val payload: ByteArray
)
