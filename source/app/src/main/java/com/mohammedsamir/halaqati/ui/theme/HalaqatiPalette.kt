package com.mohammedsamir.halaqati.ui.theme

import kotlin.math.pow

/**
 * RC27 palette as plain ARGB ints (no Compose) so contrast can be verified by a self-test.
 * Every text/background pair below is checked to WCAG AA (>= 4.5:1) in HalaqatiPaletteSelfTest.
 */
object HalaqatiPalette {
    const val DEFAULT_PRIMARY = 0xFF0B6E4F.toInt()
    const val DEFAULT_ACCENT = 0xFF0B7A55.toInt()

    /** Colors shipped before RC27; an install still using exactly this pair moves to the new defaults. */
    const val OLD_DEFAULT_PRIMARY = 0xFF155643.toInt()
    const val OLD_DEFAULT_ACCENT = 0xFF2F8B69.toInt()

    /** RC62: the RC27–RC61 default pair; an install still using it moves to the stronger RC62 green. */
    const val RC27_DEFAULT_PRIMARY = 0xFF0B5D4F.toInt()
    const val RC27_DEFAULT_ACCENT = 0xFF1B7F6B.toInt()

    data class Roles(
        val primary: Int, val onPrimary: Int, val primaryContainer: Int, val onPrimaryContainer: Int,
        val secondary: Int, val onSecondary: Int, val secondaryContainer: Int, val onSecondaryContainer: Int,
        val tertiary: Int, val onTertiary: Int, val tertiaryContainer: Int, val onTertiaryContainer: Int,
        val background: Int, val onBackground: Int, val surface: Int, val onSurface: Int,
        val surfaceVariant: Int, val onSurfaceVariant: Int, val outline: Int, val outlineVariant: Int,
        val error: Int, val onError: Int, val errorContainer: Int, val onErrorContainer: Int,
        /**
         * RC29: the full tonal surface ladder. Material 3 widgets (cards, dialogs, menus, bottom bar, sheets, text fields)
         * read these roles directly; when they were left unset they fell back to Material's purple-grey baseline and
         * clashed with the green identity (the "mismatched colors" users saw in the dark theme).
         */
        val surfaceDim: Int, val surfaceBright: Int,
        val surfaceLowest: Int, val surfaceLow: Int, val surfaceContainer: Int, val surfaceHigh: Int, val surfaceHighest: Int,
        val inverseSurface: Int, val inverseOnSurface: Int,
    )

    /** Semantic status pair (background, text). Each status keeps its own hue so states read at a glance. */
    data class Status(val bg: Int, val fg: Int)
    data class StatusSet(val success: Status, val warning: Status, val danger: Status, val info: Status, val neutral: Status) {
        fun of(kind: String): Status = when (kind) {
            "success" -> success
            "warning" -> warning
            "error", "danger" -> danger
            "neutral" -> neutral
            else -> info
        }
    }

    val LIGHT = Roles(
        primary = DEFAULT_PRIMARY, onPrimary = 0xFFFFFFFF.toInt(),
        primaryContainer = 0xFFB8E9D1.toInt(), onPrimaryContainer = 0xFF00281C.toInt(),
        secondary = DEFAULT_ACCENT, onSecondary = 0xFFFFFFFF.toInt(),
        secondaryContainer = 0xFFBFE9D3.toInt(), onSecondaryContainer = 0xFF00281C.toInt(),
        tertiary = 0xFF8A5A00.toInt(), onTertiary = 0xFFFFFFFF.toInt(),
        tertiaryContainer = 0xFFF2CF7A.toInt(), onTertiaryContainer = 0xFF2E1D00.toInt(),
        background = 0xFFF1EFE8.toInt(), onBackground = 0xFF12261F.toInt(),
        surface = 0xFFFFFFFF.toInt(), onSurface = 0xFF12261F.toInt(),
        surfaceVariant = 0xFFEFEEE4.toInt(), onSurfaceVariant = 0xFF42564D.toInt(),
        outline = 0xFF72877D.toInt(), outlineVariant = 0xFFE4E0D2.toInt(),
        error = 0xFFC62828.toInt(), onError = 0xFFFFFFFF.toInt(),
        errorContainer = 0xFFF2A7A2.toInt(), onErrorContainer = 0xFF410002.toInt(),
        surfaceDim = 0xFFDCDACC.toInt(), surfaceBright = 0xFFFFFFFF.toInt(),
        surfaceLowest = 0xFFFFFFFF.toInt(), surfaceLow = 0xFFFAF9F5.toInt(), surfaceContainer = 0xFFF6F4EE.toInt(),
        surfaceHigh = 0xFFEBE8DC.toInt(), surfaceHighest = 0xFFE4E1D3.toInt(),
        inverseSurface = 0xFF1E2B25.toInt(), inverseOnSurface = 0xFFEAF2EE.toInt(),
    )

