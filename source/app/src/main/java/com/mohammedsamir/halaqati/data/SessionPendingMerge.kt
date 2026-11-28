package com.mohammedsamir.halaqati.data

/**
 * `refreshSessions` replaces the cached session list with what the server returned. A session created on this device and still waiting in the
 * Outbox remains visible unless it has also been deleted locally. Remote sessions stay visible
 * until the server confirms deletion, including while a delete is blocked.
 */
object SessionPendingMerge {
    data class Plan(val addIds: List<String>, val hideIds: Set<String>)

    fun plan(serverIds: Set<String>, pendingUpsertIds: List<String>, pendingDeleteIds: List<String>): Plan {
        val hide = pendingDeleteIds.filter { it.isNotBlank() && it !in serverIds }.toSet()
        val add = pendingUpsertIds.filter { it.isNotBlank() && it !in serverIds && it !in hide }.distinct()
        return Plan(add, hide)
    }
}
