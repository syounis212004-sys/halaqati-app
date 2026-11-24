package com.mohammedsamir.halaqati.plans

/** Arabic text for the server's plan-phase validation errors (same wording as the web's NX error mapping). */
object PlanErrorRules {
    fun friendly(raw: String?): String {
        val m = raw.orEmpty()
        return when {
            m.contains("smart plan phases may not overlap", true) ->
                "تتداخل تواريخ مرحلتَي الحفظ والسرد لأحد الطلاب. اجعل بداية السرد بعد نهاية الحفظ ثم أعد المحاولة."
            m.contains("Sard must start after Hifz", true) -> "يجب أن تبدأ مرحلة السرد بعد نهاية مرحلة الحفظ."
            m.contains("invalid Hifz date range", true) -> "تواريخ مرحلة الحفظ غير صحيحة: النهاية قبل البداية."
            m.contains("invalid Sard date range", true) -> "تواريخ مرحلة السرد غير صحيحة: النهاية قبل البداية."
            m.isBlank() -> "تعذر تنفيذ العملية"
            else -> m
        }
    }
}
