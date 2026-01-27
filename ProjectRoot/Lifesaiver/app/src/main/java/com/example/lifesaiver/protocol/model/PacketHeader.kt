package com.example.lifesaiver.protocol.model

data class PacketHeader(
    val version: Int,
    val type: PacketType,
    val ttl: Int,
    val flags: Int,
    val length: Int,
    val timestamp: Long,
    val senderId: ByteArray,
    val recipientId: ByteArray? = null,
    val signature: ByteArray? = null,
    val route: List<ByteArray>? = null
)
