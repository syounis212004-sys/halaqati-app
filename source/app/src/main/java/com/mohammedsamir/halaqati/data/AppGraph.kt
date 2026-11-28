package com.mohammedsamir.halaqati.data

// The database implementation lives in :offline-db; its API and package stay stable.
import com.mohammedsamir.halaqati.data.HalaqatiOfflineDatabase

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

object AppGraph {
    lateinit var store: SecureSessionStore
        private set
    lateinit var api: SupabaseApi
        private set
    lateinit var queue: OfflineQueue
        private set
    lateinit var cache: LocalCache
        private set
    lateinit var offlineDb: HalaqatiOfflineDatabase
        private set
    lateinit var repo: HalaqatiRepository
        private set
    lateinit var uiPreferences: UiPreferencesStore
        private set
    lateinit var halaqaScope: HalaqaScopeStore
        private set
    lateinit var connectivity: ConnectivitySyncCoordinator
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        val appContext = context.applicationContext
        store = SecureSessionStore(appContext)
        uiPreferences = UiPreferencesStore(appContext)
        RefreshGate.init(appContext)
        halaqaScope = HalaqaScopeStore(appContext)
        offlineDb = HalaqatiOfflineDatabase.get(appContext)
        queue = OfflineQueue(offlineDb)
        cache = LocalCache(offlineDb)
        api = SupabaseApi(store)
        repo = HalaqatiRepository(appContext, api, store, queue, cache, offlineDb)
        connectivity = ConnectivitySyncCoordinator(appContext, appScope) { repo.syncNow() }.also { it.start() }

        // Never block first frame on migration; the old SQLite files remain untouched for rollback.
        appScope.launch { OfflineLegacyImporter.importIfNeeded(appContext, offlineDb) }

        // WorkManager initialization can touch disk / SQLite. Schedule it off the main thread so
        // cold start can draw the first frame from cache before background infrastructure wakes up.
        appScope.launch {
            val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            // RC27.34 (data usage): the full account pull used to run every 15 minutes on every device. Now every 3 hours.
            val work = PeriodicWorkRequestBuilder<FullSyncWorker>(3, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            val workManager = WorkManager.getInstance(appContext)
            workManager.cancelUniqueWork("halaqati-sync")
            workManager.enqueueUniquePeriodicWork(
                "halaqati-sync-3h",
                ExistingPeriodicWorkPolicy.KEEP,
                work,
            )
            // RC31: Outbox guard. If a write is still waiting (poor signal, backoff, killed process),
            // retry every 15 minutes instead of waiting for the 3-hour full sync.
            val guardWork = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "halaqati-outbox-guard",
                ExistingPeriodicWorkPolicy.KEEP,
                guardWork,
            )
            val alertWork = PeriodicWorkRequestBuilder<SmartPlanAlertsWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            workManager.enqueueUniquePeriodicWork(
                "halaqati-smart-plan-alerts",
                ExistingPeriodicWorkPolicy.KEEP,
                alertWork,
            )
        }
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        // Cached reads schedule this worker defensively. Avoid waking the network/full refresh path
        // when the durable Outbox is already empty.
        if (AppGraph.repo.pendingQueueSize() > 0) AppGraph.repo.syncNow()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}

class FullSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        AppGraph.repo.fullRefreshNow()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}

class SmartPlanAlertsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        AppGraph.repo.runSmartPlanAlerts()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }
}
