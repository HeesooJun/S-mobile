package com.example.rescuer.protocol.relay

import com.example.rescuer.protocol.util.toHexString
import java.util.concurrent.ConcurrentHashMap

class PeerDirectory {
    private val peerToAddress = ConcurrentHashMap<String, String>()

    fun record(peerId: ByteArray, address: String) {
        peerToAddress[peerId.toHexString()] = address
    }

    fun getAddress(peerIdHex: String): String? {
        return peerToAddress[peerIdHex]
    }
}
