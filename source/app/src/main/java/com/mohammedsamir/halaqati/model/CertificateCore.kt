package com.mohammedsamir.halaqati.model

import org.json.JSONObject

/** RC24 certificate core. Supabase stores official metadata/text snapshots only; PDF/assets remain local. */
data class CertificateTemplateRow(
    val id: String,
    val halaqaId: String?,
    val name: String,
    val certificateType: String?,
    val config: JSONObject = JSONObject(),
    val active: Boolean = true,
    val updatedAt: String = "",
) {
    companion object {
        fun from(j: JSONObject) = CertificateTemplateRow(
            id = j.optString("id"),
            halaqaId = j.optNullableString("halaqa_id"),
            name = j.optString("name"),
            certificateType = j.optNullableString("certificate_type"),
            config = j.optJSONObject("config") ?: JSONObject(),
            active = j.optBoolean("active", true),
            updatedAt = j.optString("updated_at"),
        )
    }
}

data class CertificateRecord(
    val id: String,
    val number: String?,
    val studentId: String,
    val halaqaId: String?,
    val certificateType: String,
    val title: String,
    val issueDate: String?,
    val status: String,
    val sourcePlanId: String?,
    val sourcePhaseId: String?,
    val templateId: String?,
    val startSurah: Int?,
    val startAyah: Int?,
    val endSurah: Int?,
    val endAyah: Int?,
    val startPage: Double?,
    val endPage: Double?,
    val pagesCount: Double?,
    val juzCount: Double?,
    val snapshot: JSONObject = JSONObject(),
    val createdAt: String = "",
    val issuedAt: String? = null,
    val updatedAt: String = "",
    val seriesKey: String? = null,
    val seriesCodeAtIssue: String? = null,
    val currentRevisionId: String? = null,
    val revisionCount: Int = 0,
    val legacyNumber: Boolean = false,
    val legacyPartial: Boolean = false,
    val voidReason: String? = null,
) {
    companion object {
        fun from(j: JSONObject): CertificateRecord {
            fun intOrNull(name: String): Int? = if (!j.has(name) || j.isNull(name)) null else j.optInt(name)
            fun doubleOrNull(name: String): Double? = if (!j.has(name) || j.isNull(name)) null else j.optDouble(name)
            return CertificateRecord(
                id = j.optString("id"),
                number = j.optNullableString("certificate_number"),
                studentId = j.optString("student_id"),
                halaqaId = j.optNullableString("halaqa_id"),
                certificateType = j.optString("certificate_type"),
                title = j.optString("title"),
                issueDate = j.optNullableString("issue_date"),
                status = j.optString("status", "draft"),
                sourcePlanId = j.optNullableString("source_plan_id"),
                sourcePhaseId = j.optNullableString("source_phase_id"),
                templateId = j.optNullableString("template_id"),
                startSurah = intOrNull("start_surah"),
                startAyah = intOrNull("start_ayah"),
                endSurah = intOrNull("end_surah"),
                endAyah = intOrNull("end_ayah"),
                startPage = doubleOrNull("start_page"),
                endPage = doubleOrNull("end_page"),
                pagesCount = doubleOrNull("pages_count"),
                juzCount = doubleOrNull("juz_count"),
                snapshot = j.optJSONObject("snapshot") ?: JSONObject(),
                createdAt = j.optString("created_at"),
                issuedAt = j.optNullableString("issued_at"),
                updatedAt = j.optString("updated_at"),
                seriesKey = j.optNullableString("series_key"),
                seriesCodeAtIssue = j.optNullableString("series_code_at_issue"),
                currentRevisionId = j.optNullableString("current_revision_id"),
                revisionCount = j.optInt("revision_count", 0),
                legacyNumber = j.optBoolean("legacy_number", false),
                legacyPartial = j.optBoolean("legacy_partial", false),
                voidReason = j.optNullableString("void_reason"),
            )
        }
    }
}

