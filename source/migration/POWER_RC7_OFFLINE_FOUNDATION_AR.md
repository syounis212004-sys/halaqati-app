# Halaqati v2.46.0 POWER RC7 — Offline Foundation + Student Data

RC7 ينقل مصدر الحقيقة المحلي من SQLite helpers منفصلة إلى Room موحدة (`halaqati-rc7.db`) مع إبقاء Supabase هو مصدر الحقيقة السحابي وRLS كما هو.

## مسار البيانات

`Compose UI → Room → Durable Outbox → WorkManager → Supabase`

القراءة تبدأ من Room/الكاش المحلي لكل حساب. الكتابات الحرجة (الطالب، الجلسة، الحضور، الملاحظة، التسميع) تحفظ محليًا أولًا ثم تُرفع عبر Outbox. إعادة تشغيل التطبيق لا تمسح العمليات المعلقة.

## ما تم في RC7

- Room 2.8.5 مع جداول account-scoped للكاش، Outbox، الطلاب، وبيانات آخر مزامنة.
- استيراد أحادي وآمن لكاش وOutbox RC6 من `halaqati-cache.db` و`halaqati-offline.db` مع ترك القواعد القديمة دون حذف لسهولة الرجوع.
- Outbox دائم مع dedupe لكل حساب، attempts، blocked، conflict، server snapshot وforce-local.
- WorkManager دوري + one-shot عند كل تعديل، شرط CONNECTED، unique KEEP، وexponential backoff.
- Student local source-of-truth كامل: الهوية، الهاتف، الميلاد، الصف، الحلقة، الفئة، المسار، ولي الأمر، الحالة، الالتحاق، البرنامج، العنوان، الملاحظات والصورة المحفوظة.
- محرر الطالب لا يعرض UUID للحلقة أو الفئة؛ يستخدم قوائم أسماء حقيقية وDate Picker.
- إنشاء الطالب وتعديله وأرشفته يعمل Offline ويُعرض فورًا بعد الحفظ.
- جلسة اليوم/الحضور/التسميع/الملاحظات تبقى Local-first وتستخدم Outbox Room الجديدة.
- Pull-to-refresh في قائمة الطلاب مع forced pull عند توفر الإنترنت.
- Sync Center يعرض العمليات المعلقة والمحجوبة والتعارضات، ويتيح للمستخدم اختيار `اعتماد تعديلي` أو `نسخة الخادم`.
- تعارضات الطلاب تعتمد `updated_at`: لا يتم قلب أو حذف نسخة المستخدم تلقائيًا.
- عمليات student المتتابعة تُدمج في أحدث snapshot وتُرفع كـ upsert واحد لكل طالب، لتفادي تراكم PATCHات متعارضة.
- `SupabaseApi.raw` يرسل `resolution=merge-duplicates` تلقائيًا لطلبات Outbox التي تحتوي `on_conflict`.
- sessions/plans/master-data cache لا تنتهي صلاحيته عند انقطاع الشبكة؛ آخر snapshot معروف يبقى قابلًا للقراءة.

## Gate المقصود لـRC7

1. مزامنة أولية Online.
2. قطع الإنترنت وإغلاق التطبيق.
3. فتح التطبيق: الطلاب/الحلقات/الفئات/الجلسات المخزنة تظهر.
4. تعديل طالب وإنشاء جلسة وتسجيل حضور/تسميع.
5. إغلاق التطبيق وفتحه Offline: العمليات ما زالت موجودة.
6. إعادة الإنترنت: WorkManager يرفع Outbox بدون فقد وبـdedupe.
7. إذا تغيّر الطالب على الخادم منذ نسخة الأساس، العملية تتحول Conflict بدل overwrite صامت.

## ما لا يدّعيه RC7

Quran Range Engine الدقيق (QCF4/604/line fractions) هو RC8 حسب الخطة المعتمدة. RC7 لا يغيّر خوارزمية نطاق القرآن الحالية حتى لا يخلط Offline Foundation مع Quran Core.
