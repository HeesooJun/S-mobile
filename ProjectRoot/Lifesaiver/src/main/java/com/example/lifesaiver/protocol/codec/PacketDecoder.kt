package com.example.lifesaiver.protocol.codec

import com.example.lifesaiver.protocol.model.Packet

interface PacketDecoder {
    fun decode(bytes: ByteArray): Packet?
}
