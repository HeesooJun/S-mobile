package com.example.lifesaiver.core.ble

import com.example.lifesaiver.protocol.core.ProtocolConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class DeviceMonitoringManager(
    private val scope: CoroutineScope,
    private val disconnectCallback: (String) -> Unit,
    private val log: (String) -> Unit = {},
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private data class BlockEntry(val expiresAtMs: Long, val reason: String)

    private val blocked = ConcurrentHashMap<String, BlockEntry>()
    private val announceTimers = ConcurrentHashMap<String, Job>()
    private val inactivityTimers = ConcurrentHashMap<String, Job>()
    private val unblockTimers = ConcurrentHashMap<String, Job>()
    private val errorDisconnects = ConcurrentHashMap<String, ArrayDeque<Long>>()

    @Synchronized
    fun isBlocked(address: String): Boolean {
        val entry = blocked[address] ?: return false
        if (entry.expiresAtMs <= clock()) {
            unblockInternal(address, logEvent = false)
            return false
        }
        return true
    }

    @Synchronized
    fun onConnectionEstablished(address: String) {
        if (isBlocked(address)) {
            disconnectCallback(address)
            return
        }
        startAnnounceTimer(address)
        startInactivityTimer(address)
    }

    @Synchronized
    fun onAnnounceReceived(address: String) {
        cancelTimer(announceTimers, address)
    }

    @Synchronized
    fun onAnyPacketReceived(address: String) {
        if (isBlocked(address)) return
        startInactivityTimer(address)
    }

    @Synchronized
    fun onDeviceDisconnected(address: String, isError: Boolean) {
        cancelTimer(announceTimers, address)
        cancelTimer(inactivityTimers, address)
        if (!isError) return
        val now = clock()
        val history = errorDisconnects.getOrPut(address) { ArrayDeque() }
        while (history.isNotEmpty() && now - history.first() > ProtocolConstants.Ble.ERROR_DISCONNECT_WINDOW_MS) {
            history.removeFirst()
        }
        history.addLast(now)
        if (history.size >= ProtocolConstants.Ble.ERROR_DISCONNECT_THRESHOLD) {
            block(address, "연결 오류 반복 감지")
        }
    }

    @Synchronized
    fun block(address: String, reason: String) {
        if (isBlocked(address)) return
        val expiresAt = clock() + ProtocolConstants.Ble.BLOCKLIST_TTL_MS
        blocked[address] = BlockEntry(expiresAt, reason)
        cancelTimer(announceTimers, address)
        cancelTimer(inactivityTimers, address)
        disconnectCallback(address)
        scheduleUnblock(address, expiresAt)
        log("차단됨: $address ($reason)")
    }

    @Synchronized
    fun clearAll() {
        blocked.clear()
        errorDisconnects.clear()
        cancelAll(announceTimers)
        cancelAll(inactivityTimers)
        cancelAll(unblockTimers)
    }

    private fun startAnnounceTimer(address: String) {
        cancelTimer(announceTimers, address)
        announceTimers[address] = scope.launch {
            delay(ProtocolConstants.Ble.ANNOUNCE_WAIT_MS)
            block(address, "ANNOUNCE 미수신")
        }
    }

    private fun startInactivityTimer(address: String) {
        cancelTimer(inactivityTimers, address)
        inactivityTimers[address] = scope.launch {
            delay(ProtocolConstants.Ble.INACTIVITY_TIMEOUT_MS)
            block(address, "활동 없음")
        }
    }

    private fun scheduleUnblock(address: String, expiresAt: Long) {
        cancelTimer(unblockTimers, address)
        val delayMs = (expiresAt - clock()).coerceAtLeast(0)
        unblockTimers[address] = scope.launch {
            delay(delayMs)
            unblockInternal(address, logEvent = true)
        }
    }

    private fun cancelTimer(map: ConcurrentHashMap<String, Job>, address: String) {
        map.remove(address)?.cancel()
    }

    private fun cancelAll(map: ConcurrentHashMap<String, Job>) {
        map.values.forEach { it.cancel() }
        map.clear()
    }

    private fun unblockInternal(address: String, logEvent: Boolean) {
        blocked.remove(address)
        errorDisconnects.remove(address)
        cancelTimer(unblockTimers, address)
        if (logEvent) {
            log("차단 해제됨: $address")
        }
    }
}
