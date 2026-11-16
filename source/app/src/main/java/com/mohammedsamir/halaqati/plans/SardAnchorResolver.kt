package com.mohammedsamir.halaqati.plans

import com.mohammedsamir.halaqati.quran.QuranMetadata
import java.time.LocalDate

/**
 * RC11.2 single source of truth for the Sard anchor.
 *
 * Only an actual `sard` recitation advances the Sard anchor. A generic `test`
 * must not silently move the anchor unless it is stored as a Sard recitation.
 * Ordering is historical: session_date first, created_at only breaks ties.
 */
object SardAnchorResolver {
    data class Anchor(
        val recitationId: String,
        val date: LocalDate,
        val surah: Int,
        val ayah: Int,
        val page: Int,
    )

    fun resolve(
        recitations: List<SmartPlanEngine.Recitation>,
        onOrBefore: LocalDate,
    ): Anchor? {
        val row = recitations.asSequence()
            .filter { it.date <= onOrBefore }
            .filter { it.activityType.equals("sard", ignoreCase = true) }
            .filter { it.endSurah != null && it.endAyah != null }
            .maxWithOrNull(compareBy<SmartPlanEngine.Recitation> { it.date }.thenBy { it.createdAt })
            ?: return null
        val surah = row.endSurah ?: return null
        val ayah = row.endAyah ?: return null
        if (surah !in 1..114 || ayah !in 1..QuranMetadata.maxAyah(surah)) return null
        return Anchor(
            recitationId = row.id,
            date = row.date,
            surah = surah,
            ayah = ayah,
            page = row.endPage?.takeIf { it in 1..604 } ?: QuranMetadata.pageFor(surah, ayah),
        )
    }
}
