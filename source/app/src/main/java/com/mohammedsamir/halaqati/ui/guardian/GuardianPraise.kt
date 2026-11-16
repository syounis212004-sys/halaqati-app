package com.mohammedsamir.halaqati.ui.guardian

/** RC27.28: pure praise lines for the guardian home (first line = headline). Empty when there is nothing to celebrate. */
object GuardianPraise {
    fun lines(o: GuardianOverview): List<String> {
        val out = ArrayList<String>()
        if (o.planComplete) out += "أتمّ ${o.childName} خطة هذا الشهر 🎉"
        val q = o.quality
        if (q != null && q >= 90.0 && o.monthMain > 0.0) out += "جودة تسميع ممتازة (${kotlin.math.round(q).toLong()}٪)"
        if (o.monthSessions >= 4 && o.monthPresent == o.monthSessions) out += "حضور كامل هذا الشهر"
        if (o.todayRecited && o.todayPages > 0.0) out += "سمّع اليوم"
        return out
    }
}
