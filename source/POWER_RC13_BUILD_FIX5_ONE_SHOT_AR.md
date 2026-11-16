# RC13 BUILD FIX5 ONE-SHOT

هذا الإصلاح خاص بالبناء فقط ولا يغيّر سلوك التطبيق أو تصميم الشهادات أو التقارير أو قاعدة البيانات.

## سبب الفشل
Kotlin 2.2.21 رفض `ReportHtmlRenderer.build` لأنه كان public افتراضياً بينما يستقبل `NativeReportStyle` وهو internal.

## الإصلاح
- جعل `ReportHtmlRenderer.build` دالة `internal` لأنها تُستخدم داخل نفس Gradle module فقط.
- إضافة حارس إلى `compile-sanity-check.py` يمنع رجوع نفس تعارض visibility قبل الوصول إلى Gradle.
- لا تغيير في CSS أو HTML أو Supabase أو Smart Plan أو PDF layout.
