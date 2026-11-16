package com.mohammedsamir.halaqati.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mohammedsamir.halaqati.data.ThemeMode
import com.mohammedsamir.halaqati.data.UiDensity
import com.mohammedsamir.halaqati.data.UiPreferences

/**
 * RC17 Design Package tokens.
 * Geometry mirrors the supplied Halaqati prototype: 8pt rhythm, 20dp page gutters,
 * 16dp actions, 20-24dp cards and a minimum 48dp touch target.
 */
object HalaqatiUiTokens {
    val TouchTarget = 48.dp
    val ActionHeight = 52.dp
    val RadiusSmall = 12.dp
    val RadiusMedium = 16.dp
    val RadiusMetric = 20.dp
    val RadiusLarge = 20.dp
    val RadiusCard = 16.dp
    val RadiusHero = 28.dp
    val ScreenHorizontal = 16.dp
    val SpaceXs = 4.dp
    val SpaceSm = 8.dp
    val SpaceMd = 12.dp
    val SpaceLg = 16.dp
    val SpaceXl = 24.dp
    /** RC47: Apple-style continuous large corners for cards and sheets. */
    val RadiusXl = 24.dp
    val RadiusSheet = 28.dp
}

/** Fixed semantic accents from the approved redesign. Primary/accent can still be user-customized. */
// RC16 parity anchors retained for regression tooling only: 0xFF0D3B2F / 0xFF52645D
object HalaqatiDesignColors {
    val DefaultPrimary = Color(HalaqatiPalette.DEFAULT_PRIMARY)
    val DefaultAccent = Color(HalaqatiPalette.DEFAULT_ACCENT)
    val Ink = Color(0xFF163C32)
    val Background = Color(0xFFF7F8F3)
    val Soft = Color(0xFFEAF1E9)
    val Border = Color(0xFFD8E3DA)
    val Gold = Color(0xFF86621F)
    val GoldSoft = Color(0xFFF5EDD9)
    val Hero = Color(0xFF103E32)
}

data class HalaqatiSpacing(val xs: Dp, val sm: Dp, val md: Dp, val lg: Dp, val xl: Dp)
val LocalHalaqatiSpacing = staticCompositionLocalOf { HalaqatiSpacing(4.dp, 8.dp, 12.dp, 16.dp, 24.dp) }

private fun spacing(density: UiDensity) = when (density) {
    UiDensity.COMFORTABLE -> HalaqatiSpacing(6.dp, 10.dp, 14.dp, 18.dp, 28.dp)
    UiDensity.BALANCED -> HalaqatiSpacing(4.dp, 8.dp, 12.dp, 16.dp, 24.dp)
    UiDensity.COMPACT -> HalaqatiSpacing(3.dp, 6.dp, 9.dp, 12.dp, 18.dp)
}

private fun c(argb: Int) = Color(argb)

private val HalaqatiLight = HalaqatiPalette.LIGHT.let { r ->
    lightColorScheme(
        primary = c(r.primary), onPrimary = c(r.onPrimary),
        primaryContainer = c(r.primaryContainer), onPrimaryContainer = c(r.onPrimaryContainer),
        secondary = c(r.secondary), onSecondary = c(r.onSecondary),
        secondaryContainer = c(r.secondaryContainer), onSecondaryContainer = c(r.onSecondaryContainer),
        tertiary = c(r.tertiary), onTertiary = c(r.onTertiary),
        tertiaryContainer = c(r.tertiaryContainer), onTertiaryContainer = c(r.onTertiaryContainer),
        background = c(r.background), onBackground = c(r.onBackground),
        surface = c(r.surface), onSurface = c(r.onSurface),
        surfaceVariant = c(r.surfaceVariant), onSurfaceVariant = c(r.onSurfaceVariant),
        outline = c(r.outline), outlineVariant = c(r.outlineVariant),
        error = c(r.error), onError = c(r.onError),
        errorContainer = c(r.errorContainer), onErrorContainer = c(r.onErrorContainer),
        surfaceDim = c(r.surfaceDim), surfaceBright = c(r.surfaceBright),
        surfaceContainerLowest = c(r.surfaceLowest), surfaceContainerLow = c(r.surfaceLow),
        surfaceContainer = c(r.surfaceContainer), surfaceContainerHigh = c(r.surfaceHigh),
        surfaceContainerHighest = c(r.surfaceHighest),
        inverseSurface = c(r.inverseSurface), inverseOnSurface = c(r.inverseOnSurface),
        // RC29: no purple elevation tint on any surface; elevation is expressed by the surface ladder.
        surfaceTint = Color.Transparent, scrim = Color.Black,
    )
}

