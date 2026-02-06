package com.example.lifesaivior.presentation

object AppShutdownHooks {
    @Volatile
    private var sendLeave: (() -> Unit)? = null
    @Volatile
    private var stopServices: (() -> Unit)? = null

    fun register(onSendLeave: () -> Unit, onStopServices: () -> Unit) {
        sendLeave = onSendLeave
        stopServices = onStopServices
    }

    fun clear() {
        sendLeave = null
        stopServices = null
    }

    fun requestShutdown() {
        val leave = sendLeave ?: return
        val stop = stopServices ?: return
        AppShutdownCoordinator.requestShutdown(
            onSendLeave = leave,
            onStopServices = stop
        )
    }
}
