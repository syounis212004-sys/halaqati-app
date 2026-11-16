package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.UserRole
import com.mohammedsamir.halaqati.ui.components.FollowUpBoard
import com.mohammedsamir.halaqati.ui.components.AppTopBar
import com.mohammedsamir.halaqati.ui.components.HalaqaComparisonCard
import com.mohammedsamir.halaqati.ui.components.PlansNeedAttentionCard
import com.mohammedsamir.halaqati.ui.components.SeasonsCard
import com.mohammedsamir.halaqati.ui.components.rememberWorkKind
import com.mohammedsamir.halaqati.plans.WorkModeRules

/**
 * RC42: follow-up and comparison on their own page, in two tabs. The cards used to sit inside the home list, so scrolling
 * them out of view threw their state away and they reloaded every time. Here each tab keeps its own list, and every card
 * remembers its last result for the session.
 */
@Composable
fun InsightsScreen(profile: Profile, pending: Int, onRefresh: () -> Unit, navigate: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var reloadKey by remember { mutableIntStateOf(0) }
    val admin = profile.role == UserRole.ADMIN || profile.role == UserRole.SUPERVISOR
    val showPlans = WorkModeRules.showSmartPlans(rememberWorkKind())
    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "المتابعة والمقارنة", onRefresh = { reloadKey += 1; onRefresh() }, pending = pending)
        TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("المتابعة", fontWeight = FontWeight.Bold) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("المقارنة", fontWeight = FontWeight.Bold) })
        }
        Crossfade(targetState = tab, label = "insights-tab") { current ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 120.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (current == 0) {
                    item { FollowUpBoard(admin = admin, showPlans = showPlans, reloadKey = reloadKey, navigate = navigate) }
                    if (showPlans) item { PlansNeedAttentionCard(onOpenPlans = { navigate("plans") }) }
                    item { OutlinedButton(onClick = { navigate("follow_up") }, modifier = Modifier.fillMaxWidth()) { Text("قائمة المتابعة الكاملة للطلاب") } }
                } else {
                    if (admin) item { HalaqaComparisonCard() }
                    item { SeasonsCard(canManage = admin) }
                }
            }
        }
    }
}