data class CertificateCoreDraft(
    val id: String? = null,
    val studentId: String,
    val halaqaId: String?,
    val certificateType: String,
    val title: String,
    val issueDate: String? = null,
    val sourcePlanId: String? = null,
    val sourcePhaseId: String? = null,
    val templateId: String? = null,
    val startSurah: Int? = null,
    val startAyah: Int? = null,
    val endSurah: Int? = null,
    val endAyah: Int? = null,
    val startPage: Double? = null,
    val endPage: Double? = null,
    val pagesCount: Double? = null,
    val juzCount: Double? = null,
    val snapshot: JSONObject = JSONObject(),
    val seriesKey: String? = null,
)

data class CertificateSeriesRow(
    val key: String,
    val code: String,
    val arabicLabel: String,
    val active: Boolean = true,
    val updatedAt: String = "",
) {
    companion object {
        fun from(j: JSONObject) = CertificateSeriesRow(
            key = j.optString("series_key"),
            code = j.optString("code"),
            arabicLabel = j.optString("arabic_label"),
            active = j.optBoolean("active", true),
            updatedAt = j.optString("updated_at"),
        )
    }
}

data class CertificateRevisionRow(
    val id: String,
    val certificateId: String,
    val revisionNo: Int,
    val renderSnapshot: CertificateRenderSnapshot,
    val contentHash: String,
    val note: String? = null,
    val createdBy: String? = null,
    val createdAt: String = "",
) {
    companion object {
        fun from(j: JSONObject) = CertificateRevisionRow(
            id = j.optString("id"),
            certificateId = j.optString("certificate_id"),
            revisionNo = j.optInt("revision_no", 1),
            renderSnapshot = CertificateRenderSnapshot.from(j.optJSONObject("render_snapshot") ?: JSONObject()),
            contentHash = j.optString("content_hash"),
            note = j.optNullableString("note"),
            createdBy = j.optNullableString("created_by"),
            createdAt = j.optString("created_at"),
        )
    }
}

data class CertificateRenderSnapshot(
    val schema: String = SCHEMA,
    val studentId: String = "",
    val studentName: String,
    val centerName: String,
    val halaqaName: String,
    val certificateType: String,
    val seriesKey: String,
    val title: String,
    val intro: String,
    val achievement: String,
    val dua: String,
    val issueDate: String,
    val officialNumber: String = "",
    val teacherName: String,
    val supervisorName: String,
    val style: JSONObject = JSONObject(),
    val assetRefs: JSONObject = JSONObject().put("policy", "local_only"),
    val source: JSONObject = JSONObject(),
    val rendererVersion: String = "rc24",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("schema", SCHEMA)
        .put("student_id", studentId)
        .put("student_name", studentName)
        .put("center_name", centerName)
        .put("halaqa_name", halaqaName)
        .put("certificate_type", certificateType)
        .put("series_key", seriesKey)
        .put("title", title)
        .put("intro", intro)
        .put("achievement", achievement)
        .put("dua", dua)
        .put("issue_date", issueDate)
        .put("official_number", officialNumber)
        .put("teacher_name", teacherName)
        .put("supervisor_name", supervisorName)
        .put("style", JSONObject(style.toString()))
        .put("asset_refs", JSONObject(assetRefs.toString()))
        .put("source", JSONObject(source.toString()))
        .put("renderer_version", rendererVersion)

    companion object {
        const val SCHEMA = "halaqati_certificate_render_v2"
        fun from(j: JSONObject) = CertificateRenderSnapshot(
            schema = j.optString("schema", SCHEMA),
            studentId = j.optString("student_id"),
            studentName = j.optString("student_name"),
            centerName = j.optString("center_name"),
            halaqaName = j.optString("halaqa_name"),
            certificateType = j.optString("certificate_type"),
            seriesKey = j.optString("series_key", "general"),
            title = j.optString("title"),
            intro = j.optString("intro"),
            achievement = j.optString("achievement"),
            dua = j.optString("dua"),
            issueDate = j.optString("issue_date"),
            officialNumber = j.optString("official_number"),
            teacherName = j.optString("teacher_name"),
            supervisorName = j.optString("supervisor_name"),
            style = j.optJSONObject("style") ?: JSONObject(),
            assetRefs = j.optJSONObject("asset_refs") ?: JSONObject().put("policy", "local_only"),
            source = j.optJSONObject("source") ?: JSONObject(),
            rendererVersion = j.optString("renderer_version", "rc24"),
        )
    }
}
