package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.PlanRebasePreview
import com.mohammedsamir.halaqati.plans.PlanRangeRebaseRules
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RC26: preview-then-apply fix for monthly hifz plans whose carried-over Quran range no longer matches
 * where the student really is. Nothing is written until the person taps "تطبيق".
 */
@Composable
fun PlanRangeRebaseDialog(halaqaId: String?, onDismiss: () -> Unit, onApplied: (Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var previews by remember { mutableStateOf<List<PlanRebasePreview>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var applying by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(halaqaId) {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) { AppGraph.repo.planRangeRebasePreviews(halaqaId) }
            previews = result
            // Clear-cut cases (student already beyond the old end) are pre-selected; others are opt-in.
            selected = result.filter { it.canApply && it.status == PlanRangeRebaseRules.Status.STALE_END }.map { it.planId }.toSet()
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر فحص الخطط"
        } finally {
            loading = false
        }
    }

    HalaqatiAlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = { Text("إعادة ضبط نطاق الشهر", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "تظهر هنا الخطط التي نطاقها القديم لا يطابق موضع الطالب الفعلي. يُعاد حساب النطاق من آخر حفظ قبل الشهر مع إبقاء هدف الصفحات. لا يتغير شيء قبل «تطبيق».",
                    style = MaterialTheme.typography.bodySmall,
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                when {
                    loading -> com.mohammedsamir.halaqati.ui.components.HalaqatiLoader()
                    previews.isEmpty() && message == null -> Text("كل الخطط سليمة، لا حاجة لإعادة الضبط.")
                    else -> LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(previews, key = { it.planId }) { p ->
                            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Checkbox(
                                        checked = p.planId in selected,
                                        enabled = p.canApply && !applying,
                                        onCheckedChange = { on -> selected = if (on) selected + p.planId else selected - p.planId },
                                    )
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(p.studentName, fontWeight = FontWeight.Bold)
                                        Text(
                                            when (p.status) {
                                                PlanRangeRebaseRules.Status.STALE_END -> "تجاوز نهاية نطاقه القديم"
                                                PlanRangeRebaseRules.Status.ROUTE_COMPLETE -> "أتمّ مسار الحفظ"
                                                else -> "بداية النطاق لا تطابق آخر حفظ"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"),
                                        )
                                        Text("آخر حفظ: ${p.latestLabel}", style = MaterialTheme.typography.bodySmall)
                                        Text("الحالي: ${p.oldLabel}", style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            when {
                                                p.canApply -> "الجديد: ${p.newLabel}"
                                                p.status == PlanRangeRebaseRules.Status.ROUTE_COMPLETE ->
                                                    "وصل نهاية مسار الحفظ فلا نطاق جديد للحفظ. أنشئ له خطة مراجعة أو تثبيت أو غيّر مساره."
                                                else -> "لا يمكن الحساب: لا يوجد حفظ سابق"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val chosen = previews.filter { it.planId in selected && it.canApply }
            Button(
                onClick = {
                    applying = true
                    scope.launch {
                        try {
                            val count = withContext(Dispatchers.IO) { AppGraph.repo.applyPlanRangeRebase(chosen) }
                            onApplied(count)
                            onDismiss()
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            message = e.message ?: "تعذر التطبيق"
                            applying = false
                        }
                    }
                },
                enabled = chosen.isNotEmpty() && !applying && !loading,
                modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget),
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (applying) "جارٍ التطبيق…" else "تطبيق (${chosen.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !applying) { Text("إلغاء") } },
    )
}
