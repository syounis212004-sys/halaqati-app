package com.mohammedsamir.halaqati.quran

/** Independent tilawah ledger. Never uses memorization mistake/prompt tally. */
enum class TilawahCategory(val key: String, val label: String, val major: Boolean) {
    WORD("word", "كلمة", true), LETTER("letter", "حرف", true), HARAKAH("harakah", "حركة", true),
    MADD("madd", "المدود", false), GHUNNAH("ghunnah", "الغنة", false),
    IKHFA_IDGHAM("ikhfa_idgham", "الإخفاء والإدغام", false),
    QALQALAH("qalqalah", "القلقلة", false), MAKHARIJ("makharij", "المخارج والصفات", false),
    WAQF("waqf", "الوقف والابتداء", false), TAFKHIM("tafkhim", "التفخيم والترقيق", false),
}

data class TilawahMark(
    val wordId: String, val page: Int, val category: TilawahCategory,
    val occurrences: Int = 1, val corrected: Boolean = false,
    val note: String = "", val subtype: String = "", val previousMarkId: String? = null,
) {
    val markId: String get() = "$wordId:${category.key}"
    init {
        require(MushafEngine.parseWordId(wordId) != null && page in 1..604)
        require(occurrences in 1..99 && note.length <= 500 && subtype.length <= 80)
    }
}

object TilawahMarks {
    const val MAX_MARKS = 512

    /** Explicit repeat increments one category on each selected word, never overwrites another category. */
    fun add(current: List<TilawahMark>, ids: List<String>, page: Int, category: TilawahCategory,
            note: String = "", subtype: String = ""): List<TilawahMark> {
        val next = current.associateBy { it.markId }.toMutableMap()
        ids.distinct().forEach { id ->
            val mark = TilawahMark(id, page, category, note = note.trim(), subtype = subtype.trim())
            val old = next[mark.markId]
            require(old == null || old.page == page) { "موضع العلامة غير متطابق" }
            require(old == null || old.occurrences < 99) { "تم بلوغ الحد الأقصى لتكرار هذه العلامة" }
            next[mark.markId] = old?.copy(
                occurrences = old.occurrences + 1, corrected = false,
                note = mark.note.ifBlank { old.note }, subtype = mark.subtype.ifBlank { old.subtype },
            ) ?: mark
        }
        require(next.size <= MAX_MARKS) { "تم بلوغ الحد الأقصى للعلامات في هذا التسميع" }
        return next.values.toList()
    }

    fun remove(current: List<TilawahMark>, id: String): List<TilawahMark> =
        current.filterNot { it.markId == id }

    fun eraseWords(current: List<TilawahMark>, ids: List<String>): List<TilawahMark> =
        current.filterNot { it.wordId in ids }

    fun correct(current: List<TilawahMark>, id: String): List<TilawahMark> =
        current.map { if (it.markId == id) it.copy(corrected = !it.corrected) else it }

    /** A correction changes status, not the historical number of errors. */
    fun count(current: List<TilawahMark>, category: TilawahCategory): Int =
        current.filter { it.category == category }.sumOf { it.occurrences }

    fun total(manual: Int, current: List<TilawahMark>, category: TilawahCategory): Int =
        (manual.coerceAtLeast(0).toLong() + count(current, category)).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}
