package com.example.lifesaiver.core.uwb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.core.uwb.UwbAvailabilityCallback
import androidx.core.uwb.UwbControleeSessionScope
import androidx.core.uwb.UwbControllerSessionScope
import androidx.core.uwb.UwbManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class UwbRanger(internal val context: Context) {
    data class ControllerOffer(
        val controllerAddress: String,
        val channel: Int,
        val preambleIndex: Int,
        val sessionId: Int
    )

    enum class SessionRole {
        CONTROLLER,
        CONTROLEE
    }

    internal val _distanceMeters = MutableStateFlow<Float?>(null)
    val distanceMeters = _distanceMeters.asStateFlow()

    internal val rangerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal val uwbManager by lazy { UwbManager.createInstance(context) }
    internal val lock = Any()
    internal val scopeCreationLock = Any()
    @Volatile internal var runtimeAvailableCache: Boolean? = null
    @Volatile internal var lastAvailabilityCheckAtMs: Long = 0L
    @Volatile internal var lastUnavailableLogAtMs: Long = 0L
    internal var availabilityCallbackRegistered = false
    internal var trackingEnabled = false
    internal var sessionRole = SessionRole.CONTROLLER
    internal var peerAddressHex: String? = null
    internal var remoteControllerAddressHex: String? = null
    internal var remoteControllerChannel: Int? = null
    internal var remoteControllerPreambleIndex: Int? = null
    internal var remoteSessionId: Int? = null
    internal var controllerScope: UwbControllerSessionScope? = null
    internal var controleeScope: UwbControleeSessionScope? = null
    internal var controllerOffer: ControllerOffer? = null
    internal var controleeAddressHex: String? = null
    internal var sessionJob: Job? = null
    internal var sessionToken: Long = 0L

    internal val availabilityCallback = object : UwbAvailabilityCallback {
        override fun onUwbStateChanged(isAvailable: Boolean, reason: Int) {
            runtimeAvailableCache = isAvailable
            lastAvailabilityCheckAtMs = SystemClock.elapsedRealtime()
            if (isAvailable) {
                logUwb("availability=true")
                restartSessionIfTracking()
            } else {
                if (SystemClock.elapsedRealtime() - lastUnavailableLogAtMs >= UNAVAILABLE_LOG_THROTTLE_MS) {
                    lastUnavailableLogAtMs = SystemClock.elapsedRealtime()
                    logUwb("availability=false reason=${availabilityReasonToText(reason)}")
                }
                synchronized(lock) { cancelSessionLocked() }
                _distanceMeters.value = null
            }
        }
    }

    fun isSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_UWB)
    }

    fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.UWB_RANGING
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isRuntimeAvailableCached(): Boolean? = isRuntimeAvailableCachedInternal()

    fun prepareControllerOfferBlocking(): ControllerOffer? = prepareControllerOfferBlockingInternal()

    fun prepareControleeAddressBlocking(): String? = prepareControleeAddressBlockingInternal()

    fun getControllerOfferOrNull(): ControllerOffer? {
        return synchronized(lock) { controllerOffer }
    }

    fun getControleeAddressOrNull(): String? {
        return synchronized(lock) { controleeAddressHex }
    }

    fun configureControllerSession(peerAddress: String?) {
        configureControllerSessionInternal(peerAddress)
    }

    fun configureControleeSession(
        controllerAddress: String?,
        channel: Int?,
        preambleIndex: Int?,
        sessionId: Int?
    ) {
        configureControleeSessionInternal(controllerAddress, channel, preambleIndex, sessionId)
    }

    fun start() {
        startInternal()
    }

    fun stop() {
        stopInternal()
    }

    fun endSession() {
        endSessionInternal()
    }

    fun release() {
        releaseInternal()
    }

    internal sealed class SessionSnapshot(val role: SessionRole) {
        class Controller(
            val scope: UwbControllerSessionScope,
            val peerAddress: ByteArray,
            val sessionId: Int
        ) : SessionSnapshot(SessionRole.CONTROLLER)

        class Controlee(
            val scope: UwbControleeSessionScope,
            val controllerAddress: ByteArray,
            val controllerChannel: Int,
            val controllerPreambleIndex: Int,
            val sessionId: Int
        ) : SessionSnapshot(SessionRole.CONTROLEE)
    }

    companion object {
        internal const val AVAILABILITY_CACHE_TTL_MS = 750L
        internal const val UNAVAILABLE_LOG_THROTTLE_MS = 3_000L
        internal const val STATIC_STS_SESSION_KEY_LENGTH = 8
        internal const val STATIC_STS_KEY_SALT = 0x5A5A5A5A
    }
}
