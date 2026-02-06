package com.example.lifesaivior.protocol.codec

import com.example.lifesaivior.protocol.model.Packet

interface PacketDecoder {
    fun decode(bytes: ByteArray): Packet?
}
