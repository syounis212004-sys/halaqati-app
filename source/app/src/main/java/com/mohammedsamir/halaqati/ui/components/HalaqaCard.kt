package com.mohammedsamir.halaqati.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.ui.theme.Entity
import com.mohammedsamir.halaqati.ui.theme.EntityColors

/** What one halaqa / program card shows. Plain values only, so the card can be rendered in a preview or a test. */
data class HalaqaCardData(
    val name: String,
    val typeLabel: String,
    val statusLabel: String,
    val statusActive: Boolean,
    val place: String,
    val schedule: String,
    /** Specialisation · level · assessment of a program; empty for a regular halaqa. */
    val programMeta: String,
    val studentCount: Int,
    val categoryCount: Int,
    val supervisorCount: Int,
    val supervisors: String,
    val teachers: String,
    val gradient: GradientPair,
)

/**
 * One halaqa/program card with a fixed structure, whatever its colour or type:
 *  1. header (pattern + gradient): logo, name, menu; below them the type and status chips
 *  2. info block: place, time, program details (one line each, never overlapping the header)
 *  3. figures row: students, categories, supervisors, and the expand arrow
 *  4. when expanded: people, [details], then [actions]
 * The caller supplies the logo, the menu, the program-end notice, extra detail lines and the action buttons as slots.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HalaqaManagementCard(
    data: HalaqaCardData,
    expanded: Boolean,
    onToggle: () -> Unit,
    logo: @Composable () -> Unit,
    menu: @Composable () -> Unit,
    notice: @Composable () -> Unit,
    details: @Composable ColumnScope.() -> Unit,
    actions: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(modifier.fillMaxWidth().animateContentSize(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.clickable(onClick = onToggle)) {
            // 1. header
            Column(
                Modifier.fillMaxWidth().background(data.gradient.brush()).nourPattern(PatternLevel.Card)
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    logo()
                    Text(
                        data.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    menu()
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    // Dark translucent chips keep white text >= 4.5:1 even on the light end of the blue/orange/teal gradients.
                    HeaderChip(data.typeLabel, Color.Black.copy(alpha = .20f))
                    HeaderChip(data.statusLabel, Color.Black.copy(alpha = if (data.statusActive) .20f else .40f))
                }
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 2. info block
                val hasInfo = data.place.isNotBlank() || data.schedule.isNotBlank() || data.programMeta.isNotBlank()
                if (hasInfo) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (data.place.isNotBlank()) InfoLine(Icons.Default.Place, data.place)
                        if (data.schedule.isNotBlank()) InfoLine(Icons.Default.Schedule, data.schedule)
                        if (data.programMeta.isNotBlank()) InfoLine(Icons.Default.School, data.programMeta, maxLines = 2)
                    }
                } else {
                    Text("المكان والموعد غير محددين", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                // 3. figures
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CountItem(Icons.Default.Groups, Entity.STUDENT, "${data.studentCount} طالب")
                    CountItem(Icons.Default.Category, Entity.CATEGORY, "${data.categoryCount} فئات")
                    CountItem(Icons.Default.SupervisorAccount, Entity.SUPERVISOR, "${data.supervisorCount} مشرف")
                    Box(Modifier.weight(1f))
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "طيّ التفاصيل" else "عرض التفاصيل",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                notice()
                // 4. expanded
                if (expanded) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    PersonLine(Icons.Default.SupervisorAccount, Entity.SUPERVISOR, "المشرفون", data.supervisors.ifBlank { "غير معيّنين" })
                    PersonLine(Icons.Default.Person, Entity.TEACHER, "المحفّظون", data.teachers.ifBlank { "غير معيّنين" })
                    details()
                    actions()
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(text: String, container: Color) {
    Surface(shape = RoundedCornerShape(50), color = container, contentColor = Color.White) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String, maxLines: Int = 1) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PersonLine(icon: ImageVector, entity: Entity, label: String, names: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = EntityColors.of(entity).accent)
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(names, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun CountItem(icon: ImageVector, entity: Entity, text: String) {
    val tone = EntityColors.of(entity)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, null, Modifier.size(18.dp), tint = tone.accent)
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
