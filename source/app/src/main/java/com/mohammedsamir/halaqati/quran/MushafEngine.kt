package com.mohammedsamir.halaqati.quran

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Interactive mushaf engine (pure Kotlin, no Android types): page layout and the marking session.
 * It is the same engine as the web one (mushaf-state.js): the printed Madinah page of 15 lines is rebuilt from the Quran.com
 * word data (QCF V2 glyph codes and printed line numbers), and marks (mistake / prompt) are counted by the circle's rules.
 * The Quran text is never typed in this repo: it only ever comes from the API response (or its cache).
 */
object MushafEngine {
    const val TOTAL_PAGES = 604

    /** The only six words whose API line number differs from the printed layout: page -> (word id -> printed line). */
    private val LINE_OVERRIDES: Map<Int, Map<String, Int>> = mapOf(
        27 to mapOf("2:181:5" to 15), 177 to mapOf("8:6:10" to 12), 254 to mapOf("13:37:8" to 6),
        400 to mapOf("29:33:11" to 6), 443 to mapOf("36:52:10" to 13), 589 to mapOf("84:21:end" to 14),
    )

    /** Pages whose last printed line is a surah header (the surah's first ayah starts on the next page): page -> surah. */
    val HEADER_AT_PAGE_END: Map<Int, Int> = mapOf(
        76 to 4, 207 to 10, 331 to 22, 341 to 23, 349 to 24, 366 to 26, 376 to 27, 414 to 32, 417 to 33, 445 to 37,
        452 to 38, 498 to 45, 506 to 47, 525 to 53, 548 to 60, 555 to 64, 557 to 65, 584 to 80,
    )

    /** Short lines the 1421H Madinah print centres (pages 1-2 are fully centred). */
    private val CENTERED_LINES: Map<Int, Set<Int>> = mapOf(
        255 to setOf(2), 528 to setOf(9), 534 to setOf(6), 545 to setOf(6), 586 to setOf(1), 593 to setOf(2), 594 to setOf(5),
        600 to setOf(10), 602 to setOf(5, 11, 15), 603 to setOf(10, 15), 604 to setOf(4, 9, 14, 15),
    )

    data class ApiWord(
        val isEnd: Boolean,
        val position: Int,
        val pageNumber: Int,
        val v2Page: Int,
        val lineNumber: Int,
        val code: String,
        val text: String,
    )

    data class ApiVerse(val key: String, val juz: Int?, val words: List<ApiWord>)

    data class Item(
        val id: String,
        val ayahKey: String,
        val surah: Int,
        val ayah: Int,
        val pos: Int,
        val text: String,
        val glyph: String,
        val fontPage: Int,
        val line: Int,
        val isEnd: Boolean,
    )

    data class Line(val n: Int, val kind: LineKind, val items: List<Item>, val surah: Int = 0) {
        val words: List<Item> get() = items.filter { !it.isEnd }
    }

    enum class LineKind { Text, Empty, Banner, Basmala }

    data class PageData(val page: Int, val juz: Int?, val items: List<Item>) {
        val words: List<Item> get() = items.filter { !it.isEnd }
    }

    fun clampPage(page: Int): Int = page.coerceIn(1, TOTAL_PAGES)

    fun parseAyahKey(key: String): Pair<Int, Int>? {
        val parts = key.split(':')
        val s = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val a = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return if (s in 1..114 && a >= 1) s to a else null
    }

    fun parseWordId(id: String): Triple<Int, Int, Int>? {
        val m = Regex("^(\\d{1,3}):(\\d{1,3}):(\\d{1,3})$").find(id) ?: return null
        val s = m.groupValues[1].toInt(); val a = m.groupValues[2].toInt(); val p = m.groupValues[3].toInt()
        return if (s in 1..114 && a >= 1 && p >= 1) Triple(s, a, p) else null
    }

    private fun printedLine(page: Int, id: String, line: Int): Int = LINE_OVERRIDES[clampPage(page)]?.get(id) ?: line

    /** Builds one printed page from the verses of the API page and its neighbours (words are kept by their own page_number). */
    fun normalizePage(page: Int, verses: List<ApiVerse>): PageData {
        val p = clampPage(page)
        val items = ArrayList<Item>()
        val seen = HashSet<String>()
        for (v in verses) {
            val (surah, ayah) = parseAyahKey(v.key) ?: continue
            for (w in v.words) {
                if (w.pageNumber != 0 && w.pageNumber != p) continue
                val uid = "$surah:$ayah:" + if (w.isEnd) "end" else w.position.toString()
                if (!seen.add(uid)) continue
                val fontPage = if (w.v2Page != 0) w.v2Page else if (w.pageNumber != 0) w.pageNumber else p
                if (w.isEnd) {
                    items += Item(uid, "$surah:$ayah", surah, ayah, 0, "", w.code, fontPage, printedLine(p, uid, w.lineNumber), true)
                    continue
                }
                if (w.position < 1) continue
                items += Item(uid, "$surah:$ayah", surah, ayah, w.position, w.text, w.code, fontPage, printedLine(p, uid, w.lineNumber), false)
            }
        }
        val juz = verses.firstOrNull()?.juz
        return PageData(p, juz, items)
    }

