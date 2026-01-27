package com.example.rescuer.protocol.pipeline

import com.example.rescuer.protocol.model.Packet

data class InboundResult(
    val packetForApp: Packet?,
    val packetForRelay: Packet?
)
