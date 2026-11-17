package com.mohammedsamir.halaqati.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * One fixed hue per kind of thing in the app (halaqa, student, category, ...), so the same entity always
 * looks the same on every screen. Status colors (success/error) stay separate and keep their own meaning.
 * Each pair keeps text on its container above 4.5:1 in both themes.
 */
enum class Entity { HALAQA, STUDENT, CATEGORY, SUPERVISOR, TEACHER, ASSOCIATION, DONOR, ANNOUNCEMENT }

data class EntityTone(val accent: Color, val container: Color, val onContainer: Color)

object EntityColors {
    private val light = mapOf(
        Entity.HALAQA to EntityTone(Color(0xFF0B6E4F), Color(0xFFD3EFE3), Color(0xFF053B2A)),
        Entity.STUDENT to EntityTone(Color(0xFF0B6F96), Color(0xFFD2ECF8), Color(0xFF053A50)),
        Entity.CATEGORY to EntityTone(Color(0xFF9A6A00), Color(0xFFF8E6B8), Color(0xFF4A3200)),
        Entity.SUPERVISOR to EntityTone(Color(0xFF6B4FA3), Color(0xFFE6DDF6), Color(0xFF2E1D54)),
        Entity.TEACHER to EntityTone(Color(0xFF0E7C7B), Color(0xFFCDEDEC), Color(0xFF053F3E)),
        Entity.ASSOCIATION to EntityTone(Color(0xFFB45A12), Color(0xFFFADDC4), Color(0xFF5A2A04)),
        Entity.DONOR to EntityTone(Color(0xFFB0415A), Color(0xFFF8D9E0), Color(0xFF5A1426)),
        Entity.ANNOUNCEMENT to EntityTone(Color(0xFF3F51B5), Color(0xFFDCE0F7), Color(0xFF1A2260)),
    )
    private val dark = mapOf(
        Entity.HALAQA to EntityTone(Color(0xFF5FD4A6), Color(0xFF123D2F), Color(0xFFBFF0DB)),
        Entity.STUDENT to EntityTone(Color(0xFF63C6EE), Color(0xFF0F3547), Color(0xFFC4E9F8)),
        Entity.CATEGORY to EntityTone(Color(0xFFF0C25A), Color(0xFF45340A), Color(0xFFFBE7B0)),
        Entity.SUPERVISOR to EntityTone(Color(0xFFC3A9F2), Color(0xFF33244F), Color(0xFFE6DAFB)),
        Entity.TEACHER to EntityTone(Color(0xFF5DD6D4), Color(0xFF0F3C3B), Color(0xFFC2F0EE)),
        Entity.ASSOCIATION to EntityTone(Color(0xFFF2A15F), Color(0xFF47270E), Color(0xFFFCDDC2)),
        Entity.DONOR to EntityTone(Color(0xFFF08AA3), Color(0xFF4A1B29), Color(0xFFFBD5DE)),
        Entity.ANNOUNCEMENT to EntityTone(Color(0xFF9FAEF5), Color(0xFF252C5C), Color(0xFFDCE1FB)),
    )

    fun tone(entity: Entity, dark: Boolean): EntityTone = (if (dark) this.dark else this.light).getValue(entity)

    @Composable
    fun of(entity: Entity): EntityTone = tone(entity, MaterialTheme.colorScheme.background.luminance() < 0.5f)
}
