package com.example.lifesaiver.core.ble

import com.example.lifesaiver.protocol.transport.Transport

class BleTransport(private val bleManager: BleManager) : Transport {
    private var onReceive: ((ByteArray) -> Unit)? = null

    init {
        bleManager.setProtocolCallback { bytes ->
            onReceive?.invoke(bytes)
        }
    }

    override fun send(data: ByteArray) {
        bleManager.sendProtocol(data)
    }

    override fun broadcast(data: ByteArray) {
        bleManager.broadcastProtocol(data)
    }

    override fun setOnReceive(listener: (ByteArray) -> Unit) {
        onReceive = listener
    }
}