    val DARK = Roles(
        primary = 0xFF4FD6A1.toInt(), onPrimary = 0xFF00382A.toInt(),
        primaryContainer = 0xFF084F39.toInt(), onPrimaryContainer = 0xFFD6F5E8.toInt(),
        secondary = 0xFF7FD9B6.toInt(), onSecondary = 0xFF06291F.toInt(),
        secondaryContainer = 0xFF11654A.toInt(), onSecondaryContainer = 0xFFD6F5E8.toInt(),
        tertiary = 0xFFEBC66F.toInt(), onTertiary = 0xFF3A2800.toInt(),
        tertiaryContainer = 0xFF3A2D08.toInt(), onTertiaryContainer = 0xFFF8E7B8.toInt(),
        background = 0xFF000000.toInt(), onBackground = 0xFFF0F6F2.toInt(),
        surface = 0xFF16211C.toInt(), onSurface = 0xFFF0F6F2.toInt(),
        surfaceVariant = 0xFF1A2520.toInt(), onSurfaceVariant = 0xFFCDDBD3.toInt(),
        outline = 0xFF9DB8AB.toInt(), outlineVariant = 0xFF33403A.toInt(),
        error = 0xFFFF8A80.toInt(), onError = 0xFF4A0B07.toInt(),
        errorContainer = 0xFFB3261E.toInt(), onErrorContainer = 0xFFFFDAD6.toInt(),
        surfaceDim = 0xFF000000.toInt(), surfaceBright = 0xFF2A3731.toInt(),
        surfaceLowest = 0xFF050907.toInt(), surfaceLow = 0xFF0B110E.toInt(), surfaceContainer = 0xFF151F1A.toInt(),
        surfaceHigh = 0xFF1B2722.toInt(), surfaceHighest = 0xFF22312A.toInt(),
        inverseSurface = 0xFFE4EEE8.toInt(), inverseOnSurface = 0xFF14201B.toInt(),
    )

    val STATUS_LIGHT = StatusSet(
        success = Status(0xFF0F7A4A.toInt(), 0xFFFFFFFF.toInt()),
        warning = Status(0xFFF2B01E.toInt(), 0xFF2B1B00.toInt()),
        danger = Status(0xFFC62828.toInt(), 0xFFFFFFFF.toInt()),
        info = Status(0xFF1565C0.toInt(), 0xFFFFFFFF.toInt()),
        neutral = Status(0xFF56655E.toInt(), 0xFFFFFFFF.toInt()),
    )

    val STATUS_DARK = StatusSet(
        success = Status(0xFF14804F.toInt(), 0xFFFFFFFF.toInt()),
        warning = Status(0xFFE0A100.toInt(), 0xFF1A1200.toInt()),
        danger = Status(0xFFC62828.toInt(), 0xFFFFFFFF.toInt()),
        info = Status(0xFF1E6FD0.toInt(), 0xFFFFFFFF.toInt()),
        neutral = Status(0xFF4A5A53.toInt(), 0xFFFFFFFF.toInt()),
    )

    private fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(argb: Int): Double =
        0.2126 * channel((argb shr 16) and 0xFF) + 0.7152 * channel((argb shr 8) and 0xFF) + 0.0722 * channel(argb and 0xFF)

    /** WCAG 2.x contrast ratio, 1.0 .. 21.0. */
    fun contrast(a: Int, b: Int): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    /** Every (name, text, background) pair that must stay readable. */
    fun textPairs(): List<Triple<String, Int, Int>> = buildList {
        for ((mode, r) in listOf("light" to LIGHT, "dark" to DARK)) {
            add(Triple("$mode onBackground/background", r.onBackground, r.background))
            add(Triple("$mode onSurface/surface", r.onSurface, r.surface))
            add(Triple("$mode onSurfaceVariant/surface", r.onSurfaceVariant, r.surface))
            add(Triple("$mode onSurfaceVariant/background", r.onSurfaceVariant, r.background))
            add(Triple("$mode onSurfaceVariant/surfaceVariant", r.onSurfaceVariant, r.surfaceVariant))
            add(Triple("$mode onSurface/surfaceVariant", r.onSurface, r.surfaceVariant))
            add(Triple("$mode primary/surface", r.primary, r.surface))
            add(Triple("$mode primary/background", r.primary, r.background))
            add(Triple("$mode onPrimary/primary", r.onPrimary, r.primary))
            add(Triple("$mode onSecondary/secondary", r.onSecondary, r.secondary))
            add(Triple("$mode secondary/surface", r.secondary, r.surface))
            add(Triple("$mode onTertiary/tertiary", r.onTertiary, r.tertiary))
            add(Triple("$mode tertiary/surface", r.tertiary, r.surface))
            add(Triple("$mode onPrimaryContainer/primaryContainer", r.onPrimaryContainer, r.primaryContainer))
            add(Triple("$mode onSecondaryContainer/secondaryContainer", r.onSecondaryContainer, r.secondaryContainer))
            add(Triple("$mode onTertiaryContainer/tertiaryContainer", r.onTertiaryContainer, r.tertiaryContainer))
            for ((n, bg) in listOf("surfaceLow" to r.surfaceLow, "surfaceContainer" to r.surfaceContainer, "surfaceHigh" to r.surfaceHigh, "surfaceHighest" to r.surfaceHighest)) {
                add(Triple("$mode onSurface/$n", r.onSurface, bg))
                add(Triple("$mode onSurfaceVariant/$n", r.onSurfaceVariant, bg))
                add(Triple("$mode primary/$n", r.primary, bg))
            }
            add(Triple("$mode primary/primaryContainer", r.primary, r.primaryContainer))
            add(Triple("$mode onSurface/primaryContainer", r.onSurface, r.primaryContainer))
            add(Triple("$mode inverseOnSurface/inverseSurface", r.inverseOnSurface, r.inverseSurface))
            add(Triple("$mode onError/error", r.onError, r.error))
            add(Triple("$mode error/surface", r.error, r.surface))
            add(Triple("$mode onErrorContainer/errorContainer", r.onErrorContainer, r.errorContainer))
        }
        for ((mode, set) in listOf("light" to STATUS_LIGHT, "dark" to STATUS_DARK)) {
            for ((name, s) in listOf("success" to set.success, "warning" to set.warning, "danger" to set.danger, "info" to set.info, "neutral" to set.neutral)) {
                add(Triple("$mode status $name", s.fg, s.bg))
            }
        }
    }
}
