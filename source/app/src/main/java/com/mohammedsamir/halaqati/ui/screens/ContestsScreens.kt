@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.Contest
import com.mohammedsamir.halaqati.data.ContestDraft
import com.mohammedsamir.halaqati.data.ContestPickerState
import com.mohammedsamir.halaqati.data.ContestRules
import com.mohammedsamir.halaqati.data.ContestStatus
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.components.StatusPill
import com.mohammedsamir.halaqati.ui.components.items
import com.mohammedsamir.halaqati.ui.runCatchingCancellable
import com.mohammedsamir.halaqati.ui.theme.cardHairline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Donor contests (المسابقات). One route, three faces by role: the donor (OBSERVER) manages his own contests, the system admin
 * reviews them, and everyone connected to the halaqa (teacher, supervisor, guardian, student) sees approved ones read-only.
 */
@Composable
fun ContestsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    when (profile.role) {
        UserRole.OBSERVER -> DonorContestsScreen()
        UserRole.ADMIN -> AdminContestsScreen()
        else -> ReadOnlyContestsScreen()
    }
}

private fun contestError(e: Throwable, fallback: String): String {
    val m = e.message.orEmpty()
    return when {
        m.contains("contest_", true) || m.contains("Could not find the function", true) || m.contains("PGRST202", true) ->
            "ميزة المسابقات لم تُفعَّل على الخادم بعد. حاول لاحقًا أو تواصل مع مدير النظام."
        m.isBlank() -> fallback
        else -> m
    }
}

private fun statusKind(s: ContestStatus) = when (s) { ContestStatus.APPROVED -> "success"; ContestStatus.REJECTED -> "danger"; ContestStatus.PENDING -> "warning" }

