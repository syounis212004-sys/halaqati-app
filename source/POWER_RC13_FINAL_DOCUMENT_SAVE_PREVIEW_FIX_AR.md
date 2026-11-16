# RC13 — الإصلاح النهائي لمعاينة وحفظ الشهادات والتقارير

هذا الإصلاح لا يغيّر منطق Supabase أو الطلاب أو الجلسات أو الخطة الذكية أو محرك القرآن. نطاقه محصور في طبقة مستندات RC13 وفحوصها.

## السبب الحقيقي للمشكلتين

### 1) المعاينة كانت مقصوصة/مكبّرة على الهاتف
المعاينة السابقة اعتمدت على zoom/initial scale داخل Android WebView مع صفحة A4 بعرض ثابت يقارب 1122px. على بعض WebView/OEM لا يتم Fit-to-width كما نتوقع، فتظهر شريحة من الورقة بدل الورقة كاملة.

الحل المعتمد يعيد أسلوب v2.44: تبقى ورقة A4 بأبعادها الحقيقية، لكن نسخة المعاينة فقط تُلف بطبقة CSS/JS تحسب عرض الحاوية فعليًا وتطبّق transform: scale(...) صريح. ملف PDF نفسه لا يستعمل هذا التحويل، لذلك لا تتغير هندسة الطباعة.

### 2) PDF بحجم 0 بايت
المسار السابق كان يطلب CreateDocument قبل أن ينتهي Chromium من إنشاء PDF. هذا ينشئ ملف الوجهة/placeholder مبكرًا؛ وإذا فشل renderer أو الكتابة لاحقًا يبقى للمستخدم ملف 0 بايت.

الحل يعيد مسار v2.44 المستقر:
1. Chromium/WebView يطبع أولًا إلى ملف مؤقت seekable داخل مساحة التطبيق.
2. يتم التحقق من أن الملف يبدأ بـ %PDF- وأن حجمه حقيقي.
3. بعد النجاح فقط يتم إنشاء عنصر MediaStore Downloads بحالة IS_PENDING=1.
4. تُنسخ البايتات كاملة إلى الوجهة.
5. يُعاد فتح الملف المخزن والتحقق منه ومن حجمه.
6. عند النجاح فقط يتم نشره بإزالة IS_PENDING، وعند أي فشل يتم حذف العنصر بدل ترك ملف 0 بايت.

## المسارات النهائية
- الشهادات: Downloads/Halaqati/Certificates
- التقارير: Downloads/Halaqati/Reports

## الملفات المعدلة فقط
- app/src/main/java/com/mohammedsamir/halaqati/documents/HtmlDocumentPreview.kt
- app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt
- app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt
- scripts/rc13-document-reports-check.py

## حمايات Regression
الفحص يمنع عودة WebView zoom/initialScale لمسار المعاينة، ويطلب وجود fit transform الصريح، ويطلب direct save إلى MediaStore بعد اكتمال temp PDF والتحقق منه، ويمنع عودة launchers القديمة التي تنشئ PDF placeholder قبل نجاح التصيير.
