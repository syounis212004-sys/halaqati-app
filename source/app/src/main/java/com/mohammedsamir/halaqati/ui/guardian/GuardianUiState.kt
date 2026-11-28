package com.mohammedsamir.halaqati.ui.guardian

import androidx.compose.runtime.Immutable
import com.mohammedsamir.halaqati.quran.RecitationParityRules
import org.json.JSONObject

/** RC17.1: typed guardian models. JSON is parsed once in the ViewModel, never inside Composables. */
@Immutable data class GuardianChild(val id: String, val name: String, val tathbit: Boolean)

@Immutable data class GuardianRecitationLine(
    val date: String,
    val activityLabel: String,
    val range: String,
    val pages: Double,
    val quality: Double?,
)

/** One explicit component of the monthly plan. Hifz and Sard are never merged into one ambiguous number. */
@Immutable data class GuardianPlanActivity(
    val key: String,
    val label: String,
    val target: Double,
    val done: Double,
) {
    val hasTarget: Boolean get() = target > 0.0001
    val percent: Double get() = if (hasTarget) (done * 100.0 / target).coerceIn(0.0, 100.0) else 0.0
    val remaining: Double get() = if (hasTarget) (target - done).coerceAtLeast(0.0) else 0.0
    val surplus: Double get() = if (hasTarget) (done - target).coerceAtLeast(0.0) else 0.0
    val complete: Boolean get() = hasTarget && done + 0.0001 >= target
}

/** RC52: figures of the latest recitation day (today when he recited today). */
@Immutable data class GuardianLastDay(
    val date: String,
    val isToday: Boolean,
    val mistakes: Int,
    val prompts: Int,
    val quality: Double?,
)

@Immutable data class GuardianEducationAssessment(
    val sessionId: String, val date: String, val title: String, val halaqaName: String,
    val scores: Map<String, Int>,
)

