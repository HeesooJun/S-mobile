package com.example.lifesaiver.core.wifi

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.aware.*
import android.net.wifi.rtt.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.Manifest
import android.os.Build
import android.location.LocationManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import com.example.lifesaiver.core.log.ConnectionLog
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.collections.copyOfRange
import kotlin.collections.firstOrNull

/**
 * [Wi-Fi Aware 통합 매니저]
 * 역할 1: RTT 거리 측정 (Ranging)
 * 역할 2: 50m 진입 시 NDP(데이터 경로) 연결
 * 역할 3: UDP 소켓을 통한 오디오 데이터 송수신
 * * [수정 사항]: 외부(ViewModel)에서 상대방의 지원 여부를 알려줘야만 동작하도록 변경
 */
class WifiAwareRanger(private val context: Context) {
    private val awareDisabledForDirectTest = false

    // --- StateFlows ---
    // 거리 정보 (Meter 단위)
    private val _rttDistance = MutableStateFlow<Float?>(null)
    val rttDistance = _rttDistance.asStateFlow()

    // 고속 연결 상태 (데이터 통로 준비 완료 여부)
    private val _isConnectionReady = MutableStateFlow(false)
    val isConnectionReady = _isConnectionReady.asStateFlow()

    private val _debugStats = MutableStateFlow(TransportDebugStats(name = "Wi-Fi Aware"))
    val debugStats = _debugStats.asStateFlow()

    // 오디오 수신 콜백 (PCM 데이터 -> AppViewModel -> AudioEngine)
    var onAudioDataReceived: ((ByteArray) -> Unit)? = null

    // --- 내부 변수 ---
    private var subscribeSession: SubscribeDiscoverySession? = null
    private var publishSession: PublishDiscoverySession? = null
    private var socket: DatagramSocket? = null
    private var peerAddress: InetAddress? = null
    private var peerPort: Int? = null
    private val socketPort = 50000
    private val connectDistanceMeters = 30.0
    private val maxUdpPayload = 1200
    private var lastNetSendLogAt = 0L
    private var lastNetRecvLogAt = 0L
    private val netLogIntervalMs = 1_000L

    // 타겟 피어 핸들 (거리 측정 및 연결 대상)
    private var currentTargetPeer: PeerHandle? = null

    // [New] 상대방 기능 지원 여부 (기본값 false: 상대가 지원한다고 하기 전까진 켜지 않음)
    private var isPeerWifiSupported: Boolean = false

    // 서비스 ID (상대방과 일치해야 함)
    private val SERVICE_ID = "RescuerServiceId"

    private var wifiAwareManager: WifiAwareManager? = null
    private var wifiRttManager: WifiRttManager? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private var session: WifiAwareSession? = null
    private var isStarting = false
    private var dataPathEnabled = true
    private var isNdpInitiator = true
    private var peerRequestedNdp = false
    private var ndpAckReceived = false
    private val ndpRequestMessage = "NDP_REQ"
    private val ndpAckMessage = "NDP_ACK"
    private var nextMessageId = 1
    private var localPeerIdForCall: String? = null
    private var expectedPeerIdForCall: String? = null

    // Ranging 관련
    private val foundPeers = mutableListOf<PeerHandle>()
    private var isRanging = false
    private var rttEnabled = true
    private val handler = Handler(Looper.getMainLooper())
    private var pendingConnectRunnable: Runnable? = null
    private var attachRetryRunnable: Runnable? = null
    private var pendingRestartRunnable: Runnable? = null
    private var ndpUnavailableCount: Int = 0
    private val ndpRequestTimeoutMs = 8_000
    private var rttFailureStreak = 0
    private val rangingIntervalBaseMs = 1_000L
    private val rangingIntervalNdpActiveMs = 1_800L
    private val rangingIntervalMaxMs = 5_000L
    private val ndpRttWarmupMs = 800L
    private val rttUnsupportedCacheMs = 5 * 60_000L
    private val rttStatusFailDisableThreshold = 6
    private val peerRttBlockedUntilMs = mutableMapOf<String, Long>()
    private var rttStatusFailStreak = 0
    private var rttWarmupUntilMs = 0L

