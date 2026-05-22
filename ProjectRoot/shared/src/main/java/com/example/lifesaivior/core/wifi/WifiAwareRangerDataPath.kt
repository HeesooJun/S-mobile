package com.example.lifesaivior.core.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.DiscoverySession
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareNetworkInfo
import android.net.wifi.aware.WifiAwareNetworkSpecifier
import android.util.Log
import com.example.lifesaivior.core.log.ConnectionLog
import kotlinx.coroutines.flow.update
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress

internal fun WifiAwareRanger.connectToCurrentPeer() {
    if (!isOnMainThread()) {
        runOnMain { connectToCurrentPeer() }
        return
    }
    // NDP 진행 상태(initiator/응답자)에 따라 연결 게이트
    val canConnect = when {
        isNdpInitiator && requiresAckBeforeConnect() -> ndpAckReceived
        isNdpInitiator -> true
        else -> peerRequestedNdp
    }
    if (!canConnect) {
        val reason = if (isNdpInitiator) "waiting ndp ack" else "waiting initiator request"
        ConnectionLog.add("Aware", "connect skipped: $reason")
        return
    }
    val connectSession: DiscoverySession = if (isNdpInitiator) {
        val subscribe = subscribeSession ?: run {
            ConnectionLog.add("Aware", "connect skipped: no subscribe session")
            scheduleFallbackConnect("no subscribe session")
            return
        }
        if (subscribe is PublishDiscoverySession) {
            ConnectionLog.add("Aware", "publish session selected by mistake -> fallback to subscribe")
            scheduleFallbackConnect("invalid initiator session")
            return
        }
        subscribe
    } else {
        val publish = publishSession ?: run {
            ConnectionLog.add("Aware", "connect skipped: no publish session")
            scheduleFallbackConnect("no publish session")
            return
        }
        if (publish is SubscribeDiscoverySession) {
            ConnectionLog.add("Aware", "subscribe session selected by mistake -> fallback to publish")
            scheduleFallbackConnect("invalid responder session")
            return
        }
        publish
    }
    val peer = currentTargetPeer ?: return

    isConnecting = true
    Log.d("WifiAware", "Connecting to peer=$peer initiator=$isNdpInitiator")
    ConnectionLog.add(
        "Aware",
        "connect to peer initiator=$isNdpInitiator session=${connectSession.javaClass.simpleName}"
    )

    // NetworkSpecifier 생성
    // NOTE: transport/port hints are only valid on publisher(server) side.
    val networkSpecifier = try {
        WifiAwareNetworkSpecifier.Builder(connectSession, peer).build()
    } catch (e: IllegalStateException) {
        Log.e("WifiAware", "Network specifier build failed", e)
        ConnectionLog.add("Aware", "specifier build failed: ${e.message}")
        isConnecting = false
        scheduleFallbackConnect("specifier build failed")
        return
    }

    // NetworkRequest 생성
    val networkRequest = NetworkRequest.Builder()
        .addTransportType(NetworkCapabilities.TRANSPORT_WIFI_AWARE)
        .setNetworkSpecifier(networkSpecifier)
        .build()

    connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    if (networkCallback != null) {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback!!)
        } catch (_: Exception) {
        }
        networkCallback = null
    }

    networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            runOnMain {
                Log.d("WifiAware", "NDP Network Available!")
                ConnectionLog.add("Aware", "NDP network available")
                ndpUnavailableCount = 0
                pendingConnectRunnable?.let { handler.removeCallbacks(it) }
                pendingConnectRunnable = null
                try {
                    // 소켓 생성 및 네트워크 바인딩
                    val udpSocket = DatagramSocket(null)
                    udpSocket.reuseAddress = true
                    udpSocket.bind(InetSocketAddress(socketPort))
                    network.bindSocket(udpSocket)
                    socket = udpSocket

                    // 상대방 IP 확인 (선택 사항)
                    val cap = connectivityManager?.getNetworkCapabilities(network)
                    val info = cap?.transportInfo as? WifiAwareNetworkInfo
                    val peerIp = info?.peerIpv6Addr
                    if (peerIp != null) {
                        peerAddress = peerIp
                        peerPort = socketPort
                    }
                    Log.d("WifiAware", "Peer IPv6: $peerIp")

                    // 연결 완료 -> 오디오 수신 루프 시작
                    rttWarmupUntilMs = System.currentTimeMillis() + ndpRttWarmupMs
                    ConnectionLog.add("Aware", "RTT warmup ${ndpRttWarmupMs}ms after NDP up")
                    _isConnectionReady.value = true
                    _debugStats.update {
                        it.copy(
                            isReady = true,
                            lastPeerAddress = peerIp?.hostAddress
                        )
                    }
                    startReceiveLoop()
                    isConnecting = false

                } catch (e: Exception) {
                    Log.e("WifiAware", "Socket setup failed", e)
                    _debugStats.update { stats ->
                        stats.copy(
                            sendFailCount = stats.sendFailCount + 1,
                            lastSendAt = System.currentTimeMillis(),
                            lastSendError = e.message ?: "socket setup failed"
                        )
                    }
                    isConnecting = false
                }
            }
        }

        override fun onLost(network: Network) {
            runOnMain {
                Log.d("WifiAware", "NDP Network Lost")
                ConnectionLog.add("Aware", "NDP network lost")
                // 연결 소실 시 상태 정리 후 fallback 연결
                _isConnectionReady.value = false
                _debugStats.update { it.copy(isReady = false) }
                isConnecting = false
                rttWarmupUntilMs = 0L
                closeSocket()
                scheduleFallbackConnect("network lost")
            }
        }

        override fun onUnavailable() {
            runOnMain {
                Log.e("WifiAware", "NDP Network Unavailable")
                ConnectionLog.add("Aware", "NDP unavailable")
                // 실패 횟수 누적: 일정 이상이면 세션 재시작
                ndpUnavailableCount += 1
                _debugStats.update { stats ->
                    stats.copy(
                        isReady = false,
                        lastSendAt = System.currentTimeMillis(),
                        lastSendError = "ndp_unavailable"
                    )
                }
                isConnecting = false
                rttWarmupUntilMs = 0L
                if (ndpUnavailableCount >= 3) {
                    scheduleAwareSessionRestart("ndp unavailable x$ndpUnavailableCount")
                } else {
                    scheduleFallbackConnect("ndp unavailable")
                }
            }
        }
    }

    try {
        ConnectionLog.add("Aware", "NDP request issued")
        connectivityManager?.requestNetwork(networkRequest, networkCallback!!, ndpRequestTimeoutMs)
    } catch (e: SecurityException) {
        Log.e("WifiAware", "requestNetwork permission error", e)
        ConnectionLog.add("Aware", "requestNetwork permission error")
        isConnecting = false
        scheduleFallbackConnect("requestNetwork permission")
    } catch (e: Exception) {
        Log.e("WifiAware", "requestNetwork failed", e)
        ConnectionLog.add("Aware", "requestNetwork failed: ${e.message}")
        isConnecting = false
        scheduleFallbackConnect("requestNetwork failed")
    }
}