    /** The printed page has 15 lines. Lines with no words are the surah banner / basmala lines (right before a surah's first ayah) or empty. */
    fun buildPageLines(data: PageData): List<Line> {
        val byLine = HashMap<Int, MutableList<Item>>()
        data.items.forEach { if (it.line in 1..15) byLine.getOrPut(it.line) { ArrayList() }.add(it) }
        val kinds = Array(15) { if (byLine.containsKey(it + 1)) LineKind.Text else LineKind.Empty }
        val surahs = IntArray(15)
        for (idx in 0 until 15) {
            val first = byLine[idx + 1]?.firstOrNull { !it.isEnd } ?: continue
            if (first.pos != 1 || first.ayah != 1) continue
            val s = first.surah
            val above = idx - 1
            val hasBasmala = s != 1 && s != 9
            if (above >= 0 && kinds[above] == LineKind.Empty) {
                if (hasBasmala && above - 1 >= 0 && kinds[above - 1] == LineKind.Empty) {
                    kinds[above] = LineKind.Basmala; surahs[above] = s
                    kinds[above - 1] = LineKind.Banner; surahs[above - 1] = s
                } else { kinds[above] = LineKind.Banner; surahs[above] = s }
            }
        }
        HEADER_AT_PAGE_END[data.page]?.let { if (kinds[14] == LineKind.Empty) { kinds[14] = LineKind.Banner; surahs[14] = it } }
        HEADER_AT_PAGE_END[data.page - 1]?.let { if (kinds[0] != LineKind.Text) { kinds[0] = LineKind.Basmala; surahs[0] = it } }
        return (0 until 15).map { Line(it + 1, kinds[it], byLine[it + 1] ?: emptyList(), surahs[it]) }
    }

    fun isOpeningPage(page: Int) = page == 1 || page == 2
    fun isCenteredLine(page: Int, line: Int) = isOpeningPage(page) || CENTERED_LINES[page]?.contains(line) == true

    /** Lines are drawn at one size; only a line wider than the text block is made smaller, by one ratio (the print never stretches words). */
    fun overflowScale(natural: Double, available: Double): Double {
        if (!(available > 0.0) || !(natural > available)) return 1.0
        return Math.round(available * 0.99 / natural * 1000.0) / 1000.0
    }

    /** ids of the words between two words (inclusive) in reading order, whatever the drag direction; empty when either is not on the page. */
    fun spanBetween(lines: List<Line>, idA: String, idB: String): List<String> {
        val ids = lines.flatMap { l -> l.words.map { it.id } }
        val a = ids.indexOf(idA); val b = ids.indexOf(idB)
        if (a == -1 || b == -1) return emptyList()
        return ids.subList(min(a, b), max(a, b) + 1)
    }

    // ------------------------------------------------------------------ marking session

    enum class MarkType { Mistake, Prompt }
    enum class MarkKind { Word, Harakah, AyahStart }

    data class Mark(val id: String, val ayahKey: String, val word: Int, val type: MarkType, val kind: MarkKind, val page: Int, val span: Int = 0)

    data class Tally(val mistakes: Int, val prompts: Int, val promptsExact: Double)
    data class Delta(val mist: Int, val prompt: Int)
    data class OverLimit(val page: Int, val mistakes: Int, val prompts: Int)
    data class Applied(val delta: Delta, val changed: Int, val page: Int? = null)

    private data class Change(val id: String, val before: Mark?, val after: Mark?)

    /**
     * Counting rules of the circle: a mistake is a phrase (one highlighted span, or words that sit next to each other in an ayah) = 1;
     * a prompt is one word, and every two harakat are one prompt.
     */
    fun tally(list: List<Mark>): Tally {
        val mist = list.filter { it.type == MarkType.Mistake }
        val parent = IntArray(mist.size) { it }
        fun find(i: Int): Int { var x = i; while (parent[x] != x) { parent[x] = parent[parent[x]]; x = parent[x] }; return x }
        fun join(a: Int, b: Int) { parent[find(a)] = find(b) }
        val loc = mist.map { parseAyahKey(it.ayahKey) ?: (0 to 0) }
        val bySpan = HashMap<Int, MutableList<Int>>()
        val byKey = HashMap<String, Int>()
        mist.forEachIndexed { i, m ->
            if (m.span != 0) bySpan.getOrPut(m.span) { ArrayList() }.add(i)
            byKey[m.ayahKey + ":" + m.word] = i
        }
        // a highlighted span is one phrase only while its words stay in a run: erasing a word in the middle splits it
        bySpan.values.forEach { group ->
            val idx = group.sortedWith(compareBy({ loc[it].first }, { loc[it].second }, { mist[it].word }))
            for (q in 1 until idx.size) {
                val a = mist[idx[q - 1]]; val b = mist[idx[q]]; val la = loc[idx[q - 1]]; val lb = loc[idx[q]]
                val sameAyahNext = a.ayahKey == b.ayahKey && b.word == a.word + 1
                val nextAyahFirst = b.word == 1 && ((lb.first == la.first && lb.second == la.second + 1) || (lb.first == la.first + 1 && lb.second == 1))
                if (sameAyahNext || nextAyahFirst) join(idx[q - 1], idx[q])
            }
        }
        mist.forEachIndexed { i, m -> byKey[m.ayahKey + ":" + (m.word + 1)]?.let { join(i, it) } }
        val roots = HashSet<Int>()
        mist.indices.forEach { roots.add(find(it)) }
        var exact = 0.0
        list.forEach { if (it.type == MarkType.Prompt) exact += if (it.kind == MarkKind.Harakah) 0.5 else 1.0 }
        return Tally(roots.size, floor(exact + 1e-9).toInt(), exact)
    }

