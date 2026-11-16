package com.mohammedsamir.halaqati.quran

import android.content.Context

object QuranCore {
    @Volatile private var engineRef: QuranRangeEngine? = null

    fun init(context: Context) {
        if (engineRef != null) return
        synchronized(this) {
            if (engineRef == null) {
                val bytes = context.applicationContext.assets.open("quran/qcf4-layout-v2.bin").use { it.readBytes() }
                engineRef = QuranRangeEngine(PackedQcf4LineLayout(bytes))
            }
        }
    }

    fun engine(): QuranRangeEngine = engineRef
        ?: error("QuranCore غير مهيأ. يجب استدعاء QuranCore.init في Application قبل استخدام محرك القرآن.")
}
