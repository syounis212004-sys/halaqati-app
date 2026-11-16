package com.mohammedsamir.halaqati.data

/**
 * Builds deterministic SharedPreferences keys that cannot collide between signed-in accounts.
 * Local student-derived data must never use an unscoped key.
 */
object LocalAccountScopeRules {
    private const val PREFIX = "owner"

    fun ownerKey(ownerUserId: String, suffix: String): String {
        val owner = ownerUserId.trim()
        val part = suffix.trim()
        require(owner.isNotEmpty()) { "owner user id is required" }
        require(part.isNotEmpty()) { "key suffix is required" }
        require(!owner.contains('|') && !part.contains('|')) { "invalid scoped-key separator" }
        return "$PREFIX|$owner|$part"
    }

    fun belongsTo(key: String, ownerUserId: String): Boolean {
        val owner = ownerUserId.trim()
        if (owner.isEmpty()) return false
        return key.startsWith("$PREFIX|$owner|")
    }
}
