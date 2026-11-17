@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.ui.components.*
import com.mohammedsamir.halaqati.ui.navigation.AddActionBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

/* ============================== small shared pieces ============================== */

internal fun supDaysSince(date: String?): Int? =
    if (date.isNullOrBlank()) null else runCatching { ChronoUnit.DAYS.between(LocalDate.parse(date.take(10)), LocalDate.now()).toInt() }.getOrNull()

@Composable
internal fun SupStat(label: String, value: String, modifier: Modifier = Modifier, accent: Color? = null, icon: ImageVector? = null) {
    val cs = MaterialTheme.colorScheme
    val tone = when (accent) { null -> "neutral"; cs.error -> "danger"; cs.tertiary -> "warning"; else -> "success" }
    NourStatTile(value, label, modifier, tone, icon)
}

/** RC84: a person row used in the follow-up lists: photo, name, a short line, and one action. */
@Composable
internal fun SupPersonRow(photo: String?, name: String, subtitle: String, @Suppress("UNUSED_PARAMETER") pair: GradientPair, action: String?, actionEnabled: Boolean = true, onAction: () -> Unit = {}) {
    NourCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NourAvatar(photo, name, NourAvatarSize.Medium)
            Column(Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (action != null) FilledTonalButton(onClick = onAction, enabled = actionEnabled, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.Default.NotificationsActive, null, modifier = Modifier.size(NourIcon.Inline)); Spacer(Modifier.width(4.dp)); Text(action)
            }
        }
    }
}

@Composable
internal fun SupSectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) =
    com.mohammedsamir.halaqati.ui.components.NourSectionTitle(text, modifier, trailing)

private val ALERT_TEMPLATES = listOf(
    "لم تبدأ الجلسة" to "لم تُفتح جلسة اليوم في حلقتك حتى الآن. نرجو فتحها وتسجيل الحضور.",
    "تأخر التسميع" to "لاحظنا تأخر التسميع في حلقتك. نرجو متابعة الطلاب وتسجيل الإنجاز.",
    "تذكير بالتقارير" to "نرجو استكمال الخطط والتقارير المطلوبة هذا الأسبوع.",
    "شكر وتقدير" to "بارك الله فيك على التزامك وجهدك مع الطلاب.",
)

