package com.mohammedsamir.halaqati.model

import com.mohammedsamir.halaqati.data.StudentTrackRules
import com.mohammedsamir.halaqati.quran.RecitationParityRules

import org.json.JSONObject

enum class UserRole { ADMIN, SUPERVISOR, TEACHER, GUARDIAN, STUDENT, OBSERVER, UNKNOWN;
    companion object { fun from(v: String?) = entries.firstOrNull { it.name.equals(v, true) } ?: UNKNOWN }
}

data class UserSession(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String = "",
)

data class Profile(
    val id: String,
    val fullName: String,
    val role: UserRole,
    val approvalStatus: String,
    val email: String = "",
    val active: Boolean = true,
    val requestedRole: UserRole = UserRole.UNKNOWN,
    val rejectionReason: String = "",
    /** RC27.42: false until a new account has completed the sign-up form (name, type, student for a guardian). */
    val onboarded: Boolean = true,
    /** RC53: a frozen account signs in but sees only the "account frozen" page until an admin lifts it. */
    val frozen: Boolean = false,
    /** RC53: storage path of the staff photo (bucket profile-avatars); empty when none. */
    val avatarPath: String = "",
)

data class NavItem(val route: String, val title: String, val iconKey: String)

data class SyncState(
    val online: Boolean = true,
    val syncing: Boolean = false,
    val pending: Int = 0,
    val blocked: Int = 0,
    /** RC31: minutes the oldest unsent change has been waiting (0 = nothing waiting). */
    val stalePendingMinutes: Int = 0,
    val lastSync: String? = null,
    val error: String? = null,
)

data class Metric(val title: String, val value: String, val subtitle: String = "")

data class Student(
    val id: String,
    val fullName: String,
    val status: String = "active",
    val track: String = "general_hifz",
    val categoryId: String? = null,
    val halaqaId: String? = null,
    val primaryTeacherId: String? = null,
    val guardianName: String? = null,
    val nationalId: String? = null,
    val phone: String? = null,
    val dateOfBirth: String? = null,
    val schoolGrade: String? = null,
    val guardianPhone: String? = null,
    val guardianEmail: String? = null,
    val gender: String? = null,
    val enrollmentDate: String? = null,
    val programName: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val photoPath: String? = null,
    val targetPagesWeekly: Double? = null,
    val targetReviewPagesWeekly: Double? = null,
    val updatedAt: String? = null,
) {
    companion object {
        fun from(j: JSONObject) = Student(
            id = j.optString("id"),
            fullName = j.optString("full_name"),
            status = j.optString("status", "active"),
            track = StudentTrackRules.canonicalStored(j.optString("track_code", "general_hifz")),
            categoryId = j.optString("category_id").takeUnless { it.isBlank() || it == "null" },
            halaqaId = j.optString("halaqa_id").takeUnless { it.isBlank() || it == "null" },
            primaryTeacherId = j.optString("primary_teacher_id").takeUnless { it.isBlank() || it == "null" },
            guardianName = j.optString("guardian_name").takeUnless { it.isBlank() || it == "null" },
            nationalId = j.optString("national_id").takeUnless { it.isBlank() || it == "null" },
            phone = j.optString("phone").takeUnless { it.isBlank() || it == "null" },
            dateOfBirth = j.optString("date_of_birth").takeUnless { it.isBlank() || it == "null" },
            schoolGrade = j.optString("school_grade").takeUnless { it.isBlank() || it == "null" },
            guardianPhone = j.optString("guardian_phone").takeUnless { it.isBlank() || it == "null" },
            guardianEmail = j.optString("guardian_email").takeUnless { it.isBlank() || it == "null" },
            gender = j.optString("gender").takeUnless { it.isBlank() || it == "null" },
            enrollmentDate = j.optString("enrollment_date").takeUnless { it.isBlank() || it == "null" },
            programName = j.optString("program_name").takeUnless { it.isBlank() || it == "null" },
            address = j.optString("address").takeUnless { it.isBlank() || it == "null" },
            notes = j.optString("notes").takeUnless { it.isBlank() || it == "null" },
            photoPath = j.optString("photo_path").takeUnless { it.isBlank() || it == "null" },
            targetPagesWeekly = if (!j.has("target_pages_weekly") || j.isNull("target_pages_weekly")) null else j.optDouble("target_pages_weekly"),
            targetReviewPagesWeekly = if (!j.has("target_review_pages_weekly") || j.isNull("target_review_pages_weekly")) null else j.optDouble("target_review_pages_weekly"),
            updatedAt = j.optString("updated_at").takeUnless { it.isBlank() || it == "null" },
        )
    }
}

