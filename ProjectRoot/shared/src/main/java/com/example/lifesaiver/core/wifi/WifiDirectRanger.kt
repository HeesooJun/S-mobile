package com.example.lifesaiver.core.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.WpsInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.lifesaiver.core.log.ConnectionLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors

class WifiDirectRanger(private val context: Context) {

    private val _isConnectionReady = MutableStateFlow(false)
    val isConnectionReady = _isConnectionReady.asStateFlow()

    private val _debugStats = MutableStateFlow(TransportDebugStats(name = "Wi-Fi Direct"))
    val debugStats = _debugStats.asStateFlow()

    var onAudioDataReceived: ((ByteArray) -> Unit)? = null

    private val port = 50001
    private val maxFrameSize = 64 * 1024
    private val helloByte: Byte = 0x00
    private val maxUdpPayload = 1200

    private val manager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel: WifiP2pManager.Channel? =
        manager?.initialize(context, Looper.getMainLooper(), null)
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val ioExecutor = Executors.newSingleThreadExecutor()
    private var socket: DatagramSocket? = null
    private var peerAddress: InetAddress? = null
    private var peerPort: Int? = null
    private var isSocketBound = false
    private var groupInterfaceName: String? = null
    private var lastBoundInterface: String? = null
    private var bindRetryRunnable: Runnable? = null
    private var bindRetryCount = 0

    private var receiverRegistered = false
    private var isStarting = false
    private var isConnecting = false
    private var currentPeer: WifiP2pDevice? = null
    private var connectionInfo: WifiP2pInfo? = null
    private var isClient = true
    private var desiredActive = false
    private var targetDeviceAddress: String? = null
    private var lockToFirstPeer = false
    private var localDeviceAddress: String? = null
    private val invalidDeviceAddresses = setOf(
        "02:00:00:00:00:00",
        "00:00:00:00:00:00",
        "ff:ff:ff:ff:ff:ff"
    )
    private val handler = Handler(Looper.getMainLooper())
    private var connectTimeoutRunnable: Runnable? = null
    private val connectTimeoutMs = 15_000L
    private var lastStartAt = 0L
    private val startCooldownMs = 1_500L
    private var retryRunnable: Runnable? = null
    private var retryCount = 0
    private val baseRetryDelayMs = 1_000L
    private val maxRetryDelayMs = 10_000L
    private var lastNetworkFailureAt = 0L
    private val networkFailureCooldownMs = 3_000L
    private val busyRemoveGroupDelayMs = 500L
    private val busyRecoveryDelayMs = 2_500L
    private var connectionInfoRunnable: Runnable? = null
    private val connectionInfoIntervalMs = 2_000L
    private var lastNetSendLogAt = 0L
    private var lastNetRecvLogAt = 0L
    private val netLogIntervalMs = 1_000L
    private val helloIntervalMs = 500L
    private var helloLoopRunnable: Runnable? = null
    private var hasReceivedPeerPacket = false
    private val maxBindRetryCount = 6
    private val bindRetryDelayMs = 800L

