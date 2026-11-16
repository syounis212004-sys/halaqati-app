# RC13 Runtime Documents One‑Shot Fix

هذا التحديث محصور في طبقة الشهادات/التقارير والإخراج والمعاينة. لا يغيّر منطق الطلاب أو الجلسات أو الخطة الذكية أو Supabase أو الصلاحيات.

## المشكلات التي عولجت

1. **PDF تقرير بحجم 0 بايت**
   - لم يعد `PrintDocumentAdapter` يكتب مباشرة إلى `content://` القادم من `ACTION_CREATE_DOCUMENT`.
   - يتم إنشاء PDF أولًا في ملف مؤقت محلي قابل للـseek، والتحقق من `%PDF-` ومن حجم فعلي أكبر من 1KB، ثم نسخ الملف المكتمل إلى URI المختار مع truncate semantics والتحقق من الناتج بعد الحفظ.

2. **المعاينة مقصوصة أو غير متمركزة على الهاتف**
   - أزيل استخدام `setInitialScale(70..120)` كقياس مطلق لصفحة بعرض 1122px.
   - المعاينة تستخدم WebView overview/fit-to-width أولًا، ثم تطبق zoom نسبيًا على الصفحة الملائمة للشاشة.

3. **تداخل نصوص الشهادة**
   - أزيل wrapper الذي غيّر تدفق عناصر v2.44.
   - خُصصت مناطق مستقلة وغير متداخلة للتواريخ والتوقيعات والـfooter.
   - أضيف preflight يفحص تداخل عناصر التدفق وعناصر الأسفل قبل السماح بالتصدير.
   - في الإعدادات القصوى/الأسماء الطويلة، يحافظ المحرك على النص المقروء ويقلص الشعار تلقائيًا فقط عندما يكون الجمع بين المقاسات المختارة مستحيلًا داخل A4؛ الشعار يبقى بالحجم المطلوب عندما توجد مساحة كافية.

4. **بوابة ضغط حقيقية**
   - Fixture عادي + Fixture ضغط باسم طويل ونص طويل وبأقصى أحجام title/student/body/logo.
   - كلاهما يجب أن يخرج صفحة A4 أفقية واحدة بلا overlap، مع نص عربي selectable.

## الملفات المعدلة مقارنة بـ BUILD FIX5

- `app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt`
- `app/src/main/java/com/mohammedsamir/halaqati/documents/HtmlDocumentPreview.kt`
- `app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt`
- `app/src/main/assets/documents/certificate.css`
- `scripts/rc13-render-fixtures.py`
- `scripts/rc13-document-reports-check.py`

لا توجد تغييرات في قاعدة البيانات أو migration أو repository business rules أو Smart Plan.
