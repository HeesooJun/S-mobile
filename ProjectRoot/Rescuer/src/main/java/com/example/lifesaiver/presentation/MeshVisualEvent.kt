package com.example.lifesaiver.presentation

sealed class MeshVisualEvent {
    data class PacketActivity(val peerId: String) : MeshVisualEvent()
}