@Immutable data class GuardianOverview(
    val childName: String,
    val tathbit: Boolean,
    val trackLabel: String,
    val subtitle: String,
    val todayAttendance: String?,
    val todaySessionExists: Boolean?,
    val todaySessionStarted: Boolean?,
    val todayRecited: Boolean,
    val todayPages: Double,
    val todayRange: String?,
    val planTitle: String?,
    val planActivities: List<GuardianPlanActivity>,
    val monthMain: Double,
    val monthMainLabel: String,
    val monthPresent: Int,
    val monthSessions: Int,
    val quality: Double?,
    val latest: List<GuardianRecitationLine>,
    val updatedAt: String?,
    val todayWard: String? = null,
    val lastDay: GuardianLastDay? = null,
    val childPhoto: String? = null,
    val program: Boolean = false,
    val education: List<GuardianEducationAssessment> = emptyList(),
) {
    val planComplete: Boolean get() = planActivities.isNotEmpty() && planActivities.filter { it.hasTarget }.let { it.isNotEmpty() && it.all(GuardianPlanActivity::complete) }

    companion object {
        fun activityLabel(raw: String, tathbit: Boolean): String = if (tathbit && raw in setOf("review", "review_near", "review_far")) "تثبيت" else when (raw) {
            "new_hifz", "hifz" -> "حفظ"; "review_near", "review_far", "review" -> "مراجعة"
            "sard" -> "سرد"; "tilawah" -> "تلاوة"; "tathbit" -> "تثبيت"; "test" -> "اختبار"; else -> "تسميع"
        }

        private fun JSONObject.intOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key).takeIf { it > 0 }
        private fun JSONObject.doubleOrNull(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
        private fun JSONObject.text(key: String): String? = if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() && it != "null" }

        fun range(o: JSONObject): String? {
            val ss = o.intOrNull("start_surah") ?: return null
            return RecitationParityRules.quranRangeLabel(ss, o.intOrNull("start_ayah"), o.intOrNull("end_surah"), o.intOrNull("end_ayah"), null, null)
        }

        private fun parsePlanActivities(plan: JSONObject?, month: JSONObject, tathbit: Boolean): List<GuardianPlanActivity> {
            if (plan == null) return emptyList()
            val array = plan.optJSONArray("activities")
            val explicit = (0 until (array?.length() ?: 0)).mapNotNull { index ->
                val row = array?.optJSONObject(index) ?: return@mapNotNull null
                val key = row.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val label = row.optString("label").takeIf { it.isNotBlank() } ?: activityLabel(key, tathbit)
                GuardianPlanActivity(
                    key = key,
                    label = label,
                    target = row.optDouble("target_pages", 0.0).coerceAtLeast(0.0),
                    done = row.optDouble("done_pages", 0.0).coerceAtLeast(0.0),
                )
            }
            if (explicit.isNotEmpty()) return explicit.distinctBy { it.key }

            // Backward-compatible fallback for an RC17 server whose RPC still returned one combined
            // done_pages value. The client deliberately ignores that combined value and separates
            // Hifz/Sard using the month facts, eliminating the 37 + 6.93 = 44 ambiguity immediately.
            if (tathbit) {
                return listOf(
                    GuardianPlanActivity(
                        key = "tathbit",
                        label = "التثبيت",
                        target = plan.optDouble("target_pages", 0.0).coerceAtLeast(0.0),
                        done = month.optDouble("tathbit", 0.0).coerceAtLeast(0.0),
                    ),
                )
            }
            val hifz = GuardianPlanActivity(
                key = "hifz",
                label = "الحفظ",
                target = plan.optDouble("target_pages", 0.0).coerceAtLeast(0.0),
                done = month.optDouble("hifz", 0.0).coerceAtLeast(0.0),
            )
            val sardDone = month.optDouble("sard", 0.0).coerceAtLeast(0.0)
            val sardTarget = plan.optDouble("sard_target_pages", 0.0).coerceAtLeast(0.0)
            return buildList {
                add(hifz)
                // Show Sard whenever it exists as a configured target OR has actual achievement.
                if (sardTarget > 0.0001 || sardDone > 0.0001) add(GuardianPlanActivity("sard", "السرد", sardTarget, sardDone))
            }
        }

        fun parse(json: JSONObject): GuardianOverview? {
            val student = json.optJSONObject("student") ?: return null
            val program = com.mohammedsamir.halaqati.plans.WorkModeRules.isProgramType(student.text("halaqa_type"))
            val tathbit = !program && student.optString("track") == "tathbit"
            val today = json.optJSONObject("today") ?: JSONObject()
            val month = json.optJSONObject("month") ?: JSONObject()
            val plan = json.optJSONObject("plan").takeUnless { program }
            val todayRanges = today.optJSONArray("ranges")
            val todayRange = (0 until (todayRanges?.length() ?: 0)).mapNotNull { todayRanges?.optJSONObject(it)?.let { o -> range(o) } }
                .distinct().joinToString(" · ").ifBlank { null }
            val latestArr = json.optJSONArray("latest")
            val latest = (0 until (latestArr?.length() ?: 0)).mapNotNull { latestArr?.optJSONObject(it) }.map { r ->
                GuardianRecitationLine(
                    date = r.optString("date"),
                    activityLabel = activityLabel(r.optString("activity"), tathbit),
                    range = range(r) ?: "—",
                    pages = r.optDouble("pages", 0.0),
                    quality = r.doubleOrNull("quality"),
                )
            }
            val educationArray = json.optJSONArray("educational_assessments")
            val education = (0 until (educationArray?.length() ?: 0)).mapNotNull { educationArray?.optJSONObject(it) }
                .filter { it.optString("session_id").isNotBlank() }
                .groupBy { it.optString("session_id") }
                .mapNotNull { (sessionId, rows) ->
                    val session = rows.firstOrNull()?.optJSONObject("sessions") ?: return@mapNotNull null
                    val grades = rows.mapNotNull { row ->
                        val key = row.optString("metric")
                        val score = row.optInt("score")
                        if (com.mohammedsamir.halaqati.ui.sessions.EducationScoreRules.valid(key,score)) key to score else null
                    }.toMap()
                    if (grades.isEmpty()) null else GuardianEducationAssessment(sessionId,
                        session.optString("session_date"),session.optString("title").ifBlank { "نشاط تربوي" },
                        session.optJSONObject("halaqas")?.optString("name").orEmpty(), grades)
                }.sortedByDescending { it.date }
            return GuardianOverview(
                childName = student.text("full_name") ?: "طالب",
                education = education,
                childPhoto = student.text("photo_path"),
                tathbit = tathbit,
                trackLabel = if (program) "" else if (tathbit) "التثبيت" else "الحفظ العام",
                program = program,
                subtitle = listOfNotNull(student.text("halaqa_name"), student.text("category_name")).joinToString(" · "),
                todayAttendance = today.text("attendance"),
                todaySessionExists = if (today.has("session_exists") && !today.isNull("session_exists")) today.optBoolean("session_exists") else null,
                todaySessionStarted = if (today.has("session_started") && !today.isNull("session_started")) today.optBoolean("session_started") else null,
                todayRecited = today.optBoolean("recited", false),
                todayPages = today.optDouble("pages", 0.0),
                todayRange = todayRange,
                planTitle = plan?.let { if (tathbit) "خطة التثبيت لهذا الشهر" else "خطة هذا الشهر" },
                planActivities = if (program) emptyList() else parsePlanActivities(plan, month, tathbit),
                monthMain = if (tathbit) month.optDouble("tathbit", 0.0) else month.optDouble("hifz", 0.0),
                monthMainLabel = if (tathbit) "التثبيت" else "الحفظ",
                monthPresent = month.optInt("present", 0),
                monthSessions = month.optInt("sessions", 0),
                quality = month.doubleOrNull("quality"),
                latest = latest,
                updatedAt = json.text("updated_at"),
                todayWard = json.optJSONObject("ward")?.text("text"),
                lastDay = json.optJSONObject("last_day")?.let { d ->
                    GuardianLastDay(
                        date = d.optString("date"),
                        isToday = d.optBoolean("is_today", false),
                        mistakes = d.optInt("mistakes", 0),
                        prompts = d.optInt("prompts", 0),
                        quality = d.doubleOrNull("quality"),
                    )
                },
            )
        }
    }
}

@Immutable data class GuardianHomeUiState(
    val children: List<GuardianChild> = emptyList(),
    val selectedId: String? = null,
    val overview: GuardianOverview? = null,
    val loadingFirst: Boolean = true,
    val refreshing: Boolean = false,
    val stale: Boolean = false,
    val message: String? = null,
)
