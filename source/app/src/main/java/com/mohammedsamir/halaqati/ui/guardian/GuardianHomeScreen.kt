package com.mohammedsamir.halaqati.ui.guardian

import com.mohammedsamir.halaqati.ui.components.nourHeroFill
import com.mohammedsamir.halaqati.ui.theme.calmContentSize
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.TrendingUp
import com.mohammedsamir.halaqati.ui.components.latticeOverlay
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens
import java.util.Locale

/** RC17.1 guardian home: explicit Hifz/Sard plan sections, RTL-first hierarchy and cache-first refresh. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardianHomeScreen(
    navigate: (String) -> Unit,
    vm: GuardianHomeViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.start() }
    val overview = state.overview

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "اطمئن على رحلته", kicker = "ولي الأمر · متابعة الأبناء")
        PullToRefreshBox(
            isRefreshing = state.refreshing && overview != null,
            onRefresh = { vm.refresh() },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = HalaqatiUiTokens.ScreenHorizontal,
                    end = HalaqatiUiTokens.ScreenHorizontal,
                    top = 4.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (overview == null) item(key = "role-welcome", contentType = "welcome") { com.mohammedsamir.halaqati.ui.components.RoleWelcomeCard(com.mohammedsamir.halaqati.model.UserRole.GUARDIAN) }
                item(key = "add-child", contentType = "add-child") { com.mohammedsamir.halaqati.ui.screens.AddChildCard() }
                item(key = "contests-card", contentType = "contests") { com.mohammedsamir.halaqati.ui.screens.ContestsHomeCard(navigate) }
                if (state.children.size > 1) {
                    item(key = "switcher", contentType = "switcher") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(state.children, key = { it.id }) { child ->
                                ProFilterChip(
                                    selected = child.id == state.selectedId,
                                    onClick = { vm.select(child.id) },
                                    label = child.name.substringBefore(' '),
                                )
                            }
                        }
                    }
                }
                if (state.stale && overview != null) {
                    item(key = "stale", contentType = "notice") {
                        StateNotice("تعرض آخر بيانات محفوظة${overview.updatedAt?.let { " · " + shortTime(it) } ?: ""}", kind = "warning")
                    }
                }
                state.message?.let { msg -> item(key = "msg", contentType = "notice") { StateNotice(msg, kind = "error") } }
                when {
                    overview == null && (state.loadingFirst || state.refreshing) ->
                        item(key = "loading", contentType = "loading") { LoadingSkeleton(rows = 3) }
                    overview == null && state.children.isEmpty() && !state.loadingFirst ->
                        item(key = "empty", contentType = "empty") { EmptyState("لا توجد ملفات طلاب مرتبطة بهذا الحساب") }
                    overview != null -> {
                        item(key = "today", contentType = "today") { TodayCard(overview) }
                        item(key = "today-detail", contentType = "today-detail") { TodayDetailGrid(overview) }
                        GuardianPraise.lines(overview).takeIf { it.isNotEmpty() }?.let { praise ->
                            item(key = "praise", contentType = "praise") {
                                com.mohammedsamir.halaqati.ui.components.InsightCardShell(
                                    kind = "success",
                                    icon = Icons.Default.EmojiEvents,
                                    title = praise.first(),
                                    summary = praise.drop(1).joinToString(" · ").ifBlank { "بارك الله فيه وزاده حرصًا" },
                                )
                            }
                        }
                        if (overview.planTitle != null) item(key = "plan", contentType = "plan") { PlanCard(overview) }
                        item(key = "metrics", contentType = "metrics") { MetricsStrip(overview) }
                        item(key = "weekly", contentType = "weekly") { WeeklySummaryCard(overview) }
                        if (overview.latest.isNotEmpty()) {
                            item(key = "latest-title", contentType = "title") {
                                Text("آخر التسميعات", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            items(overview.latest, key = { "${it.date}:${it.activityLabel}:${it.range}" }, contentType = { "recitation" }) { line ->
                                RecitationRow(line)
                            }
                        }
                        state.selectedId?.let { sid ->
                            item(key = "badges", contentType = "badges") { GuardianBadgesCard(sid) }
                        }
                        item(key = "seasons", contentType = "seasons") {
                            com.mohammedsamir.halaqati.ui.components.SeasonsCard(canManage = false)
                        }
                        item(key = "share-report", contentType = "share") {
                            val context = LocalContext.current
                            OutlinedButton(
                                onClick = {
                                    val today = java.time.LocalDate.now()
                                    val text = GuardianReportText.build(overview, today.monthValue, today.year)
                                    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
                                        .setType("text/plain")
                                        .putExtra(android.content.Intent.EXTRA_TEXT, text)
                                    runCatching { context.startActivity(android.content.Intent.createChooser(send, "مشاركة تقرير الشهر")) }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("مشاركة تقرير الشهر")
                            }
                        }
                        item(key = "shortcuts", contentType = "shortcuts") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { navigate("guardian_profile") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("ملف الابن") }
                                OutlinedButton(onClick = { navigate("guardian_achievements") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("الإنجاز") }
                                OutlinedButton(onClick = { navigate("guardian_competition") }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("المنافسة") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TodayCard(o: GuardianOverview) {
    val attendance = when (o.todayAttendance) {
        "present" -> "حضر اليوم"
        "late" -> "حضر اليوم (متأخر)"
        "absent" -> "غائب اليوم"
        "excused" -> "غائب بعذر اليوم"
        null -> when {
            o.todaySessionExists == false -> "لا توجد جلسة اليوم"
            o.todaySessionStarted == false -> "الجلسة لم تبدأ بعد"
            else -> "لم يُسجَّل الحضور بعد"
        }
        else -> "الحضور: ${o.todayAttendance}"
    }
    val onP = androidx.compose.ui.graphics.Color.White   // the hero fill is always dark (NourHeroColors), so its text is white in both themes
    val phrase = remember { com.mohammedsamir.halaqati.ui.dashboard.RolePhrases.forRole(com.mohammedsamir.halaqati.model.UserRole.GUARDIAN.name, java.time.LocalDate.now().toEpochDay()) }
    val greeting = remember { com.mohammedsamir.halaqati.ui.dashboard.RolePhrases.greeting(java.time.LocalTime.now().hour) }
    Card(
        Modifier.fillMaxWidth().calmContentSize(),
        shape = RoundedCornerShape(HalaqatiUiTokens.RadiusHero),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = onP),
    ) {
        Box(Modifier.nourHeroFill(androidx.compose.material3.MaterialTheme.colorScheme.primary)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // RC83: greeting + verse (one card with the child, not two)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(greeting, style = MaterialTheme.typography.labelLarge, color = onP.copy(alpha = .92f))
                    Text(phrase.text, style = MaterialTheme.typography.titleMedium, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(phrase.source, style = MaterialTheme.typography.labelMedium, color = onP.copy(alpha = .92f))
                }
                Spacer(Modifier.fillMaxWidth().height(1.dp).background(onP.copy(alpha = .25f)))
                // The child: big photo, name, track and halaqa, today's state as two chips
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    com.mohammedsamir.halaqati.ui.components.StudentAvatar(o.childPhoto, o.childName, 72.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(o.childName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(o.trackLabel, o.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.labelLarge, color = onP.copy(alpha = .92f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        }
                }
                // RC104: today's two states sit side by side under the child (they used to wrap into two tall rows beside the photo).
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(50), color = onP.copy(alpha = .2f)) {
                        Text(attendance, Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Surface(Modifier.weight(1f), shape = RoundedCornerShape(50), color = onP.copy(alpha = if (o.todayRecited) .32f else .14f)) {
                        Text(
                            if (o.todayRecited) "أتمّ ${if (o.tathbit) "التثبيت" else "التسميع"}" else "لم يُسجَّل ${if (o.tathbit) "التثبيت" else "التسميع"}",
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Today's ward in a clear inset panel
                Surface(shape = RoundedCornerShape(16.dp), color = onP.copy(alpha = .14f), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("مقرر اليوم", style = MaterialTheme.typography.labelMedium, color = onP.copy(alpha = .92f))
                        Text(o.todayWard ?: "لا يوجد مقرر اليوم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (o.todayRecited) {
                            o.todayRange?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = onP.copy(alpha = .92f)) }
                            Text("${if (o.tathbit) "تثبيت" else "تسميع"} اليوم: ${num(o.todayPages)} صفحة", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

/** RC52: "اطمئن على رحلته" at a glance: today's ward, recited?, attendance, mistakes and quality of the same day. */
@Composable
private fun TodayDetailGrid(o: GuardianOverview) {
    val last = o.lastDay
    val dayLabel = when {
        last == null -> "آخر يوم تسميع"
        last.isToday -> "اليوم"
        else -> "آخر يوم تسميع · ${last.date}"
    }
    Card(
        Modifier.fillMaxWidth().calmContentSize(),
        shape = RoundedCornerShape(HalaqatiUiTokens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("جودة آخر تسميع", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(dayLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailTile("الأخطاء", last?.mistakes?.toString() ?: "—", if ((last?.mistakes ?: 0) > 3) "warn" else "ok", Modifier.weight(1f))
                DetailTile("التنبيهات", last?.prompts?.toString() ?: "—", "ok", Modifier.weight(1f))
                DetailTile("الجودة", last?.quality?.let { "${num(it)}٪" } ?: "—", "ok", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DetailTile(label: String, value: String, tone: String, modifier: Modifier) {
    val color = when (tone) {
        "ok" -> MaterialTheme.colorScheme.primary
        "bad" -> MaterialTheme.colorScheme.error
        "warn" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 11.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun PlanCard(o: GuardianOverview) {
    Card(
        Modifier.fillMaxWidth().calmContentSize(),
        shape = RoundedCornerShape(HalaqatiUiTokens.RadiusCard),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(o.planTitle.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (o.planActivities.size > 1) "الحفظ والسرد محسوبان كلٌّ على حدة" else "التقدم بحسب هدف هذا الشهر",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (o.planComplete) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (o.planActivities.isEmpty()) {
                Text("لا توجد تفاصيل تقدم متاحة للخطة", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                o.planActivities.forEach { activity -> PlanActivityRow(activity) }
            }
        }
    }
}

@Composable
private fun PlanActivityRow(activity: GuardianPlanActivity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(activity.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(
                    if (activity.hasTarget) "${num(activity.done)} / ${num(activity.target)} ص" else "${num(activity.done)} ص",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (activity.hasTarget) {
                LinearProgressIndicator(
                    drawStopIndicator = {}, progress = { (activity.percent / 100.0).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    trackColor = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${num(activity.percent)}٪", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    when {
                        activity.surplus > 0.0001 -> {
                            Icon(Icons.Default.TrendingUp, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(4.dp))
                            Text("+${num(activity.surplus)} ص فوق الهدف", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        activity.complete -> Text("مكتمل", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        else -> Text("المتبقي ${num(activity.remaining)} ص", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text("إنجاز مسجل · لم يحدد للخطة هدف سرد مستقل على هذا الخادم", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MetricsStrip(o: GuardianOverview) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric(o.monthMainLabel, "${num(o.monthMain)} ص", Modifier.weight(1f))
        Metric("الحضور", "${o.monthPresent}/${o.monthSessions}", Modifier.weight(1f))
        Metric("الجودة", o.quality?.let { "${num(it)}٪" } ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(HalaqatiUiTokens.RadiusMetric), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(vertical = 14.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun RecitationRow(line: GuardianRecitationLine) {
    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${line.activityLabel} · ${line.date}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${num(line.pages)} ص${line.quality?.let { " · ${num(it)}٪" } ?: ""}", style = MaterialTheme.typography.labelMedium)
            }
            Text(line.range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun num(v: Double): String {
    val r = kotlin.math.round(v * 100) / 100
    return if (kotlin.math.abs(r - r.toLong()) < 1e-6) r.toLong().toString() else String.format(Locale.US, "%.2f", r).trimEnd('0').trimEnd('.')
}

private fun shortTime(iso: String): String = com.mohammedsamir.halaqati.ui.components.GazaTime.shortDateTime(iso).takeIf { iso.isNotBlank() }.orEmpty()
