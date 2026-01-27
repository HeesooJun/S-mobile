package com.example.lifesaiver.protocol.codec

import com.example.lifesaiver.protocol.model.Packet

interface PacketEncoder {
    fun encode(packet: Packet): ByteArray
}
