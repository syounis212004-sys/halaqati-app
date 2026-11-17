package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

private data class StaffMember(
    val id: String, val name: String, val role: String, val phone: String, val photo: String?, val active: Boolean,
    val halaqat: List<Pair<String, String>>, val students: Int,
    val sessionDays: Int, val recitations: Int, val lastSession: String, val visitsMade: Int, val visitsReceived: Int,
)

/**
 * RC90: «كادر الحلقات» — one directory of supervisors and teachers (replaces «فريق الجمعية»).
 * Each person shows the halaqat they really work in (supervisors from the supervisors link, teachers from the teachers link),
 * who has no halaqa yet, a month summary and direct call / WhatsApp. A supervisor only ever sees his own teachers.
 */
@Composable
fun TeamScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit = {}) {
    var members by remember { mutableStateOf<List<StaffMember>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("all") }
    var tick by remember { mutableIntStateOf(0) }
    val syncEpoch by AppGraph.connectivity.syncEpoch.collectAsState()
    LaunchedEffect(syncEpoch, tick) {
        com.mohammedsamir.halaqati.ui.runCatchingCancellable {
            withContext(Dispatchers.IO) {
                val repo = AppGraph.repo
                val today = LocalDate.now()
                val rows = repo.staffOverviewStored(today.withDayOfMonth(1).toString(), today.toString())
                    ?: throw IllegalStateException("لا توجد بيانات محفوظة بعد. افتح هذه الصفحة مرة واحدة وأنت متصل بالإنترنت.")
                val activeStudents = repo.tablePaged("students", "select=id,halaqa_id,status").sRows().filter { it.optString("status") == "active" }
                val perHalaqa = activeStudents.groupingBy { it.optString("halaqa_id") }.eachCount()
                rows.map { p ->
                    val hs = p.optJSONArray("halaqas")
                    val halaqat = if (hs == null) emptyList() else (0 until hs.length()).mapNotNull { i ->
                        hs.optJSONObject(i)?.let { it.optString("id") to it.supStr("name") }
                    }
                    val role = p.supStr("role")
                    StaffMember(
                        p.optString("id"), p.supStr("full_name"), role, p.supStr("phone"), p.supStr("avatar_path").ifBlank { null },
                        p.optBoolean("active", true) && !p.optBoolean("frozen", false),
                        halaqat, if (role == "supervisor") 0 else halaqat.sumOf { perHalaqa[it.first] ?: 0 },
                        p.optInt("session_days", 0), p.optInt("recitations", 0), p.supStr("last_session"),
                        p.optInt("visits_made", 0), p.optInt("visits_received", 0),
                    )
                }
            }
        }.onSuccess { members = it; error = null }.onFailure { error = it.message ?: "تعذر تحميل الكادر" }
        loading = false
    }
    val q = query.trim()
    val shown = members.filter { m ->
        (q.isBlank() || m.name.contains(q)) && when (filter) {
            "sup" -> m.role == "supervisor"
            "tch" -> m.role != "supervisor"
            "none" -> m.halaqat.isEmpty() && m.active
            else -> true
        }
    }
    val supervisors = shown.filter { it.role == "supervisor" }
    val teachers = shown.filter { it.role != "supervisor" }
    val noneCount = members.count { it.halaqat.isEmpty() && it.active }
    val canAssign = profile.role == UserRole.ADMIN
    Column(Modifier.fillMaxSize()) {
        AppTopBar("كادر الحلقات", onRefresh = { tick++; onRefresh() }, pending = pending)
        error?.let { StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        if (loading && members.isEmpty()) LoadingSkeleton(rows = 3, modifier = Modifier.padding(12.dp))
        LazyColumn(
            state = rememberLazyListState(), modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                com.mohammedsamir.halaqati.ui.components.NourStatStrip(listOf(
                    Triple(members.count { it.role == "supervisor" }.toString(), "مشرفون", false),
                    Triple(members.count { it.role != "supervisor" }.toString(), "محفّظون", false),
                    Triple(noneCount.toString(), "بلا حلقة", noneCount > 0),
                ))
            }
            item { CompactSearchField(value = query, onValueChange = { query = it }, placeholder = "بحث بالاسم", modifier = Modifier.fillMaxWidth()) }
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("all" to "الكل (${members.size})", "sup" to "المشرفون", "tch" to "المحفّظون", "none" to "بلا حلقة ($noneCount)").forEach { (k, l) ->
                        ProFilterChip(filter == k, { filter = k }, l, showCheckIcon = false)
                    }
                }
            }
            if (supervisors.isNotEmpty()) {
                item { com.mohammedsamir.halaqati.ui.components.NourSectionTitle("المشرفون (${supervisors.size})", entity = com.mohammedsamir.halaqati.ui.theme.Entity.SUPERVISOR) }
                items(supervisors, key = { "sup-" + it.id }) { StaffCard(it, canAssign, navigate, Modifier.animateItem()) }
            }
            if (teachers.isNotEmpty()) {
                item { com.mohammedsamir.halaqati.ui.components.NourSectionTitle("المحفّظون (${teachers.size})", entity = com.mohammedsamir.halaqati.ui.theme.Entity.TEACHER) }
                items(teachers, key = { "tch-" + it.id }) { StaffCard(it, canAssign, navigate, Modifier.animateItem()) }
            }
            if (!loading && shown.isEmpty()) item { EmptyState(if (members.isEmpty()) "لا يوجد مشرفون ولا محفّظون بعد" else "لا يوجد من يطابق البحث") }
        }
    }
}

