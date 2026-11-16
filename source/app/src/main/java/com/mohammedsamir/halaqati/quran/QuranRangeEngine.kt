package com.mohammedsamir.halaqati.quran

import kotlin.math.ceil
import kotlin.math.round

enum class QuranTraversalDirection {
    FORWARD_QURAN,
    REVERSE_SURAH_ORDER,
    AUTO_BY_ACTIVITY,
}

enum class QuranResolvedTraversal {
    FORWARD_QURAN,
    REVERSE_SURAH_ORDER,
    REVERSE_WITHIN_SURAH,
    PAGE_ORDER_FORWARD,
    PAGE_ORDER_REVERSE,
    EXPLICIT_SEGMENTS,
}

enum class QuranAyahDirection { ASCENDING, DESCENDING }

data class QuranSegment(
    val start: QuranLocation,
    val end: QuranLocation,
    val ayahDirection: QuranAyahDirection = QuranAyahDirection.ASCENDING,
) {
    val label: String
        get() = if (start.surah == end.surah) {
            "سورة ${QuranMetadata.surahName(start.surah)} · من الآية ${start.ayah} إلى الآية ${end.ayah}"
        } else {
            "من سورة ${QuranMetadata.surahName(start.surah)} آية ${start.ayah} إلى سورة ${QuranMetadata.surahName(end.surah)} آية ${end.ayah}"
        }

    fun compactKey(): String = "${start.surah}:${start.ayah}-${end.surah}:${end.ayah}:${ayahDirection.name.lowercase()}"
}

sealed interface QuranSelection {
    data class Ayahs(val start: QuranLocation, val end: QuranLocation) : QuranSelection
    data class Pages(val startPage: Int, val endPage: Int) : QuranSelection
    data class Surahs(val surahs: List<Int>) : QuranSelection
    data class Juz(val juz: List<Int>) : QuranSelection
    data class Segments(val segments: List<Ayahs>) : QuranSelection
}

data class QuranRangeRequest(
    val track: String,
    val activityType: String,
    val selection: QuranSelection,
    val direction: QuranTraversalDirection = QuranTraversalDirection.AUTO_BY_ACTIVITY,
)

data class QuranRangeResult(
    val inputMethod: String,
    val requestedDirection: QuranTraversalDirection,
    val resolvedTraversal: QuranResolvedTraversal,
    val orderedSegments: List<QuranSegment>,
    val start: QuranLocation?,
    val end: QuranLocation?,
    val startPage: Int?,
    val endPage: Int?,
    val exactPages: Double,
    val ayahCount: Int,
    val surahCount: Int,
    val qcfLineKeys: List<String>,
    val selectedSurahs: List<Int> = emptyList(),
    val selectedJuz: List<Int> = emptyList(),
) {
    val roundedPages: Double get() = round(exactPages * 100.0) / 100.0
    val displayLabel: String
        get() = when {
            orderedSegments.size == 1 -> orderedSegments.first().label
            start != null && end != null && start.surah == end.surah ->
                "سورة ${QuranMetadata.surahName(start.surah)} · من الآية ${start.ayah} إلى الآية ${end.ayah}"
            start != null && end != null ->
                "من سورة ${QuranMetadata.surahName(start.surah)} آية ${start.ayah} إلى سورة ${QuranMetadata.surahName(end.surah)} آية ${end.ayah}"
            else -> orderedSegments.joinToString("، ") { it.label }
        }
}

interface QuranLineLayout {
    val source: String
    val lineModel: Int
    fun page(globalAyah: Int): Int {
        val location = QuranMetadata.location(globalAyah)
        return QuranMetadata.pageFor(location.surah, location.ayah)
    }
    fun firstLine(globalAyah: Int): Int
    fun lastLine(globalAyah: Int): Int
}

/** Three bytes per ayah: page high byte, page low byte, first/last-line nibbles. */
class PackedQcf4LineLayout(private val packed: ByteArray) : QuranLineLayout {
    override val source: String = "QCF4 Hafs · Madinah Mushaf 1441 AH"
    override val lineModel: Int = 15

    init {
        require(packed.size == QuranMetadata.AYAH_COUNT * 3) { "ملف QCF4 المحلي غير مكتمل" }
        for (globalAyah in 1..QuranMetadata.AYAH_COUNT) {
            val page = page(globalAyah)
            val b = lineByte(globalAyah)
            val first = (b ushr 4) and 0x0f
            val last = b and 0x0f
            require(page in 1..604 && first in 1..15 && last in first..15) {
                "بيانات QCF4 غير صالحة عند الآية العالمية $globalAyah"
            }
        }
    }

