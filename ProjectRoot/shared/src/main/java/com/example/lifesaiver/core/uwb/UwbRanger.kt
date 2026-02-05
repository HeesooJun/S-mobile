package com.example.lifesaiver.core.uwb

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.uwb.RangingParameters
import androidx.core.uwb.RangingResult
import androidx.core.uwb.UwbAvailabilityCallback
import androidx.core.uwb.UwbAddress
import androidx.core.uwb.UwbComplexChannel
import androidx.core.uwb.UwbControleeSessionScope
import androidx.core.uwb.UwbControllerSessionScope
import androidx.core.uwb.UwbDevice
import androidx.core.uwb.UwbManager
import com.example.lifesaiver.core.log.ConnectionLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer

class UwbRanger(private val context: Context) {
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

    private val _distanceMeters = MutableStateFlow<Float?>(null)
    val distanceMeters = _distanceMeters.asStateFlow()

    private val rangerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uwbManager by lazy { UwbManager.createInstance(context) }
    private val lock = Any()
    private val scopeCreationLock = Any()
    @Volatile private var runtimeAvailableCache: Boolean? = null
    @Volatile private var lastAvailabilityCheckAtMs: Long = 0L
    @Volatile private var lastUnavailableLogAtMs: Long = 0L
    private var availabilityCallbackRegistered = false
    private var trackingEnabled = false
    private var sessionRole = SessionRole.CONTROLLER
    private var peerAddressHex: String? = null
    private var remoteControllerAddressHex: String? = null
    private var remoteControllerChannel: Int? = null
    private var remoteControllerPreambleIndex: Int? = null
    private var remoteSessionId: Int? = null
    private var controllerScope: UwbControllerSessionScope? = null
    private var controleeScope: UwbControleeSessionScope? = null
    private var controllerOffer: ControllerOffer? = null
    private var controleeAddressHex: String? = null
    private var sessionJob: Job? = null
    private var sessionToken: Long = 0L
    private val availabilityCallback = object : UwbAvailabilityCallback {
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

    fun prepareControllerOfferBlocking(): ControllerOffer? {
        if (!isSupported() || !hasPermission()) return null
        if (!isRuntimeAvailableBlocking()) return null
        synchronized(lock) {
            val cachedOffer = controllerOffer
            if (cachedOffer != null && controllerScope != null) {
                return cachedOffer
            }
        }
        val offer = synchronized(scopeCreationLock) {
            synchronized(lock) {
                val cachedOffer = controllerOffer
                if (cachedOffer != null && controllerScope != null) {
                    return@synchronized cachedOffer
                }
            }
            val scope = runBlocking(Dispatchers.IO) {
                runCatching {
                    uwbManager.controllerSessionScope()
                }.onFailure { throwable ->
                    logUwb("controller scope create failed", throwable)
                }.getOrNull()
            } ?: return@synchronized null
            val preparedOffer = ControllerOffer(
                controllerAddress = normalizeAddress(scope.localAddress.toString()) ?: return@synchronized null,
                channel = scope.uwbComplexChannel.channel,
                preambleIndex = scope.uwbComplexChannel.preambleIndex,
                sessionId = buildSessionId()
            )
            synchronized(lock) {
                val cachedOffer = controllerOffer
                if (cachedOffer != null && controllerScope != null) {
                    return@synchronized cachedOffer
                }
                controllerScope = scope
                controllerOffer = preparedOffer
                sessionRole = SessionRole.CONTROLLER
                peerAddressHex = null
                remoteControllerAddressHex = null
                remoteControllerChannel = null
                remoteControllerPreambleIndex = null
                remoteSessionId = null
            }
            preparedOffer
        } ?: return null
        restartSessionIfTracking()
        return offer
    }

    fun prepareControleeAddressBlocking(): String? {
        if (!isSupported() || !hasPermission()) return null
        if (!isRuntimeAvailableBlocking()) return null
        synchronized(lock) {
            val cachedAddress = controleeAddressHex
            if (cachedAddress != null && controleeScope != null) {
                return cachedAddress
            }
        }
        return synchronized(scopeCreationLock) {
            synchronized(lock) {
                val cachedAddress = controleeAddressHex
                if (cachedAddress != null && controleeScope != null) {
                    return@synchronized cachedAddress
                }
            }
            val scope = synchronized(lock) { controleeScope } ?: runBlocking(Dispatchers.IO) {
                runCatching {
                    uwbManager.controleeSessionScope()
                }.onFailure { throwable ->
                    logUwb("controlee scope create failed", throwable)
                }.getOrNull()
            } ?: return@synchronized null
            val address = normalizeAddress(scope.localAddress.toString()) ?: return@synchronized null
            synchronized(lock) {
                if (controleeScope == null) {
                    controleeScope = scope
                }
                controleeAddressHex = address
            }
            address
        }
    }

    fun getControllerOfferOrNull(): ControllerOffer? {
        return synchronized(lock) { controllerOffer }
    }

    fun getControleeAddressOrNull(): String? {
        return synchronized(lock) { controleeAddressHex }
    }

    fun configureControllerSession(peerAddress: String?) {
        synchronized(lock) {
            sessionRole = SessionRole.CONTROLLER
            peerAddressHex = normalizeAddress(peerAddress)
        }
        restartSessionIfTracking()
    }

    fun configureControleeSession(
        controllerAddress: String?,
        channel: Int?,
        preambleIndex: Int?,
        sessionId: Int?
    ) {
        synchronized(lock) {
            sessionRole = SessionRole.CONTROLEE
            remoteControllerAddressHex = normalizeAddress(controllerAddress)
            remoteControllerChannel = channel
            remoteControllerPreambleIndex = preambleIndex
            remoteSessionId = sessionId
        }
        restartSessionIfTracking()
    }

    fun start() {
        if (!isSupported() || !hasPermission()) {
            _distanceMeters.value = null
            synchronized(lock) { trackingEnabled = false }
            return
        }
        synchronized(lock) { trackingEnabled = true }
        startSessionIfReady()
    }

    fun stop() {
        synchronized(lock) {
            trackingEnabled = false
            cancelSessionLocked()
        }
        _distanceMeters.value = null
    }

    fun endSession() {
        synchronized(lock) {
            trackingEnabled = false
            cancelSessionLocked()
            controllerScope = null
            controleeScope = null
            controllerOffer = null
            controleeAddressHex = null
            peerAddressHex = null
            remoteControllerAddressHex = null
            remoteControllerChannel = null
            remoteControllerPreambleIndex = null
            remoteSessionId = null
        }
        _distanceMeters.value = null
    }

    fun release() {
        endSession()
        clearAvailabilityCallback()
        rangerScope.cancel()
    }

    private fun restartSessionIfTracking() {
        val shouldTrack = synchronized(lock) { trackingEnabled }
        if (!shouldTrack) return
        synchronized(lock) { cancelSessionLocked() }
        startSessionIfReady()
    }

    private fun startSessionIfReady() {
        var snapshot: SessionSnapshot? = null
        var token = 0L
        synchronized(lock) {
            if (!trackingEnabled || sessionJob != null) return
            snapshot = buildSnapshotLocked() ?: return
            sessionToken += 1L
            token = sessionToken
            sessionJob = rangerScope.launch {
                runSession(snapshot ?: return@launch, token)
            }
        }
    }

    private suspend fun runSession(snapshot: SessionSnapshot, token: Long) {
        runCatching {
            when (snapshot) {
                is SessionSnapshot.Controller -> collectController(snapshot)
                is SessionSnapshot.Controlee -> collectControlee(snapshot)
            }
        }.onFailure { throwable ->
            logUwb("session failed (${snapshot.role})", throwable)
            emitDistanceIfTracking(null)
        }
        synchronized(lock) {
            if (sessionToken == token) {
                sessionJob = null
            }
        }
    }

    private suspend fun collectController(snapshot: SessionSnapshot.Controller) {
        val sessionKeyInfo = buildStaticStsSessionKey(snapshot.sessionId)
        val parameters = RangingParameters(
            uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
            sessionId = snapshot.sessionId,
            subSessionId = 0,
            sessionKeyInfo = sessionKeyInfo,
            subSessionKeyInfo = null,
            complexChannel = null,
            peerDevices = listOf(UwbDevice(UwbAddress(snapshot.peerAddress))),
            updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
        )
        snapshot.scope.prepareSession(parameters).collect { result ->
            onRangingResult(result)
        }
    }

    private suspend fun collectControlee(snapshot: SessionSnapshot.Controlee) {
        val sessionKeyInfo = buildStaticStsSessionKey(snapshot.sessionId)
        val parameters = RangingParameters(
            uwbConfigType = RangingParameters.CONFIG_UNICAST_DS_TWR,
            sessionId = snapshot.sessionId,
            subSessionId = 0,
            sessionKeyInfo = sessionKeyInfo,
            subSessionKeyInfo = null,
            complexChannel = UwbComplexChannel(
                snapshot.controllerChannel,
                snapshot.controllerPreambleIndex
            ),
            peerDevices = listOf(UwbDevice(UwbAddress(snapshot.controllerAddress))),
            updateRateType = RangingParameters.RANGING_UPDATE_RATE_FREQUENT
        )
        snapshot.scope.prepareSession(parameters).collect { result ->
            onRangingResult(result)
        }
    }

    private fun onRangingResult(result: RangingResult) {
        when (result) {
            is RangingResult.RangingResultPosition -> {
                emitDistanceIfTracking(result.position.distance?.value)
            }

            is RangingResult.RangingResultPeerDisconnected -> {
                emitDistanceIfTracking(null)
            }

            is RangingResult.RangingResultInitialized -> Unit

            is RangingResult.RangingResultFailure -> {
                emitDistanceIfTracking(null)
            }
        }
    }

    private fun emitDistanceIfTracking(distance: Float?) {
        val enabled = synchronized(lock) { trackingEnabled }
        if (enabled) {
            _distanceMeters.value = distance
        }
    }

    private fun buildSnapshotLocked(): SessionSnapshot? {
        if (!trackingEnabled || !isSupported() || !hasPermission()) return null
        return when (sessionRole) {
            SessionRole.CONTROLLER -> {
                val offer = controllerOffer ?: return null
                val scope = controllerScope ?: return null
                val peerAddress = parseAddress(peerAddressHex) ?: return null
                SessionSnapshot.Controller(
                    scope = scope,
                    peerAddress = peerAddress,
                    sessionId = offer.sessionId
                )
            }

            SessionRole.CONTROLEE -> {
                val scope = controleeScope ?: return null
                val controllerAddress = parseAddress(remoteControllerAddressHex) ?: return null
                val controllerChannel = remoteControllerChannel ?: return null
                val controllerPreambleIndex = remoteControllerPreambleIndex ?: return null
                val sessionId = remoteSessionId ?: return null
                SessionSnapshot.Controlee(
                    scope = scope,
                    controllerAddress = controllerAddress,
                    controllerChannel = controllerChannel,
                    controllerPreambleIndex = controllerPreambleIndex,
                    sessionId = sessionId
                )
            }
        }
    }

    private fun cancelSessionLocked() {
        sessionToken += 1L
        sessionJob?.cancel()
        sessionJob = null
    }

    private fun parseAddress(rawAddress: String?): ByteArray? {
        val normalized = rawAddress?.trim()?.lowercase()?.replace('-', ':') ?: return null
        val parts = if (normalized.contains(':')) {
            normalized.split(':').filter { it.isNotBlank() }
        } else {
            if (normalized.length % 2 != 0) return null
            normalized.chunked(2)
        }
        if (parts.isEmpty()) return null
        if (parts.any { part ->
                (part.length != 1 && part.length != 2) ||
                    part.any { ch -> ch !in '0'..'9' && ch !in 'a'..'f' }
            }
        ) {
            return null
        }
        return ByteArray(parts.size) { index ->
            parts[index].padStart(2, '0').toInt(16).toByte()
        }
    }

    private fun normalizeAddress(rawAddress: String?): String? {
        val bytes = parseAddress(rawAddress) ?: return null
        return bytes.joinToString(":") { byte ->
            "%02x".format(byte.toInt() and 0xFF)
        }
    }

    private fun buildSessionId(): Int {
        val value = (System.currentTimeMillis() and 0x7FFFFFFF).toInt()
        return if (value == 0) 1 else value
    }

    private fun ensureAvailabilityCallbackRegistered() {
        if (!isSupported()) return
        synchronized(lock) {
            if (availabilityCallbackRegistered) return
            runCatching {
                uwbManager.setUwbAvailabilityCallback(
                    ContextCompat.getMainExecutor(context),
                    availabilityCallback
                )
                availabilityCallbackRegistered = true
            }.onFailure { throwable ->
                logUwb("availability callback register failed", throwable)
            }
        }
    }

    private fun clearAvailabilityCallback() {
        synchronized(lock) {
            if (!availabilityCallbackRegistered) return
            runCatching {
                uwbManager.clearUwbAvailabilityCallback()
            }.onFailure { throwable ->
                logUwb("availability callback clear failed", throwable)
            }
            availabilityCallbackRegistered = false
        }
    }

    private fun isRuntimeAvailableBlocking(forceRefresh: Boolean = false): Boolean {
        if (!isSupported() || !hasPermission()) return false
        ensureAvailabilityCallbackRegistered()
        val now = SystemClock.elapsedRealtime()
        val cached = runtimeAvailableCache
        if (!forceRefresh && cached != null && now - lastAvailabilityCheckAtMs < AVAILABILITY_CACHE_TTL_MS) {
            return cached
        }
        val available = runBlocking(Dispatchers.IO) {
            runCatching { uwbManager.isAvailable() }
                .onFailure { throwable ->
                    logUwb("availability query failed", throwable)
                }
                .getOrDefault(false)
        }
        runtimeAvailableCache = available
        lastAvailabilityCheckAtMs = now
        if (!available && now - lastUnavailableLogAtMs >= UNAVAILABLE_LOG_THROTTLE_MS) {
            lastUnavailableLogAtMs = now
            logUwb("runtime unavailable")
        }
        return available
    }

    private fun availabilityReasonToText(reason: Int): String {
        return when (reason) {
            UwbAvailabilityCallback.STATE_CHANGE_REASON_SYSTEM_POLICY -> "system_policy"
            UwbAvailabilityCallback.STATE_CHANGE_REASON_COUNTRY_CODE_ERROR -> "country_code_error"
            UwbAvailabilityCallback.STATE_CHANGE_REASON_UNKNOWN -> "unknown"
            else -> "code_$reason"
        }
    }

    private fun buildStaticStsSessionKey(sessionId: Int): ByteArray {
        return ByteBuffer.allocate(STATIC_STS_SESSION_KEY_LENGTH)
            .putInt(sessionId)
            .putInt(sessionId xor STATIC_STS_KEY_SALT)
            .array()
    }

    private fun logUwb(message: String, throwable: Throwable? = null) {
        val formatted = if (throwable != null) {
            "$message: ${throwable.message ?: throwable::class.java.simpleName}"
        } else {
            message
        }
        ConnectionLog.add("UWB", formatted)
        if (throwable != null) {
            Log.w("UwbRanger", message, throwable)
        } else {
            Log.d("UwbRanger", message)
        }
    }

    private sealed class SessionSnapshot(val role: SessionRole) {
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
        private const val AVAILABILITY_CACHE_TTL_MS = 750L
        private const val UNAVAILABLE_LOG_THROTTLE_MS = 3_000L
        private const val STATIC_STS_SESSION_KEY_LENGTH = 8
        private const val STATIC_STS_KEY_SALT = 0x5A5A5A5A
    }
}
