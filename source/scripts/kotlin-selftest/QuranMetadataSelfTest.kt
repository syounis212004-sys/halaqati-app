import com.mohammedsamir.halaqati.quran.QuranMetadata

fun main() {
    check(QuranMetadata.SURAH_COUNT == 114)
    check(QuranMetadata.AYAH_COUNT == 6236)
    check(QuranMetadata.PAGE_COUNT == 604)
    check(QuranMetadata.JUZ_COUNT == 30)
    check(QuranMetadata.RUB_COUNT == 240)
    check(QuranMetadata.surahName(1) == "الفاتحة")
    check(QuranMetadata.maxAyah(2) == 286)
    check(QuranMetadata.pageFor(1, 1) == 1)
    check(QuranMetadata.pageFor(114, 6) == 604)
    check(QuranMetadata.juzFor(1, 1) == 1)
    check(QuranMetadata.juzFor(114, 6) == 30)
    check(QuranMetadata.rubFor(1, 1) == 1)
    check(QuranMetadata.rubFor(114, 6) == 240)
    check(QuranMetadata.juzEnd(30).surah == 114)
    println("QuranMetadata self-tests passed")
}