internal fun WifiAwareRanger.startReceiveLoop() {
    // UDP 수신은 블로킹이므로 별도 스레드에서 처리
    receiveJob = udpReceiveExecutor.submit {
        val buffer = ByteArray(4096) // 버퍼 사이즈
        while (_isConnectionReady.value && socket != null && !socket!!.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket?.receive(packet) // 블로킹 대기
                if (!isExpectedSender(packet)) {
                    continue
                }
                updatePeerFromPacket(packet)

                // 유효 데이터만 잘라서 전달
                val end = packet.offset + packet.length
                val receivedData = packet.data.copyOfRange(packet.offset, end)
                maybeLogNetRecv(packet.length, packet.address, packet.port)
                _debugStats.update { stats ->
                    stats.copy(
                        recvCount = stats.recvCount + 1,
                        lastRecvAt = System.currentTimeMillis(),
                        lastRecvSize = packet.length,
                        lastRecvError = null,
                        lastPeerAddress = packet.address?.hostAddress ?: stats.lastPeerAddress
                    )
                }
                onAudioDataReceived?.invoke(receivedData)

            } catch (e: Exception) {
                if (_isConnectionReady.value) {
                    Log.e("WifiAware", "Receive error", e)
                    ConnectionLog.add("Aware", "receive error: ${e.message}")
                    _debugStats.update { stats ->
                        stats.copy(
                            recvFailCount = stats.recvFailCount + 1,
                            lastRecvAt = System.currentTimeMillis(),
                            lastRecvError = e.message ?: "receive error"
                        )
                    }
                }
            }
        }
    }
}

