package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.plans.PlanRangeRebaseRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** RC27.5 home card: only students who finished the whole hifz route (nothing left to continue). Passing a plan range end is NOT a problem. */
@Composable
fun PlansNeedAttentionCard(modifier: Modifier = Modifier, onOpenPlans: () -> Unit) {
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        // RC27.6: silently complete an undersized plan range from its target (once a day).
        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.healUndersizedPlanRanges() } }
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                if (!AppGraph.repo.online()) emptyList()
                else AppGraph.repo.planRangeRebasePreviews()
                    .filter { it.status == PlanRangeRebaseRules.Status.ROUTE_COMPLETE }
                    .map { it.studentName }
            }
        }.onSuccess { names = it }
    }
    if (names.isEmpty()) return
    InsightCardShell(
        kind = "warning",
        icon = Icons.Default.EmojiEvents,
        title = "أتمّوا مسار الحفظ",
        summary = names.take(3).joinToString("، ") + (if (names.size > 3) " … +${names.size - 3}" else "") + " — أنشئ لهم خطة مراجعة أو تثبيت.",
        count = names.size,
        onClick = onOpenPlans,
        modifier = modifier,
    )
}