    class Session(initialPage: Int = 1) {
        var page: Int = clampPage(initialPage)
            private set
        private val marks = ArrayList<Mark>()
        private val past = ArrayList<List<Change>>()
        private var future = ArrayList<List<Change>>()
        private var spanSeq = 0

        fun setPage(n: Int): Int { page = clampPage(n); return page }
        fun marksSnapshot(): List<Mark> = marks.toList()
        fun getMark(id: String): Mark? = marks.firstOrNull { it.id == id }
        val canUndo: Boolean get() = past.isNotEmpty()
        val canRedo: Boolean get() = future.isNotEmpty()
        val isEmpty: Boolean get() = marks.isEmpty()

        private fun indexOf(id: String) = marks.indexOfFirst { it.id == id }
        private fun setMark(id: String, mark: Mark?) {
            val i = indexOf(id)
            if (mark != null) { if (i == -1) marks.add(mark) else marks[i] = mark } else if (i != -1) marks.removeAt(i)
        }
        private fun apply(changes: List<Change>, forward: Boolean): Delta {
            val t0 = tally(marks)
            changes.forEach { setMark(it.id, if (forward) it.after else it.before) }
            val t1 = tally(marks)
            return Delta(t1.mistakes - t0.mistakes, t1.prompts - t0.prompts)
        }
        private fun commit(changes: List<Change>): Applied {
            if (changes.isEmpty()) return Applied(Delta(0, 0), 0)
            val d = apply(changes, true)
            past.add(changes); future = ArrayList()
            return Applied(d, changes.size)
        }
        private fun build(id: String, type: MarkType, kind: MarkKind, pageNo: Int?, span: Int): Mark {
            val (s, a, p) = parseWordId(id) ?: throw IllegalArgumentException("Invalid word id: $id")
            return Mark(id, "$s:$a", p, type, kind, clampPage(pageNo ?: page), span)
        }

        fun markWord(id: String, type: MarkType, kind: MarkKind? = null, pageNo: Int? = null): Applied {
            val existing = getMark(id)
            val removing = existing != null && existing.type == type && (kind == null || kind == existing.kind)
            val after = if (removing) null else build(id, type, kind ?: MarkKind.Word, pageNo, 0)
            return commit(listOf(Change(id, existing, after)))
        }

        fun markSpan(ids: List<String>, type: MarkType, kind: MarkKind = MarkKind.Word, pageNo: Int? = null): Applied {
            val seen = HashSet<String>(); val changes = ArrayList<Change>(); val span = ++spanSeq
            ids.forEach { id ->
                if (!seen.add(id)) return@forEach
                val existing = getMark(id)
                if (existing != null && existing.type == type && existing.kind == kind) return@forEach
                changes += Change(id, existing, build(id, type, kind, pageNo, span))
            }
            return commit(changes)
        }

        fun eraseWords(ids: List<String>): Applied {
            val seen = HashSet<String>(); val changes = ArrayList<Change>()
            ids.forEach { id -> if (seen.add(id)) getMark(id)?.let { changes += Change(id, it, null) } }
            return commit(changes)
        }

        fun undo(): Applied? {
            val changes = past.removeLastOrNull() ?: return null
            future.add(changes)
            return Applied(apply(changes, false), changes.size, (changes[0].after ?: changes[0].before)?.page)
        }

        fun redo(): Applied? {
            val changes = future.removeLastOrNull() ?: return null
            past.add(changes)
            return Applied(apply(changes, true), changes.size, (changes[0].after ?: changes[0].before)?.page)
        }

        fun totals(): Tally = tally(marks)
        fun pageTally(p: Int): Tally = tally(marks.filter { it.page == p })

        /** Pages where the marks go over the circle's limit for one page (one mistake, two prompts), however the whole recitation averages out. */
        fun overLimitPages(): List<OverLimit> =
            marks.map { it.page }.distinct().sorted().mapNotNull { p ->
                val t = pageTally(p)
                if (t.mistakes > 1 || t.promptsExact > 2.0) OverLimit(p, t.mistakes, t.prompts) else null
            }
    }
}
