@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.SegmentedTabs
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.components.halaqatiEnter
import com.mohammedsamir.halaqati.ui.theme.cardHairline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * RC52: student reward requests (cash or in-kind), the admin-managed catalog and policy, the tracking bar
 * (request → admin approval → transfer & proof → receipt → delivery) and the per-request message thread
 * between ONE donor and the admin. Donors never see each other and never contact guardians or students.
 */
private fun JSONObject.rs(key: String): String = optString(key).takeIf { it != "null" }.orEmpty()

private fun rewardStatusLabel(r: JSONObject): String = when (r.rs("status")) {
    "requested" -> "بانتظار موافقة المدير"
    "approved" -> if (r.rs("transfer_submitted_at").isNotBlank()) "بانتظار تأكيد الاستلام" else if (r.rs("kind") == "cash") "بانتظار تحويلك" else "بانتظار تسليم الهدية"
    "received" -> "تم الاستلام · بانتظار التسليم للطالب"
    "delivered" -> "سُلّمت للطالب"
    "rejected" -> "اعتذر المدير"
    "cancelled" -> "أُلغي الطلب"
    else -> r.rs("status")
}

private fun rewardMonthLabel(month: String): String = awardMonthLabel(month.take(7))

/** The five-stage tracking bar with a date under every reached stage. */
@Composable
internal fun RewardTracker(r: JSONObject) {
    val status = r.rs("status")
    val transferred = r.rs("transfer_submitted_at").isNotBlank()
    val cash = r.rs("kind") == "cash"
    val reached = when (status) {
        "requested" -> 0
        "approved" -> if (transferred) 2 else 1
        "received" -> 3
        "delivered" -> 4
        else -> -1
    }
    val steps = listOf(
        "الطلب" to r.rs("created_at"),
        "موافقة المدير" to r.rs("approved_at"),
        (if (cash) "تحويل وإثبات" else "تسليم الهدية") to r.rs("transfer_submitted_at"),
        "استلام" to r.rs("received_at"),
        "تسليم للطالب" to r.rs("delivered_at"),
    )
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LinearProgressIndicator(
            drawStopIndicator = {}, progress = { if (reached < 0) 0f else (reached + 1) / 5f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            steps.forEachIndexed { i, (label, date) ->
                val on = reached >= i
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Box(
                        Modifier.size(22.dp).background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (on) "✓" else (i + 1).toString(), style = MaterialTheme.typography.labelSmall, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    Text(if (on && date.length >= 10) date.take(10).substring(5).replace('-', '/') else " ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun RewardStatusPill(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f)) {
        Text(text, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

// ---------------------------------------------------------------------------------------------
// Donor: request dialog
// ---------------------------------------------------------------------------------------------

@Composable
internal fun RewardRequestDialog(winner: JSONObject, month: String, onDismiss: () -> Unit, onFinished: (String, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var kind by remember { mutableStateOf("cash") }
    var options by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var allowOtherCash by remember { mutableStateOf(true) }
    var allowOtherInkind by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<String?>(null) } // option id, or "other"
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("ILS") }
    var note by remember { mutableStateOf("") }
    var anonymous by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.rewardOptions() to AppGraph.repo.rewardSettings() } }
            .onSuccess { (o, s) ->
                options = o
                allowOtherCash = s.optBoolean("allow_other_cash", true)
                allowOtherInkind = s.optBoolean("allow_other_inkind", true)
            }
            .onFailure { error = it.message }
        loaded = true
    }
    val shown = options.filter { it.rs("kind") == kind }
    val otherAllowed = if (kind == "cash") allowOtherCash else allowOtherInkind
    val amountValue = amount.trim().replace(',', '.').toDoubleOrNull()
    val chosen = shown.firstOrNull { it.rs("id") == selected }
    val valid = when {
        selected == null -> false
        selected == "other" && kind == "cash" -> (amountValue ?: 0.0) > 0.0
        selected == "other" -> note.trim().length >= 3
        else -> chosen != null
    }

    HalaqatiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("مكافأة ${winner.optString("name")}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("اختر نوع المكافأة. يراجع المدير الطلب ثم تظهر لك الخطوات التالية وتتابعها في «طلبات المكافآت».", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SegmentedTabs(listOf("مبلغ مالي", "هدية عينية"), if (kind == "cash") 0 else 1, { kind = if (it == 0) "cash" else "inkind"; selected = null; error = null })
                if (!loaded) LoadingSkeleton(rows = 2)
                shown.forEach { o ->
                    val on = selected == o.rs("id")
                    Surface(
                        onClick = { selected = o.rs("id") }, shape = RoundedCornerShape(14.dp),
                        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = on, onClick = { selected = o.rs("id") })
                            Column(Modifier.weight(1f)) {
                                Text(o.rs("title"), fontWeight = FontWeight.Bold)
                                o.rs("description").takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (kind == "cash") o.amountOrNull()?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
                        }
                    }
                }
                if (loaded && shown.isEmpty() && !otherAllowed) Text("لم يحدّد المدير خيارات لهذا النوع بعد.", style = MaterialTheme.typography.bodyMedium)
                if (otherAllowed) {
                    val on = selected == "other"
                    Surface(
                        onClick = { selected = "other" }, shape = RoundedCornerShape(14.dp),
                        color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = on, onClick = { selected = "other" })
                            Text(if (kind == "cash") "مبلغ آخر أحدده" else "هدية أخرى أصفها", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (selected == "other" && kind == "cash") {
                    OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CURRENCIES.forEach { (k, l) -> ProFilterChip(currency == k, { currency = k }, l, showCheckIcon = false) }
                    }
                }
                OutlinedTextField(
                    note, { note = it }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (selected == "other" && kind == "inkind") "صف الهدية" else "ملاحظة للمدير (اختياري)") },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("مجهول الاسم عند الطالب", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(anonymous, { anonymous = it })
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(enabled = valid && !busy, onClick = {
                busy = true; error = null
                val optId = if (selected == "other") null else selected
                scope.launch {
                    com.mohammedsamir.halaqati.ui.runCatchingCancellable {
                        withContext(Dispatchers.IO) {
                            AppGraph.repo.rewardRequest(
                                winner.optString("student_id"), "$month-01", kind, optId,
                                if (selected == "other" && kind == "cash") amountValue else null,
                                if (selected == "other" && kind == "cash") currency else null,
                                note, anonymous,
                            )
                        }
                    }.onSuccess { res ->
                        busy = false
                        if (res.optBoolean("ok")) onFinished(res.optString("message").ifBlank { "وصل طلبك إلى المدير" }, true)
                        else error = res.optString("message").ifBlank { "تعذّر إرسال الطلب" }
                    }.onFailure { busy = false; error = it.message ?: "تعذّر إرسال الطلب" }
                }
            }) { Text(if (busy) "جارٍ الإرسال…" else "إرسال الطلب") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("إلغاء") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Thread sheet (shared)
// ---------------------------------------------------------------------------------------------

@Composable
internal fun ThreadSheet(kind: String, refId: String, title: String, onDismiss: () -> Unit) = ThreadSheetCore(
    title = title,
    hint = "محادثة خاصة بهذا الطلب بينك وبين المدير فقط.",
    key = "$kind:$refId",
    load = { AppGraph.repo.threadGet(kind, refId) },
    post = { AppGraph.repo.threadPost(kind, refId, it) },
    onDismiss = onDismiss,
)

/** RC60: conversation addressed by thread id (list of chats, direct chat, notification tap). */
@Composable
internal fun ChatSheet(threadId: String, title: String, onDismiss: () -> Unit) = ThreadSheetCore(
    title = title,
    hint = "محادثة خاصة بين المدير والمانح فقط.",
    key = "chat:$threadId",
    load = { AppGraph.repo.chatThreadById(threadId) },
    post = { AppGraph.repo.chatPostById(threadId, it); AppGraph.repo.chatPush() },
    onDismiss = onDismiss,
)

@Composable
private fun ThreadSheetCore(
    title: String,
    hint: String,
    key: String,
    load: suspend () -> List<JSONObject>,
    post: suspend (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    suspend fun refresh() {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { load() } }
            .onSuccess { messages = it; error = null }
            .onFailure { if (!loaded) error = it.message }
        loaded = true
    }
    LaunchedEffect(key) {
        while (true) { refresh(); delay(8000) }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
        Column(Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 560.dp).padding(horizontal = 16.dp).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(hint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (!loaded) item { LoadingSkeleton(rows = 2) }
                items(messages, key = { it.rs("id") }) { m ->
                    val role = m.rs("role")
                    if (role == "system") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .7f)) {
                                Text(m.rs("body"), Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        val mine = m.optBoolean("mine")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.Start else Arrangement.End) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.widthIn(max = 280.dp),
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(m.rs("body"), style = MaterialTheme.typography.bodyMedium)
                                    Text(com.mohammedsamir.halaqati.ui.components.GazaTime.dateTime(m.rs("created_at")), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, modifier = Modifier.weight(1f), placeholder = { Text("اكتب رسالة…") }, maxLines = 3)
                Button(enabled = text.trim().isNotEmpty() && !sending, onClick = {
                    val body = text.trim(); sending = true
                    scope.launch {
                        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { post(body) } }
                            .onSuccess { text = ""; refresh() }
                            .onFailure { error = it.message ?: "تعذّر الإرسال" }
                        sending = false
                    }
                }) { Text("إرسال") }
            }
        }
    }
}

@Composable
internal fun RewardProofDialog(id: String, kind: String, onDismiss: () -> Unit) {
    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(id, kind) {
        val data = com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.rewardProof(id, kind) } }.getOrNull()
        val bmp = data?.let {
            runCatching {
                val bytes = Base64.decode(it.substringAfter(','), Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
        if (bmp == null) failed = true else bitmap = bmp
    }
    HalaqatiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("صورة الإثبات") },
        text = {
            when {
                bitmap != null -> Image(bitmap!!, contentDescription = "صورة الإثبات", modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                failed -> Text("تعذر تحميل الصورة")
                else -> LoadingSkeleton(rows = 2)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

// ---------------------------------------------------------------------------------------------
// Donor: "طلبات المكافآت"
// ---------------------------------------------------------------------------------------------

@Composable
internal fun DonorRewardsTab(refreshTick: Int) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var transferFor by remember { mutableStateOf<String?>(null) }
    var proofFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var threadFor by remember { mutableStateOf<JSONObject?>(null) }

    fun reload(force: Boolean = false) {
        scope.launch {
            loading = true
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.myRewards(force) } }
                .onSuccess { items = it }
                .onFailure { message = it.message ?: "تعذر التحميل"; isError = true }
            loading = false
        }
    }
    LaunchedEffect(refreshTick) { reload(refreshTick > 0) }
    fun act(ok: String, block: () -> Unit) {
        scope.launch {
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { block() } }
                .onSuccess { message = ok; isError = false; reload(true) }
                .onFailure { message = it.message ?: "تعذّر التنفيذ"; isError = true }
        }
    }

    LazyColumn(
        state = rememberLazyListState(), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        message?.let { item { StateNotice(it, if (isError) "danger" else "success") } }
        if (loading && items.isEmpty()) item { LoadingSkeleton(rows = 3) }
        if (!loading && items.isEmpty()) item { EmptyState("لا توجد طلبات مكافآت بعد. من «المكرَّمون» افتح الطالب ثم اضغط «كافئ هذا الطالب».") }
        items(items, key = { it.rs("id") }) { r ->
            val status = r.rs("status")
            val cash = r.rs("kind") == "cash"
            val transferred = r.rs("transfer_submitted_at").isNotBlank()
            Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${r.rs("student_name")} · ${rewardMonthLabel(r.rs("month"))}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                listOfNotNull(r.rs("title").takeIf { it.isNotBlank() }, r.amountOrNull()).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RewardStatusPill(rewardStatusLabel(r))
                    }
                    RewardTracker(r)
                    r.rs("admin_note").takeIf { it.isNotBlank() }?.let { Text("ملاحظة المدير: $it", style = MaterialTheme.typography.bodySmall) }
                    if (status == "approved" && cash && !transferred) {
                        val pay = r.optJSONObject("pay")
                        val mapped = JSONObject()
                            .put("method", pay?.rs("pay_method").orEmpty()).put("provider", pay?.rs("pay_provider").orEmpty())
                            .put("account", pay?.rs("pay_account").orEmpty()).put("holder", pay?.rs("pay_holder").orEmpty()).put("note", pay?.rs("pay_note").orEmpty())
                        PaymentInfoCard(mapped, r.amountOrNull())
                    }
                    if (status == "delivered") {
                        r.rs("delivery_text").takeIf { it.isNotBlank() }?.let { Text("إفادة التسليم: $it", style = MaterialTheme.typography.bodyMedium) }
                        r.rs("thanks_text").takeIf { it.isNotBlank() }?.let { Text("رسالة شكر: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status == "approved" && cash && !transferred) Button(onClick = { transferFor = r.rs("id") }) { Text("رفع إثبات التحويل") }
                        if (r.optBoolean("has_delivery_image")) OutlinedButton(onClick = { proofFor = r.rs("id") to "delivery" }) { Text("صورة التسليم") }
                        OutlinedButton(onClick = { threadFor = r }) { Text(if (r.optInt("unread") > 0) "المحادثة (${r.optInt("unread")})" else "المحادثة") }
                        if (status == "requested" || (status == "approved" && !transferred)) {
                            TextButton(onClick = { act("أُلغي الطلب") { AppGraph.repo.rewardCancel(r.rs("id")) } }) { Text("إلغاء الطلب") }
                        }
                    }
                }
            }
        }
    }
    transferFor?.let { id ->
        TransferDialog(onDismiss = { transferFor = null }, onSubmit = { ref, image -> transferFor = null; act("وصل إثبات التحويل إلى المدير") { AppGraph.repo.rewardSubmitTransfer(id, ref, image) } })
    }
    proofFor?.let { RewardProofDialog(it.first, it.second) { proofFor = null } }
    threadFor?.let { r -> ThreadSheet("reward", r.rs("id"), "مكافأة ${r.rs("student_name")}", onDismiss = { threadFor = null; reload() }) }
}

// ---------------------------------------------------------------------------------------------
// Admin: "المكافآت" — policy, catalog and requests
// ---------------------------------------------------------------------------------------------

private val POLICIES = listOf(
    "one_per_month" to "مكافأة واحدة للطالب شهريًا",
    "group" to "قبول عدة طلبات وتجميعها عندي",
    "co_fund" to "تمويل مشترك من عدة مانحين",
)

@Composable
internal fun AdminRewardsPanel(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var requests by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var options by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var settings by remember { mutableStateOf(JSONObject()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var editOption by remember { mutableStateOf<JSONObject?>(null) }
    var showOption by remember { mutableStateOf(false) }
    var decide by remember { mutableStateOf<Pair<JSONObject, Boolean>?>(null) }
    var deliverFor by remember { mutableStateOf<JSONObject?>(null) }
    var proofFor by remember { mutableStateOf<Pair<String, String>?>(null) }
    var threadFor by remember { mutableStateOf<JSONObject?>(null) }

    fun reload(force: Boolean = false) {
        scope.launch {
            loading = true
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { Triple(AppGraph.repo.adminRewards(force), AppGraph.repo.rewardOptions(force), AppGraph.repo.rewardSettings(force)) } }
                .onSuccess { (r, o, s) -> requests = r; options = o; settings = s }
                .onFailure { message = it.message ?: "تعذر التحميل"; isError = true }
            loading = false
        }
    }
    LaunchedEffect(Unit) { reload() }
    fun act(ok: String, block: () -> Unit) {
        scope.launch {
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { block() } }
                .onSuccess { message = ok; isError = false; reload(true) }
                .onFailure { message = it.message ?: "تعذّر التنفيذ"; isError = true }
        }
    }
    fun saveSetting(json: JSONObject) = act("حُفظت الإعدادات") { AppGraph.repo.setRewardSettings(json) }

    LazyColumn(
        state = rememberLazyListState(), modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        message?.let { item { StateNotice(it, if (isError) "danger" else "success") } }
        if (loading && requests.isEmpty() && options.isEmpty()) item { LoadingSkeleton(rows = 3) }
        item {
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سياسة مكافأة الطالب", style = MaterialTheme.typography.titleMedium)
                    Text("ماذا يحدث إن أراد أكثر من مانح مكافأة الطالب نفسه في الشهر نفسه؟", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val current = settings.optString("dedupe_policy", "one_per_month")
                    POLICIES.forEach { (k, l) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = current == k, onClick = { saveSetting(JSONObject().put("dedupe_policy", k)) })
                            Text(l, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("السماح بمبلغ آخر يحدده المانح", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(settings.optBoolean("allow_other_cash", true), { saveSetting(JSONObject().put("allow_other_cash", it)) })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("السماح بهدية أخرى يصفها المانح", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(settings.optBoolean("allow_other_inkind", true), { saveSetting(JSONObject().put("allow_other_inkind", it)) })
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("خيارات المكافأة", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Button(onClick = { editOption = null; showOption = true }) { Text("خيار جديد") }
            }
        }
        if (!loading && options.isEmpty()) item { EmptyState("لم تُضف خيارات بعد (مثال: 20 دولار، شنطة مدرسية). يختار المانح منها.") }
        items(options, key = { "opt-" + it.rs("id") }) { o ->
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp, border = cardHairline()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(o.rs("title") + if (!o.optBoolean("active", true)) " (مخفي)" else "", style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(if (o.rs("kind") == "cash") "مبلغ مالي" else "هدية عينية", if (o.rs("kind") == "cash") o.amountOrNull() else null).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { editOption = o; showOption = true }) { Text("تعديل") }
                    TextButton(onClick = { act("حُذف الخيار") { AppGraph.repo.rewardOptionDelete(o.rs("id")) } }) { Text("حذف") }
                }
            }
        }
        item { Text("طلبات المكافآت", style = MaterialTheme.typography.titleLarge) }
        if (!loading && requests.isEmpty()) item { EmptyState("لا توجد طلبات مكافآت بعد.") }
        items(requests, key = { "req-" + it.rs("id") }) { r ->
            val status = r.rs("status")
            val cash = r.rs("kind") == "cash"
            val transferred = r.rs("transfer_submitted_at").isNotBlank()
            Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${r.rs("student_name")} · ${rewardMonthLabel(r.rs("month"))}", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "من: ${r.rs("donor_name")}${if (r.optBoolean("anonymous")) " (يظهر مجهولًا للطالب)" else ""}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                listOfNotNull(r.rs("title").takeIf { it.isNotBlank() }, r.amountOrNull()).joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        RewardStatusPill(rewardStatusLabel(r))
                    }
                    if (r.optInt("same_student_count") > 1 && status in setOf("requested", "approved")) {
                        Text("${r.optInt("same_student_count")} طلبات لهذا الطالب في هذا الشهر. راجعها معًا قبل الموافقة.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                    r.rs("note").takeIf { it.isNotBlank() }?.let { Text("ملاحظة المانح: $it", style = MaterialTheme.typography.bodySmall) }
                    RewardTracker(r)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (status == "requested") {
                            Button(onClick = { decide = r to true }) { Text("موافقة") }
                            OutlinedButton(onClick = { decide = r to false }) { Text("اعتذار") }
                        }
                        if (status == "approved") {
                            if (r.optBoolean("has_transfer_image")) OutlinedButton(onClick = { proofFor = r.rs("id") to "transfer" }) { Text("صورة الحوالة") }
                            if (r.rs("transfer_ref").isNotBlank()) Text("رقم الحوالة: ${r.rs("transfer_ref")}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.CenterVertically))
                            Button(onClick = { act("تم تأكيد الاستلام") { AppGraph.repo.rewardConfirm(r.rs("id")) } }) { Text(if (cash && !transferred) "تأكيد الاستلام" else "تأكيد الاستلام") }
                        }
                        if (status == "received") Button(onClick = { deliverFor = r }) { Text("تسليم للطالب") }
                        if (r.optBoolean("has_delivery_image")) OutlinedButton(onClick = { proofFor = r.rs("id") to "delivery" }) { Text("صورة التسليم") }
                        OutlinedButton(onClick = { threadFor = r }) { Text(if (r.optInt("unread") > 0) "المحادثة (${r.optInt("unread")})" else "المحادثة") }
                    }
                }
            }
        }
    }

    if (showOption) RewardOptionDialog(
        initial = editOption, onDismiss = { showOption = false },
        onSave = { json -> showOption = false; act("حُفظ الخيار") { AppGraph.repo.rewardOptionSave(json) } },
    )
    decide?.let { (r, approve) ->
        var note by remember(r.rs("id")) { mutableStateOf("") }
        HalaqatiAlertDialog(
            onDismissRequest = { decide = null },
            title = { Text(if (approve) "الموافقة على الطلب" else "الاعتذار عن الطلب") },
            text = {
                OutlinedTextField(
                    note, { note = it }, minLines = 2, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (approve) "ملاحظة للمانح (اختياري)" else "سبب الاعتذار (اختياري)") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    decide = null
                    act(if (approve) "تمت الموافقة وأُشعر المانح" else "تم الاعتذار وأُشعر المانح") { AppGraph.repo.rewardDecide(r.rs("id"), approve, note) }
                }) { Text(if (approve) "موافقة" else "اعتذار") }
            },
            dismissButton = { TextButton(onClick = { decide = null }) { Text("إلغاء") } },
        )
    }
    deliverFor?.let { r ->
        DeliverDialog(
            pledge = r, onDismiss = { deliverFor = null },
            onSubmit = { text, image, thanks -> deliverFor = null; act("سُجّل التسليم وأُشعر المانح") { AppGraph.repo.rewardDeliver(r.rs("id"), text, image, thanks) } },
        )
    }
    proofFor?.let { RewardProofDialog(it.first, it.second) { proofFor = null } }
    threadFor?.let { r -> ThreadSheet("reward", r.rs("id"), "${r.rs("student_name")} · ${r.rs("donor_name")}", onDismiss = { threadFor = null; reload() }) }
}

@Composable
private fun RewardOptionDialog(initial: JSONObject?, onDismiss: () -> Unit, onSave: (JSONObject) -> Unit) {
    var kind by remember { mutableStateOf(initial?.rs("kind")?.takeIf { it.isNotBlank() } ?: "cash") }
    var title by remember { mutableStateOf(initial?.rs("title").orEmpty()) }
    var amount by remember { mutableStateOf(initial?.let { o -> o.optDouble("amount", Double.NaN).takeIf { !it.isNaN() }?.let { a -> if (a == Math.rint(a)) a.toLong().toString() else a.toString() } }.orEmpty()) }
    var currency by remember { mutableStateOf(initial?.rs("currency")?.takeIf { it.isNotBlank() } ?: "ILS") }
    var description by remember { mutableStateOf(initial?.rs("description").orEmpty()) }
    var active by remember { mutableStateOf(initial?.optBoolean("active", true) ?: true) }
    HalaqatiAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "خيار مكافأة جديد" else "تعديل الخيار") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedTabs(listOf("مبلغ مالي", "هدية عينية"), if (kind == "cash") 0 else 1, { kind = if (it == 0) "cash" else "inkind" })
                OutlinedTextField(title, { title = it }, label = { Text(if (kind == "cash") "الاسم (مثال: مكافأة صغيرة)" else "الهدية (مثال: شنطة مدرسية)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (kind == "cash") {
                    OutlinedTextField(amount, { amount = it }, label = { Text("المبلغ") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        CURRENCIES.forEach { (k, l) -> ProFilterChip(currency == k, { currency = k }, l, showCheckIcon = false) }
                    }
                }
                OutlinedTextField(description, { description = it }, label = { Text("وصف (اختياري)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ظاهر للمانحين", Modifier.weight(1f))
                    Switch(active, { active = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.trim().length >= 2 && (kind != "cash" || (amount.trim().replace(',', '.').toDoubleOrNull() ?: 0.0) > 0.0),
                onClick = {
                    onSave(
                        JSONObject().put("id", initial?.rs("id").orEmpty()).put("kind", kind).put("title", title.trim())
                            .put("amount", if (kind == "cash") amount.trim().replace(',', '.') else "").put("currency", currency)
                            .put("description", description.trim()).put("active", active),
                    )
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