    override fun page(globalAyah: Int): Int {
        val offset = offset(globalAyah)
        return ((packed[offset].toInt() and 0xff) shl 8) or (packed[offset + 1].toInt() and 0xff)
    }
    override fun firstLine(globalAyah: Int): Int = ((lineByte(globalAyah) ushr 4) and 0x0f)
    override fun lastLine(globalAyah: Int): Int = lineByte(globalAyah) and 0x0f
    private fun lineByte(globalAyah: Int): Int = packed[offset(globalAyah) + 2].toInt() and 0xff
    private fun offset(globalAyah: Int): Int {
        require(globalAyah in 1..QuranMetadata.AYAH_COUNT)
        return (globalAyah - 1) * 3
    }
}

/**
 * Single Quran range engine shared by recitation, Smart Plan, quick memorization,
 * reports, certificates and analytics. It never normalizes input with min/max before
 * the track/activity policy has decided what the order means.
 */
class QuranRangeEngine(private val layout: QuranLineLayout) {
    fun calculate(request: QuranRangeRequest): QuranRangeResult = when (val selection = request.selection) {
        is QuranSelection.Ayahs -> calculateAyahs(request, selection.start, selection.end)
        is QuranSelection.Pages -> calculatePages(request, selection.startPage, selection.endPage)
        is QuranSelection.Surahs -> calculateSurahs(request, selection.surahs)
        is QuranSelection.Juz -> calculateJuz(request, selection.juz)
        is QuranSelection.Segments -> calculateExplicitSegments(request, selection.segments)
    }

    /** Resolve the first/last Quran locations touched by an ordered QCF4 line-key list. */
    /**
     * RC11.1 live Sard preview: starting from a Quran location, walk exactly in the
     * configured route and stop at the first ayah that covers the requested QCF line quota.
     * This keeps ayahs ascending inside every surah even when surah numbers descend.
     */
    fun previewPagesFrom(
        start: QuranLocation,
        targetPages: Double,
        track: String,
        activityType: String,
        direction: QuranTraversalDirection,
    ): QuranRangeResult {
        validate(start)
        require(targetPages > 0.0) { "عدد الصفحات يجب أن يكون أكبر من صفر" }
        val resolvedDirection = when (direction) {
            QuranTraversalDirection.AUTO_BY_ACTIVITY ->
                if (track == "general_hifz" && activityType in setOf("new_hifz", "hifz")) QuranTraversalDirection.REVERSE_SURAH_ORDER
                else QuranTraversalDirection.FORWARD_QURAN
            else -> direction
        }
        val requiredLines = ceil(targetPages * layout.lineModel - 1e-9).toInt().coerceAtLeast(1)
        val keys = linkedSetOf<String>()
        var end = start
        for (location in routeLocations(start, resolvedDirection)) {
            val global = QuranMetadata.globalAyah(location.surah, location.ayah)
            val page = layout.page(global)
            for (line in layout.firstLine(global)..layout.lastLine(global)) keys += "$page:$line"
            end = location
            if (keys.size >= requiredLines) break
        }
        return calculate(
            QuranRangeRequest(
                track = track,
                activityType = activityType,
                selection = QuranSelection.Ayahs(start, end),
                direction = resolvedDirection,
            )
        )
    }

    private fun routeLocations(start: QuranLocation, direction: QuranTraversalDirection): Sequence<QuranLocation> = sequence {
        when (direction) {
            QuranTraversalDirection.FORWARD_QURAN -> {
                val first = QuranMetadata.globalAyah(start.surah, start.ayah)
                for (global in first..QuranMetadata.AYAH_COUNT) yield(QuranMetadata.location(global))
            }
            QuranTraversalDirection.REVERSE_SURAH_ORDER -> {
                for (surah in start.surah downTo 1) {
                    val firstAyah = if (surah == start.surah) start.ayah else 1
                    for (ayah in firstAyah..QuranMetadata.maxAyah(surah)) yield(QuranLocation(surah, ayah))
                }
            }
            QuranTraversalDirection.AUTO_BY_ACTIVITY -> error("يجب حل اتجاه المعاينة قبل بناء المسار")
        }
    }