internal fun WifiAwareRanger.sendAudioInternal(data: ByteArray) {
    val currentSocket = socket
    val targetAddress = peerAddress
    val targetPort = peerPort ?: socketPort
    if (!_isConnectionReady.value || currentSocket == null || targetAddress == null) {
        _debugStats.update { stats ->
            stats.copy(
                sendFailCount = stats.sendFailCount + 1,
                lastSendAt = System.currentTimeMillis(),
                lastSendError = "not_ready"
            )
        }
        return
    }

    try {
        var offset = 0
        var chunks = 0
        // MTU 초과 방지를 위해 chunk로 분할 전송
        while (offset < data.size) {
            val end = minOf(offset + maxUdpPayload, data.size)
            val chunk =
                if (offset == 0 && end == data.size) data else data.copyOfRange(offset, end)
            val packet = DatagramPacket(chunk, chunk.size, targetAddress, targetPort)
            currentSocket.send(packet)
            chunks++
            _debugStats.update { stats ->
                stats.copy(
                    sendCount = stats.sendCount + 1,
                    lastSendAt = System.currentTimeMillis(),
                    lastSendSize = chunk.size,
                    lastSendError = null,
                    lastPeerAddress = targetAddress.hostAddress ?: stats.lastPeerAddress
                )
            }
            offset = end
        }
        maybeLogNetSend(data.size, chunks, targetAddress, targetPort)
    } catch (e: Exception) {
        Log.e("WifiAware", "Send error", e)
        _debugStats.update { stats ->
            stats.copy(
                sendFailCount = stats.sendFailCount + 1,
                lastSendAt = System.currentTimeMillis(),
                lastSendError = e.message ?: "send error"
            )
        }
    }
}

internal fun WifiAwareRanger.isExpectedSender(packet: DatagramPacket): Boolean {
    val expected = peerAddress ?: return true
    return packet.address == expected
}

internal fun WifiAwareRanger.updatePeerFromPacket(packet: DatagramPacket) {
    // 최초 수신 시 상대 주소/포트 보정
    val senderAddress = packet.address ?: return
    val senderPort = packet.port
    val hadPeer = peerAddress != null
    if (!hadPeer) {
        peerAddress = senderAddress
        peerPort = senderPort
    } else if (peerAddress == senderAddress && peerPort != senderPort) {
        peerPort = senderPort
        ConnectionLog.add("Aware", "peer port updated=$senderPort")
    }
    _debugStats.update { stats ->
        stats.copy(lastPeerAddress = senderAddress.hostAddress ?: stats.lastPeerAddress)
    }
}

internal fun WifiAwareRanger.shouldLog(now: Long, last: Long): Boolean {
    return now - last >= netLogIntervalMs
}

internal fun WifiAwareRanger.maybeLogNetSend(
    totalSize: Int,
    chunks: Int,
    address: InetAddress?,
    port: Int
) {
    val now = System.currentTimeMillis()
    if (!shouldLog(now, lastNetSendLogAt)) return
    lastNetSendLogAt = now
    Log.d(
        "NetPipe",
        "Aware SEND total=$totalSize chunks=$chunks peer=${address?.hostAddress}:$port"
    )
}

internal fun WifiAwareRanger.maybeLogNetRecv(size: Int, address: InetAddress?, port: Int) {
    val now = System.currentTimeMillis()
    if (!shouldLog(now, lastNetRecvLogAt)) return
    lastNetRecvLogAt = now
    Log.d(
        "NetPipe",
        "Aware RECV size=$size peer=${address?.hostAddress}:$port"
    )
}
