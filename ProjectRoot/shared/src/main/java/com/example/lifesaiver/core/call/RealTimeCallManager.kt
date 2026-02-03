package com.example.lifesaiver.core.call

import android.util.Log
import com.example.lifesaiver.core.audio.RealtimeAudioStreamEngine
import com.example.lifesaiver.core.log.ConnectionLog
import com.example.lifesaiver.core.wifi.WifiAwareRanger
import com.example.lifesaiver.core.wifi.WifiDirectRanger
import com.example.lifesaiver.protocol.model.CallHandshakeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CallTransportType {
    WIFI_AWARE,
    WIFI_DIRECT,
    NONE
}

class RealTimeCallManager(
    private val audioEngine: RealtimeAudioStreamEngine,
    private val wifiAwareRanger: WifiAwareRanger,
    private val wifiDirectRanger: WifiDirectRanger
) {
    private val _isTransmitting = MutableStateFlow(false)
    val isTransmitting = _isTransmitting.asStateFlow()

    private val _debugState = MutableStateFlow(CallDebugState())
    val debugState = _debugState.asStateFlow()

    private val _callAttemptState = MutableStateFlow<CallHandshakeState?>(null)
    val callAttemptState = _callAttemptState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var localWifiAwareSupported = false
    private var localWifiDirectSupported = false
    private var peerWifiAwareSupported = false
    private var peerWifiDirectSupported = false
    private val wifiAwareEnabled = true
    private var awareTemporarilyDisabled = false
    private var activeTransport = CallTransportType.NONE
    private var isServer = true
    private var isStreaming = false
    private var pendingStart = false
    private var isCallActive = false
    private val awareAttemptTimeoutMs = 10_000L
    private val awareRetryCount = 2
    private val awareRetryDelayMs = 1_200L
    private val directAttemptTimeoutMs = 30_000L
    private var attemptToken = 0L
    private var lastAwareFailureReason: String? = null
    private var lastAwareFailureAt = 0L

    init {
        wifiAwareRanger.onAudioDataReceived = { audioBytes ->
            if (activeTransport == CallTransportType.WIFI_AWARE) {
                audioEngine.playReceivedAudio(audioBytes)
            }
        }
        wifiDirectRanger.onAudioDataReceived = { audioBytes ->
            if (activeTransport == CallTransportType.WIFI_DIRECT) {
                audioEngine.playReceivedAudio(audioBytes)
            }
        }
        scope.launch {
            wifiAwareRanger.debugStats.collect { stats ->
                _debugState.update { it.copy(wifiAware = stats) }
                val awareFailureReason = stats.lastSendError
                if (!awareFailureReason.isNullOrBlank() &&
                    (awareFailureReason.contains("ndp_unavailable", ignoreCase = true) ||
                        awareFailureReason.contains("requestNetwork", ignoreCase = true))
                ) {
                    onAwareFailure(awareFailureReason)
                }
            }
        }
        scope.launch {
            wifiDirectRanger.debugStats.collect { stats ->
                _debugState.update { it.copy(wifiDirect = stats) }
            }
        }
        scope.launch {
            audioEngine.debugStats.collect { stats ->
                _debugState.update { it.copy(audio = stats) }
            }
        }
        scope.launch {
            combine(
                wifiAwareRanger.isConnectionReady,
                wifiDirectRanger.isConnectionReady
            ) { awareReady, directReady ->
                awareReady || directReady
            }.collect { ready ->
                if (ready && _isTransmitting.value && activeTransport != CallTransportType.NONE) {
                    ensureStreaming()
                }
                if (!ready && isStreaming) {
                    audioEngine.stopStreaming()
                    isStreaming = false
                    ConnectionLog.add("Call", "streaming stopped (transport not ready)")
                }
            }
        }
    }

    fun updateLocalCapabilities(wifiAwareSupported: Boolean, wifiDirectSupported: Boolean) {
        localWifiAwareSupported = wifiAwareSupported
        localWifiDirectSupported = wifiDirectSupported
        _debugState.update {
            it.copy(
                lastDecision = "local: aware=${localWifiAwareSupported} direct=$wifiDirectSupported"
            )
        }
        if (pendingStart) {
            startCallAttemptSequence()
        } else if (activeTransport != CallTransportType.NONE) {
            ensureTransport()
        }
    }

    fun updatePeerCapabilities(wifiAwareSupported: Boolean, wifiDirectSupported: Boolean) {
        val awareSupported = wifiAwareSupported
        Log.d(
            "RealTimeCall",
            "Peer capabilities: wifiAware=$awareSupported wifiDirect=$wifiDirectSupported"
        )
        ConnectionLog.add("Call", "peer caps aware=$awareSupported direct=$wifiDirectSupported")
        peerWifiAwareSupported = awareSupported
        peerWifiDirectSupported = wifiDirectSupported
        if (wifiAwareEnabled) {
            wifiAwareRanger.updatePeerCapability(awareSupported)
        }
        if (pendingStart) {
            startCallAttemptSequence()
        } else if (activeTransport != CallTransportType.NONE) {
            ensureTransport()
        }
    }

    fun setUseOpus(enabled: Boolean) {
        audioEngine.setPreferredOpus(enabled)
        _debugState.update { it.copy(lastDecision = "opus=$enabled") }
        ConnectionLog.add("Call", "opus=$enabled")
    }

    fun setSpeakerphoneEnabled(enabled: Boolean) {
        audioEngine.setSpeakerphoneEnabled(enabled)
        _debugState.update { it.copy(lastDecision = "speaker=$enabled") }
        ConnectionLog.add("Call", "speaker=$enabled")
    }

    fun isSpeakerphoneEnabled(): Boolean = audioEngine.isSpeakerphoneEnabled()

    fun setServerRole(isServer: Boolean) {
        this.isServer = isServer
        if (wifiAwareEnabled) {
            wifiAwareRanger.setNdpInitiator(isServer)
        }
        _debugState.update { it.copy(lastDecision = "role: ${if (isServer) "server" else "client"}") }
    }

    fun configureDirectConnection(targetDeviceAddress: String?, lockToFirstPeer: Boolean = true) {
        wifiDirectRanger.setTargetDeviceAddress(targetDeviceAddress)
        wifiDirectRanger.setLockToFirstPeer(lockToFirstPeer)
        _debugState.update {
            it.copy(
                lastDecision = if (targetDeviceAddress.isNullOrBlank()) {
                    "direct target: none"
                } else {
                    "direct target: $targetDeviceAddress"
                }
            )
        }
    }

    fun startCallSession(continuousTransmission: Boolean = false): CallTransportType {
        Log.d("RealTimeCall", "Session Start. Continuous: $continuousTransmission")
        ConnectionLog.add("Call", "start session, continuous=$continuousTransmission")
        if (continuousTransmission) {
            _isTransmitting.value = true
        }
        _debugState.update { it.copy(isTransmitting = _isTransmitting.value) }
        isCallActive = true
        awareTemporarilyDisabled = false
        lastAwareFailureReason = null
        lastAwareFailureAt = 0L
        pendingStart = true
        val possible = selectTransport()
        if (possible == CallTransportType.NONE) {
            ConnectionLog.add("Call", "no transport selected")
            return CallTransportType.NONE
        }
        startCallAttemptSequence()
        return possible
    }

    fun setTransmissionEnabled(enabled: Boolean) {
        _isTransmitting.value = enabled
        _debugState.update { it.copy(isTransmitting = enabled) }
    }

    fun stopCallSession() {
        Log.d("RealTimeCall", "Session Stop")
        ConnectionLog.add("Call", "stop session")
        _isTransmitting.value = false
        pendingStart = false
        isCallActive = false
        awareTemporarilyDisabled = false
        activeTransport = CallTransportType.NONE
        audioEngine.stopStreaming()
        if (wifiAwareEnabled) {
            wifiAwareRanger.stop()
        }
        wifiDirectRanger.stop()
        wifiDirectRanger.setTargetDeviceAddress(null)
        wifiDirectRanger.setLockToFirstPeer(false)
        isStreaming = false
        updateAttemptState(null)
        _debugState.update {
            it.copy(
                activeTransport = CallTransportType.NONE,
                isTransmitting = false,
                lastDecision = "stopped"
            )
        }
    }

    fun forceStartWifiDirectTest(): Boolean {
        Log.d("RealTimeCall", "Force Wi-Fi Direct test start")
        ConnectionLog.add("Call", "force wifi-direct test start")
        pendingStart = false
        _isTransmitting.value = true
        stopActiveTransport()
        activeTransport = CallTransportType.WIFI_DIRECT
        wifiDirectRanger.start(isClient = !isServer)
        if (!isStreaming) {
            audioEngine.startStreaming { pcmData ->
                if (_isTransmitting.value) {
                    wifiDirectRanger.sendAudio(pcmData)
                }
            }
            isStreaming = true
        }
        _debugState.update {
            it.copy(
                activeTransport = CallTransportType.WIFI_DIRECT,
                isTransmitting = true,
                lastDecision = "force wifi-direct test"
            )
        }
        return true
    }

    private fun ensureTransport(): CallTransportType {
        val selected = selectTransport()
        if (selected == CallTransportType.NONE) {
            if (activeTransport != CallTransportType.NONE) {
                stopActiveTransport()
            }
            _debugState.update { it.copy(activeTransport = CallTransportType.NONE, lastDecision = "no transport") }
            ConnectionLog.add("Call", "transport=NONE")
            return CallTransportType.NONE
        }
        if (activeTransport != selected) {
            stopActiveTransport()
            startTransport(selected)
            activeTransport = selected
            _debugState.update { it.copy(activeTransport = selected, lastDecision = "use ${selected.name}") }
            ConnectionLog.add("Call", "transport=${selected.name}")
        }
        pendingStart = false
        return selected
    }

    private fun ensureStreaming() {
        if (isStreaming) return
        if (!isActiveTransportReady()) return
        audioEngine.startStreaming { pcmData ->
            if (_isTransmitting.value) {
                when (activeTransport) {
                    CallTransportType.WIFI_AWARE -> wifiAwareRanger.sendAudio(pcmData)
                    CallTransportType.WIFI_DIRECT -> wifiDirectRanger.sendAudio(pcmData)
                    CallTransportType.NONE -> Unit
                }
            }
        }
        isStreaming = true
        ConnectionLog.add("Call", "streaming started")
    }

    private fun isActiveTransportReady(): Boolean {
        return when (activeTransport) {
            CallTransportType.WIFI_AWARE -> wifiAwareEnabled && wifiAwareRanger.isConnectionReady.value
            CallTransportType.WIFI_DIRECT -> wifiDirectRanger.isConnectionReady.value
            CallTransportType.NONE -> false
        }
    }

    private fun selectTransport(): CallTransportType {
        val wifiAwarePossible = wifiAwareEnabled &&
            !awareTemporarilyDisabled &&
            localWifiAwareSupported &&
            peerWifiAwareSupported
        if (wifiAwarePossible) return CallTransportType.WIFI_AWARE
        val wifiDirectPossible = localWifiDirectSupported && peerWifiDirectSupported
        if (wifiDirectPossible) return CallTransportType.WIFI_DIRECT
        return CallTransportType.NONE
    }

    private fun startTransport(transport: CallTransportType) {
        when (transport) {
            CallTransportType.WIFI_AWARE -> {
                if (!wifiAwareEnabled) return
                wifiAwareRanger.setDataPathEnabled(true)
                wifiAwareRanger.setNdpInitiator(isServer)
                wifiAwareRanger.updatePeerCapability(peerWifiAwareSupported)
                wifiAwareRanger.start()
            }
            CallTransportType.WIFI_DIRECT -> {
                wifiDirectRanger.start(isClient = !isServer)
            }
            CallTransportType.NONE -> Unit
        }
    }

    private fun stopActiveTransport() {
        when (activeTransport) {
            CallTransportType.WIFI_AWARE -> if (wifiAwareEnabled) wifiAwareRanger.stop()
            CallTransportType.WIFI_DIRECT -> wifiDirectRanger.stop()
            CallTransportType.NONE -> Unit
        }
    }

    private fun startCallAttemptSequence() {
        if (!isCallActive) return
        attemptToken = System.currentTimeMillis()
        val token = attemptToken
        scope.launch {
            resetTransports("attempt start")
            val awarePossible = wifiAwareEnabled &&
                !awareTemporarilyDisabled &&
                localWifiAwareSupported &&
                peerWifiAwareSupported
            if (awarePossible) {
                var awareReady = false
                val totalAwareAttempts = awareRetryCount + 1
                for (attempt in 1..totalAwareAttempts) {
                    if (!isCallActive || token != attemptToken) return@launch
                    updateAttemptState(CallHandshakeState.AWARE_TRY)
                    _debugState.update {
                        it.copy(
                            activeTransport = CallTransportType.WIFI_AWARE,
                            lastDecision = "aware try $attempt/$totalAwareAttempts"
                        )
                    }
                    ConnectionLog.add("Call", "aware try $attempt/$totalAwareAttempts")
                    startTransport(CallTransportType.WIFI_AWARE)
                    activeTransport = CallTransportType.WIFI_AWARE
                    awareReady = waitForReady(token, CallTransportType.WIFI_AWARE)
                    if (awareReady) break
                    if (!isCallActive || token != attemptToken) return@launch
                    if (wifiAwareEnabled) {
                        wifiAwareRanger.stop()
                    }
                    activeTransport = CallTransportType.NONE
                    if (attempt < totalAwareAttempts) {
                        val backoff = awareRetryDelayMs * attempt
                        ConnectionLog.add("Call", "aware retry scheduled in ${backoff}ms")
                        delay(backoff)
                    }
                }
                if (awareReady) {
                    updateAttemptState(CallHandshakeState.AWARE_OK)
                    ensureStreaming()
                    pendingStart = false
                    return@launch
                }
                if (token != attemptToken) {
                    return@launch
                }
                updateAttemptState(CallHandshakeState.AWARE_FAIL)
                ConnectionLog.add("Call", "aware timeout after retries")
                awareTemporarilyDisabled = true
                _debugState.update { it.copy(lastDecision = "aware disabled -> direct fallback") }
                ConnectionLog.add("Call", "aware disabled for current session")
            }

            val directPossible = localWifiDirectSupported && peerWifiDirectSupported
            if (directPossible) {
                updateAttemptState(CallHandshakeState.DIRECT_TRY)
                startTransport(CallTransportType.WIFI_DIRECT)
                activeTransport = CallTransportType.WIFI_DIRECT
                _debugState.update { it.copy(activeTransport = CallTransportType.WIFI_DIRECT) }
                val directReady = waitForReady(token, CallTransportType.WIFI_DIRECT)
                if (directReady) {
                    updateAttemptState(CallHandshakeState.DIRECT_OK)
                    ensureStreaming()
                    pendingStart = false
                    return@launch
                }
                if (token != attemptToken) {
                    return@launch
                }
                updateAttemptState(CallHandshakeState.DIRECT_FAIL)
                ConnectionLog.add("Call", "direct timeout")
            }

            ConnectionLog.add("Call", "no transport ready -> stop")
            stopCallSession()
        }
    }

    private suspend fun waitForReady(
        token: Long,
        transport: CallTransportType,
        timeoutOverrideMs: Long? = null
    ): Boolean {
        val start = System.currentTimeMillis()
        val timeoutMs = timeoutOverrideMs ?: when (transport) {
            CallTransportType.WIFI_AWARE -> awareAttemptTimeoutMs
            CallTransportType.WIFI_DIRECT -> directAttemptTimeoutMs
            CallTransportType.NONE -> directAttemptTimeoutMs
        }
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!isCallActive || token != attemptToken) return false
            val ready = when (transport) {
                CallTransportType.WIFI_AWARE -> wifiAwareEnabled && wifiAwareRanger.isConnectionReady.value
                CallTransportType.WIFI_DIRECT -> wifiDirectRanger.isConnectionReady.value
                CallTransportType.NONE -> false
            }
            if (ready) return true
            delay(200L)
        }
        return false
    }

    private fun resetTransports(reason: String) {
        ConnectionLog.add("Call", "reset transports ($reason)")
        if (wifiAwareEnabled) {
            wifiAwareRanger.stop()
        }
        wifiDirectRanger.stop()
        activeTransport = CallTransportType.NONE
    }

    private fun updateAttemptState(state: CallHandshakeState?) {
        if (_callAttemptState.value == state) return
        _callAttemptState.value = state
        ConnectionLog.add("Call", "attempt state=${state?.name ?: "NONE"}")
    }

    private fun onAwareFailure(reason: String) {
        if (!isCallActive || !wifiAwareEnabled || awareTemporarilyDisabled) return
        val now = System.currentTimeMillis()
        if (reason == lastAwareFailureReason && now - lastAwareFailureAt < 3_000L) return
        lastAwareFailureReason = reason
        lastAwareFailureAt = now
        awareTemporarilyDisabled = true
        ConnectionLog.add("Call", "aware failure -> direct fallback ($reason)")
        _debugState.update { it.copy(lastDecision = "aware failure -> direct ($reason)") }
        if (activeTransport == CallTransportType.WIFI_AWARE || pendingStart) {
            startCallAttemptSequence()
        }
    }
}
