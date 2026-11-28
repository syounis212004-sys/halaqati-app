package com.mohammedsamir.halaqati.ui.guardian

import java.util.Locale

/**
 * RC27.25: the monthly report of one child as a plain message a guardian can send on WhatsApp (bold with *...*).
 * Pure function over [GuardianOverview]: no network, no Android classes, so it can be shared from any screen.
 */
object GuardianReportText {
    private val MONTHS = listOf(
        "كانون الثاني", "شباط", "آذار", "نيسان", "أيار", "حزيران",
        "تموز", "آب", "أيلول", "تشرين الأول", "تشرين الثاني", "كانون الأول",
    )

    fun monthName(month: Int): String = MONTHS.getOrElse((month - 1).coerceIn(0, 11)) { "" }

    private fun num(v: Double): String {
        val r = kotlin.math.round(v * 100) / 100
        return if (kotlin.math.abs(r - r.toLong()) < 1e-6) r.toLong().toString()
        else String.format(Locale.US, "%.2f", r).trimEnd('0').trimEnd('.')
    }

    fun build(o: GuardianOverview, month: Int, year: Int): String = buildString {
        append("*تقرير شهر ${monthName(month)} $year — ${o.childName}*\n")
        val head = listOf(o.trackLabel, o.subtitle).filter { it.isNotBlank() }.joinToString(" · ")
        if (head.isNotBlank()) append(head).append('\n')
        append('\n')
        append("• الحضور: ${o.monthPresent} من ${o.monthSessions} جلسة\n")
        if (!o.program) append("• ${o.monthMainLabel}: ${num(o.monthMain)} صفحة\n")
        o.quality?.let { append("• جودة التسميع: ${num(it)}٪\n") }
        val targeted = o.planActivities.filter { it.hasTarget }
        if (targeted.isNotEmpty()) {
            append('\n')
            append("*خطة الشهر${o.planTitle?.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""}*\n")
            targeted.forEach { a ->
                append("• ${a.label}: ${num(a.done)} / ${num(a.target)} ص (${num(a.percent)}٪)")
                append(if (a.complete) " — أُنجزت ✅" else " — المتبقي ${num(a.remaining)} ص")
                append('\n')
            }
        }
        val praise = GuardianPraise.lines(o).filter { it != "سمّع اليوم" }
        if (praise.isNotEmpty()) {
            append('\n')
            praise.forEach { append("✨ ").append(it).append('\n') }
        }
        val latest = o.latest.take(3)
        if (latest.isNotEmpty()) {
            append('\n')
            append("*آخر التسميعات*\n")
            latest.forEach { l ->
                append("• ${l.date} — ${l.activityLabel}")
                if (l.range.isNotBlank()) append(": ${l.range}")
                append(" (${num(l.pages)} ص)\n")
            }
        }
        append('\n')
        append("جزاكم الله خيرًا على متابعتكم.\n— حلقاتي")
    }
}
