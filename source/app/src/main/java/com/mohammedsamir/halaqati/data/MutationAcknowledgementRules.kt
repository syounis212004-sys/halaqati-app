package com.mohammedsamir.halaqati.data

import org.json.JSONArray

/** PostgREST may return HTTP 2xx with zero updated rows under RLS.
 * Require a compact ID acknowledgement for single-row PATCH operations in phase-four data.
 * Keep bulk operations and RPC response contracts unchanged.
 */
object MutationAcknowledgementRules {
    private val tables = setOf("sessions","students","attendance_records","recitation_entries","student_daily_notes","halaqas","centers","profiles","student_halaqa_memberships")
    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

    fun expectedPatchId(method: String, path: String): String? {
        if (!method.equals("PATCH",ignoreCase=true) || !path.startsWith("/rest/v1/")) return null
        if (path.substringAfter("/rest/v1/").substringBefore('?') !in tables) return null
        val ids = path.substringAfter('?', "").split('&').filter { it.startsWith("id=eq.") }
        return ids.singleOrNull()?.removePrefix("id=eq.")?.takeIf(uuid::matches)
    }

    fun confirmed(expected: String, response: String): Boolean = runCatching {
        val rows = JSONArray(response)
        rows.length()==1 && rows.optJSONObject(0)?.optString("id")?.equals(expected,ignoreCase=true)==true
    }.getOrDefault(false)
}
