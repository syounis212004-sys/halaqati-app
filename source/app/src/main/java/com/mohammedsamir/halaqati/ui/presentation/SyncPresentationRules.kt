package com.mohammedsamir.halaqati.ui.presentation

/** Display copy only. The outbox path and operation are never changed. */
object SyncPresentationRules {
    private val operations = mapOf("POST" to "إضافة", "PATCH" to "تعديل", "DELETE" to "حذف")
    private val resources = mapOf(
        "attendance_records" to "الحضور", "recitation_entries" to "التسميع",
        "student_daily_notes" to "ملاحظات الطالب", "sessions" to "الجلسات",
        "smart_plans" to "الخطط الذكية", "smart_plan_events" to "سجل الخطة",
        "smart_plan_phases" to "مراحل الخطة", "smart_plan_phase_states" to "تقدم المراحل",
        "students" to "بيانات الطلاب", "user_devices" to "إعدادات الإشعارات",
        "monthly_comments" to "المتابعة الشهرية", "student_guardians" to "ربط ولي الأمر",
    )
    private val procedures = mapOf(
        "rc112_transition_plan_students" to "نقل الطلاب بين مراحل الخطة",
        "rc112_reconcile_plan_phase_states" to "تحديث حالات مراحل الخطة",
        "rc112_update_plan_phase" to "تعديل مرحلة الخطة",
        "rc113_bulk_configure_plan_cycles" to "إعداد مراحل خطط الطلاب",
        "rc23_get_or_create_session" to "تجهيز جلسة التسميع",
        "rc182_update_session_details" to "تعديل بيانات الجلسة",
        "set_student_primary_guardian" to "ربط ولي الأمر بالطالب",
        "rc24_issue_certificate" to "إصدار شهادة",
        "rc24_void_certificate" to "إلغاء شهادة",
    )

    fun title(method: String, path: String): String {
        val clean = path.substringBefore('?').substringBefore('#')
            .substringAfter("/rest/v1/", path.substringBefore('?').substringBefore('#'))
            .trim('/')
        if (clean.startsWith("rpc/")) return procedures[clean.removePrefix("rpc/")] ?: "عملية بيانات"
        val resource = resources[clean] ?: "بيانات التطبيق"
        return operations[method.uppercase()]?.let { "$it · $resource" } ?: resource
    }
}
