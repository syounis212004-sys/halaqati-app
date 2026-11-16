package com.mohammedsamir.halaqati.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesStoreTest {
    @Test fun defaultPreferencesMatchProductContract() {
        val p = UiPreferences.defaults()
        assertEquals(UiDensity.COMPACT, p.density)
        assertEquals(ThemeMode.SYSTEM, p.themeMode)
    }
}
