package com.mohammedsamir.halaqati.ui.dashboard

import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.SyncState

data class DashboardAttentionItem(
    val studentId: String,
    val studentName: String,
    val reason: String,
)

data class DashboardProgress(
    val recordedPages: Double = 0.0,
    val heardStudents: Int = 0,
    val activeStudents: Int = 0,
)

data class DashboardUiState(
    val profile: Profile? = null,
    val today: String = "",
    val attendance: List<DashboardAttentionItem> = emptyList(),
    val notHeard: List<DashboardAttentionItem> = emptyList(),
    val absentLate: List<DashboardAttentionItem> = emptyList(),
    /** RC81: every student with a recitation in today's started sessions, and every student marked present / late today. */
    val heardIds: Set<String> = emptySet(),
    val presentIds: Set<String> = emptySet(),
    val attention: List<DashboardAttentionItem> = emptyList(),
    val overTarget: List<DashboardAttentionItem> = emptyList(),
    val smartPlanAlerts: List<String> = emptyList(),
    val progress: DashboardProgress = DashboardProgress(),
    val rankingHighlights: List<String> = emptyList(),
    val todaySessions: Int = 0,
    val dailyOperation: DailyOperationStateEngine.State = DailyOperationStateEngine.evaluate(
        activeStudentIds = emptySet(),
        scheduled = emptyList(),
        sessions = emptyList(),
    ),
    val sync: SyncState = SyncState(),
    val loaded: Boolean = false,
    val refreshing: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
)