    fun endpointsForLineKeys(keys: List<String>): Pair<QuranLocation, QuranLocation>? {
        if (keys.isEmpty()) return null
        fun locationFor(key: String, preferLast: Boolean): QuranLocation? {
            val page = key.substringBefore(':').toIntOrNull() ?: return null
            val line = key.substringAfter(':', "").toIntOrNull() ?: return null
            if (page !in 1..QuranMetadata.PAGE_COUNT || line !in 1..layout.lineModel) return null
            val candidates = mutableListOf<Int>()
            for (g in QuranMetadata.pageStarts[page] until QuranMetadata.pageStarts[page + 1]) {
                if (line in layout.firstLine(g)..layout.lastLine(g)) candidates += g
            }
            val global = if (preferLast) candidates.lastOrNull() else candidates.firstOrNull()
            return global?.let(QuranMetadata::location)
        }
        val start = locationFor(keys.first(), preferLast = false) ?: return null
        val end = locationFor(keys.last(), preferLast = true) ?: return null
        return start to end
    }

    private fun calculateAyahs(request: QuranRangeRequest, start: QuranLocation, end: QuranLocation): QuranRangeResult {
        validate(start); validate(end)
        val resolved = resolveAyahTraversal(request, start, end)
        val segments = when (resolved) {
            QuranResolvedTraversal.FORWARD_QURAN -> forwardSegments(start, end)
            QuranResolvedTraversal.REVERSE_SURAH_ORDER -> reverseSurahSegments(start, end)
            QuranResolvedTraversal.REVERSE_WITHIN_SURAH -> listOf(QuranSegment(start, end, QuranAyahDirection.DESCENDING))
            else -> error("اتجاه الآيات غير صالح")
        }
        return resultForSegments(
            request = request,
            inputMethod = "ayahs",
            resolved = resolved,
            segments = segments,
            start = start,
            end = end,
        )
    }

    private fun calculatePages(request: QuranRangeRequest, startPage: Int, endPage: Int): QuranRangeResult {
        require(startPage in 1..QuranMetadata.PAGE_COUNT && endPage in 1..QuranMetadata.PAGE_COUNT) { "الصفحات يجب أن تكون بين 1 و604" }
        val reverse = when (request.direction) {
            QuranTraversalDirection.FORWARD_QURAN -> {
                require(endPage >= startPage) { "اتجاه الصفحات لا يطابق ترتيب المصحف" }
                false
            }
            QuranTraversalDirection.REVERSE_SURAH_ORDER -> {
                require(endPage <= startPage) { "عند عكس ترتيب السور يجب أن تتناقص أرقام الصفحات" }
                true
            }
            QuranTraversalDirection.AUTO_BY_ACTIVITY -> endPage < startPage
        }
        val pages = if (reverse) (startPage downTo endPage).toList() else (startPage..endPage).toList()
        val keys = pages.flatMap { page -> (1..layout.lineModel).map { line -> "$page:$line" } }
        val ayahs = linkedSetOf<Int>()
        pages.forEach { page ->
            for (global in QuranMetadata.pageStarts[page] until QuranMetadata.pageStarts[page + 1]) ayahs += global
        }
        val surahs = ayahs.map { QuranMetadata.location(it).surah }.toSet()
        val start = QuranMetadata.pageStart(startPage)
        val end = QuranMetadata.pageEnd(endPage)
        return QuranRangeResult(
            inputMethod = "pages",
            requestedDirection = request.direction,
            resolvedTraversal = if (reverse) QuranResolvedTraversal.PAGE_ORDER_REVERSE else QuranResolvedTraversal.PAGE_ORDER_FORWARD,
            orderedSegments = emptyList(),
            start = start,
            end = end,
            startPage = startPage,
            endPage = endPage,
            exactPages = pages.size.toDouble(),
            ayahCount = ayahs.size,
            surahCount = surahs.size,
            qcfLineKeys = keys,
        )
    }

    private fun calculateSurahs(request: QuranRangeRequest, input: List<Int>): QuranRangeResult {
        require(input.isNotEmpty()) { "اختر سورة واحدة على الأقل" }
        val raw = input.distinct()
        raw.forEach { require(it in 1..114) { "رقم سورة غير صالح: $it" } }
        val ordered = when (request.direction) {
            QuranTraversalDirection.FORWARD_QURAN -> raw.sorted()
            QuranTraversalDirection.REVERSE_SURAH_ORDER -> raw.sortedDescending()
            QuranTraversalDirection.AUTO_BY_ACTIVITY -> if (isGeneralHifz(request)) raw.sortedDescending() else raw
        }
        val segments = ordered.map { s -> QuranSegment(QuranLocation(s, 1), QuranLocation(s, QuranMetadata.maxAyah(s))) }
        return resultForSegments(
            request, "surahs", QuranResolvedTraversal.EXPLICIT_SEGMENTS, segments,
            segments.first().start, segments.last().end,
            selectedSurahs = ordered,
        )
    }

