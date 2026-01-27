package com.example.rescuer.protocol.util

fun ByteArray.toHexString(): String {
    return joinToString("") { byte -> "%02x".format(byte) }
}
