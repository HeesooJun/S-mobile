package com.example.lifesaiver.protocol.model

data class FileTransferAckPayload(
    val transferId: ByteArray,
    val status: Int = STATUS_OK
) {
    fun encode(): ByteArray {
        if (transferId.size != TRANSFER_ID_SIZE) return ByteArray(0)
        return byteArrayOf(status.toByte()) + transferId
    }

    companion object {
        const val STATUS_OK = 1
        const val TRANSFER_ID_SIZE = 32

        fun decode(bytes: ByteArray): FileTransferAckPayload? {
            if (bytes.size < 1 + TRANSFER_ID_SIZE) return null
            val status = bytes[0].toInt() and 0xFF
            val transferId = bytes.copyOfRange(1, 1 + TRANSFER_ID_SIZE)
            return FileTransferAckPayload(transferId = transferId, status = status)
        }
    }
}
