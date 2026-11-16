package com.mohammedsamir.halaqati.data

import java.time.Instant

/** Deterministic optimistic-concurrency policy shared by sync and tests. */
object ConflictRules {
    fun changedSinceBase(baseUpdatedAt: String?, serverUpdatedAt: String?): Boolean {
        if (baseUpdatedAt.isNullOrBlank() || serverUpdatedAt.isNullOrBlank()) return false
        if (baseUpdatedAt == serverUpdatedAt) return false
        val base = runCatching { Instant.parse(baseUpdatedAt) }.getOrNull()
        val server = runCatching { Instant.parse(serverUpdatedAt) }.getOrNull()
        return when {
            base != null && server != null -> server.isAfter(base)
            else -> serverUpdatedAt != baseUpdatedAt
        }
    }
}
