package com.example.lifesaiver.protocol.model

enum class PacketType(val code: Int) {
    ANNOUNCE(0x01),
    MESSAGE(0x02),
    LEAVE(0x03),
    NOISE_HANDSHAKE(0x10),
    NOISE_ENCRYPTED(0x11),
    FRAGMENT(0x20),
    REQUEST_SYNC(0x21),
    FILE_TRANSFER(0x22),
    RESCUE_ID(0x30);

    companion object {
        fun fromCode(code: Int): PacketType? {
            return entries.firstOrNull { it.code == code }
        }
    }
}