private val HalaqatiDark = HalaqatiPalette.DARK.let { r ->
    darkColorScheme(
        primary = c(r.primary), onPrimary = c(r.onPrimary),
        primaryContainer = c(r.primaryContainer), onPrimaryContainer = c(r.onPrimaryContainer),
        secondary = c(r.secondary), onSecondary = c(r.onSecondary),
        secondaryContainer = c(r.secondaryContainer), onSecondaryContainer = c(r.onSecondaryContainer),
        tertiary = c(r.tertiary), onTertiary = c(r.onTertiary),
        tertiaryContainer = c(r.tertiaryContainer), onTertiaryContainer = c(r.onTertiaryContainer),
        background = c(r.background), onBackground = c(r.onBackground),
        surface = c(r.surface), onSurface = c(r.onSurface),
        surfaceVariant = c(r.surfaceVariant), onSurfaceVariant = c(r.onSurfaceVariant),
        outline = c(r.outline), outlineVariant = c(r.outlineVariant),
        error = c(r.error), onError = c(r.onError),
        errorContainer = c(r.errorContainer), onErrorContainer = c(r.onErrorContainer),
        surfaceDim = c(r.surfaceDim), surfaceBright = c(r.surfaceBright),
        surfaceContainerLowest = c(r.surfaceLowest), surfaceContainerLow = c(r.surfaceLow),
        surfaceContainer = c(r.surfaceContainer), surfaceContainerHigh = c(r.surfaceHigh),
        surfaceContainerHighest = c(r.surfaceHighest),
        inverseSurface = c(r.inverseSurface), inverseOnSurface = c(r.inverseOnSurface),
        // RC29: no purple elevation tint on any surface; elevation is expressed by the surface ladder.
        surfaceTint = Color.Transparent, scrim = Color.Black,
    )
}

/** Semantic status colors (success / warning / danger / info / neutral) for the current light or dark scheme. */
@Composable
fun statusColors(kind: String): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val s = (if (dark) HalaqatiPalette.STATUS_DARK else HalaqatiPalette.STATUS_LIGHT).of(kind)
    return Color(s.bg) to Color(s.fg)
}

/** RC29: hairline that separates cards from the true-black dark page; light mode stays borderless. */
@Composable
fun cardHairline(): BorderStroke? =
    if (MaterialTheme.colorScheme.background.luminance() < .5f) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null

private val HalaqatiShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(HalaqatiUiTokens.RadiusSmall),
    medium = RoundedCornerShape(HalaqatiUiTokens.RadiusLarge),
    large = RoundedCornerShape(HalaqatiUiTokens.RadiusXl),
    extraLarge = RoundedCornerShape(HalaqatiUiTokens.RadiusSheet),
)

/** WCAG contrast ratio between two opaque colors. */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return if (la > lb) la / lb else lb / la
}

/** Text/icon color for content on [color]: whichever of near-black / white reads better. */
private fun readableOn(color: Color): Color {
    val dark = Color(0xFF0B1511)
    return if (contrastRatio(color, dark) >= contrastRatio(color, Color.White)) dark else Color.White
}

private fun blend(from: Color, to: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = 1f,
    )
}

/**
 * RC29: whatever primary/accent the user picked, text and icons drawn in that color must stay readable on every
 * surface of the current mode. The hue is kept; only its lightness moves, step by step, until it reaches [min]:1
 * against the hardest background (the highest surface of the ladder).
 */