@Composable
private fun ContestCard(c: Contest, showDonor: Boolean, showStatus: Boolean, actions: (@Composable RowScope.() -> Unit)? = null) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = cardHairline(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(c.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (showStatus) StatusPill(c.status.label, statusKind(c.status))
            }
            Text(c.title, style = MaterialTheme.typography.titleSmall)
            val meta = listOfNotNull(
                c.halaqaName?.let { "الحلقة: $it" } ?: if (c.halaqaId == null) "الحلقة: لم تُحدَّد بعد" else null,
                c.donorName?.takeIf { showDonor }?.let { "المانح: $it" },
            )
            meta.forEach { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("الشروط", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(c.conditions, style = MaterialTheme.typography.bodyMedium)
            Text("المكافأة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Text(c.reward, style = MaterialTheme.typography.bodyMedium)
            if (c.startsOn != null || c.endsOn != null) {
                Text(listOfNotNull(c.startsOn?.let { "من $it" }, c.endsOn?.let { "إلى $it" }).joinToString(" "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (c.status == ContestStatus.REJECTED && c.rejectReason != null) {
                StateNotice("سبب الرفض: ${c.rejectReason}", "danger")
            }
            if (actions != null) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}

/** Shared loader: stored lists open offline; a manual refresh re-reads from the server. */
@Composable
private fun rememberContestList(load: (Boolean) -> List<Contest>): ContestListState {
    val scope = rememberCoroutineScope()
    val state = remember { ContestListState() }
    state.scope = scope
    state.load = load
    LaunchedEffect(Unit) { state.reload(true) }
    return state
}

private class ContestListState {
    var items by mutableStateOf<List<Contest>>(emptyList())
    var loading by mutableStateOf(true)
    var message by mutableStateOf<String?>(null)
    var isError by mutableStateOf(false)
    lateinit var scope: kotlinx.coroutines.CoroutineScope
    lateinit var load: (Boolean) -> List<Contest>

    fun reload(force: Boolean) {
        scope.launch {
            loading = true
            runCatchingCancellable { withContext(Dispatchers.IO) { load(force) } }
                .onSuccess { items = it; if (isError) message = null; isError = false }
                .onFailure { message = contestError(it, "تعذر تحميل المسابقات"); isError = true }
            loading = false
        }
    }
}

@Composable
private fun StatusFilterRow(selected: ContestStatus?, items: List<Contest>, onSelect: (ContestStatus?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProFilterChip(selected == null, { onSelect(null) }, "الكل (${items.size})")
        ContestStatus.entries.forEach { s ->
            ProFilterChip(selected == s, { onSelect(s) }, "${s.label} (${items.count { it.status == s }})")
        }
    }
}

// ---------------------------------------------------------------- donor

@Composable
fun DonorContestsScreen() {
    val st = rememberContestList { AppGraph.repo.contestsMine(it) }
    val scope = rememberCoroutineScope()
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var halaqasLoaded by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<ContestStatus?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.contestHalaqas(true) } }
            .onSuccess { halaqas = it; halaqasLoaded = true }
    }
    val shown = ContestRules.filter(st.items, filter)

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "مسابقاتي", onRefresh = { st.reload(true) })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            notice?.let { item { StateNotice(it, "success") } }
            st.message?.let { item { StateNotice(it, if (st.isError) "danger" else "success") } }
            if (halaqasLoaded && ContestRules.pickerState(halaqas.map { it.optString("id") }) == ContestPickerState.NO_HALAQA) {
                item { StateNotice(ContestRules.NO_HALAQA_MESSAGE, "info") }
            }
            item {
                Button(
                    enabled = !opening,
                    onClick = {
                        notice = null; opening = true
                        // the donor's halaqat are read fresh each time (an admin may have linked one a minute ago); the saved copy is used only when offline
                        scope.launch {
                            runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.contestHalaqas(true) } }.onSuccess { halaqas = it; halaqasLoaded = true }
                            opening = false; showForm = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text(if (opening) "جارٍ التحميل…" else "مسابقة جديدة") }
            }
            if (st.items.isNotEmpty()) item { StatusFilterRow(filter, st.items, { filter = it }) }
            if (st.loading && st.items.isEmpty()) item { LoadingSkeleton(rows = 3) }
            if (!st.loading && st.items.isEmpty() && !st.isError) item { EmptyState("لم تضف أي مسابقة بعد. أضف مسابقتك الأولى وسترسل إلى مدير النظام للمراجعة.", animated = false) }
            if (!st.loading && st.items.isNotEmpty() && shown.isEmpty()) item { EmptyState("لا توجد مسابقات بهذه الحالة", animated = false) }
            items(shown, key = { it.id }) { c -> ContestCard(c, showDonor = false, showStatus = true) }
        }
    }

    if (showForm) {
        ContestFormDialog(
            halaqas = halaqas,
            onDismiss = { showForm = false },
            onSubmit = { draft, halaqaId ->
                val error = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.contestSubmit(draft, halaqaId) }.exceptionOrNull() }
                if (error == null) {
                    showForm = false
                    notice = "أُرسلت المسابقة إلى مدير النظام للمراجعة"
                    st.reload(true)
                }
                error?.let { contestError(it, "تعذر إرسال المسابقة") }
            },
        )
    }
}

