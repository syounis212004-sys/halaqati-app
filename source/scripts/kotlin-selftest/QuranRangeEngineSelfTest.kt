import com.mohammedsamir.halaqati.quran.*

private class TestLayout : QuranLineLayout {
    override val source = "test-qcf4"
    override val lineModel = 15
    override fun page(globalAyah: Int): Int = if (globalAyah == QuranMetadata.globalAyah(1, 1)) 600 else QuranMetadata.pageFor(QuranMetadata.location(globalAyah).surah, QuranMetadata.location(globalAyah).ayah)
    override fun firstLine(globalAyah: Int): Int = when (globalAyah) {
        QuranMetadata.globalAyah(1, 1) -> 2
        QuranMetadata.globalAyah(1, 2) -> 3
        QuranMetadata.globalAyah(1, 3) -> 3
        else -> 1
    }
    override fun lastLine(globalAyah: Int): Int = when (globalAyah) {
        QuranMetadata.globalAyah(1, 1) -> 2
        QuranMetadata.globalAyah(1, 2) -> 4
        QuranMetadata.globalAyah(1, 3) -> 5
        else -> 1
    }
}

private fun checkClose(actual: Double, expected: Double, eps: Double = 1e-9) {
    check(kotlin.math.abs(actual - expected) <= eps) { "Expected $expected, got $actual" }
}

fun main() {
    val engine = QuranRangeEngine(TestLayout())

    val partial = engine.calculate(
        QuranRangeRequest(
            track = "tathbit",
            activityType = "sard",
            selection = QuranSelection.Ayahs(QuranLocation(1, 1), QuranLocation(1, 3)),
        )
    )
    check(partial.qcfLineKeys.first() == "600:2") { "Engine must use QCF source page: ${partial.qcfLineKeys}" }
    checkClose(partial.exactPages, 4.0 / 15.0)
    check(partial.ayahCount == 3)

    val general = engine.calculate(
        QuranRangeRequest(
            track = "general_hifz",
            activityType = "new_hifz",
            selection = QuranSelection.Ayahs(QuranLocation(42, 27), QuranLocation(41, 18)),
        )
    )
    check(general.resolvedTraversal == QuranResolvedTraversal.REVERSE_SURAH_ORDER)
    check(general.orderedSegments.size == 2)
    check(general.orderedSegments[0].start == QuranLocation(42, 27))
    check(general.orderedSegments[0].end == QuranLocation(42, 53))
    check(general.orderedSegments[1].start == QuranLocation(41, 1))
    check(general.orderedSegments[1].end == QuranLocation(41, 18))

    // RC8.4 regression: general-hifz moves from Fussilat (41) to Ghafir (40),
    // while ayahs inside each surah always remain ascending.
    val fussilatToGhafir = engine.calculate(
        QuranRangeRequest(
            track = "general_hifz",
            activityType = "new_hifz",
            selection = QuranSelection.Ayahs(QuranLocation(41, 36), QuranLocation(40, 20)),
            direction = QuranTraversalDirection.REVERSE_SURAH_ORDER,
        )
    )
    check(fussilatToGhafir.orderedSegments.size == 2)
    check(fussilatToGhafir.orderedSegments[0].start == QuranLocation(41, 36))
    check(fussilatToGhafir.orderedSegments[0].end == QuranLocation(41, QuranMetadata.maxAyah(41)))
    check(fussilatToGhafir.orderedSegments[1].start == QuranLocation(40, 1))
    check(fussilatToGhafir.orderedSegments[1].end == QuranLocation(40, 20))
    check(fussilatToGhafir.orderedSegments.all { it.ayahDirection == QuranAyahDirection.ASCENDING })

    val tathbitForward = engine.calculate(
        QuranRangeRequest(
            track = "tathbit",
            activityType = "review_far",
            selection = QuranSelection.Ayahs(QuranLocation(40, 1), QuranLocation(41, 20)),
            direction = QuranTraversalDirection.FORWARD_QURAN,
        )
    )
    check(tathbitForward.resolvedTraversal == QuranResolvedTraversal.FORWARD_QURAN)
    check(tathbitForward.orderedSegments.first().start == QuranLocation(40, 1))
    check(tathbitForward.orderedSegments.last().end == QuranLocation(41, 20))

    val sardDescending = engine.calculate(
        QuranRangeRequest(
            track = "tathbit",
            activityType = "sard",
            selection = QuranSelection.Ayahs(QuranLocation(42, 53), QuranLocation(42, 27)),
        )
    )
    check(sardDescending.resolvedTraversal == QuranResolvedTraversal.REVERSE_WITHIN_SURAH)
    check(sardDescending.orderedSegments.single().ayahDirection == QuranAyahDirection.DESCENDING)
    check(sardDescending.start == QuranLocation(42, 53) && sardDescending.end == QuranLocation(42, 27))

    val generalHifzPagesForward = engine.calculate(
        QuranRangeRequest(
            track = "general_hifz",
            activityType = "new_hifz",
            selection = QuranSelection.Pages(486, 488),
        ),
    )
    check(generalHifzPagesForward.resolvedTraversal == QuranResolvedTraversal.PAGE_ORDER_FORWARD)
    checkClose(generalHifzPagesForward.exactPages, 3.0)

    val fullPages = engine.calculate(
        QuranRangeRequest(
            track = "tathbit",
            activityType = "review_near",
            selection = QuranSelection.Pages(550, 549),
        )
    )
    check(fullPages.resolvedTraversal == QuranResolvedTraversal.PAGE_ORDER_REVERSE)
    checkClose(fullPages.exactPages, 2.0)
    check(fullPages.startPage == 550 && fullPages.endPage == 549)

    val sardPreviewReverse = engine.previewPagesFrom(
        start = QuranLocation(114, 6),
        targetPages = 2.0 / 15.0,
        track = "general_hifz",
        activityType = "sard",
        direction = QuranTraversalDirection.REVERSE_SURAH_ORDER,
    )
    check(sardPreviewReverse.start == QuranLocation(114, 6))
    check(sardPreviewReverse.end == QuranLocation(111, 1)) { "Reverse Sard preview must continue across lower-numbered surahs: ${sardPreviewReverse.end}" }
    check(sardPreviewReverse.resolvedTraversal == QuranResolvedTraversal.REVERSE_SURAH_ORDER)
    check(sardPreviewReverse.orderedSegments.all { it.ayahDirection == QuranAyahDirection.ASCENDING })

    val juz30 = engine.calculate(
        QuranRangeRequest(
            track = "general_hifz",
            activityType = "new_hifz",
            selection = QuranSelection.Juz(listOf(30)),
        )
    )
    check(juz30.orderedSegments.first().start.surah == 114) {
        "General-hifz Juz must start from highest surah, got ${juz30.orderedSegments.first().start}"
    }
    check(juz30.orderedSegments.last().start.surah == 78) {
        "General-hifz Juz 30 must finish in An-Naba (78), got ${juz30.orderedSegments.last().start}"
    }
    check(juz30.orderedSegments.all { it.ayahDirection == QuranAyahDirection.ASCENDING })

    println("QuranRangeEngineSelfTest OK")
}
