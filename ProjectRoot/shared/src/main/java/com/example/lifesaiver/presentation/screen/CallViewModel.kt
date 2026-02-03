package com.example.lifesaiver.presentation.screen

import androidx.lifecycle.ViewModel
import com.example.lifesaiver.core.call.CallTransportType
import com.example.lifesaiver.core.call.RealTimeCallManager
import com.example.lifesaiver.core.profile.SurvivorProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class CallViewModel(
    private val callManager: RealTimeCallManager
) : ViewModel() {

    private val _isInCall = MutableStateFlow(false)
    val isInCall = _isInCall.asStateFlow()

    private val _targetSurvivor = MutableStateFlow<SurvivorProfile?>(null)
    val targetSurvivor = _targetSurvivor.asStateFlow()

    private val _pendingTarget = MutableStateFlow<SurvivorProfile?>(null)
    val pendingTarget = _pendingTarget.asStateFlow()

    val debugState = callManager.debugState
    private val _isDirectTestRunning = MutableStateFlow(false)
    val isDirectTestRunning = _isDirectTestRunning.asStateFlow()

    fun requestCall(survivor: SurvivorProfile) {
        _pendingTarget.value = survivor
    }

    fun clearPendingCall() {
        _pendingTarget.value = null
    }

    fun startRealTimeCall(
        survivor: SurvivorProfile,
        localWifiAwareSupported: Boolean,
        localWifiDirectSupported: Boolean,
        peerWifiAwareSupported: Boolean,
        peerWifiDirectSupported: Boolean,
        isServer: Boolean,
        useOpus: Boolean,
        targetDirectAddress: String? = null
    ): Boolean {
        if (_isInCall.value) {
            callManager.stopCallSession()
        }
        _targetSurvivor.value = survivor
        _isInCall.value = true
        callManager.configureDirectConnection(targetDirectAddress)
        callManager.setServerRole(isServer)
        callManager.setUseOpus(useOpus)

        callManager.updateLocalCapabilities(
            wifiAwareSupported = localWifiAwareSupported,
            wifiDirectSupported = localWifiDirectSupported
        )
        callManager.updatePeerCapabilities(
            wifiAwareSupported = peerWifiAwareSupported,
            wifiDirectSupported = peerWifiDirectSupported
        )
        val transport = callManager.startCallSession(continuousTransmission = true)
        if (transport == CallTransportType.NONE) {
            endCall()
            return false
        }
        return true
    }

    fun endCall() {
        callManager.stopCallSession()
        _isInCall.value = false
        _targetSurvivor.value = null
        _isDirectTestRunning.value = false
    }

    override fun onCleared() {
        super.onCleared()
        endCall() // ViewModel 파괴 시 통화 자동 종료
    }

    fun updatePeerCapability(supportsWifiAware: Boolean, supportsWifiDirect: Boolean) {
        callManager.updatePeerCapabilities(
            wifiAwareSupported = supportsWifiAware,
            wifiDirectSupported = supportsWifiDirect
        )
    }

    fun startWifiDirectTest(localWifiDirectSupported: Boolean): Boolean {
        if (!localWifiDirectSupported) return false
        val started = callManager.forceStartWifiDirectTest()
        if (started) {
            _isDirectTestRunning.value = true
        }
        return started
    }

    fun stopWifiDirectTest() {
        callManager.stopCallSession()
        _isDirectTestRunning.value = false
    }

    fun setSpeakerphoneEnabled(enabled: Boolean) {
        callManager.setSpeakerphoneEnabled(enabled)
    }

    fun toggleSpeakerphone() {
        callManager.setSpeakerphoneEnabled(!callManager.isSpeakerphoneEnabled())
    }
}
