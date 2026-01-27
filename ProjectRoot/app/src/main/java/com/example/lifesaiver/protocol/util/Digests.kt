package com.example.lifesaiver.protocol.util

import java.security.MessageDigest

fun ByteArray.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return digest.joinToString("") { "%02x".format(it) }
}
