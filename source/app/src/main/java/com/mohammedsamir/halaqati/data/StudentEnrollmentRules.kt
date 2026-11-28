package com.mohammedsamir.halaqati.data

/** UI scope selection. The server must still verify every mutation. */
object StudentEnrollmentRules {
    data class Scope(val id: String, val program: Boolean, val active: Boolean, val allowSecondary: Boolean)
    fun initialScope(requested: String?, scopes: List<Scope>): String? {
        val active = scopes.filter { it.active }.map { it.id }.distinct()
        return if (requested != null) requested.takeIf { it in active } else active.singleOrNull()
    }
    fun additionalScopes(primary: String, scopes: List<Scope>): List<Scope> =
        scopes.filter { it.id != primary && it.program && it.active && it.allowSecondary }
    fun canEditPrimary(primary: String?, context: String?, managedIds: Set<String>): Boolean =
        primary != null && primary == context && primary in managedIds
    fun detailScope(primary: String?, requested: String?, memberships: Set<String>, scopes: List<Scope>, programMode: Boolean): String? {
        val memberIds = memberships + listOfNotNull(primary)
        val visible = scopes.filter { it.id in memberIds }
        return if (requested != null) requested.takeIf { id -> visible.any { it.id == id } }
        else visible.filter { it.program == programMode }.map { it.id }.distinct().singleOrNull()
    }
}
