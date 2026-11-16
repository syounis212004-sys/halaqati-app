# RC12.2 FIX1 Build Fix 2

هذا الإصلاح يعالج خطأ Kotlin ظهر في GitHub Actions run `98413756200` بعد نجاح إصلاح تعارض `Paint.style` السابق.

## السبب
داخل الدالة المحلية `dateCard` كان هناك معامل باسم `color: Int`. داخل `Paint.apply { ... }` استُخدم السطر غير المؤهل `color = ...`، فقام Kotlin بحل الاسم إلى معامل الدالة غير القابل لإعادة التعيين بدل خاصية `Paint.color`، فظهر الخطأ:

`ReportsAndTools.kt:2561:91 'val' cannot be reassigned`

## الإصلاح
- تغيير اسم معامل الدالة من `color` إلى `textColor` لمنع shadowing.
- تأهيل خاصية الطلاء صراحة بـ `this.color = ...`.
- إضافة regression guards إلى `rc12-2-certificates-studio-check.py` لمنع عودة نفس النمط.
- لا تغيير في تصميم الشهادة أو Supabase أو Smart Plan أو بيانات الأرشيف أو versionCode/versionName؛ الإصدار الداخلي يبقى `2.47.2-RC12.2-FIX1` / `24721` لأن النسخة السابقة لم تتجاوز Kotlin compile.
