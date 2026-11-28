package com.mohammedsamir.halaqati.ui.sessions

import com.mohammedsamir.halaqati.plans.WorkModeRules

/**
 * RC9.2 session-level rules kept Android-free so the roster state/filter logic can be
 * regression-tested without Compose or Room.
 */
object SessionWorkflowRules {
    val manualSessionTypes = listOf("tasmee", "sard", "review", "test", "tajweed", "tilawah", "lesson", "other")

    fun manualTypesForScope(halaqaType: String?): List<String> =
        if (WorkModeRules.isProgramType(halaqaType)) listOf("tilawah", "lesson", "test", "other")
        else manualSessionTypes.filterNot { it == "tajweed" }

    fun canTransition(from: String, to: String): Boolean =
        (from == "open" && to == "closed") || (from in setOf("planned","closed") && to == "open")

    fun statusLabel(status: String): String = when (status) {
        "open" -> "مفتوحة"
        "closed" -> "مغلقة"
        "planned" -> "مجدولة"
        "cancelled" -> "ملغاة"
        else -> "حالة غير معروفة"
    }

    const val NOT_RECITED_MARKER = "halaqati:not_recited"

    enum class StudentState {
        UNMARKED,
        NOT_RECITED,
        RECITED,
        ABSENT,
        EXCUSED,
        LATE,
        PRESENT,
    }

    fun sessionTypeForAssignment(assignmentKind: String): String = when (assignmentKind) {
        "sard" -> "sard"
        "review", "tathbit" -> "review"
        "test" -> "test"
        else -> "tasmee"
    }

    /** One halaqa can have at most one non-cancelled session of a given type per day. */
    fun dailySessionKey(halaqaId: String, date: String, sessionType: String): String =
        "${halaqaId.trim()}|${date.trim()}|${sessionType.trim()}"

    /** Closed sessions are still today's canonical session; only cancelled rows may be replaced. */
    fun isReusableDailySessionStatus(status: String): Boolean = status.lowercase() != "cancelled"

    fun studentState(
        attendanceStatus: String?,
        participation: String?,
        hasRecitations: Boolean,
    ): StudentState = when (attendanceStatus) {
        null, "", "unmarked" -> if (participation == NOT_RECITED_MARKER) StudentState.NOT_RECITED else StudentState.UNMARKED
        "absent" -> StudentState.ABSENT
        "excused" -> StudentState.EXCUSED
        "late" -> when {
            hasRecitations -> StudentState.RECITED
            participation == NOT_RECITED_MARKER -> StudentState.NOT_RECITED
            else -> StudentState.LATE
        }
        "present" -> when {
            hasRecitations -> StudentState.RECITED
            participation == NOT_RECITED_MARKER -> StudentState.NOT_RECITED
            else -> StudentState.PRESENT
        }
        else -> if (hasRecitations) StudentState.RECITED else StudentState.UNMARKED
    }

    data class Summary(
        val total: Int,
        val unmarked: Int,
        val notRecited: Int,
        val recited: Int,
        val present: Int,
        val absent: Int,
        val excused: Int = 0,
    )

    fun summary(states: Collection<StudentState>): Summary = Summary(
        total = states.size,
        unmarked = states.count { it == StudentState.UNMARKED },
        notRecited = states.count { it == StudentState.NOT_RECITED },
        recited = states.count { it == StudentState.RECITED },
        present = states.count { it in setOf(StudentState.PRESENT, StudentState.LATE, StudentState.NOT_RECITED, StudentState.RECITED) },
        absent = states.count { it == StudentState.ABSENT },
        excused = states.count { it == StudentState.EXCUSED },
    )

    fun matchesSearch(value: String, query: String): Boolean {
        val needle = normalizeSearch(query)
        if (needle.isBlank()) return true
        return normalizeSearch(value).contains(needle)
    }

    private fun normalizeSearch(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[\u064B-\u065F\u0670]"), "")
        .replace(Regex("[أإآٱ]"), "ا")
        .replace('ى', 'ي')
        .replace('ة', 'ه')
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()


    fun matchesFilter(state: StudentState, filter: String): Boolean = when (filter) {
        "unmarked" -> state == StudentState.UNMARKED
        "not_recited" -> state == StudentState.NOT_RECITED
        "recited" -> state == StudentState.RECITED
        "present_pending" -> state == StudentState.PRESENT || state == StudentState.LATE
        "absent" -> state == StudentState.ABSENT
        "excused" -> state == StudentState.EXCUSED
        else -> true
    }

    /** Priority order for a teacher opening a session: unfinished work first. */
    fun priority(state: StudentState): Int = when (state) {
        StudentState.UNMARKED -> 0
        StudentState.PRESENT, StudentState.LATE -> 1
        StudentState.NOT_RECITED -> 2
        StudentState.RECITED -> 3
        StudentState.EXCUSED -> 4
        StudentState.ABSENT -> 5
    }
}