    fun start(isClient: Boolean = true) {
        desiredActive = true
        val now = System.currentTimeMillis()
        if (isStarting || _isConnectionReady.value) return
        if (now - lastStartAt < startCooldownMs) return
        lastStartAt = now
        this.isClient = isClient
        refreshLocalDeviceAddress()
        ConnectionLog.add("Direct", "start requested")
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT)) {
            Log.e("WifiDirect", "Wi-Fi Direct not supported on this device.")
            ConnectionLog.add("Direct", "not supported")
            return
        }
        if (!checkPermissions()) {
            Log.e("WifiDirect", "Missing permissions.")
            ConnectionLog.add("Direct", "missing permissions")
            return
        }
        if (manager == null || channel == null) {
            Log.e("WifiDirect", "WifiP2pManager not available.")
            ConnectionLog.add("Direct", "manager/channel unavailable")
            return
        }
        registerReceiverIfNeeded()
        scheduleConnectionInfoPoll()
        isStarting = true
        if (this.isClient) {
            if (!targetDeviceAddress.isNullOrBlank()) {
                isStarting = false
                requestPeers()
                scheduleConnectionInfoPoll()
                return
            }
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d("WifiDirect", "Peer discovery started.")
                    ConnectionLog.add("Direct", "discoverPeers started")
                    isStarting = false
                    schedulePeerProbe()
                    scheduleConnectionInfoPoll()
                }

                override fun onFailure(reason: Int) {
                    Log.e("WifiDirect", "Peer discovery failed: $reason")
                    ConnectionLog.add("Direct", "discoverPeers failed: $reason")
                    isStarting = false
                    scheduleRetry("discoverPeers failed: $reason")
                }
            })
        } else {
            manager.requestGroupInfo(channel) { info ->
                if (info != null) {
                    Log.d("WifiDirect", "Group already exists, skip createGroup.")
                    ConnectionLog.add("Direct", "group exists -> skip createGroup")
                    isStarting = false
                    requestGroupInfoLog("start.groupExists", bindSocketIfPossible = true)
                    startGroupOwnerSocket()
                    manager.discoverPeers(channel, null)
                    scheduleConnectionInfoPoll()
                } else {
                    manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            Log.d("WifiDirect", "Group creation requested (server).")
                            ConnectionLog.add("Direct", "createGroup requested")
                            isStarting = false
                            requestGroupInfoLog("start.createGroup", bindSocketIfPossible = true)
                            startGroupOwnerSocket()
                            manager.discoverPeers(channel, null)
                            scheduleConnectionInfoPoll()
                        }

                        override fun onFailure(reason: Int) {
                            Log.e("WifiDirect", "Create group failed: $reason")
                            ConnectionLog.add("Direct", "createGroup failed: $reason")
                            if (reason == WifiP2pManager.BUSY) {
                                handleGroupCreationBusy(manager, channel)
                            }
                            isStarting = false
                            if (reason != WifiP2pManager.BUSY) {
                                scheduleRetry("createGroup failed: $reason")
                            }
                        }
                    })
                }
            }
        }
    }

    private fun handleGroupCreationBusy(manager: WifiP2pManager, channel: WifiP2pManager.Channel) {
        if (!desiredActive) return
        ConnectionLog.add("Direct", "createGroup BUSY -> stop discovery/removeGroup")
        isStarting = false
        isConnecting = false
        _isConnectionReady.value = false
        _debugStats.update { it.copy(isReady = false) }
        closeSocket()
        manager.stopPeerDiscovery(channel, null)
        handler.postDelayed({
            if (!desiredActive) return@postDelayed
            manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    ConnectionLog.add("Direct", "removeGroup after BUSY success")
                    handler.postDelayed({
                        if (!desiredActive) return@postDelayed
                        start(isClient)
                    }, busyRecoveryDelayMs)
                }

                override fun onFailure(reason: Int) {
                    ConnectionLog.add("Direct", "removeGroup after BUSY failed: $reason")
                    scheduleRetry("removeGroup after BUSY failed: $reason")
                }
            })
        }, busyRemoveGroupDelayMs)
    }

    fun stop() {
        desiredActive = false
        cancelRetry()
        cancelConnectionInfoPoll()
        isStarting = false
        isConnecting = false
        currentPeer = null
        connectionInfo = null
        _isConnectionReady.value = false
        _debugStats.update { it.copy(isReady = false) }
        connectTimeoutRunnable?.let { handler.removeCallbacks(it) }
        connectTimeoutRunnable = null
        closeSocket()
        unregisterReceiverIfNeeded()
        manager?.removeGroup(channel, null)
        manager?.stopPeerDiscovery(channel, null)
        Log.d("WifiDirect", "Stopped.")
        ConnectionLog.add("Direct", "stopped")
    }

    fun setTargetDeviceAddress(address: String?) {
        val normalized = normalizeDeviceAddress(address)
        targetDeviceAddress = if (isUsableDeviceAddress(normalized)) normalized else null
    }

    fun connectToAddress(address: String) {
        val normalized = normalizeDeviceAddress(address)
        if (!isUsableDeviceAddress(normalized)) return
        targetDeviceAddress = normalized
        if (!desiredActive) {
            start(isClient = true)
            return
        }
        if (!isClient) {
            isClient = true
        }
        requestPeers()
    }

    fun setLockToFirstPeer(enabled: Boolean) {
        lockToFirstPeer = enabled
    }

    fun getLocalDeviceAddress(): String? {
        if (!isUsableDeviceAddress(localDeviceAddress)) {
            refreshLocalDeviceAddress()
        }
        return localDeviceAddress
    }

    fun refreshLocalDeviceAddress() {
        val manager = manager
        val channel = channel
        if (manager != null &&
            channel != null &&
            checkPermissions() &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) {
            try {
                manager.requestDeviceInfo(channel) { device ->
                    val candidate = normalizeDeviceAddress(device?.deviceAddress)
                    if (isUsableDeviceAddress(candidate)) {
                        localDeviceAddress = candidate
                    } else if (candidate != null) {
                        Log.d("WifiDirectDiag", "ignore unusable local addr from requestDeviceInfo: $candidate")
                    }
                }
            } catch (_: Exception) {
            }
        }
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val wifiMac = normalizeDeviceAddress(wifiManager?.connectionInfo?.macAddress)
        if (isUsableDeviceAddress(wifiMac)) {
            localDeviceAddress = wifiMac
        }
    }

    fun sendAudio(data: ByteArray) {
        val currentSocket = socket ?: return
        if (!_isConnectionReady.value || data.isEmpty()) {
            return
        }
        val address = peerAddress
        val portOverride = peerPort
        try {
            if (address == null || portOverride == null) {
                _debugStats.update { stats ->
                    stats.copy(
                        sendFailCount = stats.sendFailCount + 1,
                        lastSendAt = System.currentTimeMillis(),
                        lastSendError = "no_peer"
                    )
                }
                return
            }
            if (currentSocket.isClosed) {
                _debugStats.update { stats ->
                    stats.copy(
                        sendFailCount = stats.sendFailCount + 1,
                        lastSendAt = System.currentTimeMillis(),
                        lastSendError = "socket_closed"
                    )
                }
                return
            }
            val targetAddress = address
            val targetPort = portOverride
            var offset = 0
            var chunks = 0
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
                        lastPeerAddress = address?.hostAddress ?: stats.lastPeerAddress
                    )
                }
                offset = end
            }
            maybeLogNetSend(data.size, chunks, targetAddress, targetPort, routeReady = true)
        } catch (e: Exception) {
            Log.e("WifiDirect", "Send error", e)
            _debugStats.update { stats ->
                stats.copy(
                    sendFailCount = stats.sendFailCount + 1,
                    lastSendAt = System.currentTimeMillis(),
                    lastSendError = e.message ?: "send error"
                )
            }
            val message = e.message.orEmpty()
            if (message.contains("ENETUNREACH", ignoreCase = true) ||
                message.contains("Network is unreachable", ignoreCase = true) ||
                message.contains("EHOSTUNREACH", ignoreCase = true)
            ) {
                handleNetworkFailure(message)
            }
        }
    }

    private fun registerReceiverIfNeeded() {
        if (receiverRegistered) return
        val filter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(wifiDirectReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(wifiDirectReceiver, filter)
        }
        receiverRegistered = true
    }

    private fun unregisterReceiverIfNeeded() {
        if (!receiverRegistered) return
        try {
            context.unregisterReceiver(wifiDirectReceiver)
        } catch (_: Exception) {
        }
        receiverRegistered = false
    }

    private val wifiDirectReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    Log.d("WifiDirect", "State changed: $state")
                    ConnectionLog.add("Direct", "state=$state")
                }
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    requestPeers()
                }
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    val networkInfo = intent.getParcelableExtra<NetworkInfo>(
                        WifiP2pManager.EXTRA_NETWORK_INFO
                    )
                    if (networkInfo?.isConnected == true) {
                        requestGroupInfoLog("connection changed", bindSocketIfPossible = true)
                        requestConnectionInfo()
                    } else {
                        Log.d("WifiDirect", "Connection lost.")
                        ConnectionLog.add("Direct", "connection lost")
                        _isConnectionReady.value = false
                        _debugStats.update { it.copy(isReady = false) }
                        closeSocket()
                        if (desiredActive) {
                            scheduleRetry("connection lost")
                        }
                    }
                }
            }
        }
    }

    private fun requestPeers() {
        val manager = manager ?: return
        val channel = channel ?: return
        manager.requestPeers(channel) { peers ->
            ConnectionLog.add("Direct", "peers=${peers.deviceList.size}")
            if (peers.deviceList.isEmpty()) {
                scheduleRetry("no peers")
                return@requestPeers
            }
            if (!isClient) return@requestPeers
            val target = targetDeviceAddress
            val candidate = if (!target.isNullOrBlank()) {
                peers.deviceList.firstOrNull { normalizeDeviceAddress(it.deviceAddress) == target }
            } else {
                peers.deviceList.firstOrNull { it.status == WifiP2pDevice.AVAILABLE }
                    ?: peers.deviceList.firstOrNull()
            } ?: return@requestPeers
            if (currentPeer?.deviceAddress == candidate.deviceAddress) return@requestPeers
            currentPeer = candidate
            connectToPeer(candidate)
        }
    }

    private fun connectToPeer(device: WifiP2pDevice) {
        if (isConnecting) return
        val manager = manager ?: return
        val channel = channel ?: return
        ConnectionLog.add("Direct", "connect to ${device.deviceName} (${device.deviceAddress})")
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
            groupOwnerIntent = if (isClient) 0 else 15
        }
        isConnecting = true
        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d("WifiDirect", "Connect requested to ${device.deviceName}")
                ConnectionLog.add("Direct", "connect requested")
                scheduleConnectTimeout()
            }

            override fun onFailure(reason: Int) {
                Log.e("WifiDirect", "Connect failed: $reason")
                ConnectionLog.add("Direct", "connect failed: $reason")
                isConnecting = false
                cancelConnectTimeout()
                scheduleRetry("connect failed: $reason")
            }
        })
    }

    private fun requestConnectionInfo() {
        val manager = manager ?: return
        val channel = channel ?: return
        manager.requestConnectionInfo(channel) { info ->
            connectionInfo = info
            ConnectionLog.add(
                "Direct",
                "connectionInfo formed=${info.groupFormed} owner=${info.isGroupOwner}"
            )
            if (!info.groupFormed) return@requestConnectionInfo
            isConnecting = false
            cancelConnectTimeout()
            resetRetry()
            cancelConnectionInfoPoll()
            requestGroupInfoLog("connectionInfo", bindSocketIfPossible = true)
            if (info.isGroupOwner) {
                startGroupOwnerSocket()
            } else {
                val owner = info.groupOwnerAddress ?: return@requestConnectionInfo
                startClientSocket(owner)
            }
        }
    }

    private fun startGroupOwnerSocket() {
        if (_isConnectionReady.value || socket != null) return
        ioExecutor.execute {
            try {
                val udpSocket = DatagramSocket(null)
                udpSocket.reuseAddress = true
                udpSocket.bind(InetSocketAddress(port))
                socket = udpSocket
                hasReceivedPeerPacket = false
                ensureSocketBound(udpSocket)
                _isConnectionReady.value = false
                _debugStats.update { it.copy(isReady = false) }
                Log.d("WifiDirect", "Group owner UDP socket ready. waiting for peer...")
                ConnectionLog.add("Direct", "group owner socket ready (waiting peer)")
                startReceiveLoop(udpSocket)
            } catch (e: Exception) {
                Log.e("WifiDirect", "Group owner socket error", e)
                ConnectionLog.add("Direct", "group owner socket error: ${e.message}")
                _debugStats.update { stats ->
                    stats.copy(
                        sendFailCount = stats.sendFailCount + 1,
                        lastSendAt = System.currentTimeMillis(),
                        lastSendError = e.message ?: "socket error"
                    )
                }
                scheduleRetry("group owner socket error")
            }
        }
    }

    private fun startClientSocket(owner: InetAddress) {
        if (_isConnectionReady.value || socket != null) return
        ioExecutor.execute {
            try {
                val udpSocket = DatagramSocket(null)
                udpSocket.reuseAddress = true
                udpSocket.bind(InetSocketAddress(port))
                ensureSocketBound(udpSocket)
                socket = udpSocket
                peerAddress = owner
                peerPort = port
                hasReceivedPeerPacket = false
                _isConnectionReady.value = true
                _debugStats.update { it.copy(isReady = true, lastPeerAddress = owner.hostAddress) }
                Log.d("WifiDirect", "Client UDP socket connected to $owner")
                ConnectionLog.add("Direct", "client socket connected to ${owner.hostAddress}")
                sendHello()
                startHelloLoop()
                startReceiveLoop(udpSocket)
            } catch (e: Exception) {
                Log.e("WifiDirect", "Client socket error", e)
                ConnectionLog.add("Direct", "client socket error: ${e.message}")
                _debugStats.update { stats ->
                    stats.copy(
                        sendFailCount = stats.sendFailCount + 1,
                        lastSendAt = System.currentTimeMillis(),
                        lastSendError = e.message ?: "client socket error"
                    )
                }
                scheduleRetry("client socket error")
            }
        }
    }

    private fun startReceiveLoop(udpSocket: DatagramSocket) {
        ioExecutor.execute {
            val buffer = ByteArray(maxFrameSize)
            try {
                while (!udpSocket.isClosed) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udpSocket.receive(packet)
                    resolvePeerFromPacket(packet)
                    val size = packet.length
                    if (size <= 0 || size > maxFrameSize) continue
                    maybeLogNetRecv(size, packet.address, packet.port)
                    val payload = packet.data.copyOfRange(packet.offset, packet.offset + size)
                    if (!hasReceivedPeerPacket) {
                        hasReceivedPeerPacket = true
                        cancelHelloLoop()
                        ConnectionLog.add("Direct", "peer packet received -> stop hello loop")
                    }
                    if (payload.size == 1 && payload[0] == helloByte) {
                        continue
                    }
                    _debugStats.update { stats ->
                        stats.copy(
                            recvCount = stats.recvCount + 1,
                            lastRecvAt = System.currentTimeMillis(),
                            lastRecvSize = size,
                            lastRecvError = null,
                            lastPeerAddress = packet.address?.hostAddress ?: stats.lastPeerAddress
                        )
                    }
                    onAudioDataReceived?.invoke(payload)
                }
            } catch (e: Exception) {
                if (!udpSocket.isClosed && _isConnectionReady.value) {
                    Log.e("WifiDirect", "Receive error", e)
                    ConnectionLog.add("Direct", "receive error: ${e.message}")
                    _debugStats.update { stats ->
                        stats.copy(
                            recvFailCount = stats.recvFailCount + 1,
                            lastRecvAt = System.currentTimeMillis(),
                            lastRecvError = e.message ?: "receive error"
                        )
                    }
                }
            } finally {
                _isConnectionReady.value = false
                _debugStats.update { it.copy(isReady = false) }
                closeSocket()
                if (desiredActive) {
                    scheduleRetry("receive loop ended")
                }
            }
        }
    }

    private fun resolvePeerFromPacket(packet: DatagramPacket) {
        if (lockToFirstPeer && peerAddress != null && peerAddress != packet.address) {
            return
        }
        val hasNewPeer = peerAddress == null ||
            peerPort == null ||
            peerAddress != packet.address ||
            peerPort != packet.port
        if (!hasNewPeer) return
        peerAddress = packet.address
        peerPort = packet.port
        Log.d(
            "WifiDirect",
            "Client IP discovered: ${peerAddress?.hostAddress}:${peerPort ?: -1}"
        )
        _debugStats.update { stats ->
            stats.copy(
                lastPeerAddress = peerAddress?.hostAddress ?: stats.lastPeerAddress
            )
        }
        if (!_isConnectionReady.value) {
            val currentSocket = socket
            if (currentSocket != null && !currentSocket.isClosed) {
                ensureSocketBound(currentSocket)
            }
            _isConnectionReady.value = true
            _debugStats.update { stats ->
                stats.copy(
                    isReady = true,
                    lastPeerAddress = packet.address?.hostAddress ?: stats.lastPeerAddress
                )
            }
            ConnectionLog.add("Direct", "peer resolved -> ready")
        }
    }

    private fun shouldLog(now: Long, last: Long): Boolean {
        return now - last >= netLogIntervalMs
    }

    private fun maybeLogNetSend(
        totalSize: Int,
        chunks: Int,
        address: InetAddress?,
        port: Int?,
        routeReady: Boolean
    ) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastNetSendLogAt)) return
        lastNetSendLogAt = now
        Log.d(
            "NetPipe",
            "Direct SEND total=$totalSize chunks=$chunks routeReady=$routeReady peer=${address?.hostAddress}:${port ?: -1}"
        )
    }

    private fun maybeLogNetRecv(size: Int, address: InetAddress?, port: Int?) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastNetRecvLogAt)) return
        lastNetRecvLogAt = now
        Log.d(
            "NetPipe",
            "Direct RECV size=$size peer=${address?.hostAddress}:${port ?: -1}"
        )
    }

    private fun closeSocket() {
        cancelHelloLoop()
        cancelBindRetry()
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        peerAddress = null
        peerPort = null
        hasReceivedPeerPacket = false
        isSocketBound = false
        lastBoundInterface = null
    }

    private fun ensureSocketBound(udpSocket: DatagramSocket) {
        if (isSocketBound) return
        val bound = bindSocketToP2pNetwork(udpSocket)
        if (!bound) {
            scheduleBindRetry(udpSocket)
        }
    }

    private fun bindSocketToP2pNetwork(udpSocket: DatagramSocket): Boolean {
        val cm = connectivityManager ?: return false
        val network = resolveP2pNetwork(cm, groupInterfaceName) ?: run {
            ConnectionLog.add("Direct", "p2p network not found iface=${groupInterfaceName ?: "unknown"}")
            return false
        }
        return try {
            network.bindSocket(udpSocket)
            isSocketBound = true
            val iface = cm.getLinkProperties(network)?.interfaceName
            lastBoundInterface = iface
            bindRetryCount = 0
            ConnectionLog.add(
                "Direct",
                "socket bound iface=${iface ?: "unknown"} target=${groupInterfaceName ?: "none"}"
            )
            true
        } catch (e: Exception) {
            ConnectionLog.add("Direct", "bindSocket failed: ${e.message}")
            false
        }
    }

    private fun resolveP2pNetwork(cm: ConnectivityManager, targetInterface: String?): Network? {
        val networks = cm.allNetworks
        val preferred = targetInterface?.trim()?.takeIf { it.isNotEmpty() }
        if (preferred != null) {
            for (network in networks) {
                val iface = cm.getLinkProperties(network)?.interfaceName
                if (iface.equals(preferred, ignoreCase = true)) {
                    return network
                }
            }
        }
        for (network in networks) {
            val caps = cm.getNetworkCapabilities(network) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_WIFI_P2P)
            ) {
                return network
            }
        }
        for (network in networks) {
            val iface = cm.getLinkProperties(network)?.interfaceName ?: continue
            if (iface.contains("p2p", ignoreCase = true)) return network
            if (iface.startsWith("wlan", ignoreCase = true)) return network
            if (iface.startsWith("swlan", ignoreCase = true)) return network
        }
        return null
    }

    private fun scheduleBindRetry(udpSocket: DatagramSocket) {
        if (!desiredActive) return
        if (bindRetryCount >= maxBindRetryCount) return
        cancelBindRetry()
        val runnable = Runnable {
            if (!desiredActive || udpSocket.isClosed) return@Runnable
            bindRetryCount++
            val bound = bindSocketToP2pNetwork(udpSocket)
            if (!bound) {
                scheduleBindRetry(udpSocket)
            }
        }
        bindRetryRunnable = runnable
        handler.postDelayed(runnable, bindRetryDelayMs)
    }

    private fun cancelBindRetry() {
        bindRetryRunnable?.let { handler.removeCallbacks(it) }
        bindRetryRunnable = null
    }

    private fun scheduleConnectTimeout() {
        connectTimeoutRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (_isConnectionReady.value || connectionInfo?.groupFormed == true) return@Runnable
            ConnectionLog.add("Direct", "connect timeout -> cancel/retry")
            val manager = manager ?: return@Runnable
            val channel = channel ?: return@Runnable
            isConnecting = false
            manager.cancelConnect(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    ConnectionLog.add("Direct", "cancelConnect success")
                }

                override fun onFailure(reason: Int) {
                    ConnectionLog.add("Direct", "cancelConnect failed: $reason")
                }
            })
            scheduleRetry("connect timeout")
        }
        connectTimeoutRunnable = runnable
        handler.postDelayed(runnable, connectTimeoutMs)
    }

    private fun cancelConnectTimeout() {
        connectTimeoutRunnable?.let { handler.removeCallbacks(it) }
        connectTimeoutRunnable = null
    }

    private fun sendHello() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            ioExecutor.execute { sendHelloInternal() }
            return
        }
        sendHelloInternal()
    }

    private fun sendHelloInternal() {
        val currentSocket = socket ?: return
        try {
            val targetAddress = peerAddress ?: return
            val targetPort = peerPort ?: port
            val payload = byteArrayOf(helloByte)
            val packet = DatagramPacket(payload, payload.size, targetAddress, targetPort)
            currentSocket.send(packet)
        } catch (e: Exception) {
            Log.e("WifiDirect", "Hello send error", e)
        }
    }

    private fun startHelloLoop() {
        if (!isClient) return
        cancelHelloLoop()
        val runnable = object : Runnable {
            override fun run() {
                if (!desiredActive || !isClient || hasReceivedPeerPacket) {
                    cancelHelloLoop()
                    return
                }
                val currentSocket = socket
                if (currentSocket != null &&
                    !currentSocket.isClosed &&
                    peerAddress != null &&
                    peerPort != null
                ) {
                    sendHello()
                }
                handler.postDelayed(this, helloIntervalMs)
            }
        }
        helloLoopRunnable = runnable
        handler.post(runnable)
    }

    private fun cancelHelloLoop() {
        helloLoopRunnable?.let { handler.removeCallbacks(it) }
        helloLoopRunnable = null
    }

    private fun handleNetworkFailure(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastNetworkFailureAt < networkFailureCooldownMs) return
        lastNetworkFailureAt = now
        ConnectionLog.add("Direct", "network failure -> disconnect ($reason)")
        _isConnectionReady.value = false
        _debugStats.update { it.copy(isReady = false, lastSendError = reason) }
        closeSocket()
        if (desiredActive) {
            scheduleRetry("network unreachable")
        }
    }

    private fun schedulePeerProbe() {
        if (!desiredActive || !isClient) return
        handler.postDelayed({ requestPeers() }, 500L)
    }

    private fun scheduleConnectionInfoPoll() {
        if (!desiredActive) return
        cancelConnectionInfoPoll()
        val runnable = object : Runnable {
            override fun run() {
                if (!desiredActive || _isConnectionReady.value) return
                requestGroupInfoLog("poll", bindSocketIfPossible = true)
                requestConnectionInfo()
                handler.postDelayed(this, connectionInfoIntervalMs)
            }
        }
        connectionInfoRunnable = runnable
        handler.postDelayed(runnable, connectionInfoIntervalMs)
    }

    private fun cancelConnectionInfoPoll() {
        connectionInfoRunnable?.let { handler.removeCallbacks(it) }
        connectionInfoRunnable = null
    }

    private fun scheduleRetry(reason: String) {
        if (!desiredActive) return
        if (isStarting || isConnecting) return
        cancelRetry()
        val shift = retryCount.coerceAtMost(4)
        val computed = baseRetryDelayMs * (1 shl shift)
        val delay = maxOf(computed.coerceAtMost(maxRetryDelayMs), startCooldownMs)
        retryCount++
        ConnectionLog.add("Direct", "retry in ${delay}ms ($reason)")
        retryRunnable = Runnable {
            if (!desiredActive) return@Runnable
            start(isClient)
        }
        handler.postDelayed(retryRunnable!!, delay)
    }

    private fun resetRetry() {
        retryCount = 0
        cancelRetry()
    }

    private fun cancelRetry() {
        retryRunnable?.let { handler.removeCallbacks(it) }
        retryRunnable = null
    }

    private fun requestGroupInfoLog(prefix: String, bindSocketIfPossible: Boolean = false) {
        val manager = manager ?: return
        val channel = channel ?: return
        manager.requestGroupInfo(channel) { group ->
            if (group == null) return@requestGroupInfo
            updateGroupInterfaceName(group.getInterface(), "$prefix.groupInfo")
            if (bindSocketIfPossible) {
                socket?.let { currentSocket ->
                    if (!currentSocket.isClosed) {
                        if (isSocketBound && !lastBoundInterface.equals(groupInterfaceName, ignoreCase = true)) {
                            isSocketBound = false
                        }
                        ensureSocketBound(currentSocket)
                    }
                }
            }
        }
    }

    private fun updateGroupInterfaceName(rawInterfaceName: String?, source: String) {
        val normalized = rawInterfaceName?.trim()?.takeIf { it.isNotEmpty() } ?: return
        if (groupInterfaceName == normalized) return
        groupInterfaceName = normalized
        ConnectionLog.add("Direct", "group iface=$normalized ($source)")
    }

    private fun normalizeDeviceAddress(raw: String?): String? {
        return raw?.trim()?.lowercase()?.ifBlank { null }
    }

    private fun isUsableDeviceAddress(address: String?): Boolean {
        val normalized = normalizeDeviceAddress(address) ?: return false
        if (normalized in invalidDeviceAddresses) return false
        return Regex("^([0-9a-f]{2}:){5}[0-9a-f]{2}$").matches(normalized)
    }

    private fun checkPermissions(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearbyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            if (!nearbyGranted) return false
        }
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) return false
        return true
    }
}