    private fun calculateJuz(request: QuranRangeRequest, input: List<Int>): QuranRangeResult {
        require(input.isNotEmpty()) { "اختر جزءًا واحدًا على الأقل" }
        val raw = input.distinct()
        raw.forEach { require(it in 1..30) { "رقم جزء غير صالح: $it" } }
        val ordered = when (request.direction) {
            QuranTraversalDirection.FORWARD_QURAN -> raw.sorted()
            QuranTraversalDirection.REVERSE_SURAH_ORDER -> raw.sortedDescending()
            QuranTraversalDirection.AUTO_BY_ACTIVITY -> if (isGeneralHifz(request)) raw.sortedDescending() else raw
        }
        val reverseInsideJuz = request.direction == QuranTraversalDirection.REVERSE_SURAH_ORDER ||
            (request.direction == QuranTraversalDirection.AUTO_BY_ACTIVITY && isGeneralHifz(request))
        val segments = ordered.flatMap { j ->
            val juzStart = QuranMetadata.juzStart(j)
            val juzEnd = QuranMetadata.juzEnd(j)
            if (reverseInsideJuz) reverseBlockSegments(juzStart, juzEnd)
            else listOf(QuranSegment(juzStart, juzEnd))
        }
        return resultForSegments(
            request, "juz", QuranResolvedTraversal.EXPLICIT_SEGMENTS, segments,
            segments.first().start, segments.last().end,
            selectedJuz = ordered,
        )
    }


    /**
     * Reorders a normal Quran-order block by surah number while keeping every
     * individual surah slice ascending. Used by general-hifz Juz selection.
     */
    private fun reverseBlockSegments(start: QuranLocation, end: QuranLocation): List<QuranSegment> {
        require(QuranMetadata.globalAyah(start.surah, start.ayah) <= QuranMetadata.globalAyah(end.surah, end.ayah))
        if (start.surah == end.surah) return listOf(QuranSegment(start, end))
        return buildList {
            for (s in end.surah downTo start.surah) {
                val firstAyah = if (s == start.surah) start.ayah else 1
                val lastAyah = if (s == end.surah) end.ayah else QuranMetadata.maxAyah(s)
                add(QuranSegment(QuranLocation(s, firstAyah), QuranLocation(s, lastAyah)))
            }
        }
    }

    private fun calculateExplicitSegments(request: QuranRangeRequest, ranges: List<QuranSelection.Ayahs>): QuranRangeResult {
        require(ranges.isNotEmpty()) { "أضف نطاقًا واحدًا على الأقل" }
        val results = ranges.map { calculateAyahs(request.copy(selection = it), it.start, it.end) }
        val segments = results.flatMap { it.orderedSegments }
        return resultForSegments(
            request, "ayahs", QuranResolvedTraversal.EXPLICIT_SEGMENTS, segments,
            results.first().start, results.last().end,
        )
    }

    private fun resultForSegments(
        request: QuranRangeRequest,
        inputMethod: String,
        resolved: QuranResolvedTraversal,
        segments: List<QuranSegment>,
        start: QuranLocation?,
        end: QuranLocation?,
        selectedSurahs: List<Int> = emptyList(),
        selectedJuz: List<Int> = emptyList(),
    ): QuranRangeResult {
        val orderedKeys = LinkedHashSet<String>()
        val ayahs = LinkedHashSet<Int>()
        val surahs = LinkedHashSet<Int>()
        for (segment in segments) {
            validate(segment.start); validate(segment.end)
            val globals = globalsFor(segment)
            for (g in globals) {
                ayahs += g
                val loc = QuranMetadata.location(g)
                surahs += loc.surah
                val page = layout.page(g)
                val first = layout.firstLine(g)
                val last = layout.lastLine(g)
                for (line in first..last) orderedKeys += "$page:$line"
            }
        }
        val firstKey = orderedKeys.firstOrNull()
        val lastKey = orderedKeys.lastOrNull()
        return QuranRangeResult(
            inputMethod = inputMethod,
            requestedDirection = request.direction,
            resolvedTraversal = resolved,
            orderedSegments = segments,
            start = start,
            end = end,
            startPage = firstKey?.substringBefore(':')?.toIntOrNull() ?: start?.let { QuranMetadata.pageFor(it.surah, it.ayah) },
            endPage = lastKey?.substringBefore(':')?.toIntOrNull() ?: end?.let { QuranMetadata.pageFor(it.surah, it.ayah) },
            exactPages = orderedKeys.size.toDouble() / layout.lineModel.toDouble(),
            ayahCount = ayahs.size,
            surahCount = surahs.size,
            qcfLineKeys = orderedKeys.toList(),
            selectedSurahs = selectedSurahs,
            selectedJuz = selectedJuz,
        )
    }

