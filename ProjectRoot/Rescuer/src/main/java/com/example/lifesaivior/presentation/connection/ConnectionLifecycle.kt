package com.example.lifesaivior.presentation.connection

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object ConnectionLifecycle {
    fun disconnect(
        sendLeave: () -> Unit,
        clearCaches: (String) -> Unit,
        stopRescueSignal: () -> Unit,
        disconnectBle: () -> Unit,
        setDisconnecting: (Boolean) -> Unit,
        scope: CoroutineScope,
        delayMs: Long = 200L
    ) {
        setDisconnecting(true)
        sendLeave()
        clearCaches("disconnect")
        stopRescueSignal()
        scope.launch {
            delay(delayMs)
            disconnectBle()
            setDisconnecting(false)
        }
    }

    fun sendLeaveAndClear(
        sendLeave: () -> Unit,
        clearCaches: (String) -> Unit,
        reason: String
    ) {
        sendLeave()
        clearCaches(reason)
    }
}
