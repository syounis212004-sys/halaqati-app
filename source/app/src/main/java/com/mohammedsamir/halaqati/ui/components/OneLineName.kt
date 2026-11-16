package com.mohammedsamir.halaqati.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * RC27.11: ONE size for every person's name, so lists look the same on every screen.
 * - normal names: 15sp SemiBold everywhere (the style/weight a caller passes no longer changes the size);
 * - a screen header (a caller asking for a 20sp+ style): 18sp Bold;
 * The name stays on ONE line. Only when it really does not fit does it shrink, in small steps, down to 12.5sp
 * (header 15sp); an extremely long name is then cut with an ellipsis. A name that fits is never resized, so two
 * short names always look identical.
 * [maxLines] and [overflow] are accepted only so it can replace Text(...) calls without other edits.
 */
@Composable
fun OneLineName(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    @Suppress("UNUSED_PARAMETER") fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    @Suppress("UNUSED_PARAMETER") minSp: Float = 12.5f,
    @Suppress("UNUSED_PARAMETER") maxLines: Int = 1,
    @Suppress("UNUSED_PARAMETER") overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val header = style.fontSize != TextUnit.Unspecified && style.fontSize.value >= 20f
    val baseSp = if (header) 18f else 15f
    val floorSp = if (header) 15f else 12.5f
    var size by remember(text, header) { mutableFloatStateOf(baseSp) }
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(
            fontSize = size.sp,
            lineHeight = (size * 1.45f).sp,
            fontWeight = if (header) FontWeight.Bold else FontWeight.SemiBold,
        ),
        color = color,
        textAlign = textAlign,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && size > floorSp) size = (size - 0.5f).coerceAtLeast(floorSp)
        },
    )
}
