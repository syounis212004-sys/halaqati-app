@file:OptIn(ExperimentalTextApi::class)

package com.mohammedsamir.halaqati.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.mohammedsamir.halaqati.R

/** Cairo is the single application typeface. The variable font is embedded by CI before resource processing. */
/**
 * RC27.2: Cairo is a VARIABLE font (wght axis). Registering the same file for every weight without
 * variation settings made Compose draw every weight at the font's default instance, so "Bold" titles
 * looked regular and text looked weak. Each weight now pins its own wght value (API 26+; older
 * devices keep the default instance because Android cannot vary a font axis there).
 */
/** RC28: real 600/700/800 instances restore a clear Arabic title and name hierarchy. */
private fun cairo(weight: FontWeight, drawn: Int = weight.weight) = Font(
    R.font.cairo,
    weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(drawn)),
)

/** RC41: Amiri Bold gives large screen titles a calligraphic, Quranic-print character; body text stays Cairo. */
val AmiriFamily = FontFamily(Font(R.font.amiri_bold, FontWeight.Bold), Font(R.font.amiri_bold, FontWeight.SemiBold), Font(R.font.amiri_bold, FontWeight.ExtraBold))

val CairoFamily = FontFamily(
    cairo(FontWeight.Light),
    cairo(FontWeight.Normal),
    cairo(FontWeight.Medium),
    cairo(FontWeight.SemiBold, drawn = 600),
    cairo(FontWeight.Bold, drawn = 700),
    cairo(FontWeight.ExtraBold, drawn = 800),
)

/**
 * RC29 type scale. One rule: Arabic needs more line height than Latin and must never be letter-spaced, so
 * letterSpacing is pinned to 0 and every line box is centred (no extra top padding that made rows look uneven).
 * Body copy is one step larger than Material's defaults because Cairo has a small x-height; titles stay strong,
 * supporting copy stays lighter, and nothing below 12sp is used anywhere.
 */
private val ArabicLines = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)

private fun style(weight: FontWeight, size: Int, line: Int, family: FontFamily = CairoFamily) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp,
    lineHeightStyle = ArabicLines,
)

fun cairoTypography() = Typography(
    displayLarge = style(FontWeight.Bold, 44, 58, AmiriFamily),
    displayMedium = style(FontWeight.Bold, 38, 50, AmiriFamily),
    displaySmall = style(FontWeight.Bold, 32, 44, AmiriFamily),
    headlineLarge = style(FontWeight.Bold, 28, 40, AmiriFamily),
    headlineMedium = style(FontWeight.Bold, 25, 36, AmiriFamily),
    headlineSmall = style(FontWeight.Bold, 22, 32, AmiriFamily),
    titleLarge = style(FontWeight.Bold, 22, 32, AmiriFamily),
    titleMedium = style(FontWeight.SemiBold, 16, 24),
    titleSmall = style(FontWeight.SemiBold, 14, 21),
    bodyLarge = style(FontWeight.Medium, 16, 25),
    bodyMedium = style(FontWeight.Normal, 15, 23),
    bodySmall = style(FontWeight.Normal, 13, 20),
    labelLarge = style(FontWeight.SemiBold, 14, 21),
    labelMedium = style(FontWeight.Medium, 13, 19),
    labelSmall = style(FontWeight.Normal, 12, 18),
)
