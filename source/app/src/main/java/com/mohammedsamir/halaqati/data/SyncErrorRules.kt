package com.mohammedsamir.halaqati.data

import org.json.JSONObject

/**
 * RC15 Outbox error taxonomy and payload hygiene.
 * - 401: session expired → refresh once and retry (withAuthRetry); if still 401 → retryable later.
 * - 23514/23502/23503/22P02/23505: payload violates DB rules → blocked, never auto-retried with the same body.
 * - 42501/403: RLS/permission → blocked; usually a missing/failed parent record.
 * - CancellationException is never recorded as a failed operation.
 */
object SyncErrorRules {
    enum class Kind { SESSION, INVALID_PAYLOAD, PERMISSION, PARENT_MISSING, TRANSIENT, OTHER }

    private val invalidPayloadStates = setOf("23514", "23502", "23503", "22P02", "23505", "22001", "22003")

    fun classify(status: Int, sqlState: String?): Kind = when {
        status == 401 -> Kind.SESSION
        sqlState in invalidPayloadStates -> Kind.INVALID_PAYLOAD
        sqlState == "42501" || status == 403 -> Kind.PERMISSION
        status == 0 || status == 408 || status == 429 || status >= 500 -> Kind.TRANSIENT
        status == 400 || status == 409 || status == 422 -> Kind.INVALID_PAYLOAD
        else -> Kind.OTHER
    }

    fun label(kind: Kind, sqlState: String?, raw: String): String {
        val code = sqlState?.let { " [$it]" }.orEmpty()
        return when (kind) {
            Kind.SESSION -> "انتهت الجلسة$code — سيُعاد المحاولة بعد تجديد الدخول. $raw"
            Kind.INVALID_PAYLOAD -> "بيانات لا تطابق قواعد قاعدة البيانات$code — لن تُعاد المحاولة تلقائيًا. $raw"
            Kind.PERMISSION -> "صلاحيات/RLS$code — تحقق من وجود السجل الأب وصلاحيتك عليه. $raw"
            Kind.PARENT_MISSING -> "لم تُرسل لأن السجل الأب لم يُحفظ على الخادم. $raw"
            Kind.TRANSIENT -> "خطأ مؤقت في الشبكة/الخادم$code. $raw"
            Kind.OTHER -> "يحتاج مراجعة$code. $raw"
        }.trim().take(1000)
    }

    /**
     * Normalizes a queued smart_plans body to smart_plans_scope_target_check:
     * student → category_id=null, category → student_id=null, halaqa → both null.
     * A tathbit student-scope plan is always plan_type=tathbit.
     */
    fun sanitizeSmartPlan(body: JSONObject, studentTrack: (String) -> String?): JSONObject {
        val out = JSONObject(body.toString())

        // RC17.1 BUILD FIX 2: org.json turns JSON null into the literal string "null"
        // when old cached rows were read with optString(). Postgres UUID columns reject that
        // string with SQLSTATE 22P02. Normalize every nullable UUID carried by smart_plans
        // before it reaches the durable Outbox or PostgREST.
        listOf("student_id", "category_id", "parent_plan_id", "created_by").forEach { key ->
            if (!out.has(key) || out.isNull(key)) return@forEach
            val value = out.opt(key)
            val text = value?.toString()?.trim().orEmpty()
            if (value === JSONObject.NULL || text.isBlank() || text.equals("null", true) || text.equals("undefined", true)) {
                out.put(key, JSONObject.NULL)
            }
        }

        when (out.optString("scope_type")) {
            "student" -> out.put("category_id", JSONObject.NULL)
            "category" -> out.put("student_id", JSONObject.NULL)
            "halaqa" -> { out.put("student_id", JSONObject.NULL); out.put("category_id", JSONObject.NULL) }
        }
        val studentId = if (out.isNull("student_id")) "" else out.optString("student_id")
        if (out.optString("scope_type") == "student" && studentId.isNotBlank() &&
            TrackActivityPolicy.isTathbit(studentTrack(studentId)) && out.has("plan_type")) {
            out.put("plan_type", TrackActivityPolicy.TATHBIT).put("review_tier", "none")
            out.optJSONObject("settings")?.put("enabled_activities", org.json.JSONArray().put(TrackActivityPolicy.TATHBIT))
        }
        // RC26.1: heal blocked edits whose reverse-route page range violates smart_plans_check2.
        if (out.has("range_start_page") && out.has("range_end_page") && !out.isNull("range_start_page") && !out.isNull("range_end_page")) {
            val a = out.optInt("range_start_page")
            val b = out.optInt("range_end_page")
            if (a > b) { out.put("range_start_page", b); out.put("range_end_page", a) }
        }
        return out
    }
}
