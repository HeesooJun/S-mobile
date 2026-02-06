package com.example.lifesaiver.core.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.aware.AttachCallback
import android.net.wifi.aware.DiscoverySessionCallback
import android.net.wifi.aware.PeerHandle
import android.net.wifi.aware.PublishConfig
import android.net.wifi.aware.PublishDiscoverySession
import android.net.wifi.aware.SubscribeConfig
import android.net.wifi.aware.SubscribeDiscoverySession
import android.net.wifi.aware.WifiAwareManager
import android.net.wifi.aware.WifiAwareSession
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.lifesaiver.core.log.ConnectionLog
import kotlinx.coroutines.flow.update

internal fun WifiAwareRanger.startIfReady() {
    if (!isOnMainThread()) {
        runOnMain { startIfReady() }
        return
    }
    // 이미 연결 준비되었으면 패스
    if (_isConnectionReady.value || isStarting) {
        ConnectionLog.add(
            "Aware",
            "start skipped ready=${_isConnectionReady.value} session=${session != null} starting=$isStarting"
        )
        return
    }
    // 세션은 있으나 Publish/Subscribe가 비어 있으면 복구
    if (session != null) {
        ensureDiscoverySessions("session already attached")
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
    if (wifiAwareManager == null && context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_AWARE)) {
        wifiAwareManager = context.getSystemService(Context.WIFI_AWARE_SERVICE) as? WifiAwareManager
    }
    val now = System.currentTimeMillis()
    val sinceLastStop = now - lastStopAtMs
    // 직전 stop 직후 과도한 attach 방지
    if (lastStopAtMs > 0L && sinceLastStop in 0 until minRestartAfterStopMs) {
        val waitMs = (minRestartAfterStopMs - sinceLastStop).coerceAtLeast(500L)
        ConnectionLog.add("Aware", "post-stop cooldown wait=${waitMs}ms")
        scheduleAttachRetry("post-stop cooldown", waitMs)
        return
    }
    // 연속 실패 시 cooldown
    if (now < attachBlockedUntilMs) {
        val remainMs = attachBlockedUntilMs - now
        ConnectionLog.add("Aware", "attach cooldown remain=${remainMs}ms")
        scheduleAttachRetry("attach cooldown", remainMs)
        return
    }
    val sinceLastAttempt = now - lastAttachAttemptAtMs
    // attach 호출 간 최소 간격 보장
    if (lastAttachAttemptAtMs > 0L && sinceLastAttempt in 0 until minAttachAttemptIntervalMs) {
        val waitMs = (minAttachAttemptIntervalMs - sinceLastAttempt).coerceAtLeast(500L)
        ConnectionLog.add("Aware", "attach throttled wait=${waitMs}ms")
        scheduleAttachRetry("attach throttled", waitMs)
        return
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
    if (!isWifiEnabled()) {
        Log.e("WifiAware", "Wi-Fi disabled.")
        ConnectionLog.add("Aware", "wifi disabled")
        scheduleAttachRetry("wifi disabled")
        return
    }

    Log.d("WifiAware", "All conditions met. Starting Wi-Fi Aware...")
    ConnectionLog.add("Aware", "attach session")
    isStarting = true
    lastAttachAttemptAtMs = now
    val requestToken = ++attachRequestToken

    wifiAwareManager?.attach(object : AttachCallback() {
        override fun onAttached(wifiAwareSession: WifiAwareSession) {
            if (requestToken != attachRequestToken) {
                runCatching { wifiAwareSession.close() }
                return
            }
            Log.d("WifiAware", "Session Attached!")
            ConnectionLog.add("Aware", "session attached")
            session = wifiAwareSession
            isStarting = false
            attachFailureStreak = 0
            attachBlockedUntilMs = 0L
            publishToService()
            subscribeToService()
        }

        override fun onAttachFailed() {
            if (requestToken != attachRequestToken) return
            Log.e("WifiAware", "Session Attach Failed")
            attachFailureStreak = (attachFailureStreak + 1).coerceAtMost(10)
            val available = wifiAwareManager?.isAvailable ?: false
            if (attachFailureStreak >= attachFailureBlockThreshold) {
                attachBlockedUntilMs = System.currentTimeMillis() + attachFailureBlockMs
                ConnectionLog.add(
                    "Aware",
                    "attach blocked streak=$attachFailureStreak blockMs=$attachFailureBlockMs"
                )
            }
            ConnectionLog.add("Aware", "attach failed streak=$attachFailureStreak available=$available")
            isStarting = false
            scheduleAttachRetry("attach failed")
        }
    }, handler)
}

internal fun WifiAwareRanger.ensureDiscoverySessions(reason: String) {
    if (!isOnMainThread()) {
        runOnMain { ensureDiscoverySessions(reason) }
        return
    }
    val hasSession = session != null
    val needPublish = publishSession == null
    val needSubscribe = subscribeSession == null
    // 세션이 살아있는데 publish/subscribe가 비면 복구
    if (!hasSession || (!needPublish && !needSubscribe)) {
        ConnectionLog.add(
            "Aware",
            "ensure sessions skipped reason=$reason session=$hasSession publish=${!needPublish} subscribe=${!needSubscribe}"
        )
        return
    }
    ConnectionLog.add(
        "Aware",
        "ensure sessions reason=$reason publish=$needPublish subscribe=$needSubscribe"
    )
    if (needPublish) {
        publishToService()
    }
    if (needSubscribe) {
        subscribeToService()
    }
}

internal fun WifiAwareRanger.maybeStartRtt(reason: String) {
    if (!isOnMainThread()) {
        runOnMain { maybeStartRtt(reason) }
        return
    }
    when {
        !rttEnabled -> {
            Log.d("WifiAware", "RTT start skipped ($reason): disabled")
        }
        wifiRttManager == null -> {
            Log.w("WifiAware", "RTT start skipped ($reason): WifiRttManager unavailable")
            ConnectionLog.add("Aware", "RTT skipped: manager unavailable")
        }
        currentTargetPeer == null -> {
            Log.d("WifiAware", "RTT start skipped ($reason): no target")
        }
        isCurrentPeerRttBlocked() -> {
            ConnectionLog.add("Aware", "RTT skipped: peer blocked by cache")
        }
        rttRequestInFlight -> {
            Log.d("WifiAware", "RTT start skipped ($reason): request in-flight")
        }
        else -> {
            if (!isRanging) isRanging = true
            ConnectionLog.add("Aware", "start RTT ranging ($reason)")
            startRangingLoop()
        }
    }
}

internal fun WifiAwareRanger.checkPermissions(): Boolean {
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

internal fun WifiAwareRanger.isLocationServiceEnabled(): Boolean {
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

internal fun WifiAwareRanger.isWifiEnabled(): Boolean {
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    return wifiManager?.isWifiEnabled == true
}

// =========================================================================
// 2. 서비스 구독 (Subscribe)
// =========================================================================
@SuppressLint("MissingPermission")
internal fun WifiAwareRanger.subscribeToService() {
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
            val shouldUseDataPath = shouldUseDataPathForCall()

            // 기존 목록에 없으면 추가
            if (!foundPeers.contains(peerHandle)) {
                foundPeers.add(peerHandle)
            }
            if (currentTargetPeer == null && !shouldUseDataPath) {
                // 통화 컨텍스트가 없을 때만 RTT 측정을 위해 타깃 선점
                setCurrentTargetPeer(peerHandle)
            }

            if (isNdpInitiator && shouldUseDataPath && !_isConnectionReady.value && !isConnecting) {
                if (currentTargetPeer != null && currentTargetPeer != peerHandle) {
                    ConnectionLog.add("Aware", "service discovered ignored: target locked")
                    return
                }
                // initiator는 발견 즉시 NDP 요청 전송
                Log.i("WifiAware", "Service discovered. Sending NDP request...")
                ConnectionLog.add("Aware", "service discovered -> send ndp request")
                sendNdpRequestToPeer(peerHandle)
            } else if (!isNdpInitiator) {
                ConnectionLog.add("Aware", "service discovered -> wait initiator request")
            } else if (!shouldUseDataPath) {
                ConnectionLog.add(
                    "Aware",
                    "service discovered -> RTT only mode (no ndp) dataPath=$dataPathEnabled hasIds=${requiresAckBeforeConnect()}"
                )
            }
            maybeStartRtt("discover")
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
                ConnectionLog.add("Aware", "ndp ack overrides target lock")
            }
            setCurrentTargetPeer(peerHandle)
            ndpAckReceived = true
            ConnectionLog.add("Aware", "received ndp ack from target")
            maybeStartRtt("ack")
            if (!_isConnectionReady.value && !isConnecting && dataPathEnabled) {
                // ACK 수신 후 데이터 경로 연결 시도
                connectToCurrentPeer()
            }
        }
    }, handler)
}

// =========================================================================
// 2-1. 서비스 발행 (Publish)
// =========================================================================
@SuppressLint("MissingPermission")
internal fun WifiAwareRanger.publishToService() {
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
                ConnectionLog.add("Aware", "ndp request overrides target lock")
            }
            setCurrentTargetPeer(peerHandle)
            peerRequestedNdp = true
            val shouldUseDataPath = shouldUseDataPathForCall()
            if (!shouldUseDataPath) {
                ConnectionLog.add("Aware", "ignore ndp request: call context not ready")
                return
            }
            // responder는 ACK 송신 후 연결 시도
            sendNdpAckToPeer(peerHandle, signal.senderPeerId)
            ConnectionLog.add("Aware", "received ndp request from initiator")
            maybeStartRtt("request")
            if (!_isConnectionReady.value && !isConnecting && dataPathEnabled) {
                connectToCurrentPeer()
            }
        }
    }, handler)
}
