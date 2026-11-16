package com.mohammedsamir.halaqati.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class UiDensity { COMFORTABLE, BALANCED, COMPACT }
enum class HomeStyle { MODERN, COMPACT }
enum class MotionLevel { NORMAL, REDUCED }
enum class AvatarShape { RING, CIRCLE, ROUNDED }

data class UiPreferences(
    val themeMode: ThemeMode,
    val primaryArgb: Int,
    val accentArgb: Int,
    val density: UiDensity,
    val homeStyle: HomeStyle = HomeStyle.MODERN,
    val motion: MotionLevel = MotionLevel.NORMAL,
    val showStudentPhotos: Boolean = true,
    val avatarShape: AvatarShape = AvatarShape.RING,
) {
    companion object {
        fun defaults() = UiPreferences(
            themeMode = ThemeMode.SYSTEM,
            primaryArgb = com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.DEFAULT_PRIMARY,
            accentArgb = com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.DEFAULT_ACCENT,
            density = UiDensity.COMPACT,
            homeStyle = HomeStyle.COMPACT,
        )
    }
}

class UiPreferencesStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("halaqati_ui_v1", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<UiPreferences> = _state.asStateFlow()

    fun update(transform: (UiPreferences) -> UiPreferences) {
        val next = transform(_state.value)
        prefs.edit()
            .putString("theme_mode", next.themeMode.name)
            .putInt("primary_argb", next.primaryArgb)
            .putInt("accent_argb", next.accentArgb)
            .putString("density", next.density.name)
            .putString("home_style", next.homeStyle.name)
            .putString("motion", next.motion.name)
            .putBoolean("show_student_photos", next.showStudentPhotos)
            .putString("avatar_shape", next.avatarShape.name)
            .apply()
        _state.value = next
    }

    fun reset() = update { UiPreferences.defaults() }

    private fun read(): UiPreferences {
        val d = UiPreferences.defaults()
        val storedPrimary = prefs.getInt("primary_argb", d.primaryArgb)
        val storedAccent = prefs.getInt("accent_argb", d.accentArgb)
        // T6 used a temporary teal default. Migrate only that exact pair so genuine
        // user-selected custom colors are preserved while existing installs regain 2.44's palette.
        val legacyDefaults =
            (storedPrimary == 0xFF147A78.toInt() && storedAccent == 0xFF0B4F4B.toInt()) ||
            (storedPrimary == 0xFF0D3B2F.toInt() && storedAccent == 0xFF2F8B69.toInt()) ||
            // RC27: the previous green default moves to the new, higher-contrast default.
            (storedPrimary == com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.OLD_DEFAULT_PRIMARY && storedAccent == com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.OLD_DEFAULT_ACCENT) ||
            // RC62: the RC27–RC61 default pair moves to the stronger RC62 green.
            (storedPrimary == com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.RC27_DEFAULT_PRIMARY && storedAccent == com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette.RC27_DEFAULT_ACCENT)
        return UiPreferences(
            themeMode = enumValueOrDefault(prefs.getString("theme_mode", null), d.themeMode),
            primaryArgb = if (legacyDefaults) d.primaryArgb else storedPrimary,
            accentArgb = if (legacyDefaults) d.accentArgb else storedAccent,
            density = enumValueOrDefault(prefs.getString("density", null), d.density),
            homeStyle = enumValueOrDefault(prefs.getString("home_style", null), d.homeStyle),
            motion = enumValueOrDefault(prefs.getString("motion", null), d.motion),
            showStudentPhotos = prefs.getBoolean("show_student_photos", d.showStudentPhotos),
            avatarShape = enumValueOrDefault(prefs.getString("avatar_shape", null), d.avatarShape),
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback
}
