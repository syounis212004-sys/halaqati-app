package com.mohammedsamir.halaqati.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * RC8.6 connectivity bridge.
 *
 * WorkManager remains the durability fallback, but a 15 minute worker is not acceptable for the
 * foreground experience. This observer listens for a validated network and flushes the outbox as
 * soon as connectivity comes back. The epoch lets Compose/ViewModels refresh only after a completed
 * synchronization instead of requiring an app restart.
 */
class ConnectivitySyncCoordinator(
    context: Context,
    private val scope: CoroutineScope,
    private val syncAction: suspend () -> Unit,
) {
    private val cm = context.applicationContext.getSystemService(ConnectivityManager::class.java)
    private val _online = MutableStateFlow(false)
    val online: StateFlow<Boolean> = _online.asStateFlow()

    private val _syncEpoch = MutableStateFlow(0L)
    val syncEpoch: StateFlow<Long> = _syncEpoch.asStateFlow()

    private val syncing = AtomicBoolean(false)
    private val foregroundReady = AtomicBoolean(false)
    private val lastAttemptAt = AtomicLong(0L)
    private var delayedJob: Job? = null

    private fun validated(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun updateAndMaybeSync(force: Boolean = false) {
        val nowOnline = validated()
        val wasOnline = _online.value
        _online.value = nowOnline
        if (!nowOnline || !foregroundReady.get()) return
        if (!force && wasOnline && System.currentTimeMillis() - lastAttemptAt.get() < 1_500L) return
        requestImmediateSync()
    }

    fun requestImmediateSync() {
        if (!validated()) {
            _online.value = false
            return
        }
        if (!syncing.compareAndSet(false, true)) return
        lastAttemptAt.set(System.currentTimeMillis())
        delayedJob?.cancel()
        delayedJob = scope.launch(Dispatchers.IO) {
            // Give Android a very short moment after Wi-Fi/mobile data validation so DNS/routes settle.
            delay(180)
            try {
                runCatching { syncAction() }
                    .onSuccess { _syncEpoch.value = _syncEpoch.value + 1L }
            } finally {
                _online.value = validated()
                syncing.set(false)
            }
        }
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = updateAndMaybeSync()
        override fun onLost(network: Network) { _online.value = validated() }
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            val usable = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (usable) updateAndMaybeSync() else _online.value = validated()
        }
    }

    fun start() {
        _online.value = validated()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cm.registerDefaultNetworkCallback(callback)
            } else {
                cm.registerNetworkCallback(
                    NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(),
                    callback,
                )
            }
        }
        // Do not launch a full account sync from Application.onCreate(). Android recommends
        // keeping process startup light; the foreground Activity arms immediate synchronization.
    }

    fun onForegroundReady() {
        foregroundReady.set(true)
        updateAndMaybeSync(force = true)
    }

    fun onForegroundStopped() {
        foregroundReady.set(false)
    }

    fun stop() { runCatching { cm.unregisterNetworkCallback(callback) } }
}
