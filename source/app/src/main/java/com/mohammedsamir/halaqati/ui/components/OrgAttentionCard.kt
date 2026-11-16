package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentLate
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.plans.OrgAttentionRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** RC27 home card: active students without a plan covering today, and (admin/supervisor) without a linked guardian. */
@Composable
fun OrgAttentionCard(
    showGuardians: Boolean,
    modifier: Modifier = Modifier,
    onOpenPlans: () -> Unit,
    onOpenStudents: () -> Unit,
) {
    var noPlan by remember { mutableStateOf<List<String>>(emptyList()) }
    var noGuardian by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(showGuardians) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                val ineligible = AppGraph.repo.smartPlanIneligibleHalaqaIds()
                val students = AppGraph.repo.students(forceRefresh = false).map {
                    OrgAttentionRules.StudentInput(it.id, it.fullName, it.status, it.halaqaId, it.categoryId)
                }
                val plans = AppGraph.repo.plans(forceRefresh = false).map {
                    OrgAttentionRules.PlanInput(it.scopeType, it.studentId, it.categoryId, it.halaqaId, it.status, it.startDate, it.endDate)
                }
                val withoutPlan = OrgAttentionRules.studentsWithoutPlan(
                    students.filter { com.mohammedsamir.halaqati.plans.SmartPlanEligibility.isEligible(it.halaqaId, ineligible) },
                    plans, LocalDate.now().toString(),
                ).map { it.name }
                val withoutGuardian = if (showGuardians && AppGraph.repo.online()) {
                    val arr = AppGraph.repo.table("student_guardians", "select=student_id&limit=5000", forceRefresh = true)
                    val linked = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("student_id") }.toSet()
                    // An empty answer is more likely a failed/blocked read than "nobody has a guardian": stay silent.
                    if (linked.isEmpty()) emptyList()
                    else OrgAttentionRules.studentsWithoutGuardian(students, linked).map { it.name }
                } else emptyList()
                withoutPlan to withoutGuardian
            }
        }.onSuccess { (p, g) -> noPlan = p; noGuardian = g }
    }
    if (noPlan.isEmpty() && noGuardian.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (noPlan.isNotEmpty()) {
            InsightCardShell(
                kind = "info",
                icon = Icons.Default.AssignmentLate,
                title = "طلاب بلا خطة هذا الشهر",
                summary = noPlan.take(3).joinToString("، ") + if (noPlan.size > 3) " … +${noPlan.size - 3}" else "",
                count = noPlan.size,
                onClick = onOpenPlans,
            )
        }
        if (noGuardian.isNotEmpty()) {
            InsightCardShell(
                kind = "info",
                icon = Icons.Default.FamilyRestroom,
                title = "طلاب بلا ولي أمر مرتبط",
                summary = noGuardian.take(3).joinToString("، ") + if (noGuardian.size > 3) " … +${noGuardian.size - 3}" else "",
                count = noGuardian.size,
                onClick = onOpenStudents,
            )
        }
    }
}