    // RTT와 오디오 수신 루프를 분리해 상호 간섭을 줄입니다.
    private val rttExecutor = Executors.newSingleThreadExecutor()
    private val udpReceiveExecutor = Executors.newSingleThreadExecutor()
    private var receiveJob: Future<*>? = null

    // 연결 시도 중복 방지
    private var isConnecting = false

    init {
        // 매니저 초기화
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager
        }
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_RTT)) {
            wifiRttManager = context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as WifiRttManager
        }
    }

    // =========================================================================
    // [핵심] 외부 제어 인터페이스
    // =========================================================================

    /**
     * 외부(ViewModel -> HybridManager)에서 호출.
     * 상대방이 Wi-Fi Aware를 지원하는지 여부를 업데이트합니다.
     */
    fun updatePeerCapability(supportsWifiAware: Boolean) {
        Log.d("WifiAware", "Peer Capability Updated: WifiSupported = $supportsWifiAware")
        ConnectionLog.add("Aware", "peer capability=$supportsWifiAware")
        isPeerWifiSupported = supportsWifiAware

        if (!supportsWifiAware) {
            // 지원하지 않으면 즉시 중지 (배터리 절약)
            stop()
            return
        }
    }

    /**
     * 시작 시도 (조건: 하드웨어 지원 + 권한 + 상대방 지원)
     */
    fun start() {
        if (awareDisabledForDirectTest) {
            ConnectionLog.add("Aware", "disabled for direct test")
            return
        }
        startIfReady()
    }

    private fun startIfReady() {
        // 이미 연결 준비되었거나 세션이 있으면 패스
        if (_isConnectionReady.value || session != null || isStarting) {
            ConnectionLog.add(
                "Aware",
                "start skipped ready=${_isConnectionReady.value} session=${session != null} starting=$isStarting"
            )
            return
        }
        cancelAttachRetry()
        pendingRestartRunnable?.let { handler.removeCallbacks(it) }
        pendingRestartRunnable = null
        _debugStats.update {
            it.copy(
                isReady = false,
                lastSendError = null,
                lastRecvError = null
            )
        }

        val hasFeature = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)
        val serviceExists = context.getSystemService(Context.WIFI_AWARE_SERVICE) != null
        Log.d(
            "WifiAware",
            "Local capability: feature=$hasFeature service=$serviceExists"
        )
        ConnectionLog.add("Aware", "startIfReady feature=$hasFeature service=$serviceExists")

        // [조건 1] 내 하드웨어 체크
        if (wifiAwareManager == null || !wifiAwareManager!!.isAvailable) {
            val available = wifiAwareManager?.isAvailable ?: false
            Log.e("WifiAware", "Wi-Fi Aware not available on this device. available=$available")
            ConnectionLog.add("Aware", "not available (available=$available)")
            scheduleAttachRetry("not available")
            return
        }

        // [조건 2] 상대방 지원 여부 체크 (중요)
        if (!isPeerWifiSupported) {
            Log.d("WifiAware", "Peer does not support Wi-Fi Aware. Waiting...")
            ConnectionLog.add("Aware", "peer not supported")
            return
        }

        // [조건 3] 권한 체크
        if (!checkPermissions()) {
            Log.e("WifiAware", "Missing permissions.")
            ConnectionLog.add("Aware", "missing permissions")
            return
        }
        if (!isLocationServiceEnabled()) {
            Log.e("WifiAware", "Location service disabled.")
            ConnectionLog.add("Aware", "location service disabled")
            scheduleAttachRetry("location service disabled")
            return
        }

        Log.d("WifiAware", "All conditions met. Starting Wi-Fi Aware...")
        ConnectionLog.add("Aware", "attach session")
        isStarting = true

        wifiAwareManager?.attach(object : AttachCallback() {
            override fun onAttached(wifiAwareSession: WifiAwareSession) {
                Log.d("WifiAware", "Session Attached!")
                ConnectionLog.add("Aware", "session attached")
                session = wifiAwareSession
                isStarting = false
                publishToService()
                subscribeToService()
            }

            override fun onAttachFailed() {
                Log.e("WifiAware", "Session Attach Failed")
                ConnectionLog.add("Aware", "attach failed")
                isStarting = false
                scheduleAttachRetry("attach failed")
            }
        }, null)
    }

    fun setDataPathEnabled(enabled: Boolean) {
        if (dataPathEnabled == enabled) return
        dataPathEnabled = enabled
        Log.d("WifiAware", "Data path enabled=$enabled")
        ConnectionLog.add("Aware", "data path enabled=$enabled")
    }

    fun setNdpInitiator(isInitiator: Boolean) {
        if (isNdpInitiator == isInitiator) return
        isNdpInitiator = isInitiator
        isRanging = false
        peerRequestedNdp = false
        ndpAckReceived = false
        ConnectionLog.add("Aware", "ndp initiator=$isNdpInitiator")
    }

    fun configureCallContext(localPeerIdHex: String?, expectedPeerIdHex: String?) {
        localPeerIdForCall = normalizePeerId(localPeerIdHex)
        expectedPeerIdForCall = normalizePeerId(expectedPeerIdHex)
        setCurrentTargetPeer(null)
        peerRequestedNdp = false
        ndpAckReceived = false
        ConnectionLog.add(
            "Aware",
            "call ctx local=${localPeerIdForCall ?: "-"} target=${expectedPeerIdForCall ?: "-"}"
        )
    }

    fun setRttEnabled(enabled: Boolean) {
        if (rttEnabled == enabled) return
        rttEnabled = enabled
        if (!enabled) {
            isRanging = false
            rttStatusFailStreak = 0
            _rttDistance.value = null
            ConnectionLog.add("Aware", "RTT disabled")
        } else {
            ConnectionLog.add("Aware", "RTT enabled")
            if (currentTargetPeer != null && !isRanging && wifiRttManager != null && !isCurrentPeerRttBlocked()) {
                isRanging = true
                startRangingLoop()
            } else if (isCurrentPeerRttBlocked()) {
                ConnectionLog.add("Aware", "RTT skipped: peer blocked by cache")
            }
        }
    }

    private fun checkPermissions(): Boolean {
        // Android 13+ 권한 체크
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val nearbyGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
            if (!nearbyGranted) {
                Log.e("WifiAware", "Missing permission: NEARBY_WIFI_DEVICES")
                return false
            }
        }
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted) {
            Log.e("WifiAware", "Missing permission: ACCESS_FINE_LOCATION")
        }
        return fineGranted
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            val gps = runCatching {
                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            }.getOrDefault(false)
            val network = runCatching {
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            }.getOrDefault(false)
            gps || network
        }
    }

    // =========================================================================
    // 2. 서비스 구독 (Subscribe)
    // =========================================================================
    @SuppressLint("MissingPermission")
    private fun subscribeToService() {
        val config = SubscribeConfig.Builder()
            .setServiceName(SERVICE_ID)
            .build()

        session?.subscribe(config, object : DiscoverySessionCallback() {
            override fun onSubscribeStarted(session: SubscribeDiscoverySession) {
                Log.d("WifiAware", "Subscribe started successfully")
                ConnectionLog.add("Aware", "subscribe started")
                subscribeSession = session // [Fix] 세션 저장
            }

            override fun onServiceDiscovered(peerHandle: PeerHandle, serviceSpecificInfo: ByteArray?, matchFilter: List<ByteArray>?) {
                Log.i("WifiAware", "Peer Discovered: $peerHandle")
                ConnectionLog.add("Aware", "service discovered=$peerHandle")

                // 기존 목록에 없으면 추가
                if (!foundPeers.contains(peerHandle)) {
                    foundPeers.add(peerHandle)
                }

                if (isNdpInitiator && !_isConnectionReady.value && !isConnecting) {
                    if (currentTargetPeer != null && currentTargetPeer != peerHandle) {
                        ConnectionLog.add("Aware", "service discovered ignored: target locked")
                        return
                    }
                    Log.i("WifiAware", "Service discovered. Sending NDP request...")
                    ConnectionLog.add("Aware", "service discovered -> send ndp request")
                    sendNdpRequestToPeer(peerHandle)
                } else if (!isNdpInitiator) {
                    ConnectionLog.add("Aware", "service discovered -> wait initiator request")
                }
                if (rttEnabled && isNdpInitiator && wifiRttManager != null && !isRanging && currentTargetPeer != null) {
                    isRanging = true
                    ConnectionLog.add("Aware", "start RTT ranging")
                    startRangingLoop()
                }
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                if (!isNdpInitiator) return
                val signal = parseNdpSignal(message) ?: return
                if (signal.type != ndpAckMessage) return
                if (!isExpectedAckSignal(signal)) {
                    ConnectionLog.add("Aware", "ignore ndp ack from unexpected peer")
                    return
                }
                if (currentTargetPeer != null && currentTargetPeer != peerHandle) {
                    ConnectionLog.add("Aware", "ignore ndp ack: target locked")
                    return
                }
                setCurrentTargetPeer(peerHandle)
                ndpAckReceived = true
                ConnectionLog.add("Aware", "received ndp ack from target")
                if (rttEnabled && wifiRttManager != null && !isRanging && currentTargetPeer != null) {
                    isRanging = true
                    ConnectionLog.add("Aware", "start RTT ranging (ack)")
                    startRangingLoop()
                }
                if (!_isConnectionReady.value && !isConnecting && dataPathEnabled) {
                    connectToCurrentPeer()
                }
            }
        }, null)
    }

    // =========================================================================
    // 2-1. 서비스 발행 (Publish)
    // =========================================================================
    @SuppressLint("MissingPermission")
    private fun publishToService() {
        val config = PublishConfig.Builder()
            .setServiceName(SERVICE_ID)
            .build()

        session?.publish(config, object : DiscoverySessionCallback() {
            override fun onPublishStarted(session: PublishDiscoverySession) {
                Log.d("WifiAware", "Publish started successfully")
                ConnectionLog.add("Aware", "publish started")
                publishSession = session
            }

            override fun onMessageReceived(peerHandle: PeerHandle, message: ByteArray) {
                if (isNdpInitiator) return
                val signal = parseNdpSignal(message) ?: return
                if (signal.type != ndpRequestMessage) return
                if (!isExpectedRequestSignal(signal)) {
                    ConnectionLog.add("Aware", "ignore ndp request from unexpected peer")
                    return
                }
                if (currentTargetPeer != null && currentTargetPeer != peerHandle) {
                    ConnectionLog.add("Aware", "ignore ndp request: target locked")
                    return
                }
                setCurrentTargetPeer(peerHandle)
                peerRequestedNdp = true
                sendNdpAckToPeer(peerHandle, signal.senderPeerId)
                ConnectionLog.add("Aware", "received ndp request from initiator")
                if (rttEnabled && wifiRttManager != null && !isRanging && currentTargetPeer != null) {
                    isRanging = true
                    ConnectionLog.add("Aware", "start RTT ranging (request)")
                    startRangingLoop()
                }
                if (!_isConnectionReady.value && !isConnecting && dataPathEnabled) {
                    connectToCurrentPeer()
                }
            }
        }, null)
    }

    // =========================================================================
    // 3. RTT 거리 측정 루프
    // =========================================================================
    @SuppressLint("MissingPermission")
    private fun startRangingLoop() {
        if (!rttEnabled || !isRanging || currentTargetPeer == null || wifiRttManager == null) return
        if (isCurrentPeerRttBlocked()) {
            isRanging = false
            _rttDistance.value = null
            ConnectionLog.add("Aware", "RTT skipped: peer blocked by cache")
            return
        }
        val now = System.currentTimeMillis()
        if (_isConnectionReady.value && now < rttWarmupUntilMs) {
            val waitMs = (rttWarmupUntilMs - now).coerceAtMost(2_000L)
            handler.postDelayed({ startRangingLoop() }, waitMs)
            return
        }

        val request = RangingRequest.Builder()
            .addWifiAwarePeer(currentTargetPeer!!)
            .build()

        ConnectionLog.add("Aware", "ranging request")
        wifiRttManager?.startRanging(request, rttExecutor, rttCallback)
    }

    private val rttCallback = object : RangingResultCallback() {
        override fun onRangingResults(results: List<RangingResult>) {
            val best = results.firstOrNull { it.status == RangingResult.STATUS_SUCCESS }
            if (best != null) {
                rttFailureStreak = 0
                rttStatusFailStreak = 0
                val dist = best.distanceMm / 1000f
                Log.d("WifiAware", "RTT distance=${dist}m")
                ConnectionLog.add("Aware", "RTT distance=${dist}m")
                _rttDistance.value = dist
                if (currentTargetPeer == null) {
                    if (!requiresAckBeforeConnect()) {
                        setCurrentTargetPeer(best.peerHandle)
                    }
                } else if (currentTargetPeer != best.peerHandle) {
                    if (isNdpInitiator) {
                        ConnectionLog.add("Aware", "ignore RTT from non-target peer")
                    }
                    if (isRanging) handler.postDelayed({ startRangingLoop() }, 1000)
                    return
                }

                // [연결 트리거 로직]
                // 30m 이내이고, 아직 연결 안됐고, 연결 시도 중이 아니면 -> 연결
                if (isNdpInitiator && dist < connectDistanceMeters && !_isConnectionReady.value && !isConnecting) {
                    Log.i("WifiAware", "Peer close ($dist m). Requesting NDP connection...")
                    ConnectionLog.add("Aware", "distance<$connectDistanceMeters -> connect")
                    currentTargetPeer?.let { sendNdpRequestToPeer(it) }
                    if (dataPathEnabled) {
                        connectToCurrentPeer()
                    } else {
                        ConnectionLog.add("Aware", "data path disabled -> skip connect")
                    }
                }
            } else {
                rttFailureStreak = (rttFailureStreak + 1).coerceAtMost(10)
                val statusLabels = results.joinToString(",") { rangingStatusLabel(it.status) }
                if (statusLabels.isNotBlank()) {
                    ConnectionLog.add("Aware", "RTT no success statuses=$statusLabels")
                }
                val hasUnsupportedStatus = results.any { isRttPeerUnsupportedStatus(it.status) }
                val onlyGenericFail = results.isNotEmpty() && results.all { it.status == RangingResult.STATUS_FAIL }
                if (hasUnsupportedStatus) {
                    markCurrentPeerRttUnsupported("result=${statusLabels.ifBlank { "unsupported" }}")
                } else if (_isConnectionReady.value && onlyGenericFail) {
                    rttStatusFailStreak += 1
                    ConnectionLog.add(
                        "Aware",
                        "RTT generic fail streak=$rttStatusFailStreak/${rttStatusFailDisableThreshold}"
                    )
                    if (rttStatusFailStreak >= rttStatusFailDisableThreshold) {
                        markCurrentPeerRttUnsupported("repeated STATUS_FAIL")
                    }
                } else if (results.isNotEmpty()) {
                    rttStatusFailStreak = 0
                }
            }
            scheduleNextRanging()
        }

        override fun onRangingFailure(code: Int) {
            val codeLabel = rangingFailureCodeLabel(code)
            Log.w("WifiAware", "RTT failure code=$codeLabel")
            ConnectionLog.add("Aware", "RTT failure code=$codeLabel")
            val bump = if (code == RangingResultCallback.STATUS_CODE_FAIL) 2 else 1
            rttFailureStreak = (rttFailureStreak + bump).coerceAtMost(10)
            if (_isConnectionReady.value && code == RangingResultCallback.STATUS_CODE_FAIL) {
                rttStatusFailStreak += 1
                ConnectionLog.add(
                    "Aware",
                    "RTT callback fail streak=$rttStatusFailStreak/${rttStatusFailDisableThreshold}"
                )
                if (rttStatusFailStreak >= rttStatusFailDisableThreshold) {
                    markCurrentPeerRttUnsupported("callback STATUS_CODE_FAIL")
                }
            } else if (code != RangingResultCallback.STATUS_CODE_FAIL) {
                rttStatusFailStreak = 0
            }
            scheduleNextRanging()
        }
    }

    private fun scheduleNextRanging() {
        if (!isRanging) return
        val base = if (_isConnectionReady.value) {
            rangingIntervalNdpActiveMs
        } else {
            rangingIntervalBaseMs
        }
        val extra = when {
            rttFailureStreak <= 0 -> 0L
            rttFailureStreak == 1 -> 400L
            rttFailureStreak == 2 -> 900L
            else -> ((rttFailureStreak - 2) * 700L + 900L)
        }
        val delayMs = (base + extra).coerceAtMost(rangingIntervalMaxMs)
        handler.postDelayed({ startRangingLoop() }, delayMs)
    }

    // =========================================================================
    // 4. NDP 연결 및 소켓 설정 (실시간 통화용)
    // =========================================================================
    private fun connectToCurrentPeer() {
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

            override fun onLost(network: Network) {
                Log.d("WifiAware", "NDP Network Lost")
                ConnectionLog.add("Aware", "NDP network lost")
                _isConnectionReady.value = false
                _debugStats.update { it.copy(isReady = false) }
                isConnecting = false
                rttWarmupUntilMs = 0L
                closeSocket()
                scheduleFallbackConnect("network lost")
            }

            override fun onUnavailable() {
                Log.e("WifiAware", "NDP Network Unavailable")
                ConnectionLog.add("Aware", "NDP unavailable")
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

    // =========================================================================
    // 5. 오디오 송수신
    // =========================================================================
    private fun startReceiveLoop() {
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

    fun sendAudio(data: ByteArray) {
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

    private fun isExpectedSender(packet: DatagramPacket): Boolean {
        val expected = peerAddress ?: return true
        return packet.address == expected
    }

    private fun updatePeerFromPacket(packet: DatagramPacket) {
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

    // =========================================================================
    // 6. 종료 및 정리
    // =========================================================================
    fun stop() {
        isRanging = false
        rttFailureStreak = 0
        rttStatusFailStreak = 0
        rttWarmupUntilMs = 0L
        isPeerWifiSupported = false // 상태 초기화
        cancelAttachRetry()
        pendingRestartRunnable?.let { handler.removeCallbacks(it) }
        pendingRestartRunnable = null
        pendingConnectRunnable?.let { handler.removeCallbacks(it) }
        pendingConnectRunnable = null
        receiveJob?.cancel(true)
        closeSocket()
        try {
            subscribeSession?.close()
        } catch (_: Exception) {
        }
        subscribeSession = null
        try {
            publishSession?.close()
        } catch (_: Exception) {
        }
        publishSession = null
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        _isConnectionReady.value = false
        _rttDistance.value = null
        _debugStats.update {
            it.copy(
                isReady = false,
                lastSendError = null,
                lastRecvError = null
            )
        }
        peerAddress = null
        peerPort = null
        setCurrentTargetPeer(null)
        peerRequestedNdp = false
        ndpAckReceived = false
        foundPeers.clear()
        isStarting = false
        ndpUnavailableCount = 0
        Log.d("WifiAware", "Stopped.")
        ConnectionLog.add("Aware", "stopped")
    }

    private fun scheduleAttachRetry(reason: String) {
        if (!isPeerWifiSupported || _isConnectionReady.value || session != null || isStarting) return
        attachRetryRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (!isPeerWifiSupported || _isConnectionReady.value || session != null || isStarting) return@Runnable
            ConnectionLog.add("Aware", "retry attach ($reason)")
            startIfReady()
        }
        attachRetryRunnable = runnable
        handler.postDelayed(runnable, 2_000L)
    }

    private fun cancelAttachRetry() {
        attachRetryRunnable?.let { handler.removeCallbacks(it) }
        attachRetryRunnable = null
    }

    private fun scheduleFallbackConnect(reason: String) {
        if (_isConnectionReady.value || isConnecting || currentTargetPeer == null) return
        if (!isNdpInitiator && !peerRequestedNdp) {
            ConnectionLog.add("Aware", "fallback skipped: waiting initiator request")
            return
        }
        pendingConnectRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (_isConnectionReady.value || isConnecting || currentTargetPeer == null) return@Runnable
            if (!isNdpInitiator && !peerRequestedNdp) return@Runnable
            Log.w("WifiAware", "Fallback connect triggered ($reason)")
            ConnectionLog.add("Aware", "fallback connect ($reason)")
            connectToCurrentPeer()
        }
        pendingConnectRunnable = runnable
        val delay = (1000L * ndpUnavailableCount.coerceIn(1, 5))
        handler.postDelayed(runnable, delay)
    }

    private fun scheduleAwareSessionRestart(reason: String) {
        if (_isConnectionReady.value || isStarting) return
        pendingRestartRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (_isConnectionReady.value || isStarting) return@Runnable
            ConnectionLog.add("Aware", "restart session ($reason)")
            try {
                subscribeSession?.close()
            } catch (_: Exception) {
            }
            subscribeSession = null
            try {
                publishSession?.close()
            } catch (_: Exception) {
            }
            publishSession = null
            try {
                session?.close()
            } catch (_: Exception) {
            }
            session = null
            closeSocket()
            _isConnectionReady.value = false
            _debugStats.update { it.copy(isReady = false) }
            isConnecting = false
            isStarting = false
            ndpUnavailableCount = 0
            startIfReady()
        }
        pendingRestartRunnable = runnable
        handler.postDelayed(runnable, 2_500L)
    }

    private fun closeSocket() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
        socket = null
        if (networkCallback != null) {
            try {
                connectivityManager?.unregisterNetworkCallback(networkCallback!!)
            } catch (_: Exception) {
            }
            networkCallback = null
        }
        isConnecting = false
        peerAddress = null
        peerPort = null
    }

    private fun setCurrentTargetPeer(peer: PeerHandle?) {
        if (currentTargetPeer != peer) {
            rttStatusFailStreak = 0
        }
        currentTargetPeer = peer
    }

    private fun isCurrentPeerRttBlocked(now: Long = System.currentTimeMillis()): Boolean {
        val peerKey = resolveRttPeerCacheKey() ?: return false
        cleanupExpiredRttPeerBlocks(now)
        val blockedUntil = peerRttBlockedUntilMs[peerKey] ?: return false
        return blockedUntil > now
    }

    private fun cleanupExpiredRttPeerBlocks(now: Long) {
        if (peerRttBlockedUntilMs.isEmpty()) return
        val iterator = peerRttBlockedUntilMs.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value <= now) {
                iterator.remove()
            }
        }
    }

    private fun markCurrentPeerRttUnsupported(reason: String) {
        val peerKey = resolveRttPeerCacheKey() ?: return
        val now = System.currentTimeMillis()
        cleanupExpiredRttPeerBlocks(now)
        val alreadyBlocked = peerRttBlockedUntilMs[peerKey]?.let { it > now } == true
        peerRttBlockedUntilMs[peerKey] = now + rttUnsupportedCacheMs
        isRanging = false
        _rttDistance.value = null
        rttStatusFailStreak = 0
        if (!alreadyBlocked) {
            val message = "RTT disabled for peer=$peerKey reason=$reason"
            Log.w("WifiAware", message)
            ConnectionLog.add("Aware", message)
        }
    }

    private fun resolveRttPeerCacheKey(): String? {
        val expectedPeer = expectedPeerIdForCall
        if (!expectedPeer.isNullOrBlank()) return expectedPeer
        val currentPeer = currentTargetPeer ?: return null
        return "aware_peer_${currentPeer.hashCode()}"
    }

    private fun isRttPeerUnsupportedStatus(status: Int): Boolean {
        return status == RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC || status == 3
    }

    private fun rangingStatusLabel(status: Int): String {
        return when (status) {
            RangingResult.STATUS_SUCCESS -> "SUCCESS(0)"
            RangingResult.STATUS_FAIL -> "FAIL(1)"
            RangingResult.STATUS_RESPONDER_DOES_NOT_SUPPORT_IEEE80211MC -> "RESPONDER_NO_11MC(2)"
            3 -> "RESPONDER_NON_COMPLIANT(3)"
            else -> "UNKNOWN($status)"
        }
    }

    private fun rangingFailureCodeLabel(code: Int): String {
        return when (code) {
            RangingResultCallback.STATUS_CODE_FAIL -> "FAIL(1)"
            RangingResultCallback.STATUS_CODE_FAIL_RTT_NOT_AVAILABLE -> "RTT_NOT_AVAILABLE(2)"
            else -> "UNKNOWN($code)"
        }
    }

    private fun sendNdpRequestToPeer(peerHandle: PeerHandle) {
        if (!isNdpInitiator) return
        val subscribe = subscribeSession ?: return
        val messageId = nextMessageId++
        val payload = buildNdpRequestPayload()
        try {
            subscribe.sendMessage(peerHandle, messageId, payload)
            ConnectionLog.add("Aware", "send ndp request id=$messageId")
        } catch (e: Exception) {
            ConnectionLog.add("Aware", "send ndp request failed: ${e.message}")
        }
    }

    private fun sendNdpAckToPeer(peerHandle: PeerHandle, targetPeerId: String) {
        if (isNdpInitiator) return
        val publish = publishSession ?: return
        val messageId = nextMessageId++
        val payload = buildNdpAckPayload(targetPeerId)
        try {
            publish.sendMessage(peerHandle, messageId, payload)
            ConnectionLog.add("Aware", "send ndp ack id=$messageId")
        } catch (e: Exception) {
            ConnectionLog.add("Aware", "send ndp ack failed: ${e.message}")
        }
    }

    private fun requiresAckBeforeConnect(): Boolean {
        return !localPeerIdForCall.isNullOrBlank() && !expectedPeerIdForCall.isNullOrBlank()
    }

    private fun buildNdpRequestPayload(): ByteArray {
        val local = localPeerIdForCall
        val target = expectedPeerIdForCall
        val message = if (!local.isNullOrBlank() && !target.isNullOrBlank()) {
            "$ndpRequestMessage|$local|$target"
        } else {
            ndpRequestMessage
        }
        return message.toByteArray(Charsets.US_ASCII)
    }

    private fun buildNdpAckPayload(targetPeerId: String): ByteArray {
        val local = localPeerIdForCall
        val target = normalizePeerId(targetPeerId)
        val message = if (!local.isNullOrBlank() && !target.isNullOrBlank()) {
            "$ndpAckMessage|$local|$target"
        } else {
            ndpAckMessage
        }
        return message.toByteArray(Charsets.US_ASCII)
    }

    private data class NdpSignal(
        val type: String,
        val senderPeerId: String,
        val targetPeerId: String
    )

    private fun parseNdpSignal(message: ByteArray): NdpSignal? {
        val text = try {
            message.toString(Charsets.US_ASCII)
        } catch (_: Exception) {
            return null
        }
        if (text.isBlank()) return null
        val parts = text.split('|')
        val type = parts.firstOrNull() ?: return null
        val sender = normalizePeerId(parts.getOrNull(1)).orEmpty()
        val target = normalizePeerId(parts.getOrNull(2)).orEmpty()
        return NdpSignal(type = type, senderPeerId = sender, targetPeerId = target)
    }

    private fun isExpectedRequestSignal(signal: NdpSignal): Boolean {
        if (signal.type != ndpRequestMessage) return false
        if (!requiresAckBeforeConnect()) return true
        val local = localPeerIdForCall ?: return false
        val expected = expectedPeerIdForCall ?: return false
        return signal.senderPeerId == expected && signal.targetPeerId == local
    }

    private fun isExpectedAckSignal(signal: NdpSignal): Boolean {
        if (signal.type != ndpAckMessage) return false
        if (!requiresAckBeforeConnect()) return true
        val local = localPeerIdForCall ?: return false
        val expected = expectedPeerIdForCall ?: return false
        return signal.senderPeerId == expected && signal.targetPeerId == local
    }

    private fun normalizePeerId(peerId: String?): String? {
        val normalized = peerId?.trim()?.lowercase()
        return normalized?.takeIf { it.isNotBlank() }
    }

    private fun shouldLog(now: Long, last: Long): Boolean {
        return now - last >= netLogIntervalMs
    }

    private fun maybeLogNetSend(
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

    private fun maybeLogNetRecv(size: Int, address: InetAddress?, port: Int) {
        val now = System.currentTimeMillis()
        if (!shouldLog(now, lastNetRecvLogAt)) return
        lastNetRecvLogAt = now
        Log.d(
            "NetPipe",
            "Aware RECV size=$size peer=${address?.hostAddress}:$port"
        )
    }
}
