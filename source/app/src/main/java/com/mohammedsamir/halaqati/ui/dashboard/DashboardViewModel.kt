package com.mohammedsamir.halaqati.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.HalaqatiRepository
import com.mohammedsamir.halaqati.data.PlanSnapshot
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.SessionRow
import com.mohammedsamir.halaqati.model.Student
import com.mohammedsamir.halaqati.model.PlanRow
import com.mohammedsamir.halaqati.model.SyncState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import com.mohammedsamir.halaqati.plans.SmartPlanActivityRules
import java.time.LocalDate

class DashboardViewModel(
    private val repository: HalaqatiRepository = AppGraph.repo,
) : ViewModel() {
    private val _state = MutableStateFlow(DashboardUiState())
    val state: StateFlow<DashboardUiState> = _state.asStateFlow()

    private var boundOwner: String? = null

    init {
        viewModelScope.launch {
            repository.localMutationEpoch.collectLatest { epoch ->
                if (epoch > 0L && _state.value.profile != null) refreshLocalAttendanceSignals()
            }
        }
    }

    fun bind(profile: Profile, sync: SyncState) {
        val ownerChanged = boundOwner != profile.id
        val previousSync = _state.value.sync
        boundOwner = profile.id
        _state.value = _state.value.copy(profile = profile, sync = sync)
        if (ownerChanged) {
            viewModelScope.launch {
                val hadCache = loadCached(profile, sync)
                // Let cached UI reach the first frame before the heavier network fan-out starts.
                // No delay is introduced on a genuinely empty first launch.
                if (hadCache) delay(320)
                refresh(force = true)
            }
        } else if (previousSync != sync) {
            _state.value = _state.value.copy(sync = sync)
        }
    }

    private suspend fun refreshLocalAttendanceSignals() {
        val profile = _state.value.profile ?: return
        val todayDate = LocalDate.now()
        val today = todayDate.toString()
        val local = withContext(Dispatchers.IO) {
            val students = repository.cachedStudents(profile.id).orEmpty()
            val sessions = repository.cachedSessions(profile.id).orEmpty()
            val recitations = repository.cachedDashboardRecitations(profile.id)
            val plans = repository.cachedPlans(profile.id).orEmpty()
            Quadruple(students, sessions, recitations, plans)
        }
        val phaseAwareSnapshots = withContext(Dispatchers.IO) {
            runCatching { repository.planSnapshots(todayDate, forceRefresh = false) }.getOrDefault(emptyList())
        }
        val daily = deriveDailyOperation(todayDate, local.first, local.second, local.fourth, phaseAwareSnapshots)
        var rebuilt = buildState(
            profile = profile,
            sync = _state.value.sync,
            today = today,
            students = local.first,
            sessions = local.second,
            recitations = local.third,
            dailyOperation = daily,
            previous = _state.value,
            errors = _state.value.errors,
        )
        val attendanceSignals = if (!daily.countDailyMetrics) emptyList() else withContext(Dispatchers.IO) {
            val studentsById = local.first.associateBy { it.id }
            startedSessionsForToday(local.second, today).flatMap { session ->
                repository.attendanceForSession(session.id, allowNetwork = false).values.mapNotNull { row ->
                    val student = studentsById[row.studentId] ?: return@mapNotNull null
                    if (row.studentId in daily.expectedStudentIds && DashboardRules.isAbsentStatus(row.status)) {
                        DashboardAttentionItem(student.id, student.fullName, "غائب اليوم")
                    } else null
                }
            }.distinctBy { it.studentId }
        }
        val presentSignals = if (!daily.countDailyMetrics) emptySet() else withContext(Dispatchers.IO) {
            runCatching { presentTodayIds(local.second, today, allowNetwork = false) }.getOrDefault(emptySet())
        }
        rebuilt = rebuilt.copy(
            absentLate = attendanceSignals,
            presentIds = presentSignals,
            attention = _state.value.attention,
            attendance = _state.value.attendance,
            overTarget = _state.value.overTarget,
            smartPlanAlerts = _state.value.smartPlanAlerts,
            loaded = true,
        )
        _state.value = rebuilt
    }

    fun updateSync(sync: SyncState) {
        _state.value = _state.value.copy(sync = sync)
    }

    fun refresh(force: Boolean = true) {
        val profile = _state.value.profile ?: return
        val previous = _state.value
        _state.value = previous.copy(refreshing = true)
        viewModelScope.launch {
            val today = LocalDate.now().toString()
            val owner = profile.id
            val errors = linkedMapOf<String, String>()

            // RC17: the four inputs are independent → load them concurrently, then build snapshots
            // from the freshly loaded students/plans instead of fetching them again.
            val (studentsResult, sessionsResult, recitationsResult, plansResult) = coroutineScope {
                val st = async(Dispatchers.IO) {
                    runCatching { if (force) repository.refreshStudents() else repository.cachedStudents(owner) ?: repository.refreshStudents() }
                }
                val se = async(Dispatchers.IO) {
                    runCatching { if (force) repository.refreshSessions() else repository.cachedSessions(owner) ?: repository.refreshSessions() }
                }
                val rc = async(Dispatchers.IO) {
                    runCatching { if (force) repository.refreshDashboardRecitations() else repository.cachedDashboardRecitations(owner) ?: repository.refreshDashboardRecitations() }
                }
                val pl = async(Dispatchers.IO) { runCatching { repository.plans(forceRefresh = force) } }
                Quadruple(st.await(), se.await(), rc.await(), pl.await())
            }
            studentsResult.onFailure { errors["students"] = it.message ?: "تعذر تحديث الطلاب" }
            sessionsResult.onFailure { errors["sessions"] = it.message ?: "تعذر تحديث الجلسات" }
            recitationsResult.onFailure { errors["recitations"] = it.message ?: "تعذر تحديث التسميعات" }
            plansResult.onFailure { errors["daily_plan"] = it.message ?: "تعذر تحديث جدول اليوم" }

            val recitations = recitationsResult.getOrNull()
            val students = studentsResult.getOrNull()
            val sessions = sessionsResult.getOrNull()
            val plans = plansResult.getOrNull().orEmpty()
            val planSignals = com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                withContext(Dispatchers.IO) { repository.planSnapshots(LocalDate.now(), forceRefresh = force, baseInputsFresh = studentsResult.isSuccess && plansResult.isSuccess) }
            }.onFailure { errors["plans"] = it.message ?: "تعذر تحديث الخطط" }
                .getOrNull()
            val dailyOperation = deriveDailyOperation(LocalDate.now(), students.orEmpty(), sessions.orEmpty(), plans, planSignals.orEmpty())

            var merged = buildState(
                profile = profile,
                sync = _state.value.sync,
                today = today,
                students = students,
                sessions = sessions,
                recitations = recitations,
                dailyOperation = dailyOperation,
                previous = previous,
                errors = errors,
            )

            // Golden-master daily supervisor board: absence/late, plan delay,
            // review need, and students who exceeded their target. These are
            // fetched outside Compose so opening the dashboard never blocks UI.
            val attendanceSignals = if (!dailyOperation.countDailyMetrics) emptyList() else com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    val byId = students.orEmpty().associateBy { it.id }
                    startedSessionsForToday(sessions.orEmpty(), today).flatMap { session ->
                        repository.attendanceForSession(session.id).values.mapNotNull { row ->
                            val student = byId[row.studentId] ?: return@mapNotNull null
                            if (row.studentId in dailyOperation.expectedStudentIds && DashboardRules.isAbsentStatus(row.status)) {
                                DashboardAttentionItem(student.id, student.fullName, "غائب اليوم")
                            } else null
                        }
                    }.distinctBy { it.studentId }
                }
            }.onFailure { errors["attendance"] = it.message ?: "تعذر تحديث الحضور" }
                .getOrElse { previous.absentLate }

            val behind = planSignals?.filter { it.progress.behindBy > 0.01 }?.map {
                DashboardAttentionItem(it.student.id, it.student.fullName, "متأخر عن الخطة · ${"%.1f".format(java.util.Locale.US, it.progress.behindBy)} ص")
            }?.distinctBy { it.studentId } ?: previous.attention
            val exceeded = planSignals?.filter { it.progress.aheadBy > 0.01 }?.map {
                DashboardAttentionItem(it.student.id, it.student.fullName, "تجاوز هدفه · +${"%.1f".format(java.util.Locale.US, it.progress.aheadBy)} ص")
            }?.distinctBy { it.studentId } ?: previous.overTarget

            val byStudent = students.orEmpty().associateBy { it.id }
            val latestSafety = linkedMapOf<String, Double>()
            if (recitations != null) for (i in 0 until recitations.length()) {
                val row = recitations.optJSONObject(i) ?: continue
                val sid = row.optString("student_id")
                if (com.mohammedsamir.halaqati.plans.RecitationQualityRules.isPlaceholder(row.optString("evaluation"), row.optDouble("safety_percentage", 100.0), row.optDouble("pages_count", 0.0))) continue
                if (sid.isNotBlank() && sid !in latestSafety) latestSafety[sid] = row.optDouble("safety_percentage", 100.0)
            }
            val review = if (recitations == null) previous.attendance else latestSafety.entries
                .filter { it.value < 78.0 }
                .mapNotNull { (sid, safety) -> byStudent[sid]?.let { DashboardAttentionItem(sid, it.fullName, "يحتاج مراجعة · ${"%.0f".format(java.util.Locale.US, safety)}%") } }

            val presentSignals = if (!dailyOperation.countDailyMetrics) emptySet() else com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                withContext(Dispatchers.IO) { presentTodayIds(sessions.orEmpty(), today, allowNetwork = true) }
            }.getOrDefault(previous.presentIds)
            merged = merged.copy(
                absentLate = attendanceSignals,
                presentIds = presentSignals,
                attention = behind,
                attendance = review,
                overTarget = exceeded,
                smartPlanAlerts = planSignals.orEmpty().filter { it.progress.consecutiveMissed > 0 }.map {
                    "${it.student.fullName}: ${it.progress.healthLabel}"
                },
                errors = errors,
            )
            _state.value = merged.copy(refreshing = false, loaded = true)
        }
    }

    private suspend fun loadCached(profile: Profile, sync: SyncState): Boolean {
        val cached = withContext(Dispatchers.IO) {
            Quadruple(
                repository.cachedStudents(profile.id),
                repository.cachedSessions(profile.id),
                repository.cachedDashboardRecitations(profile.id),
                repository.cachedPlans(profile.id),
            )
        }
        if (cached.first == null && cached.second == null && cached.third == null && cached.fourth == null) return false
        val todayDate = LocalDate.now()
        val phaseAwareSnapshots = withContext(Dispatchers.IO) {
            runCatching { repository.planSnapshots(todayDate, forceRefresh = false) }.getOrDefault(emptyList())
        }
        val daily = deriveDailyOperation(todayDate, cached.first.orEmpty(), cached.second.orEmpty(), cached.fourth.orEmpty(), phaseAwareSnapshots)
        _state.value = buildState(
            profile = profile,
            sync = sync,
            today = todayDate.toString(),
            students = cached.first,
            sessions = cached.second,
            recitations = cached.third,
            dailyOperation = daily,
            previous = _state.value,
            errors = emptyMap(),
        ).copy(loaded = true)
        return true
    }

    private fun buildState(
        profile: Profile,
        sync: SyncState,
        today: String,
        students: List<Student>?,
        sessions: List<SessionRow>?,
        recitations: JSONArray?,
        dailyOperation: DailyOperationStateEngine.State,
        previous: DashboardUiState,
        errors: Map<String, String>,
    ): DashboardUiState {
        val activeStudents = students?.filter { it.status == "active" }
        val expectedIds = dailyOperation.expectedStudentIds
        val todaySessionIds = startedSessionsForToday(sessions.orEmpty(), today).mapTo(linkedSetOf()) { it.id }
        val heardToday = linkedSetOf<String>()
        var pages = 0.0
        if (dailyOperation.countDailyMetrics && recitations != null) {
            for (i in 0 until recitations.length()) {
                val row = recitations.optJSONObject(i) ?: continue
                val sessionId = row.optString("session_id")
                if (sessionId !in todaySessionIds) continue
                val studentId = row.optString("student_id")
                // RC81: everyone recited today counts, not only students the plan scheduled for today.
                if (studentId.isNotBlank()) heardToday.add(studentId)
                pages += row.optDouble("pages_count", 0.0)
            }
        }
        val notHeard = if (!dailyOperation.countDailyMetrics) emptyList() else activeStudents.orEmpty()
            .filter { it.id in expectedIds && it.id !in heardToday }
            .map { DashboardAttentionItem(it.id, it.fullName, "لم يسمّع اليوم") }
        val progress = DashboardProgress(
            recordedPages = pages,
            heardStudents = heardToday.size,
            activeStudents = activeStudents?.size ?: previous.progress.activeStudents,
        )
        return previous.copy(
            profile = profile,
            today = today,
            notHeard = notHeard,
            heardIds = heardToday,
            progress = progress,
            todaySessions = sessions?.count { it.date == today && it.status != "cancelled" } ?: previous.todaySessions,
            dailyOperation = dailyOperation,
            sync = sync,
            errors = errors,
        )
    }

    /** RC81: students marked present or late in today's started sessions (read from the stored attendance copy). */
    private fun presentTodayIds(sessions: List<SessionRow>, today: String, allowNetwork: Boolean): Set<String> =
        startedSessionsForToday(sessions, today).flatMap { session ->
            repository.attendanceForSession(session.id, allowNetwork = allowNetwork).values
                .filter { it.status.lowercase() in setOf("present", "late") }
                .map { it.studentId }
        }.toSet()

    private fun startedSessionsForToday(sessions: List<SessionRow>, today: String): List<SessionRow> = sessions.filter {
        it.date == today && it.status.lowercase() !in setOf("cancelled", "planned", "draft")
    }

    private fun deriveDailyOperation(
        today: LocalDate,
        students: List<Student>,
        sessions: List<SessionRow>,
        plans: List<PlanRow>,
        phaseAwareSnapshots: List<PlanSnapshot> = emptyList(),
    ): DailyOperationStateEngine.State {
        val activeStudents = students.filter { it.status == "active" }
        val activeIds = activeStudents.mapTo(linkedSetOf()) { it.id }
        val scheduled = if (phaseAwareSnapshots.isNotEmpty()) {
            // RC11.3: the dashboard follows the student's effective phase, not the legacy
            // parent Smart Plan. This prevents a Sard phase from being shown as Hifz and
            // prevents parent-plan weekdays from leaking into phase gap/off days.
            phaseAwareSnapshots.asSequence()
                .filter { it.student.id in activeIds && planScheduledOn(it.plan, today) }
                .flatMap { snapshot ->
                    SmartPlanActivityRules.enabledActivities(snapshot.plan.type, snapshot.plan.settings.enabledActivities)
                        .asSequence()
                        .map { key ->
                            val activity = when (key) {
                                "sard" -> DailyOperationStateEngine.Activity.SARD
                                "review" -> DailyOperationStateEngine.Activity.REVIEW
                                "tathbit" -> DailyOperationStateEngine.Activity.TATHBIT
                                else -> DailyOperationStateEngine.Activity.HIFZ
                            }
                            DailyOperationStateEngine.ScheduledActivity(snapshot.student.id, activity)
                        }
                }
                .distinct()
                .toList()
        } else {
            plans.asSequence()
                .filter { planScheduledOn(plan = it, today = today) }
                .flatMap { plan ->
                    val targetStudents = when (plan.scopeType) {
                        "student" -> activeStudents.filter { it.id == plan.studentId }
                        "category" -> activeStudents.filter { it.categoryId != null && it.categoryId == plan.categoryId && (plan.halaqaId.isBlank() || it.halaqaId == plan.halaqaId) }
                        else -> activeStudents.filter { plan.halaqaId.isBlank() || it.halaqaId == plan.halaqaId }
                    }
                    val activities = SmartPlanActivityRules.enabledActivities(plan.type, plan.settings.enabledActivities).map { key ->
                        when (key) {
                            "sard" -> DailyOperationStateEngine.Activity.SARD
                            "review" -> DailyOperationStateEngine.Activity.REVIEW
                            "tathbit" -> DailyOperationStateEngine.Activity.TATHBIT
                            else -> DailyOperationStateEngine.Activity.HIFZ
                        }
                    }
                    targetStudents.asSequence().flatMap { student ->
                        activities.asSequence().map { activity -> DailyOperationStateEngine.ScheduledActivity(student.id, activity) }
                    }
                }
                .distinct()
                .toList()
        }
        val todaySessions = sessions.filter { it.date == today.toString() && it.status.lowercase() != "cancelled" }
            .map { DailyOperationStateEngine.SessionSignal(it.type, it.status) }
        return DailyOperationStateEngine.evaluate(activeIds, scheduled, todaySessions)
    }

    private fun planScheduledOn(plan: PlanRow, today: LocalDate): Boolean {
        if (plan.status != "active") return false
        val start = runCatching { LocalDate.parse(plan.startDate) }.getOrNull() ?: return false
        val end = runCatching { LocalDate.parse(plan.endDate) }.getOrNull() ?: return false
        val pauseRanges = plan.settings.pauseRanges.mapNotNull { (from, to) ->
            val a = runCatching { LocalDate.parse(from) }.getOrNull()
            val b = runCatching { LocalDate.parse(to) }.getOrNull()
            if (a == null || b == null) null else a to b
        }
        val intensiveFrom = plan.settings.intensiveWeekFrom?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val intensiveTo = plan.settings.intensiveWeekTo?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val intensiveRange = if (intensiveFrom != null && intensiveTo != null) intensiveFrom to intensiveTo else null
        return DailyOperationStateEngine.isScheduledDay(
            date = today,
            start = start,
            end = end,
            scheduleWeekdays = plan.scheduleWeekdays.toSet(),
            catchUpWeekday = plan.catchUpWeekday,
            pauseRanges = pauseRanges,
            intensiveRange = intensiveRange,
            intensiveWeekdays = plan.settings.intensiveWeekdays.toSet(),
            intensiveEnabled = plan.type == "sard",
        )
    }

    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
    )

}
