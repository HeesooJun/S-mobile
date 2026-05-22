package com.example.lifesaivior.protocol.codec

import com.example.lifesaivior.protocol.model.Packet

interface PacketEncoder {
    fun encode(packet: Packet): ByteArray
}
