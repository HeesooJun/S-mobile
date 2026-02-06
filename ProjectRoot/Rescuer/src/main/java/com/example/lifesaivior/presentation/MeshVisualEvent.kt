package com.example.lifesaivior.presentation

sealed class MeshVisualEvent {
    data class PacketActivity(val peerId: String) : MeshVisualEvent()
}