@Composable
private fun StaffCard(m: StaffMember, canAssign: Boolean, navigate: (String) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val context = androidx.compose.ui.platform.LocalContext.current
    val supervisor = m.role == "supervisor"
    val pair = when {
        !m.active -> HalaqatiGradients.Slate
        supervisor -> HalaqatiGradients.Gold
        else -> HalaqatiGradients.Emerald
    }
    val edge = if (!m.active) cs.outline else if (supervisor) cs.tertiary else cs.primary
    var open by rememberSaveable(m.id) { mutableStateOf(false) }
    val reduced = com.mohammedsamir.halaqati.ui.theme.LocalReducedMotion.current
    val turn by androidx.compose.animation.core.animateFloatAsState(
        if (open) 180f else 0f, androidx.compose.animation.core.tween(if (reduced) 0 else 240, easing = androidx.compose.animation.core.FastOutSlowInEasing), label = "staffChevron",
    )
    val roleLabel = if (!m.active) "موقوف" else if (supervisor) "مشرف" else "محفّظ"
    val count = m.halaqat.size
    val countText = when (count) { 0 -> "بلا حلقة"; 1 -> "حلقة واحدة"; 2 -> "حلقتان"; else -> "$count حلقات" }
    val summary = if (supervisor || count == 0) countText else "$countText · ${m.students} طالبًا"
    Surface(
        modifier.fillMaxWidth().then(if (reduced) Modifier else Modifier.animateContentSize()),
        shape = RoundedCornerShape(18.dp), color = cs.surface, border = BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Column {
            com.mohammedsamir.halaqati.ui.components.EntityBand(if (supervisor) com.mohammedsamir.halaqati.ui.theme.Entity.SUPERVISOR else com.mohammedsamir.halaqati.ui.theme.Entity.TEACHER)
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.drawBehind {
                    // RC96: a 5dp colour edge on the reading-start side says the role at a glance (gold supervisor, emerald teacher).
                    val w = 5.dp.toPx()
                    drawRect(edge, androidx.compose.ui.geometry.Offset(if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) size.width - w else 0f, 0f), androidx.compose.ui.geometry.Size(w, size.height))
                }.background(if (!m.active) cs.surfaceVariant else if (supervisor) cs.tertiaryContainer.copy(alpha = .45f) else cs.primaryContainer.copy(alpha = .45f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StudentAvatar(m.photo, m.name, size = com.mohammedsamir.halaqati.ui.components.NourAvatarSize.Medium)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(m.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp), fontWeight = FontWeight.ExtraBold, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$roleLabel · $summary", style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (count == 0 && m.active) Surface(shape = RoundedCornerShape(50), color = cs.tertiaryContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(androidx.compose.material.icons.Icons.Default.Info, null, Modifier.size(16.dp), tint = cs.onTertiaryContainer)
                        Text("بلا حلقة", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = cs.onTertiaryContainer)
                    }
                } else Surface(shape = RoundedCornerShape(50), color = if (!m.active) cs.surface else if (supervisor) cs.tertiaryContainer else cs.primaryContainer) {
                    Text(roleLabel, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold, color = if (supervisor && m.active) cs.onTertiaryContainer else cs.onPrimaryContainer)
                }
                Icon(Icons.Default.ExpandMore, contentDescription = if (open) "طي" else "تفاصيل", tint = cs.onSurfaceVariant, modifier = Modifier.graphicsLayer { rotationZ = turn })
            }
            androidx.compose.animation.AnimatedVisibility(
                visible = open,
                enter = com.mohammedsamir.halaqati.ui.theme.motionEnter(androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(240)) + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200, delayMillis = 50))),
                exit = com.mohammedsamir.halaqati.ui.theme.motionExit(androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))),
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (m.halaqat.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        m.halaqat.forEach { h ->
                            Surface(shape = RoundedCornerShape(50), color = cs.secondaryContainer, modifier = Modifier.clickable { navigate("halaqas") }) {
                                Text(h.second, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = cs.onSecondaryContainer, maxLines = 1)
                            }
                        }
                    } else Text("لم يُسنَد إلى حلقة بعد", style = MaterialTheme.typography.bodyMedium, color = cs.error, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (supervisor) {
                            StaffMini(m.visitsMade.toString(), "زيارات الشهر", Modifier.weight(1f))
                            StaffMini(m.visitsReceived.toString(), "زيارات لحلقاته", Modifier.weight(1f))
                        } else {
                            StaffMini(m.sessionDays.toString(), "أيام جلسات", Modifier.weight(1f))
                            StaffMini(m.recitations.toString(), "تسميعات", Modifier.weight(1f))
                        }
                        StaffMini(if (m.lastSession.length >= 10) m.lastSession.substring(5, 10) else "—", "آخر جلسة", Modifier.weight(1f))
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (m.phone.isNotBlank()) {
                            FilledTonalButton(onClick = {
                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + m.phone)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }, modifier = Modifier.heightIn(min = 48.dp)) { Icon(Icons.Default.Call, null); Spacer(Modifier.width(6.dp)); Text("اتصال", fontWeight = FontWeight.Bold) }
                            OutlinedButton(onClick = {
                                val digits = m.phone.filter { it.isDigit() }
                                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://wa.me/$digits")).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("واتساب", fontWeight = FontWeight.Bold) }
                        } else Text("لا يوجد رقم هاتف", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterVertically))
                        if (canAssign) OutlinedButton(onClick = { navigate("halaqas") }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Default.GroupAdd, null); Spacer(Modifier.width(6.dp)); Text("إسناد حلقات", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StaffMini(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f)) {
        Column(Modifier.padding(vertical = 6.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

