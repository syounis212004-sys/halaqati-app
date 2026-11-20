package com.mohammedsamir.halaqati.data

/**
 * `refreshSessions` replaces the cached session list with what the server returned. A session created on this device and still waiting in the
 * Outbox is not on the server yet and used to disappear from the list until it was pushed; a session deleted locally (delete still pending)
 * came back. This decides which ids to add back and which to hide.
 */
object SessionPendingMerge {
    data class Plan(val addIds: List<String>, val hideIds: Set<String>)

    fun plan(serverIds: Set<String>, pendingUpsertIds: List<String>, pendingDeleteIds: List<String>): Plan {
        val hide = pendingDeleteIds.filter { it.isNotBlank() }.toSet()
        val add = pendingUpsertIds.filter { it.isNotBlank() && it !in serverIds && it !in hide }.distinct()
        return Plan(add, hide)
    }
}