/** A private alert from the supervisor / administrator to one teacher (or supervisor). Needs a connection. */
@Composable
internal fun SupAlertDialog(
    recipients: List<Pair<String, String>>,
    halaqaId: String?,
    presetTitle: String = "",
    presetBody: String = "",
    close: () -> Unit,
    onSent: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var recipientId by remember { mutableStateOf(recipients.firstOrNull()?.first.orEmpty()) }
    var title by remember { mutableStateOf(presetTitle) }
    var body by remember { mutableStateOf(presetBody) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    HalaqatiAlertDialog(
        onDismissRequest = { if (!sending) close() },
        title = { Text("تنبيه محفّظ") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (recipients.size > 1) {
                    HalaqatiLazySelectField(
                        label = "المستلم", selectedKey = recipientId, options = recipients,
                        onSelected = { recipientId = it }, modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(recipients.firstOrNull()?.second ?: "لا يوجد مستلم", fontWeight = FontWeight.Bold)
                }
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ALERT_TEMPLATES.forEach { (t, b) ->
                        ProFilterChip(selected = title == t, onClick = { title = t; body = b }, label = t, showCheckIcon = false)
                    }
                }
                OutlinedTextField(title, { if (it.length <= 80) title = it }, label = { Text("العنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { if (it.length <= 500) body = it }, label = { Text("النص") }, minLines = 3, maxLines = 6, modifier = Modifier.fillMaxWidth())
                error?.let { StateNotice(it, kind = "error") }
                Text("يصل التنبيه داخل التطبيق ويحتاج اتصالًا بالإنترنت.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(
                enabled = !sending && recipientId.isNotBlank() && title.isNotBlank() && body.isNotBlank(),
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = {
                    scope.launch {
                        sending = true; error = null
                        try {
                            val r = withContext(Dispatchers.IO) { AppGraph.repo.alertStaff(recipientId, title.trim(), body.trim(), halaqaId) }
                            if (r.optBoolean("ok", false)) { onSent(); close() } else error = r.optString("error", "تعذر الإرسال")
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) { error = e.message ?: "تعذر الإرسال. تحقق من الاتصال وأن تحديث الخادم مطبّق." }
                        finally { sending = false }
                    }
                },
            ) { Text(if (sending) "جارٍ الإرسال…" else "إرسال") }
        },
        dismissButton = { TextButton(onClick = close, enabled = !sending, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
    )
}

/* ============================== a halaqa card (operations view) ============================== */

@Composable
internal fun SupHalaqaCard(
    h: SupHalaqa,
    detailed: Boolean,
    onAlert: () -> Unit,
    onVisit: () -> Unit,
    onOpen: (() -> Unit)? = null,
    offDay: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val halaqaTone = com.mohammedsamir.halaqati.ui.theme.EntityColors.of(com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA)
    val teacherTone = com.mohammedsamir.halaqati.ui.theme.EntityColors.of(com.mohammedsamir.halaqati.ui.theme.Entity.TEACHER)
    val kind = when {
        h.state == TeacherDayState.NOT_STARTED && offDay -> "info"
        h.state == TeacherDayState.NOT_STARTED -> "warning"
        h.state == TeacherDayState.IN_PROGRESS -> "info"
        else -> "success"
    }
    val label = if (h.state == TeacherDayState.NOT_STARTED && offDay) "اليوم إجازة" else h.stateLabel
    val hasTeacher = h.teacherNames.isNotEmpty()
    NourCard(onClick = onOpen, marker = if (kind == "warning") nourTone("warning").first else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.clip(CircleShape).background(teacherTone.container).padding(2.dp)) {
                NourAvatar(if (hasTeacher) h.teacherPhotos.firstOrNull() else h.logo, if (hasTeacher) h.teacherNames.first() else h.name, NourAvatarSize.Small)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Groups, null, tint = halaqaTone.accent, modifier = Modifier.size(16.dp))
                    Text((if (h.isProgram) "برنامج · " else "") + h.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = halaqaTone.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (hasTeacher) h.teacherLine else "بلا محفّظ", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusPill(label, kind = kind)
        }
        if (h.students > 0) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("سُمّع ${h.recited} من ${h.students}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${h.recitedPercent}٪", style = MaterialTheme.typography.labelMedium.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.Bold, color = halaqaTone.accent)
            }
            NourProgress(h.recitedPercent / 100f, color = halaqaTone.accent)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            SupMini(Icons.Default.HowToReg, "حضور", h.present.toString(), cs.primary)
            SupMini(Icons.Default.PersonOff, "غياب", h.absent.toString(), cs.error)
            SupMini(Icons.Default.Groups, "الطلاب", h.students.toString(), cs.onSurfaceVariant)
            if (h.excused > 0) SupMini(Icons.Default.EventBusy, "بعذر", h.excused.toString(), cs.tertiary)
        }
        if (detailed) {
            val last = h.lastVisitDate
            val days = supDaysSince(last)
            Text(
                "جلسات الشهر ${h.monthSessions} · " + if (last == null) "لم تُزر بعد" else "آخر زيارة قبل ${days ?: 0} يومًا" + (h.lastVisitScore?.let { " (${oneDecimal(it)}/5)" } ?: ""),
                style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onAlert, enabled = h.teacherIds.isNotEmpty(), modifier = Modifier.weight(1f).heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.NotificationsActive, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("تنبيه المحفّظ", maxLines = 1, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(onClick = onVisit, modifier = Modifier.weight(1f).heightIn(min = 44.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
                Icon(Icons.Default.Visibility, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("زيارة", maxLines = 1, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Compact stat: icon + number + label on one line. */
@Composable
private fun SupMini(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(value, style = MaterialTheme.typography.labelLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

/* ============================== home: the operations room ============================== */

@Composable
fun SupervisorHomeScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<SupBoard?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var alertFor by remember { mutableStateOf<SupHalaqa?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        try {
            board = withContext(Dispatchers.IO) { loadSupBoard(supervisorOnly = profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR) }
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تحميل لوحة اليوم" }
    }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { reload() }

    val b = board
    val every = b?.halaqas.orEmpty() + b?.programs.orEmpty()
    val started = every.count { it.state != TeacherDayState.NOT_STARTED }
    val total = every.size
    val notStarted = if (b?.offDay == true) emptyList() else every.filter { it.state == TeacherDayState.NOT_STARTED }
    val staleVisits = every.filter { (supDaysSince(it.lastVisitDate) ?: 999) > 14 }.orEmpty()
    val openActions = b?.visits?.count { it.supStr("action_items").isNotBlank() && !it.optBoolean("action_done", false) } ?: 0
    val weekVisits = b?.visits?.count { (supDaysSince(it.optString("visit_date")) ?: 999) <= 7 } ?: 0

    Column {
        AppTopBar("غرفة العمليات", onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending, kicker = "أهلًا ${profile.fullName}")
        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        toast?.let { StateNotice(it, kind = "success", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (b == null) { LoadingSkeleton(rows = 4, modifier = Modifier.padding(12.dp)); return@Column }
        LazyColumn(
            state = androidx.compose.foundation.lazy.rememberLazyListState(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { RoleWelcomeCard(profile.role, photo = profile.avatarPath.takeIf { it.isNotBlank() && it != "null" }, photoName = profile.fullName.ifBlank { "؟" }) }
            item { OrgSummaryCard(profile, navigate) }
            item { com.mohammedsamir.halaqati.ui.components.HomeShortcuts(showPlans = false, navigate = navigate, role = profile.role) }
            item {
                com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(Triple("$started/$total", "حلقات بدأت", false), Triple(weekVisits.toString(), "زياراتي هذا الأسبوع", false), Triple(openActions.toString(), "إجراءات مفتوحة", openActions > 0)))
            }
            if (notStarted.isNotEmpty() || staleVisits.isNotEmpty()) item {
                BorderedCard(accent = MaterialTheme.colorScheme.tertiary) {
                    Text("يحتاج تدخلك", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    val needing = (notStarted + staleVisits).distinctBy { it.id }.take(6)
                    needing.forEach { h ->
                        val waiting = h in notStarted
                        val staleDays = supDaysSince(h.lastVisitDate)
                        val stale = h in staleVisits
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(h.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (waiting) StatusPill("جلسة: بانتظار البدء", kind = "warning")
                                    if (stale) StatusPill("زيارة: " + (staleDays?.let { "منذ $it يومًا" } ?: "لم تُزر بعد"), kind = "info")
                                }
                            }
                            TextButton(onClick = { alertFor = h }, enabled = h.teacherIds.isNotEmpty(), modifier = Modifier.heightIn(min = 44.dp)) { Text("تنبيه") }
                        }
                    }
                }
            }
            item { SupSectionTitle("حلقات اليوم") }
            if (b.halaqas.isEmpty()) item { EmptyState("لا توجد حلقات لعرضها") }
            items(b.halaqas, key = { it.id }) { h ->
                SupHalaqaCard(
                    h, detailed = false,
                    onAlert = { alertFor = h },
                    onVisit = { AddActionBus.request("visit:${h.id}"); navigate("visits") },
                    onOpen = { navigate("halaqas") },
                    offDay = b.offDay,
                )
            }
            if (b.programs.isNotEmpty()) {
                item { SupSectionTitle("البرامج") }
                items(b.programs, key = { "p-" + it.id }) { h ->
                    SupHalaqaCard(
                        h, detailed = false,
                        onAlert = { alertFor = h },
                        onVisit = { AddActionBus.request("visit:${h.id}"); navigate("visits") },
                        offDay = b.offDay,
                    )
                }
            }
            item {
                OutlinedButton(onClick = { navigate("supervision") }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("متابعة المحفّظين") }
            }
        }
    }
    alertFor?.let { h ->
        SupAlertDialog(
            recipients = h.teacherIds.zip(h.teacherNames),
            halaqaId = h.id,
            presetTitle = if (h.state == TeacherDayState.NOT_STARTED) ALERT_TEMPLATES[0].first else "",
            presetBody = if (h.state == TeacherDayState.NOT_STARTED) ALERT_TEMPLATES[0].second else "",
            close = { alertFor = null },
            onSent = { toast = "وصل التنبيه إلى المحفّظ" },
        )
    }
}

/* ============================== halaqas (read-only follow-up) ============================== */

@Composable
fun SupervisorHalaqasScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<SupBoard?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var alertFor by remember { mutableStateOf<SupHalaqa?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }
    suspend fun reload() {
        try { board = withContext(Dispatchers.IO) { loadSupBoard(supervisorOnly = profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR) }; message = null }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { message = e.message ?: "تعذر التحميل" }
    }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { reload() }
    val b = board
    Column {
        AppTopBar("الحلقات", onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending)
        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        toast?.let { StateNotice(it, kind = "success", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (b == null) { LoadingSkeleton(rows = 4, modifier = Modifier.padding(12.dp)); return@Column }
        LazyColumn(
            state = androidx.compose.foundation.lazy.rememberLazyListState(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (b.halaqas.isEmpty() && b.programs.isEmpty()) item { EmptyState("لا توجد حلقات مسندة إليك") }
            else item {
                val all = b.halaqas + b.programs
                com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(
                    Triple(all.size.toString(), "حلقات وبرامج", false),
                    Triple(all.sumOf { it.students }.toString(), "طالب", false),
                    Triple(all.count { it.state == TeacherDayState.NOT_STARTED && !b.offDay }.toString(), "لم تبدأ اليوم", all.any { it.state == TeacherDayState.NOT_STARTED && !b.offDay }),
                ))
            }
            items(b.halaqas + b.programs, key = { (if (it.isProgram) "p-" else "h-") + it.id }) { h ->
                SupHalaqaCard(
                    h, detailed = true,
                    onAlert = { alertFor = h },
                    onVisit = { AddActionBus.request("visit:${h.id}"); navigate("visits") },
                    onOpen = null,
                    offDay = b.offDay,
                )
            }
        }
    }
    alertFor?.let { h ->
        SupAlertDialog(
            recipients = h.teacherIds.zip(h.teacherNames),
            halaqaId = h.id, close = { alertFor = null }, onSent = { toast = "وصل التنبيه إلى المحفّظ" },
        )
    }
}

/* ============================== visits (offline-first, edit, delete with typed confirmation) ============================== */

@Composable
fun SupervisorVisitsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var board by remember { mutableStateOf<SupBoard?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<JSONObject?>(null) }
    var creating by remember { mutableStateOf(false) }
    var presetHalaqa by remember { mutableStateOf("") }
    var deleting by remember { mutableStateOf<JSONObject?>(null) }
    var filter by remember { mutableStateOf("all") }

    suspend fun reload() {
        try { board = withContext(Dispatchers.IO) { loadSupBoard(supervisorOnly = profile.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR) }; message = null }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { message = e.message ?: "تعذر تحميل الزيارات" }
    }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { reload() }
    val localEpoch by AppGraph.repo.localMutationEpoch.collectAsState()
    LaunchedEffect(localEpoch) { if (localEpoch > 0L) reload() }

    // The "+" sheet / a halaqa card asks for a new visit ("visit" or "visit:<halaqaId>").
    val request by AddActionBus.requested.collectAsState()
    LaunchedEffect(request) {
        val r = request
        if (r != null && (r == "visit" || r.startsWith("visit:"))) {
            presetHalaqa = r.substringAfter("visit:", "")
            creating = true
            AddActionBus.consume()
        }
    }

    val b = board
    val halaqaNames = b?.halaqas?.associate { it.id to it.name }.orEmpty()
    val shown = b?.visits?.filter { filter == "all" || it.optString("halaqa_id") == filter }.orEmpty()

    val visitsListState = androidx.compose.foundation.lazy.rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
    Column {
        AppTopBar("زيارات المشرف", onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending)
        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (b == null) { LoadingSkeleton(rows = 3, modifier = Modifier.padding(12.dp)); return@Column }
        LazyColumn(
            state = visitsListState,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if ((b.halaqas + b.programs).size > 1) item {
                HalaqatiLazySelectField(
                    label = "الحلقة", selectedKey = filter,
                    options = listOf("all" to "كل الحلقات") + (b.halaqas + b.programs).map { it.id to it.name },
                    onSelected = { filter = it }, modifier = Modifier.fillMaxWidth(),
                )
            }
            if (shown.isEmpty()) item { EmptyState("لا توجد زيارات مسجلة") }
            else item {
                com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(
                    Triple(shown.size.toString(), "زيارة", false),
                    Triple(shown.count { it.supStr("action_items").isNotBlank() && !it.optBoolean("action_done", false) }.toString(), "إجراءات مفتوحة", shown.any { it.supStr("action_items").isNotBlank() && !it.optBoolean("action_done", false) }),
                    Triple(shown.count { it.optBoolean("_pending", false) }.toString(), "بانتظار الرفع", false),
                ))
            }
            items(shown, key = { it.optString("id") }) { v ->
                val pend = v.optBoolean("_pending", false)
                val hasActions = v.supStr("action_items").isNotBlank()
                val done = v.optBoolean("action_done", false)
                var menuOpen by remember { mutableStateOf(false) }
                val halaqaTone = com.mohammedsamir.halaqati.ui.theme.EntityColors.of(com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA)
                BorderedCard(accent = if (pend) MaterialTheme.colorScheme.tertiary else null, padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Visibility, null, tint = halaqaTone.accent, modifier = Modifier.size(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(halaqaNames[v.optString("halaqa_id")] ?: "حلقة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(v.optString("visit_date"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (pend) StatusPill("بانتظار الرفع", kind = "warning")
                        Box {
                            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, contentDescription = "خيارات الزيارة") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(text = { Text("تعديل") }, onClick = { menuOpen = false; editing = v })
                                if (hasActions && !done) DropdownMenuItem(text = { Text("تم التنفيذ") }, onClick = {
                                    menuOpen = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) { AppGraph.repo.patch("supervisor_visits", "id=eq.${v.optString("id")}", JSONObject().put("action_done", true), dedupe = "visit:${v.optString("id")}:done") }
                                        reload()
                                    }
                                })
                                DropdownMenuItem(text = { Text("حذف", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; deleting = v })
                            }
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("تنظيم" to "organization_score", "تدريس" to "teaching_score", "متابعة" to "followup_score").forEach { (l, k) ->
                            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text("$l ${supScore(v, k)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                            }
                        }
                        if (!v.isNull("teacher_attendance") && v.has("teacher_attendance")) {
                            val here = v.optBoolean("teacher_attendance")
                            StatusPill(if (here) "المحفّظ حاضر" else "المحفّظ غائب", kind = if (here) "success" else "danger")
                        }
                    }
                    if (v.supStr("notes").isNotBlank()) Text(v.supStr("notes"), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (hasActions) Text("المطلوب: ${v.supStr("action_items")}" + if (done) " ✓ نُفّذ" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    CollapsingFab(visitsListState, "تسجيل زيارة جديدة", Icons.Default.Add) { presetHalaqa = if (filter == "all") "" else filter; creating = true }
    }

    if (creating || editing != null) {
        val initial = editing
        SupVisitDialog(
            halaqas = (b?.halaqas.orEmpty() + b?.programs.orEmpty()).map { it.id to it.name },
            initial = initial,
            presetHalaqa = presetHalaqa,
            close = { creating = false; editing = null },
        ) { halaqaId, org, teach, follow, attended, notes, actions ->
            val editId = initial?.optString("id")
            creating = false; editing = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val repo = AppGraph.repo
                        if (editId.isNullOrBlank()) {
                            val uid = repo.store.load()?.userId ?: error("الجلسة غير صالحة")
                            val id = UUID.randomUUID().toString()
                            repo.write(
                                "supervisor_visits",
                                JSONObject()
                                    .put("id", id)
                                    .put("halaqa_id", halaqaId)
                                    .put("supervisor_id", uid)
                                    .put("visit_date", LocalDate.now().toString())
                                    .put("teacher_attendance", attended)
                                    .put("organization_score", org)
                                    .put("teaching_score", teach)
                                    .put("followup_score", follow)
                                    .put("notes", notes.ifBlank { JSONObject.NULL })
                                    .put("action_items", actions.ifBlank { JSONObject.NULL }),
                                dedupe = "visit:$id",
                            )
                        } else {
                            repo.patch(
                                "supervisor_visits", "id=eq.$editId",
                                JSONObject()
                                    .put("halaqa_id", halaqaId)
                                    .put("teacher_attendance", attended)
                                    .put("organization_score", org)
                                    .put("teaching_score", teach)
                                    .put("followup_score", follow)
                                    .put("notes", notes.ifBlank { JSONObject.NULL })
                                    .put("action_items", actions.ifBlank { JSONObject.NULL }),
                                dedupe = "visit:$editId:edit",
                            )
                        }
                    }
                    reload(); onRefresh()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) { message = e.message ?: "تعذر حفظ الزيارة" }
            }
        }
    }
    deleting?.let { v ->
        var typed by remember(v) { mutableStateOf("") }
        HalaqatiAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف الزيارة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("سيُحذف سجل زيارة ${halaqaNames[v.optString("halaqa_id")] ?: "الحلقة"} بتاريخ ${v.optString("visit_date")}. للتأكيد اكتب كلمة «حذف».")
                    OutlinedTextField(typed, { typed = it }, singleLine = true, label = { Text("اكتب: حذف") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    enabled = typed.trim() == "حذف",
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = 48.dp),
                    onClick = {
                        val id = v.optString("id"); deleting = null
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) { AppGraph.repo.delete("supervisor_visits", "id=eq.$id", dedupe = "visit:$id:delete") }
                                reload(); onRefresh()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) { message = e.message ?: "تعذر الحذف" }
                        }
                    },
                ) { Text("حذف الزيارة") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
        )
    }
}

internal fun supScore(v: JSONObject, key: String): String = if (v.isNull(key) || !v.has(key)) "—" else "${v.optInt(key)}/5"

@Composable
private fun SupVisitDialog(
    halaqas: List<Pair<String, String>>,
    initial: JSONObject?,
    presetHalaqa: String,
    close: () -> Unit,
    save: (String, Int, Int, Int, Boolean, String, String) -> Unit,
) {
    var halaqaId by remember { mutableStateOf(initial?.optString("halaqa_id")?.takeIf { it.isNotBlank() } ?: presetHalaqa.ifBlank { halaqas.firstOrNull()?.first.orEmpty() }) }
    var org by remember { mutableIntStateOf(initial?.takeIf { !it.isNull("organization_score") }?.optInt("organization_score", 3) ?: 3) }
    var teach by remember { mutableIntStateOf(initial?.takeIf { !it.isNull("teaching_score") }?.optInt("teaching_score", 3) ?: 3) }
    var follow by remember { mutableIntStateOf(initial?.takeIf { !it.isNull("followup_score") }?.optInt("followup_score", 3) ?: 3) }
    var attended by remember { mutableStateOf(initial?.takeIf { !it.isNull("teacher_attendance") }?.optBoolean("teacher_attendance", true) ?: true) }
    var notes by remember { mutableStateOf(initial?.supStr("notes").orEmpty().takeIf { it != "null" }.orEmpty()) }
    var actions by remember { mutableStateOf(initial?.supStr("action_items").orEmpty().takeIf { it != "null" }.orEmpty()) }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text(if (initial == null) "زيارة إشرافية" else "تعديل الزيارة") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()).imePadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HalaqatiLazySelectField(label = "الحلقة", selectedKey = halaqaId, options = halaqas, onSelected = { halaqaId = it }, modifier = Modifier.fillMaxWidth())
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f)) {
                    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Default.HowToReg, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("المحفّظ حاضر", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = attended, onCheckedChange = { attended = it })
                    }
                }
                SupScorePicker("التنظيم", org) { org = it }
                SupScorePicker("التدريس", teach) { teach = it }
                SupScorePicker("المتابعة", follow) { follow = it }
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(actions, { actions = it }, label = { Text("المطلوب من المحفّظ") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Text("تُحفظ الزيارة على هذا الهاتف فورًا وتُرسل عند توفر الإنترنت.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(enabled = halaqaId.isNotBlank(), modifier = Modifier.heightIn(min = 48.dp), onClick = { save(halaqaId, org, teach, follow, attended, notes.trim(), actions.trim()) }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
    )
}

@Composable
private fun SupScorePicker(label: String, value: Int, onChange: (Int) -> Unit) {
    // RC95: five equal segments (never wraps or clips on 360dp), Cairo digits, the chosen one filled softly.
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text("$value/5", style = MaterialTheme.typography.labelLarge, fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { n ->
                val on = value == n
                Surface(
                    onClick = { onChange(n) },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { selected = on; contentDescription = "$label $n من 5" },
                    shape = RoundedCornerShape(12.dp),
                    color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    contentColor = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    border = BorderStroke(1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(n.toString(), fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.Medium, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
