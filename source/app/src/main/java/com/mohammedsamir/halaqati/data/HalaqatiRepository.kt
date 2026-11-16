package com.mohammedsamir.halaqati.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.mohammedsamir.halaqati.BuildConfig
import com.mohammedsamir.halaqati.model.*
import com.mohammedsamir.halaqati.plans.PlanRangeRebaseRules
import com.mohammedsamir.halaqati.plans.SmartPlanEngine
import com.mohammedsamir.halaqati.plans.SmartPlanHealthRules
import com.mohammedsamir.halaqati.plans.SmartPlanCoreStateEngine
import com.mohammedsamir.halaqati.plans.SmartPlanActivityRules
import com.mohammedsamir.halaqati.plans.SmartPlanPhaseEngine
import com.mohammedsamir.halaqati.plans.SardAnchorResolver
import com.mohammedsamir.halaqati.quran.QuranCore
import com.mohammedsamir.halaqati.quran.QuranLocation
import com.mohammedsamir.halaqati.quran.QuranMetadata
import com.mohammedsamir.halaqati.quran.QuranRangeRequest
import com.mohammedsamir.halaqati.quran.QuranSelection
import com.mohammedsamir.halaqati.quran.QuranTraversalDirection
import com.mohammedsamir.halaqati.ui.sessions.SessionWorkflowRules
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.min

data class PlanWeekRequirement(
    val amount: Double,
    val startPage: Int?,
    val endPage: Int?,
)

data class PlanNextDuty(
    val date: LocalDate,
    val assignment: SmartPlanEngine.Assignment,
)

data class PlanActivitySnapshot(
    val key: String,
    val label: String,
    val scheduledToday: Boolean = false,
    val lastRecitation: SmartPlanEngine.Recitation? = null,
    val assignment: SmartPlanEngine.Assignment? = null,
    val nextDuty: PlanNextDuty? = null,
    val needsStart: Boolean = false,
    val targetPages: Double = 0.0,
    val routeDirection: String = "forward_surahs_forward_ayahs",
    /** RC27.9: on a day the plan does not schedule, the duty the student could recite anyway (from where he stopped). */
    val extraAssignment: SmartPlanEngine.Assignment? = null,
)

data class PlanSnapshot(
    val plan: PlanRow,
    val student: Student,
    val progress: SmartPlanEngine.Progress,
    val phase: SmartPlanPhaseRow? = null,
    val phaseState: SmartPlanPhaseStateRow? = null,
    val phaseProgress: SmartPlanPhaseEngine.Progress? = null,
    val lastRecitation: SmartPlanEngine.Recitation? = null,
    val weekRequirement: PlanWeekRequirement? = null,
    val nextDuty: PlanNextDuty? = null,
    val activities: List<PlanActivitySnapshot> = emptyList(),
    val coreState: SmartPlanCoreStateEngine.State,
)

data class PhaseTransitionRequest(
    val studentId: String,
    val completedPages: Double,
    val targetPages: Double?,
    val carryShortfallPages: Double = 0.0,
    val sardTargetPagesOverride: Double? = null,
)

/** RC11.3 one-row description for the halaqa-wide Hifz -> Sard cycle controller. */
data class BulkPlanCycleRequest(
    val planId: String,
    val studentId: String,
    val hifzCompletedPages: Double,
    val hifzTargetPages: Double?,
    val hifzStartDate: String,
    val hifzEndDate: String,
    val sardStartDate: String,
    val sardEndDate: String,
    val sardTargetPages: Double = SmartPlanPhaseEngine.DEFAULT_SARD_TARGET_PAGES,
    val sardRouteDirection: String = SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH,
    val sardWeekdays: List<Int> = listOf(0, 1, 2, 3, 4, 5),
    val carryShortfallPages: Double = 0.0,
)

private const val CACHE_STUDENTS = "students"
private const val CACHE_SESSIONS = "sessions"
private const val CACHE_PLANS = "plans"
private const val CACHE_DASHBOARD_RECITATIONS = "dashboard:recitations"
private const val CACHE_PLAN_RECITATIONS = "plans:recitations"
private const val CACHE_SESSION_RECITATIONS_PREFIX = "session:recitations:"
private const val CACHE_SESSION_ATTENDANCE_PREFIX = "session:attendance:"
private const val CACHE_SESSION_NOTES_PREFIX = "session:notes:"
private const val CACHE_PRIMARY_HALAQA = "meta:primary_halaqa"
private const val CACHE_PROFILE = "profile:self"
private const val CACHE_PLAN_EVENTS = "plans:events"
private const val CACHE_PLAN_PHASES = "plans:phases"
private const val CACHE_PLAN_PHASE_STATES = "plans:phase-states"
private const val CACHE_PLAN_PHASE_TEMPLATES = "plans:phase-templates"
private const val CACHE_RANKING_PREFIX = "ranking:"
private const val CACHE_RANKING_SOURCE = "ranking:source"
private const val CACHE_RANKING_SOURCE_FROM = "ranking:source:from"
private const val CACHE_FULL_REFRESH_AT = "full-refresh:at"
private const val FULL_REFRESH_MIN_GAP_MS = 30 * 60_000L
private const val RANKING_CACHE_DAYS = 62L
private const val CACHE_GUARDIAN_CHILDREN = "guardian:children"
private const val CACHE_GUARDIAN_DASHBOARD_PREFIX = "guardian:dashboard:"
private const val CACHE_GUARDIAN_OVERVIEW_PREFIX = "guardian:overview:"
private const val CACHE_GUARDIAN_COMPETITION_PREFIX = "guardian:competition:"
private const val CACHE_REPORT_RECITATIONS_PREFIX = "reports:recitations:"
private const val CACHE_REPORT_SUMMARY_PREFIX = "reports:summary:"
/** RC27.18: tables whose rows are only meaningful together with their session. */
private const val STUDENT_COLUMNS_NO_PHOTO = "id,full_name,user_id,halaqa_id,primary_teacher_id,date_of_birth,phone,guardian_phone,enrollment_date,status,notes,target_pages_weekly,target_review_pages_weekly,created_at,updated_at,guardian_email,gender,national_id,program_name,school_grade,address,category_id,track_code,withdrawn_at,withdrawn_by,donor_contact_allowed,association_id"
private val SESSION_DERIVED_TABLES = setOf("recitation_entries", "attendance_records", "student_daily_notes", "student_points", "sessions")
private const val CACHE_ACCOUNTS = "accounts:profiles"
private const val CACHE_GUARDIAN_ACCOUNTS = "accounts:guardians"
private const val CACHE_CERTIFICATES = "certificates:core"
private const val CACHE_CERTIFICATE_TEMPLATES = "certificates:templates"
private const val CACHE_CERTIFICATE_SERIES = "certificates:series"
private const val CACHE_CERTIFICATE_REVISIONS_PREFIX = "certificates:revisions:"

class HalaqatiRepository(
    private val context: Context,
    val api: SupabaseApi,
    val store: SecureSessionStore,
    val queue: OfflineQueue,
    val cache: LocalCache,
    private val offlineDb: HalaqatiOfflineDatabase,
) {
    @Volatile private var planSnapshotMemoryDate: LocalDate? = null
    @Volatile private var planSnapshotMemoryAt = 0L
    @Volatile private var planRecDeltaAt = 0L
    @Volatile private var rankingTailAt = 0L
    /** RC27.39: the in-memory plan result is reused for at most 60 s while online (always when offline). */
    private fun planMemoryValid(today: LocalDate) =
        planSnapshotMemoryDate == today && planSnapshotMemory.isNotEmpty() &&
            (!online() || System.currentTimeMillis() - planSnapshotMemoryAt < 60_000L)
    @Volatile private var planSnapshotMemory: List<PlanSnapshot> = emptyList()
    private val pushLock = Any()
    private val _localMutationEpoch = MutableStateFlow(0L)
    val localMutationEpoch = _localMutationEpoch.asStateFlow()

    private fun signalLocalMutation() {
        reportSummaryMemo.clear()
        _localMutationEpoch.value = _localMutationEpoch.value + 1L
    }

    /** RC27.10: the month summary is the heaviest read; computed once and shared by Students, profile and Reports for a minute. */
    private class SummaryMemo(val at: Long, val rows: JSONArray)
    private val reportSummaryMemo = java.util.concurrent.ConcurrentHashMap<String, SummaryMemo>()

    @Volatile private var lastScheduleSyncAt = 0L

    fun loggedIn() = store.load() != null

    fun signOut() {
        reportSummaryMemo.clear()
        // RC18 privacy: never keep role-specific in-memory plan snapshots across an account switch.
        planSnapshotMemoryDate = null
        planSnapshotMemory = emptyList()
        store.clear()
    }

    fun authCallbackType(url: String): String = api.callbackType(url)

    fun updatePassword(newPassword: String) = api.updatePassword(newPassword)

    /** Bumped when the signed-in person's own profile changed locally (e.g. a new name) so the shell re-reads it. */
    val profileRevision = kotlinx.coroutines.flow.MutableStateFlow(0)

    fun updateMyFullName(fullName: String) {
        val name = fullName.trim()
        require(name.length >= 3) { "الاسم الكامل قصير" }
        val userId = store.load()?.userId ?: error("لا توجد جلسة مستخدم")
        patch(
            "profiles",
            "id=eq.$userId",
            JSONObject().put("full_name", name),
            dedupe = "profile-name:$userId",
        )
        // RC62: the header and the greeting read the cached profile; update it right away instead of waiting for the next login.
        runCatching {
            val cachedJson = cache.get(userId, CACHE_PROFILE, maxAge = null)?.let { JSONObject(it) }
            if (cachedJson != null) cache.put(userId, CACHE_PROFILE, cachedJson.put("full_name", name).toString())
        }
        profileRevision.value = profileRevision.value + 1
    }

    /** RC90: anyone can set or remove their own photo (the server only lets a person change their own row). */
    fun updateMyAvatar(dataUrl: String?) {
        val userId = store.load()?.userId ?: error("لا توجد جلسة مستخدم")
        patchConfirmed("profiles", "id=eq.$userId", JSONObject().put("avatar_path", dataUrl ?: JSONObject.NULL))
        runCatching {
            val cachedJson = cache.get(userId, CACHE_PROFILE, maxAge = null)?.let { JSONObject(it) }
            if (cachedJson != null) cache.put(userId, CACHE_PROFILE, cachedJson.put("avatar_path", dataUrl ?: JSONObject.NULL).toString())
        }
        profileRevision.value = profileRevision.value + 1
    }

    fun online(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun login(email: String, password: String) = api.signIn(email, password)

    fun register(email: String, password: String, name: String, phone: String, role: String) =
        api.signUp(email, password, name, phone, role)

    fun googleOAuthUrl(): String = api.googleOAuthUrl()

    fun loginWithGoogleIdToken(idToken: String, rawNonce: String) = api.signInWithGoogleIdToken(idToken, rawNonce)

    fun acceptOAuthCallback(url: String): UserSession? = api.acceptOAuthCallback(url)

    fun requestPasswordReset(email: String) = api.requestPasswordReset(email)

    fun registerPushToken(token: String) {
        if (store.load()?.userId.isNullOrBlank() || token.isBlank()) return
        if (!online()) return
        api.function(
            "halaqati-notify",
            JSONObject()
                .put("action", "register_device")
                .put("push_token", token)
                .put("platform", "android")
                .put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
                .put("app_version", BuildConfig.VERSION_NAME),
        )
        store.savePushToken(token)
    }

    /**
     * Disable the current device on the notification service while the old user session is
     * still valid. The local token is cleared even when the network call fails; the ViewModel
     * also asks Firebase to rotate/delete the FCM token so a later account cannot inherit it.
     */
    fun unregisterPushDevice() {
        val token = store.loadPushToken() ?: return
        try {
            if (loggedIn() && online()) {
                api.function(
                    "halaqati-notify",
                    JSONObject()
                        .put("action", "unregister_device")
                        .put("push_token", token),
                )
            }
        } finally {
            store.clearPushToken()
        }
    }

    fun testPush(): JSONObject = JSONObject(
        api.function(
            "halaqati-notify",
            JSONObject().put("action", "test_push"),
        ),
    )

    /**
     * RC27.49: notification center. One call to the edge function creates one row per recipient;
     * [dryRun] uses the separate manual_preview action, which only counts recipients (no rows, no push; an old deployed function just errors). Nothing here polls: lists load on open/refresh.
     */
    fun sendBroadcast(
        scope: String,
        targetId: String?,
        audience: String,
        title: String,
        body: String,
        label: String,
        sourceKey: String,
        dryRun: Boolean = false,
    ): JSONObject {
        // RC53: donors / supervisors are reached by a SQL function (administrator only), not by the edge function.
        if (audience == "donors" || audience == "supervisors") {
            val raw = withAuthRetry {
                api.rpc(
                    "rc53_broadcast_special",
                    JSONObject().put("p_audience", audience).put("p_title", title).put("p_body", body)
                        .put("p_source_key", sourceKey).put("p_label", label).put("p_dry", dryRun),
                )
            }
            return runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("error", "رد غير متوقع من الخادم") }
        }
        val req = JSONObject()
            .put("action", if (dryRun) "manual_preview" else "manual")
            .put("scope", scope)
            .put("audience", audience)
            .put("title", title)
            .put("body", body)
            .put("source_key", sourceKey)
            .put("deep_link", "notifications")
            .put("data", JSONObject().put("label", label))
        if (!targetId.isNullOrBlank()) req.put("target_id", targetId)
        return JSONObject(withAuthRetry { api.function("halaqati-notify", req) })
    }

    // ---- RC53: staff monitoring, alerts, support details, freezing and photos (see RC53_server.sql) ----

    /** One-shot per-staff figures computed on the server. Returns null when the RC53 SQL is not applied or there is no connection. */
    fun staffOverview(from: String, to: String): List<JSONObject>? {
        if (!online()) return null
        return runCatching {
            val raw = withAuthRetry { api.rpc("rc53_staff_overview", JSONObject().put("p_from", from).put("p_to", to)) }
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        }.getOrNull()
    }

    /** RC90: the staff overview kept on the device. Online it is read again; offline (or when the call fails) the last copy is shown. */
    fun staffOverviewStored(from: String, to: String): List<JSONObject>? {
        val owner = currentUserId() ?: return null
        val key = "staff-overview"
        staffOverview(from, to)?.let { fresh ->
            cache.put(owner, key, JSONArray(fresh).toString())
            return fresh
        }
        val arr = cache.get(owner, key, null)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return null
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    /** A private alert from an administrator / supervisor to one teacher or supervisor. */
    fun alertStaff(recipientId: String, title: String, body: String, halaqaId: String?): JSONObject {
        requireConfirmedMutationConnection()
        val args = JSONObject().put("p_recipient", recipientId).put("p_title", title).put("p_body", body)
        if (!halaqaId.isNullOrBlank()) args.put("p_halaqa", halaqaId)
        val raw = withAuthRetry { api.rpc("rc53_alert_staff", args) }
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("ok", false).put("error", "رد غير متوقع من الخادم") }
    }

    /** Support details (phone, kind of destination). Null while the table does not exist yet. */
    fun supportInfo(): JSONObject? = runCatching { table("app_support_info", "select=*&limit=1").optJSONObject(0) }.getOrNull()

    fun saveSupportInfo(phone: String, entityType: String, entityName: String, note: String) {
        patchConfirmed(
            "app_support_info", "id=eq.1",
            JSONObject().put("phone", phone).put("entity_type", entityType).put("entity_name", entityName).put("note", note)
                .put("updated_at", java.time.Instant.now().toString()),
        )
    }

    fun setAccountFrozen(userId: String, frozen: Boolean, reason: String = "") {
        patchConfirmed(
            "profiles", "id=eq.$userId",
            JSONObject().put("frozen", frozen).put("frozen_reason", if (frozen && reason.isNotBlank()) reason else JSONObject.NULL),
        )
    }

    fun setAvatar(userId: String, dataUrl: String?) {
        patchConfirmed("profiles", "id=eq.$userId", JSONObject().put("avatar_path", dataUrl ?: JSONObject.NULL))
    }

    /** Aggregated per-broadcast counts, computed on the server (<= 30 small rows). */
    fun broadcastStats(): List<JSONObject> {
        val raw = withAuthRetry { api.rpc("rc_broadcast_stats", JSONObject().put("p_limit", 30)) }
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    /** Who read / did not read one broadcast. Requested only when the sender opens it. */
    fun broadcastDetail(sourceKey: String): JSONObject {
        val raw = withAuthRetry { api.rpc("rc_broadcast_detail", JSONObject().put("p_source_key", sourceKey)) }
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("ok", false).put("error", "رد غير متوقع من الخادم") }
    }

    fun sendStudentEvent(
        studentId: String,
        sourceKey: String,
        title: String,
        body: String,
        data: JSONObject = JSONObject(),
    ): JSONObject = JSONObject(
        api.function(
            "halaqati-notify",
            JSONObject()
                .put("action", "event_student")
                .put("kind", "system")
                .put("student_id", studentId)
                .put("source_key", sourceKey)
                .put("title", title)
                .put("body", body)
                .put("deep_link", "student:$studentId")
                .put("data", data),
        ),
    )

    private fun recordPlanHealthSnapshot(snapshot: PlanSnapshot, today: LocalDate): JSONObject {
        val weekday = SmartPlanEngine.weekday(today)
        // RC19 guardian follow-up is intentionally a Hifz-delay signal. Sard can still
        // show its own health in the UI, but it must never create a "two Hifz days"
        // warning after the monthly phase switches on day 23.
        val activePhase = snapshot.phase
        val hifzWindow = activePhase?.phaseType == "hifz" ||
            (activePhase == null && snapshot.plan.type == "hifz" && today.dayOfMonth <= 22)
        val effectiveWeekdays = activePhase?.scheduleWeekdays?.takeIf { it.isNotEmpty() } ?: snapshot.plan.scheduleWeekdays
        val scheduled = hifzWindow && weekday in effectiveWeekdays && weekday != snapshot.plan.catchUpWeekday
        val ratio = if (snapshot.progress.plannedByToday <= .001) 1.0 else snapshot.progress.completed / snapshot.progress.plannedByToday
        return JSONObject(withAuthRetry {
            api.rpc(
                "rc19_record_plan_health",
                JSONObject()
                    .put("p_plan_id", snapshot.plan.id)
                    .put("p_student_id", snapshot.student.id)
                    .put("p_day", today.toString())
                    .put("p_scheduled", scheduled)
                    .put("p_health", snapshot.progress.health)
                    .put("p_ratio", ratio)
                    .put("p_expected_pages", snapshot.progress.plannedByToday)
                    .put("p_completed_pages", snapshot.progress.completed)
                    .put("p_behind_pages", snapshot.progress.behindBy),
            )
        })
    }

    private fun markPlanHealthNotification(snapshot: PlanSnapshot, today: LocalDate) {
        withAuthRetry {
            api.rpc(
                "rc19_mark_plan_health_notified",
                JSONObject()
                    .put("p_plan_id", snapshot.plan.id)
                    .put("p_student_id", snapshot.student.id)
                    .put("p_day", today.toString()),
            )
        }
    }

    /**
     * Native equivalent of the legacy checkSmartPlanAlerts() loop.
     * Server source_key de-duplication makes the periodic worker safe to run more than once.
     */
    fun runSmartPlanAlerts(now: ZonedDateTime = ZonedDateTime.now(ZoneId.of("Asia/Gaza"))): Int {
        if (!loggedIn() || !online()) return 0
        val profile = profileWithRefresh()
        if (profile.role !in setOf(UserRole.ADMIN, UserRole.SUPERVISOR, UserRole.TEACHER)) return 0

        val today = now.toLocalDate()
        var delivered = 0
        val todaySnapshots = planSnapshots(today)
        // RC51: stop at the first authentication / rate-limit failure instead of firing one doomed request per student.
        var authStopped = false
        todaySnapshots.forEach { snapshot ->
            if (authStopped) return@forEach
            val progress = snapshot.progress
            runCatching { recordPlanHealthSnapshot(snapshot, today) }.onFailure { failure ->
                if (failure is SupabaseException && (failure.status == 401 || failure.status == 403 || failure.status == 429)) authStopped = true
            }.onSuccess { healthState ->
                if (healthState.optBoolean("should_notify", false)) {
                    runCatching {
                        val lagPercent = healthState.optDouble("lag_percent", 0.0)
                        sendStudentEvent(
                            studentId = snapshot.student.id,
                            sourceKey = "smart-plan-health:${snapshot.plan.id}:${snapshot.student.id}:${healthState.optString("episode_key", today.toString())}",
                            title = "يحتاج متابعة · ${snapshot.student.fullName}",
                            body = "استمر التأخر عن الخطة ليومي حفظ متتاليين. المنجز ${SmartPlanEngine.round(progress.completed)} ص، والمطلوب حتى اليوم ${SmartPlanEngine.round(progress.plannedByToday)} ص، والتأخر ${SmartPlanEngine.round(progress.behindBy)} ص${if (lagPercent > 0.0) " (${SmartPlanEngine.round(lagPercent, 1)}%)" else ""}.",
                            data = JSONObject()
                                .put("smart_plan_id", snapshot.plan.id)
                                .put("health", progress.health)
                                .put("red_streak", healthState.optInt("red_streak", 0))
                                .put("behind_pages", progress.behindBy),
                        )
                        markPlanHealthNotification(snapshot, today)
                    }.onSuccess { delivered++ }
                }
            }
        }

        // RC11 phase notifications. All source keys are deterministic; the server's
        // source_key de-duplication makes this safe across worker reruns and sync retries.
        val phaseRows = planPhases()
        val phaseById = phaseRows.associateBy { it.id }
        planPhaseStates().forEach { state ->
            val phase = phaseById[state.phaseId] ?: return@forEach
            val planId = phase.planId
            when {
                phase.phaseType == "hifz" && state.status == "completed" -> runCatching {
                    val completed = state.frozenCompletedPages ?: 0.0
                    val target = state.frozenTargetPages
                    val percent = state.completionPercent ?: target?.takeIf { it > .001 }?.let { SmartPlanEngine.round(completed / it * 100.0, 1) }
                    sendStudentEvent(
                        studentId = state.studentId,
                        sourceKey = "smart-plan-phase-completed:$planId:${phase.id}:${state.studentId}",
                        title = "تم إنهاء مرحلة الحفظ",
                        body = buildString {
                            append("أُغلقت مرحلة الحفظ بإنجاز ${SmartPlanEngine.round(completed)} صفحة")
                            target?.let { append(" من ${SmartPlanEngine.round(it)}") }
                            percent?.let { append(" بنسبة ${SmartPlanEngine.round(it, 1)}%") }
                            append(".")
                        },
                        data = JSONObject().put("smart_plan_id", planId).put("phase_id", phase.id).put("phase_type", "hifz"),
                    )
                }.onSuccess { delivered++ }

                phase.phaseType == "sard" && state.status == "active" &&
                    !today.isBefore(runCatching { LocalDate.parse(state.startDateOverride ?: phase.startDate) }.getOrDefault(today.plusDays(1))) -> runCatching {
                    val effectiveTarget = state.targetPagesOverride ?: phase.targetPages
                    val goalText = when (phase.goalMode) {
                        "juz" -> "الهدف: ${phase.targetUnits ?: ((effectiveTarget ?: 0.0) / 20.0)} جزء."
                        "open" -> "الهدف: سرد مفتوح خلال الفترة."
                        else -> effectiveTarget?.let { "الهدف: ${SmartPlanEngine.round(it)} صفحة." } ?: ""
                    }
                    sendStudentEvent(
                        studentId = state.studentId,
                        sourceKey = "smart-plan-phase-started:$planId:${phase.id}:${state.studentId}",
                        title = "بدأت مرحلة السرد",
                        body = "مرحلة السرد من ${phase.startDate} إلى ${phase.endDate}. $goalText",
                        data = JSONObject().put("smart_plan_id", planId).put("phase_id", phase.id).put("phase_type", "sard"),
                    )
                }.onSuccess { delivered++ }

                phase.phaseType == "sard" && state.status == "completed" -> runCatching {
                    val percent = state.completionPercent
                    sendStudentEvent(
                        studentId = state.studentId,
                        sourceKey = "smart-plan-phase-completed:$planId:${phase.id}:${state.studentId}",
                        title = "اكتملت مرحلة السرد",
                        body = if (percent != null) "أُغلقت مرحلة السرد بنسبة ${SmartPlanEngine.round(percent, 1)}%." else "أُغلقت مرحلة السرد بنجاح.",
                        data = JSONObject().put("smart_plan_id", planId).put("phase_id", phase.id).put("phase_type", "sard"),
                    )
                }.onSuccess { delivered++ }
            }
        }
        todaySnapshots.forEach { snapshot ->
            val phase = snapshot.phase ?: return@forEach
            val last = snapshot.lastRecitation ?: return@forEach
            if (phase.phaseType != "sard" || last.date != today) return@forEach
            runCatching {
                val total = snapshot.phaseProgress?.completedPages ?: snapshot.progress.completed
                sendStudentEvent(
                    studentId = snapshot.student.id,
                    sourceKey = "smart-plan-sard-daily:${phase.id}:${last.id}",
                    title = "إنجاز السرد اليوم · ${snapshot.student.fullName}",
                    body = "سرد اليوم ${SmartPlanEngine.round(last.pagesCount)} صفحة. إجمالي المرحلة ${SmartPlanEngine.round(total)} صفحة.",
                    data = JSONObject().put("smart_plan_id", snapshot.plan.id).put("phase_id", phase.id).put("recitation_id", last.id),
                )
            }.onSuccess { delivered++ }
        }

        if (now.hour >= 17) {
            val tomorrow = today.plusDays(1)
            planSnapshots(tomorrow)
                .groupBy { it.student.id }
                .values
                .forEach { candidates ->
                    val student = candidates.firstOrNull()?.student ?: return@forEach
                    val preferred = SmartPlanAlertRules.preferredPlanType(student.track)
                    val next = candidates.firstOrNull { it.plan.type == preferred } ?: candidates.firstOrNull() ?: return@forEach
                    // RC27.50: tomorrow's duty must come from the same anchor-based activity the session card shows
                    // (continues from the student's last recitation). The engine's raw page-order assignment is NOT
                    // used: once the month target is reached it points at an old page (e.g. surah already memorised).
                    val dutyKey = if (preferred == "tathbit") "tathbit" else "hifz"
                    val dutyActivity = next.activities.firstOrNull { it.key == dutyKey }
                    val assignment = dutyActivity?.assignment ?: dutyActivity?.extraAssignment ?: return@forEach
                    if (!SmartPlanAlertRules.shouldSendHomePrep(now.hour, next.plan.homePrepPush, assignment.startPage)) {
                        return@forEach
                    }
                    val exact = assignment.lineKeys.takeIf { it.isNotEmpty() }
                        ?.let { keys -> runCatching { QuranCore.engine().endpointsForLineKeys(keys) }.getOrNull() }
                    runCatching {
                        sendStudentEvent(
                            studentId = student.id,
                            sourceKey = "smart-plan-prep:${next.plan.id}:${student.id}:$tomorrow",
                            title = "تحضير الغد · ${student.fullName}",
                            body = "مطلوب غداً ${SmartPlanAlertRules.assignmentLabel(assignment.kind, assignment.startPage, assignment.endPage, exact?.first, exact?.second)}.",
                            data = JSONObject()
                                .put("smart_plan_id", next.plan.id)
                                .put("plan_date", tomorrow.toString())
                                .put("start_page", assignment.startPage)
                                .put("end_page", assignment.endPage),
                        )
                    }.onSuccess { delivered++ }
                }
        }
        return delivered
    }

    private fun profileFromJson(json: JSONObject, session: UserSession): Profile = Profile(
        id = json.optString("id").ifBlank { session.userId },
        fullName = json.optString("full_name").ifBlank { session.email },
        role = UserRole.from(json.optString("role")),
        approvalStatus = json.optString("approval_status", "pending"),
        email = json.optString("email", session.email),
        active = json.optBoolean("active", true),
        requestedRole = UserRole.from(json.optString("requested_role")),
        rejectionReason = json.optString("rejection_reason"),
        onboarded = json.optBoolean("onboarded", true),
        frozen = json.optBoolean("frozen", false),
        avatarPath = json.optString("avatar_path").takeIf { it != "null" }.orEmpty(),
    )

    /** RC27.42: reads the own profile; before the approval-hierarchy SQL is applied the `onboarded` column does not exist. */
    private fun selectOwnProfileRow(userId: String): JSONObject? {
        val base = "id,full_name,role,requested_role,approval_status,active,email,rejection_reason"
        return try {
            api.select("profiles", "select=$base,onboarded,frozen,avatar_path&id=eq.$userId&limit=1").optJSONObject(0)
        } catch (e: SupabaseException) {
            if (e.status != 400) throw e
            // RC53 columns missing (SQL not applied yet): fall back to the RC27.42 shape, then to the bare shape.
            try {
                api.select("profiles", "select=$base,onboarded&id=eq.$userId&limit=1").optJSONObject(0)
            } catch (e2: SupabaseException) {
                if (e2.status == 400) api.select("profiles", "select=$base&id=eq.$userId&limit=1").optJSONObject(0) else throw e2
            }
        }
    }

    // ---- RC27.42: multi-level account approval (see migration/20261020_v2642_rc27_42_approval_hierarchy.sql) ----

    private fun rpcObject(name: String, args: JSONObject): JSONObject {
        val raw = withAuthRetry { api.rpc(name, args) }
        return runCatching { JSONObject(raw) }.getOrElse { JSONObject().put("ok", false).put("error", "رد غير متوقع من الخادم") }
    }

    private fun friendlyRpcError(e: Exception): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("Could not find the function", true) || raw.contains("PGRST202", true) ->
                "لم يُطبَّق تحديث قاعدة البيانات الخاص بالاعتماد بعد. تواصل مع مسؤول النظام."
            else -> raw.ifBlank { "تعذر تنفيذ العملية" }
        }
    }

    /** Returns null on success, otherwise a message for the user. */
    fun completeSignup(name: String, phone: String, role: String, nationalId: String, birthDate: String): String? {
        require(online()) { "إكمال الحساب يحتاج اتصالًا بالإنترنت" }
        return try {
            val res = rpcObject(
                "rc_complete_signup",
                JSONObject().put("p_name", name).put("p_phone", phone).put("p_role", role)
                    .put("p_national_id", if (role == "guardian") nationalId else JSONObject.NULL)
                    .put("p_birth_date", if (role == "guardian") birthDate else JSONObject.NULL),
            )
            if (res.optBoolean("ok", false)) null else res.optString("error").ifBlank { "تعذر إكمال الحساب" }
        } catch (e: SupabaseException) { friendlyRpcError(e) }
    }

    /** An approved guardian asks to link another child. Returns null on success, otherwise a message. */
    fun requestGuardianLink(nationalId: String, birthDate: String): String? {
        require(online()) { "الطلب يحتاج اتصالًا بالإنترنت" }
        return try {
            val res = rpcObject("rc_request_guardian_link", JSONObject().put("p_national_id", nationalId).put("p_birth_date", birthDate))
            if (res.optBoolean("ok", false)) null else res.optString("error").ifBlank { "تعذر إرسال الطلب" }
        } catch (e: SupabaseException) { friendlyRpcError(e) }
    }

    /** What a waiting account sees: a short Arabic status line, or null when the server has no such function yet. */
    fun mySignupStatusText(): String? = runCatching {
        val raw = withAuthRetry { api.rpc("rc_my_signup_status", JSONObject()) }
        JSONObject(raw).optString("text").takeIf { it.isNotBlank() }
    }.getOrNull()

    fun pendingApprovalRequests(): List<ApprovalRequest> {
        val raw = withAuthRetry { api.rpc("rc_pending_requests", JSONObject()) }
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { o ->
            ApprovalRequest(
                kind = o.optString("kind"),
                id = o.optString("id"),
                name = o.optString("name"),
                email = o.optString("email").takeUnless { it == "null" }.orEmpty(),
                phone = o.optString("phone").takeUnless { it == "null" }.orEmpty(),
                role = o.optString("role"),
                student = o.optString("student").takeUnless { it == "null" }.orEmpty(),
                halaqa = o.optString("halaqa").takeUnless { it == "null" }.orEmpty(),
                stage = o.optString("stage"),
                teacherName = o.optString("teacher_name").takeUnless { it == "null" }.orEmpty(),
                createdAt = o.optString("created_at"),
            )
        }
    }

    /** Returns null on success, otherwise a message. */
    fun reviewApprovalRequest(kind: String, id: String, approve: Boolean, reason: String = ""): String? {
        require(online()) { "المراجعة تحتاج اتصالًا مباشرًا بالخادم" }
        return try {
            val res = rpcObject(
                "rc_review_request",
                JSONObject().put("p_kind", kind).put("p_id", id).put("p_action", if (approve) "approve" else "reject")
                    .put("p_reason", reason.trim().ifBlank { JSONObject.NULL }),
            )
            if (res.optBoolean("ok", false)) { invalidateAccountsCache(); pushApprovalEvents(); null } else res.optString("error").ifBlank { "تعذر تنفيذ العملية" }
        } catch (e: SupabaseException) { friendlyRpcError(e) }
    }

    /** RC27.46: best effort. Asks the notify function to push the approval notifications just created; never blocks the review. */
    private fun pushApprovalEvents() {
        runCatching { api.function("halaqati-notify", JSONObject().put("action", "push_approval_events")) }
    }

    private fun invalidateAccountsCache() { currentUserId()?.let { cache.invalidate(it, CACHE_ACCOUNTS) } }

    fun cachedProfile(): Profile? {
        val session = store.load() ?: return null
        val cached = cache.get(session.userId, CACHE_PROFILE, maxAge = null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        return profileFromJson(cached, session)
    }

    fun profile(): Profile {
        val session = store.load() ?: error("not signed in")
        val cached = cache.get(session.userId, CACHE_PROFILE, maxAge = null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (!online() && cached != null) return profileFromJson(cached, session)

        val json = runCatching {
            selectOwnProfileRow(session.userId)
        }.getOrElse { error ->
            cached ?: throw error
        } ?: cached ?: JSONObject()
            .put("id", session.userId)
            .put("full_name", session.email)
            .put("role", "guardian")
            .put("requested_role", "guardian")
            .put("approval_status", "pending")
            .put("active", true)

        cache.put(session.userId, CACHE_PROFILE, json.toString())
        return profileFromJson(json, session)
    }

    fun profileWithRefresh(): Profile = try {
        profile()
    } catch (e: SupabaseException) {
        if (e.status != 401) throw e
        api.refresh() ?: throw e
        profile()
    }

    /**
     * RC14 approval refresh: when a pending user explicitly asks for the latest approval state,
     * never hide a network/RLS failure behind the old cached `pending` row.  This fixes the case
     * where an administrator approved a guardian but the client kept showing "بانتظار الاعتماد"
     * until an unrelated profile edit invalidated the cache.
     */
    fun profileStrictRefresh(): Profile {
        val session = store.load() ?: error("not signed in")
        require(online()) { "تحديث حالة الاعتماد يحتاج اتصالًا بالخادم" }
        val json = withAuthRetry { selectOwnProfileRow(session.userId) } ?: error("لم يعثر الخادم على ملف الحساب")
        cache.put(session.userId, CACHE_PROFILE, json.toString())
        return profileFromJson(json, session)
    }

    private val readCacheFreshness: Duration = Duration.ofMinutes(5)

    fun students(forceRefresh: Boolean = false): List<Student> {
        val owner = currentUserId() ?: return emptyList()
        val local = offlineDb.students().all(owner).map { Student.from(it.asJson()) }
        if (forceRefresh && online()) return refreshStudents()
        if (local.isNotEmpty()) {
            if (online()) scheduleSync()
            return local
        }
        cachedStudents(owner, null)?.let { cached ->
            cached.forEach { student -> upsertStudentLocal(owner, studentToJson(student), dirty = false) }
            if (!online()) return cached
        }
        return if (online()) refreshStudents() else emptyList()
    }

    /**
     * RC18 authoritative guardian child list. Guardian screens must never derive children from
     * the broad students() master-data endpoint. The server RPC checks student_guardians against
     * auth.uid(); the cache is owner-scoped and is the only offline fallback.
     */
    fun guardianChildren(forceRefresh: Boolean = false): List<Student> {
        val owner = currentUserId() ?: return emptyList()
        val cached = cache.get(owner, CACHE_GUARDIAN_CHILDREN, null)
            ?.let { runCatching { studentsFromJson(it) }.getOrNull() }
        if (!forceRefresh && cached != null) {
            if (online()) scheduleSync()
            return cached
        }
        if (!online()) return cached.orEmpty()
        val fresh = runCatching {
            val arr = runCatching {
                JSONArray(withAuthRetry { api.rpc("guardian_children", JSONObject()) })
            }.getOrElse {
                // Upgrade-safe fallback before the RC18 migration is applied: query only rows
                // explicitly linked to auth.uid(), never the broad students endpoint.
                val links = withAuthRetry {
                    api.select("student_guardians", "select=student_id&guardian_id=eq.$owner&limit=100")
                }
                val ids = (0 until links.length()).mapNotNull { links.optJSONObject(it)?.optString("student_id") }
                    .filter(String::isNotBlank).distinct()
                if (ids.isEmpty()) JSONArray() else withAuthRetry {
                    api.select("students", "select=*&id=in.(${ids.joinToString(",")})&status=eq.active&order=full_name.asc&limit=100")
                }
            }
            cache.put(owner, CACHE_GUARDIAN_CHILDREN, arr.toString())
            studentsFromJson(arr.toString())
        }.getOrElse { cached.orEmpty() }
        return fresh.filter { it.status == "active" }.sortedBy { it.fullName }
    }

    private fun guardianOwnsCachedChild(studentId: String): Boolean =
        studentId.isNotBlank() && guardianChildren(forceRefresh = false).any { it.id == studentId }

    /** Lightweight category metadata used by RC13 certificate grouping/filtering. */
    fun studentCategories(forceRefresh: Boolean = false): JSONArray =
        table(
            "student_categories",
            "select=id,halaqa_id,name,active&active=eq.true&order=name.asc&limit=500",
            forceRefresh = forceRefresh,
        )

    /** RC21: a student keeps one primary halaqa and may participate in additional courses/programs. */
    fun studentHalaqaMemberships(
        studentId: String? = null,
        halaqaId: String? = null,
        forceRefresh: Boolean = false,
    ): JSONArray {
        val filters = buildList {
            studentId?.takeIf { it.isNotBlank() }?.let { add("student_id=eq.$it") }
            halaqaId?.takeIf { it.isNotBlank() }?.let { add("halaqa_id=eq.$it") }
        }
        val query = buildString {
            append("select=*&order=created_at.asc&limit=2000")
            filters.forEach { append('&').append(it) }
        }
        return table("student_halaqa_memberships", query, forceRefresh = forceRefresh)
    }

    fun activeSecondaryHalaqaIds(studentId: String, forceRefresh: Boolean = false): Set<String> {
        val rows = studentHalaqaMemberships(studentId = studentId, forceRefresh = forceRefresh)
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            .filter { it.optString("status", "active") == "active" }
            .mapNotNull { it.optString("halaqa_id").takeIf(String::isNotBlank) }
            .toSet()
    }

    fun studentsForHalaqa(halaqaId: String, forceRefresh: Boolean = false): List<Student> {
        if (halaqaId.isBlank()) return emptyList()
        val base = students(forceRefresh = forceRefresh)
        val memberships = studentHalaqaMemberships(halaqaId = halaqaId, forceRefresh = forceRefresh)
        val memberIds = (0 until memberships.length()).mapNotNull { memberships.optJSONObject(it) }
            .filter { it.optString("status", "active") == "active" }
            .mapNotNull { it.optString("student_id").takeIf(String::isNotBlank) }
            .toSet()
        val primary = base.filter { it.halaqaId == halaqaId }
        val fromBase = base.filter { it.id in memberIds }
        val missingIds = memberIds - base.mapTo(hashSetOf()) { it.id }
        val fetched = if (missingIds.isNotEmpty() && online()) {
            val arr = runCatching {
                withAuthRetry {
                    api.select(
                        "students",
                        "select=*&id=in.(${missingIds.joinToString(",")})&status=eq.active&order=full_name.asc&limit=1000",
                    )
                }
            }.getOrDefault(JSONArray())
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Student::from) }
        } else emptyList()
        return (primary + fromBase + fetched).distinctBy { it.id }.filter { it.status == "active" }.sortedBy { it.fullName }
    }

    fun setStudentHalaqaMembership(studentId: String, halaqaId: String, active: Boolean) {
        require(studentId.isNotBlank() && halaqaId.isNotBlank())
        val now = java.time.Instant.now().toString()
        val today = LocalDate.now().toString()
        if (active) {
            val payload = JSONObject()
                .put("student_id", studentId)
                .put("halaqa_id", halaqaId)
                .put("membership_type", "participant")
                .put("status", "active")
                .put("joined_at", today)
                .put("ended_at", JSONObject.NULL)
                .put("updated_at", now)
            if (online()) writeConfirmed("student_halaqa_memberships", payload, onConflict = "student_id,halaqa_id")
            else write("student_halaqa_memberships", payload, onConflict = "student_id,halaqa_id", dedupe = "student-membership:$studentId:$halaqaId")
        } else {
            val patch = JSONObject().put("status", "withdrawn").put("ended_at", today).put("updated_at", now)
            val filter = "student_id=eq.$studentId&halaqa_id=eq.$halaqaId"
            if (online()) patchConfirmed("student_halaqa_memberships", filter, patch)
            else patch("student_halaqa_memberships", filter, patch, dedupe = "student-membership-end:$studentId:$halaqaId")
        }
        invalidateTableCache("student_halaqa_memberships")
    }

    fun saveStudentSecondaryMemberships(studentId: String, halaqaIds: Set<String>, confirmed: Boolean = true) {
        require(studentId.isNotBlank())
        val existing = studentHalaqaMemberships(studentId = studentId, forceRefresh = online())
        val byHalaqa = (0 until existing.length()).mapNotNull { existing.optJSONObject(it) }
            .associateBy { it.optString("halaqa_id") }
        val today = LocalDate.now().toString()
        halaqaIds.filter(String::isNotBlank).forEach { hid ->
            val payload = JSONObject()
                .put("student_id", studentId)
                .put("halaqa_id", hid)
                .put("membership_type", "participant")
                .put("status", "active")
                .put("joined_at", byHalaqa[hid]?.optString("joined_at")?.takeIf { it.isNotBlank() } ?: today)
                .put("ended_at", JSONObject.NULL)
                .put("updated_at", java.time.Instant.now().toString())
            if (confirmed && online()) writeConfirmed("student_halaqa_memberships", payload, onConflict = "student_id,halaqa_id")
            else write("student_halaqa_memberships", payload, onConflict = "student_id,halaqa_id", dedupe = "student-membership:$studentId:$hid")
        }
        byHalaqa.forEach { (hid, row) ->
            if (hid.isBlank() || hid in halaqaIds || row.optString("status") != "active") return@forEach
            val patch = JSONObject()
                .put("status", "withdrawn")
                .put("ended_at", today)
                .put("updated_at", java.time.Instant.now().toString())
            val filter = "student_id=eq.$studentId&halaqa_id=eq.$hid"
            if (confirmed && online()) patchConfirmed("student_halaqa_memberships", filter, patch)
            else patch("student_halaqa_memberships", filter, patch, dedupe = "student-membership-end:$studentId:$hid")
        }
        invalidateTableCache("student_halaqa_memberships")
    }


    fun cachedStudents(ownerId: String, maxAge: Duration? = null): List<Student>? =
        cache.get(ownerId, CACHE_STUDENTS, maxAge)?.let(::studentsFromJson)

    fun refreshStudents(): List<Student> {
        val owner = currentUserId() ?: return emptyList()
        val localFallback = { offlineDb.students().all(owner).map { Student.from(it.asJson()) } }
        if (!online()) return localFallback()
        // RC79: the bulk list no longer carries photo_path (about 15 KB per student). Photos are fetched once, then only for
        // students whose row changed on the server, and are kept in the local database in between.
        val arr = runCatching { withAuthRetry {
            api.select("students", "select=$STUDENT_COLUMNS_NO_PHOTO&order=full_name.asc&limit=1000")
        } }.getOrElse { return localFallback().ifEmpty { cachedStudents(owner, null).orEmpty() } }
        val dirtyIds = offlineDb.students().dirty(owner).mapTo(hashSetOf()) { it.id }
        val localPhotos = offlineDb.students().all(owner).associate { it.id to it.photoPath }
        val photoStamps = studentPhotoStamps()
        val needPhoto = mutableListOf<String>()
        for (index in 0 until arr.length()) {
            val row = arr.optJSONObject(index) ?: continue
            val id = row.optString("id")
            if (id in dirtyIds) continue
            if (photoStamps.optString(id) != row.optString("updated_at") || id !in localPhotos) needPhoto += id
        }
        val fetchedPhotos = hashMapOf<String, String?>()
        // RC95: photos are data URLs (tens of KB each). A chunk of 40 could exceed the 20 s read timeout on a slow line,
        // and one failed chunk left all of its students without a photo on every refresh. Smaller chunks, and a failed
        // chunk is retried in threes (stopping at the first failed retry).
        fun takePhotos(photos: org.json.JSONArray) {
            for (i in 0 until photos.length()) photos.optJSONObject(i)?.let {
                fetchedPhotos[it.optString("id")] = it.optString("photo_path").takeUnless { p -> p.isBlank() || p == "null" }
            }
        }
        var lineDown = false
        for (ids in needPhoto.chunked(12)) {
            if (lineDown) break
            runCatching {
                withAuthRetry { api.select("students", "select=id,photo_path&id=in.(${ids.joinToString(",")})") }
            }.onSuccess(::takePhotos).onFailure {
                // Retry in threes; if that fails too the connection is the problem, so stop instead of waiting on every student.
                for (few in ids.chunked(3)) {
                    val ok = runCatching { withAuthRetry { api.select("students", "select=id,photo_path&id=in.(${few.joinToString(",")})") } }
                        .onSuccess(::takePhotos).isSuccess
                    if (!ok) { lineDown = true; break }
                }
            }
        }
        offlineDb.students().clearClean(owner)
        for (index in 0 until arr.length()) {
            val row = arr.optJSONObject(index) ?: continue
            val id = row.optString("id")
            if (id in dirtyIds) continue
            val photo = if (fetchedPhotos.containsKey(id)) fetchedPhotos[id] else localPhotos[id]
            row.put("photo_path", photo ?: JSONObject.NULL)
            upsertStudentLocal(owner, row, dirty = false)
            if (fetchedPhotos.containsKey(id)) photoStamps.put(id, row.optString("updated_at"))
        }
        saveStudentPhotoStamps(photoStamps)
        cache.put(owner, CACHE_STUDENTS, localStudentsJson(owner).toString())
        markPulled(owner)
        return localFallback()
    }

    fun studentRaw(studentId: String): JSONObject? {
        val owner = currentUserId() ?: return null
        return offlineDb.students().byId(owner, studentId)?.takeUnless { it.deleted }?.asJson()
    }

    private fun studentPhotoStamps(): JSONObject = runCatching {
        val owner = currentUserId() ?: "anon"
        JSONObject(context.applicationContext.getSharedPreferences("halaqati_photo_stamps", Context.MODE_PRIVATE).getString("students:$owner", "{}") ?: "{}")
    }.getOrDefault(JSONObject())

    private fun saveStudentPhotoStamps(stamps: JSONObject) {
        val owner = currentUserId() ?: "anon"
        context.applicationContext.getSharedPreferences("halaqati_photo_stamps", Context.MODE_PRIVATE).edit().putString("students:$owner", stamps.toString()).apply()
    }

    private fun upsertStudentLocal(owner: String, row: JSONObject, dirty: Boolean) {
        if (row.optString("id").isBlank()) return
        offlineDb.students().upsert(StudentLocalEntity.fromJson(owner, row, dirty))
    }

    private fun localStudentsJson(owner: String): JSONArray = JSONArray().apply {
        offlineDb.students().all(owner).forEach { put(it.asJson()) }
    }

    private fun studentToJson(student: Student): JSONObject = JSONObject()
        .put("id", student.id).put("full_name", student.fullName).put("status", student.status)
        .put("track_code", student.track).put("category_id", student.categoryId ?: JSONObject.NULL)
        .put("halaqa_id", student.halaqaId ?: JSONObject.NULL).put("primary_teacher_id", student.primaryTeacherId ?: JSONObject.NULL)
        .put("national_id", student.nationalId ?: JSONObject.NULL)
        .put("phone", student.phone ?: JSONObject.NULL).put("date_of_birth", student.dateOfBirth ?: JSONObject.NULL)
        .put("school_grade", student.schoolGrade ?: JSONObject.NULL).put("guardian_phone", student.guardianPhone ?: JSONObject.NULL)
        .put("guardian_email", student.guardianEmail ?: JSONObject.NULL).put("gender", student.gender ?: JSONObject.NULL)
        .put("enrollment_date", student.enrollmentDate ?: JSONObject.NULL).put("program_name", student.programName ?: JSONObject.NULL)
        .put("address", student.address ?: JSONObject.NULL).put("notes", student.notes ?: JSONObject.NULL)
        .put("photo_path", student.photoPath ?: JSONObject.NULL).also { json ->
            student.targetPagesWeekly?.let { json.put("target_pages_weekly", it) }
            student.targetReviewPagesWeekly?.let { json.put("target_review_pages_weekly", it) }
            student.updatedAt?.let { json.put("updated_at", it) }
        }

    fun sessions(forceRefresh: Boolean = false, includeArchived: Boolean = false): List<SessionRow> {
        val owner = currentUserId() ?: return emptyList()
        if (!forceRefresh) cachedSessions(owner, maxAge = null, includeArchived = includeArchived)?.let { cached ->
            if (cached.isNotEmpty()) { if (online()) scheduleSync(); return cached }
        }
        if (!online()) return cachedSessions(owner, maxAge = null, includeArchived = includeArchived).orEmpty()
        return refreshSessions(includeArchived = includeArchived)
    }

    fun cachedSessions(ownerId: String, maxAge: Duration? = null, includeArchived: Boolean = false): List<SessionRow>? =
        cache.get(ownerId, CACHE_SESSIONS, maxAge)?.let { sessionsFromJson(it, includeArchived) }

    fun refreshSessions(includeArchived: Boolean = false): List<SessionRow> {
        val owner = currentUserId() ?: return emptyList()
        val stale = cachedSessions(owner, maxAge = null, includeArchived = includeArchived).orEmpty()
        if (!online()) return stale
        val arr = runCatching { withAuthRetry { api.select("sessions", "select=*&status=neq.cancelled&order=session_date.desc&limit=300") } }
            .getOrElse { return stale }
        cache.put(owner, CACHE_SESSIONS, arr.toString())
        return sessionsFromJson(arr.toString(), includeArchived)
    }

    fun plans(forceRefresh: Boolean = false): List<PlanRow> {
        val owner = currentUserId() ?: return emptyList()
        if (!forceRefresh) cachedPlans(owner, maxAge = null)?.let { cached ->
            if (cached.isNotEmpty()) { if (online()) scheduleSync(); return cached }
        }
        if (!online()) return cachedPlans(owner, maxAge = null).orEmpty()
        return refreshPlans()
    }

    fun cachedPlans(ownerId: String, maxAge: Duration? = null): List<PlanRow>? =
        cache.get(ownerId, CACHE_PLANS, maxAge)?.let(::plansFromJson)

    fun refreshPlans(): List<PlanRow> {
        val owner = currentUserId() ?: return emptyList()
        val stale = cachedPlans(owner, maxAge = null).orEmpty()
        if (!online()) return stale
        val arr = runCatching { withAuthRetry { api.select("smart_plans", "select=*&order=updated_at.desc&limit=500") } }
            .getOrElse { return stale }
        cache.put(owner, CACHE_PLANS, arr.toString())
        invalidatePlanSnapshotMemory()
        return plansFromJson(arr.toString())
    }

    private fun studentsFromJson(raw: String): List<Student> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(Student::from) }
    }

    private fun sessionsFromJson(raw: String, includeArchived: Boolean = false): List<SessionRow> {
        val arr = JSONArray(raw)
        val rows = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SessionRow::from) }
        return if (includeArchived) rows else rows.filterNot { it.archived }
    }

    private fun plansFromJson(raw: String): List<PlanRow> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(PlanRow::from) }
    }


    private fun phasesFromJson(raw: String): List<SmartPlanPhaseRow> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SmartPlanPhaseRow::from) }
    }

    private fun phaseStatesFromJson(raw: String): List<SmartPlanPhaseStateRow> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SmartPlanPhaseStateRow::from) }
    }

    private fun phaseTemplatesFromJson(raw: String): List<SmartPlanPhaseTemplateRow> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(SmartPlanPhaseTemplateRow::from) }
    }

    private fun certificatesFromJson(raw: String): List<CertificateRecord> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(CertificateRecord::from) }
    }

    private fun certificateTemplatesFromJson(raw: String): List<CertificateTemplateRow> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(CertificateTemplateRow::from) }
    }

    private fun certificateSeriesFromJson(raw: String): List<CertificateSeriesRow> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(CertificateSeriesRow::from) }
    }

    private fun certificateRevisionsFromJson(raw: String): List<CertificateRevisionRow> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(CertificateRevisionRow::from) }
    }

    private fun rpcObject(raw: String): JSONObject = raw.trim().let { text ->
        when {
            text.startsWith("[") -> JSONArray(text).optJSONObject(0) ?: JSONObject()
            text.isNotBlank() -> JSONObject(text)
            else -> JSONObject()
        }
    }

    /** RC12.1 server-backed certificate archive. Local PDF rendering stays available offline. */
    fun certificates(studentId: String? = null, forceRefresh: Boolean = false): List<CertificateRecord> {
        val owner = currentUserId() ?: return emptyList()
        val cached = cache.get(owner, CACHE_CERTIFICATES, maxAge = null)?.let(::certificatesFromJson).orEmpty()
        val all = if (!forceRefresh && cached.isNotEmpty()) cached else if (!online()) cached else {
            runCatching {
                val arr = withAuthRetry { api.select("certificates", "select=*&order=created_at.desc&limit=1500") }
                cache.put(owner, CACHE_CERTIFICATES, arr.toString())
                certificatesFromJson(arr.toString())
            }.getOrElse { cached }
        }
        return studentId?.takeIf { it.isNotBlank() }?.let { sid -> all.filter { it.studentId == sid } } ?: all
    }

    fun certificateTemplates(halaqaId: String? = null, forceRefresh: Boolean = false): List<CertificateTemplateRow> {
        val owner = currentUserId() ?: return emptyList()
        val cached = cache.get(owner, CACHE_CERTIFICATE_TEMPLATES, maxAge = null)?.let(::certificateTemplatesFromJson).orEmpty()
        val all = if (!forceRefresh && cached.isNotEmpty()) cached else if (!online()) cached else {
            runCatching {
                val arr = withAuthRetry { api.select("certificate_templates", "select=*&active=eq.true&order=name.asc&limit=300") }
                cache.put(owner, CACHE_CERTIFICATE_TEMPLATES, arr.toString())
                certificateTemplatesFromJson(arr.toString())
            }.getOrElse { cached }
        }
        return halaqaId?.takeIf { it.isNotBlank() }?.let { hid -> all.filter { it.halaqaId == null || it.halaqaId == hid } } ?: all
    }

    /** Offline-capable Draft. Final issuance/number allocation intentionally requires a live server. */
    fun saveCertificateDraft(draft: CertificateCoreDraft): CertificateRecord {
        require(draft.studentId.isNotBlank()) { "الطالب مطلوب للشهادة" }
        require(draft.certificateType in setOf("hifz", "sard", "achievement")) { "نوع الشهادة غير معتمد" }
        val id = draft.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val obj = JSONObject()
            .put("id", id)
            .put("student_id", draft.studentId)
            .put("certificate_type", draft.certificateType)
            .put("title", draft.title.trim())
            .put("status", "draft")
            .put("snapshot", JSONObject(draft.snapshot.toString()))
        draft.seriesKey?.takeIf { it.isNotBlank() }?.let { obj.put("series_key", it) }
        draft.halaqaId?.takeIf { it.isNotBlank() }?.let { obj.put("halaqa_id", it) }
        draft.issueDate?.takeIf { it.isNotBlank() }?.let { obj.put("issue_date", it) }
        draft.sourcePlanId?.takeIf { it.isNotBlank() }?.let { obj.put("source_plan_id", it) }
        draft.sourcePhaseId?.takeIf { it.isNotBlank() }?.let { obj.put("source_phase_id", it) }
        draft.templateId?.takeIf { it.isNotBlank() }?.let { obj.put("template_id", it) }
        draft.startSurah?.let { obj.put("start_surah", it) }
        draft.startAyah?.let { obj.put("start_ayah", it) }
        draft.endSurah?.let { obj.put("end_surah", it) }
        draft.endAyah?.let { obj.put("end_ayah", it) }
        draft.startPage?.let { obj.put("start_page", it) }
        draft.endPage?.let { obj.put("end_page", it) }
        draft.pagesCount?.let { obj.put("pages_count", it) }
        draft.juzCount?.let { obj.put("juz_count", it) }
        store.load()?.userId?.let { obj.put("created_by", it) }
        write("certificates", obj, onConflict = "id", dedupe = "certificate:$id")
        signalLocalMutation()
        return CertificateRecord.from(obj)
    }

    fun issueCertificate(certificateId: String, issueDate: String = LocalDate.now().toString()): CertificateRecord {
        require(certificateId.isNotBlank()) { "معرّف الشهادة غير صالح" }
        require(online()) { "اعتماد الشهادة يحتاج اتصالًا بالخادم لضمان رقم فريد" }
        LocalDate.parse(issueDate)
        val raw = synchronized(pushLock) {
            withAuthRetry {
                api.rpc(
                    "rc121_issue_certificate",
                    JSONObject().put("p_certificate_id", certificateId).put("p_issue_date", issueDate),
                )
            }
        }
        val obj = rpcObject(raw)
        if (obj.optString("id").isBlank()) error("لم يُرجع الخادم شهادة معتمدة")
        currentUserId()?.let { owner -> upsertJsonArrayCache(owner, CACHE_CERTIFICATES, obj) }
        signalLocalMutation()
        return CertificateRecord.from(obj)
    }

    /** RC24 numbering-series configuration. */
    fun certificateSeries(forceRefresh: Boolean = false): List<CertificateSeriesRow> {
        val owner = currentUserId() ?: return emptyList()
        val cached = cache.get(owner, CACHE_CERTIFICATE_SERIES, maxAge = null)?.let(::certificateSeriesFromJson).orEmpty()
        if (!forceRefresh && cached.isNotEmpty()) return cached
        if (!online()) return cached
        return runCatching {
            val arr = withAuthRetry { api.select("certificate_series", "select=*&order=series_key.asc") }
            cache.put(owner, CACHE_CERTIFICATE_SERIES, arr.toString())
            certificateSeriesFromJson(arr.toString())
        }.getOrElse { cached }
    }

    fun certificateRevisions(certificateId: String, forceRefresh: Boolean = false): List<CertificateRevisionRow> {
        require(certificateId.isNotBlank()) { "معرّف الشهادة مطلوب" }
        val owner = currentUserId() ?: return emptyList()
        val key = CACHE_CERTIFICATE_REVISIONS_PREFIX + certificateId
        val cached = cache.get(owner, key, maxAge = null)?.let(::certificateRevisionsFromJson).orEmpty()
        if (!forceRefresh && cached.isNotEmpty()) return cached
        if (!online()) return cached
        return runCatching {
            val arr = withAuthRetry { api.select("certificate_revisions", "select=*&certificate_id=eq.$certificateId&order=revision_no.desc") }
            cache.put(owner, key, arr.toString())
            certificateRevisionsFromJson(arr.toString())
        }.getOrElse { cached }
    }

    fun issueCertificateRc24(
        certificateId: String,
        seriesKey: String,
        issueDate: String,
        snapshot: CertificateRenderSnapshot,
    ): CertificateRecord {
        require(certificateId.isNotBlank()) { "معرّف الشهادة غير صالح" }
        require(seriesKey.isNotBlank()) { "سلسلة الشهادة مطلوبة" }
        require(online()) { "اعتماد الشهادة يحتاج اتصالًا بالخادم لضمان رقم فريد" }
        LocalDate.parse(issueDate)
        val errors = CertificateRules.validateRequiredSnapshot(snapshot, requireOfficialNumber = false)
        require(errors.isEmpty()) { "بيانات الشهادة ناقصة: " + errors.joinToString("، ") { it.arabicLabel } }
        val raw = synchronized(pushLock) {
            withAuthRetry {
                api.rpc(
                    "rc24_issue_certificate",
                    JSONObject()
                        .put("p_certificate_id", certificateId)
                        .put("p_series_key", seriesKey)
                        .put("p_issue_date", issueDate)
                        .put("p_render_snapshot", snapshot.toJson()),
                )
            }
        }
        val obj = rpcObject(raw)
        if (obj.optString("id").isBlank()) error("لم يُرجع الخادم شهادة RC24 معتمدة")
        currentUserId()?.let { owner ->
            upsertJsonArrayCache(owner, CACHE_CERTIFICATES, obj)
            cache.invalidate(owner, CACHE_CERTIFICATE_REVISIONS_PREFIX + certificateId)
        }
        signalLocalMutation()
        return CertificateRecord.from(obj)
    }

    fun createCertificateRevision(
        certificateId: String,
        snapshot: CertificateRenderSnapshot,
        note: String? = null,
    ): CertificateRevisionRow {
        require(online()) { "إنشاء نسخة مصححة يحتاج اتصالًا بالخادم" }
        val errors = CertificateRules.validateRequiredSnapshot(snapshot, requireOfficialNumber = true)
        require(errors.isEmpty()) { "بيانات الشهادة ناقصة: " + errors.joinToString("، ") { it.arabicLabel } }
        val raw = synchronized(pushLock) {
            withAuthRetry {
                api.rpc(
                    "rc24_create_certificate_revision",
                    JSONObject()
                        .put("p_certificate_id", certificateId)
                        .put("p_render_snapshot", snapshot.toJson())
                        .put("p_note", note ?: JSONObject.NULL),
                )
            }
        }
        val row = CertificateRevisionRow.from(rpcObject(raw))
        if (row.id.isBlank()) error("لم يُرجع الخادم نسخة شهادة")
        currentUserId()?.let { owner -> cache.invalidate(owner, CACHE_CERTIFICATE_REVISIONS_PREFIX + certificateId) }
        signalLocalMutation()
        return row
    }

    fun voidCertificate(certificateId: String, reason: String): CertificateRecord {
        require(online()) { "إلغاء الشهادة يحتاج اتصالًا بالخادم" }
        val obj = rpcObject(synchronized(pushLock) {
            withAuthRetry { api.rpc("rc24_void_certificate", JSONObject().put("p_certificate_id", certificateId).put("p_reason", reason.trim())) }
        })
        if (obj.optString("id").isBlank()) error("تعذر إلغاء الشهادة")
        currentUserId()?.let { owner -> upsertJsonArrayCache(owner, CACHE_CERTIFICATES, obj) }
        signalLocalMutation()
        return CertificateRecord.from(obj)
    }

    /**
     * RC24.1: permanently removes one certificate record (its revisions cascade).
     * Needs a live connection; the UI only offers it to administrators. Existing RLS
     * (certificates_manage_access) already scopes who may delete, so no migration is required.
     * The official counter is intentionally NOT rewound: a deleted number is never re-issued.
     */
    fun deleteCertificate(certificateId: String) {
        require(Regex("^[0-9a-fA-F-]{36}$").matches(certificateId)) { "معرّف الشهادة غير صالح" }
        val removed = deleteConfirmed("certificates", "id=eq.$certificateId")
        if (removed.length() == 0) error("لم يُحذف أي سجل؛ الشهادة غير موجودة أو لا تملك صلاحية حذفها")
        currentUserId()?.let { owner ->
            removeJsonArrayCacheRow(owner, CACHE_CERTIFICATES, certificateId)
            cache.invalidate(owner, CACHE_CERTIFICATE_REVISIONS_PREFIX + certificateId)
        }
        signalLocalMutation()
    }

    fun importLegacyCertificate(
        studentId: String,
        halaqaId: String?,
        certificateType: String,
        seriesKey: String,
        issueDate: String,
        historicalNumber: String,
        snapshot: CertificateRenderSnapshot? = null,
    ): CertificateRecord {
        require(online()) { "إضافة سجل أرشيف رسمي يحتاج اتصالًا بالخادم" }
        LocalDate.parse(issueDate)
        val args = JSONObject()
            .put("p_student_id", studentId)
            .put("p_halaqa_id", halaqaId ?: JSONObject.NULL)
            .put("p_certificate_type", certificateType)
            .put("p_series_key", seriesKey)
            .put("p_issue_date", issueDate)
            .put("p_historical_number", historicalNumber.trim())
            .put("p_render_snapshot", snapshot?.toJson() ?: JSONObject.NULL)
        val obj = rpcObject(synchronized(pushLock) { withAuthRetry { api.rpc("rc24_import_legacy_certificate", args) } })
        if (obj.optString("id").isBlank()) error("تعذر إضافة السجل التاريخي")
        currentUserId()?.let { owner -> upsertJsonArrayCache(owner, CACHE_CERTIFICATES, obj) }
        signalLocalMutation()
        return CertificateRecord.from(obj)
    }

    fun planPhases(planId: String? = null, forceRefresh: Boolean = false): List<SmartPlanPhaseRow> {
        val owner = currentUserId() ?: return emptyList()
        val cached = cache.get(owner, CACHE_PLAN_PHASES, maxAge = null)?.let(::phasesFromJson).orEmpty()
        val all = if (!forceRefresh && cached.isNotEmpty()) {
            if (online()) scheduleSync()
            cached
        } else if (!online()) cached else {
            runCatching {
                val arr = withAuthRetry { api.select("smart_plan_phases", "select=*&order=plan_id.asc,phase_order.asc&limit=2000") }
                cache.put(owner, CACHE_PLAN_PHASES, arr.toString())
                invalidatePlanSnapshotMemory()
                phasesFromJson(arr.toString())
            }.getOrElse { cached }
        }
        return if (planId.isNullOrBlank()) all else all.filter { it.planId == planId }
    }

    fun planPhaseStates(studentId: String? = null, forceRefresh: Boolean = false): List<SmartPlanPhaseStateRow> {
        val owner = currentUserId() ?: return emptyList()
        val cached = cache.get(owner, CACHE_PLAN_PHASE_STATES, maxAge = null)?.let(::phaseStatesFromJson).orEmpty()
        val all = if (!forceRefresh && cached.isNotEmpty()) {
            if (online()) scheduleSync()
            cached
        } else if (!online()) cached else {
            runCatching {
                val arr = withAuthRetry { api.select("smart_plan_phase_states", "select=*&order=updated_at.desc&limit=5000") }
                cache.put(owner, CACHE_PLAN_PHASE_STATES, arr.toString())
                invalidatePlanSnapshotMemory()
                phaseStatesFromJson(arr.toString())
            }.getOrElse { cached }
        }
        return if (studentId.isNullOrBlank()) all else all.filter { it.studentId == studentId }
    }

    /** RC11.1: expose the independent historical Sard anchor to the phase editor. */
    fun latestSardRecitation(
        studentId: String,
        today: LocalDate = LocalDate.now(),
        forceRefresh: Boolean = false,
    ): SmartPlanEngine.Recitation? {
        if (studentId.isBlank()) return null
        val recitations = planRecitations(today.minusMonths(18).toString(), today.toString(), forceRefresh)
            .filter { it.studentId == studentId && it.date <= today }
        val anchor = SardAnchorResolver.resolve(recitations, today) ?: return null
        return recitations.firstOrNull { it.id == anchor.recitationId }
    }

    /** RC17.1 BF3: one resolver for the latest accepted anchor used by editors and runtime.
     * If a plan is supplied, the same quality gate as SmartPlanEngine is applied. */
    fun latestAcceptedActivityRecitation(
        studentId: String,
        activity: String,
        planId: String? = null,
        today: LocalDate = LocalDate.now(),
        forceRefresh: Boolean = false,
    ): SmartPlanEngine.Recitation? {
        if (studentId.isBlank()) return null

        // BF3 fast path: one tiny RPC instead of pulling the 10k-row Smart Plan history merely
        // to open an editor. The local-history fallback keeps offline/old-schema behaviour intact.
        if (online()) {
            val remote = runCatching {
                val raw = withAuthRetry {
                    api.rpc(
                        "rc171_latest_activity_anchor",
                        JSONObject()
                            .put("p_student_id", studentId)
                            .put("p_activity", activity)
                            .put("p_as_of", today.toString())
                            .put("p_plan_id", planId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL),
                    )
                }.trim()
                val obj = when {
                    raw.isBlank() || raw == "null" -> null
                    raw.startsWith("[") -> JSONArray(raw).optJSONObject(0)
                    else -> JSONObject(raw)
                } ?: return@runCatching null
                val date = runCatching { LocalDate.parse(obj.optString("session_date")) }.getOrNull() ?: return@runCatching null
                SmartPlanEngine.Recitation(
                    id = obj.optString("id"),
                    studentId = studentId,
                    activityType = obj.optString("activity_type"),
                    date = date,
                    startPage = nullablePage(obj, "start_page"),
                    endPage = nullablePage(obj, "end_page"),
                    startSurah = if (!obj.has("start_surah") || obj.isNull("start_surah")) null else obj.optInt("start_surah").takeIf { it in 1..114 },
                    startAyah = if (!obj.has("start_ayah") || obj.isNull("start_ayah")) null else obj.optInt("start_ayah").takeIf { it > 0 },
                    endSurah = if (!obj.has("end_surah") || obj.isNull("end_surah")) null else obj.optInt("end_surah").takeIf { it in 1..114 },
                    endAyah = if (!obj.has("end_ayah") || obj.isNull("end_ayah")) null else obj.optInt("end_ayah").takeIf { it > 0 },
                    pagesCount = obj.optDouble("pages_count", 0.0),
                    evaluation = obj.optString("evaluation").ifBlank { null },
                    mistakes = obj.optInt("mistakes", 0),
                    prompts = obj.optInt("prompts", 0),
                    createdAt = obj.optString("created_at"),
                )
            }.getOrNull()
            if (remote != null) return remote
        }

        val all = planRecitations(today.minusMonths(18).toString(), today.toString(), forceRefresh)
            .filter { it.studentId == studentId && it.date <= today }
        val enginePlan = planId?.takeIf { it.isNotBlank() }
            ?.let { wanted -> plans().firstOrNull { it.id == wanted }?.toEnginePlan() }
        val eligible = if (enginePlan == null) {
            all.filterNot { it.evaluation.orEmpty().lowercase() in setOf("repeat", "weak", "إعادة", "ضعيف") }
        } else {
            all.filter { SmartPlanEngine.isPassing(it, enginePlan) }
        }
        return SmartPlanActivityRules.latestAnchor(eligible, activity)
    }

    /**
     * Detect the RC11 schema-cache failure before the UI creates more queued writes.
     * Offline use is still allowed; only the explicit PostgREST missing-table error is returned.
     */
    fun smartPlanPhaseSchemaIssue(): String? {
        if (!online()) return null
        return try {
            withAuthRetry { api.select("smart_plan_phases", "select=id&limit=1") }
            withAuthRetry { api.select("smart_plan_phase_states", "select=id&limit=1") }
            withAuthRetry { api.select("smart_plan_phase_templates", "select=id&limit=1") }
            // RC11.2 has mutation RPCs in addition to the RC11 tables. A lightweight
            // capability probe prevents the UI from queueing writes against a half-migrated backend.
            withAuthRetry { api.rpc("rc112_capabilities", JSONObject()) }
            withAuthRetry { api.rpc("rc113_capabilities", JSONObject()) }
            null
        } catch (e: Exception) {
            val raw = e.message.orEmpty()
            if (raw.contains("schema cache", ignoreCase = true) ||
                raw.contains("Could not find the table", ignoreCase = true) ||
                raw.contains("Could not find the function", ignoreCase = true) ||
                raw.contains("PGRST205", ignoreCase = true) || raw.contains("PGRST202", ignoreCase = true)) {
                "قاعدة Supabase الحالية غير مكتملة لـRC11.3. طبّق migrations الخاصة بـRC11 وRC11.2 إن لم تكن مطبقة، ثم migration/20260927_v2463_rc11_3_bulk_cycle_stability.sql وأعد المزامنة."
            } else null
        }
    }

    /** RC11.2: freeze ended phases even when the teacher did not press End. */
    fun reconcilePlanPhaseLifecycle(planId: String, asOf: LocalDate = LocalDate.now()) {
        if (planId.isBlank() || !online()) return
        rpcWrite(
            "rc112_reconcile_plan_phase_states",
            JSONObject().put("p_plan_id", planId).put("p_as_of", asOf.toString()),
            dedupe = "phase-reconcile:$planId:$asOf",
        )
        invalidatePlanSnapshotMemory()
    }

    fun previousSardShortfalls(
        studentIds: Set<String>,
        beforeDate: LocalDate,
        forceRefresh: Boolean = false,
    ): Map<String, Double> {
        if (studentIds.isEmpty()) return emptyMap()
        val phases = planPhases(forceRefresh = forceRefresh)
            .filter { it.phaseType == "sard" && it.status != "archived" }
            .mapNotNull { phase ->
                val end = runCatching { LocalDate.parse(phase.endDate) }.getOrNull() ?: return@mapNotNull null
                if (end >= beforeDate) null else phase to end
            }
            .sortedByDescending { it.second }
        if (phases.isEmpty()) return emptyMap()
        val states = planPhaseStates(forceRefresh = forceRefresh)
            .filter { it.studentId in studentIds }
            .associateBy { it.phaseId to it.studentId }
        val result = linkedMapOf<String, Double>()
        studentIds.forEach { studentId ->
            for ((phase, _) in phases) {
                val state = states[phase.id to studentId] ?: continue
                val target = state.frozenTargetPages ?: state.targetPagesOverride ?: phase.targetPages
                    ?: if (phase.goalMode == "juz") phase.targetUnits?.times(20.0) else null
                val completed = state.frozenCompletedPages ?: continue
                if (target != null) {
                    result[studentId] = SmartPlanEngine.round((target - completed).coerceAtLeast(0.0))
                    break
                }
            }
        }
        return result
    }

    fun planPhaseTemplates(halaqaId: String? = null, forceRefresh: Boolean = false): List<SmartPlanPhaseTemplateRow> {
        val owner = currentUserId() ?: return emptyList()
        val cached = cache.get(owner, CACHE_PLAN_PHASE_TEMPLATES, maxAge = null)?.let(::phaseTemplatesFromJson).orEmpty()
        val all = if (!forceRefresh && cached.isNotEmpty()) cached else if (!online()) cached else {
            runCatching {
                val arr = withAuthRetry { api.select("smart_plan_phase_templates", "select=*&active=eq.true&order=updated_at.desc&limit=500") }
                cache.put(owner, CACHE_PLAN_PHASE_TEMPLATES, arr.toString())
                phaseTemplatesFromJson(arr.toString())
            }.getOrElse { cached }
        }
        return if (halaqaId.isNullOrBlank()) all else all.filter { it.halaqaId == halaqaId }
    }

    fun cachedDashboardRecitations(ownerId: String, maxAge: Duration? = null): JSONArray? =
        cache.get(ownerId, CACHE_DASHBOARD_RECITATIONS, maxAge)?.let(::JSONArray)

    fun refreshDashboardRecitations(): JSONArray {
        val owner = currentUserId()
        val cached = owner?.let { cachedDashboardRecitations(it, maxAge = null) } ?: JSONArray()
        val remote = if (online()) runCatching {
            withAuthRetry {
                api.select(
                    "recitation_entries",
                    "select=id,session_id,student_id,activity_type,pages_count,mistakes,prompts,evaluation,safety_percentage,created_at,sessions!inner(session_date)" +
                        "&order=created_at.desc&limit=1200",
                )
            }
        }.getOrElse { cached } else cached
        val byId = linkedMapOf<String, JSONObject>()
        for (i in 0 until remote.length()) {
            val row = remote.optJSONObject(i) ?: continue
            val id = row.optString("id")
            if (id.isNotBlank()) byId[id] = row
        }

        // Include optimistic offline writes. Their authoritative day is the session day, never
        // created_at (which can be hours/days later when the queue finally syncs).
        val sessionsById = owner?.let { cachedSessions(it, maxAge = null) }.orEmpty().associateBy { it.id }
        queueSnapshot(1500).forEach { item ->
            if (!item.dedupe.orEmpty().startsWith("recitation:")) return@forEach
            val body = item.body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@forEach
            val id = body.optString("id")
            if (id.isBlank()) return@forEach
            val date = sessionsById[body.optString("session_id")]?.date
            if (!date.isNullOrBlank()) body.put("session_date", date)
            byId[id] = body
        }
        val arr = JSONArray()
        byId.values
            .sortedByDescending { it.optString("created_at") }
            .forEach(arr::put)
        owner?.let { cache.put(it, CACHE_DASHBOARD_RECITATIONS, arr.toString()) }
        return arr
    }

    fun attendanceForSession(sessionId: String, allowNetwork: Boolean = true): Map<String, AttendanceRow> {
        val owner = currentUserId()
        val key = CACHE_SESSION_ATTENDANCE_PREFIX + sessionId
        var source = owner?.let { cache.get(it, key, maxAge = null) }?.let { runCatching { JSONArray(it) }.getOrNull() }
        if (source == null && allowNetwork && online()) {
            source = runCatching {
                withAuthRetry {
                    api.select(
                        "attendance_records",
                        "select=id,session_id,student_id,status,excuse_reason,arrival_minutes_late" +
                            "&session_id=eq.$sessionId&limit=2000",
                    )
                }
            }.getOrNull() ?: source
            if (source != null && owner != null) cache.put(owner, key, source.toString())
        }
        if (source == null && owner != null) source = cache.get(owner, key, maxAge = null)?.let { runCatching { JSONArray(it) }.getOrNull() }
        val rows = linkedMapOf<String, AttendanceRow>()
        val arr = source ?: JSONArray()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(AttendanceRow::from)?.let { rows[it.studentId] = it }
        queueSnapshot(1200).forEach { item ->
            if (!item.dedupe.orEmpty().startsWith("attendance:$sessionId:")) return@forEach
            val body = item.body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@forEach
            val row = AttendanceRow.from(body)
            if (row.studentId.isNotBlank()) rows[row.studentId] = row
        }
        return rows
    }

    fun notesForSession(sessionId: String, allowNetwork: Boolean = true): Map<String, DailyNoteRow> {
        val owner = currentUserId()
        val key = CACHE_SESSION_NOTES_PREFIX + sessionId
        var source = owner?.let { cache.get(it, key, maxAge = null) }?.let { runCatching { JSONArray(it) }.getOrNull() }
        if (source == null && allowNetwork && online()) {
            source = runCatching {
                withAuthRetry {
                    api.select(
                        "student_daily_notes",
                        "select=id,session_id,student_id,behavior,behavior_score,teacher_comment,homework,parent_visible,participation" +
                            "&session_id=eq.$sessionId&limit=2000",
                    )
                }
            }.getOrNull() ?: source
            if (source != null && owner != null) cache.put(owner, key, source.toString())
        }
        if (source == null && owner != null) source = cache.get(owner, key, maxAge = null)?.let { runCatching { JSONArray(it) }.getOrNull() }
        val rows = linkedMapOf<String, DailyNoteRow>()
        val arr = source ?: JSONArray()
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(DailyNoteRow::from)?.let { rows[it.studentId] = it }
        queueSnapshot(1200).forEach { item ->
            if (!item.dedupe.orEmpty().startsWith("note:$sessionId:")) return@forEach
            val body = item.body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@forEach
            val row = DailyNoteRow.from(body)
            if (row.studentId.isNotBlank()) rows[row.studentId] = row
        }
        return rows
    }

    fun recitationsForSession(sessionId: String, allowNetwork: Boolean = true): Map<String, List<RecitationRow>> {
        val owner = currentUserId()
        val cacheKey = CACHE_SESSION_RECITATIONS_PREFIX + sessionId
        var source: JSONArray? = null

        // Cache-first just like v2.44: opening a session never needs a network round-trip merely
        // to show recitations that were already synced on this device.
        if (owner != null) {
            source = cache.get(owner, cacheKey, maxAge = null)
                ?.let { runCatching { JSONArray(it) }.getOrNull() }
        }
        if (source == null && allowNetwork && online()) {
            source = runCatching {
                withAuthRetry {
                    api.select(
                        "recitation_entries",
                        "select=id,session_id,student_id,activity_type,input_method,start_surah,start_ayah,end_surah,end_ayah,start_page,end_page,pages_count,ayahs_count,surahs_count,mistakes,prompts,evaluation,safety_percentage,points,teacher_note,selected_surahs,selection_data,created_at" +
                            "&session_id=eq.$sessionId&order=created_at.asc&limit=2000",
                    )
                }
            }.getOrNull() ?: source
            if (source != null && owner != null) cache.put(owner, cacheKey, source.toString())
        }
        if (source == null && owner != null) {
            source = cache.get(owner, cacheKey, maxAge = null)
                ?.let { runCatching { JSONArray(it) }.getOrNull() }
        }

        val byId = linkedMapOf<String, RecitationRow>()
        val remoteOrCached = source ?: JSONArray()
        for (index in 0 until remoteOrCached.length()) {
            remoteOrCached.optJSONObject(index)?.let(RecitationRow::from)?.let { row ->
                if (row.id.isNotBlank()) byId[row.id] = row
            }
        }

        // Optimistic/local-first overlay. The exact object that will be uploaded is rendered now,
        // so "حفظ" immediately changes the roster even with no internet.
        queueSnapshot(1500).forEach { item ->
            val dedupe = item.dedupe.orEmpty()
            if (!dedupe.startsWith("recitation:$sessionId:")) return@forEach
            val body = item.body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@forEach
            val row = RecitationRow.from(body)
            if (row.id.isNotBlank()) byId[row.id] = row
        }
        return byId.values
            .sortedBy { it.createdAt }
            .groupBy { it.studentId }
    }

    private fun activityAssignmentFromAnchor(
        row: PlanRow,
        plan: SmartPlanEngine.Plan,
        recitations: List<SmartPlanEngine.Recitation>,
        activity: String,
        requestedPages: Double,
        anchorOverride: QuranLocation? = null,
        startOverride: QuranLocation? = null,
    ): SmartPlanEngine.Assignment? {
        if (requestedPages <= 0.0) return null
        val route = SmartPlanActivityRules.routeFor(
            activity = activity,
            primaryPlanType = row.type,
            hifzRoute = row.settings.routeDirection,
            sardRoute = row.settings.sardRouteDirection,
        )
        val start = startOverride ?: run {
            val anchor = anchorOverride ?: SmartPlanActivityRules.latestAnchor(recitations, activity)?.let { last ->
                val surah = last.endSurah ?: return@let null
                val ayah = last.endAyah ?: return@let null
                QuranLocation(surah, ayah)
            } ?: return null
            SmartPlanActivityRules.nextLocation(anchor, route) ?: return null
        }
        val reverse = route == "reverse_surahs_forward_ayahs"
        // Both Hifz and Sard in the general-hifz reverse route are defined as Nas -> Baqarah.
        val globalTerminalSurah = SmartPlanActivityRules.terminalSurah(activity, route)
        if (reverse && start.surah < globalTerminalSurah) return null
        // RC17.1 BF3: every activity respects the configured terminal; previously only Sard did.
        val configuredTerminal = when {
            row.rangeEndSurah != null && row.rangeEndAyah != null -> QuranLocation(row.rangeEndSurah, row.rangeEndAyah)
            row.rangeEndPage != null -> QuranMetadata.pageEnd(row.rangeEndPage)
            else -> null
        }
        val globalTerminal = QuranLocation(globalTerminalSurah, QuranMetadata.maxAyah(globalTerminalSurah))
        val terminal = configuredTerminal ?: globalTerminal
        val direction = planTraversalDirection(route)
        val track = if (activity == "tathbit") "tathbit" else "general_hifz"
        val activityType = SmartPlanActivityRules.activityTypeFor(activity)
        fun linesUpTo(end: QuranLocation): List<String> = runCatching {
            QuranCore.engine().calculate(
                QuranRangeRequest(
                    track = track,
                    activityType = activityType,
                    selection = QuranSelection.Ayahs(start, end),
                    direction = direction,
                ),
            ).qcfLineKeys
        }.getOrNull().orEmpty()
        val wantedLines = ceil(requestedPages * 15.0).toInt().coerceAtLeast(1)
        var available = linesUpTo(terminal)
        // RC25: a plan carried into a new month keeps last month's absolute range end. A student who
        // already passed that end (while the month's page target is still open) used to get NO daily
        // hifz at all. Hifz continues along its route past the stale end; review and sard (other routes than the hifz
        // range) are likewise no longer cut off by the hifz range end. Only ever fills an empty result.
        if (available.isEmpty() && activity in setOf("hifz", "review", "sard") && configuredTerminal != null) {
            available = linesUpTo(globalTerminal)
        }
        val keys = available.take(wantedLines)
        if (keys.isEmpty()) return null
        val endpoints = QuranCore.engine().endpointsForLineKeys(keys) ?: return null
        return SmartPlanEngine.Assignment(
            kind = activity,
            pages = keys.size / 15.0,
            startPage = keys.first().substringBefore(':').toIntOrNull(),
            endPage = keys.last().substringBefore(':').toIntOrNull(),
            isCatchUp = false,
            blockedByRetry = false,
            lineKeys = keys,
        )
    }

    private fun projectedNextActivityDuty(
        row: PlanRow,
        plan: SmartPlanEngine.Plan,
        recitations: List<SmartPlanEngine.Recitation>,
        activity: String,
        todayAssignment: SmartPlanEngine.Assignment?,
        nextDate: LocalDate?,
        requestedPages: Double,
    ): PlanNextDuty? {
        val date = nextDate ?: return null
        val projectedAnchor = todayAssignment?.lineKeys?.takeIf { it.isNotEmpty() }
            ?.let { QuranCore.engine().endpointsForLineKeys(it)?.second }
        val assignment = activityAssignmentFromAnchor(
            row = row,
            plan = plan,
            recitations = recitations,
            activity = activity,
            requestedPages = requestedPages,
            anchorOverride = projectedAnchor,
        ) ?: return null
        return PlanNextDuty(date, assignment)
    }

    private fun activitySnapshotsForPlan(
        row: PlanRow,
        plan: SmartPlanEngine.Plan,
        student: Student,
        recitations: List<SmartPlanEngine.Recitation>,
        primaryProgress: SmartPlanEngine.Progress,
        today: LocalDate,
        activePhase: SmartPlanPhaseRow? = null,
        activePhaseState: SmartPlanPhaseStateRow? = null,
        phaseProgress: SmartPlanPhaseEngine.Progress? = null,
    ): List<PlanActivitySnapshot> {
        val enabled = SmartPlanActivityRules.enabledActivities(row.type, row.settings.enabledActivities)
        val passingRecitations = recitations.filter { SmartPlanEngine.isPassing(it, plan) }
        val schedule = SmartPlanEngine.planDates(plan)
        val nextDate = schedule.firstOrNull { it > today }
        val activeToday = today in schedule || primaryProgress.assignment.isCatchUp || primaryProgress.assignment.blockedByRetry
        return enabled.map { activity ->
            val globalLast = SmartPlanActivityRules.latestAnchor(passingRecitations.filter { it.date <= today }, activity)
            val currentPhaseRecitations = if (activity == "sard" && activePhase?.phaseType == "sard") {
                passingRecitations.filter { rec ->
                    rec.date <= today && (
                        rec.taggedPhaseId == activePhase.id ||
                            (rec.taggedPhaseId == null && rec.date >= plan.startDate && rec.date <= plan.endDate)
                    )
                }
            } else passingRecitations.filter { it.date <= today }
            val phaseLast = SmartPlanActivityRules.latestAnchor(currentPhaseRecitations, activity)
            val assignmentAnchorRecitations = when {
                activity != "sard" -> passingRecitations
                activePhase?.phaseType != "sard" -> passingRecitations
                activePhase.startMode in setOf(SmartPlanPhaseEngine.START_LAST_SARD, SmartPlanPhaseEngine.START_LAST_ACTIVITY) -> passingRecitations
                else -> currentPhaseRecitations
            }
            val assignmentLast = SmartPlanActivityRules.latestAnchor(assignmentAnchorRecitations.filter { it.date <= today }, activity)
            val explicitPhaseStart = if (activity == "sard" && activePhase?.phaseType == "sard" && assignmentLast == null) {
                when {
                    activePhase.startMode in setOf(SmartPlanPhaseEngine.START_MANUAL, SmartPlanPhaseEngine.START_RANGE) -> when {
                        activePhase.rangeStartSurah != null && activePhase.rangeStartAyah != null -> QuranLocation(activePhase.rangeStartSurah, activePhase.rangeStartAyah)
                        activePhase.rangeStartPage != null -> QuranMetadata.pageStart(activePhase.rangeStartPage)
                        else -> null
                    }
                    activePhase.startMode in setOf(SmartPlanPhaseEngine.START_LAST_SARD, SmartPlanPhaseEngine.START_LAST_ACTIVITY) && globalLast == null -> when {
                        activePhase.rangeStartSurah != null && activePhase.rangeStartAyah != null -> QuranLocation(activePhase.rangeStartSurah, activePhase.rangeStartAyah)
                        activePhase.rangeStartPage != null -> QuranMetadata.pageStart(activePhase.rangeStartPage)
                        else -> null
                    }
                    else -> null
                }
            } else null
            val target = when {
                activity == "sard" && activePhase?.phaseType == "sard" -> phaseProgress?.targetPages ?: 0.0
                activity == "sard" -> row.settings.sardTargetPages.takeIf { it > 0.0 } ?: 0.0
                else -> row.target
            }
            val scheduledDays = schedule.size.coerceAtLeast(1)
            val plannedDaily = if (target > 0.0) target / scheduledDays else 0.0
            val weeklyDaily = when (activity) {
                "hifz" -> student.targetPagesWeekly?.takeIf { it > 0.0 }?.div(plan.scheduleWeekdays.size.coerceAtLeast(1)) ?: 0.0
                "review" -> student.targetReviewPagesWeekly?.takeIf { it > 0.0 }?.div(plan.scheduleWeekdays.size.coerceAtLeast(1)) ?: 0.0
                else -> 0.0
            }
            val continueSurplus = activity == "sard" && activePhaseState?.settings?.optBoolean("continue_surplus", false) == true
            val dailyComputed = when {
                activity == "sard" && activePhase?.phaseType == "sard" && phaseProgress?.targetPages == null -> 0.0
                activity == "sard" && activePhase?.phaseType == "sard" && phaseProgress?.goalComplete == true && continueSurplus -> plannedDaily
                activity == "sard" && activePhase?.phaseType == "sard" -> phaseProgress?.suggestedTodayPages ?: 0.0
                else -> SmartPlanActivityRules.effectiveDailyPages(
                    activity = activity,
                    engineDaily = primaryProgress.dailyTarget,
                    originalDaily = primaryProgress.originalDaily,
                    minimumDaily = primaryProgress.minimumDaily,
                    plannedDaily = plannedDaily,
                    weeklyDaily = weeklyDaily,
                    assignmentPages = primaryProgress.assignment.pages,
                    assignmentKind = primaryProgress.assignment.kind,
                )
            }
            // RC27.5: the plan's range / month target is a minimum, not a ceiling. When the engine reports
            // nothing left to do for hifz (range or month target already reached) the student still
            // gets the planned daily amount, continuing along the route from where he stopped.
            val daily = if (activity == "hifz" && dailyComputed <= 0.001 && plannedDaily > 0.001) plannedDaily else dailyComputed
            val assignment = when {
                !activeToday -> null
                activity == "hifz" && primaryProgress.assignment.kind != "hifz" -> primaryProgress.assignment
                activity == "sard" && phaseProgress?.goalComplete == true && !continueSurplus -> null
                activity == "sard" && assignmentLast == null && explicitPhaseStart == null -> null
                daily <= 0.001 -> null
                else -> activityAssignmentFromAnchor(
                    row = row,
                    plan = plan,
                    recitations = assignmentAnchorRecitations,
                    activity = activity,
                    requestedPages = daily,
                    startOverride = explicitPhaseStart,
                )
            }
            // RC27.9: students may recite on days the plan does not schedule: offer the usual daily amount from
            // where they stopped. Nothing is counted as late, and the engine counts it normally.
            val extraAssignment = if (activeToday || assignment != null) null else when {
                activity == "sard" && assignmentLast == null && explicitPhaseStart == null -> null
                activity == "sard" && phaseProgress?.goalComplete == true && !continueSurplus -> null
                else -> {
                    val pages = if (daily > 0.001) daily else plannedDaily
                    if (pages <= 0.001) null else runCatching {
                        activityAssignmentFromAnchor(
                            row = row,
                            plan = plan,
                            recitations = assignmentAnchorRecitations,
                            activity = activity,
                            requestedPages = pages,
                            startOverride = explicitPhaseStart,
                        )
                    }.getOrNull()
                }
            }
            val next = projectedNextActivityDuty(
                row = row,
                plan = plan,
                recitations = assignmentAnchorRecitations,
                activity = activity,
                todayAssignment = assignment,
                nextDate = nextDate,
                requestedPages = daily.coerceAtLeast(1.0 / 15.0),
            )
            PlanActivitySnapshot(
                key = activity,
                label = when (activity) { "sard" -> "السرد"; "review" -> "المراجعة"; "tathbit" -> "التثبيت"; else -> "الحفظ" },
                scheduledToday = activeToday,
                lastRecitation = if (activity == "sard") (phaseLast ?: globalLast) else globalLast,
                assignment = assignment,
                nextDuty = next,
                needsStart = activity == "sard" && assignmentLast == null && explicitPhaseStart == null,
                targetPages = target,
                routeDirection = SmartPlanActivityRules.routeFor(activity, row.type, row.settings.routeDirection, row.settings.sardRouteDirection),
                extraAssignment = extraAssignment,
            )
        }
    }

    @Volatile private var tathbitRollDate: LocalDate? = null

    /**
     * RC15.2: freezes finished tathbit months and opens the current month's plan. pg_cron runs the
     * same idempotent function daily; this is a best-effort catch-up and never blocks the UI.
     * Returns true only when the server actually created or froze a plan (plans cache refresh).
     */
    private fun rollTathbitMonthsOncePerDay(today: LocalDate): Boolean {
        if (tathbitRollDate == today || !online()) return false
        tathbitRollDate = today
        return runCatching {
            val raw = withAuthRetry { api.rpc("rc152_roll_tathbit_months", JSONObject()) }.trim()
            val json = runCatching { JSONObject(raw) }.getOrNull()
            (json?.optInt("created") ?: 0) + (json?.optInt("frozen") ?: 0) > 0
        }.getOrDefault(false)
    }

    /**
     * @param baseInputsFresh RC17: the caller has just refreshed students + plans in this pass, so
     * they are read from cache instead of being fetched a second time (the dashboard used to fetch
     * them twice). Phases/states/recitations still follow [forceRefresh].
     */
    @Volatile private var ineligibleHalaqas: Set<String> = emptySet()

    /** RC27.8: halaqas that are NOT regular halaqat (courses/programs): their students get no smart plan. */
    fun smartPlanIneligibleHalaqaIds(): Set<String> {
        runCatching {
            val arr = table("halaqas", "select=id,name,active,halaqa_type,program_specialization,affects_primary_plan,starts_on,ends_on&order=name.asc&limit=200")
            com.mohammedsamir.halaqati.plans.SmartPlanEligibility.ineligibleHalaqaIds(
                (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }.map { it.optString("id") to it.optString("halaqa_type").takeIf { t -> t.isNotBlank() && t != "null" } },
            )
        }.onSuccess { ineligibleHalaqas = it }
        return ineligibleHalaqas
    }

    fun isSmartPlanEligible(halaqaId: String?): Boolean =
        com.mohammedsamir.halaqati.plans.SmartPlanEligibility.isEligible(halaqaId, ineligibleHalaqas)

    fun planSnapshots(today: LocalDate = LocalDate.now(), forceRefresh: Boolean = false, baseInputsFresh: Boolean = false): List<PlanSnapshot> =
        PerfLog.time(if (!forceRefresh && planMemoryValid(today)) "planSnapshots (memory)" else "planSnapshots (compute)") {
            planSnapshotsImpl(today, forceRefresh, baseInputsFresh)
        }

    private fun planSnapshotsImpl(today: LocalDate, forceRefresh: Boolean, baseInputsFresh: Boolean): List<PlanSnapshot> {
        smartPlanIneligibleHalaqaIds()
        if (!forceRefresh && planMemoryValid(today)) {
            if (online()) scheduleSync()
            return planSnapshotMemory
        }
        val rolled = rollTathbitMonthsOncePerDay(today)
        val students = students(forceRefresh = forceRefresh && !baseInputsFresh)
        val planRows = plans(forceRefresh = (forceRefresh && !baseInputsFresh) || rolled)
        if (students.isEmpty() || planRows.isEmpty()) return emptyList()
        val phaseRows = planPhases(forceRefresh = forceRefresh)
        val phaseStates = planPhaseStates(forceRefresh = forceRefresh)
        val phasesByPlan = phaseRows.groupBy { it.planId }
        val phaseStatesByStudent = phaseStates.groupBy { it.studentId }

        val enginePlansById = planRows.mapNotNull { row -> row.toEnginePlan()?.let { row.id to it } }.toMap()
        if (enginePlansById.isEmpty()) return emptyList()

        // RC11 needs the previous Sard anchor even when it belongs to the prior monthly plan.
        // Pull a bounded six-month history, then strictly scope progress to the active plan/phase below.
        val start = enginePlansById.values.minOf { it.startDate }.minusMonths(6).toString()
        val end = maxOf(today, enginePlansById.values.maxOf { it.endDate }).toString()
        val recitations = planRecitations(start, end, forceRefresh)
        // v2.30 Performance Core parity: filter once, then every plan works on the student's
        // short recitation list instead of rescanning thousands of rows per card.
        val recitationsByStudent = recitations.groupBy { it.studentId }
        val events = planEvents(forceRefresh)
        val rowsById = planRows.associateBy { it.id }

        val result = buildList {
            students.forEach { student ->
                val engineStudent = SmartPlanEngine.Student(
                    id = student.id,
                    halaqaId = student.halaqaId.orEmpty(),
                    categoryId = student.categoryId,
                    track = student.track,
                )
                val studentRecitations = recitationsByStudent[student.id].orEmpty()
                SmartPlanEngine.effectivePlans(enginePlansById.values.toList(), engineStudent, today)
                    .forEach { enginePlan ->
                        val row = rowsById[enginePlan.id] ?: return@forEach
                        val candidatePhaseRows = phasesByPlan[enginePlan.id].orEmpty().filter { ph ->
                            // RC15.1: a tathbit plan never runs hifz/sard phases (legacy rows from a track switch).
                            if (row.type == "tathbit") ph.phaseType == "tathbit" else ph.phaseType != "tathbit"
                        }
                        val candidatePhases = candidatePhaseRows.mapNotNull { it.toEnginePhase() }
                        val studentPhaseStates = phaseStatesByStudent[student.id].orEmpty()
                            .filter { state -> candidatePhaseRows.any { it.id == state.phaseId } }
                        val enginePhaseStates = studentPhaseStates.map { state ->
                            SmartPlanPhaseEngine.StudentState(
                                phaseId = state.phaseId,
                                studentId = state.studentId,
                                status = state.status,
                                targetPagesOverride = state.targetPagesOverride,
                                startDateOverride = state.startDateOverride?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                                endDateOverride = state.endDateOverride?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                                frozenCompletedPages = state.frozenCompletedPages,
                                frozenTargetPages = state.frozenTargetPages,
                                carriedShortfallPages = state.carryShortfallPages,
                                completionPercent = state.completionPercent,
                            )
                        }
                        val activePhaseEngine = SmartPlanPhaseEngine.effectiveActivePhase(candidatePhases, enginePhaseStates, student.id, today)
                        // RC19: only a genuinely active phase may drive the current card/assignment.
                        // contextPhase is useful for history, but using a future Sard phase as the
                        // current phase made October show “مرحلة السرد” before 23 October whenever
                        // a stale Hifz state was marked completed. Keep the parent plan visible instead.
                        val activePhase = activePhaseEngine?.let { active -> candidatePhaseRows.firstOrNull { it.id == active.id } }
                        val activePhaseState = activePhase?.let { phase -> studentPhaseStates.firstOrNull { it.phaseId == phase.id } }
                        val (phaseRowForUi, phaseAwareEnginePlan) = phaseAwarePlan(
                            row, enginePlan, activePhase, activePhaseState, activePhaseEngine,
                            studentRecitations = studentRecitations,
                        )
                        val normalizedRow = phaseRowForUi.copy(
                            settings = phaseRowForUi.settings.copy(
                                memorizationLineKeys = phaseAwareEnginePlan.settings.memorizationLineKeys,
                                routeDirection = phaseAwareEnginePlan.settings.routeDirection,
                            ),
                        )
                        val progressRecitations = studentRecitations.filter { rec ->
                            val inEffectiveDates = rec.date >= phaseAwareEnginePlan.startDate && rec.date <= phaseAwareEnginePlan.endDate
                            when {
                                activePhase != null && rec.taggedPhaseId == activePhase.id -> inEffectiveDates // phase_id owns the record, but never bypasses this month's phase dates
                                activePhase != null && rec.taggedPhaseId == null -> inEffectiveDates && recitationMatchesPlanType(phaseAwareEnginePlan.planType, rec.activityType)
                                activePhase != null -> false
                                else -> inEffectiveDates && recitationMatchesPlanType(phaseAwareEnginePlan.planType, rec.activityType)
                            }
                        }.map { rec ->
                            // RC12.1: a matching phase_id is authoritative. Legacy/imported JSON may
                            // carry a null/stale smart_plan_id; do not let that shadow the real phase FK.
                            if (activePhase != null && rec.taggedPhaseId == activePhase.id) rec.copy(taggedPlanId = phaseAwareEnginePlan.id) else rec
                        }
                        val planEvents = events[enginePlan.id].orEmpty()
                        val rawProgress = SmartPlanEngine.calculateProgress(
                            plan = phaseAwareEnginePlan,
                            studentId = student.id,
                            recitations = progressRecitations,
                            events = planEvents,
                            today = today,
                        )
                        val phaseProgress = activePhaseEngine?.let { phase ->
                            val state = enginePhaseStates.firstOrNull { it.phaseId == phase.id }
                            SmartPlanPhaseEngine.progress(phase, rawProgress.completed, today, state)
                        }
                        // Activity anchors intentionally see bounded history so Sard can resume from the
                        // previous monthly Sard anchor without counting that history toward this phase.
                        // RC11 phaseProgress is passed into Sard assignment generation so the actual ward,
                        // not only the UI hint, follows equal/flexible rebalancing.
                        val activities = activitySnapshotsForPlan(
                            normalizedRow,
                            phaseAwareEnginePlan,
                            student,
                            studentRecitations,
                            rawProgress,
                            today,
                            activePhase,
                            activePhaseState,
                            phaseProgress,
                        )
                        val hifzActivity = activities.firstOrNull { it.key == "hifz" }
                        val sardActivity = activities.firstOrNull { it.key == "sard" }
                        val progress = when {
                            phaseAwareEnginePlan.planType == "hifz" && rawProgress.assignment.kind == "hifz" && hifzActivity?.assignment != null -> rawProgress.copy(assignment = hifzActivity.assignment)
                            phaseAwareEnginePlan.planType == "sard" && sardActivity?.assignment != null -> rawProgress.copy(assignment = sardActivity.assignment)
                            else -> rawProgress
                        }.let { p ->
                            // RC52: today's ward must never start at or before the last passing recitation's end
                            // (Rabah recited 21:90-99 yet the ward read 21:91-101).
                            guardAssignmentAfterLastRecitation(normalizedRow, phaseAwareEnginePlan, studentRecitations, p, today)
                        }
                        val last = when (phaseAwareEnginePlan.planType) {
                            "hifz" -> hifzActivity?.lastRecitation
                            "sard" -> sardActivity?.lastRecitation
                            else -> progressRecitations
                                .asSequence()
                                .filter { recitationMatchesPlanType(phaseAwareEnginePlan.planType, it.activityType) }
                                .filter { it.date <= today }
                                .maxWithOrNull(compareBy<SmartPlanEngine.Recitation> { it.date }.thenBy { it.createdAt })
                        }
                        val week = smartPlanWeekRequirement(phaseAwareEnginePlan, progress, today)
                        val next = when (phaseAwareEnginePlan.planType) {
                            "hifz" -> hifzActivity?.nextDuty
                            "sard" -> sardActivity?.nextDuty
                            else -> smartPlanNextDuty(phaseAwareEnginePlan, student.id, studentRecitations, planEvents, today)
                        }
                        val weeklyTargetOverride = when (phaseAwareEnginePlan.planType) {
                            // RC44: the weekly tile of a hifz plan comes from the plan itself, not from the student weekly target.
                            "hifz" -> null
                            "review", "tathbit" -> student.targetReviewPagesWeekly
                            else -> null
                        }
                        val coreState = SmartPlanCoreStateEngine.build(
                            plan = phaseAwareEnginePlan,
                            progress = progress,
                            recitations = progressRecitations,
                            today = today,
                            weeklyTargetOverride = weeklyTargetOverride,
                        )
                        add(PlanSnapshot(
                            plan = normalizedRow,
                            student = student,
                            progress = progress,
                            phase = activePhase,
                            phaseState = activePhaseState,
                            phaseProgress = phaseProgress,
                            lastRecitation = last,
                            weekRequirement = week,
                            nextDuty = next,
                            activities = activities,
                            coreState = coreState,
                        ))
                    }
            }
        }.sortedWith(
            compareBy<PlanSnapshot> { healthPriority(it.progress.health) }
                .thenByDescending { it.progress.behindBy }
                .thenBy { it.student.fullName },
        )
        planSnapshotMemoryDate = today
        planSnapshotMemoryAt = System.currentTimeMillis()
        planSnapshotMemory = result
        return result
    }

    /**
     * RC52: a hifz ward that begins at or before the end of the student's latest passing hifz recitation is stale
     * (engine line coverage vs. real recitation ranges can disagree). Re-anchor it right after that recitation.
     */
    private fun guardAssignmentAfterLastRecitation(
        row: PlanRow,
        plan: SmartPlanEngine.Plan,
        recitations: List<SmartPlanEngine.Recitation>,
        progress: SmartPlanEngine.Progress,
        today: LocalDate,
    ): SmartPlanEngine.Progress {
        val a = progress.assignment
        if (plan.planType != "hifz" || a.kind != "hifz" || a.blockedByRetry || a.lineKeys.isEmpty() || a.pages <= 0.0) return progress
        return runCatching {
            val passing = recitations.filter { it.date <= today && SmartPlanEngine.isPassing(it, plan) }
            val last = SmartPlanActivityRules.latestAnchor(passing, "hifz") ?: return@runCatching progress
            val lastEnd = QuranLocation(last.endSurah ?: return@runCatching progress, last.endAyah ?: return@runCatching progress)
            val (start, _) = QuranCore.engine().endpointsForLineKeys(a.lineKeys) ?: return@runCatching progress
            val route = SmartPlanActivityRules.routeFor("hifz", row.type, row.settings.routeDirection, row.settings.sardRouteDirection)
            val reverse = route == "reverse_surahs_forward_ayahs"
            fun key(l: QuranLocation): Long = (if (reverse) (200 - l.surah) else l.surah).toLong() * 1000L + l.ayah
            if (key(start) > key(lastEnd)) return@runCatching progress
            val fixed = activityAssignmentFromAnchor(
                row = row, plan = plan, recitations = passing, activity = "hifz", requestedPages = a.pages,
            ) ?: return@runCatching progress
            progress.copy(assignment = fixed)
        }.getOrDefault(progress)
    }

    private fun recitationMatchesPlanType(planType: String, activityType: String): Boolean = when (planType) {
        "hifz" -> activityType in setOf("new_hifz", "hifz")
        "review" -> activityType in setOf("review", "review_near", "review_far")
        "sard" -> activityType == "sard"
        "tathbit" -> activityType in setOf("tathbit", "review_far")
        else -> false
    }

    private fun smartPlanWeekRequirement(
        plan: SmartPlanEngine.Plan,
        progress: SmartPlanEngine.Progress,
        today: LocalDate,
    ): PlanWeekRequirement {
        val weekEnd = today.plusDays(6)
        val weekDays = SmartPlanEngine.planDates(plan, today, weekEnd).size
        val futureDays = SmartPlanEngine.planDates(plan, today, plan.endDate).size
        var amount = min(
            progress.remaining,
            weekDays * progress.originalDaily + if (futureDays > 0) progress.deficit * weekDays / futureDays else 0.0,
        )
        if (plan.rangeStartPage != null && amount > 0.0) amount = min(progress.remaining, ceil(amount))
        val startPage = if (progress.assignment.blockedByRetry || progress.assignment.milestone != null) {
            progress.assignment.startPage
        } else progress.nextPage
        val endPage = if (startPage != null && amount > 0.0) {
            val steps = maxOf(1, ceil(amount).toInt()) - 1
            val descendingPages = plan.rangeStartPage != null && plan.rangeEndPage != null && plan.rangeStartPage > plan.rangeEndPage
            if (descendingPages) maxOf(plan.rangeEndPage ?: 1, startPage - steps)
            else min(plan.rangeEndPage ?: 604, startPage + steps)
        } else null
        return PlanWeekRequirement(SmartPlanEngine.round(amount), startPage, endPage)
    }

    private fun smartPlanNextDuty(
        plan: SmartPlanEngine.Plan,
        studentId: String,
        recitations: List<SmartPlanEngine.Recitation>,
        events: List<SmartPlanEngine.Event>,
        today: LocalDate,
    ): PlanNextDuty? {
        val candidates = SmartPlanEngine.planDates(plan, today.plusDays(1), plan.endDate).take(4)
        for (date in candidates) {
            val progress = SmartPlanEngine.calculateProgress(plan, studentId, recitations, events, date)
            val assignment = progress.assignment
            val milestone = assignment.milestone
            if (milestone != null) {
                // RC8.3: the milestone/test remains visible as its own requirement, while
                // "next duty" must tell the teacher what Quran range follows after approval.
                val afterApproval = SmartPlanEngine.calculateProgress(
                    plan = plan,
                    studentId = studentId,
                    recitations = recitations,
                    events = events + SmartPlanEngine.Event(type = "milestone_approved", milestoneKey = milestone.key),
                    today = date,
                ).assignment
                if (afterApproval.pages > 0.001 || afterApproval.blockedByRetry || afterApproval.lineKeys.isNotEmpty()) {
                    return PlanNextDuty(date, afterApproval)
                }
                continue
            }
            if (progress.dailyTarget > 0.001 || assignment.pages > 0.001 || assignment.blockedByRetry || assignment.lineKeys.isNotEmpty()) {
                return PlanNextDuty(date, assignment)
            }
        }
        return null
    }

    private fun planRecitations(from: String, to: String, forceRefresh: Boolean = false): List<SmartPlanEngine.Recitation> {
        val owner = currentUserId()
        var arr: JSONArray? = null
        if (!forceRefresh && owner != null) {
            arr = cache.get(owner, CACHE_PLAN_RECITATIONS, maxAge = null)?.let { runCatching { JSONArray(it) }.getOrNull() }
        }
        val planRecCols = "select=id,session_id,student_id,phase_id,activity_type,start_surah,start_ayah,end_surah,end_ayah,start_page,end_page,pages_count,evaluation,mistakes,prompts," +
            "selection_data,created_at,sessions!inner(session_date)"
        // RC27.39: the cached history never expired, so recitations saved by another teacher/device (or already pushed from
        // this phone) did not show on the Smart Plan until the next full refresh. Pull only the last 3 days and merge by id.
        if (!forceRefresh && owner != null && arr != null && online() && System.currentTimeMillis() - planRecDeltaAt > 30_000L) {
            val since = java.time.Instant.now().minus(java.time.Duration.ofDays(3)).toString()
            val delta = runCatching {
                withAuthRetry {
                    api.select(
                        "recitation_entries",
                        "$planRecCols&created_at=gte.$since&sessions.session_date=gte.$from&sessions.session_date=lte.$to&order=created_at.asc&limit=3000",
                    )
                }
            }.getOrNull()
            if (delta != null) {
                planRecDeltaAt = System.currentTimeMillis()
                val byRowId = linkedMapOf<String, JSONObject>()
                val base = arr ?: JSONArray()
                for (i in 0 until base.length()) base.optJSONObject(i)?.let { byRowId[it.optString("id")] = it }
                for (i in 0 until delta.length()) delta.optJSONObject(i)?.let { byRowId[it.optString("id")] = it }
                val merged = JSONArray()
                byRowId.values.forEach { merged.put(it) }
                arr = merged
                cache.put(owner, CACHE_PLAN_RECITATIONS, merged.toString())
            }
        }
        if (arr == null && online()) {
            arr = runCatching {
                val query = "$planRecCols&sessions.session_date=gte.$from&sessions.session_date=lte.$to&order=created_at.asc&limit=10000"
                withAuthRetry { api.select("recitation_entries", query) }
            }.getOrNull()
            if (arr != null && owner != null) cache.put(owner, CACHE_PLAN_RECITATIONS, arr.toString())
        }
        if (arr == null && owner != null) {
            arr = cache.get(owner, CACHE_PLAN_RECITATIONS, maxAge = null)?.let { runCatching { JSONArray(it) }.getOrNull() }
        }
        val sessionsById = owner?.let { cachedSessions(it, maxAge = null) }.orEmpty().associateBy { it.id }
        val byId = linkedMapOf<String, SmartPlanEngine.Recitation>()
        fun toEngine(row: JSONObject, fallbackDate: String? = null): SmartPlanEngine.Recitation? {
            val dateText = row.optJSONObject("sessions")?.optString("session_date")
                ?.takeIf { it.isNotBlank() }
                ?: fallbackDate
                ?: row.optString("smart_plan_date").takeIf { it.isNotBlank() }
                ?: return null
            val date = runCatching { LocalDate.parse(dateText) }.getOrNull() ?: return null
            val selection = row.optJSONObject("selection_data") ?: JSONObject()
            val lines = selection.optJSONArray("qcf_line_keys")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty()
            return SmartPlanEngine.Recitation(
                id = row.optString("id"),
                studentId = row.optString("student_id"),
                activityType = row.optString("activity_type"),
                date = date,
                startPage = nullablePage(row, "start_page"),
                endPage = nullablePage(row, "end_page"),
                startSurah = if (!row.has("start_surah") || row.isNull("start_surah")) null else row.optInt("start_surah").takeIf { it in 1..114 },
                startAyah = if (!row.has("start_ayah") || row.isNull("start_ayah")) null else row.optInt("start_ayah").takeIf { it > 0 },
                endSurah = if (!row.has("end_surah") || row.isNull("end_surah")) null else row.optInt("end_surah").takeIf { it in 1..114 },
                endAyah = if (!row.has("end_ayah") || row.isNull("end_ayah")) null else row.optInt("end_ayah").takeIf { it > 0 },
                pagesCount = com.mohammedsamir.halaqati.quran.RecitationParityRules.effectivePages(row.optDouble("pages_count", 0.0), lines),
                evaluation = row.optString("evaluation").ifBlank { null },
                mistakes = row.optInt("mistakes", 0),
                prompts = row.optInt("prompts", 0),
                taggedPlanId = selection.optNullableString("smart_plan_id")
                    ?: selection.optNullableString("plan_id"),
                taggedPhaseId = selection.optNullableString("smart_plan_phase_id")
                    ?: row.optNullableString("phase_id"),
                qcfLineKeys = lines,
                createdAt = row.optString("created_at"),
            )
        }
        val source = arr ?: JSONArray()
        for (index in 0 until source.length()) {
            val row = source.optJSONObject(index) ?: continue
            val rec = toEngine(row) ?: continue
            if (rec.id.isNotBlank()) byId[rec.id] = rec
        }
        // Overlay local outbox mutations so Smart Plans rebuild from the same history the
        // teacher currently sees, even before the server round-trip succeeds.
        queueSnapshot(1500).forEach { item ->
            val dedupe = item.dedupe.orEmpty()
            if (dedupe.startsWith("recitation-delete:")) {
                val recitationId = dedupe.substringAfterLast(':')
                if (recitationId.isNotBlank()) byId.remove(recitationId)
                return@forEach
            }
            if (!dedupe.startsWith("recitation:")) return@forEach
            val body = item.body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@forEach
            val sessionDate = sessionsById[body.optString("session_id")]?.date
            val rec = toEngine(body, sessionDate) ?: return@forEach
            if (rec.date.toString() < from || rec.date.toString() > to) return@forEach
            if (rec.id.isNotBlank()) byId[rec.id] = rec
        }
        return byId.values.sortedWith(compareBy<SmartPlanEngine.Recitation> { it.date }.thenBy { it.createdAt })
    }

    private fun cachedPlanEvents(owner: String): JSONArray? =
        cache.get(owner, CACHE_PLAN_EVENTS, maxAge = null)?.let { runCatching { JSONArray(it) }.getOrNull() }

    private fun planEvents(forceRefresh: Boolean = false): Map<String, List<SmartPlanEngine.Event>> {
        val owner = currentUserId()
        var arr = if (!forceRefresh) owner?.let(::cachedPlanEvents) ?: JSONArray() else JSONArray()
        if ((forceRefresh || arr.length() == 0) && online()) {
            runCatching {
                withAuthRetry {
                    api.select(
                        "smart_plan_events",
                        "select=client_event_id,plan_id,phase_id,student_id,event_type,payload,event_date,created_at&order=created_at.asc&limit=5000",
                    )
                }
            }.onSuccess { remote ->
                arr = remote
                owner?.let { cache.put(it, CACHE_PLAN_EVENTS, remote.toString()) }
            }
        }

        val rowsByIdentity = linkedMapOf<String, JSONObject>()
        fun addRow(row: JSONObject) {
            val planId = row.optString("plan_id")
            if (planId.isBlank()) return
            val payload = row.optJSONObject("payload") ?: JSONObject()
            val milestone = payload.optString("milestone_key").ifBlank { payload.optString("key") }
            val identity = row.optString("client_event_id").ifBlank {
                listOf(planId, row.optString("student_id"), row.optString("event_type"), milestone, row.optString("event_date")).joinToString("|")
            }
            rowsByIdentity[identity] = row
        }
        for (index in 0 until arr.length()) arr.optJSONObject(index)?.let(::addRow)
        queueSnapshot(2000).forEach { item ->
            if (!item.dedupe.orEmpty().startsWith("plan-event:")) return@forEach
            item.body?.let { runCatching { JSONObject(it) }.getOrNull() }?.let(::addRow)
        }

        val output = linkedMapOf<String, MutableList<SmartPlanEngine.Event>>()
        rowsByIdentity.values.forEach { row ->
            val planId = row.optString("plan_id")
            val payload = row.optJSONObject("payload") ?: JSONObject()
            val pages = when {
                payload.has("pages") -> payload.optDouble("pages", 0.0)
                payload.has("delta_pages") -> payload.optDouble("delta_pages", 0.0)
                payload.has("value") -> payload.optDouble("value", 0.0)
                else -> 0.0
            }
            val milestone = payload.optString("milestone_key").ifBlank { payload.optString("key") }.ifBlank { null }
            output.getOrPut(planId) { mutableListOf() } += SmartPlanEngine.Event(
                type = row.optString("event_type"),
                pages = pages,
                milestoneKey = milestone,
                date = row.optString("event_date").takeIf { it.isNotBlank() }?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() },
            )
        }
        return output
    }

    private fun defaultPlanRouteDirection(planType: String): String = when (planType) {
        "hifz" -> "reverse_surahs_forward_ayahs" // الناس إلى البقرة
        "tathbit", "review" -> "forward_surahs_forward_ayahs" // البقرة إلى الناس
        else -> "forward_surahs_forward_ayahs"
    }

    private fun planTraversalDirection(routeDirection: String?): QuranTraversalDirection = when (routeDirection) {
        "reverse_surahs_forward_ayahs" -> QuranTraversalDirection.REVERSE_SURAH_ORDER
        "forward_surahs_forward_ayahs" -> QuranTraversalDirection.FORWARD_QURAN
        else -> QuranTraversalDirection.AUTO_BY_ACTIVITY
    }

    private fun canonicalPlanLineKeys(row: PlanRow): List<String> {
        val stored = row.settings.memorizationLineKeys
        // RC43: a tathbit plan rolled into a new month by the server has a start location but no range end or line list.
        // Derive the exact lines from that start along the plan's route for the plan's page target (in memory only).
        if (stored.isEmpty() && row.type == "tathbit" && row.rangeStartSurah != null && row.rangeStartAyah != null &&
            row.rangeEndSurah == null && row.target > 0.0
        ) {
            val route0 = row.settings.routeDirection ?: defaultPlanRouteDirection(row.type)
            return runCatching {
                QuranCore.engine().previewPagesFrom(
                    QuranLocation(row.rangeStartSurah, row.rangeStartAyah), row.target, "tathbit", "tathbit", planTraversalDirection(route0),
                ).qcfLineKeys
            }.getOrElse { stored }
        }
        val startSurah = row.rangeStartSurah ?: return stored
        val startAyah = row.rangeStartAyah ?: return stored
        val endSurah = row.rangeEndSurah ?: return stored
        val endAyah = row.rangeEndAyah ?: return stored
        val route = row.settings.routeDirection ?: defaultPlanRouteDirection(row.type)
        val track = if (row.type == "hifz") "general_hifz" else "tathbit"
        val activity = when (row.type) {
            "hifz" -> "new_hifz"
            "sard" -> "sard"
            "tathbit" -> "tathbit"
            else -> "review_near"
        }
        return runCatching {
            QuranCore.engine().calculate(
                QuranRangeRequest(
                    track = track,
                    activityType = activity,
                    selection = QuranSelection.Ayahs(
                        QuranLocation(startSurah, startAyah),
                        QuranLocation(endSurah, endAyah),
                    ),
                    direction = planTraversalDirection(route),
                ),
            ).qcfLineKeys
        }.getOrElse { stored }
    }

    private fun PlanRow.toEnginePlan(): SmartPlanEngine.Plan? {
        val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return null
        val end = runCatching { LocalDate.parse(endDate) }.getOrNull() ?: return null
        if (id.isBlank() || halaqaId.isBlank() || target <= 0.0 || end < start) return null
        val pauses = settings.pauseRanges.mapNotNull { (fromRaw, toRaw) ->
            val from = runCatching { LocalDate.parse(fromRaw) }.getOrNull() ?: return@mapNotNull null
            val to = runCatching { LocalDate.parse(toRaw) }.getOrNull() ?: return@mapNotNull null
            if (to < from) null else SmartPlanEngine.PauseRange(from, to)
        }
        val weekdays = scheduleWeekdays.filter { it in 0..6 }.toSet().ifEmpty { setOf(0, 1, 2, 3, 4) }
        val effectiveRouteDirection = settings.routeDirection ?: defaultPlanRouteDirection(type)
        val canonicalLineKeys = canonicalPlanLineKeys(this)
        return SmartPlanEngine.Plan(
            id = id,
            scopeType = scopeType,
            halaqaId = halaqaId,
            studentId = studentId,
            categoryId = categoryId,
            parentPlanId = parentPlanId,
            planType = type,
            targetPages = target,
            startDate = start,
            endDate = end,
            scheduleWeekdays = weekdays,
            catchUpWeekday = catchUpWeekday,
            maxDailyMultiplier = maxDailyMultiplier.coerceIn(1.0, 3.0),
            autoExtend = autoExtend,
            qualityGateEnabled = qualityGateEnabled,
            minimumEvaluation = minimumEvaluation,
            rangeStartPage = rangeStartPage,
            rangeEndPage = rangeEndPage,
            milestoneMode = milestoneMode,
            milestoneRequiresApproval = milestoneRequiresApproval,
            delayAlertDays = delayAlertDays,
            status = status,
            updatedAt = updatedAt,
            createdAt = createdAt,
            settings = SmartPlanEngine.Settings(
                minimumDailyMode = settings.minimumDailyMode,
                minimumDailyPages = settings.minimumDailyPages,
                pauseRanges = pauses,
                intensiveWeekFrom = settings.intensiveWeekFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                intensiveWeekTo = settings.intensiveWeekTo?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                intensiveWeekdays = settings.intensiveWeekdays.filter { it in 0..6 }.toSet(),
                memorizationLineKeys = canonicalLineKeys,
                routeDirection = effectiveRouteDirection,
            ),
        )
    }


    private fun SmartPlanPhaseRow.toEnginePhase(): SmartPlanPhaseEngine.Phase? {
        val start = runCatching { LocalDate.parse(startDate) }.getOrNull() ?: return null
        val end = runCatching { LocalDate.parse(endDate) }.getOrNull() ?: return null
        if (id.isBlank() || planId.isBlank() || end < start) return null
        return SmartPlanPhaseEngine.Phase(
            id = id,
            planId = planId,
            order = phaseOrder,
            type = phaseType,
            startDate = start,
            endDate = end,
            scheduleWeekdays = scheduleWeekdays.filter { it in 0..6 }.toSet().ifEmpty { setOf(0,1,2,3,4) },
            goalMode = goalMode,
            targetPages = targetPages,
            targetUnits = targetUnits,
            goalUnit = goalUnit,
            distributionMode = distributionMode,
            startMode = startMode,
            rangeStartPage = rangeStartPage,
            rangeEndPage = rangeEndPage,
            rangeStartSurah = rangeStartSurah,
            rangeStartAyah = rangeStartAyah,
            rangeEndSurah = rangeEndSurah,
            rangeEndAyah = rangeEndAyah,
            allowSurplus = allowSurplus,
            status = status,
        )
    }

    private fun phaseAwarePlan(
        baseRow: PlanRow,
        basePlan: SmartPlanEngine.Plan,
        phaseRow: SmartPlanPhaseRow?,
        phaseState: SmartPlanPhaseStateRow?,
        phaseEngineOverride: SmartPlanPhaseEngine.Phase? = null,
        studentRecitations: List<SmartPlanEngine.Recitation> = emptyList(),
    ): Pair<PlanRow, SmartPlanEngine.Plan> {
        val phase = phaseEngineOverride ?: phaseRow?.toEnginePhase() ?: return baseRow to basePlan
        val state = phaseState?.let {
            SmartPlanPhaseEngine.StudentState(
                phaseId = it.phaseId,
                studentId = it.studentId,
                status = it.status,
                targetPagesOverride = it.targetPagesOverride,
                startDateOverride = it.startDateOverride?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() },
                endDateOverride = it.endDateOverride?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() },
                frozenCompletedPages = it.frozenCompletedPages,
                frozenTargetPages = it.frozenTargetPages,
                carriedShortfallPages = it.carryShortfallPages,
                completionPercent = it.completionPercent,
            )
        }
        val resolvedTarget = SmartPlanPhaseEngine.resolvedTargetPages(phase, state)
        val effectiveTarget = ((resolvedTarget ?: 0.0) + (phaseState?.carryShortfallPages ?: 0.0)).coerceAtLeast(0.0)
        val phaseRoute = if (phase.type == "sard") {
            phaseRow?.settings?.optString("route_direction")?.takeIf { it in setOf(
                SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH,
                SmartPlanPhaseEngine.ROUTE_FROM_BAQARAH_TO_NAS,
            ) } ?: baseRow.settings.sardRouteDirection
                ?: if (baseRow.type == "hifz") SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH else SmartPlanPhaseEngine.ROUTE_FROM_BAQARAH_TO_NAS
        } else basePlan.settings.routeDirection ?: defaultPlanRouteDirection(if (phase.type == "tathbit") "tathbit" else "hifz")

        fun configuredStart(): QuranLocation? = when {
            phase.rangeStartSurah != null && phase.rangeStartAyah != null -> QuranLocation(phase.rangeStartSurah, phase.rangeStartAyah)
            phase.rangeStartPage != null -> QuranMetadata.pageStart(phase.rangeStartPage)
            baseRow.rangeStartSurah != null && baseRow.rangeStartAyah != null -> QuranLocation(baseRow.rangeStartSurah, baseRow.rangeStartAyah)
            baseRow.rangeStartPage != null -> QuranMetadata.pageStart(baseRow.rangeStartPage)
            else -> null
        }
        fun phaseExplicitEnd(): QuranLocation? = when {
            phase.rangeEndSurah != null && phase.rangeEndAyah != null -> QuranLocation(phase.rangeEndSurah, phase.rangeEndAyah)
            phase.rangeEndPage != null -> QuranMetadata.pageEnd(phase.rangeEndPage)
            else -> null
        }
        fun parentTerminal(): QuranLocation? = when {
            baseRow.rangeEndSurah != null && baseRow.rangeEndAyah != null -> QuranLocation(baseRow.rangeEndSurah, baseRow.rangeEndAyah)
            baseRow.rangeEndPage != null -> QuranMetadata.pageEnd(baseRow.rangeEndPage)
            else -> null
        }

        // A phase that starts from "last activity" resolves the anchor from accepted history
        // BEFORE the phase starts, so the start does not drift forward on every refresh.
        val historicalStart = if (SmartPlanPhaseEngine.usesHistoricalActivityAnchor(phase)) {
            val activity = when (phase.type) {
                "sard" -> "sard"
                "tathbit" -> "tathbit"
                else -> "hifz"
            }
            val accepted = studentRecitations
                .asSequence()
                .filter { it.date < phase.startDate }
                .filter { SmartPlanEngine.isPassing(it, basePlan) }
                .toList()
            SmartPlanActivityRules.latestAnchor(accepted, activity)?.let { last ->
                val surah = last.endSurah
                val ayah = last.endAyah
                if (surah != null && ayah != null) SmartPlanActivityRules.nextLocation(QuranLocation(surah, ayah), phaseRoute) else null
            }
        } else null
        // RC34: a Sard phase that starts "from the last activity" but has no earlier Sard to continue from (first Sard, or the
        // only earlier one was saved without a range) and no range of its own must not fall back to the parent's Hifz range.
        // That fallback made every Sard the student actually recited look "outside the plan" and count 0 pages.
        val sardWithoutRange = phase.type == "sard" && historicalStart == null &&
            phase.startMode in setOf(SmartPlanPhaseEngine.START_LAST_SARD, SmartPlanPhaseEngine.START_LAST_ACTIVITY) &&
            phase.rangeStartSurah == null && phase.rangeStartAyah == null && phase.rangeStartPage == null &&
            phase.rangeEndSurah == null && phase.rangeEndAyah == null && phase.rangeEndPage == null
        val resolvedStart = if (sardWithoutRange) null else historicalStart ?: configuredStart()
        val explicitEnd = phaseExplicitEnd()
        val hardTerminal = if (sardWithoutRange) null else explicitEnd ?: parentTerminal()
        val activityType = SmartPlanActivityRules.activityTypeFor(when (phase.type) {
            "sard" -> "sard"
            "tathbit" -> "tathbit"
            else -> "hifz"
        })
        val track = if (phase.type == "tathbit") "tathbit" else "general_hifz"
        val traversal = planTraversalDirection(phaseRoute)

        fun reachableTerminal(start: QuranLocation, terminal: QuranLocation): Boolean = when (traversal) {
            QuranTraversalDirection.FORWARD_QURAN -> QuranMetadata.globalAyah(start.surah, start.ayah) <= QuranMetadata.globalAyah(terminal.surah, terminal.ayah)
            QuranTraversalDirection.REVERSE_SURAH_ORDER -> start.surah > terminal.surah || (start.surah == terminal.surah && start.ayah <= terminal.ayah)
            QuranTraversalDirection.AUTO_BY_ACTIVITY -> true
        }
        fun passesTerminal(candidate: QuranLocation, terminal: QuranLocation): Boolean = when (traversal) {
            QuranTraversalDirection.FORWARD_QURAN -> QuranMetadata.globalAyah(candidate.surah, candidate.ayah) > QuranMetadata.globalAyah(terminal.surah, terminal.ayah)
            QuranTraversalDirection.REVERSE_SURAH_ORDER -> candidate.surah < terminal.surah || (candidate.surah == terminal.surah && candidate.ayah > terminal.ayah)
            QuranTraversalDirection.AUTO_BY_ACTIVITY -> false
        }
        fun exactRange(start: QuranLocation, end: QuranLocation) = QuranCore.engine().calculate(
            QuranRangeRequest(
                track = track,
                activityType = activityType,
                selection = QuranSelection.Ayahs(start, end),
                direction = traversal,
            )
        )

        val generatedRange = when {
            phase.type != "hifz" || resolvedStart == null -> null
            phase.goalMode == "quran_range" && explicitEnd != null -> runCatching { exactRange(resolvedStart, explicitEnd) }.getOrNull()
            effectiveTarget > 0.001 -> runCatching {
                val preview = QuranCore.engine().previewPagesFrom(resolvedStart, effectiveTarget, track, activityType, traversal)
                // RC56: a pages goal is not cut short by the PARENT plan's old range end. Only an explicit end on the phase itself
                // clamps it; otherwise a student whose anchor sat near last month's terminal got a 3-page range for a 30-page goal.
                val terminal = explicitEnd
                val previewEnd = preview.end
                if (terminal != null && previewEnd != null && reachableTerminal(resolvedStart, terminal) && passesTerminal(previewEnd, terminal)) {
                    exactRange(resolvedStart, terminal)
                } else preview
            }.getOrNull()
            else -> null
        }
        // RC17.1 BF3: an explicit/continuity Hifz phase owns its line keys. It no longer
        // silently keeps the parent plan's old memorization_line_keys.
        val phaseLineKeys = when {
            phase.type != "hifz" -> emptyList()
            generatedRange != null -> generatedRange.qcfLineKeys
            phase.startMode == SmartPlanPhaseEngine.START_PLAN && phase.rangeStartPage == null && phase.rangeStartSurah == null -> basePlan.settings.memorizationLineKeys
            else -> emptyList()
        }
        val resolvedStartPage = resolvedStart?.let { QuranMetadata.pageFor(it.surah, it.ayah) }
        val fallbackEnd = if (phase.type == "hifz") explicitEnd else hardTerminal
        val resolvedEndPage = generatedRange?.endPage ?: fallbackEnd?.let { QuranMetadata.pageFor(it.surah, it.ayah) }
        val effectiveRangeStartPage = generatedRange?.startPage ?: resolvedStartPage ?: phase.rangeStartPage ?: if (phase.type == "hifz") baseRow.rangeStartPage else null
        val effectiveRangeEndPage = resolvedEndPage ?: phase.rangeEndPage ?: if (phase.type == "hifz" && phase.goalMode == "quran_range") baseRow.rangeEndPage else null
        val effectiveRangeStartSurah = generatedRange?.start?.surah ?: resolvedStart?.surah ?: phase.rangeStartSurah ?: if (phase.type == "hifz") baseRow.rangeStartSurah else null
        val effectiveRangeStartAyah = generatedRange?.start?.ayah ?: resolvedStart?.ayah ?: phase.rangeStartAyah ?: if (phase.type == "hifz") baseRow.rangeStartAyah else null
        val effectiveRangeEndSurah = generatedRange?.end?.surah ?: fallbackEnd?.surah ?: phase.rangeEndSurah ?: if (phase.type == "hifz" && phase.goalMode == "quran_range") baseRow.rangeEndSurah else null
        val effectiveRangeEndAyah = generatedRange?.end?.ayah ?: fallbackEnd?.ayah ?: phase.rangeEndAyah ?: if (phase.type == "hifz" && phase.goalMode == "quran_range") baseRow.rangeEndAyah else null

        val phaseRowForUi = baseRow.copy(
            type = phase.type,
            target = effectiveTarget,
            startDate = phase.startDate.toString(),
            endDate = phase.endDate.toString(),
            title = phaseRow?.title?.takeIf { it.isNotBlank() } ?: baseRow.title,
            rangeStartPage = effectiveRangeStartPage,
            rangeEndPage = effectiveRangeEndPage,
            rangeStartSurah = effectiveRangeStartSurah,
            rangeStartAyah = effectiveRangeStartAyah,
            rangeEndSurah = effectiveRangeEndSurah,
            rangeEndAyah = effectiveRangeEndAyah,
            scheduleWeekdays = phase.scheduleWeekdays.toList(),
            settings = baseRow.settings.copy(
                memorizationLineKeys = phaseLineKeys,
                routeDirection = phaseRoute,
                sardRouteDirection = if (phase.type == "sard") phaseRoute else baseRow.settings.sardRouteDirection,
                enabledActivities = listOf(when (phase.type) { "sard" -> "sard"; "tathbit" -> "tathbit"; else -> "hifz" }),
                sardTargetPages = if (phase.type == "sard") effectiveTarget else baseRow.settings.sardTargetPages,
            ),
        )
        val engine = basePlan.copy(
            planType = phase.type,
            targetPages = effectiveTarget,
            startDate = phase.startDate,
            endDate = phase.endDate,
            scheduleWeekdays = phase.scheduleWeekdays,
            catchUpWeekday = null,
            rangeStartPage = phaseRowForUi.rangeStartPage,
            rangeEndPage = phaseRowForUi.rangeEndPage,
            milestoneMode = if (phase.type == "hifz") basePlan.milestoneMode else "none",
            milestoneRequiresApproval = phase.type == "hifz" && basePlan.milestoneRequiresApproval,
            settings = basePlan.settings.copy(
                memorizationLineKeys = phaseLineKeys,
                routeDirection = phaseRoute,
            ),
        )
        return phaseRowForUi to engine
    }

    private fun nullablePage(row: JSONObject, name: String): Int? =
        if (!row.has(name) || row.isNull(name)) null else row.optInt(name).takeIf { it in 1..604 }

    private fun healthPriority(value: String): Int = when (value) {
        SmartPlanHealthRules.RED -> 0
        SmartPlanHealthRules.YELLOW -> 1
        SmartPlanHealthRules.GREEN -> 2
        SmartPlanHealthRules.BLUE -> 3
        else -> 4
    }

    /**
     * Stale-while-revalidate table reader. Ordinary screen opens are local-first;
     * only an explicit refresh waits for the server. The background sync worker
     * refreshes these snapshots after connectivity returns.
     */
    fun table(
        table: String,
        query: String = "select=*&order=created_at.desc&limit=250",
        forceRefresh: Boolean = false,
        preferCache: Boolean = false,
    ): JSONArray {
        val owner = currentUserId() ?: return JSONArray()
        if (sessionCachesDirty && online()) {
            sessionCachesDirty = false
            invalidateSessionDerivedCaches()
        }
        if (table == "students") {
            Regex("(?:^|&)id=eq\\.([^&]+)").find(query)?.groupValues?.getOrNull(1)?.let { id ->
                studentRaw(id)?.let { return JSONArray().put(it) }
            }
        }
        val key = "table:$table:${query.hashCode()}"
        val cached = cache.get(owner, key, null)?.let { runCatching { JSONArray(it) }.getOrNull() }
        // RC27.18: tables derived from sessions go stale when someone else deletes or edits a session, so online reads of
        // them are reused for at most 90 seconds; offline the last copy is still served.
        val fresh = if (table in SESSION_DERIVED_TABLES && online()) {
            cache.get(owner, key, java.time.Duration.ofSeconds(90))?.let { runCatching { JSONArray(it) }.getOrNull() }
        } else if (online()) {
            // RC84: stored copies used to live forever while online, so a deleted halaqa, a re-assigned teacher or a changed
            // supervisor scope kept showing on that device. Online, a stored copy is now reused for at most 30 minutes.
            cache.get(owner, key, java.time.Duration.ofMinutes(30))?.let { runCatching { JSONArray(it) }.getOrNull() }
        } else cached
        if (!forceRefresh && fresh != null) {
            if (online()) scheduleSync()
            return fresh
        }
        // RC79: callers that follow the once-per-cycle gate (student profile) accept the stored copy even past the 90 s window.
        if (!forceRefresh && preferCache && cached != null) return cached
        if (!online()) return cached ?: JSONArray()
        return runCatching { withAuthRetry { api.select(table, query) } }
            .onSuccess { cache.put(owner, key, it.toString()) }
            .getOrElse { cached ?: JSONArray() }
    }

    /**
     * Management mutations that must be acknowledged by Supabase before the UI reports success.
     *
     * The ordinary write/patch/delete methods intentionally use the durable offline Outbox. That is
     * ideal for high-volume day-to-day work, but management screens used to close their dialogs and
     * immediately reload a stale table snapshot before the queued mutation had reached the server.
     * These confirmed variants keep administrative create/edit/delete actions honest: when online
     * they wait for PostgREST, surface RLS/validation failures to the dialog, and invalidate every
     * cached projection of the affected table.
     */
    private fun requireConfirmedMutationConnection() {
        require(online()) { "هذه العملية الإدارية تحتاج اتصالاً بالإنترنت حتى يتم تأكيد حفظها" }
    }

    private fun invalidateTableCache(table: String) {
        currentUserId()?.let { owner -> cache.invalidate(owner, "table:$table:") }
        signalLocalMutation()
    }

    /**
     * RC27.18: every read model that is built from sessions, attendance and recitations. Deleting, cancelling,
     * archiving or editing a session must drop ALL of them; before this, rankings and reports kept counting
     * a deleted session's attendance, grades and points from a cached copy that never expired.
     */
    @Volatile private var sessionCachesDirty = false

    private fun invalidateSessionDerivedCaches(dropPlanRecitations: Boolean = true) {
        // RC27.23: offline, the cached copy is the ONLY copy. Dropping it (RC27.18 did, on every queued write) left the
        // sessions, recitations, plans and reports empty until the network came back. Offline we keep the cache and
        // remember to drop it once we are online again.
        if (!online()) {
            sessionCachesDirty = true
            invalidatePlanSnapshotMemory()
            signalLocalMutation()
            return
        }
        currentUserId()?.let { owner ->
            SESSION_DERIVED_TABLES.forEach { cache.invalidate(owner, "table:$it:") }
            // RC27.23: the plan recitation cache also holds rows saved on this phone that the server has not received yet;
            // it is only dropped when a session was deleted/cancelled/archived/restored/edited, never on an ordinary save.
            if (dropPlanRecitations) cache.invalidate(owner, CACHE_PLAN_RECITATIONS)
            cache.invalidate(owner, "guardian:plan-history:")
            cache.invalidate(owner, "guardian:badges:")
            cache.invalidate(owner, CACHE_GUARDIAN_OVERVIEW_PREFIX)
            cache.invalidate(owner, CACHE_GUARDIAN_DASHBOARD_PREFIX)
            cache.invalidate(owner, CACHE_GUARDIAN_COMPETITION_PREFIX)
            cache.invalidate(owner, CACHE_REPORT_RECITATIONS_PREFIX)
            cache.invalidate(owner, CACHE_REPORT_SUMMARY_PREFIX)
        }
        invalidatePlanSnapshotMemory()
        signalLocalMutation()
    }

    fun writeConfirmed(table: String, obj: JSONObject, onConflict: String? = null): JSONArray {
        requireConfirmedMutationConnection()
        val result = withAuthRetry {
            if (onConflict.isNullOrBlank()) api.insert(table, obj) else api.upsert(table, obj, onConflict)
        }
        invalidateTableCache(table)
        return result
    }

    fun patchConfirmed(table: String, filter: String, obj: JSONObject): JSONArray {
        requireConfirmedMutationConnection()
        val result = withAuthRetry { api.patch(table, filter, obj) }
        val owner = currentUserId()
        if (table == "students" && owner != null) {
            val id = studentIdFromFilter(filter)
            val row = result.optJSONObject(0) ?: id?.let { runCatching { fetchServerStudent(it) }.getOrNull() }
            if (row != null) {
                upsertStudentLocal(owner, row, dirty = false)
                cache.put(owner, CACHE_STUDENTS, localStudentsJson(owner).toString())
            }
        }
        invalidateTableCache(table)
        return result
    }

    fun deleteConfirmed(table: String, filter: String): JSONArray {
        requireConfirmedMutationConnection()
        val result = withAuthRetry { api.delete(table, filter) }
        invalidateTableCache(table)
        return result
    }

    fun accountProfiles(forceRefresh: Boolean = false): JSONArray {
        val owner = currentUserId() ?: return JSONArray()
        val cached = cache.get(owner, CACHE_ACCOUNTS, null)?.let { runCatching { JSONArray(it) }.getOrNull() }
        if (!forceRefresh && cached != null) {
            if (online()) scheduleSync()
            return cached
        }
        if (!online()) return cached ?: JSONArray()
        return runCatching {
            withAuthRetry {
                val base = "select=id,full_name,email,phone,role,requested_role,approval_status,active,approved_at,rejection_reason,created_at"
                try {
                    // RC53: frozen / avatar_path exist only after the RC53 SQL; fall back to the old column list before that.
                    api.select("profiles", "$base,frozen,avatar_path&order=created_at.desc&limit=1000")
                } catch (e: SupabaseException) {
                    if (e.status == 400) api.select("profiles", "$base&order=created_at.desc&limit=1000") else throw e
                }
            }
        }.onSuccess { cache.put(owner, CACHE_ACCOUNTS, it.toString()) }
            .getOrElse { cached ?: JSONArray() }
    }

    private fun requireDirectAccountConnection() {
        require(online()) { "تعديل الحسابات يحتاج اتصالاً مباشراً بالخادم" }
    }

    fun approveAccount(userId: String, role: String) {
        requireDirectAccountConnection()
        require(role in setOf("guardian", "teacher", "supervisor", "observer")) { "الصلاحية المطلوبة غير صالحة" }
        val uid = store.load()?.userId ?: error("الجلسة غير صالحة")
        withAuthRetry {
            api.patch(
                "profiles",
                "id=eq.$userId",
                JSONObject()
                    .put("role", role)
                    .put("requested_role", role)
                    .put("approval_status", "approved")
                    .put("active", true)
                    .put("approved_at", java.time.Instant.now().toString())
                    .put("approved_by", uid)
                    .put("rejection_reason", JSONObject.NULL),
            )
        }
    }

    // RC32: observer role. All data comes from dedicated read-only RPCs (see migration 20261027_v2680).
    fun observerOverview(from: String, to: String): JSONObject {
        require(online()) { "المراقب يحتاج اتصالاً بالخادم" }
        val raw = withAuthRetry { api.rpc("rc32_observer_overview", JSONObject().put("p_from", from).put("p_to", to)) }
        return JSONObject(raw)
    }

    // ---- RC46: monthly honour awards (computed on the server, approved by the admin, shown to the donor). ----
    private fun jsonRpc(name: String, args: JSONObject): String = withAuthRetry { api.rpc(name, args) }

    fun awardMonths(): List<JSONObject> {
        require(online()) { "يلزم اتصال بالإنترنت" }
        val arr = JSONArray(jsonRpc("rc46_award_months", JSONObject()))
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    fun adminAwards(month: String, scope: String): JSONObject {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return JSONObject(jsonRpc("rc46_admin_awards", JSONObject().put("p_month", month).put("p_scope", scope)))
    }

    fun donorAwards(month: String, scope: String): JSONObject {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return JSONObject(jsonRpc("rc46_donor_awards", JSONObject().put("p_month", month).put("p_scope", scope)))
    }

    fun computeAwards(month: String): JSONObject {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return JSONObject(jsonRpc("rc46_compute_awards", JSONObject().put("p_month", month)))
    }

    fun approveAwards(month: String): Boolean {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return jsonRpc("rc46_approve_awards", JSONObject().put("p_month", month)).trim() == "true"
    }

    fun excludeAward(month: String, studentId: String, reason: String): JSONObject {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return JSONObject(jsonRpc("rc46_exclude_award", JSONObject().put("p_month", month).put("p_student", studentId).put("p_reason", reason)))
    }

    fun setDonorContact(studentId: String, allowed: Boolean): Boolean {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return jsonRpc("rc46_set_donor_contact", JSONObject().put("p_student", studentId).put("p_allowed", allowed)).trim() == "true"
    }

    // ---- RC48: donor halaqa scope and support cards (pledges are tracked here; money moves outside the app). ----
    /**
     * RC86: support / donor lists are read once and kept on the device. Online they are fetched again only when asked
     * (pull-to-refresh, after an edit) or once per RefreshGate cycle; offline the stored copy is shown. Only a first-ever open
     * without internet has nothing to show.
     */
    private fun storedRpcText(name: String, args: JSONObject, force: Boolean): String {
        val owner = currentUserId()
        val key = "rpc:$name:${args.toString().hashCode()}"
        val cached = owner?.let { cache.get(it, key, null) }
        val due = cached == null || force || RefreshGate.due(key)
        if (due && online()) {
            val fresh = runCatching { jsonRpc(name, args) }
            fresh.getOrNull()?.let { raw ->
                if (owner != null) cache.put(owner, key, raw)
                RefreshGate.mark(key)
                return raw
            }
            if (cached == null) throw fresh.exceptionOrNull() ?: IllegalStateException("تعذر التحميل")
        }
        return cached ?: throw IllegalStateException("لا توجد بيانات محفوظة بعد. افتح هذه الصفحة مرة واحدة وأنت متصل بالإنترنت.")
    }

    private fun storedRpcList(name: String, force: Boolean, args: JSONObject = JSONObject()): List<JSONObject> {
        val arr = JSONArray(storedRpcText(name, args, force))
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    fun donors(force: Boolean = false): List<JSONObject> = storedRpcList("rc48_donors", force)

    fun setDonorHalaqas(donorId: String, halaqaIds: List<String>): Boolean {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return jsonRpc("rc48_set_donor_halaqas", JSONObject().put("p_donor", donorId).put("p_halaqas", JSONArray(halaqaIds))).trim() == "true"
    }

    fun supportCards(force: Boolean = false): List<JSONObject> = storedRpcList("rc49_support_cards", force)

    fun supportSaveCard(card: JSONObject): String {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return JSONObject(jsonRpc("rc49_support_save_card", JSONObject().put("p", card))).optString("id")
    }

    fun supportPledge(cardId: String, periods: List<String>, anonymous: Boolean): Int {
        require(online()) { "يلزم اتصال بالإنترنت" }
        val args = JSONObject().put("p_card", cardId).put("p_periods", JSONArray(periods)).put("p_anonymous", anonymous)
        return JSONObject(jsonRpc("rc48_support_pledge", args)).optInt("created")
    }

    fun supportCancel(pledgeId: String): Boolean {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return jsonRpc("rc48_support_cancel", JSONObject().put("p_pledge", pledgeId)).trim() == "true"
    }

    fun supportConfirm(pledgeId: String): Boolean {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return jsonRpc("rc48_support_confirm", JSONObject().put("p_pledge", pledgeId)).trim() == "true"
    }

    fun supportDeliver(pledgeId: String, text: String, imageDataUrl: String?, thanks: String = ""): Boolean {
        require(online()) { "يلزم اتصال بالإنترنت" }
        val args = JSONObject().put("p_pledge", pledgeId).put("p_text", text).put("p_image", imageDataUrl ?: JSONObject.NULL)
            .put("p_thanks", thanks.ifBlank { null } ?: JSONObject.NULL)
        return jsonRpc("rc49_support_deliver", args).trim() == "true"
    }

    fun myPledges(force: Boolean = false): List<JSONObject> = storedRpcList("rc49_my_pledges", force)

    fun supportDefaults(force: Boolean = false): JSONObject =
        JSONObject(storedRpcText("rc49_support_defaults", JSONObject(), force))

    fun setSupportDefaults(p: JSONObject): Boolean {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return jsonRpc("rc49_support_set_defaults", JSONObject().put("p", p)).trim() == "true"
    }

    fun submitTransfer(pledgeId: String, ref: String, imageDataUrl: String?): Boolean {
        require(online()) { "يلزم اتصال بالإنترنت" }
        val args = JSONObject().put("p_pledge", pledgeId).put("p_ref", ref).put("p_image", imageDataUrl ?: JSONObject.NULL)
        return jsonRpc("rc49_support_submit_transfer", args).trim() == "true"
    }

    fun donorImpact(): JSONObject {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return JSONObject(jsonRpc("rc49_donor_impact", JSONObject()))
    }

    fun donorReport(month: String): JSONObject {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return JSONObject(jsonRpc("rc49_donor_report", JSONObject().put("p_month", month)))
    }

    /**
     * RC83: a month's donor report kept on the device. Finished months older than last month never change, so they are read once.
     * This month and last month are read again at most once per cycle (RefreshGate), then served from the stored copy.
     */
    fun donorReportStored(month: String, volatile: Boolean): JSONObject? {
        val owner = currentUserId() ?: return null
        val key = "donor-report:$month"
        val cached = cache.get(owner, key, maxAge = null)?.let { runCatching { JSONObject(it) }.getOrNull() }
        val due = cached == null || (volatile && RefreshGate.due(key))
        if (!due || !online()) return cached
        val fresh = runCatching { donorReport("$month-01") }.getOrNull() ?: return cached
        cache.put(owner, key, fresh.toString())
        if (volatile) RefreshGate.mark(key)
        return fresh
    }

    /** RC83: photos of a month's winners (student id → photo). Empty when the owner turned the setting off. Stored on the device per month. */
    fun donorAwardPhotos(month: String): Map<String, String> {
        val owner = currentUserId() ?: return emptyMap()
        val m = month.take(7)
        val key = "award-photos:$m"
        val cached = cache.get(owner, key, maxAge = null)
        val raw = if ((cached == null || RefreshGate.due(key)) && online()) {
            runCatching { jsonRpc("rc83_donor_award_photos", JSONObject().put("p_month", "$m-01")) }.getOrNull()
                ?.also { cache.put(owner, key, it); RefreshGate.mark(key) } ?: cached
        } else cached
        val obj = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return emptyMap()
        return obj.keys().asSequence().mapNotNull { k -> obj.optString(k).takeIf { it.startsWith("data:image/") }?.let { k to it } }.toMap()
    }

    /** RC83: whether donors may see winners' photos (set by the system administrator; on by default). */
    fun donorPhotosSetting(): Boolean =
        table("app_settings", "select=id,donor_show_photos&limit=1", forceRefresh = online()).optJSONObject(0)?.optBoolean("donor_show_photos", true) ?: true

    fun setDonorPhotosSetting(on: Boolean) {
        patchConfirmed("app_settings", "id=eq.1", JSONObject().put("donor_show_photos", on))
        RefreshGate.invalidate()
    }

    fun donorNews(force: Boolean = false): List<JSONObject> = storedRpcList("rc49_news_list", force)

    fun saveNews(p: JSONObject): String {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return JSONObject(jsonRpc("rc49_news_save", JSONObject().put("p", p))).optString("id")
    }

    fun donorSuggest(text: String): Boolean {
        require(online()) { "يلزم اتصال بالإنترنت" }
        return jsonRpc("rc49_donor_suggest", JSONObject().put("p_text", text)).trim() == "true"
    }

    fun donorSuggestions(force: Boolean = false): List<JSONObject> = storedRpcList("rc49_suggestions", force)

    /** The delivery proof photo as a data URL, or null. Fetched on demand because it is large. */
    fun supportProof(pledgeId: String, kind: String = "delivery"): String? {
        require(online()) { "يلزم اتصال بالإنترنت" }
        val raw = jsonRpc("rc49_support_proof", JSONObject().put("p_pledge", pledgeId).put("p_kind", kind)).trim()
        if (raw.isEmpty() || raw == "null") return null
        val value = if (raw.startsWith("\"")) org.json.JSONTokener(raw).nextValue() as? String else raw
        return value?.takeIf { it.startsWith("data:image/") }
    }

    /** RC40: hifz / review / sard / tathbit pages per student and halaqa (sard = span). Falls back to empty if the RPC is missing. */
    fun observerBreakdown(from: String, to: String): JSONObject {
        require(online()) { "المراقب يحتاج اتصالاً بالخادم" }
        val raw = withAuthRetry { api.rpc("rc40_observer_breakdown", JSONObject().put("p_from", from).put("p_to", to)) }
        return JSONObject(raw)
    }

    /** RC42: one student's recitations and attendance counts for the observer (read-only RPC). */
    fun observerStudentDetail(studentId: String, from: String, to: String): JSONObject {
        require(online()) { "المراقب يحتاج اتصالاً بالخادم" }
        val raw = withAuthRetry { api.rpc("rc42_observer_student_detail", JSONObject().put("p_student", studentId).put("p_from", from).put("p_to", to)) }
        return JSONObject(raw)
    }

    fun observerRequests(): org.json.JSONArray {
        require(online()) { "هذه الصفحة تحتاج اتصالاً بالخادم" }
        return org.json.JSONArray(withAuthRetry { api.rpc("rc32_observer_requests", JSONObject()) })
    }

    fun observerSubmitRequest(studentId: String, kind: String, note: String) {
        require(online()) { "إرسال الطلب يحتاج اتصالاً بالخادم" }
        require(note.trim().length >= 3) { "اكتب سبب الطلب" }
        withAuthRetry { api.rpc("rc32_observer_submit_request", JSONObject().put("p_student", studentId).put("p_kind", kind).put("p_note", note.trim())) }
    }

    // ---- RC52: reward requests, catalog, settings and per-request message threads ----
    fun rewardSettings(force: Boolean = false): JSONObject = JSONObject(storedRpcText("rc52_settings", JSONObject(), force))
    fun setRewardSettings(json: JSONObject) { require(online()) { "يلزم اتصال بالإنترنت" }; jsonRpc("rc52_set_settings", JSONObject().put("p", json)) }
    fun rewardOptions(force: Boolean = false): List<JSONObject> = storedRpcList("rc52_reward_options", force)
    fun rewardOptionSave(json: JSONObject) { require(online()) { "يلزم اتصال بالإنترنت" }; jsonRpc("rc52_reward_option_save", JSONObject().put("p", json)) }
    fun rewardOptionDelete(id: String) { require(online()) { "يلزم اتصال بالإنترنت" }; jsonRpc("rc52_reward_option_delete", JSONObject().put("p_id", id)) }

    fun rewardRequest(studentId: String, month: String, kind: String, optionId: String?, amount: Double?, currency: String?, note: String, anonymous: Boolean): JSONObject {
        require(online()) { "إرسال الطلب يحتاج اتصالاً بالخادم" }
        return JSONObject(jsonRpc("rc52_reward_request", JSONObject()
            .put("p_student", studentId).put("p_month", month).put("p_kind", kind)
            .put("p_option", optionId ?: JSONObject.NULL).put("p_amount", amount ?: JSONObject.NULL)
            .put("p_currency", currency ?: JSONObject.NULL).put("p_note", note.trim()).put("p_anonymous", anonymous)))
    }

    fun myRewards(force: Boolean = false): List<JSONObject> = storedRpcList("rc52_my_rewards", force)
    fun adminRewards(force: Boolean = false): List<JSONObject> = storedRpcList("rc52_admin_rewards", force)

    private fun rc52Bool(name: String, args: JSONObject, failure: String) {
        require(online()) { "يلزم اتصال بالإنترنت" }
        if (!jsonRpc(name, args).trim().equals("true", true)) error(failure)
    }
    fun rewardDecide(id: String, approve: Boolean, note: String) =
        rc52Bool("rc52_reward_decide", JSONObject().put("p_id", id).put("p_approve", approve).put("p_note", note.trim().ifBlank { JSONObject.NULL }), "تمت معالجة الطلب سابقًا")
    fun rewardSubmitTransfer(id: String, ref: String, image: String?) =
        rc52Bool("rc52_reward_submit_transfer", JSONObject().put("p_id", id).put("p_ref", ref).put("p_image", image ?: JSONObject.NULL), "تعذّر إرسال الإثبات؛ الطلب ليس بانتظار تحويل")
    fun rewardConfirm(id: String) = rc52Bool("rc52_reward_confirm", JSONObject().put("p_id", id), "تعذّر تأكيد الاستلام")
    fun rewardDeliver(id: String, text: String, image: String?, thanks: String) =
        rc52Bool("rc52_reward_deliver", JSONObject().put("p_id", id).put("p_text", text).put("p_image", image ?: JSONObject.NULL).put("p_thanks", thanks.ifBlank { JSONObject.NULL }), "أكّد الاستلام أولًا")
    fun rewardCancel(id: String) = rc52Bool("rc52_reward_cancel", JSONObject().put("p_id", id), "لا يمكن إلغاء الطلب في هذه المرحلة")
    fun rewardProof(id: String, kind: String): String? {
        require(online()) { "يلزم اتصال بالإنترنت" }
        val raw = jsonRpc("rc52_reward_proof", JSONObject().put("p_id", id).put("p_kind", kind)).trim()
        if (raw.isEmpty() || raw == "null") return null
        val value = if (raw.startsWith("\"")) org.json.JSONTokener(raw).nextValue() as? String else raw
        return value?.takeIf { it.startsWith("data:image/") }
    }
    fun threadGet(kind: String, refId: String): List<JSONObject> {
        require(online()) { "المحادثة تحتاج اتصالاً بالخادم" }
        val arr = JSONObject(jsonRpc("rc52_thread_get", JSONObject().put("p_kind", kind).put("p_ref", refId))).optJSONArray("messages") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }
    fun threadPost(kind: String, refId: String, body: String) =
        rc52Bool("rc52_thread_post", JSONObject().put("p_kind", kind).put("p_ref", refId).put("p_body", body.trim()), "تعذّر إرسال الرسالة")

    // RC60: donor chats — admin conversation list, direct (general) chat, read/send by thread id.
    fun chatOverview(): JSONObject {
        require(online()) { "المحادثات تحتاج اتصالاً بالخادم" }
        return JSONObject(jsonRpc("rc54_chat_overview", JSONObject()))
    }
    fun chatOpenDirect(donorId: String): String {
        require(online()) { "المحادثة تحتاج اتصالاً بالخادم" }
        return jsonRpc("rc54_thread_open", JSONObject().put("p_donor", donorId)).trim().trim('"')
    }
    fun chatThreadById(threadId: String): List<JSONObject> {
        require(online()) { "المحادثة تحتاج اتصالاً بالخادم" }
        val arr = JSONObject(jsonRpc("rc54_thread_get_by_id", JSONObject().put("p_tid", threadId))).optJSONArray("messages") ?: JSONArray()
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }
    fun chatPostById(threadId: String, body: String) =
        rc52Bool("rc54_thread_post_by_id", JSONObject().put("p_tid", threadId).put("p_body", body.trim()), "تعذّر إرسال الرسالة")
    /** RC66: this association's subscription state (null when offline or the RC66 SQL is not applied yet). */
    fun mySubscription(): JSONObject? {
        if (!online()) return null
        return runCatching { JSONObject(jsonRpc("rc66_my_subscription", JSONObject())) }.getOrNull()
    }

    /** RC65: students the signed-in person may examine (server list, which also covers independent examiners; offline: the cached students). */
    fun examStudents(): List<JSONObject> {
        if (online()) {
            val rows = runCatching { JSONArray(jsonRpc("rc65_exam_students", JSONObject())) }.getOrNull()
            if (rows != null) return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
        }
        return students().filter { it.status == "active" }.map {
            JSONObject().put("id", it.id).put("full_name", it.fullName).put("halaqa_id", it.halaqaId ?: JSONObject.NULL).put("halaqa_name", "")
        }
    }

    /** Best effort: sends the push for the message just written (the edge function may not be deployed on every project). */
    fun chatPush() { runCatching { withAuthRetry { api.function("halaqati-chat-push", JSONObject()) } } }

    fun observerResolve(requestId: String, approved: Boolean, note: String) {
        require(online()) { "الرد على الطلب يحتاج اتصالاً بالخادم" }
        val raw = withAuthRetry {
            api.rpc("rc32_observer_resolve", JSONObject().put("p_id", requestId).put("p_status", if (approved) "approved" else "declined").put("p_note", note.trim().ifBlank { JSONObject.NULL }))
        }.trim()
        if (!raw.equals("true", true)) error("الطلب غير موجود أو تم الرد عليه سابقًا")
    }

    fun rejectAccount(userId: String, reason: String) {
        requireDirectAccountConnection()
        withAuthRetry {
            api.patch(
                "profiles",
                "id=eq.$userId",
                JSONObject()
                    .put("approval_status", "rejected")
                    .put("active", false)
                    .put("rejection_reason", reason.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL),
            )
        }
    }

    fun setAccountActive(userId: String, active: Boolean) {
        requireDirectAccountConnection()
        withAuthRetry { api.patch("profiles", "id=eq.$userId", JSONObject().put("active", active)) }
    }

    fun changeAccountRole(userId: String, role: String) {
        requireDirectAccountConnection()
        require(role in setOf("guardian", "teacher", "supervisor", "observer", "admin")) { "الصلاحية المطلوبة غير صالحة" }
        withAuthRetry {
            api.patch(
                "profiles",
                "id=eq.$userId&role=neq.admin",
                JSONObject().put("role", role).put("requested_role", role),
            )
        }
    }

    fun deleteAccountPermanently(userId: String) {
        requireDirectAccountConnection()
        val raw = withAuthRetry { api.rpc("admin_delete_account", JSONObject().put("target_user", userId)) }.trim()
        if (raw.equals("false", true) || raw.isBlank()) error("تعذر حذف الحساب نهائياً")
    }

    fun guardianAccounts(forceRefresh: Boolean = false): JSONArray {
        val owner = currentUserId() ?: return JSONArray()
        val cached = cache.get(owner, CACHE_GUARDIAN_ACCOUNTS, null)?.let { runCatching { JSONArray(it) }.getOrNull() }
        if (!forceRefresh && cached != null) return cached
        if (!online()) return cached ?: JSONArray()
        return runCatching {
            val raw = withAuthRetry { api.rpc("list_guardian_accounts") }
            JSONArray(raw)
        }.onSuccess { cache.put(owner, CACHE_GUARDIAN_ACCOUNTS, it.toString()) }
            .getOrElse { cached ?: JSONArray() }
    }

    private fun pendingPrimaryGuardianId(studentId: String): Pair<Boolean, String?> {
        val pending = queueSnapshot(2_000)
            .asSequence()
            .filter { it.path == "/rest/v1/rpc/set_student_primary_guardian" }
            .mapNotNull { item ->
                val body = item.body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@mapNotNull null
                if (body.optString("p_student_id") != studentId) return@mapNotNull null
                item.created to if (!body.has("p_guardian_id") || body.isNull("p_guardian_id")) null
                else body.optString("p_guardian_id").takeIf { it.isNotBlank() }
            }
            .maxByOrNull { it.first }
        return if (pending == null) false to null else true to pending.second
    }

    fun primaryGuardianId(studentId: String, forceRefresh: Boolean = false): String? {
        if (studentId.isBlank()) return null
        pendingPrimaryGuardianId(studentId).let { pending -> if (pending.first) return pending.second }
        val rows = table(
            "student_guardians",
            "select=guardian_id,is_primary&student_id=eq.$studentId&is_primary=eq.true&limit=1",
            forceRefresh = forceRefresh,
        )
        return rows.optJSONObject(0)?.optString("guardian_id")?.takeIf { it.isNotBlank() }
    }

    fun linkPrimaryGuardian(studentId: String, guardianId: String?) {
        require(studentId.isNotBlank()) { "الطالب غير صالح" }
        val args = JSONObject().put("p_student_id", studentId)
        if (guardianId.isNullOrBlank()) args.put("p_guardian_id", JSONObject.NULL) else args.put("p_guardian_id", guardianId)
        // Always use the same durable outbox as the student write. For a new student this guarantees
        // that the student INSERT is uploaded before the guardian FK/RPC, while existing students still
        // update optimistically through pendingPrimaryGuardianId().
        queued(
            "POST",
            "/rest/v1/rpc/set_student_primary_guardian",
            args,
            dedupe = "student-guardian:$studentId",
        )
        currentUserId()?.let { owner ->
            cache.invalidate(owner, "table:student_guardians:")
            cache.invalidate(owner, CACHE_GUARDIAN_ACCOUNTS)
        }
    }


    fun reportRecitations(
        from: String,
        to: String,
        track: String = "all",
        halaqaId: String = "all",
        sessionType: String = "all",
        forceRefresh: Boolean = false,
    ): JSONArray {
        require(track in setOf("all", "general_hifz", "tathbit")) { "المسار غير صالح" }
        require(sessionType in setOf("all", "tasmee", "sard", "review", "test", "tajweed", "tilawah", "lesson", "other")) { "نوع اللقاء غير صالح" }
        val trackFilter = if (track == "all") "" else "&${StudentTrackRules.postgrestFilter("students.track_code", track)}"
        val halaqaFilter = if (halaqaId == "all") "" else "&sessions.halaqa_id=eq.$halaqaId"
        val typeFilter = sessionTypeQuery(sessionType)
        val query = "select=student_id,activity_type,input_method,start_surah,start_ayah,end_surah,end_ayah,start_page,end_page,selected_surahs,selection_data,pages_count,mistakes,prompts,evaluation,safety_percentage,points,teacher_note,created_at," +
            "students!inner(full_name,track_code,halaqa_id,category_id,date_of_birth),sessions!inner(session_date,halaqa_id,session_type,status)" +
            "&sessions.session_date=gte.$from&sessions.session_date=lte.$to&sessions.status=neq.cancelled" +
            "$trackFilter$halaqaFilter$typeFilter&order=created_at.asc&limit=10000"
        return table("recitation_entries", query, forceRefresh = forceRefresh)
    }

    fun reportNotes(
        from: String,
        to: String,
        track: String = "all",
        halaqaId: String = "all",
        sessionType: String = "all",
        forceRefresh: Boolean = false,
    ): JSONArray {
        require(track in setOf("all", "general_hifz", "tathbit")) { "المسار غير صالح" }
        require(sessionType in setOf("all", "tasmee", "sard", "review", "test", "tajweed", "tilawah", "lesson", "other")) { "نوع اللقاء غير صالح" }
        val trackFilter = if (track == "all") "" else "&${StudentTrackRules.postgrestFilter("students.track_code", track)}"
        val halaqaFilter = if (halaqaId == "all") "" else "&sessions.halaqa_id=eq.$halaqaId"
        val typeFilter = sessionTypeQuery(sessionType)
        val query = "select=student_id,behavior,behavior_score,teacher_comment,homework,participation,created_at," +
            "students!inner(full_name,track_code,halaqa_id),sessions!inner(session_date,halaqa_id,session_type,status)" +
            "&sessions.session_date=gte.$from&sessions.session_date=lte.$to&sessions.status=neq.cancelled" +
            "$trackFilter$halaqaFilter$typeFilter&order=created_at.asc&limit=10000"
        return table("student_daily_notes", query, forceRefresh = forceRefresh)
    }

    /** RC43: days a student was absent / excused in the period (marks "غ" / "ع" in the recitation grid and the parent report). */
    fun reportAbsences(
        from: String,
        to: String,
        track: String = "all",
        halaqaId: String = "all",
        sessionType: String = "all",
    ): JSONArray {
        require(track in setOf("all", "general_hifz", "tathbit")) { "المسار غير صالح" }
        val trackFilter = if (track == "all") "" else "&${StudentTrackRules.postgrestFilter("students.track_code", track)}"
        val halaqaFilter = if (halaqaId == "all") "" else "&sessions.halaqa_id=eq.$halaqaId"
        val typeFilter = sessionTypeQuery(sessionType)
        val query = "select=student_id,status,students!inner(full_name,track_code,halaqa_id),sessions!inner(session_date,halaqa_id,session_type,status)" +
            "&status=in.(absent,excused)&sessions.session_date=gte.$from&sessions.session_date=lte.$to&sessions.status=neq.cancelled" +
            "$trackFilter$halaqaFilter$typeFilter&limit=10000"
        return table("attendance_records", query)
    }

    /** Native parity with the legacy reportStatsRows engine.
     * Keeps all active RLS-visible students in the selected track, including zero-activity rows.
     */
    fun reportSummary(
        from: String,
        to: String,
        track: String = "all",
        halaqaId: String = "all",
        sessionType: String = "all",
        forceRefresh: Boolean = false,
    ): JSONArray {
        val memoKey = "${currentUserId()}|$from|$to|$track|$halaqaId|$sessionType"
        val now = System.currentTimeMillis()
        if (!forceRefresh) reportSummaryMemo[memoKey]?.takeIf { now - it.at < 60_000L }?.let { return it.rows }
        return PerfLog.time("reportSummary (compute)") { reportSummaryCompute(from, to, track, halaqaId, sessionType, forceRefresh) }.also {
            reportSummaryMemo[memoKey] = SummaryMemo(System.currentTimeMillis(), it)
        }
    }

    private fun reportSummaryCompute(
        from: String,
        to: String,
        track: String,
        halaqaId: String,
        sessionType: String,
        forceRefresh: Boolean,
    ): JSONArray {
        val start = runCatching { LocalDate.parse(from) }.getOrElse { error("تاريخ البداية غير صالح") }
        val end = runCatching { LocalDate.parse(to) }.getOrElse { error("تاريخ النهاية غير صالح") }
        require(end >= start) { "الفترة غير صالحة" }
        require(track in setOf("all", "general_hifz", "tathbit")) { "المسار غير صالح" }
        require(sessionType in setOf("all", "tasmee", "sard", "review", "test", "tajweed", "tilawah", "lesson", "other")) { "نوع اللقاء غير صالح" }

        val studentTrack = if (track == "all") "" else "&${StudentTrackRules.postgrestFilter("track_code", track)}"
        // RC27.19: a report of one halaqa / program also covers the students who participate in it from another halaqa.
        val memberIds = if (halaqaId == "all") emptyList() else runCatching {
            val m = studentHalaqaMemberships(halaqaId = halaqaId, forceRefresh = forceRefresh)
            (0 until m.length()).mapNotNull { m.optJSONObject(it) }
                .filter { it.optString("status", "active") == "active" }
                .mapNotNull { it.optString("student_id").takeIf(String::isNotBlank) }
                .distinct()
        }.getOrDefault(emptyList())
        val studentHalaqa = when {
            halaqaId == "all" -> ""
            memberIds.isEmpty() -> "&halaqa_id=eq.$halaqaId"
            else -> "&or=(halaqa_id.eq.$halaqaId,id.in.(${memberIds.joinToString(",")}))"
        }
        val students = table(
            "students",
            "select=id,full_name,track_code,category_id,halaqa_id,status&status=eq.active$studentTrack$studentHalaqa&order=full_name.asc&limit=2000",
            forceRefresh = forceRefresh,
        )
        val categories = table("student_categories", "select=id,name&limit=1000", forceRefresh = forceRefresh)
        val categoryNames = buildMap<String, String> {
            for (i in 0 until categories.length()) {
                categories.optJSONObject(i)?.let { row ->
                    row.optString("id").takeIf { it.isNotBlank() }?.let { put(it, row.optString("name")) }
                }
            }
        }

        val sessionHalaqa = if (halaqaId == "all") "" else "&sessions.halaqa_id=eq.$halaqaId"
        val sessionTypeFilter = sessionTypeQuery(sessionType)
        val sessionFilters = "&sessions.session_date=gte.$from&sessions.session_date=lte.$to&sessions.status=neq.cancelled$sessionHalaqa$sessionTypeFilter"
        val recitations = table(
            "recitation_entries",
            "select=student_id,phase_id,activity_type,start_surah,start_ayah,end_surah,end_ayah,pages_count,mistakes,prompts,points,evaluation,safety_percentage," +
                "sessions!inner(session_date,halaqa_id,session_type,status)$sessionFilters&limit=20000",
            forceRefresh = forceRefresh,
        )
        val attendance = table(
            "attendance_records",
            "select=student_id,status,sessions!inner(session_date,halaqa_id,session_type,status)$sessionFilters&limit=20000",
            forceRefresh = forceRefresh,
        )
        val extraPoints = table(
            "student_points",
            "select=student_id,points&awarded_at=gte.$from&awarded_at=lte.$to&limit=10000",
            forceRefresh = forceRefresh,
        )

        data class Aggregate(
            val student: JSONObject,
            var hifz: Double = 0.0,
            var tathbit: Double = 0.0,
            var review: Double = 0.0,
            var sard: Double = 0.0,
            var tilawah: Double = 0.0,
            var mistakes: Int = 0,
            var prompts: Int = 0,
            var recitationPoints: Double = 0.0,
            var bonusPoints: Double = 0.0,
            var safetySum: Double = 0.0,
            var safetyCount: Int = 0,
            var present: Int = 0,
            var absent: Int = 0,
            var excused: Int = 0,
            var late: Int = 0,
        )

        val byStudent = linkedMapOf<String, Aggregate>()
        for (i in 0 until students.length()) {
            val student = students.optJSONObject(i) ?: continue
            val id = student.optString("id")
            if (id.isNotBlank()) byStudent[id] = Aggregate(student)
        }
        for (i in 0 until attendance.length()) {
            val row = attendance.optJSONObject(i) ?: continue
            val aggregate = byStudent[row.optString("student_id")] ?: continue
            when (row.optString("status")) {
                "present" -> aggregate.present++
                "absent" -> aggregate.absent++
                "excused" -> aggregate.excused++
                "late" -> aggregate.late++
            }
        }
        val hifzSpanEntries = hashMapOf<String, MutableList<com.mohammedsamir.halaqati.plans.HifzSpanRules.Entry>>()
        // RC37: Sard is measured like Hifz: one stretch from the first ayah to the last ayah reached, repeats count once.
        val sardSpanEntries = hashMapOf<String, MutableList<com.mohammedsamir.halaqati.plans.HifzSpanRules.Entry>>()
        for (i in 0 until recitations.length()) {
            val row = recitations.optJSONObject(i) ?: continue
            val aggregate = byStudent[row.optString("student_id")] ?: continue
            val pages = row.optDouble("pages_count", 0.0).coerceAtLeast(0.0)
            if (row.optString("activity_type") == "new_hifz" &&
                !TrackActivityPolicy.isTathbit(aggregate.student.optString("track_code"))
            ) hifzSpanEntries.getOrPut(row.optString("student_id")) { mutableListOf() }.add(hifzSpanEntryOf(row))
            if (row.optString("activity_type") == "sard" &&
                !TrackActivityPolicy.isTathbit(aggregate.student.optString("track_code"))
            ) sardSpanEntries.getOrPut(row.optString("student_id")) { mutableListOf() }.add(hifzSpanEntryOf(row))
            when {
                row.optString("activity_type") == "tathbit" -> aggregate.tathbit += pages
                // RC15.1: every non-test recitation of a tathbit student (incl. legacy review_far/new_hifz/sard) is tathbit.
                TrackActivityPolicy.isTathbit(aggregate.student.optString("track_code")) && row.optString("activity_type") != "test" -> aggregate.tathbit += pages
                row.optString("activity_type") == "new_hifz" -> aggregate.hifz += pages
                row.optString("activity_type").contains("review") -> aggregate.review += pages
                row.optString("activity_type") == "sard" -> aggregate.sard += pages
                else -> aggregate.tilawah += pages
            }
            aggregate.mistakes += row.optInt("mistakes", 0).coerceAtLeast(0)
            aggregate.prompts += row.optInt("prompts", 0).coerceAtLeast(0)
            aggregate.recitationPoints += row.optDouble("points", 0.0)
            if (row.has("safety_percentage") && !row.isNull("safety_percentage") &&
                !com.mohammedsamir.halaqati.plans.RecitationQualityRules.isPlaceholder(row.optString("evaluation"), row.optDouble("safety_percentage"), pages)
            ) {
                aggregate.safetySum += row.optDouble("safety_percentage", 0.0).coerceIn(0.0, 100.0)
                aggregate.safetyCount++
            }
        }
        // RC27.8: hifz pages = first ayah -> last ayah of the period (same definition as the plan screen).
        hifzSpanEntries.forEach { (sid, list) -> byStudent[sid]?.hifz = com.mohammedsamir.halaqati.plans.HifzSpanRules.pages(list) }
        sardSpanEntries.forEach { (sid, list) -> byStudent[sid]?.sard = com.mohammedsamir.halaqati.plans.HifzSpanRules.pages(list) }
        for (i in 0 until extraPoints.length()) {
            val row = extraPoints.optJSONObject(i) ?: continue
            byStudent[row.optString("student_id")]?.let { it.bonusPoints += row.optDouble("points", 0.0) }
        }

        // RC11: enrich monthly rows with separate Hifz/Sard phase targets. This keeps
        // Sard achievement out of the Hifz total while preserving the existing activity totals.
        val reportPlans = plans(forceRefresh = forceRefresh).filter { plan ->
            plan.status != "archived" && plan.startDate <= to && plan.endDate >= from &&
                (halaqaId == "all" || plan.halaqaId == halaqaId)
        }
        val reportPhases = planPhases(forceRefresh = forceRefresh).filter { phase ->
            phase.status != "archived" && phase.startDate <= to && phase.endDate >= from
        }
        val reportPhasesByPlan = reportPhases.groupBy { it.planId }
        val reportPhaseStates = planPhaseStates(forceRefresh = forceRefresh).associateBy { it.phaseId to it.studentId }
        fun phasesForStudent(student: JSONObject): List<SmartPlanPhaseRow> {
            val sid = student.optString("id")
            val hid = student.optString("halaqa_id")
            val cid = student.optString("category_id")
            val tathbitStudent = TrackActivityPolicy.isTathbit(student.optString("track_code").ifBlank { student.optString("track") })
            val matches = reportPlans.filter { plan ->
                // RC15.1: tathbit students are never measured against general hifz/sard phases.
                (if (tathbitStudent) plan.type == "tathbit" else plan.type != "tathbit") &&
                plan.halaqaId == hid && when (plan.scopeType) {
                    "student" -> plan.studentId == sid
                    "category" -> !plan.categoryId.isNullOrBlank() && plan.categoryId == cid
                    else -> true
                } && reportPhasesByPlan[plan.id].orEmpty().isNotEmpty()
            }
            val priority = mapOf("halaqa" to 1, "category" to 2, "student" to 3)
            val selected = matches.maxByOrNull { priority[it.scopeType] ?: 0 } ?: return emptyList()
            return reportPhasesByPlan[selected.id].orEmpty().filter { if (selected.type == "tathbit") it.phaseType == "tathbit" else it.phaseType != "tathbit" }
        }

        val ordered = byStudent.values.sortedWith(
            compareByDescending<Aggregate> { it.recitationPoints + it.bonusPoints }
                .thenByDescending { it.hifz + it.tathbit + it.review + it.sard + it.tilawah }
                .thenBy { it.student.optString("full_name") },
        )
        return JSONArray().apply {
            ordered.forEach { a ->
                val categoryId = a.student.optString("category_id")
                val studentId = a.student.optString("id")
                val studentPhases = phasesForStudent(a.student)
                val hifzPhase = studentPhases.firstOrNull { it.phaseType == "hifz" }
                val sardPhase = studentPhases.firstOrNull { it.phaseType == "sard" }
                fun targetFor(phase: SmartPlanPhaseRow?): Double? {
                    phase ?: return null
                    val state = reportPhaseStates[phase.id to studentId]
                    state?.frozenTargetPages?.let { return it }
                    val base = state?.targetPagesOverride ?: phase.targetPages
                        ?: if (phase.goalMode == "juz") phase.targetUnits?.times(20.0) else null
                    return base?.plus(state?.carryShortfallPages ?: 0.0)
                }
                fun completedFor(phase: SmartPlanPhaseRow?, fallback: Double): Double {
                    phase ?: return fallback
                    val state = reportPhaseStates[phase.id to studentId]
                    state?.frozenCompletedPages?.let { return it }
                    val tagged = (0 until recitations.length()).mapNotNull { recitations.optJSONObject(it) }.filter { row ->
                        row.optString("student_id") == studentId && row.optString("phase_id") == phase.id
                    }
                    if (tagged.isNotEmpty()) {
                        return if (phase.phaseType == "hifz" || phase.phaseType == "sard") com.mohammedsamir.halaqati.plans.HifzSpanRules.pages(tagged.map(::hifzSpanEntryOf))
                        else tagged.sumOf { it.optDouble("pages_count", 0.0).coerceAtLeast(0.0) }
                    }
                    val pStart = runCatching { LocalDate.parse(phase.startDate) }.getOrNull() ?: return fallback
                    val pEnd = runCatching { LocalDate.parse(phase.endDate) }.getOrNull() ?: return fallback
                    val untagged = (0 until recitations.length()).mapNotNull { recitations.optJSONObject(it) }.filter { row ->
                        if (row.optString("student_id") != studentId || row.optString("phase_id").isNotBlank()) return@filter false
                        val activity = row.optString("activity_type")
                        val typeMatches = if (phase.phaseType == "sard") activity == "sard" else activity in setOf("new_hifz", "hifz")
                        val date = row.optJSONObject("sessions")?.optString("session_date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        typeMatches && date != null && date in pStart..pEnd
                    }
                    return if (phase.phaseType == "hifz" || phase.phaseType == "sard") com.mohammedsamir.halaqati.plans.HifzSpanRules.pages(untagged.map(::hifzSpanEntryOf))
                    else untagged.sumOf { it.optDouble("pages_count", 0.0).coerceAtLeast(0.0) }
                }
                val hifzTarget = targetFor(hifzPhase)
                val sardTarget = targetFor(sardPhase)
                val hifzCompleted = completedFor(hifzPhase, a.hifz)
                val sardCompleted = completedFor(sardPhase, a.sard)
                // Keep the overall total consistent with the same phase-aware values shown
                // in Hifz/Sard columns instead of mixing legacy aggregates with frozen phases.
                val totalPages = hifzCompleted + a.tathbit + a.review + sardCompleted + a.tilawah
                val qualityPercent = if (a.safetyCount > 0) a.safetySum / a.safetyCount else 0.0
                val qualityLabel = when {
                    a.safetyCount == 0 -> "—"
                    qualityPercent >= 90.0 -> "ممتاز"
                    qualityPercent >= 80.0 -> "جيد جدًا"
                    qualityPercent >= 70.0 -> "جيد"
                    else -> "يحتاج متابعة"
                }
                put(
                    JSONObject()
                        .put("student_id", a.student.optString("id"))
                        .put("student_name", a.student.optString("full_name", "طالب"))
                        .put("track_code", StudentTrackRules.canonicalStored(a.student.optString("track_code", "general_hifz")))
                        .put("category_name", categoryNames[categoryId].orEmpty())
                        .put("hifz_pages", kotlin.math.round(hifzCompleted * 100.0) / 100.0)
                        .put("tathbit_pages", kotlin.math.round(a.tathbit * 100.0) / 100.0)
                        .put("review_pages", kotlin.math.round(a.review * 100.0) / 100.0)
                        .put("sard_pages", kotlin.math.round(sardCompleted * 100.0) / 100.0)
                        .put("tilawah_pages", kotlin.math.round(a.tilawah * 100.0) / 100.0)
                        .put("total_pages", kotlin.math.round(totalPages * 100.0) / 100.0)
                        .put("hifz_target_pages", hifzTarget ?: JSONObject.NULL)
                        .put("sard_target_pages", sardTarget ?: JSONObject.NULL)
                        .put("hifz_phase_percent", hifzTarget?.takeIf { it > .001 }?.let { kotlin.math.round(hifzCompleted / it * 1000.0) / 10.0 } ?: JSONObject.NULL)
                        .put("sard_phase_percent", sardTarget?.takeIf { it > .001 }?.let { kotlin.math.round(sardCompleted / it * 1000.0) / 10.0 } ?: JSONObject.NULL)
                        .put("quality_percent", kotlin.math.round(qualityPercent * 10.0) / 10.0)
                        .put("quality_label", qualityLabel)
                        .put("mistakes", a.mistakes)
                        .put("prompts", a.prompts)
                        .put("present", a.present)
                        .put("absent", a.absent)
                        .put("excused", a.excused)
                        .put("late", a.late)
                        .put("sessions", a.present + a.absent + a.excused + a.late)
                        .put("recitation_points", kotlin.math.round(a.recitationPoints * 100.0) / 100.0)
                        .put("bonus_points", kotlin.math.round(a.bonusPoints * 100.0) / 100.0)
                        .put("points", kotlin.math.round((a.recitationPoints + a.bonusPoints) * 100.0) / 100.0),
                )
            }
        }
    }

    fun rankingRange(
        from: String,
        to: String,
        programTrack: String = "general_hifz",
        rankingTrack: String = "hifz",
    ): JSONArray {
        val canonicalProgram = com.mohammedsamir.halaqati.quran.RecitationParityRules.canonicalProgramTrack(programTrack)
        require(rankingTrack in setOf("hifz", "review", "sard")) { "مسار الترتيب غير صالح" }
        val query = "select=student_id,pages_count,mistakes,prompts,points,activity_type,selection_data,start_surah,start_ayah,end_surah,end_ayah," +
            "students!inner(full_name,category_id,track_code),sessions!inner(session_date)" +
            "&sessions.session_date=gte.$from&sessions.session_date=lte.$to&limit=10000"
        val owner = currentUserId()
        val rankingCacheKey = "$CACHE_RANKING_PREFIX$from:$to:$canonicalProgram:$rankingTrack"
        // The cached copy only covers the recent window: use it only when the requested range lies inside it.
        val cachedFrom = owner?.let { cache.get(it, CACHE_RANKING_SOURCE_FROM, maxAge = null) }
        var source = if (cachedFrom != null && from >= cachedFrom) {
            owner?.let { cache.get(it, CACHE_RANKING_SOURCE, maxAge = null) }
                ?.let { runCatching { JSONArray(it) }.getOrNull() }
                ?: JSONArray()
        } else JSONArray()
        val usedRankingCache = source.length() > 0
        if (!usedRankingCache) source = table("recitation_entries", query)
        // RC27.39: the cached 62-day copy never expired, so rankings missed everything saved after the last full refresh.
        // Re-read only the last 3 days (at most every 30 s), swap them into the cached copy and keep it for offline use.
        val tailStart = LocalDate.now().minusDays(3).toString()
        if (usedRankingCache && owner != null && online() && to >= tailStart && System.currentTimeMillis() - rankingTailAt > 30_000L) {
            val tail = runCatching {
                withAuthRetry {
                    api.select(
                        "recitation_entries",
                        "select=student_id,pages_count,mistakes,prompts,points,activity_type,selection_data,start_surah,start_ayah,end_surah,end_ayah," +
                            "students!inner(full_name,category_id,track_code),sessions!inner(session_date)&sessions.session_date=gte.$tailStart&limit=5000",
                    )
                }
            }.getOrNull()
            if (tail != null) {
                rankingTailAt = System.currentTimeMillis()
                val merged = JSONArray()
                for (i in 0 until source.length()) {
                    val row = source.optJSONObject(i) ?: continue
                    val d = row.optJSONObject("sessions")?.optString("session_date").orEmpty()
                    if (d.isBlank() || d < tailStart) merged.put(row)
                }
                for (i in 0 until tail.length()) tail.optJSONObject(i)?.let { merged.put(it) }
                source = merged
                cache.put(owner, CACHE_RANKING_SOURCE, merged.toString())
            }
        }
        data class Aggregate(
            val student: JSONObject,
            var points: Double = 0.0,
            var pages: Double = 0.0,
            var mistakes: Int = 0,
            var prompts: Int = 0,
        )
        val byStudent = linkedMapOf<String, Aggregate>()
        val spanEntries = hashMapOf<String, MutableList<com.mohammedsamir.halaqati.plans.HifzSpanRules.Entry>>()
        for (index in 0 until source.length()) {
            val row = source.optJSONObject(index) ?: continue
            val sessionDate = row.optJSONObject("sessions")?.optString("session_date").orEmpty()
            if (sessionDate.isNotBlank() && (sessionDate < from || sessionDate > to)) continue
            val student = row.optJSONObject("students") ?: JSONObject()
            val studentTrack = com.mohammedsamir.halaqati.quran.RecitationParityRules.canonicalProgramTrack(student.optString("track_code"))
            if (studentTrack != canonicalProgram) continue
            if (!com.mohammedsamir.halaqati.quran.RecitationParityRules.activityMatchesRanking(row.optString("activity_type"), rankingTrack)) continue
            val id = row.optString("student_id")
            if (id.isBlank()) continue
            val selection = row.optJSONObject("selection_data") ?: JSONObject()
            val lines = selection.optJSONArray("qcf_line_keys")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty()
            val effectivePages = com.mohammedsamir.halaqati.quran.RecitationParityRules.effectivePages(row.optDouble("pages_count", 0.0), lines)
            val aggregate = byStudent.getOrPut(id) { Aggregate(student) }
            aggregate.points += row.optDouble("points", 0.0)
            if (((rankingTrack == "hifz" && row.optString("activity_type") == "new_hifz") || (rankingTrack == "sard" && row.optString("activity_type") == "sard")) && canonicalProgram == "general_hifz") {
                spanEntries.getOrPut(id) { mutableListOf() }.add(hifzSpanEntryOf(row))
            } else {
                aggregate.pages += effectivePages
            }
            aggregate.mistakes += row.optInt("mistakes", 0)
            aggregate.prompts += row.optInt("prompts", 0)
        }
        // RC27.8: hifz ranking pages = first ayah -> last ayah of the period, identical to the plan screen.
        spanEntries.forEach { (sid, list) -> byStudent[sid]?.let { it.pages += com.mohammedsamir.halaqati.plans.HifzSpanRules.pages(list) } }
        val ordered = byStudent.entries.sortedWith(
            compareByDescending<Map.Entry<String, Aggregate>> { it.value.points }
                .thenByDescending { it.value.pages }
                .thenBy { (it.value.mistakes + it.value.prompts * 0.5) / it.value.pages.coerceAtLeast(0.0001) },
        )
        val result = JSONArray().apply {
            ordered.forEach { (id, aggregate) ->
                put(
                    JSONObject()
                        .put("student_id", id)
                        .put("students", aggregate.student)
                        .put("student_name", aggregate.student.optString("full_name", "طالب"))
                        .put("points", kotlin.math.round(aggregate.points * 100.0) / 100.0)
                        .put("pages", kotlin.math.round(aggregate.pages * 100.0) / 100.0)
                        .put("mistakes", aggregate.mistakes)
                        .put("prompts", aggregate.prompts)
                        .put("track", rankingTrack)
                        .put("program_track", canonicalProgram),
                )
            }
        }
        if (result.length() > 0) owner?.let { cache.put(it, rankingCacheKey, result.toString()) }
        if (result.length() == 0) {
            owner?.let { cache.get(it, rankingCacheKey, maxAge = null) }?.let { raw ->
                runCatching { JSONArray(raw) }.getOrNull()?.let { return it }
            }
        }
        return result
    }

    /**
     * RC27.10: program ranking. Programs are ranked by their tilawah / tajweed performance, never by hifz amounts:
     * average tilawah_assessment.overall_score, then attendance, then number of recitations, then points.
     * Includes every student who belongs to one of [programHalaqaIds] (primary or active member), even with no activity yet.
     */
    fun programRankingRange(from: String, to: String, programHalaqaIds: Set<String>): JSONArray {
        if (programHalaqaIds.isEmpty()) return JSONArray()
        val filters = "&sessions.session_date=gte.$from&sessions.session_date=lte.$to&sessions.status=neq.cancelled"
        val recs = table(
            "recitation_entries",
            "select=student_id,activity_type,points,selection_data,sessions!inner(session_date,halaqa_id,status)$filters&limit=10000",
        )
        val att = table(
            "attendance_records",
            "select=student_id,status,sessions!inner(session_date,halaqa_id,status)$filters&limit=20000",
        )
        val everyone = students(forceRefresh = false)
        val names = everyone.associate { it.id to it.fullName }
        val memberOf = studentHalaqaMemberships(forceRefresh = false).let { arr ->
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                .filter { it.optString("status", "active") == "active" && it.optString("halaqa_id") in programHalaqaIds }
                .map { it.optString("student_id") }.toSet()
        }
        val belongs = everyone.filter { it.status == "active" && (it.halaqaId in programHalaqaIds || it.id in memberOf) }.map { it.id }.toMutableSet()
        class Acc { val scores = mutableListOf<Double>(); var present = 0; var recitations = 0; var points = 0 }
        val acc = linkedMapOf<String, Acc>()
        belongs.forEach { acc[it] = Acc() }
        for (i in 0 until recs.length()) {
            val row = recs.optJSONObject(i) ?: continue
            if (row.optJSONObject("sessions")?.optString("halaqa_id") !in programHalaqaIds) continue
            val id = row.optString("student_id")
            val a = acc.getOrPut(id) { Acc() }
            a.recitations++
            a.points += row.optDouble("points", 0.0).toInt()
            row.optJSONObject("selection_data")?.optJSONObject("tilawah_assessment")?.let { t ->
                if (t.has("overall_score") && !t.isNull("overall_score")) a.scores += t.optDouble("overall_score")
            }
        }
        for (i in 0 until att.length()) {
            val row = att.optJSONObject(i) ?: continue
            if (row.optJSONObject("sessions")?.optString("halaqa_id") !in programHalaqaIds) continue
            if (row.optString("status") != "present" && row.optString("status") != "late") continue
            acc.getOrPut(row.optString("student_id")) { Acc() }.present++
        }
        val ranked = com.mohammedsamir.halaqati.plans.WorkModeRules.rankProgram(
            acc.map { (id, a) -> com.mohammedsamir.halaqati.plans.WorkModeRules.ProgramRankInput(id, a.scores, a.present, a.recitations, a.points) },
        )
        return JSONArray().apply {
            ranked.forEach { r ->
                put(
                    JSONObject()
                        .put("student_id", r.studentId)
                        .put("student_name", names[r.studentId] ?: "طالب")
                        .put("score", r.score?.let { kotlin.math.round(it * 10.0) / 10.0 } ?: JSONObject.NULL)
                        .put("present", r.presentDays)
                        .put("recitations", r.recitations)
                        .put("points", r.points)
                        .put("track", "program"),
                )
            }
        }
    }

    fun firstHalaqaId(): String? {
        val owner = currentUserId() ?: return null
        val cached = cache.get(owner, CACHE_PRIMARY_HALAQA, maxAge = null)
            ?.let { runCatching { JSONObject(it).optString("id") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }
        val localStudentHalaqa = offlineDb.students().all(owner).firstOrNull { !it.halaqaId.isNullOrBlank() }?.halaqaId
        val fallback = cached ?: localStudentHalaqa
        if (!online()) return fallback
        return runCatching {
            withAuthRetry {
                api.select("halaqas", "select=id&active=eq.true&order=created_at.asc&limit=1")
            }.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
        }.getOrNull()?.also { id -> cache.put(owner, CACHE_PRIMARY_HALAQA, JSONObject().put("id", id).toString()) }
            ?: fallback
    }

    private fun queueOwnerId(): String = store.load()?.userId?.takeIf { it.isNotBlank() }
        ?: error("لا يمكن حفظ عملية محلية دون جلسة مستخدم صالحة")

    private fun queued(
        method: String,
        path: String,
        obj: JSONObject?,
        dedupe: String,
        baseUpdatedAt: String? = null,
        entityTable: String? = null,
        entityId: String? = null,
    ) {
        queue.add(queueOwnerId(), method, path, obj?.toString(), dedupe, baseUpdatedAt, entityTable, entityId)
        signalLocalMutation()
        if (entityTable in setOf("smart_plans", "smart_plan_events", "smart_plan_phases", "smart_plan_phase_states", "recitation_entries", "sessions")) {
            planSnapshotMemoryDate = null
            planSnapshotMemory = emptyList()
        }
        // RC27.23: only a queued DELETE drops the derived caches. Inserts/updates are merged into the caches by their own
        // writers; dropping them here wiped the counters and the plan progress right after every save (the rows were still
        // only in the outbox), until a manual refresh.
        if (method.equals("DELETE", ignoreCase = true) && path.substringAfter("/rest/v1/").substringBefore('?') in SESSION_DERIVED_TABLES) invalidateSessionDerivedCaches()
        // WorkManager is the durable fallback. While the app is open and Android has already
        // validated the network, also kick the lightweight Outbox flusher immediately instead of
        // waiting for WorkManager scheduling latency.
        scheduleSync()
        if (online()) runCatching { AppGraph.connectivity.requestImmediateSync() }
    }

    private fun shouldQueue(error: Exception): Boolean = when (error) {
        is IOException -> true
        is SupabaseException -> error.status == 0 || error.status == 408 || error.status == 429 || error.status >= 500
        else -> false
    }

    private inline fun <T> withAuthRetry(block: () -> T): T {
        val staleToken = api.currentAccessToken()
        return try {
            block()
        } catch (e: SupabaseException) {
            if (e.status != 401) throw e
            api.refresh(staleToken) ?: throw e
            block()
        }
    }

    private fun studentIdFromFilter(filter: String): String? =
        Regex("(?:^|&)id=eq\\.([^&]+)").find(filter)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

    private fun upsertPlanCache(owner: String, id: String, patch: JSONObject) {
        val current = cache.get(owner, CACHE_PLANS, maxAge = null)
            ?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
        val output = JSONArray()
        var matched = false
        for (index in 0 until current.length()) {
            val row = current.optJSONObject(index) ?: continue
            if (row.optString("id") == id) {
                val merged = JSONObject(row.toString())
                patch.keys().forEach { key -> merged.put(key, patch.opt(key)) }
                merged.put("id", id)
                output.put(merged)
                matched = true
            } else output.put(row)
        }
        if (!matched) {
            val created = JSONObject(patch.toString()).put("id", id)
            output.put(created)
        }
        cache.put(owner, CACHE_PLANS, output.toString())
        planSnapshotMemoryDate = null
        planSnapshotMemory = emptyList()
    }

    private fun mergeStudentPatch(owner: String, id: String, patch: JSONObject): String? {
        val existing = offlineDb.students().byId(owner, id) ?: return null
        val base = existing.remoteUpdatedAt
        val merged = existing.asJson()
        patch.keys().forEach { key -> merged.put(key, patch.opt(key)) }
        merged.put("id", id)
        upsertStudentLocal(owner, merged, dirty = true)
        cache.put(owner, CACHE_STUDENTS, localStudentsJson(owner).toString())
        return base
    }

    /** RC7 writes are optimistic/local-first: Room commits before any network attempt. */
    fun write(
        table: String,
        obj: JSONObject,
        onConflict: String? = null,
        dedupe: String = "${table}:${obj.optString("id", UUID.randomUUID().toString())}",
    ): JSONArray {
        val owner = queueOwnerId()
        if (table in SESSION_DERIVED_TABLES) RefreshGate.invalidate()
        val id = obj.optString("id").ifBlank { UUID.randomUUID().toString().also { obj.put("id", it) } }
        val base = if (table == "students") offlineDb.students().byId(owner, id)?.remoteUpdatedAt else null
        if (table == "students") {
            val current = offlineDb.students().byId(owner, id)?.asJson() ?: JSONObject()
            obj.keys().forEach { key -> current.put(key, obj.opt(key)) }
            current.put("id", id)
            upsertStudentLocal(owner, current, dirty = true)
            cache.put(owner, CACHE_STUDENTS, localStudentsJson(owner).toString())
        }
        if (table == "smart_plans") upsertPlanCache(owner, id, obj)
        if (table == "smart_plan_phases") upsertJsonArrayCache(owner, CACHE_PLAN_PHASES, obj)
        if (table == "smart_plan_phase_states") upsertJsonArrayCache(owner, CACHE_PLAN_PHASE_STATES, obj)
        if (table == "smart_plan_phase_templates") upsertJsonArrayCache(owner, CACHE_PLAN_PHASE_TEMPLATES, obj)
        if (table == "certificates") upsertJsonArrayCache(owner, CACHE_CERTIFICATES, obj)
        if (table == "certificate_templates") upsertJsonArrayCache(owner, CACHE_CERTIFICATE_TEMPLATES, obj)
        val queuedBody = if (table == "students") StudentSchemaContract.sanitize(offlineDb.students().byId(owner, id)?.asJson() ?: obj) else obj
        val path = if (table == "students") "/rest/v1/students?on_conflict=id"
        else "/rest/v1/$table${onConflict?.let { "?on_conflict=$it" }.orEmpty()}"
        queued("POST", path, queuedBody, if (table == "students") "student:$id" else dedupe, base, table, id)
        return JSONArray().put(queuedBody)
    }

    fun patch(
        table: String,
        filter: String,
        obj: JSONObject,
        dedupe: String = "$table:$filter",
    ): JSONArray {
        val owner = queueOwnerId()
        val id = if (table == "students") studentIdFromFilter(filter) else null
        val base = if (id != null) mergeStudentPatch(owner, id, obj) else null
        if (table == "students" && id != null) {
            val full = StudentSchemaContract.sanitize(offlineDb.students().byId(owner, id)?.asJson() ?: obj)
            queued("POST", "/rest/v1/students?on_conflict=id", full, "student:$id", base, table, id)
            return JSONArray().put(full)
        }
        if (table == "smart_plans") {
            Regex("(?:^|&)id=eq\\.([^&]+)").find(filter)?.groupValues?.getOrNull(1)?.let { planId ->
                upsertPlanCache(owner, planId, obj)
            }
        }
        val genericId = Regex("(?:^|&)id=eq\\.([^&]+)").find(filter)?.groupValues?.getOrNull(1)
        if (!genericId.isNullOrBlank()) {
            val cachedPatch = JSONObject(obj.toString()).put("id", genericId)
            when (table) {
                "smart_plan_phases" -> upsertJsonArrayCache(owner, CACHE_PLAN_PHASES, cachedPatch)
                "smart_plan_phase_states" -> upsertJsonArrayCache(owner, CACHE_PLAN_PHASE_STATES, cachedPatch)
                "smart_plan_phase_templates" -> upsertJsonArrayCache(owner, CACHE_PLAN_PHASE_TEMPLATES, cachedPatch)
                "certificates" -> upsertJsonArrayCache(owner, CACHE_CERTIFICATES, cachedPatch)
                "certificate_templates" -> upsertJsonArrayCache(owner, CACHE_CERTIFICATE_TEMPLATES, cachedPatch)
            }
        }
        // Generic PATCH rows also need their real entity id in the Outbox. Previously only
        // students populated entityId, so smart-plan failures could not be repaired/gated by id.
        queued("PATCH", "/rest/v1/$table?$filter", obj, dedupe, base, table, genericId ?: id)
        return JSONArray().put(obj)
    }

    fun delete(
        table: String,
        filter: String,
        dedupe: String = "$table:$filter:delete",
    ): JSONArray {
        val owner = queueOwnerId()
        val id = if (table == "students") studentIdFromFilter(filter) else null
        val base = id?.let { offlineDb.students().byId(owner, it)?.remoteUpdatedAt }
        if (id != null) {
            offlineDb.students().tombstone(owner, id)
            cache.put(owner, CACHE_STUDENTS, localStudentsJson(owner).toString())
        }
        queued("DELETE", "/rest/v1/$table?$filter", null, if (table == "students" && id != null) "student:$id" else dedupe, base, table, id)
        return JSONArray()
    }

    /** Queue-safe mutation RPC. RPC calls that represent state changes use the same
     * durable offline queue as REST writes instead of failing when connectivity drops. */
    fun rpcWrite(
        name: String,
        args: JSONObject = JSONObject(),
        dedupe: String = "rpc:$name:${UUID.randomUUID()}",
    ): String {
        val path = "/rest/v1/rpc/$name"
        val owner = queueOwnerId()
        if (!online()) {
            queued("POST", path, args, dedupe)
            return ""
        }
        // RC11.3: RPC mutations and Outbox flushing share one serialization lock.
        // This prevents ConnectivitySync/WorkManager/manual actions from sending the same
        // phase transition concurrently and exhausting PostgREST's connection pool.
        return synchronized(pushLock) {
            try {
                val result = withAuthRetry { api.rpc(name, args) }
                // A previous timeout may have left the exact same deterministic mutation
                // in the Outbox. Once the server confirms success, remove that stale retry.
                queue.discardDedupe(owner, dedupe)
                invalidatePlanSnapshotMemory()
                signalLocalMutation()
                result
            } catch (e: Exception) {
                if (!shouldQueue(e)) throw e
                queued("POST", path, args, dedupe)
                ""
            }
        }
    }

    private fun upsertPlanEventCache(owner: String, obj: JSONObject) {
        val current = cachedPlanEvents(owner) ?: JSONArray()
        val clientId = obj.optString("client_event_id")
        val output = JSONArray()
        var replaced = false
        for (index in 0 until current.length()) {
            val row = current.optJSONObject(index) ?: continue
            if (clientId.isNotBlank() && row.optString("client_event_id") == clientId) {
                output.put(JSONObject(obj.toString()))
                replaced = true
            } else output.put(row)
        }
        if (!replaced) output.put(JSONObject(obj.toString()))
        cache.put(owner, CACHE_PLAN_EVENTS, output.toString())
    }


    private fun removeJsonArrayCacheRow(owner: String, key: String, id: String, idField: String = "id") {
        val current = cache.get(owner, key, maxAge = null)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return
        val output = JSONArray()
        for (index in 0 until current.length()) {
            val row = current.optJSONObject(index) ?: continue
            if (row.optString(idField) != id) output.put(row)
        }
        cache.put(owner, key, output.toString())
    }

    private fun upsertJsonArrayCache(owner: String, key: String, obj: JSONObject, idField: String = "id") {
        val current = cache.get(owner, key, maxAge = null)?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
        val id = obj.optString(idField)
        val output = JSONArray()
        var replaced = false
        for (index in 0 until current.length()) {
            val row = current.optJSONObject(index) ?: continue
            if (id.isNotBlank() && row.optString(idField) == id) {
                val merged = JSONObject(row.toString())
                obj.keys().forEach { keyName -> merged.put(keyName, obj.opt(keyName)) }
                output.put(merged)
                replaced = true
            } else output.put(row)
        }
        if (!replaced) output.put(JSONObject(obj.toString()))
        cache.put(owner, key, output.toString())
    }

    fun recordPlanEvent(
        planId: String,
        studentId: String,
        eventType: String,
        payload: JSONObject = JSONObject(),
    ) {
        require(eventType in setOf(
            "milestone_approved", "auto_extension", "manual_credit", "manual_debit",
            "pause", "resume", "notification_sent", "retry_required", "catch_up",
            "phase_started", "phase_completed", "phase_reopened", "phase_shortfall_carried",
        )) { "نوع حدث الخطة غير صالح" }
        val milestoneKey = payload.optString("milestone_key").ifBlank { payload.optString("key") }
        val phaseIdForEvent = payload.optString("phase_id")
        val clientEventId = when {
            eventType == "milestone_approved" && milestoneKey.isNotBlank() -> RuntimeStabilityRules.milestoneEventKey(planId, studentId, milestoneKey)
            eventType in setOf("phase_started", "phase_completed", "phase_shortfall_carried") && phaseIdForEvent.isNotBlank() ->
                UUID.nameUUIDFromBytes("phase-event:$planId:$studentId:$eventType:$phaseIdForEvent".toByteArray()).toString()
            else -> UUID.randomUUID().toString()
        }
        val obj = JSONObject()
            .put("id", clientEventId)
            .put("client_event_id", clientEventId)
            .put("plan_id", planId)
            .put("student_id", studentId)
            .put("event_type", eventType)
            .put("event_date", LocalDate.now().toString())
            .put("payload", payload)
        payload.optString("phase_id").takeIf { it.isNotBlank() }?.let { obj.put("phase_id", it) }
        val owner = store.load()?.userId
        owner?.let { obj.put("created_by", it) }
        if (!owner.isNullOrBlank()) upsertPlanEventCache(owner, obj)
        write(
            "smart_plan_events",
            obj,
            onConflict = "client_event_id",
            dedupe = "plan-event:$clientEventId",
        )
        invalidatePlanSnapshotMemory()
    }


    fun savePlanPhase(draft: SmartPlanPhaseDraft): String {
        require(draft.planId.isNotBlank()) { "الخطة الأم غير صالحة" }
        require(draft.phaseOrder > 0) { "ترتيب المرحلة غير صالح" }
        require(draft.phaseType in setOf("hifz", "sard", "tathbit")) { "نوع المرحلة غير صالح" }
        run {
            // RC15: tathbit plans own tathbit phases only; general plans never get a tathbit phase.
            val parent = plans().firstOrNull { it.id == draft.planId }
            if (parent != null) {
                val tathbitPlan = parent.type == "tathbit" ||
                    (parent.scopeType == "student" && TrackActivityPolicy.isTathbit(studentTrackOf(parent.studentId)))
                if (tathbitPlan) require(draft.phaseType == "tathbit") { "خطة التثبيت تقبل مرحلة تثبيت فقط" }
                else require(draft.phaseType != "tathbit") { "مرحلة التثبيت لخطط مسار التثبيت فقط" }
            }
        }
        require(draft.goalMode in setOf("pages", "juz", "quran_range", "open")) { "نوع هدف المرحلة غير صالح" }
        require(draft.distributionMode in setOf("equal", "flexible")) { "طريقة توزيع الهدف غير صالحة" }
        require(draft.startMode in setOf("last_sard", "last_activity", "manual", "range_start", "plan_start")) { "بداية المرحلة غير صالحة" }
        require(draft.status in setOf("draft", "active", "completed", "archived")) { "حالة المرحلة غير صالحة" }
        val start = runCatching { LocalDate.parse(draft.startDate) }.getOrElse { error("تاريخ بداية المرحلة غير صالح") }
        val end = runCatching { LocalDate.parse(draft.endDate) }.getOrElse { error("تاريخ نهاية المرحلة غير صالح") }
        require(end >= start) { "تاريخ نهاية المرحلة يسبق البداية" }
        val weekdays = draft.scheduleWeekdays.distinct().filter { it in 0..6 }
        require(weekdays.isNotEmpty()) { "اختر يوم مرحلة واحدًا على الأقل" }
        // RC11.2: goal/start mode owns its fields. Hidden values from a previous mode must
        // never affect a later Sard assignment.
        val keepExplicitStart = draft.startMode in setOf("manual", "range_start") || draft.goalMode == "quran_range"
        // A page/juz/open goal owns only a start cursor. Its terminal is derived from the
        // requested amount at runtime. Keeping a stale end from a previous range edit can
        // silently turn a 30-page phase into "continue until the old end".
        val keepExplicitEnd = draft.goalMode == "quran_range"
        val normalizedRangeStartPage = draft.rangeStartPage.takeIf { keepExplicitStart }
        val normalizedRangeStartSurah = draft.rangeStartSurah.takeIf { keepExplicitStart }
        val normalizedRangeStartAyah = draft.rangeStartAyah.takeIf { keepExplicitStart }
        val normalizedRangeEndPage = draft.rangeEndPage.takeIf { keepExplicitEnd }
        val normalizedRangeEndSurah = draft.rangeEndSurah.takeIf { keepExplicitEnd }
        val normalizedRangeEndAyah = draft.rangeEndAyah.takeIf { keepExplicitEnd }
        if (draft.goalMode != "open") require((draft.targetPages ?: draft.targetUnits ?: 0.0) > 0.0) { "حدد هدف المرحلة" }
        if (draft.startMode in setOf("manual", "range_start")) {
            require((normalizedRangeStartSurah != null && normalizedRangeStartAyah != null) || normalizedRangeStartPage != null) {
                if (draft.phaseType == "sard") "حدد نقطة بداية السرد" else "حدد نقطة بداية المرحلة"
            }
        }
        if (draft.goalMode == "quran_range") {
            require((normalizedRangeStartSurah != null && normalizedRangeStartAyah != null) || normalizedRangeStartPage != null) { "حدد بداية النطاق القرآني" }
            require((normalizedRangeEndSurah != null && normalizedRangeEndAyah != null) || normalizedRangeEndPage != null) { "حدد نهاية النطاق القرآني" }
        }

        val parent = plans().firstOrNull { it.id == draft.planId } ?: error("لم يتم العثور على الخطة الأم")
        val currentPhases = planPhases(draft.planId).filter { it.status != "archived" }
        val existing = draft.id?.let { id -> currentPhases.firstOrNull { it.id == id } }
        if (existing != null) {
            require(existing.phaseType == draft.phaseType) { "لا يمكن تغيير نوع مرحلة بدأ استخدامها؛ أنشئ مرحلة جديدة بدلًا من ذلك" }
        } else {
            require(currentPhases.none { it.phaseType == draft.phaseType }) {
                if (draft.phaseType == "hifz") "RC11.2 يدعم مرحلة حفظ واحدة فقط" else "RC11.2 يدعم مرحلة سرد واحدة فقط"
            }
        }

        val id = draft.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val normalizedSettings = JSONObject(draft.settings.toString())
        if (draft.phaseType == "sard" && normalizedSettings.optString("route_direction").isBlank()) {
            val fallback = parent.settings.sardRouteDirection
                ?: if (parent.type == "hifz") SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH else SmartPlanPhaseEngine.ROUTE_FROM_BAQARAH_TO_NAS
            normalizedSettings.put("route_direction", fallback)
        }
        val proposedEngine = SmartPlanPhaseEngine.Phase(
            id = id, planId = draft.planId, order = draft.phaseOrder, type = draft.phaseType,
            startDate = start, endDate = end, scheduleWeekdays = weekdays.toSet(), goalMode = draft.goalMode,
            targetPages = draft.targetPages, targetUnits = draft.targetUnits, goalUnit = draft.goalUnit,
            distributionMode = draft.distributionMode, startMode = draft.startMode,
            rangeStartPage = normalizedRangeStartPage, rangeEndPage = normalizedRangeEndPage,
            rangeStartSurah = normalizedRangeStartSurah, rangeStartAyah = normalizedRangeStartAyah,
            rangeEndSurah = normalizedRangeEndSurah, rangeEndAyah = normalizedRangeEndAyah,
            allowSurplus = draft.allowSurplus, status = draft.status,
        )
        val timeline = (currentPhases.filter { it.id != id }.mapNotNull { it.toEnginePhase() } + proposedEngine)
        val blockingIssues = SmartPlanPhaseEngine.validateTimeline(timeline).filter { it.blocking }
        require(blockingIssues.isEmpty()) { blockingIssues.joinToString("؛ ") { it.message } }

        val now = java.time.Instant.now().toString()
        val expectedRevision = draft.expectedRevision ?: existing?.revision ?: 1
        val nextRevision = if (existing == null) 1 else expectedRevision + 1
        val obj = JSONObject()
            .put("id", id)
            .put("plan_id", draft.planId)
            .put("phase_order", draft.phaseOrder)
            .put("phase_type", draft.phaseType)
            .put("title", draft.title.ifBlank { when (draft.phaseType) { "sard" -> "مرحلة السرد"; "tathbit" -> "مرحلة التثبيت"; else -> "مرحلة الحفظ" } })
            .put("start_date", draft.startDate)
            .put("end_date", draft.endDate)
            .put("schedule_weekdays", JSONArray(weekdays))
            .put("goal_mode", draft.goalMode)
            .put("goal_unit", draft.goalUnit)
            .put("target_units", draft.targetUnits ?: JSONObject.NULL)
            .put("target_pages", draft.targetPages ?: JSONObject.NULL)
            .put("distribution_mode", draft.distributionMode)
            .put("start_mode", draft.startMode)
            .put("range_start_page", normalizedRangeStartPage ?: JSONObject.NULL)
            .put("range_end_page", normalizedRangeEndPage ?: JSONObject.NULL)
            .put("range_start_surah", normalizedRangeStartSurah ?: JSONObject.NULL)
            .put("range_start_ayah", normalizedRangeStartAyah ?: JSONObject.NULL)
            .put("range_end_surah", normalizedRangeEndSurah ?: JSONObject.NULL)
            .put("range_end_ayah", normalizedRangeEndAyah ?: JSONObject.NULL)
            .put("allow_surplus", draft.allowSurplus)
            .put("status", draft.status)
            .put("settings", normalizedSettings)
            .put("revision", nextRevision)
            .put("updated_at", now)
        store.load()?.userId?.let { obj.put("created_by", it) }
        if (existing == null) {
            write("smart_plan_phases", obj, onConflict = "id", dedupe = "plan-phase:$id")
        } else {
            obj.remove("created_by")
            obj.remove("id")
            obj.remove("plan_id")
            // RC11.2: update through a conditional RPC so a stale device gets an explicit
            // conflict instead of a successful 0-row PATCH. Offline replay uses the same RPC.
            rpcWrite(
                "rc112_update_plan_phase",
                JSONObject()
                    .put("p_phase_id", id)
                    .put("p_expected_revision", expectedRevision)
                    .put("p_patch", obj),
                dedupe = "plan-phase-update:$id:r$expectedRevision",
            )
            currentUserId()?.let { owner ->
                upsertJsonArrayCache(owner, CACHE_PLAN_PHASES, JSONObject(obj.toString()).put("id", id).put("plan_id", draft.planId))
            }
        }
        syncParentPlanFromPhases(draft.planId)
        invalidatePlanSnapshotMemory()
        return id
    }

    /** smart_plans is a compatibility container once RC11 phases exist. Keep its bounds and
     * legacy Hifz fields synchronized so older screens cannot become a second source of truth. */
    private fun syncParentPlanFromPhases(planId: String) {
        val phaseRows = planPhases(planId).filter { it.status != "archived" }
        if (phaseRows.isEmpty()) return
        val start = phaseRows.minOf { it.startDate }
        val end = phaseRows.maxOf { it.endDate }
        val hifz = phaseRows.firstOrNull { it.phaseType == "hifz" }
        val obj = JSONObject().put("start_date", start).put("end_date", end).put("updated_at", java.time.Instant.now().toString())
        if (hifz != null) {
            hifz.targetPages?.let { obj.put("target_pages", it).put("target_units", it) }
            obj.put("schedule_weekdays", JSONArray(hifz.scheduleWeekdays))
                .put("range_start_page", PlanRangeRebaseRules.normalizePages(hifz.rangeStartPage, hifz.rangeEndPage).first ?: JSONObject.NULL)
                .put("range_end_page", PlanRangeRebaseRules.normalizePages(hifz.rangeStartPage, hifz.rangeEndPage).second ?: JSONObject.NULL)
                .put("range_start_surah", hifz.rangeStartSurah ?: JSONObject.NULL)
                .put("range_start_ayah", hifz.rangeStartAyah ?: JSONObject.NULL)
                .put("range_end_surah", hifz.rangeEndSurah ?: JSONObject.NULL)
                .put("range_end_ayah", hifz.rangeEndAyah ?: JSONObject.NULL)
        }
        patch("smart_plans", "id=eq.$planId", obj, dedupe = "plan-container-sync:$planId")
    }

    fun upsertPlanPhaseState(
        phaseId: String,
        studentId: String,
        status: String,
        targetPagesOverride: Double? = null,
        startDateOverride: String? = null,
        endDateOverride: String? = null,
        frozenCompletedPages: Double? = null,
        frozenTargetPages: Double? = null,
        completionPercent: Double? = null,
        carryShortfallPages: Double? = null,
        settings: JSONObject? = null,
    ): String {
        require(status in setOf("not_started", "active", "completed", "skipped")) { "حالة الطالب داخل المرحلة غير صالحة" }
        require(carryShortfallPages == null || carryShortfallPages >= 0.0) { "العجز المرحل غير صالح" }
        val existing = planPhaseStates(studentId).firstOrNull { it.phaseId == phaseId }
        val id = existing?.id?.takeIf { it.isNotBlank() }
            ?: UUID.nameUUIDFromBytes("phase-state:$phaseId:$studentId".toByteArray()).toString()
        val now = java.time.Instant.now().toString()
        val mergedSettings = settings ?: existing?.settings ?: JSONObject()
        val obj = JSONObject()
            .put("id", id)
            .put("phase_id", phaseId)
            .put("student_id", studentId)
            .put("status", status)
            .put("target_pages_override", targetPagesOverride ?: existing?.targetPagesOverride ?: JSONObject.NULL)
            .put("start_date_override", startDateOverride ?: existing?.startDateOverride ?: JSONObject.NULL)
            .put("end_date_override", endDateOverride ?: existing?.endDateOverride ?: JSONObject.NULL)
            .put("frozen_completed_pages", frozenCompletedPages ?: existing?.frozenCompletedPages ?: JSONObject.NULL)
            .put("frozen_target_pages", frozenTargetPages ?: existing?.frozenTargetPages ?: JSONObject.NULL)
            .put("completion_percent", completionPercent ?: existing?.completionPercent ?: JSONObject.NULL)
            .put("carry_shortfall_pages", carryShortfallPages ?: existing?.carryShortfallPages ?: 0.0)
            .put("settings", mergedSettings)
            .put("updated_at", now)
        if (status == "completed") obj.put("completed_at", existing?.completedAt ?: now)
        else if (existing?.completedAt != null) obj.put("completed_at", existing.completedAt)
        store.load()?.userId?.let { obj.put("updated_by", it) }
        write("smart_plan_phase_states", obj, onConflict = "phase_id,student_id", dedupe = "phase-state:$phaseId:$studentId")
        return id
    }

    fun transitionPlanStudentPhase(
        planId: String,
        studentId: String,
        fromPhaseId: String,
        toPhaseId: String,
        completedPages: Double,
        targetPages: Double?,
        carryShortfallPages: Double = 0.0,
        toTargetPagesOverride: Double? = null,
    ) {
        transitionPlanStudentsBulk(
            planId = planId,
            fromPhaseId = fromPhaseId,
            toPhaseId = toPhaseId,
            rows = listOf(PhaseTransitionRequest(studentId, completedPages, targetPages, carryShortfallPages, toTargetPagesOverride)),
        )
    }

    /** RC11.2 atomic bulk transition. One queued RPC replaces four writes per student. */
    fun transitionPlanStudentsBulk(
        planId: String,
        fromPhaseId: String,
        toPhaseId: String,
        rows: List<PhaseTransitionRequest>,
    ) {
        require(rows.isNotEmpty()) { "لا يوجد طلاب للانتقال" }
        val payload = JSONArray()
        rows.distinctBy { it.studentId }.forEach { row ->
            val percent = row.targetPages?.takeIf { it > .001 }?.let { SmartPlanEngine.round(row.completedPages / it * 100.0, 1) }
            payload.put(
                JSONObject()
                    .put("student_id", row.studentId)
                    .put("completed_pages", row.completedPages.coerceAtLeast(0.0))
                    .put("target_pages", row.targetPages ?: JSONObject.NULL)
                    .put("completion_percent", percent ?: JSONObject.NULL)
                    .put("carry_shortfall_pages", row.carryShortfallPages.coerceAtLeast(0.0))
                    .put("sard_target_pages_override", row.sardTargetPagesOverride ?: JSONObject.NULL)
            )
        }
        val batchKey = UUID.nameUUIDFromBytes(
            "rc112-transition:$planId:$fromPhaseId:$toPhaseId:${rows.map { it.studentId }.sorted().joinToString(",")}".toByteArray()
        ).toString()
        rpcWrite(
            "rc112_transition_plan_students",
            JSONObject()
                .put("p_plan_id", planId)
                .put("p_from_phase_id", fromPhaseId)
                .put("p_to_phase_id", toPhaseId)
                .put("p_rows", payload),
            dedupe = "phase-transition-batch:$batchKey",
        )
        val destinationStartsNow = runCatching {
            val phase = planPhases(planId).firstOrNull { it.id == toPhaseId }
            phase == null || !LocalDate.parse(phase.startDate).isAfter(LocalDate.now())
        }.getOrDefault(true)
        val destinationStatus = if (destinationStartsNow) "active" else "not_started"
        // Optimistic cache only; do NOT enqueue duplicate REST writes beside the atomic RPC.
        currentUserId()?.let { owner ->
            rows.forEach { row ->
                val percent = row.targetPages?.takeIf { it > .001 }?.let { SmartPlanEngine.round(row.completedPages / it * 100.0, 1) }
                val fromId = UUID.nameUUIDFromBytes("phase-state:$fromPhaseId:${row.studentId}".toByteArray()).toString()
                val toId = UUID.nameUUIDFromBytes("phase-state:$toPhaseId:${row.studentId}".toByteArray()).toString()
                upsertJsonArrayCache(owner, CACHE_PLAN_PHASE_STATES, JSONObject()
                    .put("id", fromId).put("phase_id", fromPhaseId).put("student_id", row.studentId).put("status", "completed")
                    .put("frozen_completed_pages", row.completedPages).put("frozen_target_pages", row.targetPages ?: JSONObject.NULL)
                    .put("completion_percent", percent ?: JSONObject.NULL).put("completed_at", java.time.Instant.now().toString())
                    .put("carry_shortfall_pages", 0.0).put("settings", JSONObject()))
                upsertJsonArrayCache(owner, CACHE_PLAN_PHASE_STATES, JSONObject()
                    .put("id", toId).put("phase_id", toPhaseId).put("student_id", row.studentId).put("status", destinationStatus)
                    .put("target_pages_override", row.sardTargetPagesOverride ?: JSONObject.NULL)
                    .put("carry_shortfall_pages", row.carryShortfallPages).put("settings", JSONObject()))
            }
        }
        invalidatePlanSnapshotMemory()
    }

    /**
     * RC11.3 halaqa-wide cycle controller. A single durable RPC can prepare missing
     * phases, close each student's Hifz result, schedule/start Sard, apply individual
     * dates/targets/direction, and backfill unambiguous legacy recitations.
     */
    fun bulkConfigurePlanCycles(rows: List<BulkPlanCycleRequest>) {
        require(rows.isNotEmpty()) { "لا يوجد طلاب لتحديث دورة الحفظ والسرد" }
        val payload = JSONArray()
        rows.distinctBy { it.planId to it.studentId }.forEach { row ->
            val hifzStart = LocalDate.parse(row.hifzStartDate)
            val hifzEnd = LocalDate.parse(row.hifzEndDate)
            val sardStart = LocalDate.parse(row.sardStartDate)
            val sardEnd = LocalDate.parse(row.sardEndDate)
            require(hifzEnd >= hifzStart) { "نهاية الحفظ يجب ألا تسبق بدايته" }
            require(sardStart > hifzEnd) { "بداية السرد يجب أن تكون بعد نهاية الحفظ للطالب" }
            require(sardEnd >= sardStart) { "نهاية السرد يجب ألا تسبق بدايته" }
            require(row.sardTargetPages > 0.0) { "هدف السرد يجب أن يكون أكبر من صفر" }
            require(row.sardRouteDirection in setOf(
                SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH,
                SmartPlanPhaseEngine.ROUTE_FROM_BAQARAH_TO_NAS,
            )) { "اتجاه السرد غير صالح" }
            val pct = row.hifzTargetPages?.takeIf { it > .001 }
                ?.let { SmartPlanEngine.round(row.hifzCompletedPages.coerceAtLeast(0.0) / it * 100.0, 1) }
            payload.put(JSONObject()
                .put("plan_id", row.planId)
                .put("student_id", row.studentId)
                .put("hifz_completed_pages", row.hifzCompletedPages.coerceAtLeast(0.0))
                .put("hifz_target_pages", row.hifzTargetPages ?: JSONObject.NULL)
                .put("hifz_completion_percent", pct ?: JSONObject.NULL)
                .put("hifz_start_date", hifzStart.toString())
                .put("hifz_end_date", hifzEnd.toString())
                .put("sard_start_date", sardStart.toString())
                .put("sard_end_date", sardEnd.toString())
                .put("sard_target_pages", row.sardTargetPages)
                .put("sard_route_direction", row.sardRouteDirection)
                .put("sard_weekdays", JSONArray(row.sardWeekdays.distinct().filter { it in 0..6 }))
                .put("carry_shortfall_pages", row.carryShortfallPages.coerceAtLeast(0.0)))
        }
        val batchKey = UUID.nameUUIDFromBytes(
            ("rc113-cycle:" + rows.sortedBy { it.planId }.joinToString("|") {
                "${it.planId}:${it.studentId}:${it.hifzStartDate}:${it.hifzEndDate}:${it.sardStartDate}:${it.sardEndDate}:${it.sardTargetPages}:${it.sardRouteDirection}"
            }).toByteArray(),
        ).toString()
        rpcWrite(
            "rc113_bulk_configure_plan_cycles",
            JSONObject().put("p_rows", payload),
            dedupe = "rc113-cycle:$batchKey",
        )
        invalidatePlanSnapshotMemory()
    }

    fun completePlanPhaseForStudent(
        planId: String,
        phaseId: String,
        studentId: String,
        completedPages: Double,
        targetPages: Double?,
    ) {
        val percent = targetPages?.takeIf { it > .001 }?.let { SmartPlanEngine.round(completedPages / it * 100.0, 1) }
        upsertPlanPhaseState(
            phaseId = phaseId,
            studentId = studentId,
            status = "completed",
            frozenCompletedPages = completedPages,
            frozenTargetPages = targetPages,
            completionPercent = percent,
        )
        recordPlanEvent(
            planId = planId,
            studentId = studentId,
            eventType = "phase_completed",
            payload = JSONObject()
                .put("phase_id", phaseId)
                .put("completed_pages", completedPages)
                .put("target_pages", targetPages ?: JSONObject.NULL)
                .put("completion_percent", percent ?: JSONObject.NULL),
        )
    }

    fun setPlanPhaseStatus(phaseId: String, status: String) {
        require(status in setOf("draft", "active", "completed", "archived")) { "حالة المرحلة غير صالحة" }
        patch(
            "smart_plan_phases",
            "id=eq.$phaseId",
            JSONObject().put("status", status).put("updated_at", java.time.Instant.now().toString()),
            dedupe = "phase-status:$phaseId",
        )
    }

    fun savePlanPhaseTemplate(
        halaqaId: String,
        name: String,
        mode: String,
        definition: JSONObject,
        id: String? = null,
    ): String {
        require(mode in setOf("dates", "weeks", "manual")) { "نوع قالب المراحل غير صالح" }
        val templateId = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val obj = JSONObject()
            .put("id", templateId)
            .put("halaqa_id", halaqaId)
            .put("name", name.trim().ifBlank { "خطة الحفظ الشهرية" })
            .put("template_mode", mode)
            .put("definition", definition)
            .put("active", true)
            .put("updated_at", java.time.Instant.now().toString())
        store.load()?.userId?.let { obj.put("created_by", it) }
        write("smart_plan_phase_templates", obj, onConflict = "id", dedupe = "phase-template:$templateId")
        return templateId
    }

    fun applyPlanPhaseTemplate(plan: PlanRow, template: SmartPlanPhaseTemplateRow): List<String> {
        require(template.halaqaId == plan.halaqaId) { "القالب لا يخص هذه الحلقة" }
        require(template.templateMode in setOf("dates", "weeks", "manual")) { "نوع قالب المراحل غير صالح" }
        require(planPhases(plan.id).none { it.status != "archived" }) { "الخطة تحتوي مراحل بالفعل" }
        val planStart = LocalDate.parse(plan.startDate)
        val planEnd = LocalDate.parse(plan.endDate)
        val month = YearMonth.from(planStart)
        val definitions = mutableListOf<SmartPlanPhaseDraft>()

        fun clamp(date: LocalDate): LocalDate = when {
            date < planStart -> planStart
            date > planEnd -> planEnd
            else -> date
        }
        fun monthDay(day: Int, fallback: LocalDate): LocalDate {
            if (day <= 0) return clamp(month.atEndOfMonth())
            return clamp(month.atDay(day.coerceIn(1, month.lengthOfMonth())))
        }
        fun addDefaultPair(hifzStart: LocalDate, hifzEnd: LocalDate, sardStart: LocalDate, sardEnd: LocalDate, source: JSONObject = template.definition) {
            val safeHifzStart = clamp(hifzStart)
            val safeHifzEnd = clamp(maxOf(safeHifzStart, hifzEnd))
            val safeSardStart = clamp(maxOf(safeHifzEnd.plusDays(1), sardStart))
            val safeSardEnd = clamp(maxOf(safeSardStart, sardEnd))
            definitions += SmartPlanPhaseDraft(
                planId = plan.id, phaseOrder = 1, phaseType = "hifz", title = "مرحلة الحفظ",
                startDate = safeHifzStart.toString(), endDate = safeHifzEnd.toString(),
                scheduleWeekdays = plan.scheduleWeekdays.ifEmpty { listOf(0, 1, 2, 3, 4) },
                goalMode = "pages", goalUnit = "pages", targetPages = plan.target,
                distributionMode = source.optString("hifz_distribution", "flexible"), startMode = "plan_start",
                rangeStartPage = plan.rangeStartPage, rangeEndPage = plan.rangeEndPage,
                rangeStartSurah = plan.rangeStartSurah, rangeStartAyah = plan.rangeStartAyah,
                rangeEndSurah = plan.rangeEndSurah, rangeEndAyah = plan.rangeEndAyah,
            )
            val sardMode = source.optString("sard_goal_mode", "pages").takeIf { it in setOf("pages", "juz", "quran_range", "open") } ?: "pages"
            val sardUnits = if (sardMode == "open") null else source.optDouble("sard_target_units", if (sardMode == "juz") 1.0 else SmartPlanPhaseEngine.DEFAULT_SARD_TARGET_PAGES).takeIf { it > 0.0 }
            val sardPages = when (sardMode) {
                "open" -> null
                "juz" -> sardUnits?.times(20.0)
                else -> source.optDouble("sard_target_pages", sardUnits ?: SmartPlanPhaseEngine.DEFAULT_SARD_TARGET_PAGES).takeIf { it > 0.0 }
            }
            definitions += SmartPlanPhaseDraft(
                planId = plan.id, phaseOrder = 2, phaseType = "sard", title = "مرحلة السرد",
                startDate = safeSardStart.toString(), endDate = safeSardEnd.toString(),
                scheduleWeekdays = listOf(0, 1, 2, 3, 4, 5),
                goalMode = sardMode, goalUnit = when (sardMode) { "juz" -> "juz"; "quran_range" -> "range"; "open" -> "open"; else -> "pages" },
                targetUnits = sardUnits, targetPages = sardPages,
                distributionMode = source.optString("sard_distribution", "flexible"),
                startMode = source.optString("sard_start_mode", "last_sard"),
                settings = JSONObject().put(
                    "route_direction",
                    source.optString("sard_route_direction").takeIf { it in setOf(
                        SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH, SmartPlanPhaseEngine.ROUTE_FROM_BAQARAH_TO_NAS
                    ) } ?: if (plan.type == "hifz") SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH else SmartPlanPhaseEngine.ROUTE_FROM_BAQARAH_TO_NAS
                ),
            )
        }

        when (template.templateMode) {
            "dates" -> {
                fun offsetOrNull(name: String): Long? = if (template.definition.has(name) && !template.definition.isNull(name)) template.definition.optLong(name) else null
                val hifzStart = offsetOrNull("hifz_start_offset_days")?.let { clamp(planStart.plusDays(it)) }
                    ?: monthDay(template.definition.optInt("hifz_start_day", 1), planStart)
                val hifzEnd = offsetOrNull("hifz_end_offset_days")?.let { clamp(planStart.plusDays(it)) }
                    ?: monthDay(template.definition.optInt("hifz_end_day", 24), planStart.plusDays(23))
                val sardStart = offsetOrNull("sard_start_offset_days")?.let { clamp(planStart.plusDays(it)) }
                    ?: monthDay(template.definition.optInt("sard_start_day", hifzEnd.dayOfMonth + 1), hifzEnd.plusDays(1))
                val sardEnd = offsetOrNull("sard_end_offset_days")?.let { clamp(planStart.plusDays(it)) }
                    ?: template.definition.optInt("sard_end_day", 0).let { if (it <= 0) planEnd else monthDay(it, planEnd) }
                addDefaultPair(hifzStart, hifzEnd, sardStart, sardEnd)
            }
            "weeks" -> {
                val hifzWeeks = template.definition.optInt("hifz_weeks", 3).coerceAtLeast(1)
                val sardWeeks = template.definition.optInt("sard_weeks", 1).coerceAtLeast(1)
                val cycleDays = java.time.temporal.ChronoUnit.DAYS.between(planStart, planEnd) + 1L
                require(cycleDays >= (hifzWeeks + sardWeeks) * 7L) {
                    "مدة الخطة أقصر من قالب ${hifzWeeks} أسابيع حفظ + ${sardWeeks} أسبوع سرد"
                }
                // Extra days belong to Hifz; Sard remains the requested final N weeks.
                val sardStart = planEnd.minusDays(sardWeeks * 7L - 1L)
                val minimumHifzEnd = planStart.plusDays(hifzWeeks * 7L - 1L)
                require(sardStart.minusDays(1) >= minimumHifzEnd) { "قالب الأسابيع يسبب تداخلًا بين الحفظ والسرد" }
                addDefaultPair(planStart, sardStart.minusDays(1), sardStart, planEnd)
            }
            else -> {
                val array = template.definition.optJSONArray("phases") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val row = array.optJSONObject(i) ?: continue
                    val order = row.optInt("phase_order", i + 1).coerceAtLeast(1)
                    val type = row.optString("phase_type", if (order == 1) "hifz" else "sard")
                    val startOffset = row.optLong("start_offset_days", Long.MIN_VALUE)
                    val endOffset = row.optLong("end_offset_days", Long.MIN_VALUE)
                    val start = if (startOffset != Long.MIN_VALUE) clamp(planStart.plusDays(startOffset)) else clamp(runCatching { LocalDate.parse(row.optString("start_date")) }.getOrDefault(planStart))
                    val end = if (endOffset != Long.MIN_VALUE) clamp(planStart.plusDays(endOffset)) else clamp(runCatching { LocalDate.parse(row.optString("end_date")) }.getOrDefault(planEnd))
                    val mode = row.optString("goal_mode", "pages")
                    val units = if (row.has("target_units") && !row.isNull("target_units")) row.optDouble("target_units") else null
                    val pages = if (row.has("target_pages") && !row.isNull("target_pages")) row.optDouble("target_pages")
                        else if (type == "hifz") plan.target
                        else if (type == "sard" && mode == "pages") SmartPlanPhaseEngine.DEFAULT_SARD_TARGET_PAGES
                        else null
                    val weekdays = row.optJSONArray("schedule_weekdays")?.let { a -> (0 until a.length()).map { a.optInt(it) }.filter { it in 0..6 } }
                        ?: if (type == "sard") listOf(0, 1, 2, 3, 4, 5) else plan.scheduleWeekdays
                    definitions += SmartPlanPhaseDraft(
                        planId = plan.id, phaseOrder = order, phaseType = type, title = row.optString("title", if (type == "sard") "مرحلة السرد" else "مرحلة الحفظ"),
                        startDate = start.toString(), endDate = maxOf(start, end).toString(), scheduleWeekdays = weekdays,
                        goalMode = mode, goalUnit = row.optString("goal_unit", when (mode) { "juz" -> "juz"; "quran_range" -> "range"; "open" -> "open"; else -> "pages" }),
                        targetUnits = units, targetPages = pages,
                        distributionMode = row.optString("distribution_mode", "flexible"), startMode = row.optString("start_mode", if (type == "sard") "last_sard" else "plan_start"),
                        rangeStartPage = row.optInt("range_start_page").takeIf { row.has("range_start_page") && !row.isNull("range_start_page") && it in 1..604 },
                        rangeEndPage = row.optInt("range_end_page").takeIf { row.has("range_end_page") && !row.isNull("range_end_page") && it in 1..604 },
                        rangeStartSurah = row.optInt("range_start_surah").takeIf { row.has("range_start_surah") && !row.isNull("range_start_surah") && it in 1..114 },
                        rangeStartAyah = row.optInt("range_start_ayah").takeIf { row.has("range_start_ayah") && !row.isNull("range_start_ayah") && it > 0 },
                        rangeEndSurah = row.optInt("range_end_surah").takeIf { row.has("range_end_surah") && !row.isNull("range_end_surah") && it in 1..114 },
                        rangeEndAyah = row.optInt("range_end_ayah").takeIf { row.has("range_end_ayah") && !row.isNull("range_end_ayah") && it > 0 },
                        allowSurplus = row.optBoolean("allow_surplus", true),
                        settings = row.optJSONObject("settings") ?: JSONObject().also { settings ->
                            if (type == "sard") settings.put("route_direction", if (plan.type == "hifz") SmartPlanPhaseEngine.ROUTE_FROM_NAS_TO_BAQARAH else SmartPlanPhaseEngine.ROUTE_FROM_BAQARAH_TO_NAS)
                        },
                    )
                }
                require(definitions.isNotEmpty()) { "القالب اليدوي لا يحتوي مراحل" }
            }
        }
        return definitions.sortedBy { it.phaseOrder }.map(::savePlanPhase)
    }

    fun setPlanStatus(planId: String, status: String) {
        require(status in setOf("draft", "active", "paused", "completed", "archived")) { "حالة الخطة غير صالحة" }
        patch(
            "smart_plans",
            "id=eq.$planId",
            JSONObject()
                .put("status", status)
                .put("updated_at", java.time.Instant.now().toString()),
            dedupe = "plan-status:$planId",
        )
    }

    fun saveSmartPlan(draft: SmartPlanDraft): String {
        require(draft.scopeType in setOf("student", "category", "halaqa")) { "نطاق الخطة غير صالح" }
        require(draft.type in setOf("hifz", "review", "sard", "tathbit")) { "نوع الخطة غير صالح" }
        require(draft.reviewTier in setOf("none", "near", "far", "both")) { "مسار المراجعة غير صالح" }
        require(draft.minimumEvaluation in setOf("excellent", "very_good", "good", "repeat")) { "أقل تقييم غير صالح" }
        // RC23: smart-plan milestone exams were removed from the plan lifecycle.
        require(draft.status in setOf("draft", "active", "paused", "completed", "archived")) { "حالة الخطة غير صالحة" }
        require(draft.halaqaId.isNotBlank()) { "اختر الحلقة" }
        when (draft.scopeType) {
            "student" -> require(!draft.studentId.isNullOrBlank()) { "اختر الطالب" }
            "category" -> require(!draft.categoryId.isNullOrBlank()) { "اختر الفئة" }
        }
        val start = runCatching { LocalDate.parse(draft.startDate) }.getOrElse { error("تاريخ البداية غير صالح") }
        val end = runCatching { LocalDate.parse(draft.endDate) }.getOrElse { error("تاريخ النهاية غير صالح") }
        require(end >= start) { "تاريخ النهاية يجب ألا يسبق البداية" }
        require(draft.targetPages > 0.0 && draft.targetPages <= 604.0) { "هدف الخطة يجب أن يكون بين 0 و604 صفحة" }
        val weekdays = draft.scheduleWeekdays.distinct().filter { it in 0..6 }
        require(weekdays.isNotEmpty()) { "اختر يوم دوام واحداً على الأقل" }
        require(draft.catchUpWeekday == null || draft.catchUpWeekday in 0..6) { "يوم الاستدراك غير صالح" }
        require(draft.maxDailyMultiplier in 1.0..3.0) { "أقصى زيادة يومية غير صالحة" }
        require(draft.delayAlertDays in 1..30) { "عدد أيام تنبيه التأخر غير صالح" }
        require(draft.nearRevisionPages in 0.0..30.0) { "المراجعة القريبة غير صالحة" }
        require(draft.farRevisionPages in 0.0..604.0) { "المراجعة البعيدة غير صالحة" }
        require(draft.rangeStartPage == null || draft.rangeStartPage in 1..604)
        require(draft.rangeEndPage == null || draft.rangeEndPage in 1..604)

        // RC15: scope target normalization mirrors smart_plans_scope_target_check exactly.
        val scopedStudentId = draft.studentId?.takeIf { draft.scopeType == "student" && it.isNotBlank() }
        val scopedCategoryId = draft.categoryId?.takeIf { draft.scopeType == "category" && it.isNotBlank() }
        val effectiveType = if (draft.scopeType == "student") TrackActivityPolicy.planTypeFor(studentTrackOf(scopedStudentId), draft.type) else draft.type
        // RC27.6: a start with an undersized range (e.g. a stale one-line end) gets its end computed from the target.
        @Suppress("NAME_SHADOWING")
        val draft = withAutoFilledRange(draft, effectiveType)
        val now = java.time.Instant.now().toString()
        val planId = draft.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val settings = JSONObject()
            .put("minimum_daily_mode", draft.minimumDailyMode.ifBlank { "dynamic" })
            .put("minimum_daily_pages", draft.minimumDailyPages.coerceAtLeast(0.0))
            .put("intensive_weekdays", JSONArray(draft.intensiveWeekdays.distinct().filter { it in 0..6 }))
            .put("memorization_line_keys", JSONArray(draft.memorizationLineKeys.filter { it.isNotBlank() }))
            .put("enabled_activities", JSONArray(SmartPlanActivityRules.enabledActivities(effectiveType, draft.enabledActivities)))
            .put("sard_target_pages", draft.sardTargetPages.coerceIn(0.0, 604.0))
        draft.intensiveWeekFrom?.takeIf { it.isNotBlank() }?.let { settings.put("intensive_week_from", it) }
        draft.intensiveWeekTo?.takeIf { it.isNotBlank() }?.let { settings.put("intensive_week_to", it) }
        draft.routeDirection?.takeIf { it.isNotBlank() }?.let { settings.put("route_direction", it) }
        draft.sardRouteDirection?.takeIf { it.isNotBlank() }?.let { settings.put("sard_route_direction", it) }

        val obj = JSONObject()
            .put("id", planId)
            .put("scope_type", draft.scopeType)
            .put("halaqa_id", draft.halaqaId)
            .put("student_id", scopedStudentId ?: JSONObject.NULL)
            .put("category_id", scopedCategoryId ?: JSONObject.NULL)
            .put("parent_plan_id", draft.parentPlanId?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("plan_type", effectiveType)
            .put("review_tier", if (effectiveType == "review") draft.reviewTier else "none")
            .put("title", draft.title.trim().ifBlank { "${draft.type.uppercase()} ${draft.startDate}" })
            .put("goal_unit", "pages")
            .put("target_units", draft.targetPages)
            .put("target_pages", draft.targetPages)
            .put("start_date", draft.startDate)
            .put("end_date", draft.endDate)
            .put("schedule_weekdays", JSONArray(weekdays))
            .put("catch_up_weekday", draft.catchUpWeekday ?: JSONObject.NULL)
            .put("max_daily_multiplier", draft.maxDailyMultiplier)
            .put("auto_extend", false)
            .put("quality_gate_enabled", draft.qualityGateEnabled)
            .put("minimum_evaluation", draft.minimumEvaluation)
            .put("range_start_page", PlanRangeRebaseRules.normalizePages(draft.rangeStartPage, draft.rangeEndPage).first ?: JSONObject.NULL)
            .put("range_end_page", PlanRangeRebaseRules.normalizePages(draft.rangeStartPage, draft.rangeEndPage).second ?: JSONObject.NULL)
            .put("range_start_surah", draft.rangeStartSurah ?: JSONObject.NULL)
            .put("range_start_ayah", draft.rangeStartAyah ?: JSONObject.NULL)
            .put("range_end_surah", draft.rangeEndSurah ?: JSONObject.NULL)
            .put("range_end_ayah", draft.rangeEndAyah ?: JSONObject.NULL)
            .put("near_revision_pages", draft.nearRevisionPages)
            .put("far_revision_pages", draft.farRevisionPages)
            .put("milestone_mode", "none")
            .put("milestone_requires_approval", false)
            .put("delay_alert_days", draft.delayAlertDays)
            .put("home_prep_push", draft.homePrepPush)
            .put("status", draft.status)
            .put("note", draft.note.trim().takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("settings", settings)
            .put("updated_at", now)

        // RC11.2 single-source rule: after phase adoption, smart_plans is a compatibility
        // container. Legacy editor changes must not overwrite phase-owned dates/goal/range.
        if (!draft.id.isNullOrBlank()) {
            val phaseRows = planPhases(planId).filter { it.status != "archived" }
            if (phaseRows.isNotEmpty()) {
                val existingPlan = plans().firstOrNull { it.id == planId } ?: error("الخطة غير موجودة")
                require(draft.halaqaId == existingPlan.halaqaId && draft.scopeType == existingPlan.scopeType &&
                    scopedStudentId == existingPlan.studentId?.takeIf { it.isNotBlank() } &&
                    scopedCategoryId == existingPlan.categoryId?.takeIf { it.isNotBlank() }) {
                    "لا يمكن تغيير نطاق خطة تحتوي مراحل؛ أنشئ خطة جديدة أو عدّل المراحل"
                }
                val hifz = phaseRows.firstOrNull { it.phaseType == "hifz" }
                obj.put("plan_type", existingPlan.type)
                    .put("start_date", phaseRows.minOf { it.startDate })
                    .put("end_date", phaseRows.maxOf { it.endDate })
                hifz?.let { phase ->
                    val target = phase.targetPages ?: if (phase.goalMode == "juz") phase.targetUnits?.times(20.0) else null
                    target?.let { obj.put("target_units", it).put("target_pages", it) }
                    obj.put("schedule_weekdays", JSONArray(phase.scheduleWeekdays))
                        .put("range_start_page", PlanRangeRebaseRules.normalizePages(phase.rangeStartPage, phase.rangeEndPage).first ?: JSONObject.NULL)
                        .put("range_end_page", PlanRangeRebaseRules.normalizePages(phase.rangeStartPage, phase.rangeEndPage).second ?: JSONObject.NULL)
                        .put("range_start_surah", phase.rangeStartSurah ?: JSONObject.NULL)
                        .put("range_start_ayah", phase.rangeStartAyah ?: JSONObject.NULL)
                        .put("range_end_surah", phase.rangeEndSurah ?: JSONObject.NULL)
                        .put("range_end_ayah", phase.rangeEndAyah ?: JSONObject.NULL)
                }
            }
        }
        store.load()?.userId?.let { obj.put("created_by", it) }

        if (draft.id.isNullOrBlank()) {
            write("smart_plans", obj, onConflict = "id", dedupe = "smart-plan:$planId")
        } else {
            obj.remove("created_by")
            obj.remove("id")
            patch("smart_plans", "id=eq.$planId", obj, dedupe = "smart-plan-update:$planId")
        }
        return planId
    }

    fun guardianDashboard(studentId: String, from: String, to: String): JSONObject {
        require(studentId.isNotBlank()) { "معرّف الطالب غير صالح" }
        require(guardianOwnsCachedChild(studentId)) { "هذا الطالب غير مرتبط بحساب ولي الأمر" }
        val start = runCatching { LocalDate.parse(from) }.getOrElse { error("تاريخ البداية غير صالح") }
        val end = runCatching { LocalDate.parse(to) }.getOrElse { error("تاريخ النهاية غير صالح") }
        require(end >= start) { "الفترة غير صالحة" }
        val owner = currentUserId()
        val key = "$CACHE_GUARDIAN_DASHBOARD_PREFIX$studentId:$from:$to"
        val cached = owner?.let { cache.get(it, key, maxAge = null) }?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (!online()) return cached ?: JSONObject()
        val result = runCatching {
            val raw = withAuthRetry {
                api.rpc(
                    "guardian_student_dashboard_range",
                    JSONObject()
                        .put("p_student_id", studentId)
                        .put("p_from", start.toString())
                        .put("p_to", end.toString()),
                )
            }
            JSONObject(raw)
        }.getOrElse { cached ?: JSONObject() }
        if (result.length() > 0) owner?.let { cache.put(it, key, result.toString()) }
        return result
    }

    /** RC17: cache-only read, instant (no network). Null when nothing was cached yet. */
    fun cachedGuardianChildOverview(studentId: String): JSONObject? {
        val owner = currentUserId() ?: return null
        if (!guardianOwnsCachedChild(studentId)) return null
        return cache.get(owner, CACHE_GUARDIAN_OVERVIEW_PREFIX + studentId, maxAge = null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    /** RC17: one RPC for the guardian home; stale cache is returned when offline or on failure. */
    fun guardianChildOverview(studentId: String): JSONObject? {
        require(studentId.isNotBlank()) { "معرّف الطالب غير صالح" }
        require(guardianOwnsCachedChild(studentId)) { "هذا الطالب غير مرتبط بحساب ولي الأمر" }
        val cached = cachedGuardianChildOverview(studentId)
        if (!online()) return cached?.also { localizeGuardianOverview(studentId, it) }
        val owner = currentUserId() ?: return cached
        val fresh = runCatching {
            JSONObject(withAuthRetry { api.rpc("guardian_child_overview", JSONObject().put("p_student_id", studentId)) })
        }.getOrNull()
        if (fresh != null && fresh.length() > 0) {
            localizeGuardianOverview(studentId, fresh)
            cache.put(owner, CACHE_GUARDIAN_OVERVIEW_PREFIX + studentId, fresh.toString())
            return fresh
        }
        return cached?.also { localizeGuardianOverview(studentId, it) }
    }

    /**
     * RC38: the guardian's month figures are computed ON THE DEVICE from the child's own recitations, with the same rules the
     * teacher screens use (Hifz and Sard as one first-ayah..last-ayah stretch, repeats counted once; empty placeholder rows
     * ignored in the quality average). The server's totals simply add up pages, so they are overwritten here. Anything the
     * device cannot read (or when nothing was fetched yet) leaves the server's figures untouched.
     */
    /** RC44: the guardian sees the SAME plan figures as the teacher: month facts first, then the plan engine's phase numbers on top. */
    private fun localizeGuardianOverview(studentId: String, json: JSONObject) {
        localizeGuardianMonth(studentId, json)
        unifyGuardianWithPlan(studentId, json)
        attachGuardianWard(studentId, json)
        attachGuardianPhoto(studentId, json)
    }

    /**
     * RC82: the child's photo for the guardian home. Downloaded once (small RPC that checks the guardian-student link),
     * kept on the device, and read again from the server only once per refresh cycle (see RefreshGate).
     */
    private fun attachGuardianPhoto(studentId: String, json: JSONObject) {
        val owner = currentUserId() ?: return
        val key = "guardian-photo:$studentId"
        var photo = cache.get(owner, key, maxAge = null)
        if ((photo == null || RefreshGate.due(key)) && online()) {
            val raw = runCatching { withAuthRetry { api.rpc("guardian_child_photo", JSONObject().put("p_student_id", studentId)) } }.getOrNull()
            if (raw != null) {
                val value = runCatching { org.json.JSONTokener(raw.trim()).nextValue() }.getOrNull()
                photo = (value as? String)?.takeIf { it.startsWith("data:image/") } ?: "-"
                cache.put(owner, key, photo)
                RefreshGate.mark(key)
            }
        }
        if (photo != null && photo.startsWith("data:image/")) json.optJSONObject("student")?.put("photo_path", photo)
    }

    /** RC52: today's ward (from the same plan snapshot the teacher sees) for the guardian home "today" card. */
    private fun attachGuardianWard(studentId: String, json: JSONObject) {
        runCatching {
            val snaps = planSnapshots(forceRefresh = false).filter { it.student.id == studentId }
            val snap = snaps.firstOrNull { it.plan.type == "hifz" } ?: snaps.firstOrNull() ?: return@runCatching
            val a = snap.progress.assignment
            if (a.pages <= 0.0 && a.lineKeys.isEmpty()) { json.put("ward", JSONObject().put("text", JSONObject.NULL)); return@runCatching }
            val kind = when (a.kind) { "hifz" -> "حفظ"; "review" -> "مراجعة"; "sard" -> "سرد"; "tathbit" -> "تثبيت"; else -> "مقرر" }
            val exact = a.lineKeys.takeIf { it.isNotEmpty() }?.let { runCatching { QuranCore.engine().endpointsForLineKeys(it) }.getOrNull() }
            val range = exact?.let { (x, y) ->
                com.mohammedsamir.halaqati.quran.RecitationParityRules.quranRangeLabel(x.surah, x.ayah, y.surah, y.ayah, a.startPage, a.endPage)
            }
            val text = range?.let { "$kind: $it" }
            json.put("ward", JSONObject().put("text", text ?: JSONObject.NULL).put("pages", a.pages))
        }
    }

    /**
     * Target and done of the CURRENT phase (hifz or sard) come from the plan engine (the same numbers as the plans page:
     * quality-gated, inside the phase dates); the other phase keeps the month facts. Nothing is replaced when no snapshot exists.
     */
    private fun unifyGuardianWithPlan(studentId: String, json: JSONObject) {
        runCatching {
            val snap = planSnapshots(forceRefresh = false)
                .firstOrNull { it.student.id == studentId && (it.plan.type == "hifz" || it.plan.type == "sard") && it.phaseProgress != null }
                ?: return@runCatching
            val pp = snap.phaseProgress ?: return@runCatching
            val target = pp.targetPages ?: return@runCatching
            val key = snap.phase?.phaseType?.takeIf { it == "hifz" || it == "sard" } ?: return@runCatching
            val plan = json.optJSONObject("plan") ?: return@runCatching
            val month = json.optJSONObject("month") ?: JSONObject().also { json.put("month", it) }
            if (key == "hifz") plan.put("target_pages", target) else plan.put("sard_target_pages", target)
            month.put(key, pp.completedPages)
            val acts = plan.optJSONArray("activities")
            if (acts != null) {
                for (i in 0 until acts.length()) {
                    val a = acts.optJSONObject(i) ?: continue
                    if (a.optString("key") == key) { a.put("target_pages", target); a.put("done_pages", pp.completedPages) }
                }
            }
        }
    }

    private fun localizeGuardianMonth(studentId: String, json: JSONObject) {
        runCatching {
            val monthStart = LocalDate.now().withDayOfMonth(1).toString()
            val rows = table(
                "recitation_entries",
                "select=activity_type,start_surah,start_ayah,end_surah,end_ayah,pages_count,evaluation,safety_percentage,mistakes,prompts," +
                    "sessions!inner(session_date,status)&student_id=eq.$studentId&sessions.session_date=gte.$monthStart&sessions.status=neq.cancelled&limit=3000",
            )
            val list = (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
            if (list.isEmpty()) return@runCatching
            val month = json.optJSONObject("month") ?: JSONObject().also { json.put("month", it) }
            val track = json.optJSONObject("student")?.optString("track").orEmpty()
            fun entriesOf(type: String) = list.filter { it.optString("activity_type") == type }.map(::hifzSpanEntryOf)
            if (track != "tathbit") {
                if (list.any { it.optString("activity_type") == "new_hifz" }) month.put("hifz", com.mohammedsamir.halaqati.plans.HifzSpanRules.pages(entriesOf("new_hifz")))
                if (list.any { it.optString("activity_type") == "sard" }) month.put("sard", com.mohammedsamir.halaqati.plans.HifzSpanRules.pages(entriesOf("sard")))
            }
            val safeties = list.filter { it.has("safety_percentage") && !it.isNull("safety_percentage") }.filterNot {
                com.mohammedsamir.halaqati.plans.RecitationQualityRules.isPlaceholder(it.optString("evaluation"), it.optDouble("safety_percentage"), it.optDouble("pages_count", 0.0))
            }.map { it.optDouble("safety_percentage") }
            if (safeties.isNotEmpty()) month.put("quality", Math.round(safeties.average() * 10.0) / 10.0)
            // RC52: latest recitation day (today when he recited today): mistakes, prompts and quality of that same day.
            runCatching {
                val real = list.filterNot {
                    com.mohammedsamir.halaqati.plans.RecitationQualityRules.isPlaceholder(it.optString("evaluation"), it.optDouble("safety_percentage"), it.optDouble("pages_count", 0.0))
                }.filter { it.optString("activity_type") != "test" }
                val byDay = real.groupBy { it.optJSONObject("sessions")?.optString("session_date").orEmpty() }.filterKeys { it.isNotBlank() }
                val day = byDay.keys.maxOrNull()
                if (day != null) {
                    val rows = byDay.getValue(day)
                    val q = rows.filter { it.has("safety_percentage") && !it.isNull("safety_percentage") }.map { it.optDouble("safety_percentage") }
                    json.put("last_day", JSONObject()
                        .put("date", day)
                        .put("is_today", day == LocalDate.now().toString())
                        .put("mistakes", rows.sumOf { it.optInt("mistakes", 0) })
                        .put("prompts", rows.sumOf { it.optInt("prompts", 0) })
                        .put("quality", if (q.isEmpty()) JSONObject.NULL else Math.round(q.average() * 10.0) / 10.0))
                }
            }
            json.optJSONObject("plan")?.optJSONArray("activities")?.let { acts ->
                for (i in 0 until acts.length()) {
                    val a = acts.optJSONObject(i) ?: continue
                    when (a.optString("key")) {
                        "hifz" -> if (month.has("hifz")) a.put("done_pages", month.optDouble("hifz"))
                        "sard" -> if (month.has("sard")) a.put("done_pages", month.optDouble("sard"))
                    }
                }
            }
        }
    }

    fun guardianCompetition(
        studentId: String,
        track: String,
        scope: String,
        from: String,
        to: String,
    ): JSONArray {
        require(studentId.isNotBlank()) { "معرّف الطالب غير صالح" }
        require(guardianOwnsCachedChild(studentId)) { "هذا الطالب غير مرتبط بحساب ولي الأمر" }
        require(track in setOf("hifz", "review", "sard", "tathbit")) { "نوع الترتيب غير صالح" }
        require(scope in setOf("center", "category", "halaqa")) { "نطاق الترتيب غير صالح" }
        val start = runCatching { LocalDate.parse(from) }.getOrElse { error("تاريخ البداية غير صالح") }
        val end = runCatching { LocalDate.parse(to) }.getOrElse { error("تاريخ النهاية غير صالح") }
        require(end >= start) { "الفترة غير صالحة" }
        val owner = currentUserId()
        val key = "$CACHE_GUARDIAN_COMPETITION_PREFIX$studentId:$track:$scope:$from:$to"
        val cached = owner?.let { cache.get(it, key, maxAge = null) }?.let { runCatching { JSONArray(it) }.getOrNull() }
        if (!online()) return cached ?: JSONArray()
        val result = runCatching {
            val raw = withAuthRetry {
                api.rpc(
                    "guardian_competition_board_range",
                    JSONObject()
                        .put("p_student_id", studentId)
                        .put("p_track", track)
                        .put("p_scope", scope)
                        .put("p_from", start.toString())
                        .put("p_to", end.toString()),
                )
            }
            JSONArray(raw)
        }.getOrElse { cached ?: JSONArray() }
        if (result.length() > 0) owner?.let { cache.put(it, key, result.toString()) }
        return result
    }

    /** RC18: immutable month archive visible only through server-side student access checks. */
    /**
     * RC27: once a day, mark very old unread notifications read and delete old read ones (own only).
     * Needs migration RC27; before it is applied the RPC fails quietly and nothing is marked as done.
     */
    fun cleanupNotificationsIfDue(): Boolean {
        val owner = currentUserId() ?: return false
        if (!online()) return false
        if (cache.get(owner, "rc27:notification-cleanup", Duration.ofHours(24)) != null) return false
        val ok = runCatching {
            withAuthRetry { api.rpc("rc27_cleanup_notifications", JSONObject().put("p_days", 30).put("p_all", false)) }
        }.isSuccess
        if (ok) cache.put(owner, "rc27:notification-cleanup", "{}")
        return ok
    }

    data class BackupSummary(val tables: Int, val rows: Int, val failed: List<String>, val skipped: List<String>)

    /**
     * RC27: full read-only export of the important tables into a zip (one JSON per table + MANIFEST.json).
     * Requires being online. What is exported is exactly what the signed-in account may read (admin: everything).
     */
    fun exportBackup(out: java.io.OutputStream, onProgress: (String) -> Unit = {}): BackupSummary {
        check(online()) { "النسخة الاحتياطية تحتاج اتصالًا بالإنترنت" }
        val zip = java.util.zip.ZipOutputStream(out.buffered())
        val manifest = JSONObject().put("created_at", java.time.Instant.now().toString()).put("app_version", com.mohammedsamir.halaqati.BuildConfig.VERSION_NAME)
        val tablesJson = JSONObject()
        val failed = ArrayList<String>()
        val skipped = ArrayList<String>()
        var totalRows = 0
        var done = 0
        for (table in BackupRules.TABLES) {
            onProgress("$table (${done + 1}/${BackupRules.TABLES.size})")
            val rows = JSONArray()
            var offset = 0
            var ok = true
            while (true) {
                val page = try {
                    withAuthRetry { api.select(table, BackupRules.pageQuery(table, offset)) }
                } catch (e: SupabaseException) {
                    // A table missing on an older schema is skipped; anything else is a real failure.
                    if (e.status == 404 || e.sqlState == "42P01") skipped += table else failed += "$table: ${e.message}"
                    ok = false
                    break
                } catch (e: Exception) {
                    failed += "$table: ${e.message}"
                    ok = false
                    break
                }
                for (i in 0 until page.length()) rows.put(page.get(i))
                if (BackupRules.isLastPage(page.length())) break
                offset += BackupRules.PAGE
            }
            done++
            if (!ok) continue
            zip.putNextEntry(java.util.zip.ZipEntry("$table.json"))
            zip.write(rows.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            tablesJson.put(table, rows.length())
            totalRows += rows.length()
        }
        manifest.put("tables", tablesJson).put("failed", JSONArray(failed)).put("skipped", JSONArray(skipped))
        zip.putNextEntry(java.util.zip.ZipEntry("MANIFEST.json"))
        zip.write(manifest.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
        zip.flush()
        return BackupSummary(tablesJson.length(), totalRows, failed, skipped)
    }

    fun studentPlanMonthHistory(studentId: String): JSONArray {
        require(studentId.isNotBlank()) { "معرّف الطالب غير صالح" }
        val owner = currentUserId() ?: return JSONArray()
        val key = "guardian:plan-history:$studentId"
        val cached = cache.get(owner, key, null)?.let { runCatching { JSONArray(it) }.getOrNull() }
        if (!online()) return cached ?: JSONArray()
        return runCatching {
            JSONArray(withAuthRetry { api.rpc("student_plan_month_history", JSONObject().put("p_student_id", studentId)) })
        }.onSuccess { cache.put(owner, key, it.toString()) }
            .getOrElse { cached ?: JSONArray() }
    }

    /** RC18 evidence-backed badges. The RPC recomputes current eligibility idempotently. */
    fun studentAchievementBadges(studentId: String, month: LocalDate = LocalDate.now().withDayOfMonth(1)): JSONArray {
        require(studentId.isNotBlank()) { "معرّف الطالب غير صالح" }
        val owner = currentUserId() ?: return JSONArray()
        val key = "guardian:badges:$studentId:${month.withDayOfMonth(1)}"
        val cached = cache.get(owner, key, null)?.let { runCatching { JSONArray(it) }.getOrNull() }
        if (!online()) return cached ?: JSONArray()
        return runCatching {
            JSONArray(withAuthRetry {
                api.rpc(
                    "rc182_refresh_student_badges_exact",
                    JSONObject().put("p_student_id", studentId).put("p_month", month.withDayOfMonth(1).toString()),
                )
            })
        }.onSuccess { cache.put(owner, key, it.toString()) }
            .getOrElse { cached ?: JSONArray() }
    }

    /** RC19: verified monthly create/repair. Hifz stays individualized; Sard is fixed at 25 pages. */
    fun rollMonthlySmartPlans(month: LocalDate = LocalDate.now().withDayOfMonth(1)): JSONObject {
        require(online()) { "يلزم اتصال بالإنترنت لبدء دورة شهر جديدة" }
        val result = JSONObject(withAuthRetry {
            api.rpc("rc19_start_month", JSONObject().put("p_month_start", month.withDayOfMonth(1).toString()))
        })
        currentUserId()?.let { owner ->
            cache.invalidate(owner, CACHE_PLANS)
            cache.invalidate(owner, CACHE_PLAN_PHASES)
            cache.invalidate(owner, CACHE_PLAN_PHASE_STATES)
            cache.invalidate(owner, CACHE_PLAN_RECITATIONS)
            cache.invalidate(owner, "guardian:plan-history:")
            cache.invalidate(owner, "guardian:badges:")
            cache.invalidate(owner, CACHE_GUARDIAN_OVERVIEW_PREFIX)
            cache.invalidate(owner, CACHE_GUARDIAN_DASHBOARD_PREFIX)
            cache.invalidate(owner, CACHE_REPORT_SUMMARY_PREFIX)
        }
        invalidatePlanSnapshotMemory()
        // RC27.5: the plan range is the manager's own minimum target. The app never rewrites it on its own,
        // and a student who passes its end simply keeps getting the daily duty (see activitySnapshotsForPlan).
        return result
    }

    /**
     * RC26: monthly hifz plans whose carried-over Quran range no longer matches where the student is.
     * Read-only; nothing changes until [applyPlanRangeRebase] is called with the chosen previews.
     */
    fun planRangeRebasePreviews(halaqaId: String? = null): List<PlanRebasePreview> {
        require(online()) { "يلزم اتصال بالإنترنت لفحص نطاق الخطط" }
        val today = LocalDate.now()
        val plans = plans(forceRefresh = true).filter { row ->
            row.type == "hifz" && row.status == "active" && row.scopeType == "student" &&
                !row.studentId.isNullOrBlank() && (halaqaId.isNullOrBlank() || row.halaqaId == halaqaId) &&
                runCatching { LocalDate.parse(row.startDate) <= today && LocalDate.parse(row.endDate) >= today }.getOrDefault(false)
        }
        if (plans.isEmpty()) return emptyList()
        val names = students().associate { it.id to it.fullName }
        val since = today.minusDays(120).toString()
        val byStudent = HashMap<String, MutableList<SmartPlanEngine.Recitation>>()
        plans.mapNotNull { it.studentId }.distinct().chunked(40).forEach { chunk ->
            val query = "select=student_id,activity_type,start_surah,start_ayah,end_surah,end_ayah,evaluation,created_at,sessions!inner(session_date)" +
                "&activity_type=eq.new_hifz&student_id=in.(${chunk.joinToString(",")})" +
                "&sessions.session_date=gte.$since&order=created_at.asc&limit=5000"
            val rows = withAuthRetry { api.select("recitation_entries", query) }
            for (i in 0 until rows.length()) {
                val r = rows.optJSONObject(i) ?: continue
                val date = runCatching { LocalDate.parse(r.optJSONObject("sessions")?.optString("session_date").orEmpty()) }.getOrNull() ?: continue
                fun int(key: String): Int? = if (!r.has(key) || r.isNull(key)) null else r.optInt(key).takeIf { it > 0 }
                byStudent.getOrPut(r.optString("student_id")) { mutableListOf() }.add(
                    SmartPlanEngine.Recitation(
                        id = "${r.optString("student_id")}-$i",
                        studentId = r.optString("student_id"),
                        activityType = r.optString("activity_type"),
                        date = date,
                        startSurah = int("start_surah"), startAyah = int("start_ayah"),
                        endSurah = int("end_surah"), endAyah = int("end_ayah"),
                        evaluation = r.optString("evaluation").ifBlank { null },
                        createdAt = r.optString("created_at"),
                    ),
                )
            }
        }
        fun label(s: Int?, a: Int?, e: Int?, b: Int?): String =
            if (s != null && a != null && e != null && b != null)
                "من ${QuranMetadata.surahName(s)}:$a إلى ${QuranMetadata.surahName(e)}:$b"
            else "غير محدد"
        return plans.mapNotNull { row ->
            val sid = row.studentId ?: return@mapNotNull null
            val recs = byStudent[sid].orEmpty()
            val route = row.settings.routeDirection ?: defaultPlanRouteDirection(row.type)
            val rangeStart = if (row.rangeStartSurah != null && row.rangeStartAyah != null) QuranLocation(row.rangeStartSurah, row.rangeStartAyah) else null
            val rangeEnd = if (row.rangeEndSurah != null && row.rangeEndAyah != null) QuranLocation(row.rangeEndSurah, row.rangeEndAyah) else null
            val terminalSurah = SmartPlanActivityRules.terminalSurah("hifz", route)
            val routeTerminal = QuranLocation(terminalSurah, QuranMetadata.maxAyah(terminalSurah))
            val diagnosis = PlanRangeRebaseRules.diagnose(recs, LocalDate.parse(row.startDate), route, rangeStart, rangeEnd, routeTerminal)
            if (diagnosis.status == PlanRangeRebaseRules.Status.OK) return@mapNotNull null
            val expected = diagnosis.expectedStart
            val computed = if (diagnosis.status == PlanRangeRebaseRules.Status.ROUTE_COMPLETE) null
                else expected?.let { rebasedRange(row, it, route, coverThrough = diagnosis.latestEnd) }
            val patch: JSONObject? = if (expected != null && computed != null) {
                val lines = computed.first
                val end = computed.second
                val pages = PlanRangeRebaseRules.normalizePages(
                    lines.first().substringBefore(':').toIntOrNull(),
                    lines.last().substringBefore(':').toIntOrNull(),
                )
                JSONObject()
                    .put("range_start_surah", expected.surah).put("range_start_ayah", expected.ayah)
                    .put("range_end_surah", end.surah).put("range_end_ayah", end.ayah)
                    .put("range_start_page", pages.first ?: JSONObject.NULL)
                    .put("range_end_page", pages.second ?: JSONObject.NULL)
            } else null
            val newEnd = computed?.second
            PlanRebasePreview(
                planId = row.id,
                studentId = sid,
                studentName = names[sid].orEmpty().ifBlank { "طالب" },
                halaqaId = row.halaqaId,
                status = diagnosis.status,
                oldLabel = label(row.rangeStartSurah, row.rangeStartAyah, row.rangeEndSurah, row.rangeEndAyah),
                newLabel = if (expected != null && newEnd != null) label(expected.surah, expected.ayah, newEnd.surah, newEnd.ayah) else "غير متاح",
                latestLabel = diagnosis.latestEnd?.let { "${QuranMetadata.surahName(it.surah)}:${it.ayah}" } ?: "لا يوجد",
                patch = patch,
            )
        }
    }

    private fun withAutoFilledRange(draft: SmartPlanDraft, effectiveType: String): SmartPlanDraft {
        val startSurah = draft.rangeStartSurah ?: return draft
        val startAyah = draft.rangeStartAyah ?: return draft
        if (draft.scopeType != "student" || effectiveType !in setOf("hifz", "tathbit")) return draft
        val sp = draft.rangeStartPage
        val ep = draft.rangeEndPage
        val lineCount = draft.memorizationLineKeys.size.takeIf { it > 0 }
            ?: if (sp != null && ep != null) (kotlin.math.abs(ep - sp) + 1) * 15 else null
        if (!PlanRangeRebaseRules.isUndersized(lineCount, draft.targetPages)) return draft
        return runCatching {
            val tathbit = effectiveType == "tathbit"
            val route = draft.routeDirection ?: defaultPlanRouteDirection(effectiveType)
            val preview = QuranCore.engine().previewPagesFrom(
                QuranLocation(startSurah, startAyah), draft.targetPages,
                if (tathbit) "tathbit" else "general_hifz", if (tathbit) "tathbit" else "new_hifz", planTraversalDirection(route),
            )
            val end = preview.end
            if (end == null || preview.qcfLineKeys.isEmpty()) draft
            else draft.copy(
                rangeEndSurah = end.surah, rangeEndAyah = end.ayah,
                rangeStartPage = preview.startPage, rangeEndPage = preview.endPage,
                memorizationLineKeys = preview.qcfLineKeys,
            )
        }.getOrDefault(draft)
    }

    @Volatile private var rangeHealDate: LocalDate? = null

    /**
     * RC27.6: a student's hifz/tathbit plan that has a start but a range far smaller than its own target
     * (typically a start-only pick that kept a stale end) gets its end recomputed from the target, so the
     * daily duty is never zero. Never shrinks or moves a range, skips phase-managed plans, once a day.
     * Returns the number of plans fixed.
     */
    fun healUndersizedPlanRanges(force: Boolean = false): Int {
        val today = LocalDate.now()
        if (!online() || (!force && rangeHealDate == today)) return 0
        rangeHealDate = today
        val candidates = plans(forceRefresh = false).filter { row ->
            row.type in setOf("hifz", "tathbit") && row.status == "active" && row.scopeType == "student" &&
                !row.studentId.isNullOrBlank() && row.rangeStartSurah != null && row.rangeStartAyah != null &&
                runCatching { LocalDate.parse(row.startDate) <= today && LocalDate.parse(row.endDate) >= today }.getOrDefault(false) &&
                PlanRangeRebaseRules.isUndersized(
                    row.settings.memorizationLineKeys.size.takeIf { it > 0 }
                        ?: if (row.rangeStartPage != null && row.rangeEndPage != null)
                            (kotlin.math.abs(row.rangeEndPage - row.rangeStartPage) + 1) * 15 else null,
                    row.target,
                )
        }
        if (candidates.isEmpty()) return 0
        val ids = candidates.joinToString(",") { it.id }
        val phased = runCatching {
            val rows = withAuthRetry { api.select("smart_plan_phases", "select=plan_id&status=neq.archived&plan_id=in.($ids)&limit=500") }
            (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.optString("plan_id") }.toSet()
        }.getOrDefault(candidates.map { it.id }.toSet())
        val rawSettings = runCatching {
            val rows = withAuthRetry { api.select("smart_plans", "select=id,settings&id=in.($ids)&limit=500") }
            (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }.associate { it.optString("id") to (it.optJSONObject("settings") ?: JSONObject()) }
        }.getOrNull() ?: return 0
        var fixed = 0
        candidates.filter { it.id !in phased }.forEach { row ->
            runCatching {
                val start = QuranLocation(row.rangeStartSurah!!, row.rangeStartAyah!!)
                val route = row.settings.routeDirection ?: defaultPlanRouteDirection(row.type)
                val tathbit = row.type == "tathbit"
                val preview = QuranCore.engine().previewPagesFrom(
                    start, row.target, if (tathbit) "tathbit" else "general_hifz",
                    if (tathbit) "tathbit" else "new_hifz", planTraversalDirection(route),
                )
                val end = preview.end ?: return@runCatching
                if (preview.qcfLineKeys.isEmpty()) return@runCatching
                val pages = PlanRangeRebaseRules.normalizePages(preview.startPage, preview.endPage)
                val settings = JSONObject((rawSettings[row.id] ?: JSONObject()).toString())
                    .put("memorization_line_keys", JSONArray(preview.qcfLineKeys))
                val body = JSONObject()
                    .put("range_end_surah", end.surah).put("range_end_ayah", end.ayah)
                    .put("range_start_page", pages.first ?: JSONObject.NULL)
                    .put("range_end_page", pages.second ?: JSONObject.NULL)
                    .put("settings", settings)
                    .put("updated_at", java.time.Instant.now().toString())
                val result = patchConfirmed("smart_plans", "id=eq.${row.id}", body)
                if (result.length() > 0) fixed += 1
            }
        }
        if (fixed > 0) {
            currentUserId()?.let { owner -> cache.invalidate(owner, CACHE_PLANS) }
            invalidatePlanSnapshotMemory()
        }
        return fixed
    }

    /** Lines and end location for [row].target pages starting at [start] along the plan route. */
    private fun rebasedRange(row: PlanRow, start: QuranLocation, route: String, coverThrough: QuranLocation? = null): Pair<List<String>, QuranLocation>? {
        if (row.target <= 0.0) return null
        val terminalSurah = SmartPlanActivityRules.terminalSurah("hifz", route)
        val terminal = QuranLocation(terminalSurah, QuranMetadata.maxAyah(terminalSurah))
        val direction = planTraversalDirection(route)
        fun linesBetween(from: QuranLocation, to: QuranLocation): List<String> = runCatching {
            QuranCore.engine().calculate(
                QuranRangeRequest(
                    track = "general_hifz",
                    activityType = "new_hifz",
                    selection = QuranSelection.Ayahs(from, to),
                    direction = direction,
                ),
            ).qcfLineKeys
        }.getOrNull().orEmpty()
        val wanted = ceil(row.target * 15.0).toInt().coerceAtLeast(1)
        // RC27.3: when the student is already ahead inside this month, the range must reach past where he
        // is now. Otherwise the rebase restored "start .. start + target" and he was beyond the end again.
        val covered = if (coverThrough != null &&
            PlanRangeRebaseRules.position(coverThrough, route) >= PlanRangeRebaseRules.position(start, route)
        ) linesBetween(start, coverThrough).size else 0
        val lines = linesBetween(start, terminal).take(PlanRangeRebaseRules.rebasedLineCount(wanted, covered))
        if (lines.isEmpty()) return null
        val end = QuranCore.engine().endpointsForLineKeys(lines)?.second ?: return null
        return lines to end
    }

    /** RC26: applies the chosen corrections. Each PATCH is audited server-side by rc18_audit_smart_plans. */
    fun applyPlanRangeRebase(previews: List<PlanRebasePreview>): Int {
        var applied = 0
        val failures = ArrayList<String>()
        previews.filter { it.canApply }.forEach { preview ->
            val body = JSONObject(preview.patch.toString()).put("updated_at", java.time.Instant.now().toString())
            // RC27.3: confirmed write (waits for the server) so success is real; an empty answer means RLS/no row.
            val result = runCatching { patchConfirmed("smart_plans", "id=eq.${preview.planId}", body) }
            when {
                result.isFailure -> failures += "${preview.studentName}: ${result.exceptionOrNull()?.message.orEmpty()}"
                result.getOrNull()?.length() == 0 -> failures += "${preview.studentName}: لم يُعدَّل أي صف (صلاحيات)"
                else -> applied += 1
            }
        }
        if (applied > 0) {
            currentUserId()?.let { owner -> cache.invalidate(owner, CACHE_PLANS) }
            invalidatePlanSnapshotMemory()
        }
        if (failures.isNotEmpty() && applied == 0) throw IllegalStateException(failures.joinToString("؛ "))
        return applied
    }

    fun openOrCreateTodaySession(halaqaId: String, date: String, assignmentKind: String): Result<SessionRow> =
        runCatching { ensureTodaySession(halaqaId, date, assignmentKind) }

    private fun cachedDailySession(owner: String, halaqaId: String, date: String, sessionType: String): SessionRow? =
        cachedSessions(owner)?.firstOrNull {
            it.halaqaId == halaqaId && it.date == date && it.type == sessionType &&
                SessionWorkflowRules.isReusableDailySessionStatus(it.status)
        }

    private fun serverGetOrCreateDailySession(
        halaqaId: String,
        date: String,
        sessionType: String,
        title: String,
        proposedId: String,
    ): SessionRow {
        val raw = withAuthRetry {
            api.rpc(
                "rc23_get_or_create_session",
                JSONObject()
                    .put("p_halaqa_id", halaqaId)
                    .put("p_session_date", date)
                    .put("p_session_type", sessionType)
                    .put("p_title", title.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                    .put("p_proposed_id", proposedId),
            )
        }
        val row = JSONArray(raw).optJSONObject(0) ?: error("تعذر إنشاء أو فتح جلسة اليوم")
        return SessionRow.from(row).also(::cacheSession)
    }

    fun ensureTodaySession(halaqaId: String, date: String, assignmentKind: String): SessionRow {
        require(halaqaId.isNotBlank()) { "معرّف الحلقة غير صالح" }
        val sessionDate = LocalDate.parse(date)
        val owner = currentUserId() ?: error("لا توجد جلسة مستخدم صالحة")
        val sessionType = SessionWorkflowRules.sessionTypeForAssignment(assignmentKind)

        cachedDailySession(owner, halaqaId, date, sessionType)?.let { return it }

        if (online()) {
            val existing = runCatching {
                withAuthRetry {
                    api.select(
                        "sessions",
                        "select=*&halaqa_id=eq.$halaqaId&session_date=eq.$date&session_type=eq.$sessionType" +
                            "&status=neq.cancelled&order=created_at.asc&limit=1",
                    )
                }.optJSONObject(0)
            }.getOrNull()
            if (existing != null) return SessionRow.from(existing).also(::cacheSession)
        }

        // Saturday is the fixed halaqa off-day. Reading/opening today's workflow must
        // never create a phantom session. Existing exceptional sessions were returned above.
        if (SmartPlanEngine.weekday(sessionDate) == 6) {
            error("اليوم السبت إجازة حسب نظام الحلقة. لا يتم إنشاء جلسة تلقائيًا في يوم الإجازة.")
        }

        val title = when (sessionType) {
            "sard" -> "جلسة السرد"
            "review" -> "جلسة المراجعة والتثبيت"
            "test" -> "جلسة اختبار مستقلة"
            "tilawah" -> "جلسة التلاوة"
            else -> "جلسة تسميع اليوم"
        }
        val deterministicId = UUID.nameUUIDFromBytes(
            "daily-session:${SessionWorkflowRules.dailySessionKey(halaqaId, date, sessionType)}".toByteArray(),
        ).toString()
        if (online()) {
            return serverGetOrCreateDailySession(halaqaId, date, sessionType, title, deterministicId)
        }
        val id = createSession(halaqaId, date, sessionType, title, deterministicId)
        return SessionRow(id, halaqaId, date, sessionType, title, "open")
    }

    fun createSession(
        halaqaId: String,
        date: String,
        type: String,
        title: String,
        id: String? = null,
    ): String {
        require(type in SessionWorkflowRules.manualSessionTypes) { "نوع الجلسة غير صالح" }
        LocalDate.parse(date)
        val owner = currentUserId() ?: error("لا توجد جلسة مستخدم صالحة")
        cachedDailySession(owner, halaqaId, date, type)?.let { return it.id }

        val existingCancelled = cachedSessions(owner)?.any {
            it.halaqaId == halaqaId && it.date == date && it.type == type && it.status == "cancelled"
        } == true
        val canonicalId = id ?: if (existingCancelled) UUID.randomUUID().toString() else UUID.nameUUIDFromBytes(
            "daily-session:${SessionWorkflowRules.dailySessionKey(halaqaId, date, type)}".toByteArray(),
        ).toString()

        if (online()) {
            return serverGetOrCreateDailySession(halaqaId, date, type, title, canonicalId).id
        }

        val obj = JSONObject()
            .put("id", canonicalId)
            .put("halaqa_id", halaqaId)
            .put("session_date", date)
            .put("session_type", type)
            .put("title", title.takeIf { it.isNotBlank() })
            .put("status", "open")
        store.load()?.userId?.let { obj.put("created_by", it) }

        // Offline taps for the same halaqa/day/type collapse into one Outbox mutation.
        queued(
            method = "POST",
            path = "/rest/v1/sessions?on_conflict=id",
            obj = obj,
            dedupe = "session-day:${SessionWorkflowRules.dailySessionKey(halaqaId, date, type)}",
            entityTable = "sessions",
            entityId = canonicalId,
        )
        cacheSession(SessionRow(canonicalId, halaqaId, date, type, title, "open"))
        return canonicalId
    }

    /** Merge an optimistic local mutation into a session-scoped JSONArray cache immediately. */
    private fun upsertSessionScopedCache(prefix: String, sessionId: String, row: JSONObject) {
        val owner = currentUserId() ?: return
        val key = prefix + sessionId
        val current = cache.get(owner, key, maxAge = null)
            ?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
        val id = row.optString("id")
        val merged = JSONArray()
        var replaced = false
        for (index in 0 until current.length()) {
            val existing = current.optJSONObject(index) ?: continue
            if (id.isNotBlank() && existing.optString("id") == id) {
                merged.put(JSONObject(row.toString()))
                replaced = true
            } else {
                merged.put(existing)
            }
        }
        if (!replaced) merged.put(JSONObject(row.toString()))
        cache.put(owner, key, merged.toString())
    }

    private fun removeSessionScopedCacheRow(prefix: String, sessionId: String, rowId: String) {
        val owner = currentUserId() ?: return
        if (rowId.isBlank()) return
        val key = prefix + sessionId
        val current = cache.get(owner, key, maxAge = null)
            ?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return
        val remaining = JSONArray()
        for (index in 0 until current.length()) {
            val existing = current.optJSONObject(index) ?: continue
            if (existing.optString("id") != rowId) remaining.put(existing)
        }
        cache.put(owner, key, remaining.toString())
    }

    private fun upsertPlanRecitationCacheRow(row: JSONObject, sessionId: String) {
        val owner = currentUserId() ?: return
        val id = row.optString("id")
        if (id.isBlank()) return
        val enriched = JSONObject(row.toString())
        if (enriched.optJSONObject("sessions") == null) {
            cachedSessions(owner, maxAge = null).orEmpty().firstOrNull { it.id == sessionId }?.date?.let { date ->
                enriched.put("sessions", JSONObject().put("session_date", date))
            }
        }
        val current = cache.get(owner, CACHE_PLAN_RECITATIONS, maxAge = null)
            ?.let { runCatching { JSONArray(it) }.getOrNull() } ?: JSONArray()
        val merged = JSONArray()
        var replaced = false
        for (index in 0 until current.length()) {
            val existing = current.optJSONObject(index) ?: continue
            if (existing.optString("id") == id) {
                merged.put(enriched)
                replaced = true
            } else merged.put(existing)
        }
        if (!replaced) merged.put(enriched)
        cache.put(owner, CACHE_PLAN_RECITATIONS, merged.toString())
    }

    private fun removePlanRecitationCacheRow(recitationId: String) {
        val owner = currentUserId() ?: return
        if (recitationId.isBlank()) return
        val current = cache.get(owner, CACHE_PLAN_RECITATIONS, maxAge = null)
            ?.let { runCatching { JSONArray(it) }.getOrNull() } ?: return
        val remaining = JSONArray()
        for (index in 0 until current.length()) {
            val existing = current.optJSONObject(index) ?: continue
            if (existing.optString("id") != recitationId) remaining.put(existing)
        }
        cache.put(owner, CACHE_PLAN_RECITATIONS, remaining.toString())
    }

    private fun invalidatePlanSnapshotMemory() {
        planSnapshotMemoryDate = null
        planSnapshotMemory = emptyList()
    }

    private fun cacheSession(row: SessionRow) {
        val owner = currentUserId() ?: return
        val current = cachedSessions(owner, maxAge = null, includeArchived = true).orEmpty().filterNot { it.id == row.id }
        val merged = (listOf(row) + current).sortedByDescending { it.date }
        val json = JSONArray()
        merged.forEach { session ->
            json.put(
                JSONObject()
                    .put("id", session.id)
                    .put("halaqa_id", session.halaqaId)
                    .put("session_date", session.date)
                    .put("session_type", session.type)
                    .put("title", session.title)
                    .put("status", session.status)
                    .put("notes", session.notes.takeIf { it.isNotBlank() })
                    .put("activity_data", runCatching { JSONObject(session.activityDataJson) }.getOrElse { JSONObject() }.put("archived", session.archived)),
            )
        }
        cache.put(owner, CACHE_SESSIONS, json.toString())
    }


    private fun removeSessionFromLocalCache(sessionId: String) {
        val owner = currentUserId() ?: return
        val remaining = cachedSessions(owner, maxAge = null, includeArchived = true).orEmpty().filterNot { it.id == sessionId }
        val arr = JSONArray()
        remaining.forEach { session ->
            arr.put(
                JSONObject()
                    .put("id", session.id)
                    .put("halaqa_id", session.halaqaId)
                    .put("session_date", session.date)
                    .put("session_type", session.type)
                    .put("title", session.title)
                    .put("status", session.status)
                    .put("notes", session.notes.takeIf { it.isNotBlank() })
                    .put("activity_data", runCatching { JSONObject(session.activityDataJson) }.getOrElse { JSONObject() }.put("archived", session.archived)),
            )
        }
        cache.put(owner, CACHE_SESSIONS, arr.toString())
        cache.invalidate(owner, CACHE_SESSION_RECITATIONS_PREFIX + sessionId)
        cache.invalidate(owner, CACHE_SESSION_ATTENDANCE_PREFIX + sessionId)
        cache.invalidate(owner, CACHE_SESSION_NOTES_PREFIX + sessionId)
    }

    /** RC18.2: edit session metadata transactionally so date changes rebind recitations to the correct monthly plan/phase. */
    fun updateSessionDetails(
        session: SessionRow,
        halaqaId: String,
        date: String,
        type: String,
        title: String,
        notes: String,
    ): SessionRow {
        require(session.id.isNotBlank()) { "معرّف الجلسة غير صالح" }
        require(halaqaId.isNotBlank()) { "اختر الحلقة" }
        require(type in SessionWorkflowRules.manualSessionTypes) { "نوع الجلسة غير صالح" }
        LocalDate.parse(date)
        require(online()) { "تعديل بيانات الجلسة يحتاج اتصالًا بالإنترنت حتى تبقى الخطط الشهرية والتقارير متسقة" }
        val body = JSONObject()
            .put("p_session_id", session.id)
            .put("p_halaqa_id", halaqaId)
            .put("p_session_date", date)
            .put("p_session_type", type)
            .put("p_title", title.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("p_notes", notes.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        val raw = withAuthRetry { api.rpc("rc182_update_session_details", body) }
        val obj = when {
            raw.isBlank() || raw == "null" -> error("لم يرجع الخادم بيانات الجلسة بعد التعديل")
            raw.startsWith("[") -> JSONArray(raw).optJSONObject(0)
            else -> JSONObject(raw)
        } ?: error("تعذر قراءة الجلسة المعدلة")
        val updated = SessionRow.from(obj)
        cacheSession(updated)
        invalidateSessionDerivedCaches()
        return updated
    }

    /** RC18.2: archive/restore without replacing the rest of activity_data JSON. */
    fun setSessionArchived(session: SessionRow, archived: Boolean): SessionRow {
        require(session.id.isNotBlank()) { "معرّف الجلسة غير صالح" }
        if (online()) {
            val raw = withAuthRetry {
                api.rpc(
                    "rc182_set_session_archived",
                    JSONObject().put("p_session_id", session.id).put("p_archived", archived),
                )
            }
            val obj = when {
                raw.isBlank() || raw == "null" -> null
                raw.startsWith("[") -> JSONArray(raw).optJSONObject(0)
                else -> JSONObject(raw)
            } ?: error("تعذر قراءة حالة الجلسة")
            val updated = SessionRow.from(obj)
            cacheSession(updated)
            invalidateSessionDerivedCaches()
            return updated
        }
        val activity = runCatching { JSONObject(session.activityDataJson) }.getOrElse { JSONObject() }
        val status = if (archived) {
            if (!activity.has("pre_archive_status")) activity.put("pre_archive_status", session.status)
            activity.put("archived", true)
            "closed"
        } else {
            val restored = activity.optString("pre_archive_status").takeIf { it in setOf("open", "planned", "closed") } ?: "closed"
            activity.remove("pre_archive_status")
            activity.put("archived", false)
            restored
        }
        patch(
            "sessions",
            "id=eq.${session.id}",
            JSONObject().put("status", status).put("activity_data", activity),
            dedupe = "session-archive:${session.id}",
        )
        invalidateSessionDerivedCaches()
        return session.copy(status = status, archived = archived, activityDataJson = activity.toString()).also(::cacheSession)
    }

    fun restoreSession(session: SessionRow): SessionRow = setSessionArchived(session, false)

    fun cancelSession(sessionId: String) {
        require(sessionId.isNotBlank()) { "معرّف الجلسة غير صالح" }
        patch("sessions", "id=eq.$sessionId", JSONObject().put("status", "cancelled"), dedupe = "session-cancel:$sessionId")
        removeSessionFromLocalCache(sessionId)
        invalidateSessionDerivedCaches()
    }

    fun archiveSession(sessionId: String) {
        require(sessionId.isNotBlank()) { "معرّف الجلسة غير صالح" }
        val owner = currentUserId() ?: error("لا توجد جلسة مستخدم صالحة")
        val session = cachedSessions(owner, maxAge = null, includeArchived = true).orEmpty().firstOrNull { it.id == sessionId }
            ?: SessionRow(sessionId, "", LocalDate.now().toString(), "tasmee", "", "closed")
        setSessionArchived(session, true)
    }

    fun deleteSession(sessionId: String) {
        require(sessionId.isNotBlank()) { "معرّف الجلسة غير صالح" }
        // Supabase schema has ON DELETE CASCADE from attendance_records, recitation_entries,
        // student_daily_notes and notifications to sessions. Queue one mutation instead of four;
        // this is materially faster on weak/VPN mobile links and remains atomic on the server.
        delete("sessions", "id=eq.$sessionId", dedupe = "session-delete:$sessionId")
        removeSessionFromLocalCache(sessionId)
        invalidateSessionDerivedCaches()
    }

    /** RC8.6: one reconnect refresh warms recent rosters in three batched requests instead of
     * forcing every opened session to perform its own attendance/notes/recitations round-trips. */
    private fun warmRecentSessionDetails(owner: String, sessionIds: List<String>) {
        val ids = sessionIds.filter { it.isNotBlank() }.distinct().take(24)
        if (ids.isEmpty() || !online()) return
        val filter = ids.joinToString(",")
        fun distribute(rows: JSONArray, keyPrefix: String) {
            val grouped = linkedMapOf<String, JSONArray>()
            ids.forEach { grouped[it] = JSONArray() }
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                grouped[row.optString("session_id")]?.put(row)
            }
            grouped.forEach { (sessionId, arr) -> cache.put(owner, keyPrefix + sessionId, arr.toString()) }
        }
        runCatching {
            distribute(
                withAuthRetry {
                    api.select(
                        "attendance_records",
                        "select=id,session_id,student_id,status,excuse_reason,arrival_minutes_late&session_id=in.($filter)&limit=10000",
                    )
                },
                CACHE_SESSION_ATTENDANCE_PREFIX,
            )
        }
        runCatching {
            distribute(
                withAuthRetry {
                    api.select(
                        "student_daily_notes",
                        "select=id,session_id,student_id,behavior,behavior_score,teacher_comment,homework,parent_visible,participation&session_id=in.($filter)&limit=10000",
                    )
                },
                CACHE_SESSION_NOTES_PREFIX,
            )
        }
        runCatching {
            distribute(
                withAuthRetry {
                    api.select(
                        "recitation_entries",
                        "select=id,session_id,student_id,activity_type,input_method,start_surah,start_ayah,end_surah,end_ayah,start_page,end_page,pages_count,ayahs_count,surahs_count,mistakes,prompts,evaluation,safety_percentage,points,teacher_note,selected_surahs,selection_data,created_at&session_id=in.($filter)&order=created_at.asc&limit=10000",
                    )
                },
                CACHE_SESSION_RECITATIONS_PREFIX,
            )
        }
    }

    fun refreshSessionDetails(sessionId: String) {
        val owner = currentUserId() ?: return
        if (!online() || sessionId.isBlank()) return
        warmRecentSessionDetails(owner, listOf(sessionId))
    }

    fun saveAttendance(
        sessionId: String,
        studentId: String,
        status: String,
        excuseReason: String = "",
        arrivalMinutesLate: Int = 0,
    ) = saveAttendanceLocalFirst(sessionId, studentId, status, excuseReason, arrivalMinutesLate)

    fun saveAttendanceLocalFirst(
        sessionId: String,
        studentId: String,
        status: String,
        excuseReason: String = "",
        arrivalMinutesLate: Int = 0,
    ) {
        require(status in setOf("present", "absent", "excused", "late")) { "حالة حضور غير صالحة" }
        require(arrivalMinutesLate >= 0) { "دقائق التأخر غير صالحة" }
        val id = UUID.nameUUIDFromBytes("$sessionId:$studentId".toByteArray()).toString()
        val obj = JSONObject()
            .put("id", id)
            .put("session_id", sessionId)
            .put("student_id", studentId)
            .put("status", status)
            .put("arrival_minutes_late", if (status == "late") arrivalMinutesLate else 0)
        if (status == "excused" && excuseReason.isNotBlank()) obj.put("excuse_reason", excuseReason.trim())
        store.load()?.userId?.let { obj.put("recorded_by", it) }
        queued(
            "POST",
            "/rest/v1/attendance_records?on_conflict=session_id,student_id",
            obj,
            "attendance:$sessionId:$studentId",
        )
        // Persist the optimistic value in the same cache read by the roster. The Outbox is then
        // durability/sync metadata, not the only place where the UI can rediscover this write.
        upsertSessionScopedCache(CACHE_SESSION_ATTENDANCE_PREFIX, sessionId, obj)
    }

    fun saveDailyNote(
        sessionId: String,
        studentId: String,
        behavior: String = "normal",
        behaviorScore: Int? = null,
        teacherComment: String = "",
        homework: String = "",
        parentVisible: Boolean = true,
        participation: String = "",
    ) = saveDailyNoteLocalFirst(
        sessionId, studentId, behavior, behaviorScore, teacherComment, homework, parentVisible, participation,
    )

    fun saveDailyNoteLocalFirst(
        sessionId: String,
        studentId: String,
        behavior: String = "normal",
        behaviorScore: Int? = null,
        teacherComment: String = "",
        homework: String = "",
        parentVisible: Boolean = true,
        participation: String = "",
    ) {
        require(behavior in setOf("excellent", "good", "normal", "needs_attention", "warning")) { "تقييم السلوك غير صالح" }
        require(behaviorScore == null || behaviorScore in 1..5) { "درجة السلوك يجب أن تكون من 1 إلى 5" }
        val id = UUID.nameUUIDFromBytes("note:$sessionId:$studentId".toByteArray()).toString()
        val obj = JSONObject()
            .put("id", id)
            .put("session_id", sessionId)
            .put("student_id", studentId)
            .put("behavior", behavior)
            .put("teacher_comment", teacherComment.trim().takeIf { it.isNotBlank() })
            .put("homework", homework.trim().takeIf { it.isNotBlank() })
            .put("parent_visible", parentVisible)
        behaviorScore?.let { obj.put("behavior_score", it) }
        participation.trim().takeIf { it.isNotBlank() }?.let { obj.put("participation", it) }
        store.load()?.userId?.let { obj.put("created_by", it) }
        queued(
            "POST",
            "/rest/v1/student_daily_notes?on_conflict=session_id,student_id",
            obj,
            "note:$sessionId:$studentId",
        )
        upsertSessionScopedCache(CACHE_SESSION_NOTES_PREFIX, sessionId, obj)
    }

    fun saveRecitation(draft: RecitationDraft) = saveRecitationLocalFirst(draft)

    /** RC15: local track lookup used by the tathbit/general activity policy. */
    private fun studentTrackOf(studentId: String?): String? {
        if (studentId.isNullOrBlank()) return null
        return runCatching { offlineDb.students().byId(queueOwnerId(), studentId)?.asJson()?.optString("track_code") }.getOrNull()
    }

    fun saveRecitationLocalFirst(draft: RecitationDraft): String = saveRecitationNormalized(
        // RC15: a tathbit student can only receive tathbit (or test); tathbit is never stored as review_far.
        draft.copy(activityType = TrackActivityPolicy.recitationActivityFor(studentTrackOf(draft.studentId), draft.activityType)),
    )

    private fun saveRecitationNormalized(draft: RecitationDraft): String {
        require(draft.activityType in setOf("new_hifz", "review_near", "review_far", "sard", "tilawah", "test", "tathbit")) {
            "نوع التسميع غير صالح"
        }
        // RC27.9: program sessions (courses, التلاوة والتجويد, qualifying) record tilawah/test only: never hifz/sard/review.
        if (draft.activityType in setOf("new_hifz", "review_near", "review_far", "sard", "tathbit")) {
            val sessionHalaqa = currentUserId()
                ?.let { cachedSessions(it, null, true) }?.firstOrNull { it.id == draft.sessionId }?.halaqaId
            if (sessionHalaqa != null) {
                smartPlanIneligibleHalaqaIds()
                require(isSmartPlanEligible(sessionHalaqa)) { "جلسات البرامج والدورات تقبل التلاوة والاختبار فقط، لا الحفظ ولا السرد ولا المراجعة" }
            }
        }
        require(draft.pages >= 0) { "عدد الصفحات غير صالح" }
        require(draft.mistakes >= 0 && draft.prompts >= 0) { "عدد الأخطاء أو التلقين غير صالح" }
        require(draft.evaluation in setOf("not_rated", "excellent", "very_good", "good", "repeat")) {
            "التقييم غير صالح"
        }
        require(draft.inputMethod in setOf("pages", "ayahs", "surahs", "juz", "import")) { "طريقة الإدخال غير صالحة" }
        require(draft.ayahsCount >= 0 && draft.surahsCount >= 0) { "إحصاءات النطاق غير صالحة" }
        draft.startSurah?.let { require(it in 1..114) { "سورة البداية غير صالحة" } }
        draft.endSurah?.let { require(it in 1..114) { "سورة النهاية غير صالحة" } }
        draft.startAyah?.let { require(it > 0) { "آية البداية غير صالحة" } }
        draft.endAyah?.let { require(it > 0) { "آية النهاية غير صالحة" } }
        draft.startPage?.let { require(it in 1..604) { "صفحة البداية غير صالحة" } }
        draft.endPage?.let { require(it in 1..604) { "صفحة النهاية غير صالحة" } }
        draft.selectedSurahs.forEach { require(it in 1..114) { "رقم سورة مختارة غير صالح" } }
        draft.selectedJuz.forEach { require(it in 1..30) { "رقم جزء مختار غير صالح" } }
        draft.traversalDirection?.let {
            require(it in setOf("FORWARD_QURAN", "REVERSE_SURAH_ORDER", "REVERSE_WITHIN_SURAH", "PAGE_ORDER_FORWARD", "PAGE_ORDER_REVERSE", "EXPLICIT_SEGMENTS")) {
                "اتجاه النطاق غير صالح"
            }
        }

        val normalizedQcfLines = draft.qcfLineKeys.distinct().also { keys ->
            keys.forEach { key ->
                val parts = key.split(':')
                val page = parts.getOrNull(0)?.toIntOrNull()
                val line = parts.getOrNull(1)?.toIntOrNull()
                require(parts.size == 2 && page in 1..604 && line in 1..15) { "مفتاح QCF4 غير صالح: $key" }
            }
        }
        // QCF4 line keys are the authoritative unit for partial-page selections.
        // Never round here: 1 line = 1/15 page. Display rounding belongs to the UI only.
        val actualPages = if (normalizedQcfLines.isNotEmpty()) {
            normalizedQcfLines.distinct().size.toDouble() / 15.0
        } else draft.pages
        require(actualPages in 0.0..80.0) { "عدد الصفحات خارج النطاق المعتمد" }

        val id = draft.clientId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val tilawahScore = draft.tilawahAssessment
            ?.takeIf { draft.activityType == "tilawah" }
            ?.optDouble("overall_score", Double.NaN)
            ?.takeIf { !it.isNaN() }
            ?.coerceIn(0.0, 100.0)
        val safety = tilawahScore ?: SafetyScoring.calculate(actualPages, draft.mistakes, draft.prompts)
        val evaluation = if (draft.activityType == "tilawah" && tilawahScore != null) {
            when {
                tilawahScore >= 90.0 -> "excellent"
                tilawahScore >= 80.0 -> "very_good"
                tilawahScore >= 70.0 -> "good"
                else -> "repeat"
            }
        } else SafetyScoring.inferredEvaluation(safety, actualPages > 0.0)
        val points = if (draft.activityType == "tilawah" && tilawahScore != null) {
            kotlin.math.round((actualPages * (tilawahScore / 100.0)) * 100.0) / 100.0
        } else SafetyScoring.points(actualPages, draft.mistakes, draft.prompts)
        val penaltyUnits = if (draft.activityType == "tilawah") draft.mistakes.toDouble() else SafetyScoring.penaltyUnits(draft.mistakes, draft.prompts)
        val penaltyPerPage = if (actualPages > 0.0) penaltyUnits / actualPages else 0.0
        val resolvedSmartPlanId = draft.smartPlanId?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
            ?: draft.smartPlanPhaseId?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }?.let { phaseId ->
                planPhases(forceRefresh = false).firstOrNull { it.id == phaseId }?.planId
            }
        val resolvedSmartPlanPhaseId = draft.smartPlanPhaseId?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }

        val selectionData = JSONObject()
            .put("native", true)
            .put("quran_engine", "rc8.2")
            .put("line_model", 15)
            .put("mode", draft.inputMethod)
            .put("input_method", draft.inputMethod)
        if (draft.inputMethod == "import") selectionData.put("imported", true)
        if (draft.startSurah != null && draft.startAyah != null) {
            selectionData.put("start_global", runCatching { com.mohammedsamir.halaqati.quran.QuranMetadata.globalAyah(draft.startSurah, draft.startAyah) }.getOrNull())
        }
        if (draft.endSurah != null && draft.endAyah != null) {
            selectionData.put("end_global", runCatching { com.mohammedsamir.halaqati.quran.QuranMetadata.globalAyah(draft.endSurah, draft.endAyah) }.getOrNull())
        }
        resolvedSmartPlanId?.let { selectionData.put("smart_plan_id", it) }
        resolvedSmartPlanPhaseId?.let { selectionData.put("smart_plan_phase_id", it) }
        draft.smartPlanType?.takeIf { it.isNotBlank() }?.let { selectionData.put("smart_plan_type", it) }
        draft.smartPlanDate?.takeIf { it.isNotBlank() }?.let { selectionData.put("smart_plan_date", it) }
        if (normalizedQcfLines.isNotEmpty()) selectionData.put("qcf_line_keys", JSONArray(normalizedQcfLines))
        draft.traversalDirection?.takeIf { it.isNotBlank() }?.let { selectionData.put("quran_traversal_direction", it) }
        if (draft.quranSegments.isNotEmpty()) selectionData.put("quran_segments", JSONArray(draft.quranSegments))
        if (draft.selectedSurahs.isNotEmpty()) selectionData.put("selected_surahs", JSONArray(draft.selectedSurahs))
        if (draft.selectedJuz.isNotEmpty()) selectionData.put("selected_juz", JSONArray(draft.selectedJuz))
        draft.tilawahAssessment?.takeIf { draft.activityType == "tilawah" }?.let {
            selectionData.put("tilawah_assessment", JSONObject(it.toString()))
        }

        val obj = JSONObject()
            .put("id", id)
            .put("student_id", draft.studentId)
            .put("session_id", draft.sessionId)
            .put("activity_type", draft.activityType)
            .put("input_method", draft.inputMethod)
            .put("pages_count", actualPages)
            .put("ayahs_count", draft.ayahsCount)
            .put("surahs_count", draft.surahsCount)
            .put("mistakes", draft.mistakes)
            .put("prompts", draft.prompts)
            .put("evaluation", evaluation)
            // RC39: a recitation with no content has no safety (it used to be stored as 0 and dragged averages down).
            .put("safety_percentage", if (actualPages <= 0.0 && evaluation == "not_rated") JSONObject.NULL else safety)
            .put("penalty_units", penaltyUnits)
            .put("penalty_per_page", penaltyPerPage)
            .put("points", points)
            .put("scoring_version", if (draft.activityType == "tilawah") "TILAWAH_V1" else "V6")
            .put("selection_data", selectionData)
        // RC15.2: tathbit recitations belong to the monthly plan, never to a phase. The server
        // trigger resolves plan_id from the session date, so no schema-dependent column is sent.
        if (draft.activityType != TrackActivityPolicy.TATHBIT) resolvedSmartPlanPhaseId?.let { obj.put("phase_id", it) }
        if (draft.notes.isNotBlank()) obj.put("teacher_note", draft.notes)
        draft.startSurah?.let { obj.put("start_surah", it) }
        draft.startAyah?.let { obj.put("start_ayah", it) }
        draft.endSurah?.let { obj.put("end_surah", it) }
        draft.endAyah?.let { obj.put("end_ayah", it) }
        draft.startPage?.let { obj.put("start_page", it) }
        draft.endPage?.let { obj.put("end_page", it) }
        if (draft.selectedSurahs.isNotEmpty()) obj.put("selected_surahs", JSONArray(draft.selectedSurahs))
        store.load()?.userId?.let { obj.put("created_by", it) }

        queued(
            "POST",
            "/rest/v1/recitation_entries?on_conflict=id",
            obj,
            "recitation:${draft.sessionId}:${draft.studentId}:$id",
        )
        val cachedRow = JSONObject(obj.toString()).put("created_at", draft.createdAt?.takeIf { it.isNotBlank() } ?: java.time.Instant.now().toString())
        upsertSessionScopedCache(CACHE_SESSION_RECITATIONS_PREFIX, draft.sessionId, cachedRow)
        upsertPlanRecitationCacheRow(cachedRow, draft.sessionId)
        invalidatePlanSnapshotMemory()
        return id
    }

    /**
     * Immediate RC10 Undo. It only succeeds while every operation created by the quick-save
     * is still pending in this account's durable Outbox. If sync already consumed any part,
     * nothing is deleted locally; the caller can fall back to the full session editor.
     */
    fun undoPendingQuickSave(sessionId: String, studentId: String, recitationId: String? = null): Boolean {
        val owner = currentUserId() ?: return false
        val dedupes = buildList {
            add("attendance:$sessionId:$studentId")
            recitationId?.takeIf { it.isNotBlank() }?.let { add("recitation:$sessionId:$studentId:$it") }
        }
        val discarded = queue.discardPendingDedupesIfComplete(owner, dedupes)
        if (discarded && !recitationId.isNullOrBlank()) {
            removeSessionScopedCacheRow(CACHE_SESSION_RECITATIONS_PREFIX, sessionId, recitationId)
            removePlanRecitationCacheRow(recitationId)
            invalidatePlanSnapshotMemory()
        }
        return discarded
    }

    /** RC9.3 historical recitation delete. The mutation is idempotent, removes the local
     * session cache row immediately after commit, and invalidates the derived Smart Plan state
     * so anchors/month/week totals are rebuilt from the remaining authoritative history. */
    fun deleteRecitation(sessionId: String, studentId: String, recitationId: String) {
        require(sessionId.isNotBlank()) { "معرّف الجلسة غير صالح" }
        require(studentId.isNotBlank()) { "معرّف الطالب غير صالح" }
        require(recitationId.isNotBlank()) { "معرّف التسميع غير صالح" }
        delete(
            "recitation_entries",
            "id=eq.$recitationId",
            dedupe = "recitation-delete:$sessionId:$studentId:$recitationId",
        )
        removeSessionScopedCacheRow(CACHE_SESSION_RECITATIONS_PREFIX, sessionId, recitationId)
        removePlanRecitationCacheRow(recitationId)
        invalidatePlanSnapshotMemory()
    }

    /** RC9.2 roster Undo for a newly-created recitation. Synced historical entries are edited,
     * never destructively recreated. */
    fun undoPendingRecitation(sessionId: String, studentId: String, recitationId: String): Boolean {
        val owner = currentUserId() ?: return false
        if (recitationId.isBlank()) return false
        val dedupe = "recitation:$sessionId:$studentId:$recitationId"
        val discarded = queue.discardPendingDedupesIfComplete(owner, listOf(dedupe))
        if (discarded) {
            removeSessionScopedCacheRow(CACHE_SESSION_RECITATIONS_PREFIX, sessionId, recitationId)
            removePlanRecitationCacheRow(recitationId)
            invalidatePlanSnapshotMemory()
        }
        return discarded
    }

    fun currentUserId(): String? = store.load()?.userId?.takeIf { it.isNotBlank() }

    fun pendingQueueSize(): Int = currentUserId()?.let { queue.size(it) } ?: 0
    fun blockedQueueSize(): Int = currentUserId()?.let { queue.blockedSize(it) } ?: 0
    fun conflictQueueSize(): Int = currentUserId()?.let { queue.conflictSize(it) } ?: 0

    /** RC31: minutes the oldest unsent change has been waiting (0 when nothing waits). Reads Room: call off the main thread. */
    fun oldestPendingMinutes(): Int =
        currentUserId()?.let { queue.oldestPendingAt(it) }
            ?.let { ((System.currentTimeMillis() - it) / 60_000L).toInt().coerceAtLeast(0) } ?: 0

    /** RC15: read-only tathbit consistency report (server view). Empty when offline or not migrated. */
    fun tathbitConsistencyIssues(): List<String> {
        if (!online()) return emptyList()
        val arr = runCatching { withAuthRetry { api.select("rc15_tathbit_consistency_issues", "select=issue,record_id,value&limit=200") } }.getOrNull() ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val row = arr.optJSONObject(i) ?: return@mapNotNull null
            val label = when (row.optString("issue")) {
                "tathbit_student_non_tathbit_plan" -> "طالب تثبيت لديه خطة ${row.optString("value")}"
                "tathbit_plan_non_tathbit_phase" -> "خطة تثبيت فيها مرحلة ${row.optString("value")}"
                "general_student_tathbit_plan" -> "طالب حفظ عام لديه خطة تثبيت"
                else -> row.optString("issue")
            }
            "$label · ${row.optString("record_id").take(8)}"
        }
    }

    /** RC15: atomic plan + phases + states through one server transaction (RLS still enforced). */
    fun createSmartPlanBundle(plan: JSONObject, phases: JSONArray, states: JSONArray = JSONArray()): String {
        val clean = SyncErrorRules.sanitizeSmartPlan(plan) { studentTrackOf(it) }
        val planId = clean.optString("id").ifBlank { UUID.randomUUID().toString().also { clean.put("id", it) } }
        rpcWrite(
            "rc15_create_smart_plan_bundle",
            JSONObject().put("p_plan", clean).put("p_phases", phases).put("p_states", states),
            dedupe = "smart-plan-bundle:$planId",
        )
        upsertPlanCache(queueOwnerId(), planId, clean)
        return planId
    }

    fun queueSnapshot(limit: Int = 200): List<OfflineQueue.SnapshotItem> =
        currentUserId()?.let { queue.snapshot(it, limit) } ?: emptyList()

    fun retryBlockedQueueItem(id: Long) { currentUserId()?.let { queue.retryBlocked(id, it); signalLocalMutation() } }
    fun discardQueueItem(id: Long) { currentUserId()?.let { queue.discard(id, it); signalLocalMutation() } }

    fun retryAllBlockedQueueItems() {
        val owner = currentUserId() ?: return
        queue.snapshot(owner, 1000).filter { it.blocked && !it.conflict }.forEach { queue.retryBlocked(it.id, owner) }
        signalLocalMutation()
        scheduleSync()
    }

    fun resolveConflictUseLocal(id: Long) {
        val owner = currentUserId() ?: return
        queue.forceLocal(id, owner)
        signalLocalMutation()
        scheduleSync()
    }

    fun resolveConflictUseServer(id: Long) {
        val owner = currentUserId() ?: return
        val item = queue.item(id, owner) ?: return
        val server = item.serverJson?.let { runCatching { JSONObject(it) }.getOrNull() }
        if (item.entityTable == "students" && !item.entityId.isNullOrBlank()) {
            if (server != null) upsertStudentLocal(owner, server, dirty = false)
            else offlineDb.students().deleteAny(owner, item.entityId)
            cache.put(owner, CACHE_STUDENTS, localStudentsJson(owner).toString())
        }
        queue.discard(id, owner)
        signalLocalMutation()
    }

    private fun fetchServerStudent(id: String): JSONObject? = withAuthRetry {
        api.select("students", "select=*&id=eq.$id&limit=1")
    }.optJSONObject(0)

    private fun detectConflict(owner: String, item: OfflineQueue.Item): Boolean {
        if (item.forceLocal || item.entityTable != "students" || item.entityId.isNullOrBlank() || item.baseUpdatedAt.isNullOrBlank()) return false
        val server = fetchServerStudent(item.entityId)
        val serverUpdated = server?.optString("updated_at")?.ifBlank { null }
        if (!ConflictRules.changedSinceBase(item.baseUpdatedAt, serverUpdated)) return false
        queue.markConflict(
            item.id, owner,
            "تعارض: نسخة الخادم تغيّرت بعد بدء تعديلك. اختر اعتماد تعديلي أو اعتماد نسخة الخادم.",
            server?.toString(),
        )
        return true
    }

    /**
     * RC8.6 V3 migration: older builds queued four mutations for one session deletion.
     * The live database uses ON DELETE CASCADE, so if the parent session delete is pending,
     * the three child deletes are redundant. Compact them before upload so existing devices
     * immediately benefit from the one-request path after upgrading.
     */
    private fun serverSessionHasData(sessionId: String): Boolean =
        listOf("recitation_entries", "attendance_records", "student_daily_notes").any { table ->
            api.select(table, "select=id&session_id=eq.$sessionId&limit=1").length() > 0
        }

    private fun compactLegacySessionDeletes(owner: String) {
        val snapshot = queue.snapshot(owner, 2_000)
        val parentIds = snapshot.asSequence()
            .mapNotNull { item -> item.dedupe?.takeIf { it.startsWith("session-delete:") }?.substringAfter("session-delete:") }
            .filter(String::isNotBlank)
            .toSet()
        if (parentIds.isEmpty()) return
        val obsoletePrefixes = listOf("session-recitation-delete:", "session-attendance-delete:", "session-notes-delete:")
        snapshot.forEach { item ->
            val dedupe = item.dedupe.orEmpty()
            val obsolete = obsoletePrefixes.any { prefix ->
                dedupe.startsWith(prefix) && dedupe.substringAfter(prefix) in parentIds
            }
            if (obsolete && !item.blocked) queue.discard(item.id, owner)
        }
    }

    private fun backgroundRefreshDue(owner: String, minimumAgeMs: Long = 120_000L): Boolean {
        val last = offlineDb.syncMeta().get(owner)?.lastPullAt ?: return true
        return System.currentTimeMillis() - last >= minimumAgeMs
    }

    private fun repairLegacyStudentSchemaQueue(owner: String) {
        queue.snapshot(owner, 2_000).forEach { item ->
            if (!item.path.contains("/rest/v1/students")) return@forEach
            val raw = item.body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@forEach
            if (!StudentSchemaContract.containsUnsupported(raw)) return@forEach
            val clean = StudentSchemaContract.sanitize(raw)
            val entityId = item.entityId ?: clean.optString("id").takeIf { it.isNotBlank() }
            queue.add(
                ownerUserId = owner,
                method = item.method,
                path = item.path,
                body = clean.toString(),
                dedupe = item.dedupe,
                baseUpdatedAt = item.baseUpdatedAt,
                entityTable = "students",
                entityId = entityId,
            )
        }
    }

    /**
     * RC17.1 BUILD FIX 2 migration for devices that already queued/blocked a smart-plan edit
     * containing parent_plan_id/student_id/category_id as the literal string "null".
     * Replacing the same dedupe atomically resets the row to pending with a sanitized JSON null,
     * so the user does not need to delete the operation or recreate the plan.
     */
    private fun repairLegacySmartPlanUuidQueue(owner: String) {
        queue.snapshot(owner, 2_000).forEach { item ->
            if (item.entityTable != "smart_plans" && !item.path.contains("/rest/v1/smart_plans")) return@forEach
            if (item.method == "DELETE") return@forEach
            val raw = item.body?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return@forEach
            val clean = SyncErrorRules.sanitizeSmartPlan(raw) { studentTrackOf(it) }
            val pathId = Regex("""(?:\?|&)id=eq\.([^&]+)""").find(item.path)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
            val entityId = item.entityId ?: pathId ?: clean.optString("id").takeIf { it.isNotBlank() }
            val bodyChanged = clean.toString() != raw.toString()
            val metadataChanged = entityId != item.entityId
            if (!bodyChanged && !metadataChanged) return@forEach
            queue.add(
                ownerUserId = owner,
                method = item.method,
                path = item.path,
                body = clean.toString(),
                dedupe = item.dedupe,
                baseUpdatedAt = item.baseUpdatedAt,
                entityTable = "smart_plans",
                entityId = entityId,
            )
        }
    }

    /** RC25 (admin): sessions removed from the server that can still be restored from their snapshot. */
    fun deletedSessionSnapshots(): List<JSONObject> {
        val rows = withAuthRetry {
            api.select(
                "deleted_session_snapshots",
                "select=id,session_id,deleted_at,restored_at,session_row,recitation_entries,attendance_records,student_daily_notes" +
                    "&order=deleted_at.desc&limit=100",
            )
        }
        return (0 until rows.length()).mapNotNull { rows.optJSONObject(it) }
    }

    /** RC25 (admin): one-tap restore through the security-definer RPC rc25_restore_deleted_session. */
    fun restoreDeletedSession(snapshotId: String): String {
        val raw = withAuthRetry { api.rpc("rc25_restore_deleted_session", JSONObject().put("p_snapshot_id", snapshotId)) }
        runCatching { refreshSessions(includeArchived = true) }
        invalidateSessionDerivedCaches()
        return raw
    }

    private val sessionChildTables = listOf("recitation_entries", "attendance_records", "student_daily_notes")

    /** RC25: recreate a session row (same id) that vanished on the server; returns true if recreated. */
    private fun recreateMissingParentSession(owner: String, path: String, body: String?): Boolean {
        if (sessionChildTables.none { path.contains("/rest/v1/$it") } || body.isNullOrBlank()) return false
        val sid = runCatching {
            val text = body.trim()
            val obj = if (text.startsWith("[")) JSONArray(text).optJSONObject(0) else JSONObject(text)
            obj?.optString("session_id")?.takeIf { it.isNotBlank() }
        }.getOrNull() ?: return false
        val local = cachedSessions(owner, maxAge = null, includeArchived = true)?.firstOrNull { it.id == sid } ?: return false
        return runCatching {
            serverGetOrCreateDailySession(local.halaqaId, local.date, local.type, local.title, local.id)
            true
        }.getOrDefault(false)
    }

    fun flushQueue(): Int {
        if (!online()) return 0
        val owner = currentUserId() ?: return 0
        repairLegacyStudentSchemaQueue(owner)
        repairLegacySmartPlanUuidQueue(owner)
        compactLegacySessionDeletes(owner)
        var pushed = 0
        // RC15 parent/child gating: a phase is never sent when its plan failed, and a phase
        // state is never sent when its phase failed. Blocked parents from earlier runs count too.
        val blockedSnapshot = queue.snapshot(owner, 2000).filter { it.blocked }
        val failedPlanIds = blockedSnapshot.filter { it.entityTable == "smart_plans" }.mapNotNull { it.entityId }.toMutableSet()
        val failedPhaseIds = blockedSnapshot.filter { it.entityTable == "smart_plan_phases" }.mapNotNull { it.entityId }.toMutableSet()
        for (item in queue.all(owner)) {
            if (detectConflict(owner, item)) continue
            val parsed = item.body?.let { runCatching { JSONObject(it) }.getOrNull() }
            val parentPlan = parsed?.optString("plan_id")?.takeIf { it.isNotBlank() }
            val parentPhase = parsed?.optString("phase_id")?.takeIf { it.isNotBlank() }
            val orphan = (item.entityTable == "smart_plan_phases" && parentPlan != null && parentPlan in failedPlanIds) ||
                (item.entityTable == "smart_plan_phase_states" && parentPhase != null && parentPhase in failedPhaseIds)
            if (orphan) {
                queue.markBlocked(item.id, owner, SyncErrorRules.label(SyncErrorRules.Kind.PARENT_MISSING, null, "أصلح الخطة/المرحلة الأم أولًا ثم أعد المحاولة."))
                if (item.entityTable == "smart_plan_phases") item.entityId?.let { failedPhaseIds += it }
                signalLocalMutation()
                continue
            }
            val body = if (item.entityTable == "smart_plans" && parsed != null && item.method != "DELETE") {
                SyncErrorRules.sanitizeSmartPlan(parsed) { studentTrackOf(it) }.toString()
            } else item.body
            try {
                // RC24.4: a queued session DELETE must never wipe recitation/attendance that another
                // device already uploaded. Daily session ids are deterministic, so two phones share one id.
                if (item.method == "DELETE" && item.entityTable == "sessions") {
                    val sid = SessionDeleteGuard.sessionIdFromPath(item.path)
                    if (sid != null && withAuthRetry { serverSessionHasData(sid) }) {
                        queue.done(item.id, owner)
                        signalLocalMutation()
                        continue
                    }
                }
                try {
                    withAuthRetry { api.raw(item.method, item.path, body) }
                } catch (fk: SupabaseException) {
                    // RC25 self-healing: the parent session was removed on the server while this
                    // device still holds its recitation/attendance/notes. Recreate the session with
                    // the same id and retry once so offline work is never lost.
                    if (fk.sqlState == "23503" && item.method != "DELETE" && recreateMissingParentSession(owner, item.path, body)) {
                        withAuthRetry { api.raw(item.method, item.path, body) }
                    } else throw fk
                }
                if (item.entityTable == "students" && !item.entityId.isNullOrBlank()) {
                    val server = runCatching { fetchServerStudent(item.entityId) }.getOrNull()
                    if (server != null) upsertStudentLocal(owner, server, dirty = false)
                    else if (item.method == "DELETE") offlineDb.students().deleteClean(owner, item.entityId)
                    cache.put(owner, CACHE_STUDENTS, localStudentsJson(owner).toString())
                }
                queue.done(item.id, owner)
                signalLocalMutation()
                pushed += 1
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                val status = (e as? SupabaseException)?.status ?: 0
                val sqlState = (e as? SupabaseException)?.sqlState
                val kind = if (e is SupabaseException) SyncErrorRules.classify(status, sqlState) else SyncErrorRules.Kind.TRANSIENT
                val message = SyncErrorRules.label(kind, sqlState, e.message ?: e::class.java.simpleName)
                signalLocalMutation()
                when (kind) {
                    SyncErrorRules.Kind.INVALID_PAYLOAD, SyncErrorRules.Kind.PERMISSION, SyncErrorRules.Kind.OTHER -> {
                        // Deterministic failure: block once, do not replay the same body again and again.
                        queue.markBlocked(item.id, owner, message)
                        if (item.entityTable == "smart_plans") item.entityId?.let { failedPlanIds += it }
                        if (item.entityTable == "smart_plan_phases") item.entityId?.let { failedPhaseIds += it }
                        continue
                    }
                    SyncErrorRules.Kind.SESSION, SyncErrorRules.Kind.TRANSIENT, SyncErrorRules.Kind.PARENT_MISSING -> {
                        // RC27.43: a request that keeps failing on the server itself (HTTP 5xx / timeout, not "offline")
                        // must not freeze every later change. After 6 failed tries park it as blocked and go on.
                        if (kind == SyncErrorRules.Kind.TRANSIENT && (status in 500..599 || status == 408) && item.attempts >= 6) {
                            queue.markBlocked(item.id, owner, message)
                            continue
                        }
                        queue.fail(item.id, owner, message)
                        // RC31: only a lost connection or an expired session stops the whole flush. A server error on ONE
                        // request (HTTP 5xx / timeout) is retried later, while everything queued after it still goes up now.
                        if (kind == SyncErrorRules.Kind.TRANSIENT && (status in 500..599 || status == 408)) continue
                        throw e
                    }
                }
            }
        }
        markPushed(owner)
        if (pushed > 0) invalidatePlanSnapshotMemory()
        return pushed
    }

    /**
     * Foreground sync path: upload durable local mutations only and return quickly.
     * Expensive pulls are scheduled separately so a tap on "مزامنة" never waits for reports,
     * rankings, supervision and every historical session to download again.
     */
    fun syncNow() {
        if (!online()) return
        val owner = currentUserId() ?: return
        val pushed = synchronized(pushLock) { flushQueue() }
        // Foreground/manual sync is an Outbox flush. A full account reconciliation is opportunistic
        // and throttled, never part of the user's spinner path.
        // RC27.34: pushes keep local caches up to date themselves; a full pull is scheduled at most every 30 minutes.
        if (backgroundRefreshDue(owner, FULL_REFRESH_MIN_GAP_MS)) scheduleBackgroundRefresh()
    }

    /** Periodic/background reconciliation. Never used as the blocking foreground sync path. */
    fun fullRefreshNow() {
        if (!online()) return
        val owner = currentUserId() ?: return
        synchronized(pushLock) { flushQueue() }
        // RC27.34 (data usage): a full account pull downloads thousands of rows. Do it at most every 30 minutes;
        // screens still refresh their own data when opened (90 s cache), so this only warms the offline copy.
        val lastFull = cache.get(owner, CACHE_FULL_REFRESH_AT, maxAge = null)?.toLongOrNull() ?: 0L
        if (System.currentTimeMillis() - lastFull < FULL_REFRESH_MIN_GAP_MS) return
        cache.put(owner, CACHE_FULL_REFRESH_AT, System.currentTimeMillis().toString())
        invalidatePlanSnapshotMemory()
        runCatching { refreshStudents() }
        runCatching { refreshSessions() }
        runCatching { warmRecentSessionDetails(owner, cachedSessions(owner, maxAge = null).orEmpty().map { it.id }) }
        runCatching { refreshPlans() }
        runCatching { accountProfiles(forceRefresh = true) }
        runCatching { guardianAccounts(forceRefresh = true) }
        runCatching { table("halaqas", "select=id,name,active&order=name.asc&limit=200", forceRefresh = true) }
        runCatching { table("halaqas", "select=id,name,active,halaqa_type,program_specialization,affects_primary_plan,starts_on,ends_on&order=name.asc&limit=200", forceRefresh = true) }
        runCatching { table("student_categories", "select=id,halaqa_id,name,active&order=name.asc&limit=500", forceRefresh = true) }
        runCatching {
            // RC27.34: only the last ~2 months are kept offline for rankings (was every recitation ever, up to 10000 rows, every pull).
            val windowStart = LocalDate.now().minusDays(RANKING_CACHE_DAYS).toString()
            val rankingSource = withAuthRetry {
                api.select(
                    "recitation_entries",
                    "select=student_id,pages_count,mistakes,prompts,points,activity_type,selection_data,start_surah,start_ayah,end_surah,end_ayah," +
                        "students!inner(full_name,category_id,track_code),sessions!inner(session_date)&sessions.session_date=gte.$windowStart&limit=10000",
                )
            }
            cache.put(owner, CACHE_RANKING_SOURCE, rankingSource.toString())
            cache.put(owner, CACHE_RANKING_SOURCE_FROM, windowStart)
        }
        runCatching { planEvents(forceRefresh = true) }
        // Warm the screens that must remain useful after connectivity/DNS disappears.
        runCatching { planSnapshots(LocalDate.now(), forceRefresh = true) }
        val today = LocalDate.now()
        val monthStart = YearMonth.from(today).atDay(1).toString()
        runCatching { reportRecitations(monthStart, today.toString(), "all", forceRefresh = true) }
        runCatching { reportSummary(monthStart, today.toString(), "all", "all", "all", forceRefresh = true) }
        runCatching { table("supervisor_visits", "select=*&order=visit_date.desc&limit=300", forceRefresh = true) }
        runCatching { table("student_goals", "select=*&period_start=lte.$today&period_end=gte.$today&limit=1000", forceRefresh = true) }
        // Supervision dashboard uses these exact projections; warm them so it can reopen fully offline.
        val supervisionSessions = runCatching { table("sessions", "select=*&order=session_date.desc&limit=300", forceRefresh = true) }.getOrElse { JSONArray() }
        runCatching {
            table(
                "smart_plans",
                "select=id,scope_type,halaqa_id,student_id,category_id,plan_type,start_date,end_date,status,settings&status=neq.archived&limit=1000",
                forceRefresh = true,
            )
        }
        runCatching { table("halaqas", "select=id,name&active=eq.true&order=name.asc&limit=100", forceRefresh = true) }
        val todaySessionIds = (0 until supervisionSessions.length()).mapNotNull { index ->
            supervisionSessions.optJSONObject(index)?.takeIf {
                it.optString("session_date") == today.toString() && it.optString("status") != "cancelled"
            }?.optString("id")?.takeIf(String::isNotBlank)
        }
        if (todaySessionIds.isNotEmpty()) {
            runCatching {
                table(
                    "attendance_records",
                    "select=student_id,status&session_id=in.(${todaySessionIds.joinToString(",")})&limit=3000",
                    forceRefresh = true,
                )
            }
        }
        runCatching { table("announcements", "select=*&order=pinned.desc&order=publish_at.desc&limit=200", forceRefresh = true) }
        runCatching { table("forum_posts", "select=*&order=pinned.desc&order=created_at.desc&limit=200", forceRefresh = true) }
        runCatching { table("forum_comments", "select=*&order=created_at.asc&limit=1000", forceRefresh = true) }
        offlineDb.syncMeta().put(
            (offlineDb.syncMeta().get(owner) ?: SyncMetaEntity(owner)).copy(
                lastPushAt = System.currentTimeMillis(), lastPullAt = System.currentTimeMillis(), lastError = null,
            ),
        )
    }

    fun scheduleBackgroundRefresh() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "halaqati-background-refresh",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FullSyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(5, java.util.concurrent.TimeUnit.SECONDS)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
        )
    }

    fun lastSyncInstant(): String? {
        val owner = currentUserId() ?: return null
        val meta = offlineDb.syncMeta().get(owner) ?: return null
        val millis = listOfNotNull(meta.lastPushAt, meta.lastPullAt).maxOrNull() ?: return null
        return java.time.Instant.ofEpochMilli(millis).toString()
    }

    private fun markPushed(owner: String) {
        val old = offlineDb.syncMeta().get(owner) ?: SyncMetaEntity(owner)
        offlineDb.syncMeta().put(old.copy(lastPushAt = System.currentTimeMillis(), lastError = null))
    }

    private fun markPulled(owner: String) {
        val old = offlineDb.syncMeta().get(owner) ?: SyncMetaEntity(owner)
        offlineDb.syncMeta().put(old.copy(lastPullAt = System.currentTimeMillis(), lastError = null))
    }

    fun scheduleSync() {
        val now = System.currentTimeMillis()
        if (now - lastScheduleSyncAt < 4_000L) return
        lastScheduleSyncAt = now
        WorkManager.getInstance(context).enqueueUniqueWork(
            "halaqati-sync-now",
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
                .build(),
        )
    }

}


/** RC27.8: builds a [HifzSpanRules.Entry] from a recitation_entries JSON row (ayah fields may be missing/null). */
internal fun hifzSpanEntryOf(row: JSONObject): com.mohammedsamir.halaqati.plans.HifzSpanRules.Entry {
    fun i(k: String): Int? = if (row.has(k) && !row.isNull(k)) row.optInt(k, -1).takeIf { it > 0 } else null
    return com.mohammedsamir.halaqati.plans.HifzSpanRules.Entry(
        i("start_surah"), i("start_ayah"), i("end_surah"), i("end_ayah"),
        row.optDouble("pages_count", 0.0).coerceAtLeast(0.0),
    )
}

/** RC27.8: "التلاوة والتجويد" is one program: filtering by either session type matches both. */
internal fun sessionTypeQuery(sessionType: String): String = when (sessionType) {
    "all" -> ""
    "tajweed", "tilawah" -> "&sessions.session_type=in.(tajweed,tilawah)"
    else -> "&sessions.session_type=eq.$sessionType"
}
