package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/** RC104: the quiet ornamental rule at the top of every dialog (same girih motif and strip level as the top-bar band). */
@Composable
fun DialogOrnament(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).nourPattern(PatternLevel.Strip, MaterialTheme.colorScheme.primary))
}

/**
 * RC104: Material's AlertDialog with the ornamental rule above its first slot. Same parameters and behaviour as
 * [AlertDialog]; only the rule is added, so every dialog of the app carries the same identity from one place.
 */
@Composable
fun HalaqatiAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties(),
) {
    fun withOrnament(slot: @Composable () -> Unit): @Composable () -> Unit = { Column { DialogOrnament(); Spacer(Modifier.height(10.dp)); slot() } }
    val first = when { icon != null -> 0; title != null -> 1; text != null -> 2; else -> -1 }
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon?.let { if (first == 0) withOrnament(it) else it },
        title = title?.let { if (first == 1) withOrnament(it) else it },
        text = text?.let { if (first == 2) withOrnament(it) else it },
        shape = shape,
        containerColor = containerColor,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties,
    )
}