data class SessionRow(
    val id: String,
    val halaqaId: String,
    val date: String,
    val type: String,
    val title: String,
    val status: String,
    val archived: Boolean = false,
    val notes: String = "",
    val activityDataJson: String = "{}",
) {
    companion object {
        fun from(j: JSONObject) = SessionRow(
            id = j.optString("id"),
            halaqaId = j.optString("halaqa_id"),
            date = j.optString("session_date"),
            type = j.optString("session_type"),
            title = j.optString("title").takeUnless { it.isBlank() || it == "null" } ?: "",
            status = j.optString("status"),
            archived = j.optJSONObject("activity_data")?.optBoolean("archived", false) ?: false,
            notes = j.optString("notes").takeUnless { it.isBlank() || it == "null" } ?: "",
            activityDataJson = (j.optJSONObject("activity_data") ?: JSONObject()).toString(),
        )
    }
}

data class RecitationDraft(
    val studentId: String,
    val sessionId: String,
    val activityType: String,
    val startPage: Int?,
    val endPage: Int?,
    val pages: Double,
    val mistakes: Int,
    val prompts: Int,
    val evaluation: String,
    val notes: String = "",
    val smartPlanId: String? = null,
    val smartPlanPhaseId: String? = null,
    val smartPlanType: String? = null,
    val smartPlanDate: String? = null,
    val qcfLineKeys: List<String> = emptyList(),
    val inputMethod: String = "pages",
    val clientId: String? = null,
    val startSurah: Int? = null,
    val startAyah: Int? = null,
    val endSurah: Int? = null,
    val endAyah: Int? = null,
    val ayahsCount: Int = 0,
    val surahsCount: Int = 0,
    val selectedSurahs: List<Int> = emptyList(),
    val selectedJuz: List<Int> = emptyList(),
    val traversalDirection: String? = null,
    val quranSegments: List<String> = emptyList(),
    val tilawahAssessment: JSONObject? = null,
    /** Pages where the mushaf marks went over the circle's per-page limit; the server grades the entry "repeat" (V7). */
    val overLimitPages: List<Int> = emptyList(),
    val createdAt: String? = null,
)

