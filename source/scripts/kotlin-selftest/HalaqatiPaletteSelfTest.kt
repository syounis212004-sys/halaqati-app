import com.mohammedsamir.halaqati.ui.theme.HalaqatiPalette

private fun check(ok: Boolean, msg: String) { if (!ok) { println("FAIL: $msg"); kotlin.system.exitProcess(1) } }

fun main() {
    var worst = 99.0
    var bad = 0
    HalaqatiPalette.textPairs().forEach { (name, fg, bg) ->
        val c = HalaqatiPalette.contrast(fg, bg)
        if (c < worst) worst = c
        if (c < 4.5) { println("LOW CONTRAST %.2f  %s".format(c, name)); bad++ }
    }
    check(bad == 0, "$bad pairs below WCAG AA 4.5:1")
    // Non-text UI parts (borders, focus) need 3:1 against the surface they sit on.
    check(HalaqatiPalette.contrast(HalaqatiPalette.LIGHT.outline, HalaqatiPalette.LIGHT.surface) >= 3.0, "light outline vs surface")
    check(HalaqatiPalette.contrast(HalaqatiPalette.DARK.outline, HalaqatiPalette.DARK.surface) >= 3.0, "dark outline vs surface")
    // Status colors must be distinguishable from each other and from plain info pills.
    for (set in listOf(HalaqatiPalette.STATUS_LIGHT, HalaqatiPalette.STATUS_DARK)) {
        val bgs = listOf(set.success.bg, set.warning.bg, set.danger.bg, set.info.bg, set.neutral.bg)
        check(bgs.toSet().size == 5, "status backgrounds must differ")
        check(set.of("success") != set.of("info") && set.of("error") != set.of("warning"), "kind mapping")
    }
    println("HalaqatiPaletteSelfTest PASS (worst contrast %.2f:1)".format(worst))
}
