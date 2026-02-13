package com.example.lifesaivior.core.wifi

import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.wifi.aware.*
import android.net.wifi.rtt.*
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.lifesaivior.core.log.ConnectionLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * [Wi-Fi Aware 통합 매니저]
 * 역할 1: RTT 거리 측정 (Ranging)
 * 역할 2: 50m 진입 시 NDP(데이터 경로) 연결
 * 역할 3: UDP 소켓을 통한 오디오 데이터 송수신
 * * [수정 사항]: 외부(ViewModel)에서 상대방의 지원 여부를 알려줘야만 동작하도록 변경
 */
class WifiAwareRanger(internal val context: Context) {
    internal val awareDisabledForDirectTest = false

    // --- StateFlows ---
    // 거리 정보 (Meter 단위)
    internal val _rttDistance = MutableStateFlow<Float?>(null)
    val rttDistance = _rttDistance.asStateFlow()

    // 고속 연결 상태 (데이터 통로 준비 완료 여부)
    internal val _isConnectionReady = MutableStateFlow(false)
    val isConnectionReady = _isConnectionReady.asStateFlow()

    internal val _debugStats = MutableStateFlow(TransportDebugStats(name = "Wi-Fi Aware"))
    val debugStats = _debugStats.asStateFlow()

    // 오디오 수신 콜백 (PCM 데이터 -> AppViewModel -> AudioEngine)
    var onAudioDataReceived: ((ByteArray) -> Unit)? = null

    // --- 내부 변수 ---
    internal var subscribeSession: SubscribeDiscoverySession? = null
    internal var publishSession: PublishDiscoverySession? = null
    @Volatile internal var socket: DatagramSocket? = null
    @Volatile internal var peerAddress: InetAddress? = null
    @Volatile internal var peerPort: Int? = null
    internal val socketPort = 50000
    internal val connectDistanceMeters = 20.0
    internal val maxUdpPayload = 1200
    internal var lastNetSendLogAt = 0L
    internal var lastNetRecvLogAt = 0L
    internal val netLogIntervalMs = 10_000L

    // 타겟 피어 핸들 (거리 측정 및 연결 대상)
    internal var currentTargetPeer: PeerHandle? = null

    // [New] 상대방 기능 지원 여부 (기본값 false: 상대가 지원한다고 하기 전까진 켜지 않음)
    internal var isPeerWifiSupported: Boolean = false

    // 서비스 ID (상대방과 일치해야 함)
    internal val SERVICE_ID = "RescuerServiceId"

    internal var wifiAwareManager: WifiAwareManager? = null
    internal var wifiRttManager: WifiRttManager? = null
    internal var connectivityManager: ConnectivityManager? = null
    internal var networkCallback: ConnectivityManager.NetworkCallback? = null

    @Volatile internal var session: WifiAwareSession? = null
    @Volatile internal var isStarting = false
    internal var dataPathEnabled = true
    internal var isNdpInitiator = true
    internal var peerRequestedNdp = false
    internal var ndpAckReceived = false
    internal var lastNdpRequestBroadcastAtMs = 0L
    internal val ndpRequestMessage = "NDP_REQ"
    internal val ndpAckMessage = "NDP_ACK"
    internal var nextMessageId = 1
    internal var localPeerIdForCall: String? = null
    internal var expectedPeerIdForCall: String? = null

    // Ranging 관련
    internal val foundPeers = mutableListOf<PeerHandle>()
    internal var isRanging = false
    internal var rttEnabled = true
    internal val handler = Handler(Looper.getMainLooper())
    internal var pendingConnectRunnable: Runnable? = null
    internal var attachRetryRunnable: Runnable? = null
    internal var pendingRestartRunnable: Runnable? = null
    internal var attachFailureStreak = 0
    internal var attachBlockedUntilMs = 0L
    internal var lastAttachAttemptAtMs = 0L
    internal var attachRequestToken = 0L
    internal val minAttachAttemptIntervalMs = 1_200L
    internal val attachRetryBaseMs = 2_000L
    internal val attachRetryMaxMs = 20_000L
    internal val attachFailureBlockThreshold = 5
    internal val attachFailureBlockMs = 30_000L
    internal var lastStopAtMs = 0L
    internal val minRestartAfterStopMs = 1_500L
    internal var ndpUnavailableCount: Int = 0
    internal val ndpRequestTimeoutMs = 8_000
    internal var rttFailureStreak = 0
    internal val rangingIntervalBaseMs = 1_000L
    internal val rangingIntervalNdpActiveMs = 1_800L
    internal val rangingIntervalMaxMs = 5_000L
    internal val ndpRttWarmupMs = 800L
    internal val rttUnsupportedCacheMs = 5 * 60_000L
    internal val rttStatusFailDisableThreshold = 6
    internal val peerRttBlockedUntilMs = mutableMapOf<String, Long>()
    internal var rttStatusFailStreak = 0
    internal var rttWarmupUntilMs = 0L
    internal var rttRequestInFlight = false
    internal var rttWatchdogRunnable: Runnable? = null
    internal val rttCallbackTimeoutMs = 3_000L

    // RTT와 오디오 수신 루프를 분리해 상호 간섭을 줄입니다.
    internal val rttExecutor = Executors.newSingleThreadExecutor()
    internal val udpReceiveExecutor = Executors.newSingleThreadExecutor()
    internal var receiveJob: Future<*>? = null