data class RecitationRow(
    val id: String,
    val sessionId: String,
    val studentId: String,
    val activityType: String,
    val startPage: Int? = null,
    val endPage: Int? = null,
    val startSurah: Int? = null,
    val startAyah: Int? = null,
    val endSurah: Int? = null,
    val endAyah: Int? = null,
    val pages: Double = 0.0,
    val ayahsCount: Int = 0,
    val surahsCount: Int = 0,
    val mistakes: Int = 0,
    val prompts: Int = 0,
    val evaluation: String = "not_rated",
    val safetyPercentage: Double? = null,
    val points: Double? = null,
    val teacherNote: String = "",
    val inputMethod: String = "pages",
    val qcfLineKeys: List<String> = emptyList(),
    val selectedSurahs: List<Int> = emptyList(),
    val selectedJuz: List<Int> = emptyList(),
    val traversalDirection: String? = null,
    val smartPlanId: String? = null,
    val smartPlanPhaseId: String? = null,
    val smartPlanType: String? = null,
    val smartPlanDate: String? = null,
    val tilawahAssessment: JSONObject? = null,
    val overLimitPages: List<Int> = emptyList(),
    val createdAt: String = "",
) {
    companion object {
        private fun jsonStrings(array: org.json.JSONArray?): List<String> = if (array == null) emptyList() else
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        private fun jsonInts(array: org.json.JSONArray?): List<Int> = if (array == null) emptyList() else
            (0 until array.length()).mapNotNull { array.optInt(it).takeIf { v -> v > 0 } }

        fun from(j: JSONObject): RecitationRow {
            val selection = j.optJSONObject("selection_data") ?: JSONObject()
            val lineKeys = jsonStrings(selection.optJSONArray("qcf_line_keys"))
            val storedPages = j.optDouble("pages_count", 0.0).coerceAtLeast(0.0)
            return RecitationRow(
                id = j.optString("id"),
                sessionId = j.optString("session_id"),
                studentId = j.optString("student_id"),
                activityType = j.optString("activity_type"),
                startPage = if (!j.has("start_page") || j.isNull("start_page")) null else j.optInt("start_page").takeIf { it in 1..604 },
                endPage = if (!j.has("end_page") || j.isNull("end_page")) null else j.optInt("end_page").takeIf { it in 1..604 },
                startSurah = if (!j.has("start_surah") || j.isNull("start_surah")) null else j.optInt("start_surah").takeIf { it in 1..114 },
                startAyah = if (!j.has("start_ayah") || j.isNull("start_ayah")) null else j.optInt("start_ayah").takeIf { it > 0 },
                endSurah = if (!j.has("end_surah") || j.isNull("end_surah")) null else j.optInt("end_surah").takeIf { it in 1..114 },
                endAyah = if (!j.has("end_ayah") || j.isNull("end_ayah")) null else j.optInt("end_ayah").takeIf { it > 0 },
                pages = RecitationParityRules.effectivePages(storedPages, lineKeys),
                ayahsCount = j.optInt("ayahs_count", 0).coerceAtLeast(0),
                surahsCount = j.optInt("surahs_count", 0).coerceAtLeast(0),
                mistakes = j.optInt("mistakes", 0).coerceAtLeast(0),
                prompts = j.optInt("prompts", 0).coerceAtLeast(0),
                evaluation = j.optString("evaluation", "not_rated"),
                safetyPercentage = if (!j.has("safety_percentage") || j.isNull("safety_percentage")) null else j.optDouble("safety_percentage"),
                points = if (!j.has("points") || j.isNull("points")) null else j.optDouble("points"),
                teacherNote = j.optString("teacher_note"),
                inputMethod = j.optString("input_method").ifBlank { selection.optString("input_method", "pages") },
                qcfLineKeys = lineKeys,
                selectedSurahs = if (j.has("selected_surahs")) jsonInts(j.optJSONArray("selected_surahs")) else jsonInts(selection.optJSONArray("selected_surahs")),
                selectedJuz = jsonInts(selection.optJSONArray("selected_juz")),
                traversalDirection = selection.optNullableString("quran_traversal_direction"),
                smartPlanId = selection.optNullableString("smart_plan_id"),
                smartPlanPhaseId = selection.optNullableString("smart_plan_phase_id") ?: j.optNullableString("phase_id"),
                smartPlanType = selection.optNullableString("smart_plan_type"),
                smartPlanDate = selection.optNullableString("smart_plan_date"),
                tilawahAssessment = selection.optJSONObject("tilawah_assessment"),
                overLimitPages = jsonInts(selection.optJSONArray("over_limit_pages")),
                createdAt = j.optString("created_at"),
            )
        }
    }
}

data class AttendanceRow(
    val id: String,
    val sessionId: String,
    val studentId: String,
    val status: String,
    val excuseReason: String = "",
    val arrivalMinutesLate: Int = 0,
) {
    companion object {
        fun from(j: JSONObject) = AttendanceRow(
            id = j.optString("id"),
            sessionId = j.optString("session_id"),
            studentId = j.optString("student_id"),
            status = j.optString("status", "present"),
            excuseReason = j.optString("excuse_reason"),
            arrivalMinutesLate = j.optInt("arrival_minutes_late", 0).coerceAtLeast(0),
        )
    }
}

