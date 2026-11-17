@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.nourHeroFill
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

/* ============================== data ============================== */

/** The hierarchy, top to bottom: system owner > association > association admin > halaqa supervisors > centers/mosques (+ halaqat) > teachers > students. */
private data class OrgPerson(val id: String, val name: String, val photo: String?, val role: String, val associationId: String?, val owner: Boolean)
private data class OrgHalaqa(val id: String, val name: String, val associationId: String?, val centerId: String?, val teachers: List<OrgPerson>, val supervisors: List<OrgPerson>, val students: Int, val logo: String? = null)
private data class OrgCenter(val id: String, val name: String, val kind: String, val manager: OrgPerson?, val halaqat: List<OrgHalaqa>, val logo: String? = null)
private data class OrgAssociation(
    val id: String, val name: String,
    val admins: List<OrgPerson>, val supervisors: List<OrgPerson>, val teachers: List<OrgPerson>,
    val centers: List<OrgCenter>, val unassigned: List<OrgHalaqa>, val students: Int, val logo: String? = null,
) { val halaqatCount get() = centers.sumOf { it.halaqat.size } + unassigned.size }
private data class OrgRoot(val isOwner: Boolean, val associations: List<OrgAssociation>, val people: List<OrgPerson>, val halaqat: List<OrgHalaqa>, val centersMissing: Boolean)

private const val NEED_LOGO_SQL = "لحفظ الشعارات شغّل ملف RC69_logos.sql في Supabase مرة واحدة."
private const val NEED_SQL = "لتفعيل المراكز والمساجد شغّل ملف RC67_org.sql في Supabase مرة واحدة."

private fun kindAr(kind: String) = if (kind == "mosque") "مسجد" else "مركز"
private fun kindPair(kind: String) = if (kind == "mosque") HalaqatiGradients.Gold else HalaqatiGradients.Emerald

private fun loadOrg(myId: String): OrgRoot {
    val repo = AppGraph.repo
    val fresh = repo.online()
    val owner = runCatching { repo.table("profiles", "select=id,is_platform_owner&id=eq.$myId&limit=1", forceRefresh = fresh).sRows().firstOrNull()?.optBoolean("is_platform_owner", false) }.getOrNull() == true
    PlatformOwnerState.isOwner = owner
    val assocResult = runCatching { repo.table("associations", "select=*&order=created_at.asc&limit=100", forceRefresh = fresh).sRows() }
    val assocRows = assocResult.getOrDefault(emptyList())
    val centersResult = runCatching { repo.table("centers", "select=*&order=name.asc&limit=300", forceRefresh = fresh).sRows() }
    val halaqaRows = repo.table("halaqas", "select=*&order=name.asc&limit=400", forceRefresh = fresh).sRows().filter { it.optBoolean("active", true) }
    val tLinks = repo.table("halaqa_teachers", "select=halaqa_id,teacher_id&limit=2000", forceRefresh = fresh).sRows()
    val sLinks = repo.table("halaqa_supervisors", "select=halaqa_id,supervisor_id&limit=2000", forceRefresh = fresh).sRows()
    val profileRows = repo.table("profiles", "select=id,full_name,role,association_id,avatar_path,is_platform_owner,active&order=full_name.asc&limit=800", forceRefresh = fresh).sRows()
    val people = profileRows.filter { it.optBoolean("active", true) }.map {
        OrgPerson(it.optString("id"), it.supStr("full_name").ifBlank { "—" }, it.supStr("avatar_path").ifBlank { null }, it.supStr("role"), it.supStr("association_id").ifBlank { null }, it.optBoolean("is_platform_owner", false))
    }
    val byId = people.associateBy { it.id }
    val students = repo.table("students", "select=id,halaqa_id,status&limit=4000", forceRefresh = false).sRows().filter { it.optString("status") == "active" }
    val perHalaqa = students.groupingBy { it.optString("halaqa_id") }.eachCount()
    val halaqat = halaqaRows.map { h ->
        val id = h.optString("id")
        OrgHalaqa(
            id, h.supStr("name").ifBlank { "حلقة" }, h.supStr("association_id").ifBlank { null }, h.supStr("center_id").ifBlank { null },
            tLinks.filter { it.optString("halaqa_id") == id }.mapNotNull { byId[it.optString("teacher_id")] }.distinctBy { it.id },
            sLinks.filter { it.optString("halaqa_id") == id }.mapNotNull { byId[it.optString("supervisor_id")] }.distinctBy { it.id },
            perHalaqa[id] ?: 0, h.supStr("logo_path").ifBlank { null },
        )
    }
    val centerRows = centersResult.getOrDefault(emptyList()).filter { it.optBoolean("active", true) }
    val assocList = if (assocRows.isNotEmpty()) assocRows.map { Triple(it.optString("id"), it.supStr("name").ifBlank { "جمعية" }, it.supStr("logo_path").ifBlank { null }) } else if (assocResult.isFailure) listOf(Triple("", runCatching { repo.mySubscription()?.optString("name") }.getOrNull().orEmpty().ifBlank { "الجمعية" }, null as String?)) else emptyList()
    val associations = assocList.map { (aid, aname, alogo) ->
        val mine = halaqat.filter { (it.associationId ?: "") == aid || (aid.isEmpty()) }
        val centers = centerRows.filter { (it.supStr("association_id")) == aid || aid.isEmpty() }.map { c ->
            val cid = c.optString("id")
            OrgCenter(cid, c.supStr("name").ifBlank { "مركز" }, c.supStr("kind").ifBlank { "center" }, byId[c.supStr("manager_id")], mine.filter { it.centerId == cid }, c.supStr("logo_path").ifBlank { null })
        }
        val known = centers.map { it.id }.toSet()
        val staff = people.filter { !it.owner && (it.associationId ?: "") == aid }
        OrgAssociation(
            aid, aname, staff.filter { it.role == "admin" }, staff.filter { it.role == "supervisor" }, staff.filter { it.role == "teacher" },
            centers, mine.filter { it.centerId == null || it.centerId !in known }, mine.sumOf { it.students }, alogo,
        )
    }
    return OrgRoot(owner, associations, people, halaqat, centersResult.isFailure)
}

