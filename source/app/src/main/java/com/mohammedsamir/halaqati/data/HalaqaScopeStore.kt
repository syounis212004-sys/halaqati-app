package com.mohammedsamir.halaqati.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** RC25: remembers the last halaqa chosen by each signed-in user (admin / supervisor). */
class HalaqaScopeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("halaqati_scope_v1", Context.MODE_PRIVATE)
    private val _selected = MutableStateFlow(readAll())
    val selected: StateFlow<Map<String, String>> = _selected.asStateFlow()

    private fun readAll(): Map<String, String> = runCatching {
        prefs.all.mapNotNull { (k, v) -> (v as? String)?.takeIf { it.isNotBlank() }?.let { k to it } }.toMap()
    }.getOrDefault(emptyMap())

    fun savedFor(userId: String?): String? = userId?.let { _selected.value[it] }

    /** Pass null to go back to the halaqa overview. */
    fun select(userId: String?, halaqaId: String?) {
        if (userId.isNullOrBlank()) return
        runCatching {
            prefs.edit().apply { if (halaqaId.isNullOrBlank()) remove(userId) else putString(userId, halaqaId) }.apply()
        }
        _selected.value = _selected.value.toMutableMap().also {
            if (halaqaId.isNullOrBlank()) it.remove(userId) else it[userId] = halaqaId
        }
    }
}