data class DailyNoteRow(
    val id: String,
    val sessionId: String,
    val studentId: String,
    val behavior: String = "normal",
    val behaviorScore: Int? = null,
    val teacherComment: String = "",
    val homework: String = "",
    val parentVisible: Boolean = true,
    val participation: String = "",
) {
    companion object {
        fun from(j: JSONObject) = DailyNoteRow(
            id = j.optString("id"),
            sessionId = j.optString("session_id"),
            studentId = j.optString("student_id"),
            behavior = j.optString("behavior", "normal"),
            behaviorScore = if (!j.has("behavior_score") || j.isNull("behavior_score")) null else j.optInt("behavior_score").takeIf { it in 1..5 },
            teacherComment = j.optString("teacher_comment"),
            homework = j.optString("homework"),
            parentVisible = j.optBoolean("parent_visible", true),
            participation = j.optString("participation"),
        )
    }
}

data class PlanSettingsRow(
    val minimumDailyMode: String = "dynamic",
    val minimumDailyPages: Double = 0.0,
    val pauseRanges: List<Pair<String, String>> = emptyList(),
    val intensiveWeekFrom: String? = null,
    val intensiveWeekTo: String? = null,
    val intensiveWeekdays: List<Int> = listOf(0, 1, 2, 3, 4, 5),
    val memorizationLineKeys: List<String> = emptyList(),
    val routeDirection: String? = null,
    val enabledActivities: List<String> = emptyList(),
    val sardTargetPages: Double = 0.0,
    val sardRouteDirection: String? = null,
    val monthlyHifzTargetPages: Double = 0.0,
)

data class SmartPlanDraft(
    val id: String? = null,
    val scopeType: String = "student",
    val halaqaId: String,
    val studentId: String? = null,
    val categoryId: String? = null,
    val parentPlanId: String? = null,
    val type: String = "hifz",
    val reviewTier: String = "none",
    val title: String,
    val targetPages: Double,
    val startDate: String,
    val endDate: String,
    val scheduleWeekdays: List<Int> = listOf(0, 1, 2, 3, 4),
    val catchUpWeekday: Int? = null,
    val maxDailyMultiplier: Double = 1.5,
    val autoExtend: Boolean = true,
    val qualityGateEnabled: Boolean = true,
    val minimumEvaluation: String = "good",
    val rangeStartPage: Int? = null,
    val rangeEndPage: Int? = null,
    val rangeStartSurah: Int? = null,
    val rangeStartAyah: Int? = null,
    val rangeEndSurah: Int? = null,
    val rangeEndAyah: Int? = null,
    val nearRevisionPages: Double = 7.0,
    val farRevisionPages: Double = 20.0,
    val milestoneMode: String = "juz",
    val milestoneRequiresApproval: Boolean = true,
    val delayAlertDays: Int = 3,
    val homePrepPush: Boolean = true,
    val status: String = "active",
    val note: String = "",
    val minimumDailyMode: String = "dynamic",
    val minimumDailyPages: Double = 0.0,
    val intensiveWeekFrom: String? = null,
    val intensiveWeekTo: String? = null,
    val intensiveWeekdays: List<Int> = listOf(0, 1, 2, 3, 4, 5),
    val memorizationLineKeys: List<String> = emptyList(),
    val routeDirection: String? = null,
    val enabledActivities: List<String> = emptyList(),
    val sardTargetPages: Double = 0.0,
    val sardRouteDirection: String? = null,
)

