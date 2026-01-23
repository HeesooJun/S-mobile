package com.example.lifesaiver.protocol.transport

class RecordingTransport : Transport {
    private var onReceive: ((ByteArray) -> Unit)? = null

    val sent = mutableListOf<ByteArray>()
    val broadcasts = mutableListOf<ByteArray>()

    override fun send(data: ByteArray) {
        sent.add(data)
    }

    override fun broadcast(data: ByteArray) {
        broadcasts.add(data)
    }

    override fun setOnReceive(listener: (ByteArray) -> Unit) {
        onReceive = listener
    }

    fun emit(data: ByteArray) {
        onReceive?.invoke(data)
    }
}
