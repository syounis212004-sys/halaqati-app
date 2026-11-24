package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.plans.PhaseDaysRules
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.ResponsiveChipGrid
import java.time.LocalDate

private val DAY_LABELS = listOf(0 to "الأحد", 1 to "الاثنين", 2 to "الثلاثاء", 3 to "الأربعاء", 4 to "الخميس", 5 to "الجمعة", 6 to "السبت")

/**
 * One way to choose phase days everywhere (phase editor, bulk sheet, one student in the bulk sheet): quick sets first, the seven days
 * for exact control, and a plain sentence about what the days mean for the period still ahead (days left, pages per day), so that
 * "Friday only" over a one-week Sard is seen before it is saved, not after.
 */
@Composable
internal fun PhaseDaysPicker(
    days: Set<Int>,
    onDays: (Set<Int>) -> Unit,
    start: String?,
    end: String?,
    targetPages: Double?,
    modifier: Modifier = Modifier,
    title: String = "أيام السرد",
) {
    val from = remember(start) { runCatching { LocalDate.parse(start) }.getOrNull() }
    val to = remember(end) { runCatching { LocalDate.parse(end) }.getOrNull() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        val presets = listOf(
            PhaseDaysRules.SUN_TO_FRI to "الأحد – الجمعة",
            PhaseDaysRules.SUN_TO_THU to "أيام الحلقة (الأحد – الخميس)",
            PhaseDaysRules.FRIDAY_ONLY to "الجمعة فقط",
        )
        ResponsiveChipGrid(items = presets, minItemWidth = 150.dp, maxColumns = 3) { (set, label), itemModifier ->
            ProFilterChip(days == set.toSet(), { onDays(set.toSet()) }, label, itemModifier, showCheckIcon = false)
        }
        ResponsiveChipGrid(items = DAY_LABELS, minItemWidth = 104.dp, maxColumns = 3) { (day, label), itemModifier ->
            ProFilterChip(
                selected = day in days,
                onClick = { onDays(if (day in days) days - day else days + day) },
                label = label, modifier = itemModifier, showCheckIcon = false,
            )
        }
        if (6 in days) Text("السبت إجازة ثابتة في الحلقة؛ اختره فقط لمرحلة استثنائية.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (from != null && to != null) {
            val c = PhaseDaysRules.check(LocalDate.now(), from, to, days, targetPages)
            Text(
                c.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (c.level == "ok") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The same phase with only its days replaced (same id and revision, so a stale device gets the normal conflict, never a second phase). */
internal fun com.mohammedsamir.halaqati.model.SmartPlanPhaseRow.withDays(days: List<Int>): com.mohammedsamir.halaqati.model.SmartPlanPhaseDraft =
    com.mohammedsamir.halaqati.model.SmartPlanPhaseDraft(
        id = id, planId = planId, phaseOrder = phaseOrder, phaseType = phaseType, title = title, startDate = startDate, endDate = endDate,
        scheduleWeekdays = days, goalMode = goalMode, goalUnit = goalUnit, targetUnits = targetUnits, targetPages = targetPages,
        distributionMode = distributionMode, startMode = startMode, rangeStartPage = rangeStartPage, rangeEndPage = rangeEndPage,
        rangeStartSurah = rangeStartSurah, rangeStartAyah = rangeStartAyah, rangeEndSurah = rangeEndSurah, rangeEndAyah = rangeEndAyah,
        allowSurplus = allowSurplus, status = status, settings = org.json.JSONObject(settings.toString()), expectedRevision = revision,
    )
