package com.example.lifesaiver.protocol.sync

import com.example.lifesaiver.protocol.model.Packet
import java.security.MessageDigest

object PacketIdUtil {
    fun computeIdBytes(packet: Packet): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(packet.header.type.code.toByte())
        md.update(packet.header.senderId)
        val ts = packet.header.timestamp
        for (i in 7 downTo 0) {
            md.update(((ts ushr (i * 8)) and 0xFF).toByte())
        }
        md.update(packet.payload)
        return md.digest().copyOf(16)
    }

    fun computeIdHex(packet: Packet): String {
        return computeIdBytes(packet).joinToString("") { b -> "%02x".format(b) }
    }
}
