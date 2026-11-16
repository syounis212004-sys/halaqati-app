package com.mohammedsamir.halaqati

import android.app.Application
import com.mohammedsamir.halaqati.data.AppGraph
import com.mohammedsamir.halaqati.push.NotificationHelper
import com.mohammedsamir.halaqati.quran.QuranCore

class HalaqatiApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        QuranCore.init(this)
        // RC27.7: hifz plans count the continuous stretch from the first to the last ayah recited in the month.
        com.mohammedsamir.halaqati.plans.SmartPlanEngine.spanLineCounter = { from, to, route ->
            runCatching {
                QuranCore.engine().calculate(
                    com.mohammedsamir.halaqati.quran.QuranRangeRequest(
                        track = "general_hifz",
                        activityType = "new_hifz",
                        selection = com.mohammedsamir.halaqati.quran.QuranSelection.Ayahs(from, to),
                        direction = if (route == "reverse_surahs_forward_ayahs")
                            com.mohammedsamir.halaqati.quran.QuranTraversalDirection.REVERSE_SURAH_ORDER
                        else com.mohammedsamir.halaqati.quran.QuranTraversalDirection.FORWARD_QURAN,
                    ),
                ).qcfLineKeys.size
            }.getOrNull()
        }
        com.mohammedsamir.halaqati.plans.SmartPlanEngine.spanLineKeyProvider = { from, to, route ->
            runCatching {
                QuranCore.engine().calculate(
                    com.mohammedsamir.halaqati.quran.QuranRangeRequest(
                        track = "general_hifz",
                        activityType = "new_hifz",
                        selection = com.mohammedsamir.halaqati.quran.QuranSelection.Ayahs(from, to),
                        direction = if (route == "reverse_surahs_forward_ayahs")
                            com.mohammedsamir.halaqati.quran.QuranTraversalDirection.REVERSE_SURAH_ORDER
                        else com.mohammedsamir.halaqati.quran.QuranTraversalDirection.FORWARD_QURAN,
                    ),
                ).qcfLineKeys
            }.getOrNull()
        }
        AppGraph.init(this)
        NotificationHelper.ensureChannel(this)
    }
}
