package com.example.lifesaiver.core.model

data class ChatMessage(
    val text: String,
    val isMine: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val path: String? = null,
    val senderName: String? = null,
    val senderPeerId: String? = null,
    val recipientPeerId: String? = null
)
