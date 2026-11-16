package com.mohammedsamir.halaqati.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.CardColors
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import com.mohammedsamir.halaqati.ui.components.LocalNourDepth
import com.mohammedsamir.halaqati.ui.components.NourDepth
import com.mohammedsamir.halaqati.ui.components.NourButton
import com.mohammedsamir.halaqati.ui.components.nourRaised
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.ui.theme.HalaqatiUiTokens

/**
 * RC27.11: ONE card look for the whole app. Card / ElevatedCard / OutlinedCard in this package resolve here
 * (same-package declarations win over the material3 star import), so every card gets the same shape, the same
 * fine hairline border and no shadow. Callers may still pass their own colors (status tints) and an emphasised
 * border (thicker than a hairline); the shape and elevation are fixed on purpose so screens never drift apart.
 */
@Composable
private fun unifiedColors(colors: CardColors?): CardColors =
    colors ?: CardDefaults.cardColors(containerColor = androidx.compose.ui.graphics.Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface)

/** RC96: gradient + brand-tinted lift on every card that uses the default colour; caller-coloured cards are only lifted. */
@Composable
private fun depth(m: Modifier, colors: CardColors?, shape: Shape): Modifier =
    m.nourRaised(shape, fill = colors == null)

// RC28: content cards are separated by surfaces/spacing; only outlined cards request a border.
// RC29: on the true-black dark theme a card (surface ~ #101814) is only ~1.16:1 from the page, so a hairline keeps
// cards and lists readable as separate objects. Light mode keeps the borderless look.
@Composable
private fun unifiedBorder(border: BorderStroke?): BorderStroke? = border ?: com.mohammedsamir.halaqati.ui.theme.cardHairline()
    ?: if (LocalNourDepth.current == NourDepth.Row) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null

@Composable
private fun outlinedBorder(border: BorderStroke?): BorderStroke =
    border ?: BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

private val unifiedShape: Shape get() = androidx.compose.foundation.shape.RoundedCornerShape(HalaqatiUiTokens.RadiusMedium)

@Composable
fun Card(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    colors: CardColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: androidx.compose.material3.CardElevation? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(
    modifier = depth(modifier, colors, shape ?: unifiedShape), shape = shape ?: unifiedShape, colors = unifiedColors(colors),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = unifiedBorder(border), content = content,
)

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    colors: CardColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: androidx.compose.material3.CardElevation? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(
    onClick = onClick, modifier = depth(modifier, colors, shape ?: unifiedShape), enabled = enabled, shape = shape ?: unifiedShape, colors = unifiedColors(colors),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = unifiedBorder(border), content = content,
)

@Composable
fun ElevatedCard(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    colors: CardColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: androidx.compose.material3.CardElevation? = null,
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(
    modifier = depth(modifier, colors, shape ?: unifiedShape), shape = shape ?: unifiedShape, colors = unifiedColors(colors),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = null, content = content,
)

@Composable
fun ElevatedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    colors: CardColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: androidx.compose.material3.CardElevation? = null,
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(
    onClick = onClick, modifier = depth(modifier, colors, shape ?: unifiedShape), enabled = enabled, shape = shape ?: unifiedShape, colors = unifiedColors(colors),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = null, content = content,
)

@Composable
fun OutlinedCard(
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    colors: CardColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: androidx.compose.material3.CardElevation? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(
    modifier = depth(modifier, colors, shape ?: unifiedShape), shape = shape ?: unifiedShape, colors = unifiedColors(colors),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = outlinedBorder(border), content = content,
)

@Composable
fun OutlinedCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
    colors: CardColors? = null,
    @Suppress("UNUSED_PARAMETER") elevation: androidx.compose.material3.CardElevation? = null,
    border: BorderStroke? = null,
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(
    onClick = onClick, modifier = depth(modifier, colors, shape ?: unifiedShape), enabled = enabled, shape = shape ?: unifiedShape, colors = unifiedColors(colors),
    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), border = outlinedBorder(border), content = content,
)

/** RC98: the filled button with the identity's depth (see NourButton); same signature as the Material one plus nothing else. */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) = NourButton(onClick, modifier, enabled, shape, colors, elevation, border, contentPadding, interactionSource, content)