private fun readableAccent(color: Color, against: Color, min: Float = 4.5f): Color {
    val toward = if (against.luminance() > .5f) Color.Black else Color.White
    var result = color
    var steps = 0
    while (contrastRatio(result, against) < min && steps < 14) {
        result = blend(result, toward, .1f)
        steps++
    }
    return result
}

object DensityScale {
    fun layout(density: UiDensity): Float = when (density) { UiDensity.COMPACT -> 0.94f; UiDensity.BALANCED -> 1.0f; UiDensity.COMFORTABLE -> 1.06f }
    fun text(density: UiDensity): Float = when (density) { UiDensity.COMPACT -> 0.97f; UiDensity.BALANCED -> 1.0f; UiDensity.COMFORTABLE -> 1.03f }
}

/**
 * The colour scheme the app actually draws with, for a preference set and mode. Extracted from [HalaqatiTheme] unchanged so the
 * contrast unit test (ContrastTest) can check the real colours of both themes without a Compose runtime.
 */
internal fun halaqatiColorScheme(preferences: UiPreferences, dark: Boolean): androidx.compose.material3.ColorScheme {
    val selectedPrimary = Color(preferences.primaryArgb)
    val selectedAccent = Color(preferences.accentArgb)
    val base = if (dark) HalaqatiDark else HalaqatiLight
    val hardest = base.surfaceContainerHighest
    val primary = readableAccent(selectedPrimary, hardest)
    val accent = readableAccent(selectedAccent, hardest)
    return base.copy(
        primary = primary,
        onPrimary = readableOn(primary),
        secondary = accent,
        onSecondary = readableOn(accent),
        // Containers carry the user's hue but stay in the mode's own value range, so text in `primary` or
        // `onPrimaryContainer` is readable on them in both light and dark.
        primaryContainer = if (dark) blend(selectedPrimary, Color.Black, .5f) else base.primaryContainer,
        onPrimaryContainer = if (dark) Color(0xFFD6F5E8) else base.onPrimaryContainer,
        // RC47: Material's default (filled) card paints surfaceContainerHighest, a dull beige in the light theme.
        // Cards, text fields and menus now sit as clean white sheets on the warm grey page, like iOS grouped lists.
        surfaceContainerHighest = if (dark) base.surfaceContainerHighest else Color.White,
        surfaceContainerHigh = if (dark) base.surfaceContainerHigh else Color(0xFFFAF9F5),
    )
}

@Composable
fun HalaqatiTheme(preferences: UiPreferences = UiPreferences.defaults(), content: @Composable () -> Unit) {
    val dark = when (preferences.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme = halaqatiColorScheme(preferences, dark)
    // RC27.10: density really scales the layout. dp values scale by [DensityScale]; text moves only half as much, so
    // "مضغوط" fits more on screen without making the Arabic text hard to read, and "مريح" stays comfortable.
    val systemDensity = androidx.compose.ui.platform.LocalDensity.current
    val layoutScale = DensityScale.layout(preferences.density)
    val scaledDensity = androidx.compose.ui.unit.Density(
        density = systemDensity.density * layoutScale,
        fontScale = systemDensity.fontScale * DensityScale.text(preferences.density) / layoutScale,
    )
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        androidx.compose.ui.platform.LocalDensity provides scaledDensity,
        LocalReducedMotion provides (preferences.motion == com.mohammedsamir.halaqati.data.MotionLevel.REDUCED),
        LocalHalaqatiSpacing provides spacing(preferences.density),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = cairoTypography(),
            shapes = HalaqatiShapes,
            content = content,
        )
    }
}


/**
 * RC32: colour for emphasised text. In the dark theme it is the plain on-surface (white) text colour so headings and
 * numbers never turn green/pink/brown on a dark card; in the light theme it is the brand colour as before.
 */
@Composable
fun textAccent(kind: String = "primary"): Color {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < .5f
    if (dark) return scheme.onSurface
    return when (kind) { "tertiary" -> scheme.tertiary; "secondary" -> scheme.secondary; else -> scheme.primary }
}
