package com.example.rescuer.protocol.codec

import com.example.rescuer.protocol.model.Packet

interface PacketDecoder {
    fun decode(bytes: ByteArray): Packet?
}
