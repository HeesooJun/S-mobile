package com.example.lifesaivior.protocol.util

fun ByteArray.toHexString(): String {
    return joinToString("") { byte -> "%02x".format(byte) }
}
