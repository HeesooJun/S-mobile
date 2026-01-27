package com.example.rescuer.protocol.transport

interface Transport {
    fun send(data: ByteArray)
    fun broadcast(data: ByteArray, excludeAddress: String? = null)
    fun setOnReceive(listener: (ByteArray, String?) -> Unit)
    fun sendToAddress(address: String, data: ByteArray): Boolean = false
    fun getNetworkSize(): Int = 1
}
