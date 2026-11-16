package com.mohammedsamir.halaqati.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class QuickAction(val key: String, val title: String, val hint: String, val icon: ImageVector)

/**
 * RC42: the "＋" button opens this sheet over whatever page is showing (no navigation, so the current page never flickers).
 * Only the chosen action navigates, and the destination page opens its own add dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(showQuickMemorization: Boolean, onDismiss: () -> Unit, onPick: (String) -> Unit, supervisor: Boolean = false) {
    val haptic = LocalHapticFeedback.current
    val actions = if (supervisor) listOf(
        QuickAction("visit", "تسجيل زيارة", "زيارة محفّظ أو حلقة، وتُحفظ بدون إنترنت", Icons.Default.PlayArrow),
        QuickAction("alert", "تنبيه محفّظ", "أرسل تنبيهًا لمحفّظ من لوحة المتابعة", Icons.Default.Campaign),
        QuickAction("announcement", "إعلان جديد", "ينشر لحلقاتك", Icons.Default.Campaign),
    ) else buildList {
        add(QuickAction("session", "جلسة جديدة", "اختر الحلقة والتاريخ والنوع", Icons.Default.CalendarMonth))
        add(QuickAction("today", "فتح جلسة اليوم", "ابدأ رصد الحضور والتسميع الآن", Icons.Default.PlayArrow))
        add(QuickAction("student", "إضافة طالب", "بياناته وصورته وحلقته", Icons.Default.PersonAdd))
        add(QuickAction("announcement", "إعلان جديد", "ينشر لحلقتك أو لكل الحلقات", Icons.Default.Campaign))
        if (showQuickMemorization) add(QuickAction("quick", "التحفيظ السريع", "تسميع مباشر دون فتح جلسة", Icons.Default.EditNote))
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("إضافة سريعة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            actions.forEachIndexed { index, action ->
                var shown by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { delay(index * 45L); shown = true }
                val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(220), label = "quick-alpha")
                val lift by animateFloatAsState(if (shown) 0f else 14f, tween(260), label = "quick-lift")
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPick(action.key)
                    },
                    modifier = Modifier.fillMaxWidth().graphicsLayer { this.alpha = alpha; translationY = lift },
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(44.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(action.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(24.dp))
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(action.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(action.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Box(Modifier.size(12.dp))
        }
    }
}
