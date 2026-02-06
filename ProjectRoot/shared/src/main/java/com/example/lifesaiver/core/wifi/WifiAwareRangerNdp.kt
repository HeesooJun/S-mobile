package com.example.lifesaiver.core.wifi

import android.net.wifi.aware.PeerHandle
import com.example.lifesaiver.core.log.ConnectionLog

internal fun WifiAwareRanger.sendNdpRequestToPeer(peerHandle: PeerHandle) {
    if (!isNdpInitiator) return
    val subscribe = subscribeSession ?: return
    val messageId = nextMessageId++
    // NDP 요청 메시지: "NDP_REQ|localPeerId|targetPeerId"
    val payload = buildNdpRequestPayload()
    try {
        subscribe.sendMessage(peerHandle, messageId, payload)
        ConnectionLog.add("Aware", "send ndp request id=$messageId")
    } catch (e: Exception) {
        ConnectionLog.add("Aware", "send ndp request failed: ${e.message}")
    }
}

internal fun WifiAwareRanger.trySendNdpRequestToKnownPeers(reason: String) {
    if (!isOnMainThread()) {
        runOnMain { trySendNdpRequestToKnownPeers(reason) }
        return
    }
    // initiator + 데이터 경로 사용 조건일 때만 브로드캐스트
    if (!isNdpInitiator) return
    if (!shouldUseDataPathForCall()) return
    if (ndpAckReceived || isConnecting || _isConnectionReady.value) return
    val peers = buildList {
        currentTargetPeer?.let { add(it) }
        if (isEmpty()) {
            addAll(foundPeers)
        }
    }
    if (peers.isEmpty()) {
        ConnectionLog.add("Aware", "ndp request skipped (no peers) reason=$reason")
        return
    }
    val now = System.currentTimeMillis()
    // 과도한 요청 방지
    if (now - lastNdpRequestBroadcastAtMs < 500L) return
    lastNdpRequestBroadcastAtMs = now
    ConnectionLog.add("Aware", "send ndp request to ${peers.size} peers ($reason)")
    peers.forEach { sendNdpRequestToPeer(it) }
}

internal fun WifiAwareRanger.sendNdpAckToPeer(peerHandle: PeerHandle, targetPeerId: String) {
    if (isNdpInitiator) return
    val publish = publishSession ?: return
    val messageId = nextMessageId++
    // NDP ACK 메시지: "NDP_ACK|localPeerId|targetPeerId"
    val payload = buildNdpAckPayload(targetPeerId)
    try {
        publish.sendMessage(peerHandle, messageId, payload)
        ConnectionLog.add("Aware", "send ndp ack id=$messageId")
    } catch (e: Exception) {
        ConnectionLog.add("Aware", "send ndp ack failed: ${e.message}")
    }
}

internal fun WifiAwareRanger.requiresAckBeforeConnect(): Boolean {
    return !localPeerIdForCall.isNullOrBlank() && !expectedPeerIdForCall.isNullOrBlank()
}

internal fun WifiAwareRanger.shouldUseDataPathForCall(): Boolean {
    return dataPathEnabled && requiresAckBeforeConnect()
}

internal fun WifiAwareRanger.buildNdpRequestPayload(): ByteArray {
    val local = localPeerIdForCall
    val target = expectedPeerIdForCall
    // peer id가 없으면 최소 메시지로 전송
    val message = if (!local.isNullOrBlank() && !target.isNullOrBlank()) {
        "$ndpRequestMessage|$local|$target"
    } else {
        ndpRequestMessage
    }
    return message.toByteArray(Charsets.US_ASCII)
}

internal fun WifiAwareRanger.buildNdpAckPayload(targetPeerId: String): ByteArray {
    val local = localPeerIdForCall
    val target = normalizePeerId(targetPeerId)
    // 응답 대상이 명확할 때만 peer id 포함
    val message = if (!local.isNullOrBlank() && !target.isNullOrBlank()) {
        "$ndpAckMessage|$local|$target"
    } else {
        ndpAckMessage
    }
    return message.toByteArray(Charsets.US_ASCII)
}

internal data class NdpSignal(
    val type: String,
    val senderPeerId: String,
    val targetPeerId: String
)

internal fun WifiAwareRanger.parseNdpSignal(message: ByteArray): NdpSignal? {
    val text = try {
        message.toString(Charsets.US_ASCII)
    } catch (_: Exception) {
        return null
    }
    // 포맷: TYPE|SENDER|TARGET
    if (text.isBlank()) return null
    val parts = text.split('|')
    val type = parts.firstOrNull() ?: return null
    val sender = normalizePeerId(parts.getOrNull(1)).orEmpty()
    val target = normalizePeerId(parts.getOrNull(2)).orEmpty()
    return NdpSignal(type = type, senderPeerId = sender, targetPeerId = target)
}

internal fun WifiAwareRanger.isExpectedRequestSignal(signal: NdpSignal): Boolean {
    if (signal.type != ndpRequestMessage) return false
    if (!requiresAckBeforeConnect()) return true
    val local = localPeerIdForCall ?: return false
    val expected = expectedPeerIdForCall ?: return false
    return signal.senderPeerId == expected && signal.targetPeerId == local
}

internal fun WifiAwareRanger.isExpectedAckSignal(signal: NdpSignal): Boolean {
    if (signal.type != ndpAckMessage) return false
    if (!requiresAckBeforeConnect()) return true
    val local = localPeerIdForCall ?: return false
    val expected = expectedPeerIdForCall ?: return false
    return signal.senderPeerId == expected && signal.targetPeerId == local
}

internal fun WifiAwareRanger.normalizePeerId(peerId: String?): String? {
    val normalized = peerId?.trim()?.lowercase()
    return normalized?.takeIf { it.isNotBlank() }
}
