package com.mohammedsamir.halaqati.documents

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * RC15.2: PDF/certificate exports run in a process-level scope, never in a Composable's
 * rememberCoroutineScope(). A recomposition, a scroll that disposes a LazyColumn item, or a
 * screen transition can no longer cancel a half-written PDF ("The coroutine scope left the
 * composition"). The renderer still serialises exports with its own mutex.
 */
object DocumentExportManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val active = AtomicInteger(0)
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch {
        _running.value = active.incrementAndGet() > 0
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("HalaqatiPdf", "document export crashed", e)
        } finally {
            _running.value = active.decrementAndGet() > 0
        }
    }
}
