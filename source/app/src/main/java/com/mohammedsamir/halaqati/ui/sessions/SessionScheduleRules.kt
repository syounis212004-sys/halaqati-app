package com.mohammedsamir.halaqati.ui.sessions

import java.time.LocalDate

/** Session schedules use ISO weekdays (Mon=1..Sun=7), never smart-plan weekday indexes. */
object SessionScheduleRules {
    val weekdayLabels = listOf(7 to "الأحد",1 to "الاثنين",2 to "الثلاثاء",3 to "الأربعاء",4 to "الخميس",5 to "الجمعة",6 to "السبت")
    fun days(program: Boolean, configured: Set<Int>?): Set<Int> =
        configured?.filter { it in 1..7 }?.toSet() ?: if (program) (1..7).toSet() else setOf(1,2,3,4,5,7)
    fun automaticEnabled(program: Boolean, configured: Boolean?): Boolean = configured ?: !program
    fun allowsAutomatic(date: LocalDate, program: Boolean, configuredDays: Set<Int>?, configuredAuto: Boolean?): Boolean =
        automaticEnabled(program, configuredAuto) && date.dayOfWeek.value in days(program, configuredDays)
    /** Exact ID tokens for known queued session operations; no substring matches. */
    fun pendingFor(id: String, dedupe: String?, path: String?, bodySessionId: String?): Boolean =
        bodySessionId == id || dedupe.orEmpty().split(':').contains(id) ||
            path.orEmpty().substringAfter('?',"").split('&').any {
                it == "id=eq.$id" || it == "session_id=eq.$id"
            }
}
