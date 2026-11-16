@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mohammedsamir.halaqati.ui.screens

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.mohammedsamir.halaqati.ui.components.HalaqatiGradients
import com.mohammedsamir.halaqati.ui.components.girih
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.documents.DocumentImageEncoder
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.SegmentedTabs
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.components.halaqatiEnter
import com.mohammedsamir.halaqati.ui.theme.cardHairline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.YearMonth

/**
 * RC48: support cards. The admin publishes a need (support for teachers or the system admin, hospitality for students,
 * prizes); a donor pledges one or more periods; money moves outside the app. The admin confirms receipt and then records
 * the delivery with a written note and/or a photo, which the donor sees under "كفالاتي".
 */
private val SUPPORT_KINDS = listOf(
    "teacher" to "دعم المحفّظين", "admin" to "دعم مسؤول النظام", "hospitality" to "ضيافة للطلاب", "award" to "جوائز الطلاب", "other" to "أخرى",
)
private val RECURRENCES = listOf("once" to "مرة واحدة", "weekly" to "أسبوعي", "monthly" to "شهري")

private fun kindLabel(k: String) = SUPPORT_KINDS.firstOrNull { it.first == k }?.second ?: k
private fun recurrenceLabel(r: String) = RECURRENCES.firstOrNull { it.first == r }?.second ?: r
private fun pledgeStatusLabel(s: String) = when (s) {
    "pledged" -> "متعهَّد · بانتظار الاستلام"
    "received" -> "تم استلام الدعم"
    "delivered" -> "وصل إلى مستحقيه"
    else -> s
}

private fun defaultPeriods(recurrence: String): String = when (recurrence) {
    "weekly" -> "الأسبوع الأول\nالأسبوع الثاني\nالأسبوع الثالث\nالأسبوع الرابع"
    "monthly" -> (0..2).joinToString("\n") { awardMonthLabel(YearMonth.now().plusMonths(it.toLong()).toString()) }
    else -> ""
}

