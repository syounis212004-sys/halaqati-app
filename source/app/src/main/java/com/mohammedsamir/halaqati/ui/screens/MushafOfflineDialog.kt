package com.mohammedsamir.halaqati.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.quran.MushafOfflinePlan
import com.mohammedsamir.halaqati.quran.MushafStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** True when the active network is not metered (Wi-Fi, Ethernet): a ~200 MB download should not eat mobile data by surprise. */
internal fun isUnmeteredNetwork(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
}

internal fun formatMegabytes(bytes: Long): String = digits(String.format(java.util.Locale.US, "%.0f", bytes / 1_000_000.0)) + " م.ب"

/**
 * "Use the mushaf without internet": pick juz (or all), see the size, download on Wi-Fi, or delete what was downloaded.
 * What was finished stays when the download is stopped, so it can be continued later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MushafOfflineDialog(currentPage: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { MushafStore.get(context) }
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf(store.packStatus()) }
    var selected by remember { mutableStateOf(setOf<Int>()) }
    var wifiOnly by remember { mutableStateOf(true) }
    var job by remember { mutableStateOf<Job?>(null) }
    var done by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    val running = job?.isActive == true

    LaunchedEffect(Unit) { status = store.packStatus() }

    val pages = MushafOfflinePlan.pagesFor(selected)
    val missing = pages.filterNot { store.isPageDownloaded(it) }
    val onWifi = isUnmeteredNetwork(context)
    val blocked = wifiOnly && !onWifi

    fun start() {
        message = ""
        val todo = missing
        job = scope.launch {
            val out = store.downloadPages(todo) { d, t -> done = d; total = t }
            status = store.packStatus()
            message = if (out.failed.isEmpty()) "اكتمل التنزيل. صفحات المصحف المحدّدة تعمل الآن بدون إنترنت."
            else "تعذّر تنزيل ${digits(out.failed.size)} صفحة (الإنترنت أو الخادم). ما اكتمل محفوظ، ويمكنك المتابعة لاحقًا."
        }
    }

    AlertDialog(
        onDismissRequest = { if (!running) onDismiss() },
        title = { Text("المصحف بدون إنترنت") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "المحمّل الآن: ${digits(status.pages)} من ٦٠٤ صفحة (${formatMegabytes(status.bytes)}). ما تحمّله يُفتح بلا إنترنت.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("اختر الأجزاء:", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val curJuz = (1..30).firstOrNull { currentPage in MushafOfflinePlan.juzPages(it) } ?: 1
                    FilterChip(selected = selected == setOf(curJuz), enabled = !running, onClick = { selected = setOf(curJuz) }, label = { Text("الجزء الحالي") })
                    FilterChip(selected = selected.size == 30, enabled = !running, onClick = { selected = (1..30).toSet() }, label = { Text("المصحف كاملًا") })
                    FilterChip(selected = selected.isEmpty(), enabled = !running, onClick = { selected = emptySet() }, label = { Text("مسح الاختيار") })
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (j in 1..30) FilterChip(
                        selected = j in selected, enabled = !running,
                        onClick = { selected = if (j in selected) selected - j else selected + j }, label = { Text(digits(j)) },
                    )
                }
                if (pages.isNotEmpty()) Text(
                    if (missing.isEmpty()) "كل صفحات اختيارك محمّلة."
                    else "سيُنزَّل ${digits(missing.size)} صفحة، نحو ${formatMegabytes(MushafOfflinePlan.estimateBytes(missing.size))}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("التنزيل عبر Wi-Fi فقط", Modifier.weight(1f))
                    Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it }, enabled = !running)
                }
                if (blocked && missing.isNotEmpty()) Text("أنت غير متصل بشبكة Wi-Fi. اتصل بها أو أوقف خيار Wi-Fi فقط.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                if (running || total > 0 && message.isEmpty()) {
                    LinearProgressIndicator(progress = { if (total == 0) 0f else done / total.toFloat() }, modifier = Modifier.fillMaxWidth())
                    Text("${digits(done)} / ${digits(total)} صفحة", style = MaterialTheme.typography.bodySmall)
                }
                if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium)
                if (status.pages > 0 && !running) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.padding(top = 4.dp)) {
                    Text("حذف المصحف المحمّل", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            if (running) TextButton(onClick = { job?.cancel(); status = store.packStatus() }) { Text("إيقاف") }
            else TextButton(onClick = { start() }, enabled = missing.isNotEmpty() && !blocked) { Text("تنزيل") }
        },
        dismissButton = { if (!running) TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("حذف المصحف المحمّل؟") },
        text = { Text("ستُحذف الصفحات والخطوط المحمّلة (${formatMegabytes(status.bytes)}) وتحتاج الإنترنت لفتحها من جديد. علامات التسميع لا تتأثر.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { store.deletePack(); status = store.packStatus(); selected = emptySet(); total = 0; message = "حُذف المصحف المحمّل." } }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } },
    )
}
