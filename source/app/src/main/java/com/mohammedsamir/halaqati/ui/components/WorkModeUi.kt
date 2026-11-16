package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.WorkModeHolder
import com.mohammedsamir.halaqati.plans.WorkMode
import com.mohammedsamir.halaqati.plans.WorkModeRules

/** RC27.10: the kind a work screen should show right now (REGULAR or PROGRAM, never MIXED). */
@Composable
fun rememberWorkKind(): WorkMode {
    val state by WorkModeHolder.state.collectAsState()
    val choice by WorkModeHolder.kind.collectAsState()
    return WorkModeRules.resolveKind(state.mode, choice)
}

/** Shown only when the person reaches both kinds and no single halaqa is selected: halaqat and programs are shown apart. */
@Composable
fun WorkKindToggle(modifier: Modifier = Modifier) {
    val state by WorkModeHolder.state.collectAsState()
    if (state.mode != WorkMode.MIXED) return
    val kind = rememberWorkKind()
    Row(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ProFilterChip(selected = kind == WorkMode.REGULAR, onClick = { WorkModeHolder.setKind(WorkMode.REGULAR) }, label = "الحلقات", modifier = Modifier.weight(1f), showCheckIcon = false)
        ProFilterChip(selected = kind == WorkMode.PROGRAM, onClick = { WorkModeHolder.setKind(WorkMode.PROGRAM) }, label = "البرامج", modifier = Modifier.weight(1f), showCheckIcon = false)
    }
}

/**
 * RC27.10: the unified halaqa / program scope bar for ranking, reports and certificates.
 * Same store as the sessions / students / plans screens, so the choice carries over. Hidden for a person with one halaqa.
 */
@Composable
fun WorkScopeBar(role: com.mohammedsamir.halaqati.model.UserRole, modifier: Modifier = Modifier) {
    val state by WorkModeHolder.state.collectAsState()
    val savedScopes by com.mohammedsamir.halaqati.data.AppGraph.halaqaScope.selected.collectAsState()
    val userId = remember { com.mohammedsamir.halaqati.data.AppGraph.store.load()?.userId }
    val choices = state.activeIds.map { it to (state.nameById[it] ?: "حلقة") }
    val scope = com.mohammedsamir.halaqati.ui.scope.HalaqaScopeRules.resolve(role.name.lowercase(), state.activeIds, savedScopes[userId])
    if (!scope.showSwitcher) return
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HalaqaScopeBar(
            halaqas = choices,
            selectedId = state.selectedId,
            onSelect = { com.mohammedsamir.halaqati.data.AppGraph.halaqaScope.select(userId, it) },
            allLabel = "كل الحلقات والبرامج",
        )
        if (state.selectedId == null) WorkKindToggle(Modifier.padding(horizontal = 0.dp))
    }
}
