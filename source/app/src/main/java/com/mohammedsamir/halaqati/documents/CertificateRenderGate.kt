package com.mohammedsamir.halaqati.documents

import android.webkit.WebView
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume

data class CertificateRenderGateResult(
    val ready: Boolean,
    val layoutOk: Boolean,
    val requiredNodesPresent: Boolean,
    val requiredNodesVisible: Boolean,
    val requiredNodesInsidePage: Boolean,
    val noProtectedOverlap: Boolean,
    val invalidSections: List<String>,
    val renderRevision: String,
) {
    val valid: Boolean get() = ready && layoutOk && requiredNodesPresent && requiredNodesVisible && requiredNodesInsidePage && noProtectedOverlap

    /** Specific Arabic message naming the section and what is wrong with it (never a blind generic error). */
    fun arabicError(): String {
        if (valid) return ""
        val notReady = !ready && invalidSections.isEmpty()
        if (notReady) return "الخط والمعاينة لم يكتملا بعد؛ انتظر لحظة ثم أعد المحاولة"
        val parts = invalidSections.map { raw ->
            val section = raw.substringBefore(':')
            val why = raw.substringAfter(':', "")
            val label = when (section) {
                "kicker" -> "عنوان شهادة تقدير وإنجاز"
                "title" -> "عنوان الشهادة"
                "student" -> "اسم الطالب"
                "achievement" -> "نص الإنجاز"
                "intro" -> "المقدمة"
                "dua" -> "الدعاء"
                "center" -> "اسم المركز"
                "halaqa" -> "اسم الحلقة"
                "body" -> "محتوى الشهادة"
                else -> section
            }
            val reason = when (why) {
                "missing" -> "فارغ"
                "hidden" -> "مخفي"
                "outside" -> "خارج حدود A4"
                "overlap" -> "يتداخل مع عنصر آخر"
                "overflow" -> "أطول من المساحة المتاحة؛ اختصره"
                else -> ""
            }
            if (reason.isEmpty()) label else "$label ($reason)"
        }.distinct()
        return if (parts.isEmpty()) "تعذر ضبط تنسيق الشهادة على A4؛ اضغط «إصلاح التنسيق» ثم أعد المحاولة" else "راجع ${parts.joinToString("، ")}"
    }

    companion object {
        fun fromJson(obj: JSONObject): CertificateRenderGateResult {
            val invalid = obj.optJSONArray("invalid_sections") ?: JSONArray()
            return CertificateRenderGateResult(
                ready = obj.optBoolean("ready", false),
                layoutOk = obj.optBoolean("layout_ok", false),
                requiredNodesPresent = obj.optBoolean("required_nodes_present", false),
                requiredNodesVisible = obj.optBoolean("required_nodes_visible", false),
                requiredNodesInsidePage = obj.optBoolean("required_nodes_inside_page", false),
                noProtectedOverlap = obj.optBoolean("no_protected_overlap", false),
                invalidSections = (0 until invalid.length()).mapNotNull { invalid.optString(it).takeIf(String::isNotBlank) },
                renderRevision = obj.optString("render_revision", "unknown"),
            )
        }
    }
}

suspend fun evaluateCertificateGate(view: WebView): CertificateRenderGateResult = suspendCancellableCoroutine { cont ->
    view.evaluateJavascript(
        "(function(){try{return JSON.stringify(window.__HALAQATI_CERTIFICATE_GATE__?window.__HALAQATI_CERTIFICATE_GATE__():{ready:false,layout_ok:false,required_nodes_present:false,required_nodes_visible:false,required_nodes_inside_page:false,no_protected_overlap:false,invalid_sections:['gate:missing'],render_revision:'missing'})}catch(e){return JSON.stringify({ready:false,layout_ok:false,required_nodes_present:false,required_nodes_visible:false,required_nodes_inside_page:false,no_protected_overlap:false,invalid_sections:['gate:error'],render_revision:'error'})}})()",
    ) { raw ->
        if (!cont.isActive) return@evaluateJavascript
        val decoded = runCatching { JSONArray("[$raw]").optString(0) }.getOrDefault("")
        val result = runCatching { CertificateRenderGateResult.fromJson(JSONObject(decoded)) }.getOrElse {
            CertificateRenderGateResult(false, false, false, false, false, false, listOf("gate:parse"), "parse-error")
        }
        cont.resume(result)
    }
}

/**
 * The page's own gate re-runs the (idempotent) fit before answering, so the only thing that can still be
 * "not ready" is a font that is genuinely still loading. Poll briefly instead of failing on a race.
 */
suspend fun awaitCertificateGate(view: WebView, attempts: Int = 12, pauseMs: Long = 150L): CertificateRenderGateResult {
    var last = evaluateCertificateGate(view)
    var n = 1
    while (!last.ready && n < attempts) {
        delay(pauseMs)
        last = evaluateCertificateGate(view)
        n++
    }
    return last
}
