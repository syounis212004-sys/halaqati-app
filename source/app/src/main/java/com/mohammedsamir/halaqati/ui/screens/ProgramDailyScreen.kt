package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.WorkModeHolder
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.SyncState
import com.mohammedsamir.halaqati.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class ProgramDay(
    val students: Int,
    val sessions: Int,
    val present: Int,
    val absent: Int,
    val assessed: Int?,
    val followUp: List<Pair<String, String>>,
)

/** Program-only daily metrics. A missing attendance record is never treated as presence or absence. */
@Composable
internal fun ProgramDailyScreen(
    profile: Profile,
    sync: SyncState,
    navigate: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    followUpOnly: Boolean = false,
) {
    val mode by WorkModeHolder.state.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val ids = mode.programIds
    var data by remember(profile.id, ids) { mutableStateOf<ProgramDay?>(null) }
    var error by remember(profile.id, ids) { mutableStateOf(false) }
    LaunchedEffect(profile.id, ids, refresh, sync) {
        if (ids.isEmpty()) return@LaunchedEffect
        error = false
        try {
            data = withContext(Dispatchers.IO) {
                val repo = AppGraph.repo
                val members = ids.flatMap { repo.studentsForHalaqa(it, forceRefresh = refresh > 0) }.filter { it.status == "active" }.distinctBy { it.id }
                val memberIds = members.map { it.id }.toSet()
                val today = LocalDate.now(GazaTime.zone).toString()
                val sessions = repo.sessions(forceRefresh = refresh > 0).filter { it.halaqaId in ids && it.date == today && !it.archived && it.status != "cancelled" }
                val attendance = sessions.flatMap { repo.attendanceForSession(it.id).values }.filter { it.studentId in memberIds }
                val present = attendance.filter { it.status in setOf("present", "late") }.map { it.studentId }.toSet()
                val absent = attendance.filter { it.status == "absent" }.map { it.studentId }.toSet() - present
                val lessons = sessions.filter { it.type == "lesson" }
                val assessed = if (lessons.isEmpty()) emptySet() else runCatching {
                    lessons.flatMap { repo.educationScores(it.id, refresh = refresh > 0).filterValues { it.containsKey("general") }.keys }.toSet().intersect(memberIds)
                }.getOrNull()
                ProgramDay(members.size, sessions.size, present.size, absent.size, assessed?.size,
                    members.filter { it.id in absent }.map { it.id to it.fullName })
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) { error = true }
    }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(if (followUpOnly) "متابعة البرامج" else "لوحة البرامج", onBack = onBack, onRefresh = { refresh++ })
        WorkScopeBar(profile.role)
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(mode.selectedId?.let { mode.nameById[it] } ?: "البرامج المحددة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            val day = data
            if (ids.isEmpty()) item { EmptyState("لا يوجد برنامج محدد") }
            else if (day == null && !error) item { LoadingSkeleton(rows = 3) }
            if (error) item { StateNotice("تعذر تحديث بيانات البرامج", kind = "warning") }
            if (day != null) {
                if (!followUpOnly) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { MetricCard("المشاركون", day.students.toString()) }
                            Box(Modifier.weight(1f)) { MetricCard("جلسات اليوم", day.sessions.toString()) }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.weight(1f)) { MetricCard("حضور مسجّل", day.present.toString()) }
                            Box(Modifier.weight(1f)) { MetricCard("غياب مسجّل", day.absent.toString()) }
                        }
                    }
                    item { MetricCard("مقيّمون تربويًا اليوم", day.assessed?.toString() ?: "—") }
                    item { Button(onClick = { navigate("session_today") }, modifier = Modifier.fillMaxWidth()) { Text("جلسات اليوم") } }
                    item { HomeShortcuts(showPlans = false, navigate = navigate, showAwards = profile.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN, role = profile.role) }
                }
                item { Text("متابعة الغياب المسجّل", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                if (day.followUp.isEmpty()) item { EmptyState("لا توجد حالات غياب مسجّل اليوم") }
                items(day.followUp, key = { it.first }) { row ->
                    ElevatedCard(onClick = { navigate("student:${row.first}") }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text(row.second, fontWeight = FontWeight.SemiBold)
                            Text("غياب مسجّل اليوم", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