    private fun resolveAyahTraversal(request: QuranRangeRequest, start: QuranLocation, end: QuranLocation): QuranResolvedTraversal {
        return when (request.direction) {
            QuranTraversalDirection.FORWARD_QURAN -> {
                require(QuranMetadata.globalAyah(start.surah, start.ayah) <= QuranMetadata.globalAyah(end.surah, end.ayah)) {
                    "نهاية النطاق يجب أن تأتي بعد البداية في ترتيب المصحف"
                }
                QuranResolvedTraversal.FORWARD_QURAN
            }
            QuranTraversalDirection.REVERSE_SURAH_ORDER -> {
                validateReverseSurahOrder(start, end)
                QuranResolvedTraversal.REVERSE_SURAH_ORDER
            }
            QuranTraversalDirection.AUTO_BY_ACTIVITY -> {
                if (isGeneralHifz(request)) {
                    validateReverseSurahOrder(start, end)
                    QuranResolvedTraversal.REVERSE_SURAH_ORDER
                } else when {
                    start.surah < end.surah -> QuranResolvedTraversal.FORWARD_QURAN
                    start.surah > end.surah -> QuranResolvedTraversal.REVERSE_SURAH_ORDER
                    start.ayah <= end.ayah -> QuranResolvedTraversal.FORWARD_QURAN
                    else -> QuranResolvedTraversal.REVERSE_WITHIN_SURAH
                }
            }
        }
    }

    private fun validateReverseSurahOrder(start: QuranLocation, end: QuranLocation) {
        require(start.surah >= end.surah) { "الاتجاه المعتمد هنا من السورة الأعلى رقمًا إلى الأقل" }
        if (start.surah == end.surah) {
            require(start.ayah <= end.ayah) { "داخل السورة في هذا الاتجاه تُقرأ الآيات تصاعديًا" }
        }
    }

    private fun forwardSegments(start: QuranLocation, end: QuranLocation): List<QuranSegment> {
        require(start.surah <= end.surah)
        if (start.surah == end.surah) {
            require(start.ayah <= end.ayah)
            return listOf(QuranSegment(start, end))
        }
        return buildList {
            add(QuranSegment(start, QuranLocation(start.surah, QuranMetadata.maxAyah(start.surah))))
            for (s in (start.surah + 1) until end.surah) add(QuranSegment(QuranLocation(s, 1), QuranLocation(s, QuranMetadata.maxAyah(s))))
            add(QuranSegment(QuranLocation(end.surah, 1), end))
        }
    }

    private fun reverseSurahSegments(start: QuranLocation, end: QuranLocation): List<QuranSegment> {
        require(start.surah >= end.surah)
        if (start.surah == end.surah) {
            require(start.ayah <= end.ayah)
            return listOf(QuranSegment(start, end))
        }
        return buildList {
            add(QuranSegment(start, QuranLocation(start.surah, QuranMetadata.maxAyah(start.surah))))
            for (s in (start.surah - 1) downTo (end.surah + 1)) add(QuranSegment(QuranLocation(s, 1), QuranLocation(s, QuranMetadata.maxAyah(s))))
            add(QuranSegment(QuranLocation(end.surah, 1), end))
        }
    }

    private fun globalsFor(segment: QuranSegment): IntProgression {
        val a = QuranMetadata.globalAyah(segment.start.surah, segment.start.ayah)
        val b = QuranMetadata.globalAyah(segment.end.surah, segment.end.ayah)
        return when (segment.ayahDirection) {
            QuranAyahDirection.ASCENDING -> {
                require(a <= b) { "النطاق الصاعد داخل المقطع غير صالح" }
                a..b
            }
            QuranAyahDirection.DESCENDING -> {
                require(segment.start.surah == segment.end.surah && a >= b) { "النطاق النازل داخل السورة غير صالح" }
                a downTo b
            }
        }
    }

    private fun isGeneralHifz(request: QuranRangeRequest): Boolean =
        request.track == "general_hifz" && request.activityType in setOf("new_hifz", "hifz")

    private fun validate(location: QuranLocation) {
        require(location.surah in 1..114)
        require(location.ayah in 1..QuranMetadata.maxAyah(location.surah))
    }
}
