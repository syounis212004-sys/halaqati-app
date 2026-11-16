package com.mohammedsamir.halaqati.plans

/**
 * RC34: some old rows are placeholders (an unrated recitation with no pages, saved with a stored safety of 0).
 * They are not real recitations, so they must not pull a quality / safety average down.
 */
object RecitationQualityRules {
    fun isPlaceholder(evaluation: String?, safety: Double?, pages: Double? = null): Boolean {
        val ev = evaluation.orEmpty().trim().lowercase()
        val unrated = ev.isEmpty() || ev == "not_rated" || ev == "null"
        val noSafety = safety == null || safety.isNaN() || safety <= 0.0
        val noPages = pages == null || pages.isNaN() || pages <= 0.0
        return unrated && noSafety && noPages
    }
}
