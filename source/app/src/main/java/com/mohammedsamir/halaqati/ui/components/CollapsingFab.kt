package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** The page area already ends above the bottom navigation bar, so the FAB only needs the standard margin. */
val FabMargin = 16.dp
/** Bottom padding of a list that has a FAB: FAB height 56dp + margin above and below, so the last item can scroll clear of it. */
val FabClearance = 88.dp

/** Extended FAB that collapses to an icon when the list below it has been scrolled. Place inside a Box over the list. */
@Composable
fun BoxScope.CollapsingFab(listState: androidx.compose.foundation.lazy.LazyListState, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val expanded by remember(listState) { derivedStateOf { !listState.canScrollBackward } }
    ExtendedFloatingActionButton(
        onClick = onClick,
        expanded = expanded,
        icon = { Icon(icon, contentDescription = label) },
        text = { Text(label) },
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = FabMargin, bottom = FabMargin),
    )
}
