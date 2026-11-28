package com.mohammedsamir.halaqati.data

import java.text.Normalizer

object StudentGuardianRules {
    data class GuardianOption(
        val id: String,
        val fullName: String,
        val email: String? = null,
        val phone: String? = null,
        val metadataAvailable: Boolean = true,
    )

    fun linkedOptions(ids: List<String>, accounts: List<GuardianOption>): List<GuardianOption> {
        val byId = accounts.associateBy { it.id }
        return ids.filter { it.isNotBlank() }.distinct().map { id -> byId[id] ?: GuardianOption(id, "ولي أمر مرتبط", metadataAvailable = false) }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه')
        .replace(Regex("\\s+"), " ")

    fun matches(option: GuardianOption, query: String): Boolean {
        val q = normalize(query)
        if (q.isBlank()) return true
        return sequenceOf(option.fullName, option.email.orEmpty(), option.phone.orEmpty())
            .map(::normalize)
            .any { it.contains(q) }
    }

    fun label(option: GuardianOption): String = buildString {
        append(option.fullName.ifBlank { option.email ?: "ولي أمر" })
        option.phone?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
    }

    fun filter(options: List<GuardianOption>, query: String, limit: Int = 8): List<GuardianOption> =
        options.asSequence().filter { matches(it, query) }.take(limit.coerceAtLeast(1)).toList()
}
