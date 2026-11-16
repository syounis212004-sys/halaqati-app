# Halaqati 2.46 Stable — RC9 → RC15 closure

هذه النسخة مبنية تراكميًا فوق RC7 وRC8، ولا تعيد أي طبقة نجحت سابقًا.

## RC9 — Smart Plan POWER
- بطاقات المطلوب/المنجز/المتبقي/التعويض/الأسبوع/آخر تسميع/المقرر القادم وسبب المقرر موجودة في المحرك والواجهة.
- حالات الخطة أخضر/أصفر/أحمر، Student Override، بوابة الجودة، التمديد، الاختبارات المرحلية وتنبيهات التأخر محفوظة.
- نطاقات القرآن الدقيقة تنتقل عبر `memorization_line_keys` و`QuranRangeEngine` ولا يُعاد قلب البداية والنهاية رقميًا.

## RC10 — Live Session + Quick Memorization
- جلسة اليوم والتحفيظ السريع والحضور/الغياب/الأخطاء/التلقين/التسميع المحلي والطالب التالي تعمل Offline-first.
- أضيف Undo فوري آمن: يلغي زوج الحضور/التسميع فقط إذا كان كامل الحفظ ما زال Pending في Outbox للحساب نفسه؛ إذا بدأت المزامنة لا يحذف جزءًا من العملية ويطلب التعديل من نموذج الجلسة الكامل.

## RC11 — Dashboard + Students
- لوحة اليوم، ابدأ جلسة اليوم، التحفيظ السريع، لم يسمّع، غائب، متأخر، يحتاج مراجعة وتجاوز الهدف موجودة مع بطاقات الطلاب وRTL.

## RC12 — Certificates Studio
- المعاينة وPDF والطباعة تعتمد محرّك الرسم نفسه ونسبة A4 مع القوالب والشعارات والتوقيعات والألوان والأرشفة والمشاركة.

## RC13 — Report Studio
- تم إصلاح أصل مشكلة Scroll: كل الاستوديو والمعاينة والفلاتر والنتائج أصبحت داخل `ReportStudioScrollableSurface` واحد بدل Column ثابت + LazyColumn منفصل.
- أضيف اختبار Compose آلي `ReportStudioScrollTest` ينفذ Swipe حتى `report_studio_last_option`.
- التصدير PDF / Excel / Word / PNG والمشاركة والتخصيص باقية دون إزالة.

## RC14 — Guardian + Management
- ولي الأمر، الإدارة، الصلاحيات، الفئات، الحلقات، الإنجازات، المنافسة، الإعلانات، المنتدى، الإشعارات، الاستيراد/التصدير والأرشيف تبقى ضمن بوابة Full Parity الحالية.

## RC15 — Performance + UI/UX
- Cairo / RTL / ألوان حلقتي / Compose Native / StateFlow / مفاتيح Lazy ثابتة تبقى كما في RC7/RC8.
- أضيف `baseline-prof.txt` للمسارات الساخنة.
- أضيف module مستقل `:benchmark` وفيه `StartupBenchmark` باستخدام AndroidX Macrobenchmark لقياس Cold Startup دون الاعتماد على حالة تسجيل الدخول.
- التطبيق `profileable` للـshell فقط لتشغيل أدوات الأداء، وليس debuggable في Release.

## بوابة 2.46 Stable
الـStable لا يعتمد على فرق الصفحات الساذج. يبقى QCF4 604 صفحة / 15 سطرًا هو المصدر المحلي لحساب كسور الصفحات. المساران فقط: `general_hifz` و`tathbit`، والسرد Activity.

التحقق المحلي المتاح يشمل كل source-contract checks وKotlin self-tests. Android compile/lint/APK وAndroid instrumentation/Macrobenchmark تحتاج Android SDK/device، لذلك ينفذها Workflow الـStable بمهل زمنية محددة ولا يسمح بإخراج Release artifact قبل نجاح البوابات المحددة للـRelease.
