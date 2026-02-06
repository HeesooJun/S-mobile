package com.example.lifesaivior.protocol.pipeline

import com.example.lifesaivior.protocol.model.Packet

data class InboundResult(
    val packetForApp: Packet?,
    val packetForRelay: Packet?
)
