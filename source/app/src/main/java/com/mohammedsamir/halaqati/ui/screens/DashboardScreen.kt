@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.SyncState
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.GoldenFeatureHero
import com.mohammedsamir.halaqati.ui.components.MetricCard
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.components.halaqatiEnter
import com.mohammedsamir.halaqati.ui.dashboard.DashboardRules
import com.mohammedsamir.halaqati.ui.dashboard.DailyOperationStateEngine
import com.mohammedsamir.halaqati.ui.dashboard.DashboardViewModel
import com.mohammedsamir.halaqati.ui.dashboard.FollowUpRules
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens

@Composable
fun DashboardScreen(
    p: Profile,
    sync: SyncState,
    navigate: (String) -> Unit,
    onSync: () -> Unit,
    vm: DashboardViewModel = viewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uiPrefs by com.mohammedsamir.halaqati.data.AppGraph.uiPreferences.state.collectAsStateWithLifecycle()
    val compactHome = uiPrefs.homeStyle == com.mohammedsamir.halaqati.data.HomeStyle.COMPACT
    // RC27.10: work mode - a program has no smart plans, so plan cards / plan follow-up are hidden there.
    val workKind = com.mohammedsamir.halaqati.ui.components.rememberWorkKind()
    val showPlans = com.mohammedsamir.halaqati.plans.WorkModeRules.showSmartPlans(workKind)
    LaunchedEffect(p.id, sync) { vm.bind(p, sync) }
    LaunchedEffect(p.id) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { com.mohammedsamir.halaqati.data.AppGraph.repo.cleanupNotificationsIfDue() }
        }
    }

    var selectedDailyBucket by remember { mutableStateOf<DailyBoardBucket?>(null) }
    val dailyBuckets = remember(state.notHeard, state.absentLate) { listOf(
        DailyBoardBucket("لم يسمّع اليوم", state.notHeard, "danger"),
        DailyBoardBucket("غائب اليوم", state.absentLate, "warning"),
    ) }
    val planBuckets = remember(state.attention, state.attendance, state.overTarget) { listOf(
        DailyBoardBucket("متأخر عن الخطة", state.attention, "warning"),
        DailyBoardBucket("يحتاج مراجعة", state.attendance, "info"),
        DailyBoardBucket("تجاوز هدفه", state.overTarget, "success"),
    ) }
    val followUpGroups = remember(state.attention, state.attendance, state.absentLate, state.notHeard) {
        FollowUpRules.group(
            delayed = state.attention,
            quality = state.attendance,
            absence = state.absentLate,
            notHeard = state.notHeard,
        )
    }
    val allFollowUp = remember(followUpGroups) { followUpGroups.flatMap { it.items }.distinctBy { it.studentId } }
    val isOffDay = state.dailyOperation.mode == DailyOperationStateEngine.Mode.OFF_DAY
    val waitingForSession = state.dailyOperation.mode == DailyOperationStateEngine.Mode.NO_SESSION_YET

    // RC81: counted from what really happened today. Present = marked present/late, or recited, or scheduled and not marked absent.
    // Waiting = present but not recited yet. (It used to count only students the plan scheduled today, so a day with many
    // recitations still showed "1 present, 1 waiting, 0 heard".)
    val absentIds = remember(state.absentLate) { state.absentLate.map { it.studentId }.toSet() }
    val presentSet = remember(state.presentIds, state.heardIds, state.dailyOperation.expectedStudentIds, absentIds) {
        state.presentIds + state.heardIds + (state.dailyOperation.expectedStudentIds - absentIds)
    }
    val todayPresent = if (state.dailyOperation.countDailyMetrics) presentSet.size else 0
    val waitingCount = if (state.dailyOperation.countDailyMetrics) (presentSet - state.heardIds).size else 0
    val firstName = com.mohammedsamir.halaqati.ui.dashboard.HomeMessages.displayName(p.fullName)
    val homeState = com.mohammedsamir.halaqati.ui.dashboard.HomeMessages.state(state.loaded, isOffDay, waitingForSession, state.progress.heardStudents, waitingCount)
    val homeMessage = remember(homeState, p.role, state.today, waitingCount, state.progress.heardStudents, state.absentLate.size) {
        com.mohammedsamir.halaqati.ui.dashboard.HomeMessages.pick(
            role = if (p.role == com.mohammedsamir.halaqati.model.UserRole.TEACHER) "teacher" else "admin",
            state = homeState,
            seed = java.time.LocalDate.now().toEpochDay().toInt(),
            waiting = waitingCount,
            absent = state.absentLate.size,
            heard = state.progress.heardStudents,
        )
    }
    val homeIcon = when (homeMessage.icon) {
        "play" -> Icons.Default.PlayCircle
        "book" -> Icons.Default.MenuBook
        "check" -> Icons.Default.TaskAlt
        "star" -> Icons.Default.EmojiEvents
        "rest" -> Icons.Default.Weekend
        else -> Icons.Default.Sync
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(
            title = "أهلًا، $firstName",
            onRefresh = { vm.refresh(force = true) },
            pending = sync.pending,
            kicker = if (state.today.isBlank()) "لوحة اليوم · متابعة الحلقة" else "${state.today} · ${hijriToday()}",
        )
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(
                start = HalaqatiUiTokens.ScreenHorizontal,
                end = HalaqatiUiTokens.ScreenHorizontal,
                top = 4.dp,
                bottom = HalaqatiUiTokens.SpaceXl,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                // RC81: the greeting + verse live inside the day card (one card instead of two).
                val phrase = remember(p.role) { com.mohammedsamir.halaqati.ui.dashboard.RolePhrases.forRole(p.role.name, java.time.LocalDate.now().toEpochDay()) }
                val greeting = remember { com.mohammedsamir.halaqati.ui.dashboard.RolePhrases.greeting(java.time.LocalTime.now().hour) }
                com.mohammedsamir.halaqati.ui.components.TodayHeroCard(
                    greeting = if (compactHome) "" else greeting,
                    verse = if (compactHome) "" else phrase.text,
                    verseSource = if (compactHome) "" else phrase.source,
                    photo = p.avatarPath.takeIf { it.isNotBlank() && it != "null" },
                    photoName = p.fullName.ifBlank { "؟" },
                    heard = state.progress.heardStudents,
                    icon = homeIcon,
                    title = homeMessage.title,
                    subtitle = homeMessage.subtitle,
                    loaded = state.loaded,
                    offDay = isOffDay,
                    waitingForSession = waitingForSession,
                    present = todayPresent,
                    waiting = waitingCount,
                    onStart = { navigate("session_today") },
                )
            }
            if (p.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN || p.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR) item { OrgSummaryCard(p, navigate) }
            if (com.mohammedsamir.halaqati.data.ContestsFeature.enabled && p.role != com.mohammedsamir.halaqati.model.UserRole.ADMIN) item(key = "contests-card") { ContestsHomeCard(navigate) }
            item { com.mohammedsamir.halaqati.ui.components.HomeShortcuts(showPlans = showPlans, navigate = navigate, showAwards = p.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN, role = p.role) }
            if (!state.loaded) {
                item { LoadingSkeleton(rows = 2) }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) { MetricCard("حاضر اليوم", todayPresent.toString(), icon = Icons.Default.HowToReg, onClick = { navigate("session_today") }) }
                        Box(Modifier.weight(1f)) { MetricCard("أتمّ التسميع", state.progress.heardStudents.toString(), icon = Icons.Default.TaskAlt, onClick = { navigate("reports") }) }
                        Box(Modifier.weight(1f)) { MetricCard("بانتظارك", waitingCount.toString(), icon = Icons.Default.HourglassTop, onClick = { navigate("follow_up") }) }
                    }
                }
            }

            if (showPlans) item {
                FilledTonalButton(
                    onClick = { navigate("quick_memorization") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Text("التحفيظ السريع", fontWeight = FontWeight.SemiBold)
                }
            }

            if (p.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN || p.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR || p.role == com.mohammedsamir.halaqati.model.UserRole.TEACHER) {
                item { ApprovalsInboxCard(onOpen = { navigate("approvals") }) }
            }
            // RC42: the analytical cards (follow-up and comparison) live on their own page so they keep their state and
            // stop reloading every time the home list scrolls; home shows one entry card.
            if (p.role == com.mohammedsamir.halaqati.model.UserRole.ADMIN || p.role == com.mohammedsamir.halaqati.model.UserRole.SUPERVISOR || p.role == com.mohammedsamir.halaqati.model.UserRole.TEACHER) {
                item {
                    ElevatedCard(onClick = { navigate("insights") }, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Leaderboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(Modifier.weight(1f)) {
                                Text("المتابعة والمقارنة", fontWeight = FontWeight.Bold)
                                Text("الغياب المتكرر، الطلاب المنقطعون، الخطط المتأخرة، ومقارنة الحلقات", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(Icons.Default.ChevronLeft, contentDescription = null)
                        }
                    }
                }
            }

            if (sync.blocked > 0) {
                item { StateNotice("هناك ${sync.blocked} عمليات مزامنة تحتاج مراجعة.", "error") }
            }

            state.errors.values.firstOrNull()?.let { message ->
                item { StateNotice("بقيت البيانات السابقة ظاهرة. $message", kind = "warning") }
            }


        item {
            Text("المتابعة اليومية", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        item {
            if (!state.loaded) {
                LoadingSkeleton(rows = 1)
            } else if (isOffDay || waitingForSession) {
                DailyOperationBanner(
                    title = state.dailyOperation.title,
                    subtitle = state.dailyOperation.subtitle,
                    offDay = isOffDay,
                )
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    dailyBuckets.forEach { bucket ->
                        DailyStatusCard(
                            bucket = bucket,
                            modifier = Modifier.weight(1f),
                            onOpen = { selectedDailyBucket = bucket },
                        )
                    }
                }
            }
        }
        if (showPlans) item {
            Text("متابعة الخطة", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (showPlans) item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                planBuckets.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { bucket ->
                            DailyStatusCard(
                                bucket = bucket,
                                modifier = Modifier.weight(1f),
                                onOpen = { selectedDailyBucket = bucket },
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("طلاب يحتاجون متابعة", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                if (allFollowUp.isNotEmpty()) {
                    TextButton(onClick = { navigate("follow_up") }) { Text("فتح المتابعة") }
                }
            }
        }
        followUpGroups.forEach { group ->
            val title = group.title
            val itemsInGroup = group.items
            item { Text(title, style = MaterialTheme.typography.labelLarge, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"), fontWeight = FontWeight.SemiBold) }
            items(itemsInGroup.take(if (compactHome) 2 else 4), key = { "follow:$title:${it.studentId}" }) { followItem ->
                ElevatedCard(onClick = { navigate("student:${followItem.studentId}") }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f)) {
                            com.mohammedsamir.halaqati.ui.components.OneLineName(followItem.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(followItem.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Default.ChevronLeft, contentDescription = "فتح الطالب", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
        if (allFollowUp.isEmpty() && state.loaded) {
            item { EmptyState("لا توجد تنبيهات متابعة حاليًا") }
        }

        item {
            OutlinedButton(
                onClick = { navigate("rankings") },
                modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
            ) { Text("الإحصائيات والترتيب") }
        }
        if (sync.pending > 0) {
            item {
                OutlinedButton(
                    onClick = onSync,
                    modifier = Modifier.fillMaxWidth().heightIn(min = HalaqatiUiTokens.TouchTarget),
                ) { Text("مزامنة ${sync.pending} عملية معلقة") }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

        selectedDailyBucket?.let { bucket ->
            ModalBottomSheet(onDismissRequest = { selectedDailyBucket = null }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(bucket.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    if (bucket.items.isEmpty()) {
                        EmptyState("لا توجد عناصر في هذه القائمة")
                    } else {
                        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(bucket.items, key = { "daily:${bucket.title}:${it.studentId}" }) { item ->
                                ListItem(
                                    headlineContent = { Text(item.studentName, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = { Text(item.reason) },
                                    trailingContent = { Icon(Icons.Default.ChevronLeft, contentDescription = "فتح الطالب") },
                                    modifier = Modifier.clickable {
                                        selectedDailyBucket = null
                                        navigate("student:${item.studentId}")
                                    },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}


@Composable
private fun DailyOperationBanner(
    title: String,
    subtitle: String,
    offDay: Boolean,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (offDay) {
                Text(
                    "لم يسمّع اليوم: 0 · غائب اليوم: 0",
                    style = MaterialTheme.typography.labelLarge,
                    color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"),
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    "لن يُحسب أي طالب كـ«لم يسمّع» أو «غائب» قبل بدء الجلسة.",
                    style = MaterialTheme.typography.labelMedium,
                    color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"),
                )
            }
        }
    }
}


private data class DailyBoardBucket(
    val title: String,
    val items: List<com.mohammedsamir.halaqati.ui.dashboard.DashboardAttentionItem>,
    val kind: String,
)

@Composable
private fun DailyStatusCard(
    bucket: DailyBoardBucket,
    modifier: Modifier = Modifier,
    onOpen: () -> Unit,
) {
    // Nour: a white card, status colour only on the icon badge, the count and a thin top marker.
    val tone = when (bucket.kind) { "danger" -> "danger"; "success" -> "success"; "warning" -> "warning"; else -> "neutral" }
    val (accent, _, _) = com.mohammedsamir.halaqati.ui.components.nourTone(tone)
    val badge = when (bucket.kind) {
        "danger" -> Icons.Default.Warning
        "success" -> Icons.Default.EmojiEvents
        else -> Icons.Default.Info
    }
    com.mohammedsamir.halaqati.ui.components.NourCard(
        modifier = modifier.halaqatiEnter(),
        onClick = onOpen,
        marker = if (bucket.items.isEmpty()) null else accent,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.mohammedsamir.halaqati.ui.components.NourIconBadge(badge, tone = tone, size = 40.dp)
            Text(
                bucket.items.size.toString(),
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = com.mohammedsamir.halaqati.ui.theme.CairoFamily),
                fontWeight = FontWeight.ExtraBold,
                color = if (bucket.items.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else accent,
            )
        }
        Text(bucket.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        bucket.items.take(2).forEach { item ->
            Text(item.studentName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        if (bucket.items.isEmpty()) {
            Text("لا يوجد", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("عرض القائمة كاملة (${bucket.items.size})", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

private fun hijriToday(): String = runCatching {
    val d = java.time.chrono.HijrahDate.from(java.time.LocalDate.now())
    val months = listOf("محرّم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوّال", "ذو القعدة", "ذو الحجة")
    "${d.get(java.time.temporal.ChronoField.DAY_OF_MONTH)} ${months[d.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) - 1]} ${d.get(java.time.temporal.ChronoField.YEAR)}هـ"
}.getOrDefault("لوحة اليوم")