private fun JSONArray.objs(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.str(key: String): String = optString(key).takeIf { it != "null" }.orEmpty()

@Composable
fun SupportAdminScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var cards by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var donors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deliverFor by remember { mutableStateOf<JSONObject?>(null) }
    var assignFor by remember { mutableStateOf<JSONObject?>(null) }
    var proofFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var threadFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var defaults by remember { mutableStateOf<JSONObject?>(null) }
    var showDefaults by remember { mutableStateOf(false) }
    var news by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var newsTitle by remember { mutableStateOf("") }
    var newsBody by remember { mutableStateOf("") }

    // RC86: the lists are read once and kept on the device; they are fetched again only on a manual refresh or after an edit.
    fun reload(force: Boolean = false) {
        scope.launch {
            loading = true
            com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    val c = AppGraph.repo.supportCards(force)
                    val d = AppGraph.repo.donors(force)
                    val h = AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type&order=name.asc&limit=200").objs().filter { it.optBoolean("active", true) }
                    defaults = runCatching { AppGraph.repo.supportDefaults(force) }.getOrNull()
                    news = runCatching { AppGraph.repo.donorNews(force) }.getOrDefault(emptyList())
                    suggestions = runCatching { AppGraph.repo.donorSuggestions(force) }.getOrDefault(emptyList())
                    Triple(c, d, h)
                }
            }.onSuccess { (c, d, h) -> cards = c; donors = d; halaqas = h; if (isError) message = null }
                .onFailure { message = it.message ?: "تعذر التحميل"; isError = true }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }

    fun act(okText: String, block: () -> Unit) {
        scope.launch {
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { block() } }
                .onSuccess { message = okText; isError = false; reload(true) }
                .onFailure { message = it.message ?: "تعذر التنفيذ"; isError = true }
        }
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "الدعم والمانحون", onRefresh = { reload(true); onRefresh() }, pending = pending)
        SegmentedTabs(listOf("البطاقات", "المانحون", "أخبار", "المكافآت"), tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        if (tab == 3) AdminRewardsPanel(Modifier.weight(1f)) else LazyColumn(
            state = rememberLazyListState(), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let { item { StateNotice(it, if (isError) "danger" else "success") } }
            if (loading && cards.isEmpty() && donors.isEmpty()) item { LoadingSkeleton(rows = 3) }
            if (tab == 0) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { editing = null; showEditor = true }, modifier = Modifier.weight(1f)) { Text("بطاقة دعم جديدة") }
                        OutlinedButton(onClick = { showDefaults = true }, modifier = Modifier.weight(1f)) { Text("حساب الدعم الافتراضي") }
                    }
                }
                if (cards.isNotEmpty()) item {
                    com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(
                        Triple(cards.size.toString(), "بطاقات دعم", false),
                        Triple((0 until cards.size).count { cards[it].optString("status") == "open" }.toString(), "مفتوحة", false),
                        Triple(donors.size.toString(), "مانحون", false),
                    ))
                }
                if (!loading && cards.isEmpty()) item { EmptyState("لا توجد بطاقات دعم بعد. أنشئ بطاقة (ضيافة، دعم المحفّظين...) ليتكفّل بها المانحون.") }
                items(cards.size) { i ->
                    val c = cards[i]
                    SupportCardAdmin(
                        c, onEdit = { editing = c; showEditor = true },
                        onToggle = { act(if (c.optString("status") == "open") "أُغلقت البطاقة" else "أُعيد فتح البطاقة") { AppGraph.repo.supportSaveCard(JSONObject(c.toString()).put("status", if (c.optString("status") == "open") "closed" else "open").put("periods", c.optJSONArray("periods")?.let { arr -> JSONArray((0 until arr.length()).map { arr.getJSONObject(it).optString("period") }) } ?: JSONArray())) } },
                        onConfirm = { id -> act("تم تأكيد الاستلام وأُرسل شكر للمانح") { AppGraph.repo.supportConfirm(id) } },
                        onDeliver = { deliverFor = it },
                        onCancel = { id -> act("أُلغيت الكفالة") { AppGraph.repo.supportCancel(id) } },
                        onProof = { id, kind -> proofFor = id to kind },
                        onThread = { id, title -> threadFor = id to title },
                    )
                }
            } else if (tab == 1) {
                item { Text("حدّد الحلقات التي يراها كل مانح. من لم تُحدَّد له حلقة يرى كل الحلقات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (!loading && donors.isEmpty()) item { EmptyState("لا يوجد مانحون معتمدون بعد.") }
                items(donors.size) { i ->
                    val d = donors[i]
                    val ids = d.optJSONArray("halaqa_ids")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
                    val names = halaqas.filter { it.optString("id") in ids }.joinToString("، ") { it.optString("name") }
                    DonorRow(d, if (ids.isEmpty()) "كل الحلقات" else names, i) { assignFor = d }
                }
            } else {
                item {
                    Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("خبر جديد للمانحين", style = MaterialTheme.typography.titleMedium)
                            OutlinedTextField(newsTitle, { newsTitle = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                            OutlinedTextField(newsBody, { newsBody = it }, label = { Text("النص") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                            Button(enabled = newsTitle.trim().length >= 2, onClick = {
                                val t = newsTitle.trim(); val b = newsBody.trim(); newsTitle = ""; newsBody = ""
                                act("نُشر الخبر وأُشعر المانحون") { AppGraph.repo.saveNews(JSONObject().put("title", t).put("body", b).put("active", true)) }
                            }) { Text("نشر") }
                        }
                    }
                }
                items(news.size) { i ->
                    val n = news[i]
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, border = cardHairline()) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(n.optString("title"), style = MaterialTheme.typography.titleSmall)
                                n.str("body").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            TextButton(onClick = {
                                act(if (n.optBoolean("active")) "أُخفي الخبر" else "أُظهر الخبر") {
                                    AppGraph.repo.saveNews(JSONObject().put("id", n.optString("id")).put("title", n.optString("title")).put("body", n.str("body")).put("active", !n.optBoolean("active")))
                                }
                            }) { Text(if (n.optBoolean("active")) "إخفاء" else "إظهار") }
                        }
                    }
                }
                item { Text("اقتراحات المانحين", style = MaterialTheme.typography.titleMedium) }
                if (suggestions.isEmpty()) item { EmptyState("لا توجد اقتراحات بعد.") }
                items(suggestions.size) { i ->
                    val sg = suggestions[i]
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, border = cardHairline()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(sg.optString("donor_name"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                            Text(sg.optString("body"), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
    if (showDefaults) DefaultsDialog(
        initial = defaults, onDismiss = { showDefaults = false },
        onSave = { json -> showDefaults = false; act("حُفظ حساب الدعم الافتراضي") { AppGraph.repo.setSupportDefaults(json) } },
    )

    if (showEditor) SupportCardEditor(
        initial = editing, halaqas = halaqas, onDismiss = { showEditor = false },
        onSave = { json -> showEditor = false; act("تم حفظ البطاقة") { AppGraph.repo.supportSaveCard(json) } },
    )
    deliverFor?.let { pl ->
        DeliverDialog(
            pledge = pl, onDismiss = { deliverFor = null },
            onSubmit = { text, image, thanks -> deliverFor = null; act("سُجّل التسليم وأُشعر المانح") { AppGraph.repo.supportDeliver(pl.optString("id"), text, image, thanks) } },
        )
    }
    assignFor?.let { d ->
        val current = d.optJSONArray("halaqa_ids")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty().toSet()
        HalaqaAssignDialog(
            donorName = d.optString("name"), halaqas = halaqas, initial = current, onDismiss = { assignFor = null },
            onSave = { ids -> assignFor = null; act("تم تحديد حلقات المانح") { AppGraph.repo.setDonorHalaqas(d.optString("donor_id"), ids) } },
        )
    }
    proofFor?.let { ProofDialog(it.first, it.second) { proofFor = null } }
    threadFor?.let { t -> ThreadSheet("pledge", t.first, t.second, onDismiss = { threadFor = null }) }
}

@Composable
private fun SupportCardAdmin(
    c: JSONObject, onEdit: () -> Unit, onToggle: () -> Unit, onConfirm: (String) -> Unit,
    onDeliver: (JSONObject) -> Unit, onCancel: (String) -> Unit, onProof: (String, String) -> Unit, onThread: (String, String) -> Unit = { _, _ -> },
) {
    val pledges = c.optJSONArray("pledges")?.objs().orEmpty()
    val periods = c.optJSONArray("periods")?.objs().orEmpty()
    val open = c.optString("status") == "open"
    Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(kindLabel(c.optString("kind")) + " · " + recurrenceLabel(c.optString("recurrence")), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    Text(c.optString("title"), style = MaterialTheme.typography.titleLarge)
                }
                Surface(shape = RoundedCornerShape(10.dp), color = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant) {
                    Text(if (open) "مفتوحة" else "مغلقة", Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            c.str("description").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            val meta = listOfNotNull(
                c.amountOrNull(),
                c.str("amount_text").takeIf { it.isNotBlank() },
                c.optInt("beneficiaries", -1).takeIf { it >= 0 }?.let { "$it طالبًا مستفيدًا" },
                c.str("halaqa_name").takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                periods.forEach { p ->
                    val full = p.optInt("taken") >= c.optInt("slots", 1)
                    AssistChipLike("${p.optString("period")} · ${p.optInt("taken")}/${c.optInt("slots", 1)}", full)
                }
            }
            pledges.forEach { pl ->
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
                    Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(pl.optString("donor_name") + if (pl.optBoolean("anonymous")) " (يظهر مجهولًا)" else "", fontWeight = FontWeight.SemiBold)
                        Text(pl.optString("period") + " · " + pledgeStatusLabel(pl.optString("status")), style = MaterialTheme.typography.bodySmall)
                        if (pl.str("transfer_submitted_at").isNotBlank()) {
                            Text("إثبات التحويل: " + (pl.str("transfer_ref").ifBlank { "صورة مرفقة" }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        } else if (pl.optString("status") == "pledged") {
                            Text("لم يرسل المانح إثبات التحويل بعد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        pl.str("proof_text").takeIf { it.isNotBlank() }?.let { Text("الإثبات: $it", style = MaterialTheme.typography.bodySmall) }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            val st = pl.optString("status")
                            if (st == "pledged") TextButton(onClick = { onConfirm(pl.optString("id")) }) { Text("تأكيد الاستلام") }
                            if (st == "pledged" || st == "received") TextButton(onClick = { onDeliver(pl) }) { Text("إثبات التسليم") }
                            if (pl.optBoolean("has_transfer_image")) TextButton(onClick = { onProof(pl.optString("id"), "transfer") }) { Text("صورة الحوالة") }
                            if (pl.optBoolean("has_image")) TextButton(onClick = { onProof(pl.optString("id"), "delivery") }) { Text("صورة التسليم") }
                            TextButton(onClick = { onThread(pl.optString("id"), pl.optString("donor_name") + " · " + pl.optString("period")) }) { Text("المحادثة") }
                            if (st == "pledged" || st == "received") TextButton(onClick = { onCancel(pl.optString("id")) }) { Text("إلغاء") }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEdit) { Text("تعديل") }
                OutlinedButton(onClick = onToggle) { Text(if (open) "إغلاق" else "إعادة فتح") }
            }
        }
    }
}

@Composable
private fun AssistChipLike(text: String, full: Boolean) {
    Surface(shape = RoundedCornerShape(999.dp), color = if (full) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SupportCardEditor(initial: JSONObject?, halaqas: List<JSONObject>, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var kind by remember { mutableStateOf(initial?.optString("kind")?.ifBlank { null } ?: "hospitality") }
    var title by remember { mutableStateOf(initial?.optString("title").orEmpty()) }
    var description by remember { mutableStateOf(initial?.str("description").orEmpty()) }
    var amount by remember { mutableStateOf(initial?.str("amount_text").orEmpty()) }
    var recurrence by remember { mutableStateOf(initial?.optString("recurrence")?.ifBlank { null } ?: "once") }
    var periodsText by remember {
        mutableStateOf(initial?.optJSONArray("periods")?.objs()?.joinToString("\n") { it.optString("period") }.orEmpty())
    }
    var slots by remember { mutableStateOf((initial?.optInt("slots", 1) ?: 1).toString()) }
    var beneficiaries by remember { mutableStateOf(initial?.optInt("beneficiaries", -1)?.takeIf { it >= 0 }?.toString().orEmpty()) }
    var halaqaId by remember { mutableStateOf(initial?.str("halaqa_id").orEmpty()) }
    var amountNum by remember { mutableStateOf(initial?.optDouble("amount", Double.NaN)?.takeIf { !it.isNaN() }?.let { if (it == Math.rint(it)) it.toLong().toString() else it.toString() }.orEmpty()) }
    var currency by remember { mutableStateOf(initial?.optString("currency")?.takeIf { it.isNotBlank() && it != "null" } ?: "ILS") }
    var useDefault by remember { mutableStateOf(initial?.optBoolean("uses_default_payment", true) ?: true) }
    val pay = remember { PaymentForm(if (initial?.optBoolean("uses_default_payment", true) == false) initial?.optJSONObject("pay") else null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "بطاقة دعم جديدة" else "تعديل البطاقة") },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("النوع", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    SUPPORT_KINDS.forEach { (k, l) -> ProFilterChip(kind == k, { kind = k }, l, showCheckIcon = false) }
                }
                OutlinedTextField(title, { title = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("الوصف (اختياري)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(amount, { amount = it }, label = { Text("وصف العطاء (مثال: بسكويت وعصير)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(amountNum, { amountNum = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) }, label = { Text("المبلغ المطلوب لكل فترة") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    CURRENCIES.forEach { (k, l) -> ProFilterChip(currency == k, { currency = k }, l, showCheckIcon = false) }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("استخدام الحساب الافتراضي", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    Switch(checked = useDefault, onCheckedChange = { useDefault = it })
                }
                if (!useDefault) PaymentFormFields(pay)
                Text("التكرار", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    RECURRENCES.forEach { (r, l) ->
                        ProFilterChip(recurrence == r, { recurrence = r; if (initial == null || periodsText.isBlank()) periodsText = defaultPeriods(r) }, l, showCheckIcon = false)
                    }
                }
                if (recurrence != "once") OutlinedTextField(periodsText, { periodsText = it }, label = { Text("الفترات (كل فترة في سطر)") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(slots, { slots = it.filter(Char::isDigit).take(2) }, label = { Text("كفلاء لكل فترة") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(beneficiaries, { beneficiaries = it.filter(Char::isDigit).take(4) }, label = { Text("عدد المستفيدين") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Text("الحلقة", style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    ProFilterChip(halaqaId.isBlank(), { halaqaId = "" }, "كل الحلقات", showCheckIcon = false)
                    halaqas.forEach { h -> ProFilterChip(halaqaId == h.optString("id"), { halaqaId = h.optString("id") }, h.optString("name"), showCheckIcon = false) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.trim().length >= 2, onClick = {
                val periods = if (recurrence == "once") JSONArray() else JSONArray(periodsText.lines().map { it.trim() }.filter { it.isNotEmpty() })
                onSave(
                    JSONObject().put("amount", amountNum.trim()).put("currency", currency).also { j -> if (!useDefault) pay.putInto(j) }
                        .put("kind", kind).put("title", title.trim()).put("description", description.trim()).put("amount_text", amount.trim())
                        .put("recurrence", recurrence).put("periods", periods).put("slots", slots.toIntOrNull()?.coerceIn(1, 50) ?: 1)
                        .put("beneficiaries", beneficiaries.ifBlank { "" }).put("halaqa_id", halaqaId)
                        .put("status", initial?.optString("status")?.ifBlank { null } ?: "open")
                        .also { j -> initial?.optString("id")?.takeIf { it.isNotBlank() }?.let { j.put("id", it) } },
                )
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun Modifier.verticalScrollCompat(): Modifier = this.then(Modifier.verticalScroll(rememberScrollState()))

@Composable
internal fun DeliverDialog(pledge: JSONObject, onDismiss: () -> Unit, onSubmit: (String, String?, String) -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    var thanks by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) image = DocumentImageEncoder.uriJpeg(context, uri, maxDimension = 1024, quality = 70)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إثبات التسليم") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${pledge.optString("donor_name")} · ${pledge.optString("period")}", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(text, { text = it }, label = { Text("إفادة مكتوبة (مثال: سُلّمت الضيافة للطلاب)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedButton(onClick = { picker.launch("image/*") }) { Text(if (image == null) "إرفاق صورة" else "تغيير الصورة") }
                if (image != null) Text("أُرفقت صورة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                OutlinedTextField(thanks, { thanks = it }, label = { Text("رسالة شكر للمانح (اختياري)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { TextButton(enabled = text.trim().length >= 3 || image != null, onClick = { onSubmit(text.trim(), image, thanks.trim()) }) { Text("تسجيل التسليم") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun HalaqaAssignDialog(donorName: String, halaqas: List<JSONObject>, initial: Set<String>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
    var selected by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حلقات $donorName") },
        text = {
            Column(Modifier.verticalScrollCompat(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("إن لم تحدّد شيئًا يرى المانح كل الحلقات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                halaqas.forEach { h ->
                    val id = h.optString("id")
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = id in selected, onCheckedChange = { on -> selected = if (on) selected + id else selected - id })
                        Text(h.optString("name"))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(selected.toList()) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun ProofDialog(pledgeId: String, kind: String, onDismiss: () -> Unit) {
    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(pledgeId) {
        val data = com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.supportProof(pledgeId, kind) } }.getOrNull()
        val bmp = data?.let {
            runCatching {
                val bytes = Base64.decode(it.substringAfter(','), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        if (bmp == null) failed = true else bitmap = bmp
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صورة الإثبات") },
        text = {
            when {
                bitmap != null -> Image(bitmap!!, contentDescription = "صورة إثبات التسليم", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                failed -> Text("تعذر تحميل الصورة")
                else -> LoadingSkeleton(rows = 2)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

/** Donor side: my pledges (with the delivery proof) and the open support cards I can pledge to. */
@Composable
internal fun DonorSupportTab(refreshTick: Int) {
    val scope = rememberCoroutineScope()
    var cards by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var mine by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var pledgeFor by remember { mutableStateOf<JSONObject?>(null) }
    var proofFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var transferFor by remember { mutableStateOf<String?>(null) }
    var threadFor by remember { mutableStateOf<Pair<String, String>?>(null) }

    fun reload(force: Boolean = false) {
        scope.launch {
            loading = true
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.supportCards(force) to AppGraph.repo.myPledges(force) } }
                .onSuccess { (c, m) -> cards = c; mine = m }
                .onFailure { message = it.message ?: "تعذر التحميل"; isError = true }
            loading = false
        }
    }
    LaunchedEffect(refreshTick) { reload(refreshTick > 0) }

    LazyColumn(
        state = rememberLazyListState(), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        message?.let { item { StateNotice(it, if (isError) "danger" else "success") } }
        if (loading && cards.isEmpty() && mine.isEmpty()) item { LoadingSkeleton(rows = 3) }
        if (mine.isNotEmpty()) {
            item { Text("كفالاتي", style = MaterialTheme.typography.titleLarge) }
            items(mine.size) { i ->
                val p = mine[i]
                val st = p.optString("status")
                val submitted = p.str("transfer_submitted_at").isNotBlank()
                val step = when { st == "delivered" -> 4; st == "received" -> 3; submitted -> 2; else -> 1 }
                Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(p.optString("title"), style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(p.optString("period"), p.amountOrNull()).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LinearProgressIndicator(
                            drawStopIndicator = {}, progress = { step / 4f },
                            modifier = Modifier.fillMaxWidth().height(6.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant, color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            listOf("تعهّدت", "أرسلت الإثبات", "استلمنا الدعم", "وصل إلى مستحقيه").mapIndexed { idx, t -> if (idx + 1 <= step) "✓ $t" else t }.joinToString("  ·  "),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (st != "delivered") PaymentInfoCard(p.optJSONObject("pay"), p.amountOrNull())
                        if (submitted) Text("إثبات التحويل: " + p.str("transfer_ref").ifBlank { "صورة مرفقة" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        p.optInt("beneficiaries", -1).takeIf { it >= 0 && st == "delivered" }?.let { Text("استفاد منها $it طالبًا", style = MaterialTheme.typography.bodySmall) }
                        p.str("proof_text").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        p.str("thanks_text").takeIf { it.isNotBlank() }?.let {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .6f)) {
                                Text("💌 $it", Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (st == "pledged" || st == "received") Button(onClick = { transferFor = p.optString("id") }) { Text(if (submitted) "تحديث الإثبات" else "أرسل إثبات التحويل") }
                            if (p.optBoolean("has_image")) OutlinedButton(onClick = { proofFor = p.optString("id") to "delivery" }) { Text("صورة التسليم") }
                            OutlinedButton(onClick = { threadFor = p.optString("id") to p.optString("title") }) { Text("المحادثة") }
                            if (st == "delivered") OutlinedButton(onClick = { cards.firstOrNull { it.optString("id") == p.optString("card_id") }?.let { pledgeFor = it } }) { Text("كفّل مرة أخرى") }
                            if (st == "pledged") TextButton(onClick = {
                                scope.launch {
                                    com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.supportCancel(p.optString("id")) } }
                                        .onSuccess { message = "أُلغيت الكفالة"; isError = false; reload(true) }
                                        .onFailure { message = it.message; isError = true }
                                }
                            }) { Text("إلغاء الكفالة") }
                        }
                    }
                }
            }
        }
        item { Text("فرص الدعم", style = MaterialTheme.typography.titleLarge) }
        if (!loading && cards.isEmpty()) item { EmptyState("لا توجد بطاقات دعم مفتوحة الآن. سيصلك إشعار عند نشر بطاقة جديدة.") }
        items(cards.size) { i ->
            val c = cards[i]
            val periods = c.optJSONArray("periods")?.objs().orEmpty()
            val anyFree = periods.any { it.optInt("taken") < c.optInt("slots", 1) && it.str("mine").isBlank() }
            Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(kindLabel(c.optString("kind")) + " · " + recurrenceLabel(c.optString("recurrence")), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                    Text(c.optString("title"), style = MaterialTheme.typography.titleLarge)
                    c.str("description").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                    val meta = listOfNotNull(c.amountOrNull(), c.str("amount_text").takeIf { it.isNotBlank() }, c.optInt("beneficiaries", -1).takeIf { it >= 0 }?.let { "$it طالبًا مستفيدًا" }, c.str("halaqa_name").takeIf { it.isNotBlank() }).joinToString(" · ")
                    if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        periods.forEach { p ->
                            val taken = p.optInt("taken") >= c.optInt("slots", 1)
                            AssistChipLike(p.optString("period") + if (p.str("mine").isNotBlank()) " ✓ كفالتك" else if (taken) " · مكتملة" else "", taken)
                        }
                    }
                    val covered = periods.count { it.optInt("taken") >= c.optInt("slots", 1) }
                    if (periods.isNotEmpty()) {
                        LinearProgressIndicator(drawStopIndicator = {}, progress = { covered.toFloat() / periods.size }, modifier = Modifier.fillMaxWidth().height(6.dp), trackColor = MaterialTheme.colorScheme.surfaceVariant, color = MaterialTheme.colorScheme.primary)
                        Text("غُطّيت $covered من ${periods.size} فترات", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(enabled = anyFree, onClick = { pledgeFor = c }, modifier = Modifier.fillMaxWidth()) { Text(if (anyFree) "أتكفّل" else "اكتملت الكفالات") }
                }
            }
        }
    }

    pledgeFor?.let { c ->
        val periods = c.optJSONArray("periods")?.objs().orEmpty().filter { it.optInt("taken") < c.optInt("slots", 1) && it.str("mine").isBlank() }
        var chosen by remember(c) { mutableStateOf(if (periods.size == 1) setOf(periods.first().optString("period")) else emptySet()) }
        var anonymous by remember(c) { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { pledgeFor = null },
            title = { Text("التكفّل: ${c.optString("title")}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("اختر الفترات التي تتكفّل بها. التحويل يتم خارج التطبيق وسيؤكد المدير الاستلام.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    periods.forEach { p ->
                        val name = p.optString("period")
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = name in chosen, onCheckedChange = { on -> chosen = if (on) chosen + name else chosen - name })
                            Text(name)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("أتكفّل دون ذكر اسمي", Modifier.weight(1f))
                        Switch(checked = anonymous, onCheckedChange = { anonymous = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = chosen.isNotEmpty(), onClick = {
                    val list = chosen.toList(); val anon = anonymous; val id = c.optString("id")
                    pledgeFor = null
                    scope.launch {
                        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.supportPledge(id, list, anon) } }
                            .onSuccess { n -> message = if (n > 0) "جزاك الله خيرًا، سُجّلت كفالتك. حوّل المبلغ ثم أرسل إثبات التحويل من «كفالاتي»" else "تعذّر التسجيل (قد تكون الفترة اكتملت)"; isError = n == 0; reload(true) }
                            .onFailure { message = it.message ?: "تعذر التسجيل"; isError = true }
                    }
                }) { Text("تأكيد التكفّل") }
            },
            dismissButton = { TextButton(onClick = { pledgeFor = null }) { Text("إلغاء") } },
        )
    }
    proofFor?.let { ProofDialog(it.first, it.second) { proofFor = null } }
    threadFor?.let { t -> ThreadSheet("pledge", t.first, t.second, onDismiss = { threadFor = null }) }
    transferFor?.let { id ->
        TransferDialog(onDismiss = { transferFor = null }, onSubmit = { ref, image ->
            transferFor = null
            scope.launch {
                com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.submitTransfer(id, ref, image) } }
                    .onSuccess { ok -> message = if (ok) "وصل إثبات التحويل إلى المدير، جزاك الله خيرًا" else "تعذّر إرسال الإثبات"; isError = !ok; reload(true) }
                    .onFailure { message = it.message ?: "تعذّر الإرسال"; isError = true }
            }
        })
    }
}

/** RC86: one compact donor row (about 72dp) instead of a tall card: a gradient badge with the initial and a girih lattice, the name, the contact and what the donor sees. */
@Composable
private fun DonorRow(d: JSONObject, sees: String, index: Int, onAssign: () -> Unit) {
    val pairs = listOf(HalaqatiGradients.Emerald, HalaqatiGradients.Blue, HalaqatiGradients.Violet, HalaqatiGradients.Teal, HalaqatiGradients.Rose, HalaqatiGradients.Orange, HalaqatiGradients.Gold)
    val name = d.optString("name").ifBlank { "مانح" }
    val pair = pairs[(name.hashCode().let { if (it < 0) -it else it } + index) % pairs.size]
    Surface(
        Modifier.fillMaxWidth().clickable { onAssign() }, shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface, border = cardHairline(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.width(62.dp).fillMaxHeight().background(pair.brush()).girih(Color.White, .22f, 26.dp),
                contentAlignment = Alignment.Center,
            ) { Text(name.trim().take(1).uppercase(), color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold) }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 9.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(d.str("email").ifBlank { d.str("phone") }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Visibility, null, Modifier.size(14.dp), tint = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                    Text(sees, style = MaterialTheme.typography.labelMedium, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onAssign, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Tune, contentDescription = "تحديد الحلقات") }
        }
    }
}
