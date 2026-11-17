@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import com.mohammedsamir.halaqati.ui.components.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mohammedsamir.halaqati.model.Profile
import com.mohammedsamir.halaqati.model.SyncState
import com.mohammedsamir.halaqati.ui.components.EmptyState
import com.mohammedsamir.halaqati.ui.components.LoadingSkeleton
import com.mohammedsamir.halaqati.ui.components.ProFilterChip
import com.mohammedsamir.halaqati.ui.dashboard.DashboardAttentionItem
import com.mohammedsamir.halaqati.ui.dashboard.DashboardViewModel
import com.mohammedsamir.halaqati.ui.dashboard.FollowUpRules
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens

@Composable
fun StudentFollowUpScreen(
    profile: Profile,
    sync: SyncState,
    onBack: () -> Unit,
    navigate: (String) -> Unit,
    vm: DashboardViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(profile.id, sync) { vm.bind(profile, sync) }

    var query by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("الكل") }
    val groups = remember(state.attention, state.attendance, state.absentLate, state.notHeard) {
        FollowUpRules.group(
            delayed = state.attention,
            quality = state.attendance,
            absence = state.absentLate,
            notHeard = state.notHeard,
        )
    }
    val groupLabels = remember(groups) { listOf("الكل") + groups.map { it.title } }
    LaunchedEffect(groupLabels) { if (selectedGroup !in groupLabels) selectedGroup = "الكل" }

    fun matches(row: DashboardAttentionItem): Boolean = query.isBlank() ||
        row.studentName.contains(query.trim(), ignoreCase = true) || row.reason.contains(query.trim(), ignoreCase = true)

    Scaffold(
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        Text("متابعة الطلاب", fontWeight = FontWeight.Bold)
                        Text(
                            "${groups.flatMap { it.items }.distinctBy { it.studentId }.size} طالب يحتاج متابعة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refresh(force = true) }, modifier = Modifier.size(HalaqatiUiTokens.TouchTarget)) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث")
                    }
                },
            )
            com.mohammedsamir.halaqati.ui.components.LatticeStrip()
            }
        },
    ) { padding ->
        LazyColumn(
        state = androidx.compose.foundation.lazy.rememberLazyListState(),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("ابحث باسم الطالب أو سبب المتابعة") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                )
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    groupLabels.forEach { label ->
                        ProFilterChip(
                            selected = selectedGroup == label,
                            onClick = { selectedGroup = label },
                            label = label,
                        )
                    }
                }
            }

            if (!state.loaded) {
                item { LoadingSkeleton(rows = 5) }
            } else if (groups.isEmpty()) {
                item { EmptyState("لا توجد حالات متابعة حاليًا") }
            } else {
                val visibleGroups = if (selectedGroup == "الكل") groups else groups.filter { it.title == selectedGroup }
                visibleGroups.forEach { group ->
                    val visible = group.items.filter(::matches)
                    if (visible.isNotEmpty()) {
                        item(key = "title:${group.title}") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(visible.size.toString(), style = MaterialTheme.typography.labelLarge, color = com.mohammedsamir.halaqati.ui.theme.textAccent("primary"))
                            }
                        }
                        items(visible, key = { "${group.title}:${it.studentId}" }) { row ->
                            ElevatedCard(
                                onClick = { navigate("student:${row.studentId}") },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                                ) {
                                    Icon(Icons.Default.PersonSearch, contentDescription = null, modifier = Modifier.size(19.dp), tint = MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f)) {
                                        Text(row.studentName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
                                        Text(row.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Default.ChevronLeft, contentDescription = "فتح ملف الطالب", modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
                if (visibleGroups.all { group -> group.items.none(::matches) }) {
                    item { EmptyState("لا توجد نتائج مطابقة للبحث") }
                }
            }
        }
    }
}
