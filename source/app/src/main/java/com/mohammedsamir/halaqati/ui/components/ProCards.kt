package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * RC53: a card with a clearly visible outline and a surface colour that differs from the page background, so neighbouring
 * cards never melt into each other. [accent] draws the outline (and a slightly stronger tint) in a status colour.
 */
@Composable
fun BorderedCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    onClick: (() -> Unit)? = null,
    padding: Dp = 12.dp,
    /** RC104: set for a card about a halaqa, a teacher or a supervisor: adds the entity ornament band on top. */
    entity: com.mohammedsamir.halaqati.ui.theme.Entity? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val line = accent ?: MaterialTheme.colorScheme.primary.copy(alpha = .55f)
    val fill = if (accent != null) accent.copy(alpha = .07f).compositeOverSurface() else MaterialTheme.colorScheme.surface
    val base = modifier.fillMaxWidth()
    val surfaceModifier = (if (onClick != null) base.clickable { onClick() } else base)
        .nourRaised(RoundedCornerShape(16.dp), tint = accent ?: MaterialTheme.colorScheme.primary, fill = false)
    Surface(
        modifier = surfaceModifier,
        shape = RoundedCornerShape(16.dp),
        color = fill,
        border = BorderStroke(1.5.dp, line),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column {
            if (entity != null) EntityBand(entity)
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
private fun Color.compositeOverSurface(): Color = this.compositeOver(MaterialTheme.colorScheme.surface)

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1f - a),
        green = green * a + background.green * (1f - a),
        blue = blue * a + background.blue * (1f - a),
        alpha = 1f,
    )
}
