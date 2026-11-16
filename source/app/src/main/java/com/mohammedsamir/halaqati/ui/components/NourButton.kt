package com.mohammedsamir.halaqati.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * RC98: the filled button with the identity's depth: a soft top-light gradient and a brand-tinted lift. Only a default-coloured,
 * enabled button gets it; a caller-coloured (e.g. destructive) or disabled button is the unchanged Material button, so no state
 * or colour meaning changes. The gradient stays within +-8% of primary so onPrimary keeps its contrast (ContrastTest).
 */
@Composable
fun NourButton(
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
) {
    val cs = MaterialTheme.colorScheme
    if (colors != null || !enabled) {
        androidx.compose.material3.Button(
            onClick = onClick, modifier = modifier, enabled = enabled, shape = shape,
            colors = colors ?: ButtonDefaults.buttonColors(), elevation = elevation ?: ButtonDefaults.buttonElevation(),
            border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content,
        )
        return
    }
    val brush = remember(cs.primary) { Brush.verticalGradient(NourButtonColors.stops(cs.primary)) }
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier.nourRaised(shape, depth = NourDepth.Card, fill = false).background(brush, shape),
        enabled = true, shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = cs.onPrimary),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        border = border, contentPadding = contentPadding, interactionSource = interactionSource, content = content,
    )
}

/** The gradient of the default filled button: primary, lit 8% at the top and shaded 8% at the bottom. */
object NourButtonColors {
    fun stops(primary: Color): List<Color> = listOf(lerp(primary, Color.White, .08f), primary, lerp(primary, Color.Black, .08f))
}
