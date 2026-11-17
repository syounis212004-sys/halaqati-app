package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.StateNotice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

private fun JSONArray.bcRows(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun bcWhen(iso: String): String = com.mohammedsamir.halaqati.ui.components.GazaTime.shortDateTime(iso)

private fun bcRoleAr(role: String): String = when (role) {
    "guardian" -> "ولي أمر"
    "teacher" -> "معلم"
    "supervisor" -> "مشرف"
    "observer" -> "مانح"
    "admin" -> "مدير"
    else -> role
}

private fun bcAudienceAr(a: String): String = when (a) {
    "guardians" -> "أولياء الأمور"
    "teachers" -> "المعلمون"
    "all" -> "الجميع"
    "donors" -> "المانحون"
    "supervisors" -> "المشرفون"
    else -> ""
}

/**
 * RC27.49: notification center for admin / supervisor.
 * Compose a notification for everyone, a halaqa, a category or one student, then see who read it.
 * Egress: nothing polls. The sent list is one aggregated server query (<= 30 rows) loaded on open/refresh,
 * and the per-person read list is fetched only when one broadcast is opened.
 */
@Composable
fun BroadcastCenter(profile: Profile, onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var sentVersion by remember { mutableIntStateOf(0) }
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
            }
            Text("مركز الإشعارات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("إرسال جديد") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("المُرسَل ومن قرأ") })
        }
        if (tab == 0) BroadcastComposer(profile) { sentVersion++; tab = 1 }
        else BroadcastSentList(sentVersion)
    }
}