/* ============================== screen ============================== */

@Composable
fun OrgScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var root by remember { mutableStateOf<OrgRoot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var editingCenter by remember { mutableStateOf<OrgCenter?>(null) }
    var addingCenter by remember { mutableStateOf(false) }
    var moving by remember { mutableStateOf<OrgHalaqa?>(null) }
    var deleting by remember { mutableStateOf<OrgCenter?>(null) }
    var removing by remember { mutableStateOf<Pair<OrgPerson, String>?>(null) }
    var detaching by remember { mutableStateOf<OrgHalaqa?>(null) }
    var addingAssociation by remember { mutableStateOf(false) }
    var editingAssociation by remember { mutableStateOf<OrgAssociation?>(null) }
    var logoFor by remember { mutableStateOf<OrgHalaqa?>(null) }
    var picker by remember { mutableStateOf<String?>(null) }
    var attaching by remember { mutableStateOf<OrgHalaqa?>(null) }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()

    LaunchedEffect(syncEpoch, tick) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { loadOrg(profile.id) } }
            .onSuccess { r ->
                root = r; error = null
                if (!r.isOwner && openId == null) openId = r.associations.firstOrNull()?.id
            }
            .onFailure { error = it.message ?: "تعذر تحميل الهيكل الإداري" }
    }

    fun act(done: String, block: () -> Unit) {
        scope.launch {
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { block() } }
                .onSuccess { message = done; tick++; onRefresh() }
                .onFailure { e ->
                    val m = e.message.orEmpty()
                    message = if (m.contains("logo_path")) NEED_LOGO_SQL else if (m.contains("centers") || m.contains("center_id") || m.contains("42P01") || m.contains("42703")) NEED_SQL else (m.ifBlank { "تعذر تنفيذ العملية" })
                }
        }
    }

    val r = root
    val assoc = r?.associations?.firstOrNull { it.id == openId }
    val isOwner = r?.isOwner == true
    val canEdit = isOwner || profile.role == UserRole.ADMIN
    BackHandler(enabled = isOwner && assoc != null) { openId = null }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(if (assoc != null && isOwner) assoc.name else "الهيكل الإداري", onBack = if (isOwner && assoc != null) ({ openId = null }) else null, onRefresh = { tick++; onRefresh() }, pending = pending)
        error?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        message?.let { StateNotice(it, kind = if (it == NEED_SQL || it == NEED_LOGO_SQL) "warning" else "info", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (r == null) { LoadingSkeleton(rows = 4, modifier = Modifier.padding(12.dp)); return@Column }
        LazyColumn(
            state = rememberLazyListState(), modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isOwner && assoc == null) {
                // ---- level 0: the system owner sees every association ----
                item { OwnerHero(r) }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("الجمعيات (${r.associations.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        FilledTonalButton(onClick = { addingAssociation = true }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text("إضافة جمعية") }
                    }
                }
                if (r.associations.isEmpty()) item { EmptyState("لا توجد جمعيات بعد. أضف أول جمعية ثم عيّن مسؤولها.") }
                items(r.associations, key = { "a-" + it.id }) { a -> AssociationCard(a, Modifier.animateItem()) { openId = a.id } }
                val loose = r.halaqat.filter { it.associationId == null }
                if (loose.isNotEmpty()) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LinkOff, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("حلقات مستقلة (${loose.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    item { Text("لا تتبع أي جمعية: يراها مسؤول النظام ومن أُسندت إليهم مباشرة. اربط الحلقة بجمعية متى شئت دون فقد أي بيانات.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(loose, key = { "lh-" + it.id }) { h ->
                        ElevatedCard(Modifier.fillMaxWidth().animateItem()) {
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(h.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text("${h.students} طالب · ${h.teachers.size} محفّظ · ${h.supervisors.size} مشرف", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                FilledTonalButton(onClick = { attaching = h }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(6.dp)); Text("ربط بجمعية") }
                            }
                        }
                    }
                }
            } else if (!isOwner && r.associations.isEmpty()) {
                // ---- RC91: a supervisor/teacher whose halaqat belong to no association: show only what is theirs, never an invented "الجمعية" ----
                val mine = r.halaqat
                item { IndependentHero(mine, profile) }
                if (mine.isEmpty()) item { EmptyState("لا توجد حلقات مُسندة إليك بعد.") }
                else item {
                    val u = OrgCenter("", "حلقات بلا مركز", "center", null, mine)
                    OrgCenterCard(u, "none" in expanded, false, { expanded = if ("none" in expanded) expanded - "none" else expanded + "none" },
                        onEdit = null, onDelete = null, onMove = {}, neutral = true)
                }
            } else if (assoc != null) {
                // ---- level 1: one association, top to bottom ----
                item { AssociationHero(assoc, isOwner, profile, if (canEdit) ({ editingAssociation = assoc }) else null) }
                if (r.centersMissing) item { StateNotice(NEED_SQL, kind = "warning") }

                item { SectionHeader(1, "مسؤول الجمعية", Icons.Default.AdminPanelSettings, HalaqatiGradients.Deep, if (isOwner) "تعيين" else null) { picker = "admin" } }
                if (assoc.admins.isEmpty()) item { MissingCard(if (isOwner) "لم يُعيَّن مسؤول لهذه الجمعية بعد. اضغط «تعيين» لاختيار حساب مسؤول." else "لم يُعيَّن مسؤول لهذه الجمعية بعد.") }
                items(assoc.admins, key = { "ad-" + it.id }) { PersonCard(it, HalaqatiGradients.Deep, "مسؤول الجمعية", emptyList(), if (isOwner) ({ removing = it to "admin" }) else null, Modifier.animateItem()) }

                item { SectionHeader(2, "مشرفو الحلقات", Icons.Default.SupervisorAccount, HalaqatiGradients.Gold, if (canEdit) "إضافة" else null) { picker = "supervisor" } }
                if (assoc.supervisors.isEmpty()) item { MissingCard("لا يوجد مشرفون في هذه الجمعية بعد.") }
                items(assoc.supervisors, key = { "sv-" + it.id }) { p ->
                    PersonCard(p, HalaqatiGradients.Gold, "مشرف", (assoc.centers.flatMap { it.halaqat } + assoc.unassigned).filter { h -> h.supervisors.any { it.id == p.id } }.map { it.name }, if (canEdit) ({ removing = p to "supervisor" }) else null, Modifier.animateItem())
                }

                item { SectionHeader(3, "المراكز والمساجد", Icons.Default.AccountBalance, HalaqatiGradients.Emerald, if (canEdit && !r.centersMissing) "إضافة" else null) { addingCenter = true } }
                if (assoc.centers.isEmpty() && !r.centersMissing) item { MissingCard("لا توجد مراكز أو مساجد بعد.") }
                items(assoc.centers, key = { "c-" + it.id }) { c ->
                    OrgCenterCard(c, c.id in expanded, canEdit, { expanded = if (c.id in expanded) expanded - c.id else expanded + c.id },
                        onEdit = { editingCenter = c }, onDelete = { deleting = c }, onMove = { moving = it }, onLogo = { logoFor = it }, onDetach = if (isOwner) ({ detaching = it }) else null, modifier = Modifier.animateItem())
                }
                if (assoc.unassigned.isNotEmpty()) item {
                    val u = OrgCenter("", "حلقات غير مرتبطة بمركز", "center", null, assoc.unassigned)
                    OrgCenterCard(u, "none" in expanded, canEdit && !r.centersMissing, { expanded = if ("none" in expanded) expanded - "none" else expanded + "none" },
                        onEdit = null, onDelete = null, onMove = { moving = it }, onLogo = { logoFor = it }, neutral = true, onDetach = if (isOwner) ({ detaching = it }) else null)
                }
                if (isOwner) item {
                    OutlinedButton(onClick = { picker = "halaqa" }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(8.dp)); Text("ربط حلقة بهذه الجمعية") }
                }

                item { SectionHeader(4, "المحفّظون", Icons.Default.School, HalaqatiGradients.Teal, if (canEdit) "إضافة" else null) { picker = "teacher" } }
                if (assoc.teachers.isEmpty()) item { MissingCard("لا يوجد محفّظون في هذه الجمعية بعد.") }
                items(assoc.teachers, key = { "t-" + it.id }) { p ->
                    PersonCard(p, HalaqatiGradients.Teal, "محفّظ", (assoc.centers.flatMap { it.halaqat } + assoc.unassigned).filter { h -> h.teachers.any { it.id == p.id } }.map { it.name }, if (canEdit) ({ removing = p to "teacher" }) else null, Modifier.animateItem())
                }

                item { SectionHeader(5, "الطلاب", Icons.Default.Groups, HalaqatiGradients.Blue, null) {} }
                item {
                    Surface(Modifier.fillMaxWidth().clickable { navigate("students") }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .6f), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Text(assoc.students.toString(), style = MaterialTheme.typography.displaySmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Column(Modifier.weight(1f)) {
                                Text("طالب نشط", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                Text("موزّعون على ${assoc.halaqatCount} حلقات", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }

    // ---- sheets and dialogs ----
    if (addingAssociation) EntitySheet("إضافة جمعية", "اسم الجمعية", "", null, onDismiss = { addingAssociation = false }) { name, logo ->
        addingAssociation = false
        act("أُضيفت جمعية $name") { AppGraph.repo.writeConfirmed("associations", JSONObject().put("id", UUID.randomUUID().toString()).put("name", name).also { if (logo != null) it.put("logo_path", logo) }) }
    }
    editingAssociation?.let { ea ->
        EntitySheet("تعديل ${ea.name}", "اسم الجمعية", ea.name, ea.logo, onDismiss = { editingAssociation = null }) { name, logo ->
            editingAssociation = null
            act("حُفظت الجمعية") {
                AppGraph.repo.patchConfirmed("associations", "id=eq.${ea.id}", JSONObject().put("name", name).also { if (logo != ea.logo) it.put("logo_path", logo ?: JSONObject.NULL) })
            }
        }
    }
    logoFor?.let { h ->
        EntitySheet("شعار ${h.name}", "", h.name, h.logo, onDismiss = { logoFor = null }, nameEditable = false) { _, logo ->
            logoFor = null
            act("حُفظ شعار الحلقة") { AppGraph.repo.patchConfirmed("halaqas", "id=eq.${h.id}", JSONObject().put("logo_path", logo ?: JSONObject.NULL)) }
        }
    }
    if ((addingCenter || editingCenter != null) && assoc != null && r != null) CenterSheet(
        existing = editingCenter, people = r.people.filter { !it.owner && it.associationId == assoc.id && it.role in setOf("admin", "supervisor", "teacher") },
        onDismiss = { addingCenter = false; editingCenter = null },
        onSave = { name, kind, manager, logo ->
            val ed = editingCenter
            addingCenter = false; editingCenter = null
            if (ed == null) act("أُضيف $name") {
                AppGraph.repo.writeConfirmed("centers", JSONObject().put("id", UUID.randomUUID().toString()).put("name", name).put("kind", kind).put("manager_id", manager ?: JSONObject.NULL).put("active", true).also { if (assoc.id.isNotBlank()) it.put("association_id", assoc.id); if (logo != null) it.put("logo_path", logo) })
            } else act("حُفظت التعديلات") {
                AppGraph.repo.patchConfirmed("centers", "id=eq.${ed.id}", JSONObject().put("name", name).put("kind", kind).put("manager_id", manager ?: JSONObject.NULL).also { if (logo != ed.logo) it.put("logo_path", logo ?: JSONObject.NULL) })
            }
        },
    )
    moving?.let { h ->
        if (assoc != null) MoveSheet(h, assoc.centers, onDismiss = { moving = null }) { target ->
            moving = null
            act("نُقلت ${h.name}") { AppGraph.repo.patchConfirmed("halaqas", "id=eq.${h.id}", JSONObject().put("center_id", target ?: JSONObject.NULL)) }
        }
    }
    deleting?.let { c ->
        HalaqatiAlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("حذف ${c.name}؟") },
            text = { Text("تبقى الحلقات كما هي وتصير «غير مرتبطة بمركز». لا يُحذف أي طالب أو حلقة.") },
            confirmButton = { TextButton(onClick = { deleting = null; act("حُذف ${c.name}") { AppGraph.repo.deleteConfirmed("centers", "id=eq.${c.id}") } }) { Text("حذف", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("تراجع") } },
        )
    }
    removing?.let { (person, role) ->
        val roleName = when (role) { "admin" -> "مسؤول الجمعية"; "supervisor" -> "المشرف"; else -> "المحفّظ" }
        HalaqatiAlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("إزالة ${person.name} من الجمعية؟") },
            text = { Text("يُفصل $roleName عن «${assoc?.name.orEmpty()}»" + (if (role != "admin") " ويُرفع عن حلقاتها" else "") + ". يبقى حسابه وبياناته كما هي، ويمكنك إعادة إضافته لاحقًا.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    val hids = (assoc?.centers?.flatMap { it.halaqat }.orEmpty() + assoc?.unassigned.orEmpty()).map { it.id }
                    act("أُزيل ${person.name} من الجمعية") {
                        if (role == "teacher" && hids.isNotEmpty()) AppGraph.repo.deleteConfirmed("halaqa_teachers", "teacher_id=eq.${person.id}&halaqa_id=in.(${hids.joinToString(",")})")
                        if (role == "supervisor" && hids.isNotEmpty()) AppGraph.repo.deleteConfirmed("halaqa_supervisors", "supervisor_id=eq.${person.id}&halaqa_id=in.(${hids.joinToString(",")})")
                        AppGraph.repo.patchConfirmed("profiles", "id=eq.${person.id}", JSONObject().put("association_id", JSONObject.NULL))
                    }
                }) { Text("إزالة", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("تراجع") } },
        )
    }
    detaching?.let { h ->
        HalaqatiAlertDialog(
            onDismissRequest = { detaching = null },
            title = { Text("فصل ${h.name} عن الجمعية؟") },
            text = { Text("تصير الحلقة حلقة مستقلة: يراها مسؤول النظام فقط، إضافةً إلى من أُسند إليها مباشرة من مشرفين ومحفّظين. يبقى طلابها ومحفّظوها ومشرفوها كما هم، ولا يُحذف أي طالب أو تسميع. يمكنك ربطها بجمعية في أي وقت.") },
            confirmButton = {
                TextButton(onClick = {
                    detaching = null
                    act("فُصلت ${h.name} عن الجمعية") {
                        AppGraph.repo.patchConfirmed("halaqas", "id=eq.${h.id}", JSONObject().put("association_id", JSONObject.NULL).put("center_id", JSONObject.NULL))
                        AppGraph.repo.patchConfirmed("students", "halaqa_id=eq.${h.id}", JSONObject().put("association_id", JSONObject.NULL))
                    }
                }) { Text("فصل", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { detaching = null }) { Text("تراجع") } },
        )
    }
    val attachH = attaching
    if (attachH != null && r != null) {
        PickSheet("ربط ${attachH.name} بجمعية", "اختر الجمعية", r.associations.filter { it.id.isNotEmpty() }.map { it.id to it.name }, { attaching = null }) { aid ->
            attaching = null
            act("رُبطت ${attachH.name} بالجمعية") {
                AppGraph.repo.patchConfirmed("halaqas", "id=eq.${attachH.id}", JSONObject().put("association_id", aid).put("center_id", JSONObject.NULL))
                AppGraph.repo.patchConfirmed("students", "halaqa_id=eq.${attachH.id}", JSONObject().put("association_id", aid))
            }
        }
    }
    val kind = picker
    if (kind != null && assoc != null && r != null) {
        val aid = assoc.id
        when (kind) {
            "halaqa" -> PickSheet("ربط حلقة بـ ${assoc.name}", "اختر حلقة", r.halaqat.filter { it.associationId != aid }.map { it.id to (it.name + if (it.associationId != null) " (تتبع جمعية أخرى)" else "") }, { picker = null }) { hid ->
                picker = null
                act("رُبطت الحلقة بالجمعية") {
                    AppGraph.repo.patchConfirmed("halaqas", "id=eq.$hid", JSONObject().put("association_id", aid).put("center_id", JSONObject.NULL))
                    AppGraph.repo.patchConfirmed("students", "halaqa_id=eq.$hid", JSONObject().put("association_id", aid))
                }
            }
            else -> {
                val role = kind
                val title = when (role) { "admin" -> "تعيين مسؤول الجمعية"; "supervisor" -> "إضافة مشرف"; else -> "إضافة محفّظ" }
                val cands = r.people.filter { !it.owner && it.role == role && it.associationId != aid }
                val hint = when (role) { "admin" -> "الحساب يجب أن يكون من نوع «مسؤول» (يُنشأ من شاشة الحسابات والصلاحيات)."; "supervisor" -> "الحساب يجب أن يكون معتمدًا كمشرف."; else -> "الحساب يجب أن يكون معتمدًا كمحفّظ." }
                PickSheet(title, hint, cands.map { it.id to (it.name + if (it.associationId != null) " (في جمعية أخرى)" else "") }, { picker = null }) { pid ->
                    picker = null
                    act("تم الربط بالجمعية") { AppGraph.repo.patchConfirmed("profiles", "id=eq.$pid", JSONObject().put("association_id", aid)) }
                }
            }
        }
    }
}

/* ============================== pieces ============================== */

@Composable
private fun OwnerHero(r: OrgRoot) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
        Column(Modifier.nourHeroFill(androidx.compose.material3.MaterialTheme.colorScheme.primary).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientIcon(Icons.Default.Public, HalaqatiGradients.Gold, tile = 56.dp, glyph = 28.dp)
                Column(Modifier.weight(1f)) {
                    Text("إدارة النظام", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("أنت: مسؤول النظام · تربط الجمعيات ومسؤوليها", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .92f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrgStat("جمعيات", r.associations.size.toString(), Modifier.weight(1f))
                OrgStat("مسؤولو جمعيات", r.associations.sumOf { it.admins.size }.toString(), Modifier.weight(1f))
                OrgStat("حلقات", r.associations.sumOf { it.halaqatCount }.toString(), Modifier.weight(1f))
                OrgStat("طلاب", r.associations.sumOf { it.students }.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AssociationHero(a: OrgAssociation, isOwner: Boolean, profile: Profile, onEdit: (() -> Unit)?) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
        Column(Modifier.nourHeroFill(androidx.compose.material3.MaterialTheme.colorScheme.primary).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrgLogo(a.logo, Icons.Default.AccountBalance, HalaqatiGradients.Gold, tile = 56.dp, glyph = 28.dp)
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 2)
                    Text("أنت: " + if (isOwner) "مسؤول النظام" else roleLabelArabic(profile.role), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .92f))
                }
                if (onEdit != null) IconButton(onClick = onEdit, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.Edit, "تعديل الجمعية والشعار", tint = Color.White) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrgStat("مشرفون", a.supervisors.size.toString(), Modifier.weight(1f))
                OrgStat("مراكز", a.centers.size.toString(), Modifier.weight(1f))
                OrgStat("حلقات", a.halaqatCount.toString(), Modifier.weight(1f))
                OrgStat("محفّظون", a.teachers.size.toString(), Modifier.weight(1f))
                OrgStat("طلاب", a.students.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun IndependentHero(halaqat: List<OrgHalaqa>, profile: Profile) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
        Column(Modifier.nourHeroFill(androidx.compose.material3.MaterialTheme.colorScheme.primary).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GradientIcon(Icons.Default.Groups, HalaqatiGradients.Gold, tile = 56.dp, glyph = 28.dp)
                Column(Modifier.weight(1f)) {
                    Text("الحلقات المستقلة", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("أنت: " + roleLabelArabic(profile.role) + " · حلقات لا تتبع جمعية", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = .92f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OrgStat("حلقات", halaqat.size.toString(), Modifier.weight(1f))
                OrgStat("محفّظون", halaqat.flatMap { it.teachers }.distinctBy { it.id }.size.toString(), Modifier.weight(1f))
                OrgStat("طلاب", halaqat.sumOf { it.students }.toString(), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OrgStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = .16f)) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .95f), maxLines = 1)
        }
    }
}

@Composable
private fun AssociationCard(a: OrgAssociation, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(24.dp), color = cs.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OrgLogo(a.logo, Icons.Default.AccountBalance, HalaqatiGradients.Gold, tile = 52.dp, glyph = 28.dp)
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                    Text(a.admins.firstOrNull()?.let { "المسؤول: " + it.name } ?: "لم يُعيَّن مسؤول بعد", style = MaterialTheme.typography.bodyMedium, color = if (a.admins.isEmpty()) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
                Icon(Icons.Default.ChevronLeft, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("مشرفون" to a.supervisors.size, "مراكز" to a.centers.size, "حلقات" to a.halaqatCount, "محفّظون" to a.teachers.size, "طلاب" to a.students).forEach { (l, v) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(v.toString(), style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), fontWeight = FontWeight.ExtraBold)
                        Text(l, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(step: Int, title: String, icon: ImageVector, pair: GradientPair, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(step.toString(), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.ExtraBold, fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily)
        }
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        if (action != null) FilledTonalButton(onClick = onAction, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
    }
}

@Composable
private fun MissingCard(text: String) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceVariant, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Text(text, Modifier.padding(14.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun PersonCard(p: OrgPerson, pair: GradientPair, roleText: String, halaqat: List<String>, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = cs.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(60.dp).background(Color.White, CircleShape).padding(3.dp)) { StudentAvatar(p.photo, p.name, size = 54.dp) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                Text(roleText + if (halaqat.isNotEmpty()) " · " + halaqat.joinToString("، ") else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.LinkOff, "إزالة من الجمعية", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun PersonChip(p: OrgPerson, supervisor: Boolean) {
    Surface(shape = RoundedCornerShape(50), color = if (supervisor) HalaqatiGradients.Gold.a else HalaqatiGradients.Emerald.a) {
        Row(Modifier.padding(start = 4.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StudentAvatar(p.photo, p.name, size = 26.dp)
            Text(p.name, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 130.dp))
        }
    }
}

@Composable
private fun OrgCenterCard(
    c: OrgCenter, open: Boolean, canEdit: Boolean, onToggle: () -> Unit,
    onEdit: (() -> Unit)?, onDelete: (() -> Unit)?, onMove: (OrgHalaqa) -> Unit, onLogo: (OrgHalaqa) -> Unit = {}, neutral: Boolean = false,
    onDetach: ((OrgHalaqa) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    val pair = if (neutral) HalaqatiGradients.Slate else kindPair(c.kind)
    var menu by remember { mutableStateOf(false) }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = cs.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).clickable(onClick = onToggle).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (c.logo != null) OrgLogo(c.logo, Icons.Default.AccountBalance, pair, tile = 52.dp, glyph = 28.dp)
                else Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (neutral) Icons.Default.FolderOpen else Icons.Default.AccountBalance, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp)) }
                }
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                    Text(
                        (if (neutral) "" else kindAr(c.kind) + " · ") + "${c.halaqat.size} حلقات · ${c.halaqat.sumOf { it.students }} طالبًا",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    c.manager?.let { Text((if (c.kind == "mosque") "مسؤول المسجد: " else "مدير المركز: ") + it.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface) }
                }
                if (canEdit && onEdit != null) Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, "إجراءات", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("تعديل") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onEdit() })
                        if (onDelete != null) DropdownMenuItem(text = { Text("حذف") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = { menu = false; onDelete() })
                    }
                }
                Icon(if (open) Icons.Default.ExpandLess else Icons.Default.ExpandMore, if (open) "طي" else "فتح", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AnimatedVisibility(visible = open) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (c.halaqat.isEmpty()) Text("لا توجد حلقات هنا بعد.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    c.halaqat.forEach { h -> OrgHalaqaRow(h, pair, canEdit, onMove, onLogo, onDetach) }
                }
            }
        }
    }
}

@Composable
private fun OrgHalaqaRow(h: OrgHalaqa, pair: GradientPair, canMove: Boolean, onMove: (OrgHalaqa) -> Unit, onLogo: (OrgHalaqa) -> Unit, onDetach: ((OrgHalaqa) -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(18.dp), color = cs.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OrgLogo(h.logo, Icons.Default.MenuBook, pair, tile = 40.dp, glyph = 22.dp)
                Column(Modifier.weight(1f)) {
                    Text(h.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
                    Text("${h.students} طالبًا", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                }
                if (canMove) IconButton(onClick = { onLogo(h) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.AddPhotoAlternate, "شعار الحلقة") }
                if (canMove) TextButton(onClick = { onMove(h) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("نقل") }
                if (onDetach != null) IconButton(onClick = { onDetach(h) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.LinkOff, "فصل الحلقة عن الجمعية", tint = MaterialTheme.colorScheme.error) }
            }
            if (h.supervisors.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("المشرف:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                h.supervisors.forEach { PersonChip(it, supervisor = true) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("المحفّظ:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                if (h.teachers.isEmpty()) Text("لم يُعيَّن بعد", style = MaterialTheme.typography.labelMedium, color = cs.error, fontWeight = FontWeight.Bold)
                h.teachers.forEach { PersonChip(it, supervisor = false) }
            }
        }
    }
}

@Composable
internal fun LogoPicker(logo: String?, onChange: (String?) -> Unit, fallback: ImageVector) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var failed by remember { mutableStateOf(false) }
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val data = com.mohammedsamir.halaqati.documents.DocumentImageEncoder.uriJpeg(context, uri, maxDimension = 384, quality = 85)
            if (data != null) { failed = false; onChange(data) } else failed = true
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OrgLogo(logo, fallback, HalaqatiGradients.Emerald, tile = 72.dp, glyph = 34.dp)
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (logo == null) "إضافة شعار" else "تغيير الشعار") }
            if (logo != null) TextButton(onClick = { onChange(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("إزالة") }
        }
        Text(if (failed) "تعذر قراءة الصورة" else "يُضغط الشعار تلقائيًا إلى أقل من 100 كيلوبايت.", style = MaterialTheme.typography.labelMedium, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntitySheet(title: String, label: String, name0: String, logo0: String?, onDismiss: () -> Unit, nameEditable: Boolean = true, onSave: (String, String?) -> Unit) {
    var name by remember { mutableStateOf(name0) }
    var logo by remember { mutableStateOf(logo0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            if (nameEditable) OutlinedTextField(name, { name = it.take(80) }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            LogoPicker(logo, { logo = it }, Icons.Default.AccountBalance)
            Button(enabled = name.trim().length >= 2, onClick = { onSave(name.trim(), logo) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("حفظ", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun PickSheet(title: String, hint: String, options: List<Pair<String, String>>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var selected by remember { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (options.isEmpty()) StateNotice("لا توجد خيارات متاحة الآن.", kind = "info")
            else HalaqatiLazySelectField(label = "الاختيار", selectedKey = selected.ifBlank { null }, options = options, onSelected = { selected = it }, modifier = Modifier.fillMaxWidth(), searchable = true)
            Button(enabled = selected.isNotBlank(), onClick = { onPick(selected) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("تأكيد", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun CenterSheet(existing: OrgCenter?, people: List<OrgPerson>, onDismiss: () -> Unit, onSave: (String, String, String?, String?) -> Unit) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(existing?.kind ?: "center") }
    var manager by remember { mutableStateOf(existing?.manager?.id.orEmpty()) }
    var logo by remember { mutableStateOf(existing?.logo) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (existing == null) "إضافة مركز أو مسجد" else "تعديل ${existing.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            OutlinedTextField(name, { name = it.take(80) }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ProFilterChip(kind == "center", { kind = "center" }, "مركز", showCheckIcon = false)
                ProFilterChip(kind == "mosque", { kind = "mosque" }, "مسجد", showCheckIcon = false)
            }
            HalaqatiLazySelectField(
                label = if (kind == "mosque") "مسؤول المسجد" else "مدير المركز", selectedKey = manager.ifBlank { null },
                options = people.map { it.id to it.name }, onSelected = { manager = it }, modifier = Modifier.fillMaxWidth(),
                allowEmpty = true, emptyKey = "", emptyOptionLabel = "بدون", searchable = true,
            )
            LogoPicker(logo, { logo = it }, Icons.Default.AccountBalance)
            Button(enabled = name.trim().length >= 2, onClick = { onSave(name.trim(), kind, manager.ifBlank { null }, logo) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("حفظ", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun MoveSheet(h: OrgHalaqa, centers: List<OrgCenter>, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    var target by remember { mutableStateOf(h.centerId.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("نقل ${h.name}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            HalaqatiLazySelectField(
                label = "المركز أو المسجد", selectedKey = target.ifBlank { null },
                options = centers.map { it.id to (kindAr(it.kind) + " · " + it.name) }, onSelected = { target = it }, modifier = Modifier.fillMaxWidth(),
                allowEmpty = true, emptyKey = "", emptyOptionLabel = "بدون مركز",
            )
            Button(onClick = { onPick(target.ifBlank { null }) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("نقل", fontWeight = FontWeight.Bold) }
        }
    }
}

/* ============================== home card ============================== */

/** Home card for admins / supervisors; for the system owner it opens the list of associations instead. */
/** Arabic count phrase: 0 → "بدون …", 1 → "جمعية واحدة", 2 → "جمعيتان", 3–10 → "5 جمعيات", more → "12 جمعية". */
internal fun countAr(n: Int, singular: String, one: String, two: String, plural: String): String = when {
    n <= 0 -> "بدون ${plural}"
    n == 1 -> one
    n == 2 -> two
    n in 3..10 -> "$n $plural"
    else -> "$n $singular"
}

@Composable
fun OrgSummaryCard(profile: Profile, navigate: (String) -> Unit, modifier: Modifier = Modifier) {
    var name by remember { mutableStateOf("") }
    var associations by remember { mutableIntStateOf(0) }
    var halaqat by remember { mutableIntStateOf(0) }
    var staff by remember { mutableIntStateOf(0) }
    val owner = PlatformOwnerState.isOwner
    LaunchedEffect(profile.id) {
        withContext(Dispatchers.IO) {
            val repo = AppGraph.repo
            val isOwner = runCatching { repo.table("profiles", "select=id,is_platform_owner&id=eq.${profile.id}&limit=1").sRows().firstOrNull()?.optBoolean("is_platform_owner", false) }.getOrNull() == true
            PlatformOwnerState.isOwner = isOwner
            name = runCatching { repo.mySubscription()?.optString("name") }.getOrNull().orEmpty()
            associations = runCatching { repo.table("associations", "select=id&limit=200").sRows().size }.getOrDefault(0)
            halaqat = runCatching { repo.table("halaqas", "select=id,active&limit=400").sRows().count { it.optBoolean("active", true) } }.getOrDefault(0)
            staff = runCatching { staffProfiles().size }.getOrDefault(0)
        }
    }
    NourCard(modifier, onClick = { navigate("org") }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NourIconBadge(if (owner) Icons.Default.Public else Icons.Default.AccountTree, tone = "warning", size = NourAvatarSize.Large)
            Column(Modifier.weight(1f)) {
                Text(if (owner) "إدارة الجمعيات" else name.ifBlank { "الهيكل الإداري" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (owner) "${countAr(associations, "جمعية", "جمعية واحدة", "جمعيتان", "جمعيات")} · ${countAr(halaqat, "حلقة", "حلقة واحدة", "حلقتان", "حلقات")}"
                    else "${countAr(halaqat, "حلقة", "حلقة واحدة", "حلقتان", "حلقات")} · ${countAr(staff, "عضو", "عضو واحد", "عضوان", "أعضاء")}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronLeft, null, modifier = Modifier.size(NourIcon.Action), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
