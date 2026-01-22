package com.example.lifesaiver.protocol.transport

interface Transport {
    fun send(data: ByteArray)
    fun setOnReceive(listener: (ByteArray) -> Unit)
}
