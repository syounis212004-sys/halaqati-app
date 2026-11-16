package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * RC25 (admin only): lists sessions that were removed from the server together with their recitation,
 * attendance and notes snapshot, and restores one with a single tap.
 */
@Composable
fun DeletedSessionsDialog(halaqaNames: Map<String, String>, onDismiss: () -> Unit, onRestored: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var busyId by remember { mutableStateOf<String?>(null) }

    suspend fun load() {
        loading = true
        try {
            rows = withContext(Dispatchers.IO) { AppGraph.repo.deletedSessionSnapshots() }
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "تعذر قراءة الجلسات المحذوفة. تأكد من الاتصال ومن تشغيل ملف SQL الخاص بالنسخة. (${e.message.orEmpty()})"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(Unit) { load() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الجلسات المحذوفة", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "كل جلسة حُذفت وفيها بيانات تُحفظ هنا. الاستعادة تعيد الجلسة وتسميعها وحضورها وملاحظاتها.",
                    style = MaterialTheme.typography.bodySmall,
                )
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                when {
                    loading -> com.mohammedsamir.halaqati.ui.components.HalaqatiLoader()
                    rows.isEmpty() && message == null -> Text("لا توجد جلسات محذوفة.")
                    else -> LazyColumn(
                        Modifier.heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(rows, key = { it.optString("id") }) { row ->
                            val sess = row.optJSONObject("session_row") ?: JSONObject()
                            val snapshotId = row.optString("id")
                            val restored = !row.isNull("restored_at") && row.optString("restored_at").isNotBlank()
                            val recs = row.optJSONArray("recitation_entries")?.length() ?: 0
                            val att = row.optJSONArray("attendance_records")?.length() ?: 0
                            val notes = row.optJSONArray("student_daily_notes")?.length() ?: 0
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(14.dp),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        (halaqaNames[sess.optString("halaqa_id")] ?: "حلقة") + " · " + sess.optString("session_date"),
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        "تسميع $recs · حضور $att · ملاحظات $notes · حُذفت ${com.mohammedsamir.halaqati.ui.components.GazaTime.dateTime(row.optString("deleted_at"))}",
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    if (restored) {
                                        Text("تمت استعادتها", style = MaterialTheme.typography.labelMedium, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                                    } else {
                                        Button(
                                            onClick = {
                                                busyId = snapshotId
                                                scope.launch {
                                                    try {
                                                        withContext(Dispatchers.IO) { AppGraph.repo.restoreDeletedSession(snapshotId) }
                                                        onRestored()
                                                        load()
                                                    } catch (e: kotlinx.coroutines.CancellationException) {
                                                        throw e
                                                    } catch (e: Exception) {
                                                        message = e.message ?: "تعذرت الاستعادة"
                                                    } finally {
                                                        busyId = null
                                                    }
                                                }
                                            },
                                            enabled = busyId == null,
                                            modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget),
                                            shape = RoundedCornerShape(14.dp),
                                        ) { Text(if (busyId == snapshotId) "جارٍ الاستعادة…" else "استعادة الجلسة") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}
