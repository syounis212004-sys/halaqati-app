@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.HalaqatiAlertDialog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.ApprovalRequest
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.StateNotice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun roleLabelAr(role: String) = when (role) {
    "guardian" -> "ولي أمر"
    "teacher" -> "محفّظ"
    "supervisor" -> "مشرف حلقات"
    "observer" -> "مانح"
    else -> "مستخدم"
}

private fun stageLabelAr(r: ApprovalRequest): String = when {
    r.kind == "link" && r.stage == "teacher" -> "بانتظار موافقة المحفّظ"
    r.kind == "link" && r.stage == "admin" -> "وافق المحفّظ${if (r.teacherName.isNotBlank()) " (${r.teacherName})" else ""} · بانتظار التأكيد"
    else -> "طلب حساب جديد"
}

/**
 * RC27.42: the approval inbox. What each person sees is decided by the server (rc_pending_requests):
 * admin: everything · supervisor: teacher accounts only · teacher: guardians linked to his own students.
 */
@Composable
fun ApprovalsScreen(profile: Profile, unified: Boolean = false, tabs: @Composable () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<ApprovalRequest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<String?>(null) }
    var rejecting by remember { mutableStateOf<ApprovalRequest?>(null) }

    suspend fun reload() {
        loading = true
        try {
            items = withContext(Dispatchers.IO) { AppGraph.repo.pendingApprovalRequests() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            messageIsError = true
            message = if (e.message.orEmpty().contains("rc_pending_requests", true)) "لم يُطبَّق تحديث قاعدة البيانات الخاص بالاعتماد بعد." else (e.message ?: "تعذر تحميل الطلبات")
        }
        loading = false
    }

    fun review(req: ApprovalRequest, approve: Boolean, reason: String = "") {
        scope.launch {
            busyId = req.id
            val error = withContext(Dispatchers.IO) {
                runCatching { AppGraph.repo.reviewApprovalRequest(req.kind, req.id, approve, reason) }
                    .getOrElse { it.message ?: "تعذر تنفيذ العملية" }
            }
            busyId = null
            messageIsError = error != null
            message = error ?: if (approve) "تمت الموافقة على طلب ${req.name}" else "تم رفض طلب ${req.name}"
            reload()
        }
    }

    LaunchedEffect(Unit) { reload() }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = if (unified) "الحسابات والصلاحيات" else "طلبات الاعتماد", onRefresh = { scope.launch { reload() } })
        tabs()
        Text(
            when (profile.role) {
                UserRole.ADMIN -> "طلبات الحسابات الجديدة وطلبات ربط أولياء الأمور بالطلاب."
                UserRole.SUPERVISOR -> "طلبات المحفّظين الجدد. اعتماد المشرفين وأولياء الأمور من مسؤول النظام."
                else -> "طلبات أولياء الأمور المرتبطة بطلاب حلقتك. بعد موافقتك يؤكد مسؤول النظام."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "identity") { StudentIdentitySection() }
            message?.let { text -> item(key = "message") { StateNotice(text, if (messageIsError) "error" else "success") } }
            when {
                loading && items.isEmpty() -> item(key = "loading") { LoadingSkeleton(rows = 3) }
                items.isEmpty() -> item(key = "empty") { EmptyState("لا توجد طلبات بانتظارك الآن") }
                else -> {
                    items(items, key = { it.kind + ":" + it.id }) { req ->
                        ElevatedCard(shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (req.kind == "link") Icons.Default.FamilyRestroom else Icons.Default.PersonAdd, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(req.name.ifBlank { "بدون اسم" }, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            roleLabelAr(req.role) + if (req.email.isNotBlank()) " · ${req.email}" else "",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                if (req.kind == "link") {
                                    Text("الطالب: ${req.student}" + if (req.halaqa.isNotBlank()) " · ${req.halaqa}" else "", style = MaterialTheme.typography.bodyMedium)
                                }
                                if (req.phone.isNotBlank()) {
                                    Text("الجوال: ${req.phone}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                                    Text(stageLabelAr(req), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = { review(req, approve = true) },
                                        enabled = busyId == null,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) {
                                        Text(
                                            when {
                                                req.kind == "link" && profile.role == UserRole.ADMIN -> "تأكيد وربط"
                                                else -> "موافقة"
                                            },
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { rejecting = req },
                                        enabled = busyId == null,
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) { Text("رفض") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    rejecting?.let { req ->
        var reason by remember(req.id) { mutableStateOf("") }
        HalaqatiAlertDialog(
            onDismissRequest = { rejecting = null },
            title = { Text("رفض طلب ${req.name}") },
            text = {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("سبب الرفض (اختياري)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { rejecting = null; review(req, approve = false, reason = reason) }) { Text("تأكيد الرفض") } },
            dismissButton = { TextButton(onClick = { rejecting = null }) { Text("إلغاء") } },
        )
    }
}


/** Admin-only hub. Legacy approval links still open this same hub on its requests tab. */
@Composable
fun AccountsAndApprovalsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, initialApprovals: Boolean = true) {
    var approvals by androidx.compose.runtime.saveable.rememberSaveable(profile.id, initialApprovals) { mutableStateOf(initialApprovals) }
    val tabs: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "طلبات الاعتماد", false to "الحسابات").forEach { (isApprovals, title) ->
                val selected = approvals == isApprovals
                FilterChip(
                    selected = selected, onClick = { approvals = isApprovals },
                    label = {
                        Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isApprovals) Icons.Default.AssignmentTurnedIn else Icons.Default.Groups, contentDescription = null, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                        }
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                )
            }
        }
    }
    if (approvals) ApprovalsScreen(profile, unified = true, tabs = tabs)
    else UsersScreen(pending, onRefresh, openApprovals = { approvals = true }, tabs = tabs)
}

/** RC27.42: dashboard shortcut shown only when something is waiting for this person. */
@Composable
fun ApprovalsInboxCard(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var count by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        count = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.pendingApprovalRequests().size }.getOrDefault(0) }
    }
    if (count > 0) {
        ElevatedCard(modifier.fillMaxWidth().clickable { onOpen() }, shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("طلبات اعتماد بانتظارك", fontWeight = FontWeight.Bold)
                    Text("$count طلب بحاجة إلى مراجعتك", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Default.ChevronLeft, contentDescription = null)
            }
        }
    }
}

/** RC27.42: an approved guardian asks to link another child (teacher then admin must approve). */
@Composable
fun AddChildCard(modifier: Modifier = Modifier, retry: Boolean = false) {
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var nationalId by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    OutlinedButton(onClick = { open = true; result = null }, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Default.PersonAdd, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text(if (retry) "إعادة محاولة ربط الطالب" else "إضافة ابن/ابنة")
    }
    result?.takeIf { !open }?.let { StateNotice(it, if (isError) "error" else "success", Modifier.padding(top = 6.dp)) }

    if (open) {
        HalaqatiAlertDialog(
            onDismissRequest = { if (!busy) open = false },
            title = { Text("ربط ابن/ابنة") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("أدخل رقم هوية الطالب وتاريخ ميلاده. يمر الطلب بموافقة المحفّظ ثم تأكيد مسؤول النظام.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(nationalId, { nationalId = it.filter { c -> c.isDigit() || c in '\u0660'..'\u0669' } }, label = { Text("رقم هوية الطالب") }, singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    BirthDateField(label = "تاريخ ميلاد الطالب", value = birth, onChange = { birth = it })
                    result?.let { StateNotice(it, if (isError) "error" else "success") }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy && nationalId.isNotBlank() && birth.isNotBlank(),
                    onClick = {
                        scope.launch {
                            busy = true
                            val date = normalizeBirthDate(birth)
                            val err = if (date == null) "تاريخ الميلاد غير صحيح، اكتبه بصيغة 2015-03-27" else withContext(Dispatchers.IO) {
                                runCatching { AppGraph.repo.requestGuardianLink(nationalId.trim(), date) }
                                    .getOrElse { it.message ?: "تعذر إرسال الطلب" }
                            }
                            busy = false
                            isError = err != null
                            result = err ?: "تم إرسال الطلب وبانتظار الاعتماد"
                            if (err == null) { nationalId = ""; birth = ""; open = false }
                        }
                    },
                ) { Text(if (busy) "جارٍ الإرسال…" else "إرسال الطلب") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("إلغاء") } },
        )
    }
}
