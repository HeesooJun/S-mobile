package com.example.rescuer.core.ble

import com.example.rescuer.protocol.transport.Transport

class BleTransport(private val bleManager: BleManager) : Transport {
    private var onReceive: ((ByteArray, String?) -> Unit)? = null

    init {
        bleManager.setProtocolCallback { bytes, address ->
            onReceive?.invoke(bytes, address)
        }
    }

    override fun send(data: ByteArray) {
        bleManager.sendProtocol(data)
    }

    override fun broadcast(data: ByteArray, excludeAddress: String?) {
        bleManager.broadcastProtocol(data, excludeAddress)
    }

    override fun setOnReceive(listener: (ByteArray, String?) -> Unit) {
        onReceive = listener
    }

    override fun sendToAddress(address: String, data: ByteArray): Boolean {
        return bleManager.sendProtocolTo(address, data)
    }

    override fun getNetworkSize(): Int {
        return bleManager.getConnectedPeerCount()
    }
}
