package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val MONTH_NAMES = listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")

/**
 * RC52: a real month picker. ‹ › step through the approved months (newest first in [months]); the centre button opens a
 * sheet with the twelve months of each year — only months in [months] (the approved ones) can be chosen.
 * [months] and [selected] are "yyyy-MM".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthPicker(months: List<String>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    val index = months.indexOf(selected)
    val label = selected?.let { monthLabel(it) } ?: "اختر الشهر"
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = .45f)),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            // RTL: the right arrow goes back to an older month.
            IconButton(onClick = { if (index in 0 until months.lastIndex) onSelect(months[index + 1]) }, modifier = Modifier.size(48.dp), enabled = index in 0 until months.lastIndex) {
                Icon(Icons.Default.ChevronRight, "الشهر السابق")
            }
            Row(
                Modifier.weight(1f).height(48.dp).clickable { open = true },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ExpandMore, null, Modifier.padding(start = 6.dp).size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = { if (index > 0) onSelect(months[index - 1]) }, modifier = Modifier.size(48.dp), enabled = index > 0) {
                Icon(Icons.Default.ChevronLeft, "الشهر التالي")
            }
        }
    }
    if (open) {
        val sorted = months.sorted()
        val years = if (sorted.isEmpty()) emptyList() else (sorted.last().take(4).toInt() downTo sorted.first().take(4).toInt()).toList()
        ModalBottomSheet(onDismissRequest = { open = false }, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("اختر الشهر", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("الأشهر المعتمدة فقط قابلة للاختيار.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                years.forEach { year ->
                    Text(year.toString(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    (0 until 12 step 4).forEach { start ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            (start until start + 4).forEach { m ->
                                val key = "%04d-%02d".format(java.util.Locale.US, year, m + 1)
                                val enabled = key in months
                                val on = key == selected
                                Box(
                                    Modifier.weight(1f).height(46.dp)
                                        .background(
                                            when { on -> MaterialTheme.colorScheme.primary; enabled -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f); else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .4f) },
                                            RoundedCornerShape(14.dp),
                                        )
                                        .clickable(enabled = enabled) { onSelect(key); open = false },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        MONTH_NAMES[m], style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 1,
                                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                        color = when { on -> MaterialTheme.colorScheme.onPrimary; enabled -> MaterialTheme.colorScheme.onPrimaryContainer; else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun monthLabel(key: String): String {
    val parts = key.take(7).split("-")
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return key
    return "${MONTH_NAMES.getOrNull(m - 1) ?: key} ${parts[0]}"
}
