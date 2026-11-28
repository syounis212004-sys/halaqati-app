package com.mohammedsamir.halaqati.data

// The database implementation lives in :offline-db; its API and package stay stable.
import com.mohammedsamir.halaqati.data.HalaqatiOfflineDatabase

import java.time.Duration
import java.time.Instant

/** Account-scoped Room cache. Authentication secrets are never stored here. */
class LocalCache(private val db: HalaqatiOfflineDatabase) {
    fun put(ownerUserId: String, key: String, json: String, savedAt: Instant = Instant.now()) {
        requireScope(ownerUserId, key)
        require(json.isNotBlank()) { "cache json must not be blank" }
        db.cache().put(CacheEntryEntity(ownerUserId.trim(), key.trim(), json, savedAt.toEpochMilli()))
    }

    fun get(ownerUserId: String, key: String, maxAge: Duration? = null, now: Instant = Instant.now()): String? {
        if (!isValidScope(ownerUserId, key) || (maxAge?.isNegative == true)) return null
        val row = db.cache().get(ownerUserId.trim(), key.trim()) ?: return null
        if (!isUsable(Instant.ofEpochMilli(row.savedAt), maxAge, now)) return null
        return row.jsonValue
    }

    fun invalidate(ownerUserId: String, prefix: String) {
        if (isValidScope(ownerUserId, prefix)) db.cache().invalidate(ownerUserId.trim(), prefix.trim())
    }

    fun clearOwner(ownerUserId: String) { if (ownerUserId.isNotBlank()) db.cache().clearOwner(ownerUserId.trim()) }

    private fun requireScope(ownerUserId: String, key: String) = require(isValidScope(ownerUserId, key)) { "cache owner and key are required" }

    companion object {
        fun isValidScope(ownerUserId: String, key: String) = ownerUserId.isNotBlank() && key.isNotBlank()
        fun isUsable(savedAt: Instant, maxAge: Duration?, now: Instant = Instant.now()): Boolean {
            if (maxAge == null) return true
            if (maxAge.isNegative) return false
            if (savedAt.isAfter(now)) return true
            return Duration.between(savedAt, now) <= maxAge
        }
    }
}
