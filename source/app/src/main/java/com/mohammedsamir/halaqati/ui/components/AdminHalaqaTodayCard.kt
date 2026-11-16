package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** RC25 home card for admin/supervisor: which active halaqas have not recorded a session today. */
@Composable
fun AdminHalaqaTodayCard(modifier: Modifier = Modifier, onOpenSessions: () -> Unit) {
    var missing by remember { mutableStateOf<List<String>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                val today = LocalDate.now().toString()
                val sessions = AppGraph.repo.sessions(forceRefresh = false, includeArchived = false)
                val h = AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type&order=name.asc&limit=200", forceRefresh = false)
                val active = (0 until h.length()).mapNotNull { h.optJSONObject(it) }
                    .filter { it.optBoolean("active", true) && com.mohammedsamir.halaqati.plans.SmartPlanEligibility.isHalaqaType(it.optString("halaqa_type").takeIf { t -> t != "null" }) } // RC27.30: halaqas only, programs excluded
                val done = sessions.filter { it.date.startsWith(today) && it.status != "cancelled" }.map { it.halaqaId }.toSet()
                active.size to active.filter { it.optString("id") !in done }.map { it.optString("name") }
            }
        }.onSuccess { (count, names) ->
            total = count
            missing = names
            loaded = true
        }
    }
    if (!loaded || total < 2) return
    InsightCardShell(
        kind = if (missing.isEmpty()) "success" else "warning",
        icon = if (missing.isEmpty()) Icons.Default.EventAvailable else Icons.Default.EventBusy,
        title = if (missing.isEmpty()) "كل الحلقات سجّلت جلسة اليوم" else "حلقات لم تسجّل جلسة اليوم",
        summary = if (missing.isEmpty()) "$total من $total" else missing.take(6).joinToString("، ") + if (missing.size > 6) " …" else "",
        count = if (missing.isEmpty()) null else missing.size,
        onClick = onOpenSessions,
        modifier = modifier,
    )
}
