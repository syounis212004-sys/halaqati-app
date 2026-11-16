package com.mohammedsamir.halaqati.plans

import com.mohammedsamir.halaqati.quran.QuranLocation
import com.mohammedsamir.halaqati.quran.QuranMetadata

/**
 * RC8.5 activity rules for Smart Plans.
 * A student's system track is still general-hifz/tathbit; hifz/sard are independent plan activities.
 * Each activity owns its own latest-recitation anchor and must never advance from another activity.
 */
object SmartPlanActivityRules {
    private val hifzActivities = setOf("new_hifz", "hifz")
    private val sardActivities = setOf("sard")
    private val reviewActivities = setOf("review", "review_near", "review_far")
    // RC15: tathbit is its own activity. review_far is accepted only as legacy read-compat.
    private val tathbitActivities = setOf("tathbit", "review_far")

    fun enabledActivities(planType: String, stored: List<String>): List<String> {
        if (planType == "tathbit") return listOf("tathbit")
        val allowed = listOf("hifz", "sard", "review")
        val clean = stored.map { it.trim().lowercase() }.filter { it in allowed }.distinct()
        if (clean.isNotEmpty()) return clean
        return when (planType) {
            "sard" -> listOf("sard")
            "review" -> listOf("review")
            else -> listOf("hifz")
        }
    }

    fun activityMatches(activity: String, recitationActivityType: String): Boolean = when (activity) {
        "hifz" -> recitationActivityType.lowercase() in hifzActivities
        "sard" -> recitationActivityType.lowercase() in sardActivities
        "review" -> recitationActivityType.lowercase() in reviewActivities
        "tathbit" -> recitationActivityType.lowercase() in tathbitActivities
        else -> false
    }

    fun latestAnchor(recitations: List<SmartPlanEngine.Recitation>, activity: String): SmartPlanEngine.Recitation? =
        recitations.asSequence()
            .filter { activityMatches(activity, it.activityType) }
            .filter { it.endSurah != null && it.endAyah != null }
            .maxWithOrNull(compareBy<SmartPlanEngine.Recitation> { it.date }.thenBy { it.createdAt })

    fun nextLocation(anchorEnd: QuranLocation, routeDirection: String): QuranLocation? {
        val maxAyah = QuranMetadata.maxAyah(anchorEnd.surah)
        if (anchorEnd.ayah < maxAyah) return QuranLocation(anchorEnd.surah, anchorEnd.ayah + 1)
        return when (routeDirection) {
            "reverse_surahs_forward_ayahs" -> {
                val nextSurah = anchorEnd.surah - 1
                if (nextSurah < 1) null else QuranLocation(nextSurah, 1)
            }
            else -> {
                val nextSurah = anchorEnd.surah + 1
                if (nextSurah > 114) null else QuranLocation(nextSurah, 1)
            }
        }
    }

    fun routeFor(activity: String, primaryPlanType: String, hifzRoute: String?, sardRoute: String?): String = when (activity) {
        "hifz" -> hifzRoute ?: if (primaryPlanType == "hifz") "reverse_surahs_forward_ayahs" else "forward_surahs_forward_ayahs"
        "sard" -> sardRoute ?: if (primaryPlanType == "hifz") "reverse_surahs_forward_ayahs" else "forward_surahs_forward_ayahs"
        "tathbit" -> hifzRoute ?: "forward_surahs_forward_ayahs"
        else -> "forward_surahs_forward_ayahs"
    }

    /** Product route boundary: reverse general-hifz/Sard runs from An-Nas down to Al-Baqarah.
     * Tathbit may cover the complete Mushaf, including Al-Fatihah, when a reverse range is chosen. */
    fun terminalSurah(activity: String, routeDirection: String): Int = when {
        routeDirection != "reverse_surahs_forward_ayahs" -> 114
        activity in setOf("hifz", "sard") -> 2
        activity == "tathbit" -> 1
        else -> 114
    }

    /**
     * RC8.6 amount rule. The recitation anchor chooses the next Quran location;
     * it must not shrink the requested ward to a single leftover line from a
     * stale legacy range.
     *
     * RC44: for hifz the PLAN is the only source of the daily amount (engine daily incl. recovery, original / planned daily,
     * minimum). The student's weekly target ([weeklyDaily]) no longer raises it: it used to win the "max" and made the written
     * ward (1.4 pages) disagree with "مطلوب اليوم" (0.47) on the same card.
     */
    fun effectiveDailyPages(
        activity: String,
        engineDaily: Double,
        originalDaily: Double,
        minimumDaily: Double,
        plannedDaily: Double,
        weeklyDaily: Double,
        assignmentPages: Double,
        assignmentKind: String,
    ): Double = when (activity) {
        "hifz" -> maxOf(
            engineDaily,
            originalDaily,
            minimumDaily,
            plannedDaily,
            assignmentPages.takeIf { assignmentKind == "hifz" } ?: 0.0,
        )
        "sard" -> maxOf(engineDaily, plannedDaily)
        else -> maxOf(engineDaily, plannedDaily, weeklyDaily)
    }.coerceAtLeast(0.0)

    fun activityTypeFor(activity: String): String = when (activity) {
        "sard" -> "sard"
        "review" -> "review_near"
        "tathbit" -> "tathbit"
        else -> "new_hifz"
    }
}