@Composable
private fun ContestFormDialog(halaqas: List<JSONObject>, onDismiss: () -> Unit, onSubmit: suspend (ContestDraft, String?) -> String?) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var conditions by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("") }
    var startsOn by remember { mutableStateOf("") }
    var endsOn by remember { mutableStateOf("") }
    val ids = halaqas.map { it.optString("id") }
    var halaqaId by remember { mutableStateOf(ids.singleOrNull()) }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var serverError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val picker = ContestRules.pickerState(ids)

    @Composable
    fun field(key: String, value: String, label: String, onChange: (String) -> Unit, lines: Int = 1, hint: String? = null) {
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) }, minLines = lines, enabled = !busy,
            isError = key in errors, supportingText = (errors[key] ?: hint)?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }

    HalaqatiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("مسابقة جديدة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                field("name", name, "اسم المسابقة", { name = it })
                field("title", title, "العنوان", { title = it })
                field("conditions", conditions, "الشروط", { conditions = it }, lines = 3)
                field("reward", reward, "المكافأة", { reward = it }, lines = 2)
                field("startsOn", startsOn, "تاريخ البداية (اختياري)", { startsOn = it }, hint = "يوم-شهر-سنة، مثل 01-12-2026")
                field("endsOn", endsOn, "تاريخ النهاية (اختياري)", { endsOn = it }, hint = "يوم-شهر-سنة، مثل 31-12-2026")
                Text("الحلقة المستهدفة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                when (picker) {
                    ContestPickerState.NO_HALAQA -> StateNotice(ContestRules.NO_HALAQA_MESSAGE, "info")
                    ContestPickerState.SINGLE -> StatusPill(halaqas.first().optString("name"), "neutral")
                    ContestPickerState.CHOOSE -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        halaqas.forEach { h -> ProFilterChip(halaqaId == h.optString("id"), { halaqaId = h.optString("id") }, h.optString("name"), enabled = !busy) }
                    }
                }
                if (picker == ContestPickerState.CHOOSE && halaqaId == null) {
                    Text("اختر الحلقة التي تستهدفها المسابقة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("تصل المسابقة إلى مدير النظام، وتظهر لأصحاب الحلقة بعد اعتمادها.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                serverError?.let { StateNotice(it, "danger") }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && (picker != ContestPickerState.CHOOSE || halaqaId != null),
                onClick = {
                    val draft = ContestDraft(name, title, conditions, reward, ContestRules.normalizeDate(startsOn), ContestRules.normalizeDate(endsOn))
                    errors = ContestRules.validate(draft)
                    if (errors.isEmpty()) scope.launch { busy = true; serverError = onSubmit(draft, halaqaId); busy = false }
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (busy) "جارٍ الإرسال…" else "إرسال للمراجعة") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
    )
}

// ---------------------------------------------------------------- admin

@Composable
fun AdminContestsScreen() {
    val st = rememberContestList { AppGraph.repo.contestsAdmin(it) }
    val scope = rememberCoroutineScope()
    var filter by remember { mutableStateOf<ContestStatus?>(ContestStatus.PENDING) }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var deciding by remember { mutableStateOf<Pair<Contest, Boolean>?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var noticeError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.contestHalaqas(true) } }.onSuccess { halaqas = it }
    }
    val shown = ContestRules.filter(st.items, filter)
    val pendingCount = st.items.count { it.status == ContestStatus.PENDING }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "مسابقات المانحين", onRefresh = { st.reload(true) })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            notice?.let { item { StateNotice(it, if (noticeError) "danger" else "success") } }
            st.message?.let { item { StateNotice(it, if (st.isError) "danger" else "success") } }
            if (st.items.isNotEmpty()) {
                item { StateNotice(if (pendingCount > 0) "بانتظار قرارك: $pendingCount" else "لا توجد مسابقات بانتظار القرار", if (pendingCount > 0) "warning" else "info") }
                item { StatusFilterRow(filter, st.items, { filter = it }) }
            }
            if (st.loading && st.items.isEmpty()) item { LoadingSkeleton(rows = 3) }
            if (!st.loading && st.items.isEmpty() && !st.isError) item { EmptyState("لا توجد مسابقات من المانحين بعد", animated = false) }
            if (!st.loading && st.items.isNotEmpty() && shown.isEmpty()) item { EmptyState("لا توجد مسابقات بهذه الحالة", animated = false) }
            items(shown, key = { it.id }) { c ->
                ContestCard(
                    c, showDonor = true, showStatus = true,
                    actions = if (c.status == ContestStatus.PENDING) ({
                        Button(onClick = { deciding = c to true }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (c.halaqaId == null) "تحديد الحلقة واعتماد" else "اعتماد") }
                        OutlinedButton(onClick = { deciding = c to false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("رفض") }
                    }) else null,
                )
            }
        }
    }

    deciding?.let { (contest, approve) ->
        DecisionDialog(
            contest = contest, approve = approve, halaqas = halaqas, onDismiss = { deciding = null },
            onConfirm = { reason, halaqaId, link ->
                val result = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.contestDecide(contest, approve, reason, halaqaId, link) } }
                deciding = null
                result.onSuccess { ok ->
                    noticeError = !ok
                    notice = if (!ok) "تم البت في هذه المسابقة سابقًا" else if (approve) "اعتُمدت المسابقة وأُبلغ المانح وأصحاب الحلقة" else "رُفضت المسابقة وأُبلغ المانح"
                }.onFailure { noticeError = true; notice = contestError(it, "تعذر تنفيذ القرار") }
                st.reload(true)
            },
        )
    }
}

