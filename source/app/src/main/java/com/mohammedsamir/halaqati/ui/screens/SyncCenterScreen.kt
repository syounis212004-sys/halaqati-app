@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import com.mohammedsamir.halaqati.ui.presentation.SyncPresentationRules
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.OfflineQueue
import com.mohammedsamir.halaqati.model.SyncState
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.StateNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SyncCenterScreen(sync: SyncState, onSync: () -> Unit) {
    var discardId by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<OfflineQueue.SnapshotItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var consistencyIssues by remember { mutableStateOf<List<String>>(emptyList()) }
    val pendingRows = rows.count { !it.blocked }
    val blockedRows = rows.count { it.blocked }

    suspend fun reload() {
        loading = true
        rows = try {
            withContext(Dispatchers.IO) { AppGraph.repo.queueSnapshot() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر قراءة قائمة المزامنة"
            emptyList()
        }
        consistencyIssues = try {
            withContext(Dispatchers.IO) { AppGraph.repo.tathbitConsistencyIssues() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
        loading = false
    }

    LaunchedEffect(sync.pending, sync.blocked) { reload() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = "مركز المزامنة",
            onRefresh = { scope.launch { reload() } },
            pending = pendingRows,
        )
        if (consistencyIssues.isNotEmpty()) {
            StateNotice(
                "فحص مسار التثبيت: ${consistencyIssues.size} حالة تحتاج تصحيح — " + consistencyIssues.take(3).joinToString("؛ "),
                kind = "error",
            )
        }
                if (loading && rows.isNotEmpty()) {
            StateNotice(
                "جارٍ تحديث حالة المزامنة…",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        message?.let {
            StateNotice(it, "error", Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SyncMetric("الاتصال", if (sync.online) "متصل" else "غير متصل", Modifier.weight(1f))
            SyncMetric("معلّق", pendingRows.toString(), Modifier.weight(1f))
            SyncMetric("يحتاج مراجعة", blockedRows.toString(), Modifier.weight(1f))
        }

        ElevatedCard(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (sync.online) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (sync.online) "الاتصال بالخادم متاح" else "أنت تعمل دون اتصال",
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            sync.lastSync?.let { "آخر مزامنة: ${prettyInstant(it)}" }
                                ?: "لم تُسجل مزامنة ناجحة في هذه الجلسة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = onSync,
                        enabled = sync.online && !sync.syncing,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        if (sync.syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = null)
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (sync.syncing) "جارٍ…" else "مزامنة")
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = !sync.online) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        com.mohammedsamir.halaqati.ui.components.HalaqatiMotion(com.mohammedsamir.halaqati.ui.components.HalaqatiAnimation.Offline, Modifier.size(84.dp))
                    }
                }
                androidx.compose.animation.AnimatedVisibility(visible = sync.syncing) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        com.mohammedsamir.halaqati.ui.components.HalaqatiMotion(com.mohammedsamir.halaqati.ui.components.HalaqatiAnimation.Sync, Modifier.size(72.dp))
                    }
                }
                if (!sync.online && pendingRows > 0) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            "تم حفظ ${pendingRows} تغييرات محليًا على هذا الجهاز. لن تضيع عند إغلاق الشاشة، وستُرفع تلقائيًا بعد عودة الاتصال.",
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (blockedRows > 0) {
                    Text(
                        "هذه العمليات تحتاج مراجعة. افتح التفاصيل، وصحح السجل أو أعد المحاولة بعد معالجة السبب.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppGraph.repo.retryAllBlockedQueueItems()
                                }
                                reload()
                            }
                        },
                        enabled = sync.online && !sync.syncing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("إعادة محاولة كل العمليات المحجوبة")
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (loading && rows.isEmpty()) {
            LoadingSkeleton(rows = 3, modifier = Modifier.padding(horizontal = 12.dp))
        }
        if (!loading && rows.isEmpty()) {
            EmptyState("لا توجد عمليات معلقة — البيانات متزامنة")
        } else {
            LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.id }) { item ->
                    var showTechnicalDetails by remember(item.id) { mutableStateOf(false) }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (item.blocked) Icons.Default.ErrorOutline else Icons.Default.CloudUpload,
                                    contentDescription = null,
                                    tint = if (item.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        SyncPresentationRules.title(item.method, item.path),
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "المحاولات: ${item.attempts} · ${prettyMillis(item.created)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Surface(
                                    color = if (item.blocked) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = if (item.blocked) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    shape = MaterialTheme.shapes.extraLarge,
                                ) {
                                    Row(
                                        Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(
                                            if (item.blocked) Icons.Default.WarningAmber else Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(when { item.conflict -> "تعارض"; item.blocked -> "مراجعة"; else -> "بانتظار الرفع" }, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            TextButton(onClick = { showTechnicalDetails = !showTechnicalDetails }) {
                                Text(if (showTechnicalDetails) "إخفاء التفاصيل التقنية" else "التفاصيل التقنية")
                                Icon(if (showTechnicalDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                            }
                            if (showTechnicalDetails) SelectionContainer {
                                Text(
                                    "${item.method} ${item.path.substringBefore('?')}\n${item.lastError.orEmpty()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (item.blocked && !item.lastError.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text(
                                        simplifyError(item.lastError),
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                            if (!item.blocked && item.attempts > 0 && !item.lastError.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .7f),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text(
                                        "آخر محاولة: ${simplifyTransientError(item.lastError)} · ستتم إعادة المحاولة تلقائيًا",
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    )
                                }
                            }
                            if (item.conflict) {
                                Text(
                                    "تم العثور على تعديل أحدث في الخادم. اختر صراحة أي نسخة تريد اعتمادها؛ لن يحذف RC7 أي نسخة تلقائيًا.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { AppGraph.repo.resolveConflictUseLocal(item.id) }
                                            reload()
                                        }
                                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("اعتماد تعديلي") }
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { AppGraph.repo.resolveConflictUseServer(item.id) }
                                            reload()
                                        }
                                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("نسخة الخادم") }
                                }
                            } else if (item.blocked) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                AppGraph.repo.retryBlockedQueueItem(item.id)
                                                AppGraph.repo.scheduleSync()
                                            }
                                            reload()
                                        }
                                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Icon(Icons.Default.Refresh, contentDescription = null)
                                        Text(" إعادة المحاولة")
                                    }
                                    TextButton(onClick = { discardId = item.id }, modifier = Modifier.heightIn(min = 48.dp)) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                        Text(" حذف العملية")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    discardId?.let { id ->
        HalaqatiAlertDialog(
            onDismissRequest = { discardId = null },
            title = { Text("حذف العملية المعلّقة؟") },
            text = { Text("سيُحذف هذا التعديل من قائمة الرفع، وقد لا يصل إلى الأجهزة الأخرى. راجع التفاصيل قبل المتابعة.") },
            confirmButton = {
                TextButton(onClick = {
                    discardId = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { AppGraph.repo.discardQueueItem(id) }
                            reload()
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { message = "تعذر حذف العملية. حاول مرة أخرى." }
                    }
                }) { Text("حذف العملية", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { discardId = null }) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun SyncMetric(title: String, value: String, modifier: Modifier = Modifier) {
    ElevatedCard(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

private fun simplifyError(error: String): String {
    val clean = error.replace("\\n", " ").replace(Regex("\\s+"), " ").trim()
    return when {
        clean.contains("row-level security", ignoreCase = true) || clean.contains("RLS", ignoreCase = true) ->
            "رفضت صلاحيات قاعدة البيانات هذه العملية. أعد فتح الحساب أو تحقق من صلاحية الدور وربط الحلقة/الطالب ثم أعد المحاولة."
        clean.contains("violates check constraint", ignoreCase = true) ->
            "إحدى القيم لا تطابق قواعد البيانات المسموح بها. افتح السجل وصحح القيمة قبل إعادة المحاولة."
        clean.contains("duplicate", ignoreCase = true) ->
            "يوجد سجل مطابق بالفعل في الخادم. حدّث البيانات ثم أعد المحاولة إذا لزم."
        else -> "تعذر إتمام العملية. راجع السجل أو افتح التفاصيل لمعرفة السبب."
    }
}

private fun simplifyTransientError(error: String): String {
    val clean = error.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
    return when {
        clean.contains("timeout", ignoreCase = true) || clean.contains("timed out", ignoreCase = true) -> "انتهت مهلة الاتصال"
        clean.contains("resolve host", ignoreCase = true) || clean.contains("hostname", ignoreCase = true) -> "تعذر الوصول إلى الخادم"
        clean.contains("network", ignoreCase = true) || clean.contains("connection", ignoreCase = true) -> "اتصال الشبكة غير مستقر"
        else -> "تعذر الرفع مؤقتًا"
    }
}

private fun prettyMillis(value: Long): String = runCatching { com.mohammedsamir.halaqati.ui.components.GazaTime.fromMillis(value) }.getOrDefault("—")

private fun prettyInstant(value: String): String = com.mohammedsamir.halaqati.ui.components.GazaTime.dateTime(value)
