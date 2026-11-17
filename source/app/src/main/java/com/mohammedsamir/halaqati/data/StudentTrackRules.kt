package com.mohammedsamir.halaqati.data

/**
 * Canonical student-track contract for the Native app.
 * The product has exactly two student tracks:
 *  - general_hifz: مسار الحفظ العام
 *  - tathbit: مسار التثبيت
 *
 * Legacy production rows may still contain `sard`; that value is read as
 * general_hifz. Sard remains a recitation/activity type, never a third track.
 */
object StudentTrackRules {
    const val GENERAL_HIFZ = "general_hifz"
    const val TATHBIT = "tathbit"
    val allowed = setOf(GENERAL_HIFZ, TATHBIT)

    fun canonicalStored(value: String?): String = when (value?.trim()?.lowercase()) {
        TATHBIT -> TATHBIT
        GENERAL_HIFZ, "sard", "", null -> GENERAL_HIFZ
        else -> GENERAL_HIFZ
    }

    /** Strict parser for user/import input. Blank means the normal general track. */
    fun parseInput(value: String?): String? {
        val raw = value.orEmpty().trim()
        if (raw.isBlank()) return GENERAL_HIFZ
        val n = raw.lowercase()
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace(Regex("\\s+"), " ")
        return when {
            n == GENERAL_HIFZ || n == "hifz" || n == "general hifz" || n == "general-hifz" ||
                n.contains("الحفظ العام") || n == "حفظ" || n == "سرد" || n == "sard" -> GENERAL_HIFZ
            n == TATHBIT || n.contains("تثبيت") -> TATHBIT
            else -> null
        }
    }

    fun label(value: String?): String = when (canonicalStored(value)) {
        TATHBIT -> "مسار التثبيت"
        else -> "مسار الحفظ العام"
    }

    /** PostgREST filter that also includes legacy `sard` rows in general_hifz. */
    fun postgrestFilter(column: String, track: String): String = when (track) {
        GENERAL_HIFZ -> "$column=in.($GENERAL_HIFZ,sard)"
        TATHBIT -> "$column=eq.$TATHBIT"
        else -> error("المسار غير صالح")
    }
}


/**
 * RC15 single source of truth: tathbit is a first-class activity, not review/review_far.
 * A tathbit student gets tathbit plans, tathbit phases and tathbit recitations only
 * ("test" stays allowed for milestone exams). General-hifz students never get tathbit.
 */
object TrackActivityPolicy {
    const val TATHBIT = "tathbit"
    val TATHBIT_RECITATIONS = setOf(TATHBIT, "tilawah", "test")
    val GENERAL_RECITATIONS = setOf("new_hifz", "review_near", "review_far", "sard", "tilawah", "test")
    /** Legacy rows written before RC15 stored tathbit as review_far. Read-compat only, never written. */
    val LEGACY_TATHBIT_READ = setOf(TATHBIT, "review_far")

    fun isTathbit(track: String?): Boolean = StudentTrackRules.canonicalStored(track) == StudentTrackRules.TATHBIT

    fun allowedRecitations(track: String?): Set<String> = if (isTathbit(track)) TATHBIT_RECITATIONS else GENERAL_RECITATIONS

    fun allowedPlanTypes(track: String?): Set<String> = if (isTathbit(track)) setOf(TATHBIT) else setOf("hifz", "review", "sard")

    fun allowedPhaseTypes(track: String?): Set<String> = if (isTathbit(track)) setOf(TATHBIT) else setOf("hifz", "sard")

    /** Plan type written for a student-scope plan. Tathbit students are forced to tathbit. */
    fun planTypeFor(track: String?, requested: String): String {
        if (isTathbit(track)) return TATHBIT
        require(requested != TATHBIT) { "خطة التثبيت متاحة لطلاب مسار التثبيت فقط" }
        return requested
    }

    /** Normalizes a recitation activity for the student's track or rejects it. */
    fun recitationActivityFor(track: String?, requested: String): String {
        val r = requested.trim().lowercase()
        if (isTathbit(track)) {
            return when (r) {
                TATHBIT, "review", "review_near", "review_far" -> TATHBIT
                "tilawah" -> "tilawah"
                "test" -> "test"
                else -> error("طالب مسار التثبيت يُسجَّل له تثبيت أو تلاوة أو اختبار فقط؛ لا حفظ ولا سرد ولا مراجعة")
            }
        }
        require(r != TATHBIT) { "التثبيت نشاط خاص بطلاب مسار التثبيت" }
        return r
    }

    /**
     * Activity for the one-tap "recited" button, from the smart-plan assignment kind.
     *
     * A general-hifz student who must repeat a failed portion gets an assignment of kind "tathbit" (a retry),
     * which is not a recordable activity for that track. Like the full recitation form, fall back to the
     * activity of the session instead of refusing to save.
     */
    fun quickSaveActivity(track: String?, assignmentKind: String, sessionType: String?): String {
        val requested = when (assignmentKind.trim().lowercase()) {
            "sard" -> "sard"
            "review" -> "review_near"
            TATHBIT -> TATHBIT
            "test" -> "test"
            else -> "new_hifz"
        }
        return runCatching { recitationActivityFor(track, requested) }.getOrElse {
            if (isTathbit(track)) TATHBIT else when (sessionType?.trim()?.lowercase()) {
                "sard" -> "sard"
                "review" -> "review_near"
                "test" -> "test"
                "tajweed", "tilawah" -> "tilawah"
                else -> "new_hifz"
            }
        }
    }
}
