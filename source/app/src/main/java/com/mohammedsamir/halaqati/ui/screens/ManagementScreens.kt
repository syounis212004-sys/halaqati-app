@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import com.mohammedsamir.halaqati.ui.components.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.PlanSnapshot
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.Student
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.quran.RecitationParityRules
import com.mohammedsamir.halaqati.ui.components.*
import com.mohammedsamir.halaqati.ui.sessions.SessionWorkflowRules
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens
import com.mohammedsamir.halaqati.ui.theme.Entity
import com.mohammedsamir.halaqati.ui.theme.EntityColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private fun JSONArray.rows(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun roleCanManage(role: UserRole) = role in setOf(UserRole.ADMIN, UserRole.SUPERVISOR, UserRole.TEACHER)
private fun roleCanAdmin(role: UserRole) = role in setOf(UserRole.ADMIN, UserRole.SUPERVISOR)


@Composable
fun HalaqasManagementScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var categories by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var teachers by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var supervisors by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var teacherLinks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var supervisorLinks by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var memberships by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var editHalaqa by remember { mutableStateOf<JSONObject?>(null) }
    var logoHalaqa by remember { mutableStateOf<JSONObject?>(null) }
    var addHalaqa by remember { mutableStateOf(false) }
    var categoryHalaqa by remember { mutableStateOf<JSONObject?>(null) }
    var staffHalaqa by remember { mutableStateOf<JSONObject?>(null) }
    var membersHalaqa by remember { mutableStateOf<JSONObject?>(null) }
    var deleteHalaqa by remember { mutableStateOf<JSONObject?>(null) }
    var halaqaMenuId by remember { mutableStateOf<String?>(null) }
    var halaqaSaving by remember { mutableStateOf(false) }
    var halaqaDialogError by remember { mutableStateOf<String?>(null) }
    var showArchivedHalaqas by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) {
                if (AppGraph.repo.online()) AppGraph.repo.syncNow()
                val h = AppGraph.repo.table("halaqas", "select=*&order=created_at.asc&limit=200", forceRefresh = true).rows()
                val c = AppGraph.repo.table("student_categories", "select=*&order=name.asc&limit=500", forceRefresh = true).rows()
                val s = AppGraph.repo.students()
                val p = if (profile.role in setOf(UserRole.ADMIN, UserRole.SUPERVISOR)) {
                    AppGraph.repo.table("profiles", "select=id,full_name,email,role,approval_status,active&approval_status=eq.approved&active=eq.true&limit=500", forceRefresh = true).rows()
                } else emptyList()
                val tl = AppGraph.repo.table("halaqa_teachers", "select=*&limit=1000", forceRefresh = true).rows()
                val sl = AppGraph.repo.table("halaqa_supervisors", "select=*&limit=1000", forceRefresh = true).rows()
                val ml = AppGraph.repo.studentHalaqaMemberships(forceRefresh = true).rows()
                listOf(h, c, p, tl, sl, ml) to s
            }
            halaqas = result.first[0]
            categories = result.first[1]
            val profiles = result.first[2]
            teachers = profiles.filter { it.optString("role") == "teacher" }
            supervisors = profiles.filter { it.optString("role") == "supervisor" }
            teacherLinks = result.first[3]
            supervisorLinks = result.first[4]
            memberships = result.first[5]
            students = result.second
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر تحميل بيانات الحلقات"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(Unit) { reload() }
    val halaqaListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val expandedHalaqas = remember { mutableStateListOf<String>() }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = if (profile.role == UserRole.ADMIN) "إدارة الحلقات والفئات" else "الحلقات والفئات",
            onRefresh = { scope.launch { reload() }; onRefresh() },
            pending = pending,
        )
        message?.let { StateNotice(it, kind = if (it.startsWith("حُفظ") || it.startsWith("أُزيل") || it.startsWith("تمت") || it.startsWith("نُقل")) "success" else "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (loading && halaqas.isEmpty()) LoadingSkeleton(rows = 3, modifier = Modifier.padding(12.dp))

        if (profile.role == UserRole.ADMIN && halaqas.any { it.has("archived_at") && !it.isNull("archived_at") }) {
            ResponsiveChipGrid(
                items = listOf(false to "الحلقات الحالية", true to "الأرشيف"),
                minItemWidth = 130.dp,
                maxColumns = 2,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { pair, itemModifier ->
                ProFilterChip(showArchivedHalaqas == pair.first, { showArchivedHalaqas = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
            }
        }
        val visibleHalaqas = halaqas.filter { h ->
            val archived = h.has("archived_at") && !h.isNull("archived_at") && h.optString("archived_at").isNotBlank()
            if (showArchivedHalaqas) archived else !archived
        }
        if (!loading && visibleHalaqas.isEmpty()) EmptyState(if (showArchivedHalaqas) "لا توجد حلقات مؤرشفة" else "لا توجد حلقات بعد")
        LazyColumn(
        state = halaqaListState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // RC98: the numbers scroll with the list (they were fixed above it).
            if (visibleHalaqas.isNotEmpty() && !showArchivedHalaqas) item(key = "halaqa-strip", contentType = "strip") {
                val noTeacher = visibleHalaqas.count { h -> teacherLinks.none { it.optString("halaqa_id") == h.optString("id") } }
            com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(
                Triple(visibleHalaqas.size.toString(), "حلقة", false),
                Triple(students.count { it.status == "active" }.toString(), "طالب نشط", false),
                Triple(noTeacher.toString(), "بلا محفّظ", noTeacher > 0),
            ))
            }
            items(visibleHalaqas, key = { it.optString("id") }) { h ->
                val hid = h.optString("id")
                val primaryStudents = students.filter { it.halaqaId == hid && it.status == "active" }
                val additionalIds = memberships.filter { it.optString("halaqa_id") == hid && it.optString("status", "active") == "active" }
                    .map { it.optString("student_id") }.toSet()
                val hStudents = (primaryStudents + students.filter { it.id in additionalIds && it.status == "active" }).distinctBy { it.id }
                val hCats = categories.filter { it.optString("halaqa_id") == hid && it.optBoolean("active", true) }
                val tIds = teacherLinks.filter { it.optString("halaqa_id") == hid }.map { it.optString("teacher_id") }.toSet()
                val sIds = supervisorLinks.filter { it.optString("halaqa_id") == hid }.map { it.optString("supervisor_id") }.toSet()
                val tNames = teachers.filter { it.optString("id") in tIds }.joinToString("، ") { it.optString("full_name", "محفّظ") }
                val sNames = supervisors.filter { it.optString("id") in sIds }.joinToString("، ") { it.optString("full_name", "مشرف") }
                val hType = h.mgmtStr("halaqa_type").ifBlank { "regular" }
                val headerPair = when (hType) {
                    "training" -> HalaqatiGradients.Blue
                    "course" -> HalaqatiGradients.Orange
                    "tajweed", "tilawah" -> HalaqatiGradients.Teal
                    "other" -> HalaqatiGradients.Violet
                    else -> HalaqatiGradients.Deep
                }
                val expanded = hid in expandedHalaqas
                val isArchived = h.has("archived_at") && !h.isNull("archived_at") && h.optString("archived_at").isNotBlank()
                val typeLabel = when (hType) {
                    "tajweed", "tilawah" -> com.mohammedsamir.halaqati.plans.ProgramKindRules.TILAWAH_TAJWEED_LABEL
                    "training" -> "دورة تأهيلية"
                    "course" -> "دورة"
                    "other" -> "برنامج"
                    else -> "حلقة أساسية"
                }
                val programMeta = if (hType != "regular") listOf(programSpecAr(h.mgmtStr("program_specialization")), programLevelAr(h.mgmtStr("program_level")), programAssessAr(h.mgmtStr("assessment_type"))).filter { it.isNotBlank() }.joinToString(" · ") else ""
                HalaqaManagementCard(
                    data = HalaqaCardData(
                        name = h.optString("name", "حلقة"),
                        typeLabel = typeLabel,
                        statusLabel = if (isArchived) "مؤرشفة" else if (h.optBoolean("active", true)) "نشطة" else "موقوفة",
                        statusActive = h.optBoolean("active", true) && !isArchived,
                        place = h.mgmtStr("location"),
                        schedule = h.mgmtStr("schedule_text"),
                        programMeta = programMeta,
                        studentCount = hStudents.size,
                        categoryCount = hCats.size,
                        supervisorCount = sIds.size,
                        supervisors = sNames,
                        teachers = tNames.ifBlank { if (profile.role == UserRole.TEACHER) "حسب الربط" else "" },
                        gradient = headerPair,
                    ),
                    expanded = expanded,
                    onToggle = { if (expanded) expandedHalaqas.remove(hid) else expandedHalaqas.add(hid) },
                    logo = {
                        // RC86: the halaqa logo, with a visible "add / change logo" badge for the administrator.
                        Box(Modifier.size(52.dp)) {
                            Box(Modifier.align(Alignment.TopStart).background(Color.White, RoundedCornerShape(16.dp)).padding(2.dp)) {
                                OrgLogo(h.mgmtStr("logo_path").ifBlank { null }, Icons.Default.Groups, headerPair, tile = 44.dp, glyph = 24.dp)
                            }
                            if (profile.role == UserRole.ADMIN) {
                                Surface(
                                    onClick = { logoHalaqa = h },
                                    modifier = Modifier.align(Alignment.BottomEnd).size(22.dp),
                                    shape = androidx.compose.foundation.shape.CircleShape,
                                    color = MaterialTheme.colorScheme.tertiary, contentColor = Color.White, shadowElevation = 3.dp,
                                ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Default.AddPhotoAlternate, contentDescription = "إضافة أو تغيير شعار الحلقة", modifier = Modifier.size(14.dp)) } }
                            }
                        }
                    },
                    menu = {
                    if (profile.role == UserRole.ADMIN) {
                        Box {
                            IconButton(
                                onClick = { halaqaMenuId = hid },
                                modifier = Modifier.size(HalaqatiUiTokens.TouchTarget),
                            ) { Icon(Icons.Default.MoreVert, contentDescription = "إجراءات الحلقة", tint = Color.White) }
                            DropdownMenu(
                                expanded = halaqaMenuId == hid,
                                onDismissRequest = { halaqaMenuId = null },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("تعديل الحلقة") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = { halaqaMenuId = null; halaqaDialogError = null; editHalaqa = h },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (h.mgmtStr("logo_path").isBlank()) "إضافة شعار للحلقة" else "تغيير شعار الحلقة") },
                                    leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                                    onClick = { halaqaMenuId = null; logoHalaqa = h },
                                )
                                DropdownMenuItem(
                                    text = { Text("تعيين الطاقم") },
                                    leadingIcon = { Icon(Icons.Default.GroupAdd, contentDescription = null) },
                                    onClick = { halaqaMenuId = null; staffHalaqa = h },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (h.optBoolean("active", true)) "إيقاف الحلقة" else "تفعيل الحلقة") },
                                    leadingIcon = { Icon(if (h.optBoolean("active", true)) Icons.Default.PauseCircle else Icons.Default.PlayCircle, contentDescription = null) },
                                    onClick = {
                                        halaqaMenuId = null
                                        scope.launch {
                                            try {
                                                withContext(Dispatchers.IO) {
                                                    AppGraph.repo.patchConfirmed(
                                                        "halaqas",
                                                        "id=eq.$hid",
                                                        JSONObject().put("active", !h.optBoolean("active", true)).put("updated_at", Instant.now().toString()),
                                                    )
                                                }
                                                reload(); onRefresh()
                                            } catch (e: kotlinx.coroutines.CancellationException) {
                                                throw e
                                            } catch (e: Exception) { message = e.message ?: "تعذر تحديث حالة الحلقة" }
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(if (h.has("archived_at") && !h.isNull("archived_at") && h.optString("archived_at").isNotBlank()) "استعادة الحلقة من الأرشيف" else "أرشفة الحلقة") },
                                    leadingIcon = { Icon(if (h.has("archived_at") && !h.isNull("archived_at") && h.optString("archived_at").isNotBlank()) Icons.Default.Restore else Icons.Default.Archive, contentDescription = null) },
                                    onClick = {
                                        halaqaMenuId = null
                                        scope.launch {
                                            try {
                                                val archived = h.has("archived_at") && !h.isNull("archived_at") && h.optString("archived_at").isNotBlank()
                                                withContext(Dispatchers.IO) {
                                                    AppGraph.repo.patchConfirmed(
                                                        "halaqas",
                                                        "id=eq.$hid",
                                                        JSONObject()
                                                            .put("active", archived)
                                                            .put("archived_at", if (archived) JSONObject.NULL else Instant.now().toString())
                                                            .put("updated_at", Instant.now().toString()),
                                                    )
                                                }
                                                message = if (archived) "تمت استعادة الحلقة" else "تمت أرشفة الحلقة دون حذف تاريخها"
                                                reload(); onRefresh()
                                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                            catch (e: Exception) { message = e.message ?: "تعذر تحديث أرشيف الحلقة" }
                                        }
                                    },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("خيارات متقدمة: حذف نهائي", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { halaqaMenuId = null; deleteHalaqa = h },
                                )
                            }
                        }
                    }
                    },
                    notice = {
                        // RC86: a program is never closed automatically; the administrator is reminded a week before and after its end date.
                        val programEnds = if (hType != "regular") h.mgmtStr("ends_on").takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } else null
                        if (programEnds != null) {
                            val daysLeft = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), programEnds)
                            if (daysLeft <= 7) StateNotice(
                                when {
                                    daysLeft < 0 -> "انتهى تاريخ هذا البرنامج منذ ${-daysLeft} يومًا. أنهِه بالأرشفة أو مدّد تاريخه."
                                    daysLeft == 0L -> "ينتهي هذا البرنامج اليوم."
                                    else -> "ينتهي هذا البرنامج بعد $daysLeft أيام."
                                },
                                kind = if (daysLeft < 0) "warning" else "info",
                            )
                        }
                    },
                    details = {
                        if (additionalIds.isNotEmpty()) {
                            Text("الانتساب: أساسي ${primaryStudents.size} · إضافي ${additionalIds.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "المسارات: حفظ عام ${hStudents.count { it.track != "tathbit" }} · تثبيت ${hStudents.count { it.track == "tathbit" }}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    actions = {
                        if (h.optString("halaqa_type", "regular") != "regular" || h.optBoolean("allow_secondary_members", true)) {
                            FilledTonalButton(
                                onClick = { membersHalaqa = h },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (h.optString("halaqa_type", "regular") == "regular") "الطلاب المنتسبون" else "إدارة المشاركين في البرنامج")
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { categoryHalaqa = h },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) { Text("الفئات") }
                            if (profile.role == UserRole.ADMIN) {
                                FilledTonalButton(
                                    onClick = { halaqaDialogError = null; editHalaqa = h },
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("تعديل")
                                }
                            }
                        }
                    },
                )
            }
        }
    }
    if (profile.role == UserRole.ADMIN) {
        CollapsingFab(halaqaListState, "إضافة حلقة", Icons.Default.Add) { halaqaDialogError = null; addHalaqa = true }
    }
    }

    if (addHalaqa || editHalaqa != null) {
        HalaqaDialog(
            current = editHalaqa,
            saving = halaqaSaving,
            errorText = halaqaDialogError,
            close = { if (!halaqaSaving) { addHalaqa = false; editHalaqa = null; halaqaDialogError = null } },
            save = { name, location, schedule, description, halaqaType, programKind, specialization, programLevel, assessmentType, affectsPrimaryPlan, startsOn, endsOn, logo ->
                scope.launch {
                    halaqaSaving = true
                    halaqaDialogError = null
                    try {
                        withContext(Dispatchers.IO) {
                            val obj = JSONObject()
                                .put("name", name)
                                .put("location", location.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                                .put("schedule_text", schedule.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                                .put("description", description.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                                .put("halaqa_type", halaqaType)
                                .put("program_kind", programKind.takeIf { halaqaType != "regular" && it.isNotBlank() } ?: JSONObject.NULL)
                                .put("program_specialization", specialization.takeIf { halaqaType != "regular" } ?: JSONObject.NULL)
                                .put("program_level", programLevel.takeIf { halaqaType != "regular" } ?: JSONObject.NULL)
                                .put("assessment_type", assessmentType.takeIf { halaqaType != "regular" } ?: JSONObject.NULL)
                                .put("affects_primary_plan", halaqaType != "regular" && affectsPrimaryPlan)
                                .put("starts_on", startsOn?.toString() ?: JSONObject.NULL)
                                .put("ends_on", endsOn?.toString() ?: JSONObject.NULL)
                                .put("allow_secondary_members", true)
                                .put("active", true)
                            val oldLogo = editHalaqa?.mgmtStr("logo_path")?.ifBlank { null }
                            if (logo != oldLogo) obj.put("logo_path", logo ?: JSONObject.NULL)
                            val id = editHalaqa?.optString("id")
                            if (id.isNullOrBlank()) AppGraph.repo.writeConfirmed("halaqas", obj) 
                            else AppGraph.repo.patchConfirmed("halaqas", "id=eq.$id", obj.put("updated_at", Instant.now().toString()))
                        }
                        addHalaqa = false; editHalaqa = null
                        reload(); onRefresh()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        val text = e.message ?: "تعذر حفظ الحلقة"
                        message = text
                        halaqaDialogError = text
                    } finally {
                        halaqaSaving = false
                    }
                }
            },
        )
    }
    logoHalaqa?.let { h ->
        var pending by remember(h) { mutableStateOf(h.mgmtStr("logo_path").ifBlank { null }) }
        HalaqatiAlertDialog(
            onDismissRequest = { logoHalaqa = null },
            title = { Text("شعار ${h.optString("name", "الحلقة")}") },
            text = { LogoPicker(pending, { pending = it }, Icons.Default.Groups) },
            confirmButton = {
                Button(onClick = {
                    val hid = h.optString("id"); val value = pending
                    logoHalaqa = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { AppGraph.repo.patchConfirmed("halaqas", "id=eq.$hid", JSONObject().put("logo_path", value ?: JSONObject.NULL).put("updated_at", Instant.now().toString())) }
                            message = if (value == null) "أُزيل شعار الحلقة" else "حُفظ شعار الحلقة"
                            reload(); onRefresh()
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (e: Exception) { message = e.message ?: "تعذر حفظ الشعار" }
                    }
                }) { Text("حفظ") }
            },
            dismissButton = { TextButton(onClick = { logoHalaqa = null }) { Text("إلغاء") } },
        )
    }
    categoryHalaqa?.let { h ->
        CategoriesDialog(
            halaqa = h,
            categories = categories.filter { it.optString("halaqa_id") == h.optString("id") },
            students = students,
            canEdit = roleCanManage(profile.role),
            close = { categoryHalaqa = null },
            changed = { scope.launch { reload() }; onRefresh() },
        )
    }

    membersHalaqa?.let { h ->
        HalaqaMembersDialog(
            halaqa = h,
            students = students,
            memberships = memberships.filter { it.optString("halaqa_id") == h.optString("id") },
            canEdit = roleCanManage(profile.role),
            close = { membersHalaqa = null },
            changed = { scope.launch { reload() }; onRefresh() },
        )
    }

    // RC86: a teacher works in ONE regular halaqa (any number of programs). Adding him to another regular halaqa moves him; ask first.
    var pendingMove by remember { mutableStateOf<Triple<JSONObject, Set<String>, Set<String>>?>(null) }
    fun saveStaff(h: JSONObject, wantedTeachers: Set<String>, wantedSupervisors: Set<String>) {
        scope.launch {
            try {
                val hid = h.optString("id")
                withContext(Dispatchers.IO) {
                    val oldTeachers = teacherLinks.filter { it.optString("halaqa_id") == hid }.map { it.optString("teacher_id") }.toSet()
                    val oldSupervisors = supervisorLinks.filter { it.optString("halaqa_id") == hid }.map { it.optString("supervisor_id") }.toSet()
                    oldTeachers.filter { it !in wantedTeachers }.forEach { teacherId ->
                        AppGraph.repo.deleteConfirmed("halaqa_teachers", "halaqa_id=eq.$hid&teacher_id=eq.$teacherId")
                    }
                    wantedTeachers.filter { it !in oldTeachers }.forEachIndexed { index, teacherId ->
                        AppGraph.repo.writeConfirmed(
                            "halaqa_teachers",
                            JSONObject().put("halaqa_id", hid).put("teacher_id", teacherId).put("is_primary", index == 0 && oldTeachers.isEmpty()),
                            onConflict = "halaqa_id,teacher_id",
                        )
                    }
                    oldSupervisors.filter { it !in wantedSupervisors }.forEach { supervisorId ->
                        AppGraph.repo.deleteConfirmed("halaqa_supervisors", "halaqa_id=eq.$hid&supervisor_id=eq.$supervisorId")
                    }
                    wantedSupervisors.filter { it !in oldSupervisors }.forEach { supervisorId ->
                        val obj = JSONObject().put("halaqa_id", hid).put("supervisor_id", supervisorId)
                        AppGraph.repo.store.load()?.userId?.let { obj.put("created_by", it) }
                        AppGraph.repo.writeConfirmed(
                            "halaqa_supervisors",
                            obj,
                            onConflict = "halaqa_id,supervisor_id",
                        )
                    }
                    AppGraph.repo.patchConfirmed(
                        "halaqas",
                        "id=eq.$hid",
                        JSONObject().put("supervisor_id", wantedSupervisors.firstOrNull() ?: JSONObject.NULL).put("updated_at", Instant.now().toString()),
                    )
                }
                staffHalaqa = null
                reload(); onRefresh()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) { message = e.message ?: "تعذر تحديث طاقم الحلقة" }
        }
    }
    staffHalaqa?.let { h ->
        HalaqaStaffDialog(
            halaqa = h,
            teachers = teachers,
            supervisors = supervisors,
            currentTeacherIds = teacherLinks.filter { it.optString("halaqa_id") == h.optString("id") }.map { it.optString("teacher_id") }.toSet(),
            currentSupervisorIds = supervisorLinks.filter { it.optString("halaqa_id") == h.optString("id") }.map { it.optString("supervisor_id") }.toSet(),
            close = { staffHalaqa = null },
            save = { wantedTeachers, wantedSupervisors ->
                val hid = h.optString("id")
                val isRegular = h.mgmtStr("halaqa_type").ifBlank { "regular" } == "regular"
                val current = teacherLinks.filter { it.optString("halaqa_id") == hid }.map { it.optString("teacher_id") }.toSet()
                val moving = if (!isRegular) emptyList() else wantedTeachers.filter { it !in current }.filter { tid ->
                    teacherLinks.any { l -> l.optString("teacher_id") == tid && l.optString("halaqa_id") != hid &&
                        halaqas.firstOrNull { it.optString("id") == l.optString("halaqa_id") }?.mgmtStr("halaqa_type").orEmpty().ifBlank { "regular" } == "regular" }
                }
                if (moving.isEmpty()) saveStaff(h, wantedTeachers, wantedSupervisors) else pendingMove = Triple(h, wantedTeachers, wantedSupervisors)
            },
        )
    }
    pendingMove?.let { (h, wantedTeachers, wantedSupervisors) ->
        val hid = h.optString("id")
        val current = teacherLinks.filter { it.optString("halaqa_id") == hid }.map { it.optString("teacher_id") }.toSet()
        val lines = wantedTeachers.filter { it !in current }.mapNotNull { tid ->
            val from = teacherLinks.filter { it.optString("teacher_id") == tid && it.optString("halaqa_id") != hid }
                .mapNotNull { l -> halaqas.firstOrNull { it.optString("id") == l.optString("halaqa_id") }?.takeIf { it.mgmtStr("halaqa_type").ifBlank { "regular" } == "regular" } }.firstOrNull()
            from?.let { (teachers.firstOrNull { t -> t.optString("id") == tid }?.optString("full_name") ?: "المحفّظ") + " من «" + it.optString("name") + "»" }
        }
        HalaqatiAlertDialog(
            onDismissRequest = { pendingMove = null },
            title = { Text("نقل محفّظ بين حلقتين") },
            text = { Text("المحفّظ يعمل في حلقة عادية واحدة فقط، لذلك سيُنقل:\n• " + lines.joinToString("\n• ") + "\nإلى «${h.optString("name")}». يبقى له كل ما يشارك فيه من برامج.") },
            confirmButton = { Button(onClick = { pendingMove = null; saveStaff(h, wantedTeachers, wantedSupervisors) }) { Text("نقل وحفظ") } },
            dismissButton = { TextButton(onClick = { pendingMove = null }) { Text("إلغاء") } },
        )
    }

    deleteHalaqa?.let { h ->
        DeleteHalaqaDialog(
            halaqa = h,
            close = { deleteHalaqa = null },
            confirm = {
                scope.launch {
                    try {
                        val hid = h.optString("id")
                        withContext(Dispatchers.IO) {
                            AppGraph.repo.rpcWrite(
                                "delete_halaqa_cascade",
                                JSONObject().put("p_halaqa_id", hid),
                                dedupe = "halaqa-cascade-delete:$hid",
                            )
                        }
                        // Hide immediately even when the safe cascade RPC is queued offline.
                        halaqas = halaqas.filterNot { it.optString("id") == hid }
                        deleteHalaqa = null
                        if (AppGraph.repo.online()) reload()
                        onRefresh()
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر حذف الحلقة" }
                }
            },
        )
    }
}

@Composable
private fun HalaqaMembersDialog(
    halaqa: JSONObject,
    students: List<Student>,
    memberships: List<JSONObject>,
    canEdit: Boolean,
    close: () -> Unit,
    changed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val hid = halaqa.optString("id")
    var query by remember(hid) { mutableStateOf("") }
    var activeIds by remember(hid, memberships.size) {
        mutableStateOf(
            memberships.filter { it.optString("status", "active") == "active" }
                .map { it.optString("student_id") }.filter(String::isNotBlank).toSet()
        )
    }
    var savingId by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val filtered = students.filter { student ->
        student.status == "active" && (query.isBlank() || SessionWorkflowRules.matchesSearch(student.fullName, query))
    }.sortedWith(compareBy<Student> { it.halaqaId != hid }.thenBy { it.fullName })

    ModalBottomSheet(onDismissRequest = close) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مشاركو ${halaqa.optString("name", "الحلقة")}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    "الطالب الأساسي يبقى في حلقته الأصلية، ويمكن إضافته هنا كعضو إضافي دون تكرار سجله أو نقل خطته.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query, onValueChange = { query = it },
                    label = { Text("بحث عن طالب") }, leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                )
                error?.let { StateNotice(it, "error") }
            }
            if (filtered.isEmpty()) {
                EmptyState("لا يوجد طلاب مطابقون")
            } else {
                LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(filtered, key = { it.id }) { student ->
                        val primaryHere = student.halaqaId == hid
                        val selected = primaryHere || student.id in activeIds
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(Modifier.weight(1f)) {
                                    OneLineName(student.fullName, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                    Text(
                                        if (primaryHere) "طالب أساسي في هذه الحلقة" else if (student.id in activeIds) "مشارك إضافي" else "غير مشارك",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (primaryHere) {
                                    StatusPill("أساسي", "success")
                                } else if (canEdit) {
                                    Switch(
                                        checked = student.id in activeIds,
                                        enabled = savingId == null,
                                        onCheckedChange = { wanted ->
                                            val before = activeIds
                                            activeIds = if (wanted) activeIds + student.id else activeIds - student.id
                                            savingId = student.id
                                            error = null
                                            scope.launch {
                                                try {
                                                    withContext(Dispatchers.IO) { AppGraph.repo.setStudentHalaqaMembership(student.id, hid, wanted) }
                                                    changed()
                                                } catch (e: kotlinx.coroutines.CancellationException) {
                                                    throw e
                                                } catch (e: Exception) {
                                                    activeIds = before
                                                    error = e.message ?: "تعذر تعديل انتساب الطالب"
                                                } finally { savingId = null }
                                            }
                                        },
                                    )
                                } else {
                                    StatusPill(if (selected) "مشارك" else "—", "info")
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = close, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("إغلاق") }
        }
    }
}

@Composable
private fun HalaqaStaffDialog(
    halaqa: JSONObject,
    teachers: List<JSONObject>,
    supervisors: List<JSONObject>,
    currentTeacherIds: Set<String>,
    currentSupervisorIds: Set<String>,
    close: () -> Unit,
    save: (Set<String>, Set<String>) -> Unit,
) {
    var wantedTeachers by remember(halaqa.optString("id")) { mutableStateOf(currentTeacherIds) }
    var wantedSupervisors by remember(halaqa.optString("id")) { mutableStateOf(currentSupervisorIds) }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text("طاقم ${halaqa.optString("name", "الحلقة")}") },
        text = {
            LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                item { com.mohammedsamir.halaqati.ui.components.NourSectionTitle("المحفّظون", entity = com.mohammedsamir.halaqati.ui.theme.Entity.TEACHER) }
                if (teachers.isEmpty()) item { Text("لا توجد حسابات محفّظين معتمدة.", style = MaterialTheme.typography.bodyMedium) }
                items(teachers, key = { "t:${it.optString("id")}" }) { person ->
                    val id = person.optString("id")
                    ListItem(
                        headlineContent = { Text(person.optString("full_name", "محفّظ")) },
                        supportingContent = { Text(person.optString("email"), style = MaterialTheme.typography.bodySmall) },
                        leadingContent = {
                            Checkbox(
                                checked = id in wantedTeachers,
                                onCheckedChange = { checked -> wantedTeachers = if (checked) wantedTeachers + id else wantedTeachers - id },
                            )
                        },
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
                item { HorizontalDivider(); Spacer(Modifier.height(6.dp)); Text("المشرفون", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold) }
                if (supervisors.isEmpty()) item { Text("لا توجد حسابات مشرفين معتمدة.", style = MaterialTheme.typography.bodyMedium) }
                items(supervisors, key = { "s:${it.optString("id")}" }) { person ->
                    val id = person.optString("id")
                    ListItem(
                        headlineContent = { Text(person.optString("full_name", "مشرف")) },
                        supportingContent = { Text(person.optString("email"), style = MaterialTheme.typography.bodySmall) },
                        leadingContent = {
                            Checkbox(
                                checked = id in wantedSupervisors,
                                onCheckedChange = { checked -> wantedSupervisors = if (checked) wantedSupervisors + id else wantedSupervisors - id },
                            )
                        },
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
                item {
                    Text(
                        "أول مشرف محدد يُحفظ أيضًا كمشرف رئيسي للحلقة للتوافق مع البيانات القديمة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = { save(wantedTeachers, wantedSupervisors) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("حفظ التعيينات") } },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
    )
}

@Composable
private fun DeleteHalaqaDialog(halaqa: JSONObject, close: () -> Unit, confirm: () -> Unit) {
    val expected = halaqa.optString("name")
    var typed by remember(expected) { mutableStateOf("") }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        icon = { Icon(Icons.Default.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text("حذف الحلقة نهائيًا") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StateNotice("الأفضل استخدام «أرشفة الحلقة» للحفاظ على الطلاب والتسميعات والتقارير التاريخية. استخدم الحذف النهائي فقط إذا كنت متأكدًا أن تاريخ الحلقة لم يعد مطلوبًا.", "warning")
                Text("الحذف النهائي قد يزيل البيانات المرتبطة بهذه الحلقة ولا يمكن التراجع عنه بعد مزامنته مع الخادم.")
                Text("اكتب اسم الحلقة للتأكيد: $expected", fontWeight = FontWeight.Bold)
                OutlinedTextField(typed, { typed = it }, label = { Text("اسم الحلقة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (!AppGraph.repo.online()) {
                    Text(
                        "أنت دون اتصال: سيُحفظ طلب الحذف محليًا ويُرسل إلى الخادم عند عودة الاتصال.",
                        color = com.mohammedsamir.halaqati.ui.theme.textAccent("tertiary"),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = confirm,
                enabled = typed.trim() == expected.trim() && expected.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("حذف نهائي") }
        },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
    )
}

/** RC86: the program details were shown as raw codes (teacher_training · open · attendance); show them in Arabic. */
private fun programSpecAr(code: String) = mapOf(
    "hifz" to "حفظ", "intensive_hifz" to "حفظ مكثف", "tathbit" to "تثبيت", "tilawah" to "تلاوة", "tajweed_theory" to "تجويد نظري",
    "tajweed_applied" to "تجويد تطبيقي", "noorani" to "القاعدة النورانية", "ijazah" to "إجازة", "qiraat" to "قراءات",
    "teacher_training" to "تأهيل محفظين", "reading_support" to "علاج ضعف القراءة", "selected_surahs" to "سور/أجزاء محددة", "general" to "برنامج عام", "general_hifz" to "حفظ عام",
)[code] ?: code
private fun programLevelAr(code: String) = mapOf("beginner" to "مبتدئ", "intermediate" to "متوسط", "advanced" to "متقدم", "mixed" to "مختلط", "open" to "مستوى مفتوح")[code] ?: code
private fun programAssessAr(code: String) = mapOf("none" to "بدون تقييم", "attendance" to "حضور والتزام", "rubric" to "تقييم مهاري", "exam" to "اختبار", "ijazah" to "إجازة/سند")[code] ?: code

/** RC86: some rows hold the literal text "null" (saved by an older version); never show it. */
private fun JSONObject.mgmtStr(key: String): String = optString(key).trim().takeIf { it != "null" }.orEmpty()

/** RC86: a number tile with its own gradient and the girih lattice, replacing the flat grey tiles. */
@Composable
private fun HalaqaStatTile(label: String, value: String, pair: GradientPair, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(pair.brush()).nourPattern(PatternLevel.Tile).padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .95f))
        }
    }
}

@Composable
private fun SmallStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun HalaqaDialog(
    current: JSONObject?,
    saving: Boolean = false,
    errorText: String? = null,
    close: () -> Unit,
    save: (String, String, String, String, String, String, String, String, String, Boolean, LocalDate?, LocalDate?, String?) -> Unit,
) {
    var name by remember(current) { mutableStateOf(current?.optString("name").orEmpty()) }
    var logo by remember(current) { mutableStateOf(current?.mgmtStr("logo_path")?.ifBlank { null }) }
    var location by remember(current) { mutableStateOf(current?.mgmtStr("location").orEmpty()) }
    var schedule by remember(current) { mutableStateOf(current?.mgmtStr("schedule_text").orEmpty()) }
    var description by remember(current) { mutableStateOf(current?.mgmtStr("description").orEmpty()) }
    var halaqaType by remember(current) { mutableStateOf(com.mohammedsamir.halaqati.plans.ProgramKindRules.canonical(current?.optString("halaqa_type", "regular"))) }
    var programKind by remember(current) { mutableStateOf(current?.mgmtStr("program_kind").orEmpty()) }
    var specialization by remember(current) { mutableStateOf(current?.mgmtStr("program_specialization").orEmpty()) }
    var programLevel by remember(current) { mutableStateOf(current?.optString("program_level", "mixed")?.ifBlank { "mixed" } ?: "mixed") }
    var assessmentType by remember(current) { mutableStateOf(current?.optString("assessment_type", "none")?.ifBlank { "none" } ?: "none") }
    var affectsPrimaryPlan by remember(current) { mutableStateOf(current?.optBoolean("affects_primary_plan", false) ?: false) }
    var startsOn by remember(current) { mutableStateOf(current?.optString("starts_on")?.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now()) }
    var endsOn by remember(current) { mutableStateOf(current?.optString("ends_on")?.takeIf { it.isNotBlank() && it != "null" }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() } ?: LocalDate.now().plusMonths(1)) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text(if (current == null) "إضافة حلقة" else "تعديل الحلقة") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogoPicker(logo, { logo = it }, Icons.Default.Groups)
                OutlinedTextField(name, { name = it }, label = { Text("اسم الحلقة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("نوع الحلقة / البرنامج", style = MaterialTheme.typography.labelLarge)
                ResponsiveChipGrid(
                    items = listOf("regular" to "أساسية", "tilawah" to com.mohammedsamir.halaqati.plans.ProgramKindRules.TILAWAH_TAJWEED_LABEL, "training" to "تأهيلية", "course" to "دورة"),
                    minItemWidth = 105.dp, maxColumns = 2,
                ) { pair, itemModifier ->
                    ProFilterChip(halaqaType == pair.first, { halaqaType = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
                if (halaqaType != "regular") {
                    Text("تخصص البرنامج", style = MaterialTheme.typography.labelLarge)
                    HalaqatiLazySelectField(
                        label = "التخصص",
                        selectedKey = specialization.ifBlank { when (halaqaType) { "tajweed" -> "tajweed_applied"; "tilawah" -> "tilawah"; "training" -> "teacher_training"; else -> "general" } },
                        options = listOf(
                            "hifz" to "حفظ", "intensive_hifz" to "حفظ مكثف", "tathbit" to "تثبيت", "tilawah" to "تلاوة",
                            "tajweed_theory" to "تجويد نظري", "tajweed_applied" to "تجويد تطبيقي", "noorani" to "القاعدة النورانية",
                            "ijazah" to "إجازة", "qiraat" to "قراءات", "teacher_training" to "تأهيل محفظين",
                            "reading_support" to "علاج ضعف القراءة", "selected_surahs" to "سور/أجزاء محددة", "general" to "برنامج عام"
                        ),
                        onSelected = { specialization = it },
                        modifier = Modifier.fillMaxWidth(),
                        searchable = true,
                    )
                    OutlinedTextField(programKind, { programKind = it }, label = { Text("طبيعة البرنامج / ملاحظة تعريفية") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("المستوى", style = MaterialTheme.typography.labelLarge)
                    ResponsiveChipGrid(items = listOf("beginner" to "مبتدئ", "intermediate" to "متوسط", "advanced" to "متقدم", "mixed" to "مختلط"), minItemWidth = 105.dp, maxColumns = 2) { pair, itemModifier ->
                        ProFilterChip(programLevel == pair.first, { programLevel = pair.first }, pair.second, itemModifier.fillMaxWidth(), showCheckIcon = false)
                    }
                    Text("نمط التقييم", style = MaterialTheme.typography.labelLarge)
                    HalaqatiLazySelectField(
                        label = "التقييم", selectedKey = assessmentType,
                        options = listOf("none" to "بدون تقييم", "attendance" to "حضور والتزام", "rubric" to "تقييم مهاري", "exam" to "اختبار", "ijazah" to "إجازة/سند"),
                        onSelected = { assessmentType = it }, modifier = Modifier.fillMaxWidth(), searchable = false,
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text("يؤثر على الخطة الأساسية", fontWeight = FontWeight.SemiBold)
                            Text("فعّله فقط إذا كان إنجاز هذا البرنامج يجب أن يدخل في تقدم خطة الطالب الأساسية.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = affectsPrimaryPlan, onCheckedChange = { affectsPrimaryPlan = it })
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        HalaqatiDateField("بداية البرنامج", startsOn, { pickStart = true }, Modifier.weight(1f))
                        HalaqatiDateField("نهاية البرنامج", endsOn, { pickEnd = true }, Modifier.weight(1f))
                    }
                }
                OutlinedTextField(location, { location = it }, label = { Text("المكان") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(schedule, { schedule = it }, label = { Text("البرنامج") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, { description = it }, label = { Text("الوصف") }, minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                if (halaqaType != "regular") StateNotice("طلاب هذه الدورة يمكن إضافتهم كعضوية إضافية دون نقلهم من حلقتهم الأساسية.", "info")
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { save(name.trim(), location.trim(), schedule.trim(), description.trim(), halaqaType, programKind.trim(), specialization.ifBlank { "general" }, programLevel, assessmentType, affectsPrimaryPlan, startsOn.takeIf { halaqaType != "regular" }, endsOn.takeIf { halaqaType != "regular" }, logo) },
                enabled = !saving && name.trim().length >= 2 && (halaqaType == "regular" || !endsOn.isBefore(startsOn)),
            ) {
                if (saving) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (saving) "جاري الحفظ" else "حفظ")
            }
        },
        dismissButton = { TextButton(onClick = close, enabled = !saving) { Text("إلغاء") } },
    )
    if (pickStart) HalaqatiDatePickerDialog(startsOn, "بداية البرنامج", { pickStart = false }) { startsOn = it; if (endsOn.isBefore(it)) endsOn = it; pickStart = false }
    if (pickEnd) HalaqatiDatePickerDialog(endsOn, "نهاية البرنامج", { pickEnd = false }) { endsOn = it; pickEnd = false }
}

@Composable
private fun CategoriesDialog(
    halaqa: JSONObject,
    categories: List<JSONObject>,
    students: List<Student>,
    canEdit: Boolean,
    close: () -> Unit,
    changed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var minAge by remember { mutableStateOf("") }
    var maxAge by remember { mutableStateOf("") }
    var editingCategoryId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteCategory by remember { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAutoClassify by remember { mutableStateOf(false) }
    var replaceExisting by remember { mutableStateOf(false) }
    var autoMessage by remember { mutableStateOf<String?>(null) }

    fun resetEditor() {
        editingCategoryId = null
        name = ""
        minAge = ""
        maxAge = ""
        error = null
    }

    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text("فئات ${halaqa.optString("name", "الحلقة")}") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.filter { it.optBoolean("active", true) }.forEach { c ->
                    val count = students.count { it.categoryId == c.optString("id") && it.status == "active" }
                    val ages = listOf(c.optString("min_age"), c.optString("max_age")).filter { it.isNotBlank() && it != "null" }.joinToString("–")
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Category, null, Modifier.size(18.dp), tint = EntityColors.of(Entity.CATEGORY).accent)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.optString("name", "فئة"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                Text(
                                    listOf(if (ages.isBlank()) "" else "العمر $ages", "$count طالب").filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            if (canEdit) {
                                IconButton(
                                    onClick = {
                                        editingCategoryId = c.optString("id")
                                        name = c.optString("name")
                                        minAge = c.optString("min_age").takeUnless { it == "null" } ?: ""
                                        maxAge = c.optString("max_age").takeUnless { it == "null" } ?: ""
                                        error = null
                                    },
                                    modifier = Modifier.size(48.dp),
                                ) { Icon(Icons.Default.Edit, contentDescription = "تعديل الفئة", Modifier.size(20.dp)) }
                                IconButton(
                                    onClick = { pendingDeleteCategory = c },
                                    modifier = Modifier.size(48.dp),
                                ) { Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الفئة", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
                if (canEdit && categories.any { it.optBoolean("active", true) }) {
                    OutlinedButton(
                        onClick = { replaceExisting = false; autoMessage = null; showAutoClassify = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("تصنيف الطلاب تلقائياً حسب العمر")
                    }
                    autoMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                }
                if (canEdit) {
                    HorizontalDivider()
                    Text(if (editingCategoryId == null) "إضافة فئة" else "تعديل الفئة", fontWeight = FontWeight.Bold)
                    OutlinedTextField(name, { name = it }, label = { Text("اسم الفئة") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(minAge, { minAge = it.filter(Char::isDigit).take(3) }, label = { Text("من عمر") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(maxAge, { maxAge = it.filter(Char::isDigit).take(3) }, label = { Text("إلى عمر") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    if (editingCategoryId != null) {
                        TextButton(onClick = { resetEditor() }, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("إلغاء التعديل") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            if (canEdit) Button(
                enabled = name.trim().length >= 2,
                onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val obj = JSONObject()
                                    .put("halaqa_id", halaqa.optString("id"))
                                    .put("name", name.trim())
                                    .put("active", true)
                                    .put("min_age", minAge.toIntOrNull()?.coerceIn(0, 100) ?: JSONObject.NULL)
                                    .put("max_age", maxAge.toIntOrNull()?.coerceIn(0, 100) ?: JSONObject.NULL)
                                val editId = editingCategoryId
                                if (editId == null) {
                                    AppGraph.repo.store.load()?.userId?.let { obj.put("created_by", it) }
                                    AppGraph.repo.writeConfirmed("student_categories", obj)
                                } else {
                                    AppGraph.repo.patchConfirmed("student_categories", "id=eq.$editId", obj.put("updated_at", Instant.now().toString()))
                                }
                            }
                            resetEditor()
                            changed()
                        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { error = e.message ?: "تعذر إضافة الفئة" }
                    }
                },
            ) { Text(if (editingCategoryId == null) "إضافة" else "حفظ التعديل") }
        },
        dismissButton = { TextButton(onClick = close) { Text("إغلاق") } },
    )

    if (showAutoClassify) {
        val hid = halaqa.optString("id")
        val activeCats = categories.filter { it.optBoolean("active", true) }
        fun bound(c: JSONObject, key: String): Int? = if (!c.has(key) || c.isNull(key)) null else c.optInt(key)
        val today = LocalDate.now()
        val plan = remember(students, categories, replaceExisting) {
            var noDob = 0; var outside = 0; var ambiguous = 0; var keptExisting = 0; var already = 0
            val assign = mutableListOf<Pair<Student, String>>()
            students.filter { it.halaqaId == hid && it.status == "active" }.forEach { st ->
                val dob = st.dateOfBirth?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
                val age = dob?.let { java.time.temporal.ChronoUnit.YEARS.between(it, today).toInt() }
                // A missing or future date of birth (typo) must not be guessed into the youngest category.
                if (age == null || age < 0) { noDob++; return@forEach }
                val matches = activeCats.filter { c -> (bound(c, "min_age")?.let { age >= it } ?: true) && (bound(c, "max_age")?.let { age <= it } ?: true) }
                when {
                    matches.isEmpty() -> outside++
                    matches.size > 1 -> ambiguous++
                    else -> {
                        val target = matches.first().optString("id")
                        when {
                            st.categoryId == target -> already++
                            !st.categoryId.isNullOrBlank() && !replaceExisting -> keptExisting++
                            else -> assign.add(st to target)
                        }
                    }
                }
            }
            Triple(assign.toList(), listOf(noDob, outside, ambiguous), listOf(keptExisting, already))
        }
        HalaqatiAlertDialog(
            onDismissRequest = { showAutoClassify = false },
            title = { Text("تصنيف الطلاب حسب العمر") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("سيُصنَّف: ${plan.first.size} طالب", fontWeight = FontWeight.Bold)
                    Text("بلا تاريخ ميلاد: ${plan.second[0]} (يُتخطّى)")
                    Text("خارج أي فئة: ${plan.second[1]} (يُتخطّى)")
                    Text("تطابق أكثر من فئة: ${plan.second[2]} (يُتخطّى)")
                    if (plan.third[0] > 0) Text("لديهم تصنيف حالي: ${plan.third[0]} (يُترك كما هو)")
                    if (plan.third[1] > 0) Text("مصنَّفون صحيحاً: ${plan.third[1]}")
                    Row(Modifier.fillMaxWidth().clickable { replaceExisting = !replaceExisting }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = replaceExisting, onCheckedChange = { replaceExisting = it })
                        Text("استبدال التصنيف الحالي")
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = plan.first.isNotEmpty(),
                    onClick = {
                        val work = plan.first
                        showAutoClassify = false
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    work.forEach { (st, cid) ->
                                        AppGraph.repo.patch("students", "id=eq.${st.id}", JSONObject().put("category_id", cid), dedupe = "student-category:${st.id}")
                                    }
                                }
                                autoMessage = "تم تصنيف ${work.size} طالب"
                                changed()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) { error = e.message ?: "تعذر تصنيف الطلاب" }
                        }
                    },
                ) { Text("تطبيق") }
            },
            dismissButton = { TextButton(onClick = { showAutoClassify = false }) { Text("إلغاء") } },
        )
    }

    pendingDeleteCategory?.let { category ->
        HalaqatiAlertDialog(
            onDismissRequest = { pendingDeleteCategory = null },
            title = { Text("حذف الفئة") },
            text = {
                Text("سيتم إزالة الفئة «${category.optString("name", "الفئة")}» وفك ربط الطلاب بها. سجلات التسميع والحضور للطلاب لن تُحذف.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val id = category.optString("id")
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    students.filter { it.categoryId == id }.forEach { st ->
                                        AppGraph.repo.patchConfirmed("students", "id=eq.${st.id}", JSONObject().put("category_id", JSONObject.NULL))
                                    }
                                    AppGraph.repo.deleteConfirmed("student_categories", "id=eq.$id")
                                }
                                if (editingCategoryId == id) resetEditor()
                                pendingDeleteCategory = null
                                changed()
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "تعذر حذف الفئة"
                                pendingDeleteCategory = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget),
                ) { Text("حذف") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteCategory = null }, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("إلغاء") } },
        )
    }
}

@Composable
fun CommunityNativeScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf("announcements") }
    var announcements by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var posts by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var comments by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var addAnnouncement by remember { mutableStateOf(false) }
    val annRequested by com.mohammedsamir.halaqati.ui.navigation.AddActionBus.requested.collectAsState()
    LaunchedEffect(annRequested) {
        if (annRequested == "announcement") {
            com.mohammedsamir.halaqati.ui.navigation.AddActionBus.consume()
            addAnnouncement = true
        }
    }
    var editAnnouncement by remember { mutableStateOf<JSONObject?>(null) }
    var deleteAnnouncement by remember { mutableStateOf<JSONObject?>(null) }
    var announcementSaving by remember { mutableStateOf(false) }
    var announcementDialogError by remember { mutableStateOf<String?>(null) }
    var addPost by remember { mutableStateOf(false) }
    var editPost by remember { mutableStateOf<JSONObject?>(null) }
    var deletePost by remember { mutableStateOf<JSONObject?>(null) }
    var commentPost by remember { mutableStateOf<JSONObject?>(null) }
    var deleteComment by remember { mutableStateOf<JSONObject?>(null) }

    suspend fun reload() {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) {
                if (AppGraph.repo.online()) AppGraph.repo.syncNow()
                listOf(
                    AppGraph.repo.table("announcements", "select=*&order=pinned.desc&order=publish_at.desc&limit=200", forceRefresh = true).rows(),
                    AppGraph.repo.table("forum_posts", "select=*&order=pinned.desc&order=created_at.desc&limit=200", forceRefresh = true).rows(),
                    AppGraph.repo.table("forum_comments", "select=*&order=created_at.asc&limit=1000", forceRefresh = true).rows(),
                    AppGraph.repo.table("halaqas", "select=id,name&active=eq.true&order=name.asc&limit=100", forceRefresh = true).rows(),
                )
            }
            announcements = result[0]; posts = result[1]; comments = result[2]; halaqas = result[3]
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تحميل المجتمع" }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { reload() }
    val annListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val forumListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val expandedItems = remember { mutableStateListOf<String>() }
    var itemMenuId by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        AppTopBar("الإعلانات ومنتدى الحلقة", onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
            // RC39: same clarity as the filter chips: the active segment has a strong fill and a brand border.
            val segmentColors = SegmentedButtonDefaults.colors(
                activeContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f).compositeOver(MaterialTheme.colorScheme.surface),
                activeContentColor = MaterialTheme.colorScheme.onSurface,
                activeBorderColor = MaterialTheme.colorScheme.primary,
                inactiveBorderColor = MaterialTheme.colorScheme.outline,
                inactiveContentColor = MaterialTheme.colorScheme.onSurface,
            )
            SegmentedButton(selected = tab == "announcements", onClick = { tab = "announcements" }, shape = SegmentedButtonDefaults.itemShape(0, 2), colors = segmentColors) { Text("الإعلانات") }
            SegmentedButton(selected = tab == "forum", onClick = { tab = "forum" }, shape = SegmentedButtonDefaults.itemShape(1, 2), colors = segmentColors) { Text("المنتدى") }
        }
        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (loading && announcements.isEmpty() && posts.isEmpty()) LoadingSkeleton(rows = 3, modifier = Modifier.padding(12.dp))

        if (tab == "announcements") {
            if (!loading && announcements.isEmpty()) EmptyState("لا توجد إعلانات")
            LazyColumn(
                state = annListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = FabClearance),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(announcements, key = { it.optString("id") }) { a ->
                    val aid = a.optString("id")
                    val open = aid in expandedItems
                    ElevatedCard(Modifier.fillMaxWidth().animateContentSize()) {
                        Column(
                            Modifier.clickable { if (open) expandedItems.remove(aid) else expandedItems.add(aid) }.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (a.optBoolean("pinned", false)) Icons.Default.PushPin else Icons.Default.Campaign, contentDescription = null, Modifier.size(20.dp), tint = EntityColors.of(Entity.ANNOUNCEMENT).accent)
                                Spacer(Modifier.width(8.dp))
                                Text(a.optString("title", "إعلان"), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                                    Text(announcementCategoryAr(a.optString("category")), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                                if (roleCanManage(profile.role)) {
                                    Box {
                                        IconButton(onClick = { itemMenuId = aid }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "خيارات الإعلان")
                                        }
                                        DropdownMenu(expanded = itemMenuId == aid, onDismissRequest = { itemMenuId = null }) {
                                            DropdownMenuItem(
                                                text = { Text("تعديل الإعلان") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = { itemMenuId = null; announcementDialogError = null; editAnnouncement = a },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("حذف الإعلان", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = { itemMenuId = null; deleteAnnouncement = a },
                                            )
                                        }
                                    }
                                } else Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                a.optString("body"), style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (open) Int.MAX_VALUE else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(
                                "${audienceAr(a.optString("audience"))} · ${priorityAr(a.optString("priority"))}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        } else {
            if (!loading && posts.isEmpty()) EmptyState("لا توجد منشورات")
            LazyColumn(
                state = forumListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = FabClearance),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(posts, key = { it.optString("id") }) { p ->
                    val pid = p.optString("id")
                    val open = pid in expandedItems
                    val postComments = comments.filter { it.optString("post_id") == pid }
                    ElevatedCard(Modifier.fillMaxWidth().animateContentSize()) {
                        Column(
                            Modifier.clickable { if (open) expandedItems.remove(pid) else expandedItems.add(pid) }.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (p.optBoolean("pinned", false)) Icons.Default.PushPin else Icons.Default.Forum, null, Modifier.size(20.dp), tint = EntityColors.of(Entity.ANNOUNCEMENT).accent)
                                Spacer(Modifier.width(8.dp))
                                Text(p.optString("title", "منشور"), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                                    Text(postTypeAr(p.optString("post_type")), Modifier.padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                                if (p.optString("author_id") == profile.id || roleCanAdmin(profile.role)) {
                                    Box {
                                        IconButton(onClick = { itemMenuId = pid }, modifier = Modifier.size(48.dp)) {
                                            Icon(Icons.Default.MoreVert, contentDescription = "خيارات المنشور")
                                        }
                                        DropdownMenu(expanded = itemMenuId == pid, onDismissRequest = { itemMenuId = null }) {
                                            DropdownMenuItem(
                                                text = { Text("تعديل المنشور") },
                                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                                onClick = { itemMenuId = null; editPost = p },
                                            )
                                            DropdownMenuItem(
                                                text = { Text("حذف المنشور", color = MaterialTheme.colorScheme.error) },
                                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                                onClick = { itemMenuId = null; deletePost = p },
                                            )
                                        }
                                    }
                                } else Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                p.optString("body"), style = MaterialTheme.typography.bodyMedium,
                                maxLines = if (open) Int.MAX_VALUE else 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text(
                                "${p.optString("author_name", "عضو الحلقة")} · ${postComments.size} تعليق",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            )
                            if (open) {
                                Column(Modifier.padding(end = 8.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    postComments.take(3).forEach { c ->
                                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                                            Row(Modifier.padding(start = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                                Column(Modifier.weight(1f)) {
                                                    Text(c.optString("author_name", "عضو"), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                                    Text(c.optString("body"), style = MaterialTheme.typography.bodyMedium)
                                                }
                                                if (c.optString("author_id") == profile.id || roleCanAdmin(profile.role)) {
                                                    IconButton(onClick = { deleteComment = c }, modifier = Modifier.size(48.dp)) {
                                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف التعليق")
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    if (p.optBoolean("comments_enabled", true)) {
                                        TextButton(onClick = { commentPost = p }, modifier = Modifier.align(Alignment.End)) { Text("إضافة تعليق") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (tab == "announcements" && roleCanManage(profile.role)) {
        CollapsingFab(annListState, "إعلان جديد", Icons.Default.Campaign) { announcementDialogError = null; addAnnouncement = true }
    }
    if (tab == "forum") {
        CollapsingFab(forumListState, if (roleCanManage(profile.role)) "منشور جديد" else "استفسار جديد", Icons.Default.AddComment) { addPost = true }
    }
    }

    if (addAnnouncement) {
        AnnouncementDialog(
            halaqas = halaqas,
            current = null,
            saving = announcementSaving,
            errorText = announcementDialogError,
            close = { if (!announcementSaving) { addAnnouncement = false; announcementDialogError = null } },
        ) { halaqaId, title, body, audience, priority, pinned, category ->
            scope.launch {
                announcementSaving = true
                announcementDialogError = null
                try {
                    withContext(Dispatchers.IO) {
                        val obj = JSONObject()
                            .put("title", title)
                            .put("body", body)
                            .put("audience", audience)
                            .put("priority", priority)
                            .put("pinned", pinned)
                            .put("category", category)
                            .put("publish_at", Instant.now().toString())
                        if (halaqaId.isNotBlank()) obj.put("halaqa_id", halaqaId)
                        AppGraph.repo.store.load()?.userId?.let { obj.put("created_by", it) }
                        AppGraph.repo.writeConfirmed("announcements", obj)
                    }
                    addAnnouncement = false; reload(); onRefresh()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val text = e.message ?: "تعذر نشر الإعلان"
                    message = text
                    announcementDialogError = text
                } finally {
                    announcementSaving = false
                }
            }
        }
    }
    editAnnouncement?.let { current ->
        AnnouncementDialog(
            halaqas = halaqas,
            current = current,
            saving = announcementSaving,
            errorText = announcementDialogError,
            close = { if (!announcementSaving) { editAnnouncement = null; announcementDialogError = null } },
        ) { halaqaId, title, body, audience, priority, pinned, category ->
            scope.launch {
                announcementSaving = true
                announcementDialogError = null
                try {
                    withContext(Dispatchers.IO) {
                        AppGraph.repo.patchConfirmed(
                            "announcements",
                            "id=eq.${current.optString("id")}",
                            JSONObject()
                                .put("halaqa_id", halaqaId.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                                .put("title", title)
                                .put("body", body)
                                .put("audience", audience)
                                .put("priority", priority)
                                .put("pinned", pinned)
                                .put("category", category),
                        )
                    }
                    editAnnouncement = null; reload(); onRefresh()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val text = e.message ?: "تعذر تعديل الإعلان"
                    message = text
                    announcementDialogError = text
                } finally {
                    announcementSaving = false
                }
            }
        }
    }
    deleteAnnouncement?.let { current ->
        ConfirmDeleteDialog(
            title = "حذف الإعلان",
            message = "سيتم حذف الإعلان «${current.optString("title") }» نهائيًا.",
            close = { deleteAnnouncement = null },
            confirm = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { AppGraph.repo.deleteConfirmed("announcements", "id=eq.${current.optString("id")}") }
                        deleteAnnouncement = null; reload(); onRefresh()
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر حذف الإعلان" }
                }
            },
        )
    }

    if (addPost) {
        ForumPostDialog(profile = profile, halaqas = halaqas, current = null, close = { addPost = false }) { halaqaId, type, title, body, commentsEnabled, pinned ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val uid = AppGraph.repo.store.load()?.userId ?: error("الجلسة غير صالحة")
                        val obj = JSONObject()
                            .put("halaqa_id", halaqaId)
                            .put("author_id", uid)
                            .put("author_name", profile.fullName)
                            .put("post_type", type)
                            .put("title", title)
                            .put("body", body)
                            .put("comments_enabled", commentsEnabled)
                            .put("pinned", pinned && roleCanManage(profile.role))
                        AppGraph.repo.writeConfirmed("forum_posts", obj)
                    }
                    addPost = false; reload(); onRefresh()
                } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message }
            }
        }
    }
    editPost?.let { current ->
        ForumPostDialog(profile = profile, halaqas = halaqas, current = current, close = { editPost = null }) { halaqaId, type, title, body, commentsEnabled, pinned ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        AppGraph.repo.patchConfirmed(
                            "forum_posts",
                            "id=eq.${current.optString("id")}",
                            JSONObject()
                                .put("halaqa_id", halaqaId)
                                .put("post_type", type)
                                .put("title", title)
                                .put("body", body)
                                .put("comments_enabled", commentsEnabled)
                                .put("pinned", pinned && roleCanManage(profile.role))
                                .put("updated_at", Instant.now().toString()),
                        )
                    }
                    editPost = null; reload(); onRefresh()
                } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تعديل المنشور" }
            }
        }
    }
    deletePost?.let { current ->
        ConfirmDeleteDialog(
            title = "حذف المنشور",
            message = "سيتم حذف المنشور وتعليقاته المرتبطة وفق سياسات قاعدة البيانات.",
            close = { deletePost = null },
            confirm = {
                scope.launch {
                    try {
                        val id = current.optString("id")
                        withContext(Dispatchers.IO) {
                            // Delete comments first so this remains safe even if the FK has no cascade.
                            AppGraph.repo.deleteConfirmed("forum_comments", "post_id=eq.$id")
                            AppGraph.repo.deleteConfirmed("forum_posts", "id=eq.$id")
                        }
                        deletePost = null; reload(); onRefresh()
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر حذف المنشور" }
                }
            },
        )
    }
    deleteComment?.let { current ->
        ConfirmDeleteDialog(
            title = "حذف التعليق",
            message = "هل تريد حذف هذا التعليق؟",
            close = { deleteComment = null },
            confirm = {
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { AppGraph.repo.deleteConfirmed("forum_comments", "id=eq.${current.optString("id")}") }
                        deleteComment = null; reload(); onRefresh()
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر حذف التعليق" }
                }
            },
        )
    }

    commentPost?.let { post ->
        CommentDialog(close = { commentPost = null }) { body ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val uid = AppGraph.repo.store.load()?.userId ?: error("الجلسة غير صالحة")
                        AppGraph.repo.writeConfirmed(
                            "forum_comments",
                            JSONObject()
                                .put("post_id", post.optString("id"))
                                .put("author_id", uid)
                                .put("author_name", profile.fullName)
                                .put("body", body),
                        )
                    }
                    commentPost = null; reload(); onRefresh()
                } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message }
            }
        }
    }
}

private fun announcementCategoryAr(v: String) = mapOf("daily" to "يومي", "monthly" to "شهري", "schedule" to "جدول", "urgent" to "عاجل", "achievement" to "إنجاز", "general" to "عام")[v] ?: "عام"
private fun audienceAr(v: String) = mapOf("all" to "الكل", "teachers" to "المحفّظين", "guardians" to "أولياء الأمور", "students" to "الطلاب")[v] ?: "الكل"
private fun priorityAr(v: String) = mapOf("urgent" to "عاجل", "important" to "مهم", "normal" to "عادي")[v] ?: "عادي"
private fun postTypeAr(v: String) = mapOf("question" to "استفسار", "achievement" to "إنجاز", "notice" to "ملاحظة", "discussion" to "نقاش")[v] ?: "نقاش"

@Composable
private fun AnnouncementDialog(
    halaqas: List<JSONObject>,
    current: JSONObject? = null,
    saving: Boolean = false,
    errorText: String? = null,
    close: () -> Unit,
    save: (String, String, String, String, String, Boolean, String) -> Unit,
) {
    var halaqaId by remember(current?.optString("id")) { mutableStateOf(current?.optString("halaqa_id").orEmpty()) }
    var title by remember(current?.optString("id")) { mutableStateOf(current?.optString("title").orEmpty()) }
    var body by remember(current?.optString("id")) { mutableStateOf(current?.optString("body").orEmpty()) }
    var audience by remember(current?.optString("id")) { mutableStateOf(current?.optString("audience", "all") ?: "all") }
    var priority by remember(current?.optString("id")) { mutableStateOf(current?.optString("priority", "normal") ?: "normal") }
    var category by remember(current?.optString("id")) { mutableStateOf(current?.optString("category", "general") ?: "general") }
    var pinned by remember(current?.optString("id")) { mutableStateOf(current?.optBoolean("pinned", false) ?: false) }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text(if (current == null) "إعلان جديد" else "تعديل الإعلان") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HalaqatiLazySelectField(
                    label = "الحلقة",
                    selectedKey = halaqaId,
                    options = halaqas.map { it.optString("id") to it.optString("name", "حلقة") },
                    onSelected = { halaqaId = it },
                    modifier = Modifier.fillMaxWidth(),
                    searchable = halaqas.size > 8,
                    allowEmpty = true,
                    emptyOptionLabel = "كل الحلقات",
                    emptyLabel = "كل الحلقات",
                )
                OutlinedTextField(title, { title = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    body,
                    { body = it },
                    label = { Text("نص الإعلان") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 230.dp),
                )
                Text("الجمهور", style = MaterialTheme.typography.labelLarge)
                ResponsiveChipGrid(items = listOf("all", "guardians", "teachers", "students"), minItemWidth = 106.dp, maxColumns = 2) { v, itemModifier ->
                    ProFilterChip(audience == v, { audience = v }, audienceAr(v), itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
                Text("الأولوية", style = MaterialTheme.typography.labelLarge)
                ResponsiveChipGrid(items = listOf("normal", "important", "urgent"), minItemWidth = 106.dp, maxColumns = 3) { v, itemModifier ->
                    ProFilterChip(priority == v, { priority = v }, priorityAr(v), itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
                Text("التصنيف", style = MaterialTheme.typography.labelLarge)
                ResponsiveChipGrid(items = listOf("general", "daily", "monthly", "schedule", "achievement", "urgent"), minItemWidth = 112.dp, maxColumns = 3) { v, itemModifier ->
                    ProFilterChip(category == v, { category = v }, announcementCategoryAr(v), itemModifier.fillMaxWidth(), showCheckIcon = false)
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(pinned, { pinned = it }, enabled = !saving); Spacer(Modifier.width(8.dp)); Text("تثبيت الإعلان") }
                errorText?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { save(halaqaId, title.trim(), body.trim(), audience, priority, pinned, category) },
                enabled = !saving && title.trim().length >= 2 && body.trim().length >= 2,
                modifier = Modifier.heightIn(min = 48.dp),
            ) {
                if (saving) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (saving) "جاري النشر" else if (current == null) "نشر" else "حفظ")
            }
        },
        dismissButton = { TextButton(onClick = close, enabled = !saving) { Text("إلغاء") } },
    )
}

@Composable
private fun ForumPostDialog(
    profile: Profile,
    halaqas: List<JSONObject>,
    current: JSONObject? = null,
    close: () -> Unit,
    save: (String, String, String, String, Boolean, Boolean) -> Unit,
) {
    var halaqaId by remember(current?.optString("id")) { mutableStateOf(current?.optString("halaqa_id").orEmpty().ifBlank { halaqas.firstOrNull()?.optString("id").orEmpty() }) }
    var type by remember(current?.optString("id")) { mutableStateOf(current?.optString("post_type").orEmpty().ifBlank { if (roleCanManage(profile.role)) "discussion" else "question" }) }
    var title by remember(current?.optString("id")) { mutableStateOf(current?.optString("title").orEmpty()) }
    var body by remember(current?.optString("id")) { mutableStateOf(current?.optString("body").orEmpty()) }
    var commentsEnabled by remember(current?.optString("id")) { mutableStateOf(current?.optBoolean("comments_enabled", true) ?: true) }
    var pinned by remember(current?.optString("id")) { mutableStateOf(current?.optBoolean("pinned", false) ?: false) }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text(if (current != null) "تعديل المنشور" else if (roleCanManage(profile.role)) "منشور جديد" else "استفسار جديد") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HalaqatiLazySelectField(
                    label = "الحلقة",
                    selectedKey = halaqaId,
                    options = halaqas.map { it.optString("id") to it.optString("name", "حلقة") },
                    onSelected = { halaqaId = it },
                    modifier = Modifier.fillMaxWidth(),
                    searchable = halaqas.size > 8,
                )
                if (roleCanManage(profile.role)) {
                    Text("نوع المنشور", style = MaterialTheme.typography.labelLarge)
                    ResponsiveChipGrid(items = listOf("discussion", "question", "achievement", "notice"), minItemWidth = 112.dp, maxColumns = 2) { v, itemModifier ->
                        ProFilterChip(type == v, { type = v }, postTypeAr(v), itemModifier.fillMaxWidth(), showCheckIcon = false)
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(body, { body = it }, label = { Text("المحتوى") }, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(commentsEnabled, { commentsEnabled = it }); Spacer(Modifier.width(8.dp)); Text("السماح بالتعليقات") }
                if (roleCanManage(profile.role)) Row(verticalAlignment = Alignment.CenterVertically) { Switch(pinned, { pinned = it }); Spacer(Modifier.width(8.dp)); Text("تثبيت المنشور") }
            }
        },
        confirmButton = { Button(onClick = { save(halaqaId, type, title.trim(), body.trim(), commentsEnabled, pinned) }, enabled = halaqaId.isNotBlank() && title.trim().length >= 2 && body.trim().length >= 2, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (current == null) "نشر" else "حفظ") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

@Composable
private fun CommentDialog(close: () -> Unit, save: (String) -> Unit) {
    var body by remember { mutableStateOf("") }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text("إضافة تعليق") },
        text = { OutlinedTextField(body, { body = it }, label = { Text("التعليق") }, minLines = 2, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { save(body.trim()) }, enabled = body.trim().isNotBlank()) { Text("إرسال") } },
        dismissButton = { TextButton(onClick = close) { Text("إلغاء") } },
    )
}

@Composable
private fun ConfirmDeleteDialog(title: String, message: String, close: () -> Unit, confirm: () -> Unit) {
    HalaqatiAlertDialog(
        onDismissRequest = close,
        icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = confirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("حذف") }
        },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
    )
}

@Composable
fun NotificationsNativeScreen(profile: Profile, pending: Int, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var unreadOnly by remember { mutableStateOf(false) }
    var preferences by remember { mutableStateOf<JSONObject?>(null) }
    var showPreferences by remember { mutableStateOf(false) }
    var chatThread by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf("all") }
    var showArchived by remember { mutableStateOf(false) }
    var expandedGroups by remember { mutableStateOf(setOf<String>()) }
    var showCenter by remember { mutableStateOf(false) }

    suspend fun reload() {
        loading = true
        try {
            val result = withContext(Dispatchers.IO) {
                // RC53: an administrator can read every notification in the system (RLS), which buried his own under hundreds of
                // automatic alerts addressed to teachers. The page now shows only what was sent TO this account.
                val notifications = AppGraph.repo.table("notifications", "select=*&recipient_id=eq.${profile.id}&order=created_at.desc&limit=400").rows()
                    .filter { it.optString("recipient_id") == profile.id }
                val prefs = AppGraph.repo.table("notification_preferences", "select=*&user_id=eq.${profile.id}&limit=1").rows().firstOrNull()
                notifications to prefs
            }
            rows = NotificationReadOverlay.apply(result.first)
            preferences = result.second ?: JSONObject()
                .put("user_id", profile.id)
                .put("push_enabled", true)
                .put("attendance", true)
                .put("recitation", true)
                .put("behavior", true)
                .put("announcements", true)
                .put("achievements", true)
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تحميل الإشعارات" }
        finally { loading = false }
    }
    LaunchedEffect(Unit) { reload() }
    // RC24.2: old "اختبار مرحلي" alerts are legacy noise (milestone exams were removed from smart plans).
    val legacyRows = rows.filter { notificationIsLegacyTest(it) }
    val liveRows = rows.filter { !notificationIsLegacyTest(it) }
    val archiveCutoff = Instant.now().minusSeconds(30L * 24L * 3600L)
    val archivedCount = liveRows.count { notificationIsArchived(it, archiveCutoff) }
    val kinds = liveRows.map { it.optString("kind") }.filter { it.isNotBlank() }.distinct()
    val shown = liveRows.filter { n ->
        (showArchived || !notificationIsArchived(n, archiveCutoff)) &&
            (!unreadOnly || notificationUnread(n)) &&
            (kindFilter == "all" || n.optString("kind") == kindFilter) &&
            (query.isBlank() || (n.optString("title") + " " + n.optString("body")).contains(query.trim()))
    }
    val unread = liveRows.count { notificationUnread(it) }
    val groups = LinkedHashMap<String, MutableList<JSONObject>>()
    shown.forEach { n -> groups.getOrPut(notificationGroupTitle(n) + "|" + n.optString("created_at").take(10)) { mutableListOf() }.add(n) }
    val entries = groups.entries.map { it.key to it.value.toList() }

    fun markRead(list: List<JSONObject>) {
        val targets = list.filter { notificationUnread(it) }
        if (targets.isEmpty()) return
        // RC62: show it as read immediately (optimistic), then sync; the overlay keeps it read across reloads and re-opening the page.
        targets.forEach { NotificationReadOverlay.ids.add(it.optString("id")) }
        rows = NotificationReadOverlay.apply(rows)
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    targets.forEach { n ->
                        AppGraph.repo.patch("notifications", "id=eq.${n.optString("id")}", JSONObject().put("read_at", Instant.now().toString()), dedupe = "notification-read:${n.optString("id")}")
                    }
                }
                onRefresh()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                targets.forEach { NotificationReadOverlay.ids.remove(it.optString("id")) }
                message = e.message ?: "تعذر تحديث الحالة"
                reload()
            }
        }
    }

    fun chatOpener(n: JSONObject): (() -> Unit)? {
        if (n.optString("kind") != "support_thread") return null
        val tid = chatThreadIdOf(n) ?: return null
        return { markRead(listOf(n)); chatThread = tid }
    }

    if (showCenter) BroadcastCenter(profile) { showCenter = false; scope.launch { reload() } } else Column {
        AppTopBar("الإشعارات", onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending)
        if (roleCanAdmin(profile.role)) {
            FilledTonalButton(
                onClick = { showCenter = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).heightIn(min = 48.dp),
            ) { Text("إرسال إشعار / متابعة من قرأ") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { showPreferences = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Tune, contentDescription = "تفضيلات الإشعارات")
            }
            Spacer(Modifier.width(4.dp))
            ProFilterChip(unreadOnly, { unreadOnly = !unreadOnly }, "غير المقروءة ($unread)")
            Spacer(Modifier.weight(1f))
            if (unread > 0) TextButton(onClick = { markRead(liveRows) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("تحديد الكل كمقروء") }
        }
        CompactSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = "بحث باسم الطالب أو النص",
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )
        if (kinds.size > 1) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ProFilterChip(kindFilter == "all", { kindFilter = "all" }, "كل الأنواع")
                kinds.forEach { k -> ProFilterChip(kindFilter == k, { kindFilter = if (kindFilter == k) "all" else k }, notificationKindAr(k)) }
            }
        }
        if (archivedCount > 0 || legacyRows.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (archivedCount > 0) TextButton(onClick = { showArchived = !showArchived }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (showArchived) "إخفاء المؤرشفة ($archivedCount)" else "إظهار المؤرشفة ($archivedCount)", style = MaterialTheme.typography.bodySmall)
                }
                if (legacyRows.isNotEmpty()) TextButton(onClick = { markRead(legacyRows) }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("تنظيف إشعارات الاختبارات القديمة (${legacyRows.size})", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        chatThread?.let { id -> ChatSheet(id, if (profile.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN) "محادثة مع مانح" else "محادثة مع المدير") { chatThread = null } }
        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (loading && rows.isEmpty()) LoadingSkeleton(rows = 4, modifier = Modifier.padding(12.dp))
        if (!loading && shown.isEmpty()) EmptyState("لا توجد إشعارات")
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(entries, key = { it.first }) { entry ->
                val list = entry.second
                if (list.size >= 3) {
                    val open = entry.first in expandedGroups
                    val groupUnread = list.count { notificationUnread(it) }
                    ElevatedCard(Modifier.fillMaxWidth()) {
                        Column {
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp)
                                    .clickable { expandedGroups = if (open) expandedGroups - entry.first else expandedGroups + entry.first }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(notificationIcon(list.first().optString("kind")), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Column(Modifier.weight(1f)) {
                                    Text(notificationGroupTitle(list.first()), fontWeight = FontWeight.Bold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Text(
                                        "${list.size} إشعارًا" + (if (groupUnread > 0) " · $groupUnread غير مقروء" else "") + " · " + entry.first.substringAfter('|'),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (groupUnread > 0) TextButton(onClick = { markRead(list) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("مقروء", style = MaterialTheme.typography.bodySmall) }
                                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (open) "طي" else "توسيع")
                            }
                            if (open) {
                                list.forEach { n ->
                                    HorizontalDivider()
                                    NotificationCompactRow(n, showSubject = true, onOpen = chatOpener(n)) { markRead(listOf(n)) }
                                }
                            }
                        }
                    }
                } else {
                    list.forEach { n ->
                        ElevatedCard(Modifier.fillMaxWidth()) { NotificationCompactRow(n, showSubject = false, onOpen = chatOpener(n)) { markRead(listOf(n)) } }
                    }
                }
            }
        }
    }

    if (showPreferences) {
        NotificationPreferencesDialog(
            current = preferences,
            close = { showPreferences = false },
            save = { updated ->
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            AppGraph.repo.writeConfirmed(
                                "notification_preferences",
                                updated.put("user_id", profile.id).put("updated_at", Instant.now().toString()),
                                onConflict = "user_id",
                            )
                        }
                        preferences = updated
                        showPreferences = false
                        onRefresh()
                    } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر حفظ تفضيلات الإشعارات" }
                }
            },
        )
    }
}

@Composable
internal fun NotificationPreferencesDialog(
    current: JSONObject?,
    close: () -> Unit,
    save: (JSONObject) -> Unit,
) {
    var push by remember(current?.toString()) { mutableStateOf(current?.optBoolean("push_enabled", true) ?: true) }
    var attendance by remember(current?.toString()) { mutableStateOf(current?.optBoolean("attendance", true) ?: true) }
    var recitation by remember(current?.toString()) { mutableStateOf(current?.optBoolean("recitation", true) ?: true) }
    var behavior by remember(current?.toString()) { mutableStateOf(current?.optBoolean("behavior", true) ?: true) }
    var announcements by remember(current?.toString()) { mutableStateOf(current?.optBoolean("announcements", true) ?: true) }
    var achievements by remember(current?.toString()) { mutableStateOf(current?.optBoolean("achievements", true) ?: true) }
    val rows = listOf(
        Triple("الإشعارات الفورية", "تشغيل أو إيقاف Push على هذا الحساب", push),
        Triple("الحضور والغياب", "تنبيهات الحضور والتأخر والغياب", attendance),
        Triple("التسميع", "نتائج الحفظ والمراجعة والسرد", recitation),
        Triple("السلوك والمتابعة", "ملاحظات السلوك والمتابعة اليومية", behavior),
        Triple("الإعلانات", "إعلانات الحلقة والمواعيد", announcements),
        Triple("الإنجازات", "الشهادات والإنجازات", achievements),
    )
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text("تفضيلات الإشعارات") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                rows.forEachIndexed { index, row ->
                    val checked = row.third
                    ListItem(
                        headlineContent = { Text(row.first) },
                        supportingContent = { Text(row.second, style = MaterialTheme.typography.bodySmall) },
                        trailingContent = {
                            Switch(
                                checked = checked,
                                onCheckedChange = { value ->
                                    when (index) {
                                        0 -> push = value
                                        1 -> attendance = value
                                        2 -> recitation = value
                                        3 -> behavior = value
                                        4 -> announcements = value
                                        5 -> achievements = value
                                    }
                                },
                            )
                        },
                        modifier = Modifier.heightIn(min = 56.dp),
                    )
                }
                Text(
                    "إيقاف نوع معيّن لا يحذف الإشعارات الموجودة داخل التطبيق؛ بل يضبط التفضيلات المستخدمة عند الإرسال.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    save(
                        JSONObject()
                            .put("push_enabled", push)
                            .put("attendance", attendance)
                            .put("recitation", recitation)
                            .put("behavior", behavior)
                            .put("announcements", announcements)
                            .put("achievements", achievements),
                    )
                },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = 48.dp)) { Text("إلغاء") } },
    )
}

private fun notificationUnread(n: JSONObject): Boolean = n.isNull("read_at") || n.optString("read_at").isBlank()
private fun notificationGroupTitle(n: JSONObject): String = n.optString("title", "إشعار").substringBefore(" · ").trim().ifBlank { "إشعار" }
private fun notificationSubject(n: JSONObject): String = n.optString("title", "").substringAfter(" · ", "").trim()
private fun notificationIsLegacyTest(n: JSONObject): Boolean = n.optString("body").contains("اختبار مرحلي") || n.optString("title").contains("اختبار مرحلي")
private fun notificationIsArchived(n: JSONObject, cutoff: Instant): Boolean =
    !notificationUnread(n) && runCatching { Instant.parse(n.optString("created_at")).isBefore(cutoff) }.getOrDefault(false)

@Composable
private fun NotificationCompactRow(n: JSONObject, showSubject: Boolean, onOpen: (() -> Unit)? = null, onRead: () -> Unit) {
    val isUnread = notificationUnread(n)
    val unreadTone = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            // RC104: an unread row is tinted and carries a 4dp edge on the reading-start side; a read row stays plain and quieter.
            .then(if (isUnread) Modifier.background(unreadTone.copy(alpha = .07f)).drawBehind {
                val w = 4.dp.toPx()
                drawRect(unreadTone, androidx.compose.ui.geometry.Offset(if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - w else 0f, 0f), androidx.compose.ui.geometry.Size(w, size.height))
            } else Modifier)
            .then(if (onOpen != null) Modifier.clickable { onOpen() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // RC95: soft icon badge (unread = emerald tint, read = neutral) instead of a solid dark disc.
        com.mohammedsamir.halaqati.ui.components.NourIconBadge(notificationIcon(n.optString("kind")), tone = if (isUnread) "success" else "neutral")
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            val head = if (showSubject) notificationSubject(n).ifBlank { n.optString("title", "إشعار") } else n.optString("title", "إشعار")
            Text(head, fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(n.optString("body"), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(
                notificationKindAr(n.optString("kind")) + n.optString("created_at").takeIf { it.isNotBlank() }?.let { " · ${notificationTime(it)}" }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isUnread) IconButton(onClick = onRead, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) { Icon(Icons.Default.Done, contentDescription = "تحديد كمقروء") }
    }
}

/** RC60: chat notifications carry the thread id in deep_link ("chat:<id>") or, for older rows, in source_key ("msg:<id>:<time>"). */
private fun chatThreadIdOf(n: JSONObject): String? {
    val link = n.optString("deep_link").trim()
    val fromLink = link.takeIf { it.startsWith("chat:") }?.substringAfter("chat:")
    val fromKey = n.optString("source_key").takeIf { it.startsWith("msg:") }?.split(':')?.getOrNull(1)
    return (fromLink ?: fromKey)?.trim()?.takeIf { it.length == 36 }
}

private fun notificationTime(value: String): String = com.mohammedsamir.halaqati.ui.components.GazaTime.dateTime(value)

private fun notificationKindAr(v: String) = mapOf(
    "attendance" to "الحضور", "recitation" to "التسميع", "behavior" to "السلوك", "announcement" to "إعلان", "achievement" to "إنجاز",
    "manual" to "رسالة من الإدارة", "system" to "تنبيه النظام", "support_thread" to "محادثة", "support_pledge" to "كفالة",
    "support_received" to "استلام دعم", "awards_approved" to "التكريم", "observer_request" to "طلب مراقب", "observer_request_result" to "ردّ على طلب",
)[v] ?: "أخرى"

/** RC62: ids read on this device whose server copy has not caught up yet, so "مقروء" never bounces back to unread. */
private object NotificationReadOverlay {
    val ids: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    fun stamp(): String = Instant.now().toString()
    fun apply(list: List<JSONObject>): List<JSONObject> = list.map { n ->
        val id = n.optString("id")
        if (!notificationUnread(n)) { ids.remove(id); n }
        else if (id in ids) JSONObject(n.toString()).put("read_at", stamp())
        else n
    }
}
private fun notificationIcon(v: String) = when (v) {
    "attendance" -> Icons.Default.HowToReg
    "recitation" -> Icons.Default.MenuBook
    "behavior" -> Icons.Default.Psychology
    "announcement" -> Icons.Default.Campaign
    "achievement" -> Icons.Default.EmojiEvents
    "system" -> Icons.Default.Settings
    "support_thread" -> Icons.Default.Forum
    "support_pledge", "support_received" -> Icons.Default.Favorite
    "awards_approved" -> Icons.Default.EmojiEvents
    "manual" -> Icons.Default.Mail
    else -> Icons.Default.Notifications
}

private data class SupervisionFollowup(val student: Student, val reasons: List<String>, val score: Int)

private fun supervisionPlanCovers(plan: JSONObject, student: Student, today: String): Boolean {
    if (plan.optString("status") == "archived") return false
    val start = plan.optString("start_date")
    val end = plan.optString("end_date")
    if (start.isNotBlank() && start > today) return false
    if (end.isNotBlank() && end < today) return false
    if (plan.optString("halaqa_id") != student.halaqaId) return false
    val scope = plan.optString("scope_type", "student")
    val inScope = when (scope) {
        "halaqa" -> true
        "category" -> plan.optString("category_id").isNotBlank() && plan.optString("category_id") == student.categoryId
        else -> plan.optString("student_id") == student.id
    }
    if (!inScope) return false
    if (plan.optString("plan_type") != "hifz" || scope == "student") return true
    val ranges = plan.optJSONObject("settings")?.optJSONObject("student_ranges")
    return ranges?.has(student.id) == true
}

@Composable
fun SupervisionNativeScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var sessions by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var students by remember { mutableStateOf<List<Student>>(emptyList()) }
    var plans by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var visits by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var halaqas by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var goals by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var todayAttendance by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var monthRecitations by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var addVisit by remember { mutableStateOf(false) }
    var selectedHalaqa by remember { mutableStateOf("all") }

    suspend fun reload() {
        loading = students.isEmpty()
        try {
            val result = withContext(Dispatchers.IO) {
                val today = LocalDate.now().toString()
                val monthStart = LocalDate.now().withDayOfMonth(1).toString()
                val ses = AppGraph.repo.table("sessions", "select=*&order=session_date.desc&limit=300").rows()
                val st = AppGraph.repo.students()
                val pl = AppGraph.repo.table(
                    "smart_plans",
                    "select=id,scope_type,halaqa_id,student_id,category_id,plan_type,start_date,end_date,status,settings&status=neq.archived&limit=1000",
                ).rows()
                val vi = AppGraph.repo.table("supervisor_visits", "select=*&order=visit_date.desc&limit=300").rows()
                AppGraph.repo.smartPlanIneligibleHalaqaIds()
                val ha = AppGraph.repo.table("halaqas", "select=id,name&active=eq.true&order=name.asc&limit=100").rows()
                val go = AppGraph.repo.table("student_goals", "select=*&period_start=lte.$today&period_end=gte.$today&limit=1000").rows()
                val todaySessionIds = ses.filter { it.optString("session_date") == today && it.optString("status") != "cancelled" }.map { it.optString("id") }.filter { it.isNotBlank() }
                val at = if (todaySessionIds.isEmpty()) emptyList() else AppGraph.repo.table(
                    "attendance_records",
                    "select=student_id,status&session_id=in.(${todaySessionIds.joinToString(",")})&limit=3000",
                ).rows()
                val rec = AppGraph.repo.reportRecitations(monthStart, today, "all").rows()
                listOf(ses, pl, vi, ha, go, at, rec) to st
            }
            sessions = result.first[0]
            plans = result.first[1]
            visits = result.first[2]
            halaqas = result.first[3]
            goals = result.first[4]
            todayAttendance = result.first[5]
            monthRecitations = result.first[6]
            students = result.second
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message ?: "تعذر تحميل المتابعة" }
        finally { loading = false }
    }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch) { if (syncEpoch > 0L) reload() }
    LaunchedEffect(Unit) { reload() }

    val monthStart = LocalDate.now().withDayOfMonth(1).toString()
    val today = LocalDate.now().toString()
    val scopedSessions = sessions.filter { selectedHalaqa == "all" || it.optString("halaqa_id") == selectedHalaqa }
    val scopedVisits = visits.filter { selectedHalaqa == "all" || it.optString("halaqa_id") == selectedHalaqa }
    val monthSessions = scopedSessions.count { it.optString("session_date") in monthStart..today && it.optString("status") != "cancelled" }
    val activeStudents = students.filter { it.status == "active" && (selectedHalaqa == "all" || it.halaqaId == selectedHalaqa) }
    val activeStudentIds = activeStudents.map { it.id }.toSet()
    val hasPlan = activeStudents.associate { student -> student.id to plans.any { supervisionPlanCovers(it, student, today) } }
    // RC27.8: students of non-halaqa programs never need a smart plan.
    val withoutPlan = activeStudents.count { hasPlan[it.id] != true && AppGraph.repo.isSmartPlanEligible(it.halaqaId) }
    val sessionCountByHalaqa = scopedSessions
        .filter { it.optString("session_date") in monthStart..today && it.optString("status") != "cancelled" }
        .groupingBy { it.optString("halaqa_id") }
        .eachCount()
    val monthlyHifzByStudent = monthRecitations
        .filter { it.optString("activity_type") == "new_hifz" }
        .groupBy { it.optString("student_id") }
        .mapValues { (_, values) -> values.sumOf { it.optDouble("pages_count", 0.0) } }
    val absentIds = todayAttendance
        .filter { it.optString("status") == "absent" && it.optString("student_id") in activeStudentIds }
        .map { it.optString("student_id") }
        .toSet()
    val followup = activeStudents.mapNotNull { student ->
        val reasons = mutableListOf<String>()
        var score = 0
        if (student.id in absentIds) { reasons += "غائب اليوم"; score += 50 }
        if (hasPlan[student.id] != true && AppGraph.repo.isSmartPlanEligible(student.halaqaId)) { reasons += "لا توجد خطة ذكية فعالة"; score += 30 }
        val goal = goals.firstOrNull { it.optString("student_id") == student.id }
        val target = goal?.optDouble("target_hifz_pages", 0.0) ?: 0.0
        if (target > 0.0) {
            val held = sessionCountByHalaqa[student.halaqaId] ?: 0
            val expectedDays = (goal?.optInt("expected_session_days", 20) ?: 20).coerceAtLeast(1)
            val expected = target * minOf(1.0, held.toDouble() / expectedDays.toDouble())
            val actual = monthlyHifzByStudent[student.id] ?: 0.0
            val gap = (expected - actual).coerceAtLeast(0.0)
            if (gap >= 0.5) {
                reasons += "متأخر ${plainNumber(gap)} ص عن وتيرة الشهر"
                score += minOf(20, kotlin.math.ceil(gap * 4.0).toInt())
            }
        }
        if (reasons.isEmpty()) null else SupervisionFollowup(student, reasons, score)
    }.sortedWith(compareByDescending<SupervisionFollowup> { it.score }.thenBy { it.student.fullName }).take(10)

    Column {
        AppTopBar("متابعة المحفّظ", onRefresh = { scope.launch { reload() }; onRefresh() }, pending = pending)
        message?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (loading && students.isEmpty()) LoadingSkeleton(rows = 3, modifier = Modifier.padding(12.dp))
        if (halaqas.isNotEmpty()) {
            HalaqatiLazySelectField(
                label = "الحلقة",
                selectedKey = selectedHalaqa,
                options = listOf("all" to "كل الحلقات") + halaqas.map { it.optString("id") to it.optString("name", "حلقة") },
                onSelected = { selectedHalaqa = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                searchable = halaqas.size > 8,
            )
        }
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallStat("جلسات الشهر", monthSessions.toString(), Modifier.weight(1f))
                    SmallStat("بلا خطة", withoutPlan.toString(), Modifier.weight(1f))
                    SmallStat("الزيارات", scopedVisits.size.toString(), Modifier.weight(1f))
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("يحتاج متابعة الآن", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("مرتبة حسب الأولوية التشغيلية", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Badge(containerColor = if (followup.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary) { Text(followup.size.toString()) }
                        }
                        if (followup.isEmpty()) {
                            Text("✓ لا توجد حالات عاجلة في البيانات الحالية.", color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                        } else {
                            followup.forEach { row ->
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                                    Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OneLineName(row.student.fullName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer, shape = MaterialTheme.shapes.extraLarge) {
                                                Text("درجة ${row.score}", Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelLarge)
                                            }
                                        }
                                        ResponsiveChipGrid(items = row.reasons, minItemWidth = 130.dp, maxColumns = 2) { reason, itemModifier ->
                                            Box(itemModifier) { StatusPill(reason, kind = "warning") }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = { navigate("students:${row.student.id}") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("فتح ملف الطالب") }
                                            TextButton(onClick = { navigate("plans") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("الخطة") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("اختصارات العمل", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        listOf(
                            Triple("الجلسات اليومية", "الحضور والتسميع", "sessions"),
                            Triple("الخطط الذكية", "المقرر والتأخر والتعويض", "plans"),
                            Triple("التقارير", "تقارير الحلقة والمسارات", "reports"),
                            Triple("الترتيب", "النقاط والإنجازات", "rankings"),
                            Triple("الطلاب", "ملفات الطلاب", "students"),
                            Triple("الإعلانات", "التواصل مع أولياء الأمور", "community"),
                        ).forEach { (title, sub, route) ->
                            OutlinedButton(onClick = { navigate(route) }, modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget), contentPadding = PaddingValues(12.dp)) {
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(title, fontWeight = FontWeight.Bold); Text(sub, style = MaterialTheme.typography.labelSmall) }
                                Icon(Icons.Default.ChevronLeft, null)
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("زيارات المشرف", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (roleCanAdmin(profile.role)) Button(onClick = { addVisit = true }, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("إضافة زيارة") }
                }
            }
            if (!loading && scopedVisits.isEmpty()) item { EmptyState("لا توجد زيارات مسجلة للحلقة المختارة") }
            items(scopedVisits, key = { it.optString("id") }) { v ->
                val hName = halaqas.firstOrNull { it.optString("id") == v.optString("halaqa_id") }?.optString("name") ?: "حلقة"
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row { Text(hName, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(v.optString("visit_date"), style = MaterialTheme.typography.labelMedium) }
                        Text("التنظيم ${scoreText(v, "organization_score")} · التدريس ${scoreText(v, "teaching_score")} · المتابعة ${scoreText(v, "followup_score")}", style = MaterialTheme.typography.bodySmall)
                        if (v.optString("notes").isNotBlank()) Text(v.optString("notes"))
                        if (v.optString("action_items").isNotBlank()) Text("المتابعة: ${v.optString("action_items")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (addVisit) {
        SupervisorVisitDialog(halaqas, close = { addVisit = false }) { halaqaId, org, teach, follow, notes, actions ->
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        val uid = AppGraph.repo.store.load()?.userId ?: error("الجلسة غير صالحة")
                        AppGraph.repo.writeConfirmed(
                            "supervisor_visits",
                            JSONObject()
                                .put("halaqa_id", halaqaId)
                                .put("supervisor_id", uid)
                                .put("visit_date", LocalDate.now().toString())
                                .put("organization_score", org)
                                .put("teaching_score", teach)
                                .put("followup_score", follow)
                                .put("notes", notes.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                                .put("action_items", actions.takeIf { it.isNotBlank() } ?: JSONObject.NULL),
                        )
                    }
                    addVisit = false; reload(); onRefresh()
                } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) { message = e.message }
            }
        }
    }
}

private fun scoreText(v: JSONObject, key: String): String = if (v.isNull(key) || !v.has(key)) "—" else "${v.optInt(key)}/5"

@Composable
private fun SupervisorVisitDialog(halaqas: List<JSONObject>, close: () -> Unit, save: (String, Int, Int, Int, String, String) -> Unit) {
    var halaqaId by remember { mutableStateOf(halaqas.firstOrNull()?.optString("id").orEmpty()) }
    var org by remember { mutableIntStateOf(3) }
    var teach by remember { mutableIntStateOf(3) }
    var follow by remember { mutableIntStateOf(3) }
    var notes by remember { mutableStateOf("") }
    var actions by remember { mutableStateOf("") }
    HalaqatiAlertDialog(
        onDismissRequest = close,
        title = { Text("زيارة إشرافية") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HalaqatiLazySelectField(
                    label = "الحلقة",
                    selectedKey = halaqaId,
                    options = halaqas.map { it.optString("id") to it.optString("name", "حلقة") },
                    onSelected = { halaqaId = it },
                    modifier = Modifier.fillMaxWidth(),
                    searchable = halaqas.size > 8,
                )
                ScorePicker("التنظيم", org) { org = it }
                ScorePicker("التدريس", teach) { teach = it }
                ScorePicker("المتابعة", follow) { follow = it }
                OutlinedTextField(notes, { notes = it }, label = { Text("ملاحظات") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(actions, { actions = it }, label = { Text("بنود المتابعة") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { save(halaqaId, org, teach, follow, notes.trim(), actions.trim()) },
                enabled = halaqaId.isNotBlank(),
                modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget),
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = close, modifier = Modifier.heightIn(min = HalaqatiUiTokens.TouchTarget)) { Text("إلغاء") } },
    )
}

@Composable
private fun ScorePicker(label: String, value: Int, onChange: (Int) -> Unit) {
    Column {
        Text("$label: $value/5", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { n -> ProFilterChip(value == n, { onChange(n) }, n.toString()) }
        }
    }
}

private data class GuardianAnalyticsRow(
    val student: Student,
    val month: JSONObject,
    val trend: JSONObject,
    val today: JSONObject,
    val plan: PlanSnapshot?,
    val programs: List<GuardianProgramSummary> = emptyList(),
)

/** RC27.9: a program (course / التلاوة والتجويد / qualifying) the child takes besides, or instead of, the halaqa. */
private data class GuardianProgramSummary(
    val name: String,
    val type: String,
    val sessions: Int,
    val present: Int,
    val absent: Int,
    val recitations: Int,
    val avgSafety: Double?,
    val lastEvaluation: String?,
)

private fun guardianPrograms(student: Student, monthStart: String, monthEnd: String): List<GuardianProgramSummary> = runCatching {
    fun rows(arr: JSONArray) = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    val halaqas = rows(AppGraph.repo.table("halaqas", "select=id,name,active,halaqa_type,program_specialization,affects_primary_plan,starts_on,ends_on&order=name.asc&limit=200"))
        .associateBy { it.optString("id") }
    val memberships = rows(AppGraph.repo.table("student_halaqa_memberships", "select=halaqa_id,status&student_id=eq.${student.id}&limit=50"))
        .filter { it.optString("status", "active") == "active" }.map { it.optString("halaqa_id") }
    val programIds = (memberships + listOfNotNull(student.halaqaId)).distinct().filter { id ->
        val t = halaqas[id]?.optString("halaqa_type")?.takeIf { it != "null" }
        halaqas.containsKey(id) && !com.mohammedsamir.halaqati.plans.SmartPlanEligibility.isHalaqaType(t)
    }
    if (programIds.isEmpty()) return@runCatching emptyList()
    val range = "&sessions.session_date=gte.$monthStart&sessions.session_date=lte.$monthEnd&sessions.status=neq.cancelled"
    val attendance = rows(AppGraph.repo.table("attendance_records", "select=status,sessions!inner(session_date,halaqa_id,status)&student_id=eq.${student.id}$range&limit=500"))
    val recs = rows(AppGraph.repo.table("recitation_entries", "select=activity_type,evaluation,safety_percentage,created_at,sessions!inner(session_date,halaqa_id,status)&student_id=eq.${student.id}$range&order=created_at.desc&limit=500"))
    programIds.map { id ->
        val h = halaqas[id]!!
        val att = attendance.filter { it.optJSONObject("sessions")?.optString("halaqa_id") == id }
        val rc = recs.filter { it.optJSONObject("sessions")?.optString("halaqa_id") == id }
        val safeties = rc.mapNotNull { r -> if (r.has("safety_percentage") && !r.isNull("safety_percentage") && !com.mohammedsamir.halaqati.plans.RecitationQualityRules.isPlaceholder(r.optString("evaluation"), r.optDouble("safety_percentage"))) r.optDouble("safety_percentage") else null }
        GuardianProgramSummary(
            name = h.optString("name", "برنامج"),
            type = h.optString("halaqa_type"),
            sessions = att.size,
            present = att.count { it.optString("status") in setOf("present", "late") },
            absent = att.count { it.optString("status") == "absent" },
            recitations = rc.size,
            avgSafety = safeties.takeIf { it.isNotEmpty() }?.average(),
            lastEvaluation = rc.firstOrNull()?.optString("evaluation")?.takeIf { it.isNotBlank() && it != "null" },
        )
    }
}.getOrDefault(emptyList())

@Composable
private fun GuardianProgramsSection(programs: List<GuardianProgramSummary>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("البرامج الأخرى لابني", fontWeight = FontWeight.Bold)
        programs.forEach { p ->
            val kind = when (p.type) {
                "tajweed", "tilawah" -> com.mohammedsamir.halaqati.plans.ProgramKindRules.TILAWAH_TAJWEED_LABEL
                "training" -> "برنامج تأهيلي"
                else -> "دورة"
            }
            Surface(color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f), shape = MaterialTheme.shapes.medium) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    com.mohammedsamir.halaqati.ui.components.EntityHeader(com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA, p.name, subtitle = kind)
                    Text(
                        "هذا الشهر: ${p.sessions} جلسة · حضور ${p.present} · غياب ${p.absent}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (p.recitations == 0) "لا توجد تسميعات مسجلة هذا الشهر"
                        else "تسميعات ${p.recitations}" + (p.avgSafety?.let { " · متوسط الجودة ${guardianNumber(it)}%" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun GuardianProfileNativeScreen(
    profile: Profile,
    online: Boolean,
    pending: Int,
    onRefresh: () -> Unit,
    initialStudentId: String? = null,
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<GuardianAnalyticsRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }
    var trendDays by remember { mutableIntStateOf(7) }

    suspend fun reload() {
        // RC17 cache-first: offline still renders the last saved data (repository returns cache).
        loading = rows.isEmpty()
        try {
            rows = withContext(Dispatchers.IO) {
                val students = AppGraph.repo.students().filter { it.status == "active" }
                val snapshots = AppGraph.repo.planSnapshots()
                val monthStart = LocalDate.now().withDayOfMonth(1).toString()
                val monthEnd = LocalDate.now().toString()
                val trendStart = LocalDate.now().minusDays(29).toString()
                students.map { student ->
                    val month = AppGraph.repo.guardianDashboard(student.id, monthStart, monthEnd)
                    val trend = AppGraph.repo.guardianDashboard(student.id, trendStart, monthEnd)
                    val today = AppGraph.repo.guardianDashboard(student.id, monthEnd, monthEnd)
                    val plan = snapshots
                        .filter { it.student.id == student.id }
                        .sortedBy { if (it.plan.type == "hifz") 0 else 1 }
                        .firstOrNull()
                    GuardianAnalyticsRow(student, month, trend, today, plan, guardianPrograms(student, monthStart, monthEnd))
                }.sortedWith(
                    compareBy<GuardianAnalyticsRow> { if (it.student.id == initialStudentId) 0 else 1 }
                        .thenBy { it.student.fullName },
                )
            }
            message = null
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            message = e.message ?: "تعذر تحميل ملف الطالب"
        } finally {
            loading = false
        }
    }
    LaunchedEffect(online, initialStudentId) { reload() }

    Column {
        AppTopBar(
            if (profile.role == UserRole.GUARDIAN) "متابعة أبنائي" else "ملف الطالب",
            onRefresh = { scope.launch { reload() }; onRefresh() },
            pending = pending,
        )
        if (!online) {
            StateNotice(
                "وضع عدم الاتصال · تعرض آخر بيانات محفوظة، وتُحدَّث تلقائيًا عند عودة الاتصال.",
                kind = "warning",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        message?.let { StateNotice(it, kind = if (online) "error" else "warning", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (loading && rows.isEmpty()) LoadingSkeleton(rows = 3, modifier = Modifier.padding(12.dp))
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = FabClearance),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            if (profile.role == UserRole.GUARDIAN) "ملخص المتابعة" else "ملخص الأداء",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "الحفظ والحضور والترتيب وجودة التسميع وإنجاز الخطة من البيانات الفعلية.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
            if (!loading && rows.isEmpty()) item { EmptyState("لا توجد ملفات طلاب مرتبطة بهذا الحساب") }
            items(rows, key = { it.student.id }) { row ->
                GuardianAnalyticsCard(row, trendDays, onTrendDays = { trendDays = it })
            }
        }
    }
}

@Composable
private fun GuardianAnalyticsCard(
    row: GuardianAnalyticsRow,
    trendDays: Int,
    onTrendDays: (Int) -> Unit,
) {
    val student = row.student
    val ranks = row.month.optJSONObject("ranks") ?: JSONObject()
    val hifz = ranks.optJSONObject("hifz") ?: JSONObject()
    val attendance = row.month.optJSONObject("attendance") ?: JSONObject()
    val hifzPages = hifz.optDouble("pages", 0.0)
    val present = attendance.optInt("present", 0)
    val safety = latestGuardianSafety(row.trend)
    val planProgress = row.plan?.progress
    val categoryRank = guardianRankText(hifz, "category")
    val halaqaRank = guardianRankText(hifz, "halaqa")
    val planText = planProgress?.let { "${guardianNumber(it.completed)} / ${guardianNumber(it.completed + it.remaining)} ص" } ?: "—"

    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    OneLineName(student.fullName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (student.track == "tathbit") "مسار التثبيت" else "مسار الحفظ العام",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            GuardianTodayAlert(row.today)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuardianMetric("حفظ الشهر", "${guardianNumber(hifzPages)} ص", Modifier.weight(1f))
                GuardianMetric("الحضور", present.toString(), Modifier.weight(1f))
                GuardianMetric("ترتيب الفئة", categoryRank, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GuardianMetric("ترتيب الحلقة", halaqaRank, Modifier.weight(1f))
                GuardianMetric("جودة آخر تسميع", safety?.let { "${guardianNumber(it)}%" } ?: "—", Modifier.weight(1f))
                GuardianMetric("إنجاز الخطة", planText, Modifier.weight(1f))
            }

            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("ملخص المتابعة", fontWeight = FontWeight.Bold)
                        Text(
                            guardianSummary(row),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("تطور الحفظ", fontWeight = FontWeight.Bold)
                        Text("صفحات التسميع يومًا بيوم", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(7, 14, 30).forEach { days ->
                            ProFilterChip(trendDays == days, { onTrendDays(days) }, days.toString())
                        }
                    }
                }
                GuardianTrend(row.trend, trendDays)
            }

            if (row.programs.isNotEmpty()) GuardianProgramsSection(row.programs)

            row.plan?.let { snapshot ->
                val a = snapshot.progress.assignment
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("خطة اليوم", fontWeight = FontWeight.Bold)
                        }
                        Text(
                            when {
                                a.pages > 0.0 -> "${guardianNumber(a.pages)} صفحة · ${guardianPageRange(a.startPage, a.endPage)}"
                                snapshot.progress.remaining <= 0.0 -> "تم إكمال الخطة"
                                else -> "لا يوجد مقرر اليوم"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (snapshot.progress.behindBy > 0.0) {
                            Text(
                                "متأخر ${guardianNumber(snapshot.progress.behindBy)} صفحة، والتعويض موزع على الأيام القادمة.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuardianTodayAlert(todayDashboard: JSONObject) {
    val attendance = todayDashboard.optJSONObject("attendance") ?: JSONObject()
    val daily = todayDashboard.optJSONArray("daily") ?: JSONArray()
    val today = LocalDate.now().toString()
    val activity = (0 until daily.length())
        .mapNotNull { daily.optJSONObject(it) }
        .firstOrNull { it.optString("date") == today }
    val present = attendance.optInt("present", 0)
    val absent = attendance.optInt("absent", 0)
    val excused = attendance.optInt("excused", 0)
    val late = attendance.optInt("late", 0)
    val hifz = activity?.optDouble("hifz", 0.0) ?: 0.0
    val review = activity?.optDouble("review", 0.0) ?: 0.0
    val sard = activity?.optDouble("sard", 0.0) ?: 0.0
    val hasRecitation = hifz > 0.0 || review > 0.0 || sard > 0.0
    val attendanceText = when {
        late > 0 -> "متأخر"
        present > 0 -> "حاضر"
        excused > 0 -> "غائب بعذر"
        absent > 0 -> "غائب"
        else -> "لم يُسجل الحضور بعد"
    }
    val kind = when {
        absent > 0 -> "error"
        excused > 0 || late > 0 -> "warning"
        present > 0 && hasRecitation -> "success"
        else -> "info"
    }
    val detail = buildList {
        add(attendanceText)
        if (hifz > 0.0) add("حفظ ${guardianNumber(hifz)} ص")
        if (review > 0.0) add("مراجعة ${guardianNumber(review)} ص")
        if (sard > 0.0) add("سرد ${guardianNumber(sard)} ص")
        activity?.takeIf { !it.isNull("safety") }?.optDouble("safety")?.let { add("جودة ${guardianNumber(it)}%") }
    }.joinToString(" · ")
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = when (kind) {
            "error" -> MaterialTheme.colorScheme.errorContainer
            "warning" -> MaterialTheme.colorScheme.tertiaryContainer
            "success" -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.secondaryContainer
        },
        contentColor = when (kind) {
            "error" -> MaterialTheme.colorScheme.onErrorContainer
            "warning" -> MaterialTheme.colorScheme.onTertiaryContainer
            "success" -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSecondaryContainer
        },
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                when {
                    absent > 0 -> Icons.Default.EventBusy
                    present > 0 && hasRecitation -> Icons.Default.CheckCircle
                    else -> Icons.Default.Today
                },
                contentDescription = null,
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("حالة اليوم", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(detail.ifBlank { "لم يُسجل نشاط اليوم بعد" }, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun GuardianMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.padding(horizontal = 7.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GuardianTrend(dashboard: JSONObject, days: Int) {
    val daily = dashboard.optJSONArray("daily") ?: JSONArray()
    val byDate = (0 until daily.length()).mapNotNull { daily.optJSONObject(it) }.associateBy { it.optString("date") }
    val end = LocalDate.now()
    val series = (days - 1 downTo 0).map { offset ->
        val date = end.minusDays(offset.toLong())
        date to (byDate[date.toString()]?.optDouble("hifz", 0.0) ?: 0.0)
    }
    val total = series.sumOf { it.second }
    val max = series.maxOfOrNull { it.second }?.coerceAtLeast(1.0) ?: 1.0
    Text(
        "إجمالي الفترة ${guardianNumber(total)} ص · أيام الحفظ ${series.count { it.second > 0.0 }}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        series.forEach { (date, value) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(14.dp).height(72.dp), contentAlignment = Alignment.BottomCenter) {
                    val height = if (value <= 0.0) 2.dp else (8 + (56 * (value / max))).dp
                    Box(
                        Modifier.width(12.dp).height(height).background(
                            if (value > 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    )
                }
                val showLabel = days <= 7 || date == end || date.dayOfMonth % 7 == 0
                Text(if (showLabel) date.dayOfMonth.toString() else "", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun guardianRankText(metric: JSONObject, scope: String): String {
    val pages = metric.optDouble("pages", 0.0)
    val rank = metric.optInt("${scope}_rank", 0)
    val total = metric.optInt("${scope}_total", 0)
    return if (pages > 0.0 && rank > 0 && total > 0) "$rank / $total" else "—"
}

private fun latestGuardianSafety(dashboard: JSONObject): Double? {
    val daily = dashboard.optJSONArray("daily") ?: return null
    for (index in daily.length() - 1 downTo 0) {
        val row = daily.optJSONObject(index) ?: continue
        if (row.has("safety") && !row.isNull("safety")) return row.optDouble("safety")
    }
    return null
}

private fun guardianSummary(row: GuardianAnalyticsRow): String {
    val p = row.plan?.progress
    val hifz = row.month.optJSONObject("ranks")?.optJSONObject("hifz")?.optDouble("pages", 0.0) ?: 0.0
    val attendance = row.month.optJSONObject("attendance") ?: JSONObject()
    val absent = attendance.optInt("absent", 0)
    return when {
        p != null && p.health == "red" -> "الخطة تحتاج تدخلاً: يوجد تأخر ${guardianNumber(p.behindBy)} صفحة. يُفضّل الالتزام بالمقرر والتعويض الموزع."
        p != null && p.health == "yellow" -> "التقدم قريب من المطلوب، لكن توجد فجوة ${guardianNumber(p.behindBy)} صفحة تحتاج متابعة خلال الأيام القادمة."
        hifz <= 0.0 -> "لا توجد صفحات حفظ مسجلة في هذا الشهر حتى الآن."
        absent >= 3 -> "التقدم المسجل ${guardianNumber(hifz)} صفحة، مع $absent أيام غياب تحتاج متابعة."
        p != null -> "التقدم مستقر: ${guardianNumber(hifz)} صفحة حفظ هذا الشهر، وإنجاز الخطة ${guardianNumber(p.percentage)}%."
        else -> "تم تسجيل ${guardianNumber(hifz)} صفحة حفظ هذا الشهر."
    }
}

private fun guardianNumber(value: Double): String {
    val rounded = kotlin.math.round(value * 100.0) / 100.0
    return if (kotlin.math.abs(rounded - rounded.toInt()) < 0.000001) rounded.toInt().toString()
    else rounded.toString().trimEnd('0').trimEnd('.')
}

private fun guardianPageRange(start: Int?, end: Int?): String =
    if (start == null && end == null) "حسب الخطة" else RecitationParityRules.pageRangeQuranLabel(start, end)

