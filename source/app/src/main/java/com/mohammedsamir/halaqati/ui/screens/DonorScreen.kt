@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import com.mohammedsamir.halaqati.ui.components.nourHeroFill
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.mohammedsamir.halaqati.ui.components.GradientIcon
import com.mohammedsamir.halaqati.ui.components.HalaqatiGradients
import com.mohammedsamir.halaqati.ui.components.StudentAvatar
import com.mohammedsamir.halaqati.ui.components.girih
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.latticeOverlay
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.components.SegmentedTabs
import com.mohammedsamir.halaqati.ui.components.PillChip
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.components.animatedCountText
import com.mohammedsamir.halaqati.ui.components.halaqatiEnter
import com.mohammedsamir.halaqati.ui.components.heroBackground
import com.mohammedsamir.halaqati.ui.components.heroBrush
import com.mohammedsamir.halaqati.ui.theme.cardHairline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** RC50: the donor's home is a summary of everything (halaqa month figures, stars, support, impact, news); each section opens its page. */
@Composable
fun DonorHomeScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit = {}) {
    var tick by remember { mutableIntStateOf(0) }
    var viewCurrent by rememberSaveable { mutableStateOf(true) }
    var reports by remember { mutableStateOf<Map<String, JSONObject>>(emptyMap()) }
    var photos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var halaqaNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var starWinners by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var awardMonth by remember { mutableStateOf<String?>(null) }
    var openCards by remember { mutableIntStateOf(0) }
    var needProof by remember { mutableIntStateOf(0) }
    var pledgeCount by remember { mutableIntStateOf(0) }
    var deliveredCount by remember { mutableIntStateOf(0) }
    var beneficiaries by remember { mutableIntStateOf(0) }
    var news by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val thisMonth = remember { java.time.YearMonth.now() }
    val shownMonth = (if (viewCurrent) thisMonth else thisMonth.minusMonths(1)).toString()
    val report = reports[shownMonth]

    LaunchedEffect(tick) {
        loading = true
        withContext(Dispatchers.IO) {
            val months = runCatching { AppGraph.repo.awardMonths() }.getOrDefault(emptyList())
            val latest = months.firstOrNull()?.optString("month")?.take(7)
            awardMonth = latest
            photos = latest?.let { runCatching { AppGraph.repo.donorAwardPhotos(it) }.getOrDefault(emptyMap()) }.orEmpty()
            val payload = latest?.let { runCatching { AppGraph.repo.donorAwards("$it-01", "halaqa") }.getOrNull() }
            val groups = payload?.optJSONArray("groups")?.awardObjects().orEmpty()
            halaqaNames = groups.map { it.optString("halaqa_name") }.filter { it.isNotBlank() && it != "null" }.distinct()
            starWinners = groups.mapNotNull { g ->
                g.optJSONArray("winners")?.awardObjects()?.firstOrNull { it.optInt("rank") == 1 }
                    ?.let { w -> JSONObject(w.toString()).put("category_name", g.optString("category_name")) }
            }
            // RC83: this month (so far) and the previous five, kept on the device; only this month and last are re-read once per cycle.
            val lastMonth = thisMonth.minusMonths(1).toString()
            reports = (5 downTo 0).map { thisMonth.minusMonths(it.toLong()).toString() }.mapNotNull { m ->
                runCatching { AppGraph.repo.donorReportStored(m, volatile = m >= lastMonth) }.getOrNull()?.let { m to it }
            }.toMap()
            openCards = runCatching { AppGraph.repo.supportCards(tick > 0).size }.getOrDefault(0)
            val mine = runCatching { AppGraph.repo.myPledges(tick > 0) }.getOrDefault(emptyList())
            pledgeCount = mine.size
            deliveredCount = mine.count { it.optString("status") == "delivered" }
            needProof = mine.count { (it.optString("status") == "pledged" || it.optString("status") == "received") && (it.optString("transfer_submitted_at").isBlank() || it.optString("transfer_submitted_at") == "null") }
            beneficiaries = mine.filter { it.optString("status") == "delivered" }.sumOf { it.optInt("beneficiaries", 0) }
            news = runCatching { AppGraph.repo.donorNews(tick > 0).take(2) }.getOrDefault(emptyList())
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "الرئيسية", kicker = "أهلًا بك، " + profile.fullName.trim().substringBefore(' '), onRefresh = { tick++; onRefresh() }, pending = pending)
        LazyColumn(
            state = rememberLazyListState(), modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(28.dp), color = Color.Transparent) {
                    Column(
                        Modifier.nourHeroFill(androidx.compose.material3.MaterialTheme.colorScheme.primary).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("السلام عليكم ورحمة الله", style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = .9f))
                                Text("شكرًا لدعمك", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                            GradientIcon(Icons.Default.Favorite, HalaqatiGradients.Gold, tile = 52.dp, glyph = 28.dp)
                        }
                        if (halaqaNames.isNotEmpty()) Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = .2f)) {
                            Text("حلقتك: " + halaqaNames.joinToString("، "), Modifier.padding(horizontal = 14.dp, vertical = 6.dp), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        val donorPhrase = remember { com.mohammedsamir.halaqati.ui.dashboard.RolePhrases.forRole("observer", java.time.LocalDate.now().toEpochDay()) }
                        Surface(shape = RoundedCornerShape(18.dp), color = Color.Black.copy(alpha = .24f), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    donorPhrase.text,
                                    style = MaterialTheme.typography.titleLarge, fontFamily = com.mohammedsamir.halaqati.ui.theme.AmiriFamily,
                                    fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp, color = Color.White,
                                )
                                Text("— " + donorPhrase.source, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = HalaqatiGradients.Gold.b)
                            }
                        }
                    }
                }
            }
            if (loading && report == null) item { LoadingSkeleton(rows = 3) }
            run {
                val baseMonth = if (viewCurrent) thisMonth.minusMonths(1) else thisMonth.minusMonths(2)
                val base = reports[baseMonth.toString()]
                val prorata = if (viewCurrent) java.time.LocalDate.now().dayOfMonth.toDouble() / thisMonth.lengthOfMonth() else 1.0
                item {
                    SummaryCard("ملخص حلقتك · " + awardMonthLabel(shownMonth) + if (viewCurrent) " (حتى اليوم)" else "", "الإحصاءات", { navigate("donor_stats") }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ProFilterChip(viewCurrent, { viewCurrent = true }, "هذا الشهر", modifier = Modifier.weight(1f), showCheckIcon = false)
                            ProFilterChip(!viewCurrent, { viewCurrent = false }, "الشهر الماضي", modifier = Modifier.weight(1f), showCheckIcon = false)
                        }
                        if (report == null) {
                            Text(if (loading) "جارٍ التحميل…" else "لا توجد بيانات لهذا الشهر بعد.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            val r = report
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SummaryTile("طالبًا", r.optInt("students").toString(), Modifier.weight(1f))
                                SummaryTile("جلسة", r.optInt("sessions").toString(), Modifier.weight(1f))
                                SummaryTile("مكرَّمون", if (viewCurrent) "—" else r.optInt("winners").toString(), Modifier.weight(1f))
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                TrendTile("صفحة حفظ", awardNum(r.optDouble("hifz_pages")), base?.let { trendText(r.optDouble("hifz_pages"), it.optDouble("hifz_pages"), prorata) }, Modifier.weight(1f))
                                TrendTile("صفحة مراجعة", awardNum(r.optDouble("review_pages")), base?.let { trendText(r.optDouble("review_pages"), it.optDouble("review_pages"), prorata) }, Modifier.weight(1f))
                                AttendanceTile(if (r.isNull("attendance_pct")) null else r.optInt("attendance_pct"), Modifier.weight(1f))
                            }
                            if (base != null) Text(
                                if (viewCurrent) "الأسهم: مقارنة بالفترة نفسها من الشهر الماضي (تقريبية)" else "الأسهم: مقارنة بالشهر الذي قبله",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            val series = (5 downTo 0).mapNotNull { reports[thisMonth.minusMonths(it.toLong()).toString()]?.optDouble("hifz_pages") }
                            if (series.size >= 2) {
                                Text("صفحات الحفظ · آخر ${series.size} أشهر", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                Sparkline(series)
                            }
                        }
                    }
                }
            }
            item {
                SummaryCard("نجوم الشهر", "كل المكرَّمين", { navigate("donor_awards") }) {
                    if (starWinners.isEmpty()) {
                        Text("لم يُعتمد تكريم بعد. سيصلك إشعار عند اعتماد المدير.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        awardMonth?.let { Text(awardMonthLabel(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary) }
                        starWinners.forEach { w ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MedalAvatar(1, w.optString("name"), 44, photos[w.optString("student_id")])
                                Column(Modifier.weight(1f)) {
                                    Text(w.optString("name"), style = MaterialTheme.typography.titleSmall)
                                    Text(w.optString("category_name") + " · " + awardNum(w.optDouble("score")) + " / 100", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item {
                SummaryCard("الدعم", "افتح الدعم", { navigate("donor_support") }) {
                    Text(if (openCards > 0) "$openCards بطاقة دعم مفتوحة تنتظر كفلاء." else "لا توجد بطاقات دعم مفتوحة الآن.", style = MaterialTheme.typography.bodyMedium)
                    if (needProof > 0) {
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .7f)) {
                            Text("لديك $needProof كفالة تنتظر إرسال إثبات التحويل.", Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                SummaryCard("أثري", "التفاصيل", { navigate("donor_impact") }) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SummaryTile("كفالة", pledgeCount.toString(), Modifier.weight(1f))
                        SummaryTile("وصلت", deliveredCount.toString(), Modifier.weight(1f))
                        SummaryTile("طالب استفاد", beneficiaries.toString(), Modifier.weight(1f))
                    }
                }
            }
            if (news.isNotEmpty()) item {
                SummaryCard("آخر أخبار الحلقة", "أثري", { navigate("donor_impact") }) {
                    news.forEach { n ->
                        Column {
                            Text(n.optString("title"), style = MaterialTheme.typography.titleSmall)
                            n.optString("body").takeIf { it.isNotBlank() && it != "null" }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(title: String, action: String, onAction: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.mohammedsamir.halaqati.ui.components.NourSectionTitle(title, Modifier.weight(1f), entity = com.mohammedsamir.halaqati.ui.theme.Entity.HALAQA)
                TextButton(onClick = onAction) { Text(action + " ‹") }
            }
            content()
        }
    }
}

@Composable
private fun SummaryTile(label: String, value: String, modifier: Modifier) {
    // RC104: the shared stat tile (tonal gradient, same as the hero strips) instead of a flat grey tile.
    com.mohammedsamir.halaqati.ui.components.NourStatTile(animatedCountText(value), label, modifier)
}

private fun trendText(now: Double, base: Double, prorata: Double): Pair<String, Boolean>? {
    val b = base * prorata
    if (b <= 0.5) return null
    val pct = Math.round((now - b) / b * 100.0).toInt()
    return (if (pct >= 0) "▲ $pct٪" else "▼ ${-pct}٪") to (pct >= 0)
}

@Composable
private fun TrendTile(label: String, value: String, trend: Pair<String, Boolean>?, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
        Column(Modifier.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(animatedCountText(value), style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily), color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (trend != null) Text(trend.first, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (trend.second) Color(0xFF1B7F4B) else MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AttendanceTile(pct: Int?, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .55f)) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(progress = { (pct ?: 0) / 100f }, modifier = Modifier.size(50.dp), strokeWidth = 5.dp, trackColor = MaterialTheme.colorScheme.surface)
                Text(if (pct == null) "—" else "$pct٪", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.ExtraBold)
            }
            Text("حضور", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Oldest month on the right (Arabic reading order), newest on the left. */
@Composable
private fun Sparkline(values: List<Double>) {
    val color = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        val top = (values.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
        val stepX = if (values.size > 1) size.width / (values.size - 1) else size.width
        val pts = values.mapIndexed { i, v ->
            androidx.compose.ui.geometry.Offset(size.width - i * stepX, size.height - 4f - (v / top).toFloat() * (size.height - 8f))
        }
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(pts.first().x, pts.first().y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
        pts.forEach { drawCircle(color, 4.dp.toPx(), it) }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun HowAwardsWork() {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.5.dp, border = cardHairline()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("كيف احتُسب التكريم؟", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text("تُجمع خمسة معايير في درجة من 100، ويعتمد المدير النتيجة بعد انتهاء الشهر.", style = MaterialTheme.typography.bodyMedium)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("الحفظ", "السرد", "الحضور", "السلوك", "جودة التسميع").forEach { c ->
                    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text(c, Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Text("المستويات: ذهبي للمركز الأول، فضي للثاني، برونزي للثالث.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DonorPage(title: String, profile: Profile, pending: Int, onRefresh: () -> Unit, content: @Composable (Int) -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = title, kicker = "أهلًا بك، " + profile.fullName.trim().substringBefore(' '), onRefresh = { tick++; onRefresh() }, pending = pending)
        Box(Modifier.weight(1f)) { content(tick) }
    }
}

@Composable
fun DonorAwardsPage(profile: Profile, pending: Int, onRefresh: () -> Unit) = DonorPage("المكرَّمون", profile, pending, onRefresh) { DonorAwardsTab(it) }

@Composable
fun DonorSupportPage(profile: Profile, pending: Int, onRefresh: () -> Unit) = DonorPage("الدعم", profile, pending, onRefresh) { tick ->
    var section by rememberSaveable { mutableIntStateOf(0) }
    val chatScope = rememberCoroutineScope()
    var chatId by remember { mutableStateOf<String?>(null) }
    var chatError by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        chatError?.let { com.mohammedsamir.halaqati.ui.components.StateNotice(it, kind = "error", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
        SegmentedTabs(listOf("الكفالات والدعم", "طلبات المكافآت"), section, { section = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        com.mohammedsamir.halaqati.ui.components.TabContentTransition(section, { it }, Modifier.weight(1f)) { s ->
            if (s == 0) DonorSupportTab(tick) else DonorRewardsTab(tick)
        }
    }
    // RC104: the chat with the administrator is a side button (like announcements and visits) instead of a bar above the tabs.
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = {
                    chatScope.launch {
                        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.mohammedsamir.halaqati.data.AppGraph.repo.chatOpenDirect(profile.id) } }
                            .onSuccess { chatId = it; chatError = null }
                            .onFailure { chatError = it.message ?: "تعذر فتح المحادثة" }
                    }
                },
        icon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Default.Forum, contentDescription = null) },
        text = { Text("محادثة المدير") },
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = com.mohammedsamir.halaqati.ui.components.FabMargin, bottom = com.mohammedsamir.halaqati.ui.components.FabMargin),
    )
    }
    chatId?.let { id -> ChatSheet(id, "محادثة مع المدير") { chatId = null } }
}

@Composable
fun DonorImpactPage(profile: Profile, pending: Int, onRefresh: () -> Unit) = DonorPage("أثري", profile, pending, onRefresh) { DonorImpactTab(it) }

@Composable
private fun DonorAwardsTab(refreshTick: Int) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var months by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
    var month by remember { mutableStateOf<String?>(null) }
    var scopeKind by remember { mutableStateOf("halaqa") }
    var data by remember { mutableStateOf<JSONObject?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf<JSONObject?>(null) }
    var rewardFor by remember { mutableStateOf<JSONObject?>(null) }
    var rewardNote by remember { mutableStateOf("") }
    var notice by remember { mutableStateOf<String?>(null) }
    var photos by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    fun loadMonths() {
        scope.launch {
            loading = true; error = null
            com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.awardMonths() } }
                .onSuccess { list ->
                    months = list
                    month = month?.takeIf { m -> list.any { it.optString("month").take(7) == m } } ?: list.firstOrNull()?.optString("month")?.take(7)
                    if (list.isEmpty()) data = null
                }
                .onFailure { error = it.message ?: "تعذر التحميل" }
            loading = false
        }
    }
    LaunchedEffect(refreshTick) { loadMonths() }
    LaunchedEffect(month, scopeKind) {
        val m = month ?: return@LaunchedEffect
        loading = true
        com.mohammedsamir.halaqati.ui.runCatchingCancellable { withContext(Dispatchers.IO) { AppGraph.repo.donorAwards("$m-01", scopeKind) } }
            .onSuccess { data = it; error = null }
            .onFailure { error = it.message ?: "تعذر التحميل"; data = null }
        loading = false
    }

    LaunchedEffect(month) {
        val m = month ?: return@LaunchedEffect
        photos = withContext(Dispatchers.IO) { runCatching { AppGraph.repo.donorAwardPhotos(m) }.getOrDefault(emptyMap()) }
    }
    val groups = data?.optJSONArray("groups")?.awardObjects().orEmpty()
    fun open(intent: Intent) = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            notice?.let { n -> item { StateNotice(n, "success") } }
            error?.let { item { StateNotice(it, "warning") } }
            if (months.isNotEmpty()) item {
                com.mohammedsamir.halaqati.ui.components.MonthPicker(months.map { it.optString("month").take(7) }, month, { month = it })
            }
            if (months.isNotEmpty()) item {
                SegmentedTabs(listOf("حسب الحلقة", "حسب الفئة"), if (scopeKind == "halaqa") 0 else 1, { scopeKind = if (it == 0) "halaqa" else "category" })
            }
            if (loading && data == null) item { LoadingSkeleton(rows = 3) }
            if (!loading && months.isEmpty() && error == null) item { EmptyState("لم يُعتمد تكريم أي شهر بعد. سيصلك إشعار عند اعتماد المدير.") }
            item(key = "groups") {
                com.mohammedsamir.halaqati.ui.components.TabContentTransition(scopeKind, { if (it == "halaqa") 0 else 1 }) { _ ->
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        groups.forEach { g ->
                val winners = g.optJSONArray("winners")?.awardObjects().orEmpty()
                val title = listOf(g.optString("halaqa_name").takeIf { it.isNotBlank() && it != "null" }, g.optString("category_name")).filterNotNull().joinToString(" · ")
                Surface(
                    Modifier.fillMaxWidth().halaqatiEnter(), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 1.5.dp, border = cardHairline(),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("نجوم الشهر", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        if (winners.isEmpty()) {
                            Text("لم يستوفِ أحد شروط التكريم في هذه المجموعة هذا الشهر.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Podium(winners, photos, onPick = { picked = it })
                        }
                    }
                }
                        }
                    }
                }
            }
            if (groups.isNotEmpty()) item(key = "how") { HowAwardsWork() }
        }
    }

    rewardFor?.let { w ->
        RewardRequestDialog(
            winner = w, month = month.orEmpty(),
            onDismiss = { rewardFor = null },
            onFinished = { msg, _ -> rewardFor = null; picked = null; notice = msg },
        )
    }
    notice?.let { n -> LaunchedEffect(n) { kotlinx.coroutines.delay(3500); notice = null } }

    picked?.let { w ->
        ModalBottomSheet(onDismissRequest = { picked = null }, containerColor = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            val phone = w.optString("phone").takeIf { it.isNotBlank() && it != "null" }
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MedalAvatar(w.optInt("rank"), w.optString("name"), 56, photos[w.optString("student_id")])
                    Column(Modifier.weight(1f)) {
                        Text(w.optString("name"), style = MaterialTheme.typography.titleMedium)
                        Text("${w.optString("halaqa_name")} · ${awardNum(w.optDouble("score"))} / 100 · ${awardNum(w.optDouble("pages"))} صفحة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ScoreBar("الحفظ", w.optDouble("hifz_pct"), allowOver = true)
                ScoreBar("السرد", w.optDouble("sard_pct"))
                ScoreBar("الحضور", w.optDouble("attendance_pct"))
                ScoreBar("السلوك", w.optDouble("behavior"))
                ScoreBar("جودة التسميع", w.optDouble("quality"))
                Text("للدعم أو المكافأة افتح تبويب «الدعم»، أو اطلب مكافأة هذا الطالب عبر المدير.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { rewardFor = w }, modifier = Modifier.fillMaxWidth()) { Text("كافئ هذا الطالب") }
                if (phone != null) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    FilledTonalButton(onClick = { open(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) }, modifier = Modifier.weight(1f)) { Text("اتصال بولي الأمر") }
                    FilledTonalButton(onClick = { open(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + phone.filter { it.isDigit() }))) }, modifier = Modifier.weight(1f)) { Text("واتساب") }
                }
            }
        }
    }
}

@Composable
private fun MedalAvatar(rank: Int, name: String, size: Int, photo: String? = null) {
    val base = medalColor(rank)
    if (photo != null) {
        Box(Modifier.size((size + 8).dp)) {
            Box(Modifier.align(Alignment.Center).border(3.dp, base, CircleShape).padding(3.dp)) { StudentAvatar(photo, name, size.dp) }
            Box(Modifier.align(Alignment.BottomEnd).size((size * 0.4f).dp).background(base, CircleShape), contentAlignment = Alignment.Center) {
                Text(rank.toString(), color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
            }
        }
        return
    }
    Box(
        Modifier.size(size.dp).background(base, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Text(name.trim().take(1), color = Color.White, style = MaterialTheme.typography.titleLarge) }
}

@Composable
private fun ScoreBar(label: String, raw: Double, allowOver: Boolean = false) {
    // Only the hifz figure may pass 100% (a student can exceed the plan); every other figure is capped at 100%.
    val value = if (allowOver) raw else raw.coerceAtMost(100.0)
    val fraction = (value / 100.0).coerceIn(0.0, 1.0).toFloat()
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text(awardNum(value) + "٪" + if (allowOver && value > 100.0) " · تجاوز الخطة" else "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LinearProgressIndicator(
            drawStopIndicator = {}, progress = { fraction }, modifier = Modifier.fillMaxWidth().height(6.dp).padding(top = 2.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant, color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** First place in the middle and tallest, second on the right, third on the left (RTL reading order). Plinths rise in. */
@Composable
private fun Podium(winners: List<JSONObject>, photos: Map<String, String>, onPick: (JSONObject) -> Unit) {
    val byRank = winners.associateBy { it.optInt("rank") }
    val order = listOf(2, 1, 3).map { byRank[it] }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
        listOf(2, 1, 3).forEachIndexed { idx, rank ->
            val w = order[idx]
            val target = when (rank) { 1 -> 64.dp; 2 -> 44.dp; else -> 30.dp }
            val h by animateDpAsState(if (shown) target else 0.dp, tween(650), label = "plinth")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (w != null) {
                    Surface(onClick = { onPick(w) }, color = Color.Transparent) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            MedalAvatar(rank, w.optString("name"), if (rank == 1) 72 else 58, photos[w.optString("student_id")])
                            Spacer(Modifier.height(6.dp))
                            Text(w.optString("name"), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2)
                            Text("${awardNum(w.optDouble("score"))} / 100", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(when (rank) { 1 -> "ذهبي"; 2 -> "فضي"; else -> "برونزي" }, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = medalColor(rank))
                        }
                    }
                }
                if (w != null) Box(
                    Modifier.fillMaxWidth().height(h).padding(top = 6.dp).background(medalColor(rank), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                    contentAlignment = Alignment.TopCenter,
                ) { Text(rank.toString(), color = Color.White, style = MaterialTheme.typography.titleLarge.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily)) }
            }
        }
    }
}
