package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.ui.scope.HalaqaOverviewRow
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens

/**
 * RC25: the current halaqa is always visible on the screens that depend on it.
 * It is a context switch (it swaps the data set), not a filter that narrows the current one.
 * Hidden for people who only ever see one halaqa (a teacher), so their screens stay clean.
 */
@Composable
fun HalaqaScopeBar(
    halaqas: List<Pair<String, String>>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
    allowAll: Boolean = true,
    allLabel: String = "كل الحلقات",
) {
    var open by remember { mutableStateOf(false) }
    val name = halaqas.firstOrNull { it.first == selectedId }?.second ?: allLabel
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget).clickable { open = true },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(20.dp))
            Text("الحلقة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(
                name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("تغيير", style = MaterialTheme.typography.labelMedium, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
    }
    if (open) {
        HalaqatiLazySelectSheet(
            title = "اختر الحلقة",
            selectedKey = selectedId.orEmpty(),
            options = halaqas,
            searchable = halaqas.size > 8,
            allowEmpty = allowAll,
            emptyKey = "",
            emptyOptionLabel = allLabel,
            onDismiss = { open = false },
            onSelected = { key ->
                open = false
                onSelect(key.takeIf { it.isNotBlank() })
            },
        )
    }
}

/** Collapsible month header used by sessions, plans and reports so they all look and behave the same. */
@Composable
fun MonthSectionHeader(
    label: String,
    summary: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget).clickable(onClick = onToggle),
        shape = RoundedCornerShape(12.dp),
        color = if (expanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        tonalElevation = if (expanded) 0.dp else 1.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "طيّ" else "فتح")
        }
    }
}

/** Entry page for an admin / supervisor: one card per halaqa, then open the one you need. */
@Composable
fun HalaqaOverviewCard(
    name: String,
    row: HalaqaOverviewRow,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "",
) {
    Surface(
        modifier = modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            EntityHeader(com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA, name, trailing = {
                Surface(shape = RoundedCornerShape(10.dp), color = androidx.compose.ui.graphics.Color.Black.copy(alpha = .22f)) {
                    Text(
                        if (row.hasTodaySession) "جلسة اليوم مفتوحة" else "لا جلسة اليوم",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            })
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("${row.studentCount} طالب", style = MaterialTheme.typography.bodyMedium)
                Text("${row.sessionCount} جلسة", style = MaterialTheme.typography.bodyMedium)
                Text(
                    row.lastSessionDate?.let { "آخر جلسة $it" } ?: "لا جلسات بعد",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One line of an earlier month's plan: what the plan was and how it ended. */
@Composable
fun PlanHistoryLine(title: String, subtitle: String, status: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}