data class PlanRow(
    val id: String,
    val halaqaId: String,
    val scopeType: String,
    val studentId: String?,
    val categoryId: String?,
    val parentPlanId: String?,
    val type: String,
    val reviewTier: String,
    val target: Double,
    val status: String,
    val startDate: String,
    val endDate: String,
    val title: String,
    val rangeStartPage: Int? = null,
    val rangeEndPage: Int? = null,
    val rangeStartSurah: Int? = null,
    val rangeStartAyah: Int? = null,
    val rangeEndSurah: Int? = null,
    val rangeEndAyah: Int? = null,
    val scheduleWeekdays: List<Int> = emptyList(),
    val catchUpWeekday: Int? = null,
    val maxDailyMultiplier: Double = 1.5,
    val autoExtend: Boolean = true,
    val qualityGateEnabled: Boolean = true,
    val minimumEvaluation: String = "very_good",
    val nearRevisionPages: Double = 7.0,
    val farRevisionPages: Double = 20.0,
    val milestoneMode: String = "juz",
    val milestoneRequiresApproval: Boolean = true,
    val delayAlertDays: Int = 3,
    val homePrepPush: Boolean = true,
    val note: String = "",
    val createdAt: String = "",
    val updatedAt: String = "",
    val settings: PlanSettingsRow = PlanSettingsRow(),
) {
    companion object {
        fun from(j: JSONObject): PlanRow {
            val schedule = j.optJSONArray("schedule_weekdays")?.let { array ->
                (0 until array.length()).map { array.optInt(it) }.filter { it in 0..6 }
            } ?: emptyList()
            val settingsJson = j.optJSONObject("settings") ?: JSONObject()
            val intensiveDays = settingsJson.optJSONArray("intensive_weekdays")?.let { array ->
                (0 until array.length()).map { array.optInt(it) }.filter { it in 0..6 }
            } ?: listOf(0, 1, 2, 3, 4, 5)
            val lineKeys = settingsJson.optJSONArray("memorization_line_keys")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()
            val enabledActivities = settingsJson.optJSONArray("enabled_activities")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()
            val pauseRanges = settingsJson.optJSONArray("pause_ranges")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    val from = item.optString("from").ifBlank { item.optString("start") }
                    val to = item.optString("to").ifBlank { item.optString("end") }
                    if (from.isBlank() || to.isBlank()) null else from to to
                }
            } ?: emptyList()
            fun nullableInt(name: String, min: Int, max: Int): Int? =
                if (j.isNull(name) || !j.has(name)) null else j.optInt(name).takeIf { it in min..max }

            return PlanRow(
                id = j.optString("id"),
                halaqaId = j.optString("halaqa_id"),
                scopeType = j.optString("scope_type", "student"),
                studentId = j.optNullableString("student_id"),
                categoryId = j.optNullableString("category_id"),
                parentPlanId = j.optNullableString("parent_plan_id"),
                type = j.optString("plan_type"),
                reviewTier = j.optString("review_tier", "none"),
                target = j.optDouble("target_pages", 0.0),
                status = j.optString("status"),
                startDate = j.optString("start_date"),
                endDate = j.optString("end_date"),
                title = j.optString("title"),
                rangeStartPage = nullableInt("range_start_page", 1, 604),
                rangeEndPage = nullableInt("range_end_page", 1, 604),
                rangeStartSurah = nullableInt("range_start_surah", 1, 114),
                rangeStartAyah = nullableInt("range_start_ayah", 1, 1000),
                rangeEndSurah = nullableInt("range_end_surah", 1, 114),
                rangeEndAyah = nullableInt("range_end_ayah", 1, 1000),
                scheduleWeekdays = schedule,
                catchUpWeekday = nullableInt("catch_up_weekday", 0, 6),
                maxDailyMultiplier = j.optDouble("max_daily_multiplier", 1.5),
                autoExtend = false,
                qualityGateEnabled = j.optBoolean("quality_gate_enabled", true),
                minimumEvaluation = j.optString("minimum_evaluation", "very_good"),
                nearRevisionPages = j.optDouble("near_revision_pages", 7.0),
                farRevisionPages = j.optDouble("far_revision_pages", 20.0),
                milestoneMode = j.optString("milestone_mode", "juz"),
                milestoneRequiresApproval = j.optBoolean("milestone_requires_approval", true),
                delayAlertDays = j.optInt("delay_alert_days", 3).coerceIn(1, 30),
                homePrepPush = j.optBoolean("home_prep_push", true),
                note = j.optString("note"),
                createdAt = j.optString("created_at"),
                updatedAt = j.optString("updated_at"),
                settings = PlanSettingsRow(
                    minimumDailyMode = settingsJson.optString("minimum_daily_mode", "dynamic"),
                    minimumDailyPages = settingsJson.optDouble("minimum_daily_pages", 0.0),
                    pauseRanges = pauseRanges,
                    intensiveWeekFrom = settingsJson.optString("intensive_week_from").ifBlank { null },
                    intensiveWeekTo = settingsJson.optString("intensive_week_to").ifBlank { null },
                    intensiveWeekdays = intensiveDays,
                    memorizationLineKeys = lineKeys,
                    routeDirection = settingsJson.optNullableString("route_direction"),
                    enabledActivities = enabledActivities,
                    sardTargetPages = settingsJson.optDouble("sard_target_pages", settingsJson.optDouble("rc19_sard_target_pages", 0.0)),
                    sardRouteDirection = settingsJson.optNullableString("sard_route_direction"),
                    monthlyHifzTargetPages = settingsJson.optDouble("monthly_hifz_target_pages", 0.0),
                ),
            )
        }
    }
}



