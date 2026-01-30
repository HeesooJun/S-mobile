package com.example.lifesaiver.protocol.codec

object PacketFlags {
    const val HAS_RECIPIENT: Int = 0x01
    const val HAS_SIGNATURE: Int = 0x02
    const val IS_COMPRESSED: Int = 0x04
    const val HAS_ROUTE: Int = 0x08
}
