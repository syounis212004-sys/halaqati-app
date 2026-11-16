package com.mohammedsamir.halaqati.data

/** RC27 pure rules for the in-app full backup (admin): which tables, in which stable order, and the page query. */
object BackupRules {
    const val PAGE = 1000

    val TABLES = listOf(
        "profiles", "halaqas", "halaqa_teachers", "halaqa_supervisors", "students", "student_guardians",
        "student_categories", "student_halaqa_memberships", "sessions", "attendance_records",
        "recitation_entries", "student_daily_notes", "student_goals", "monthly_comments", "announcements",
        "supervisor_visits", "student_points", "app_settings", "smart_plans", "smart_plan_events",
        "smart_plan_phases", "smart_plan_phase_states", "smart_plan_phase_templates",
        "smart_plan_month_archives", "smart_plan_health_daily", "certificate_templates", "certificates",
        "certificate_series", "certificate_revisions", "achievement_badge_definitions", "student_badge_awards",
        "management_audit_log", "deleted_session_snapshots", "notification_preferences",
    )

    /** Tables without an `id` column page on their primary key; offsets need a stable order. */
    private val ORDER = mapOf(
        "achievement_badge_definitions" to "code",
        "certificate_series" to "series_key",
        "halaqa_supervisors" to "halaqa_id.asc,supervisor_id",
        "halaqa_teachers" to "halaqa_id.asc,teacher_id",
        "notification_preferences" to "user_id",
        "smart_plan_health_daily" to "plan_id.asc,student_id.asc,day",
        "student_guardians" to "student_id.asc,guardian_id",
    )

    fun orderFor(table: String): String = ORDER[table] ?: "id"

    fun pageQuery(table: String, offset: Int): String =
        "select=*&order=${orderFor(table)}.asc&offset=$offset&limit=$PAGE"

    fun fileName(date: String) = "halaqati-backup-$date.zip"

    /** A page shorter than PAGE is the last one. */
    fun isLastPage(rowsInPage: Int) = rowsInPage < PAGE
}