/** RC11 — one editable phase inside the parent monthly smart plan. */
data class SmartPlanPhaseRow(
    val id: String,
    val planId: String,
    val phaseOrder: Int,
    val phaseType: String,
    val title: String,
    val startDate: String,
    val endDate: String,
    val scheduleWeekdays: List<Int>,
    val goalMode: String,
    val goalUnit: String,
    val targetUnits: Double?,
    val targetPages: Double?,
    val distributionMode: String,
    val startMode: String,
    val rangeStartPage: Int? = null,
    val rangeEndPage: Int? = null,
    val rangeStartSurah: Int? = null,
    val rangeStartAyah: Int? = null,
    val rangeEndSurah: Int? = null,
    val rangeEndAyah: Int? = null,
    val allowSurplus: Boolean = true,
    val status: String = "active",
    val completedAt: String? = null,
    val settings: JSONObject = JSONObject(),
    val revision: Int = 1,
    val updatedAt: String = "",
) {
    companion object {
        fun from(j: JSONObject): SmartPlanPhaseRow {
            fun ni(name: String, min: Int, max: Int): Int? =
                if (!j.has(name) || j.isNull(name)) null else j.optInt(name).takeIf { it in min..max }
            val weekdays = j.optJSONArray("schedule_weekdays")?.let { a ->
                (0 until a.length()).map { a.optInt(it) }.filter { it in 0..6 }
            } ?: emptyList()
            return SmartPlanPhaseRow(
                id = j.optString("id"),
                planId = j.optString("plan_id"),
                phaseOrder = j.optInt("phase_order", 1).coerceAtLeast(1),
                phaseType = j.optString("phase_type", "hifz"),
                title = j.optString("title"),
                startDate = j.optString("start_date"),
                endDate = j.optString("end_date"),
                scheduleWeekdays = weekdays,
                goalMode = j.optString("goal_mode", "pages"),
                goalUnit = j.optString("goal_unit", "pages"),
                targetUnits = if (!j.has("target_units") || j.isNull("target_units")) null else j.optDouble("target_units"),
                targetPages = if (!j.has("target_pages") || j.isNull("target_pages")) null else j.optDouble("target_pages"),
                distributionMode = j.optString("distribution_mode", "flexible"),
                startMode = j.optString("start_mode", "plan_start"),
                rangeStartPage = ni("range_start_page", 1, 604),
                rangeEndPage = ni("range_end_page", 1, 604),
                rangeStartSurah = ni("range_start_surah", 1, 114),
                rangeStartAyah = ni("range_start_ayah", 1, 1000),
                rangeEndSurah = ni("range_end_surah", 1, 114),
                rangeEndAyah = ni("range_end_ayah", 1, 1000),
                allowSurplus = j.optBoolean("allow_surplus", true),
                status = j.optString("status", "active"),
                completedAt = j.optString("completed_at").ifBlank { null },
                settings = j.optJSONObject("settings") ?: JSONObject(),
                revision = j.optInt("revision", 1).coerceAtLeast(1),
                updatedAt = j.optString("updated_at"),
            )
        }
    }
}

