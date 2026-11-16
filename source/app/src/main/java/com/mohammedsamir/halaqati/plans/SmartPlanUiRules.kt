package com.mohammedsamir.halaqati.plans

/** RC9.3 Android-free visual semantics for Smart Plan status chips/cards. */
object SmartPlanUiRules {
    enum class Tone { NEUTRAL, PRIMARY, SUCCESS, WARNING }

    data class Visual(
        val label: String,
        val tone: Tone,
        val supportingText: String = "",
    )

    data class PerformanceBand(
        val label: String,
        val tone: Tone,
    )

    fun schedulePerformancePercent(completed: Double, requiredByToday: Double): Double {
        if (requiredByToday <= 0.001) return 100.0
        return (completed.coerceAtLeast(0.0) / requiredByToday * 100.0).coerceAtLeast(0.0)
    }

    fun performanceBand(percent: Double): PerformanceBand = when {
        percent > 110.0 -> PerformanceBand("متقدم", Tone.PRIMARY)
        percent >= 90.0 -> PerformanceBand("على المسار", Tone.SUCCESS)
        percent >= 75.0 -> PerformanceBand("يحتاج انتباه", Tone.WARNING)
        else -> PerformanceBand("يحتاج متابعة", Tone.WARNING)
    }

    fun visualFor(status: SmartPlanCoreStateEngine.StatusCode, surplusPages: Double = 0.0): Visual = when (status) {
        SmartPlanCoreStateEngine.StatusCode.NOT_STARTED -> Visual("لم تبدأ", Tone.NEUTRAL)
        SmartPlanCoreStateEngine.StatusCode.ON_TRACK -> Visual("على الخطة", Tone.PRIMARY)
        SmartPlanCoreStateEngine.StatusCode.AHEAD -> Visual("متقدم", Tone.SUCCESS)
        SmartPlanCoreStateEngine.StatusCode.BEHIND -> Visual("يحتاج تعويض", Tone.WARNING)
        SmartPlanCoreStateEngine.StatusCode.COMPLETE_EARLY -> Visual(
            "أتم الخطة مبكرًا",
            Tone.SUCCESS,
            if (surplusPages > 0.001) "+${plain(surplusPages)} ص إضافية" else "الهدف الشهري مكتمل",
        )
        SmartPlanCoreStateEngine.StatusCode.COMPLETE -> Visual(
            "الخطة مكتملة",
            Tone.SUCCESS,
            if (surplusPages > 0.001) "+${plain(surplusPages)} ص إضافية" else "تم تحقيق الهدف",
        )
    }

    private fun plain(value: Double): String {
        val rounded = kotlin.math.round(value * 100.0) / 100.0
        return if (kotlin.math.abs(rounded - rounded.toInt()) < 0.000001) rounded.toInt().toString()
        else rounded.toString().trimEnd('0').trimEnd('.')
    }
}
