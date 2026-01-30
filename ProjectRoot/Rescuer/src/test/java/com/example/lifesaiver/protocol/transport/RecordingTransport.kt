package com.example.lifesaiver.protocol.transport

class RecordingTransport : Transport {
    private var onReceive: ((ByteArray, String?) -> Unit)? = null

    val sent = mutableListOf<ByteArray>()
    val broadcasts = mutableListOf<ByteArray>()

    override fun send(data: ByteArray) {
        sent.add(data)
    }

    override fun broadcast(data: ByteArray, excludeAddress: String?) {
        broadcasts.add(data)
    }

    override fun setOnReceive(listener: (ByteArray, String?) -> Unit) {
        onReceive = listener
    }

    fun emit(data: ByteArray, address: String? = null) {
        onReceive?.invoke(data, address)
    }
}