data class SmartPlanPhaseStateRow(
    val id: String,
    val phaseId: String,
    val studentId: String,
    val status: String,
    val targetPagesOverride: Double? = null,
    val startDateOverride: String? = null,
    val endDateOverride: String? = null,
    val frozenCompletedPages: Double? = null,
    val frozenTargetPages: Double? = null,
    val completionPercent: Double? = null,
    val carryShortfallPages: Double = 0.0,
    val completedAt: String? = null,
    val settings: JSONObject = JSONObject(),
) {
    companion object {
        fun from(j: JSONObject) = SmartPlanPhaseStateRow(
            id = j.optString("id"),
            phaseId = j.optString("phase_id"),
            studentId = j.optString("student_id"),
            status = j.optString("status", "not_started"),
            targetPagesOverride = if (!j.has("target_pages_override") || j.isNull("target_pages_override")) null else j.optDouble("target_pages_override"),
            startDateOverride = j.optString("start_date_override").ifBlank { null },
            endDateOverride = j.optString("end_date_override").ifBlank { null },
            frozenCompletedPages = if (!j.has("frozen_completed_pages") || j.isNull("frozen_completed_pages")) null else j.optDouble("frozen_completed_pages"),
            frozenTargetPages = if (!j.has("frozen_target_pages") || j.isNull("frozen_target_pages")) null else j.optDouble("frozen_target_pages"),
            completionPercent = if (!j.has("completion_percent") || j.isNull("completion_percent")) null else j.optDouble("completion_percent"),
            carryShortfallPages = j.optDouble("carry_shortfall_pages", 0.0).coerceAtLeast(0.0),
            completedAt = j.optString("completed_at").ifBlank { null },
            settings = j.optJSONObject("settings") ?: JSONObject(),
        )
    }
}

data class SmartPlanPhaseDraft(
    val id: String? = null,
    val planId: String,
    val phaseOrder: Int,
    val phaseType: String,
    val title: String,
    val startDate: String,
    val endDate: String,
    val scheduleWeekdays: List<Int> = listOf(0,1,2,3,4),
    val goalMode: String = "pages",
    val goalUnit: String = "pages",
    val targetUnits: Double? = null,
    val targetPages: Double? = null,
    val distributionMode: String = "flexible",
    val startMode: String = "plan_start",
    val rangeStartPage: Int? = null,
    val rangeEndPage: Int? = null,
    val rangeStartSurah: Int? = null,
    val rangeStartAyah: Int? = null,
    val rangeEndSurah: Int? = null,
    val rangeEndAyah: Int? = null,
    val allowSurplus: Boolean = true,
    val status: String = "active",
    val settings: JSONObject = JSONObject(),
    val expectedRevision: Int? = null,
)

data class SmartPlanPhaseTemplateRow(
    val id: String,
    val halaqaId: String,
    val name: String,
    val templateMode: String,
    val definition: JSONObject,
    val active: Boolean = true,
) {
    companion object {
        fun from(j: JSONObject) = SmartPlanPhaseTemplateRow(
            id = j.optString("id"),
            halaqaId = j.optString("halaqa_id"),
            name = j.optString("name"),
            templateMode = j.optString("template_mode", "weeks"),
            definition = j.optJSONObject("definition") ?: JSONObject(),
            active = j.optBoolean("active", true),
        )
    }
}
