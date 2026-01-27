package com.example.rescuer.protocol.codec

import com.example.rescuer.protocol.model.Packet

interface PacketEncoder {
    fun encode(packet: Packet): ByteArray
}
