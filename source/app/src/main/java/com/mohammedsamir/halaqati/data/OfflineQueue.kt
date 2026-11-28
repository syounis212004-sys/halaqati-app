package com.mohammedsamir.halaqati.data

// The database implementation lives in :offline-db; its API and package stay stable.
import com.mohammedsamir.halaqati.data.HalaqatiOfflineDatabase

/** Durable account-scoped Room Outbox used by every offline-first mutation. */
class OfflineQueue(private val db: HalaqatiOfflineDatabase) {
    fun add(
        ownerUserId: String,
        method: String,
        path: String,
        body: String?,
        dedupe: String? = null,
        baseUpdatedAt: String? = null,
        entityTable: String? = null,
        entityId: String? = null,
        preserveExisting: Boolean = false,
    ) {
        require(ownerUserId.isNotBlank()) { "Offline queue requires an authenticated account" }
        val key = dedupe ?: "${method.uppercase()}:$path:${body.orEmpty().hashCode()}"
        val row = OutboxEntity(
            ownerUserId = ownerUserId.trim(), method = method.uppercase(), path = path,
            body = body, createdAt = System.currentTimeMillis(), dedupe = key,
            baseUpdatedAt = baseUpdatedAt, entityTable = entityTable, entityId = entityId,
        )
        if (preserveExisting) db.outbox().insertIfAbsent(row) else db.outbox().replaceDedupe(row)
    }

    fun size(ownerUserId: String): Int = if (ownerUserId.isBlank()) 0 else db.outbox().count(ownerUserId, OutboxState.PENDING)
    fun blockedSize(ownerUserId: String): Int = if (ownerUserId.isBlank()) 0 else
        db.outbox().count(ownerUserId, OutboxState.BLOCKED) + db.outbox().count(ownerUserId, OutboxState.CONFLICT)
    fun oldestPendingAt(ownerUserId: String): Long? = if (ownerUserId.isBlank()) null else db.outbox().oldestPendingAt(ownerUserId)
    fun conflictSize(ownerUserId: String): Int = if (ownerUserId.isBlank()) 0 else db.outbox().count(ownerUserId, OutboxState.CONFLICT)

    data class Item(
        val id: Long, val method: String, val path: String, val body: String?, val attempts: Int,
        val baseUpdatedAt: String?, val entityTable: String?, val entityId: String?, val forceLocal: Boolean,
    )
    data class SnapshotItem(
        val id: Long, val method: String, val path: String, val attempts: Int, val blocked: Boolean,
        val conflict: Boolean, val lastError: String?, val created: Long, val dedupe: String?, val body: String?,
        val serverJson: String?, val entityTable: String?, val entityId: String?, val baseUpdatedAt: String?,
    )

    fun all(ownerUserId: String, limit: Int = 100): List<Item> = if (ownerUserId.isBlank()) emptyList() else
        db.outbox().pending(ownerUserId, limit).map {
            Item(it.id, it.method, it.path, it.body, it.attempts, it.baseUpdatedAt, it.entityTable, it.entityId, it.forceLocal)
        }

    fun snapshot(ownerUserId: String, limit: Int = 200): List<SnapshotItem> = if (ownerUserId.isBlank()) emptyList() else
        db.outbox().snapshot(ownerUserId, limit).map {
            SnapshotItem(
                it.id, it.method, it.path, it.attempts,
                blocked = it.state != OutboxState.PENDING,
                conflict = it.state == OutboxState.CONFLICT,
                lastError = it.lastError, created = it.createdAt, dedupe = it.dedupe, body = it.body,
                serverJson = it.serverJson, entityTable = it.entityTable, entityId = it.entityId, baseUpdatedAt = it.baseUpdatedAt,
            )
        }

    fun retryBlocked(id: Long, ownerUserId: String) { if (ownerUserId.isNotBlank()) db.outbox().retry(ownerUserId, id) }
    fun forceLocal(id: Long, ownerUserId: String) { if (ownerUserId.isNotBlank()) db.outbox().forceLocal(ownerUserId, id) }
    fun discard(id: Long, ownerUserId: String) { if (ownerUserId.isNotBlank()) db.outbox().delete(ownerUserId, id) }
    fun discardDedupe(ownerUserId: String, dedupe: String) {
        if (ownerUserId.isNotBlank() && dedupe.isNotBlank()) db.outbox().deleteDedupe(ownerUserId, dedupe)
    }
    fun discardPendingDedupesIfComplete(ownerUserId: String, dedupes: List<String>): Boolean {
        val normalized = dedupes.map(String::trim).filter(String::isNotBlank).distinct()
        if (ownerUserId.isBlank() || normalized.isEmpty()) return false
        return db.outbox().deletePendingDedupesIfComplete(ownerUserId, normalized)
    }
    fun done(id: Long, ownerUserId: String) = discard(id, ownerUserId)
    fun fail(id: Long, ownerUserId: String, error: String) { if (ownerUserId.isNotBlank()) db.outbox().incrementAttempts(ownerUserId, id, error.take(1000)) }
    fun markBlocked(id: Long, ownerUserId: String, error: String) { if (ownerUserId.isNotBlank()) db.outbox().block(ownerUserId, id, error.take(1000)) }
    fun markConflict(id: Long, ownerUserId: String, error: String, serverJson: String?) {
        if (ownerUserId.isNotBlank()) db.outbox().conflict(ownerUserId, id, error.take(1000), serverJson)
    }
    fun item(id: Long, ownerUserId: String): OutboxEntity? = if (ownerUserId.isBlank()) null else db.outbox().byId(ownerUserId, id)
    fun clearBlocked(ownerUserId: String) {
        if (ownerUserId.isBlank()) return
        snapshot(ownerUserId, 10_000).filter { it.blocked }.forEach { discard(it.id, ownerUserId) }
    }
}