@Composable
private fun DecisionDialog(contest: Contest, approve: Boolean, halaqas: List<JSONObject>, onDismiss: () -> Unit, onConfirm: suspend (String, String?, Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf("") }
    var halaqaId by remember { mutableStateOf(contest.halaqaId) }
    var link by remember { mutableStateOf(contest.halaqaId == null) }
    var busy by remember { mutableStateOf(false) }
    val to = if (approve) ContestStatus.APPROVED else ContestStatus.REJECTED
    val error = ContestRules.decisionError(contest.status, to, reason, halaqaId)
    HalaqatiAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(if (approve) "اعتماد المسابقة" else "رفض المسابقة") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(contest.name, fontWeight = FontWeight.Bold)
                if (approve) {
                    Text("الحلقة المستهدفة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    if (halaqas.isEmpty()) StateNotice("لا توجد حلقات لعرضها. تحقق من الاتصال ثم أعد المحاولة.", "warning")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        halaqas.forEach { h -> ProFilterChip(halaqaId == h.optString("id"), { halaqaId = h.optString("id") }, h.optString("name"), enabled = !busy) }
                    }
                    if (halaqaId != contest.halaqaId || contest.halaqaId == null) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Checkbox(checked = link, onCheckedChange = { link = it }, enabled = !busy)
                            Text("ربط المانح ${contest.donorName.orEmpty()} بهذه الحلقة", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Text("بعد الاعتماد تظهر المسابقة لمحفّظي الحلقة ومشرفيها وأولياء أمور طلابها وطلابها للقراءة فقط.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text("سبب الرفض (يظهر للمانح)") }, minLines = 3, enabled = !busy, modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && error == null,
                onClick = { scope.launch { busy = true; onConfirm(reason, halaqaId, approve && link) } },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(if (busy) "جارٍ التنفيذ…" else if (approve) "اعتماد" else "رفض") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
    )
}

// ---------------------------------------------------------------- everyone connected to the halaqa (read-only)

@Composable
fun ReadOnlyContestsScreen() {
    val st = rememberContestList { AppGraph.repo.contestsVisible(it) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "المسابقات", onRefresh = { st.reload(true) })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            st.message?.let { item { StateNotice(it, "danger") } }
            if (st.loading && st.items.isEmpty()) item { LoadingSkeleton(rows = 2) }
            if (!st.loading && st.items.isEmpty() && !st.isError) item { EmptyState("لا توجد مسابقات معتمدة لحلقتك الآن", animated = false) }
            items(st.items, key = { it.id }) { c -> ContestCard(c, showDonor = true, showStatus = false) }
        }
    }
}

/** Small home card: how many approved contests the user's halaqa has; tap opens the list. Hidden when there are none or it cannot load. */
@Composable
fun ContestsHomeCard(navigate: (String) -> Unit) {
    var count by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(Unit) {
        count = runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.contestsVisible(false).size } }.getOrNull()
    }
    val n = count ?: return
    if (n == 0) return
    Card(
        onClick = { navigate("contests") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = RoundedCornerShape(16.dp), border = cardHairline(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f)) {
                Text("المسابقات", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(if (n == 1) "مسابقة واحدة معتمدة لحلقتك" else "$n مسابقات معتمدة لحلقتك", style = MaterialTheme.typography.bodyMedium)
            }
            Text("عرض", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
