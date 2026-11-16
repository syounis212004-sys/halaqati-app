package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.plans.OrgInsightRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * RC27.32 home card (admin/supervisor): competition seasons between halaqas (or between categories).
 * A season has a name, a level (halaqas or categories) and a duration; standings are live from the same data as the
 * comparison card, and closing a season saves the final ranking. Programs never compete with halaqas.
 * Needs the `competition_seasons` table; when it does not exist yet the card stays hidden.
 */
@Composable
fun SeasonsCard(modifier: Modifier = Modifier, canManage: Boolean) {
    val scope = rememberCoroutineScope()
    var seasons by remember { mutableStateOf<List<JSONObject>?>(null) }
    var live by remember { mutableStateOf<List<Pair<String, OrgInsightRules.HalaqaStats>>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }
    var showNew by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var epoch by remember { mutableIntStateOf(0) }

    LaunchedEffect(epoch) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                if (!AppGraph.repo.online()) return@withContext null
                val arr = AppGraph.repo.table("competition_seasons", "select=*&order=start_date.desc&limit=30", forceRefresh = true)
                val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
                val active = list.firstOrNull { it.optString("status") == "active" }
                val standings = if (active != null && canManage) { // live standings need full read access: managers only
                    val from = LocalDate.parse(active.optString("start_date").take(10))
                    val end = LocalDate.parse(active.optString("end_date").take(10))
                    val to = if (end.isBefore(LocalDate.now())) end else LocalDate.now()
                    compareGroups(from, to, active.optString("level") == "category", fresh = true)
                        ?.let { (stats, names) -> OrgInsightRules.rank(stats).map { (names[it.halaqaId].orEmpty()) to it } }
                        .orEmpty()
                } else emptyList()
                list to standings
            }
        }.onSuccess { r -> if (r != null) { seasons = r.first; live = r.second } }
    }

    val list = seasons ?: return // table missing, offline or no access: stay hidden
    val active = list.firstOrNull { it.optString("status") == "active" }
    val closed = list.filter { it.optString("status") == "closed" }.take(5)
    val today = LocalDate.now()

    InsightCardShell(
        kind = "info",
        icon = Icons.Default.EmojiEvents,
        title = "المواسم والمسابقات",
        summary = if (active != null) active.optString("name") + " · " + (ChronoUnit.DAYS.between(today, LocalDate.parse(active.optString("end_date").take(10))).coerceAtLeast(0)) + " يومًا متبقية"
        else if (canManage) "لا موسم نشط — ابدأ موسمًا جديدًا" else "لا موسم نشط",
        expanded = expanded,
        onClick = { expanded = !expanded },
        modifier = modifier,
    ) {
        if (expanded) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (active != null) {
                Text(
                    (if (active.optString("level") == "category") "مسابقة الفئات" else "مسابقة الحلقات") +
                        " · من ${active.optString("start_date").take(10)} إلى ${active.optString("end_date").take(10)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (canManage && live.isEmpty()) Text("لا بيانات بعد في هذا الموسم", style = MaterialTheme.typography.bodySmall)
                live.take(5).forEachIndexed { i, (name, st) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("${i + 1}", fontWeight = FontWeight.Bold, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                        Text(name.ifBlank { "—" }, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1)
                        Text(
                            "حضور ${st.attendancePercent?.let { "$it٪" } ?: "—"} · جودة ${st.qualityPercent?.let { "$it٪" } ?: "—"}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (canManage) {
                    TextButton(onClick = { confirmClose = true }, enabled = !busy) { Text("إنهاء الموسم وحفظ النتائج") }
                }
            } else if (canManage) {
                Button(onClick = { error = null; showNew = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("موسم جديد") }
            }
            if (closed.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f))
                Text("مواسم سابقة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                val context = androidx.compose.ui.platform.LocalContext.current
                closed.forEachIndexed { index, c ->
                    val winners = c.optJSONArray("winners") ?: JSONArray()
                    val first = winners.optJSONObject(0)?.optString("name").orEmpty()
                    Text(
                        c.optString("name") + (if (first.isNotBlank()) " — الأول: $first" else "") +
                            (winners.optJSONObject(1)?.optString("name")?.takeIf { it.isNotBlank() }?.let { " · الثاني: $it" } ?: "") +
                            (winners.optJSONObject(2)?.optString("name")?.takeIf { it.isNotBlank() }?.let { " · الثالث: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (index == 0 && first.isNotBlank()) {
                        TextButton(onClick = { shareSeasonResult(context, c) }) { Text("مشاركة التهنئة") }
                    }
                }
            }
        }
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        var level by remember { mutableStateOf("halaqa") }
        var days by remember { mutableIntStateOf(30) }
        AlertDialog(
            onDismissRequest = { if (!busy) showNew = false },
            title = { Text("موسم جديد") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("اسم الموسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("المستوى", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProFilterChip(level == "halaqa", { level = "halaqa" }, "الحلقات ككل", showCheckIcon = false)
                        ProFilterChip(level == "category", { level = "category" }, "حسب الفئات", showCheckIcon = false)
                    }
                    Text("المدة", style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OrgInsightRules.SEASON_DURATIONS.forEach { (d, label) ->
                            ProFilterChip(days == d, { days = d }, label, showCheckIcon = false)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && name.trim().length >= 2,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            try {
                                withContext(Dispatchers.IO) {
                                    AppGraph.repo.writeConfirmed(
                                        "competition_seasons",
                                        JSONObject()
                                            .put("name", name.trim())
                                            .put("level", level)
                                            .put("start_date", LocalDate.now().toString())
                                            .put("end_date", LocalDate.now().plusDays(days.toLong()).toString())
                                            .put("status", "active"),
                                    )
                                }
                                showNew = false; epoch++
                            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                                error = e.message ?: "تعذر إنشاء الموسم"; showNew = false
                            } finally { busy = false }
                        }
                    },
                ) { Text("ابدأ الموسم") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }, enabled = !busy) { Text("إلغاء") } },
        )
    }

    if (confirmClose && active != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmClose = false },
            title = { Text("إنهاء الموسم") },
            text = { Text("سيُحفظ الترتيب الحالي كنتيجة نهائية لموسم «${active.optString("name")}» ويُغلق الموسم.") },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            try {
                                withContext(Dispatchers.IO) {
                                    val winners = JSONArray()
                                    live.forEachIndexed { i, (name, st) ->
                                        winners.put(
                                            JSONObject().put("rank", i + 1).put("name", name).put("group_id", st.halaqaId)
                                                .put("attendance", st.attendancePercent ?: JSONObject.NULL)
                                                .put("quality", st.qualityPercent ?: JSONObject.NULL)
                                                .put("pages_per_student", st.pagesPerStudent),
                                        )
                                    }
                                    AppGraph.repo.patchConfirmed(
                                        "competition_seasons",
                                        "id=eq.${active.optString("id")}",
                                        JSONObject().put("status", "closed").put("closed_at", java.time.Instant.now().toString()).put("winners", winners),
                                    )
                                }
                                confirmClose = false; epoch++
                            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                                error = e.message ?: "تعذر إنهاء الموسم"; confirmClose = false
                            } finally { busy = false }
                        }
                    },
                ) { Text("إنهاء وحفظ") }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }, enabled = !busy) { Text("إلغاء") } },
        )
    }
}

/** Congratulation message for the newest closed season (WhatsApp or any app). */
private fun shareSeasonResult(context: android.content.Context, season: JSONObject) {
    val winners = season.optJSONArray("winners") ?: JSONArray()
    val medals = listOf("🥇", "🥈", "🥉")
    val text = buildString {
        append("🏆 *نتائج مسابقة ${season.optString("name")}*\n")
        append(if (season.optString("level") == "category") "مسابقة الفئات" else "مسابقة الحلقات").append('\n')
        append("من ${season.optString("start_date").take(10)} إلى ${season.optString("end_date").take(10)}\n\n")
        for (i in 0 until minOf(3, winners.length())) {
            val w = winners.optJSONObject(i) ?: continue
            append(medals[i]).append(' ').append(w.optString("name"))
            if (!w.isNull("attendance")) append(" — حضور ${w.optInt("attendance")}٪")
            if (!w.isNull("quality")) append(" · جودة ${w.optInt("quality")}٪")
            append('\n')
        }
        append("\nمبارك للفائزين، وبارك الله في جهود الجميع.\n— حلقاتي")
    }
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(android.content.Intent.createChooser(send, "مشاركة التهنئة")) }
}
