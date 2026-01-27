package com.example.rescuer.protocol.pipeline

import com.example.rescuer.protocol.model.Packet

object TtlPolicy {
    fun decrementForRelay(packet: Packet): Packet? {
        val ttl = packet.header.ttl
        if (ttl <= 0) return null
        return packet.copy(header = packet.header.copy(ttl = ttl - 1))
    }
}
