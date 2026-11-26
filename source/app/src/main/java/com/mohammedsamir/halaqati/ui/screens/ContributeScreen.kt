package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.data.ContestStatus
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.SegmentedTabs
import com.mohammedsamir.halaqati.ui.components.StateNotice
import com.mohammedsamir.halaqati.ui.runCatchingCancellable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "المساهمة": one page per role for taking part in the halaqa's life.
 *  - Guardian: sponsor ("تكفّل") a halaqa of his children, see the halaqa's contests, set the reminder.
 *  - Teacher / supervisor / admin: create contests (opens the contests page), see where theirs stand, set the reminder.
 * The reminder is local (see ReminderPanel); the page itself reads the same cached lists as the other pages.
 */
@Composable
fun ContributeScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit) {
    var tick by rememberSaveable { mutableIntStateOf(0) }
    val guardian = profile.role == UserRole.GUARDIAN
    val tabs = if (guardian) listOf("تكفّل بحلقة", "المسابقات", "التذكير") else listOf("المسابقات", "التذكير")
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "المساهمة", kicker = "أهلًا بك، " + profile.fullName.trim().substringBefore(' '), onRefresh = { tick++; onRefresh() }, pending = pending)
        SegmentedTabs(tabs, tab, { tab = it }, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
        Box(Modifier.weight(1f)) {
            when {
                guardian && tab == 0 -> SponsorSupport(profile, tick, showRewards = false)
                guardian && tab == 1 -> ReadOnlyContestsScreen(embedded = true)
                !guardian && tab == 0 -> StaffContributeTab(profile.role, tick, navigate)
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { ReminderPanel(profile.role.name) }
                }
            }
        }
    }
}

@Composable
private fun StaffContributeTab(role: UserRole, tick: Int, navigate: (String) -> Unit) {
    var line by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tick) {
        line = runCatchingCancellable {
            withContext(Dispatchers.IO) {
                if (role == UserRole.ADMIN) {
                    val n = AppGraph.repo.contestsAdmin(false).count { it.status == ContestStatus.PENDING }
                    if (n == 0) "لا توجد مسابقات بانتظار قرارك" else "بانتظار قرارك: $n"
                } else {
                    val mine = AppGraph.repo.contestsMine(false)
                    if (mine.isEmpty()) "لم تُنشئ مسابقة بعد" else "مسابقاتك: ${mine.count { it.status == ContestStatus.APPROVED }} معتمدة، ${mine.count { it.status == ContestStatus.PENDING }} بانتظار المدير"
                }
            }
        }.getOrNull()
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أنشئ مسابقة لحلقتك", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    if (role == UserRole.ADMIN) "تُعتمد مسابقتك فورًا وتظهر لمحفّظي الحلقة ومشرفيها وأولياء أمور طلابها وطلابها."
                    else "تصل مسابقتك إلى مدير النظام للاعتماد، ثم تظهر لمحفّظي الحلقة ومشرفيها وأولياء أمور طلابها وطلابها.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        line?.let { item { StateNotice(it, "info") } }
        item { Button(onClick = { navigate("contests") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("فتح المسابقات وإنشاء مسابقة") } }
    }
}
