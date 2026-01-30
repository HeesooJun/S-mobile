package com.example.lifesaiver.presentation

import android.os.Process
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object AppShutdownCoordinator {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val shutdownToken = AtomicLong(0L)
    @Volatile
    private var shutdownJob: Job? = null

    fun requestShutdown(
        onSendLeave: () -> Unit,
        onStopServices: () -> Unit
    ) {
        val token = shutdownToken.incrementAndGet()
        shutdownJob?.cancel()
        val job = scope.launch {
            try { onSendLeave() } catch (_: Exception) { }
            delay(200)
            if (!isActive || shutdownToken.get() != token) return@launch
            try { onStopServices() } catch (_: Exception) { }
            delay(100)
            if (!isActive || shutdownToken.get() != token) return@launch
            try { Process.killProcess(Process.myPid()) } catch (_: Exception) { }
            try { System.exit(0) } catch (_: Exception) { }
        }
        shutdownJob = job
        job.invokeOnCompletion {
            if (shutdownJob === job) {
                shutdownJob = null
            }
        }
    }
}
