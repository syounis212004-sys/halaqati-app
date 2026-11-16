package com.mohammedsamir.halaqati.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.mohammedsamir.halaqati.data.ThemeMode
import com.mohammedsamir.halaqati.data.UiPreferences
import com.mohammedsamir.halaqati.ui.components.NourButtonColors
import com.mohammedsamir.halaqati.ui.components.NourHeroColors
import com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette
import com.mohammedsamir.halaqati.ui.theme.contrastRatio
import com.mohammedsamir.halaqati.ui.theme.halaqatiColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * RC98: real contrast checks on the colours the app draws with, in BOTH themes and for every colour preset a user can pick in
 * Settings. This replaces what the RC29 / RC38 source gates could only do by string-matching (they cannot tell a readable
 * pair from an unreadable one). WCAG AA: 4.5:1 for small text, 3:1 for large text and for the boundary of a control.
 */
class ContrastTest {
    private val presets: List<Pair<String, Pair<Int, Int>>> = listOf(
        "default" to (HalaqatiPalette.DEFAULT_PRIMARY to HalaqatiPalette.DEFAULT_ACCENT),
        "olive" to (0xFF3B4929.toInt() to 0xFF5B7A3A.toInt()),
        "blue" to (0xFF12345B.toInt() to 0xFF2D6CDF.toInt()),
        "brown" to (0xFF5A3825.toInt() to 0xFFAD744F.toInt()),
        "violet" to (0xFF342B5F.toInt() to 0xFF765BC4.toInt()),
    )

    private fun everyScheme(check: (name: String, cs: androidx.compose.material3.ColorScheme) -> Unit) {
        for ((name, pair) in presets) for (dark in listOf(false, true)) {
            val prefs = UiPreferences.defaults().copy(primaryArgb = pair.first, accentArgb = pair.second, themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT)
            check("$name/${if (dark) "dark" else "light"}", halaqatiColorScheme(prefs, dark))
        }
    }

    private val failures = mutableListOf<String>()
    private fun need(label: String, a: Color, b: Color, min: Float) {
        val r = contrastRatio(a, b)
        if (r < min) failures += "%s: %.2f < %.1f".format(label, r, min)
    }
    private fun done() = assertTrue("contrast failures:\n" + failures.joinToString("\n"), failures.isEmpty())

    @Test fun rolePairsAreReadable() {
        everyScheme { n, cs ->
            need("$n onSurface/surface", cs.onSurface, cs.surface, 4.5f)
            need("$n onSurface/background", cs.onSurface, cs.background, 4.5f)
            need("$n onSurfaceVariant/surface", cs.onSurfaceVariant, cs.surface, 4.5f)
            need("$n onPrimary/primary", cs.onPrimary, cs.primary, 4.5f)
            need("$n onPrimaryContainer/primaryContainer", cs.onPrimaryContainer, cs.primaryContainer, 4.5f)
            need("$n onSecondaryContainer/secondaryContainer", cs.onSecondaryContainer, cs.secondaryContainer, 4.5f)
            need("$n onTertiaryContainer/tertiaryContainer", cs.onTertiaryContainer, cs.tertiaryContainer, 4.5f)
            need("$n onErrorContainer/errorContainer", cs.onErrorContainer, cs.errorContainer, 4.5f)
            need("$n onSurfaceVariant/surfaceVariant", cs.onSurfaceVariant, cs.surfaceVariant, 4.5f)
        }
        done()
    }

    /** White text on every point of the hero gradient, plus the worst case of the white lattice (NourHeroColors.LatticeAlpha) drawn over its lightest stop. */
    @Test fun heroStripTextIsReadableInBothThemes() {
        everyScheme { n, cs ->
            val stops = NourHeroColors.stops(cs.primary)
            stops.forEachIndexed { i, s ->
                need("$n hero stop $i / white label", s, Color.White, NourHeroColors.MIN_WHITE_CONTRAST)
                need("$n hero stop $i + lattice / white label", lerp(s, Color.White, NourHeroColors.LatticeAlpha), Color.White, NourHeroColors.MIN_WHITE_CONTRAST)
                need("$n hero stop $i / attention number (large text)", s, NourHeroColors.Attention, 3f)
            }
        }
        done()
    }

    /** The tile gradient runs container -> surface; the text is the container's `on` colour, so check BOTH ends. */
    @Test fun statTilesAreReadableAtBothGradientEnds() {
        everyScheme { n, cs ->
            val tones = mapOf(
                "neutral" to (cs.primaryContainer to cs.onPrimaryContainer),
                "success" to (cs.primaryContainer to cs.onPrimaryContainer),
                "warning" to (cs.tertiaryContainer to cs.onTertiaryContainer),
                "danger" to (cs.errorContainer to cs.onErrorContainer),
            )
            for ((tone, pair) in tones) {
                val (container, on) = pair
                need("$n tile $tone bottom", container, on, 4.5f)
                need("$n tile $tone top", lerp(container, cs.surface, .45f), on, 4.5f)
            }
        }
        done()
    }

    /** Selected vs unselected filter chip: readable labels AND a selected fill that stands out from the surface without relying on hue. */
    @Test fun filterChipStatesAreDistinguishable() {
        everyScheme { n, cs ->
            need("$n selected chip label", cs.onPrimary, cs.primary, 4.5f)
            need("$n unselected chip label", cs.onSurface, cs.surface, 4.5f)
            need("$n selected fill vs surface (control boundary)", cs.primary, cs.surface, 3f)
        }
        done()
    }

    @Test fun defaultButtonGradientKeepsItsLabelReadable() {
        everyScheme { n, cs ->
            NourButtonColors.stops(cs.primary).forEachIndexed { i, s -> need("$n button stop $i / onPrimary", s, cs.onPrimary, 4.5f) }
        }
        done()
    }

    @Test fun heroBaseKeepsTheUsersHue() {
        // The darkening must only move lightness: a pure blue stays blue, it must not turn grey or green.
        val blue = Color(0xFF3F6FD8)
        val b = NourHeroColors.base(blue)
        assertTrue("base should stay blue-dominant", b.blue > b.red && b.blue > b.green)
        assertTrue("base must read with white", contrastRatio(b, Color.White) >= 4.5f)
    }
}