@Composable
private fun BroadcastComposer(profile: Profile, onSent: () -> Unit) {
    val scope = rememberCoroutineScope()
    val isAdmin = profile.role == UserRole.ADMIN
    var target by remember { mutableStateOf(if (isAdmin) "all" else "halaqa") }
    var targetId by remember { mutableStateOf<String?>(null) }
    var targetLabel by remember { mutableStateOf("") }
    var audience by remember { mutableStateOf("guardians") }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var halaqas by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var categories by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var students by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var optionsError by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var confirmCount by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                val hq = AppGraph.repo.table("halaqas", "select=id,name&order=name.asc&limit=300").bcRows()
                val cats = AppGraph.repo.table("student_categories", "select=id,halaqa_id,name&order=name.asc&limit=500").bcRows()
                val hqNames = hq.associate { it.optString("id") to it.optString("name") }
                halaqas = hq.map { it.optString("id") to it.optString("name") }
                categories = cats.map { c ->
                    val h = hqNames[c.optString("halaqa_id")].orEmpty()
                    c.optString("id") to (c.optString("name") + if (h.isNotBlank()) " — $h" else "")
                }
                students = AppGraph.repo.students().filter { it.status != "withdrawn" }
                    .sortedBy { it.fullName }.map { it.id to it.fullName }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { optionsError = e.message ?: "تعذر تحميل القوائم" }
    }

    fun pickTarget(t: String) {
        target = t; targetId = null; targetLabel = ""; query = ""
        // Donors / supervisors are not tied to a halaqa: choosing a narrower target goes back to a normal audience.
        if (t != "all" && (audience == "donors" || audience == "supervisors")) audience = "guardians"
    }

    val scopeLabel = when (target) {
        "all" -> "كل الحلقات"
        "halaqa" -> "حلقة: $targetLabel"
        "category" -> "فئة: $targetLabel"
        else -> "طالب: $targetLabel"
    }
    val options = when (target) {
        "halaqa" -> halaqas
        "category" -> categories
        "student" -> students
        else -> emptyList()
    }.filter { query.isBlank() || it.second.contains(query.trim()) }.take(12)
    val ready = title.isNotBlank() && body.isNotBlank() && (target == "all" || targetId != null) && !sending

    fun request(dry: Boolean, key: String) = AppGraph.repo.sendBroadcast(
        scope = target, targetId = targetId, audience = audience,
        title = title.trim(), body = body.trim(), label = scopeLabel, sourceKey = key, dryRun = dry,
    )

    fun preview() {
        scope.launch {
            sending = true; message = null
            try {
                val r = withContext(Dispatchers.IO) { request(true, "bc:preview") }
                if (r.has("error")) { message = r.optString("error"); messageIsError = true }
                else if (r.optInt("recipients") == 0) { message = "لا يوجد مستلمون لهذا الاختيار (تأكد من ربط أولياء الأمور بالطلاب)"; messageIsError = true }
                else confirmCount = r.optInt("recipients")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { message = e.message ?: "تعذر التحقق من المستلمين"; messageIsError = true }
            finally { sending = false }
        }
    }

    fun send() {
        scope.launch {
            sending = true; message = null; confirmCount = null
            try {
                val r = withContext(Dispatchers.IO) { request(false, "bc:" + UUID.randomUUID()) }
                if (r.has("error")) { message = r.optString("error"); messageIsError = true }
                else {
                    title = ""; body = ""
                    onSent()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) { message = e.message ?: "تعذر الإرسال"; messageIsError = true }
            finally { sending = false }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("إلى من؟", fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (isAdmin) ProFilterChip(target == "all", { pickTarget("all") }, "الجميع")
            ProFilterChip(target == "halaqa", { pickTarget("halaqa") }, "حلقة")
            ProFilterChip(target == "category", { pickTarget("category") }, "فئة")
            ProFilterChip(target == "student", { pickTarget("student") }, "طالب محدد")
        }
        if (target != "all") {
            if (targetId != null) {
                Text("المختار: $targetLabel", color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"), fontWeight = FontWeight.Bold)
            }
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text(if (target == "student") "ابحث باسم الطالب" else "ابحث بالاسم") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            optionsError?.let { StateNotice(it, kind = "error") }
            options.forEach { (id, name) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable { targetId = id; targetLabel = name.substringBefore(" — ") }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = targetId == id, onClick = { targetId = id; targetLabel = name.substringBefore(" — ") })
                    Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (options.size == 12) Text("اكتب في البحث لتضييق النتائج", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text("لمن يصل؟", fontWeight = FontWeight.Bold)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ProFilterChip(audience == "guardians", { audience = "guardians" }, "أولياء الأمور")
            ProFilterChip(audience == "teachers", { audience = "teachers" }, "المعلمون")
            ProFilterChip(audience == "all", { audience = "all" }, "الجميع (أولياء + معلمون + مشرفون)")
            if (isAdmin) {
                ProFilterChip(audience == "donors", { audience = "donors"; pickTarget("all"); audience = "donors" }, "المانحون")
                ProFilterChip(audience == "supervisors", { audience = "supervisors"; pickTarget("all"); audience = "supervisors" }, "المشرفون")
            }
        }

        OutlinedTextField(
            value = title, onValueChange = { if (it.length <= 80) title = it },
            label = { Text("العنوان") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = body, onValueChange = { if (it.length <= 500) body = it },
            label = { Text("نص الإشعار") }, minLines = 4, maxLines = 8,
            supportingText = { Text("${body.length}/500") },
            modifier = Modifier.fillMaxWidth(),
        )
        message?.let { StateNotice(it, kind = if (messageIsError) "error" else "info") }
        Button(onClick = { preview() }, enabled = ready, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(if (sending) "جارٍ التحقق…" else "متابعة")
        }
        Text(
            "تصل للمستلم داخل التطبيق دائمًا، وكإشعار على الهاتف إذا فتح التطبيق المحدّث مرة وفعّل الإشعارات.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    confirmCount?.let { n ->
        HalaqatiAlertDialog(
            onDismissRequest = { if (!sending) confirmCount = null },
            title = { Text("تأكيد الإرسال") },
            text = { Text("سيصل الإشعار إلى $n شخصًا ($scopeLabel — ${bcAudienceAr(audience)}). لا يمكن التراجع بعد الإرسال.") },
            confirmButton = { Button(onClick = { send() }, enabled = !sending, modifier = Modifier.heightIn(min = 48.dp)) { Text("إرسال الآن") } },
            dismissButton = { TextButton(onClick = { confirmCount = null }, enabled = !sending, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
        )
    }
}

@Composable
private fun BroadcastSentList(version: Int) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var opened by remember { mutableStateOf<JSONObject?>(null) }

    fun load() {
        scope.launch {
            loading = true
            try {
                rows = withContext(Dispatchers.IO) { AppGraph.repo.broadcastStats() }
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = (e.message ?: "تعذر تحميل المُرسَل") +
                    if ((e.message ?: "").contains("rc_broadcast_stats")) " — شغّل ملف SQL الخاص بمركز الإشعارات" else ""
            } finally { loading = false }
        }
    }
    LaunchedEffect(version) { load() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("آخر الإرسالات", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = { load() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Refresh, contentDescription = "تحديث") }
        }
        error?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp)) }
        if (loading && rows.isEmpty()) LoadingSkeleton(rows = 3, modifier = Modifier.padding(12.dp))
        if (!loading && rows.isEmpty() && error == null) EmptyState("لم ترسل أي إشعار من هنا بعد")
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(rows, key = { it.optString("source_key") }) { b ->
                val total = b.optInt("total")
                val read = b.optInt("read")
                ElevatedCard(Modifier.fillMaxWidth().clickable { opened = b }, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(b.optString("title"), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(bcWhen(b.optString("created_at")), b.optString("label"), bcAudienceAr(b.optString("audience"))).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        LinearProgressIndicator(drawStopIndicator = {}, progress = { if (total == 0) 0f else read.toFloat() / total }, modifier = Modifier.fillMaxWidth())
                        Text("قرأه $read من $total · وصل كإشعار هاتف: ${b.optInt("push_sent")}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    opened?.let { b -> BroadcastDetailDialog(b) { opened = null } }
}

@Composable
private fun BroadcastDetailDialog(b: JSONObject, close: () -> Unit) {
    var people by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showRead by remember { mutableStateOf(false) }

    LaunchedEffect(b.optString("source_key")) {
        try {
            val r = withContext(Dispatchers.IO) { AppGraph.repo.broadcastDetail(b.optString("source_key")) }
            if (r.optBoolean("ok", false)) people = (r.optJSONArray("recipients") ?: JSONArray()).bcRows()
            else error = r.optString("error", "تعذر التحميل")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { error = e.message ?: "تعذر التحميل" }
        finally { loading = false }
    }
    val unreadList = people.filter { it.isNull("read_at") || it.optString("read_at").isBlank() }
    val readList = people.filter { !(it.isNull("read_at") || it.optString("read_at").isBlank()) }
    val shown = if (showRead) readList else unreadList

    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text(b.optString("title"), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(b.optString("body"), style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                error?.let { StateNotice(it, kind = "error") }
                if (loading) LoadingSkeleton(rows = 3)
                else if (error == null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ProFilterChip(!showRead, { showRead = false }, "لم يقرأ (${unreadList.size})")
                        ProFilterChip(showRead, { showRead = true }, "قرأ (${readList.size})")
                    }
                    if (shown.isEmpty()) Text(if (showRead) "لم يقرأ أحد بعد" else "الجميع قرأ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(shown) { p ->
                            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(p.optString("name"), fontWeight = FontWeight.Bold)
                                val sub = buildList {
                                    add(bcRoleAr(p.optString("role")))
                                    p.optString("children").takeIf { it.isNotBlank() && !p.isNull("children") }?.let { add("أبناء: $it") }
                                    if (showRead) add("قرأ ${bcWhen(p.optString("read_at"))}")
                                }.joinToString(" · ")
                                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp)) { Text("إغلاق") } },
    )
}