    // 연결 시도 중복 방지
    @Volatile internal var isConnecting = false

    internal fun isOnMainThread(): Boolean = Looper.myLooper() == handler.looper

    internal fun runOnMain(block: () -> Unit) {
        if (isOnMainThread()) {
            block()
        } else {
            handler.post(block)
        }
    }

    init {
        // 매니저 초기화
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
            wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as WifiAwareManager
        }
        wifiRttManager = context.getSystemService(Context.WIFI_RTT_RANGING_SERVICE) as? WifiRttManager
    }

    // =========================================================================
    // [핵심] 외부 제어 인터페이스
    // =========================================================================

    /**
     * 외부(ViewModel -> HybridManager)에서 호출.
     * 상대방이 Wi-Fi Aware를 지원하는지 여부를 업데이트합니다.
     */
    fun updatePeerCapability(supportsWifiAware: Boolean) {
        if (!isOnMainThread()) {
            runOnMain { updatePeerCapability(supportsWifiAware) }
            return
        }
        Log.d("WifiAware", "Peer Capability Updated: WifiSupported = $supportsWifiAware")
        ConnectionLog.add("Aware", "peer capability=$supportsWifiAware")
        val capabilityChanged = isPeerWifiSupported != supportsWifiAware
        isPeerWifiSupported = supportsWifiAware

        if (!supportsWifiAware) {
            // 지원하지 않으면 즉시 중지 (배터리 절약)
            stop()
            return
        }
        if (capabilityChanged) {
            // false -> true 전환 시 이미 tracking 경로가 켜져 있어도 start()가 다시 호출되지 않을 수 있다.
            // capability 업데이트에서 attach 재시도를 직접 트리거해 상세 페이지 진입 직후 RTT가 붙도록 보장한다.
            startIfReady()
        }
    }

    /**
     * 시작 시도 (조건: 하드웨어 지원 + 권한 + 상대방 지원)
     */
    fun start() {
        if (!isOnMainThread()) {
            runOnMain { start() }
            return
        }
        if (awareDisabledForDirectTest) {
            ConnectionLog.add("Aware", "disabled for direct test")
            return
        }
        startIfReady()
    }

    fun setDataPathEnabled(enabled: Boolean) {
        if (!isOnMainThread()) {
            runOnMain { setDataPathEnabled(enabled) }
            return
        }
        if (dataPathEnabled == enabled) return
        dataPathEnabled = enabled
        Log.d("WifiAware", "Data path enabled=$enabled")
        ConnectionLog.add("Aware", "data path enabled=$enabled")
        if (enabled) {
            trySendNdpRequestToKnownPeers("data path enabled")
        }
    }

    fun setNdpInitiator(isInitiator: Boolean) {
        if (!isOnMainThread()) {
            runOnMain { setNdpInitiator(isInitiator) }
            return
        }
        if (isNdpInitiator == isInitiator) return
        isNdpInitiator = isInitiator
        isRanging = false
        peerRequestedNdp = false
        ndpAckReceived = false
        ConnectionLog.add("Aware", "ndp initiator=$isNdpInitiator")
        if (isNdpInitiator) {
            trySendNdpRequestToKnownPeers("ndp initiator")
        }
    }

    fun configureCallContext(localPeerIdHex: String?, expectedPeerIdHex: String?) {
        if (!isOnMainThread()) {
            runOnMain { configureCallContext(localPeerIdHex, expectedPeerIdHex) }
            return
        }
        localPeerIdForCall = normalizePeerId(localPeerIdHex)
        expectedPeerIdForCall = normalizePeerId(expectedPeerIdHex)
        setCurrentTargetPeer(null)
        peerRequestedNdp = false
        ndpAckReceived = false
        ConnectionLog.add(
            "Aware",
            "call ctx local=${localPeerIdForCall ?: "-"} target=${expectedPeerIdForCall ?: "-"}"
        )
        trySendNdpRequestToKnownPeers("call ctx updated")
    }

    fun setRttEnabled(enabled: Boolean) {
        if (!isOnMainThread()) {
            runOnMain { setRttEnabled(enabled) }
            return
        }
        if (rttEnabled == enabled) {
            if (enabled) {
                maybeStartRtt("toggle(no-op)")
            }
            return
        }
        rttEnabled = enabled
        if (!enabled) {
            isRanging = false
            rttRequestInFlight = false
            clearRttWatchdog()
            rttStatusFailStreak = 0
            _rttDistance.value = null
            ConnectionLog.add("Aware", "RTT disabled")
        } else {
            ConnectionLog.add("Aware", "RTT enabled")
            maybeStartRtt("toggle")
        }
    }

    fun sendAudio(data: ByteArray) {
        sendAudioInternal(data)
    }

    fun stop() {
        stopInternal()
    }

    fun resetForCallAttempt(reason: String? = null) {
        resetForCallAttemptInternal(reason)
    }

    internal val rttCallback = object : RangingResultCallback() {
        override fun onRangingResults(results: List<RangingResult>) {
            runOnMain { handleRangingResultsOnMain(results) }
        }

        override fun onRangingFailure(code: Int) {
            runOnMain { handleRangingFailureOnMain(code) }
        }
    }
}
