package com.example.lifesaiver.protocol.pipeline

import com.example.lifesaiver.protocol.model.Packet

data class InboundResult(
    val packetForApp: Packet?,
    val packetForRelay: Packet?
)
