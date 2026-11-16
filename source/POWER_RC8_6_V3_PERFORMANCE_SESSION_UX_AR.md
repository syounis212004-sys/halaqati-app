# Halaqati 2.46 — RC8.6 V3 Performance + Session UX

هذه الحزمة تكمل RC8.6 فوق Startup Crash Hotfix V3 ولا تبدأ RC9.

## لماذا كان التطبيق يبدو بطيئًا رغم وجود Cache؟

1. مسار المزامنة اليدوية كان يفرغ الـOutbox ثم يطلق مصالحة حساب كاملة تضم الطلاب والجلسات والخطط والتقارير والترتيب والمتابعة وغيرها. تم فصل المسارين: رفع التغييرات الخفيفة للمستخدم أولاً، ثم مصالحة كاملة بالخلفية فقط عند الحاجة.
2. قراءة Cache كانت تستدعي WorkManager كثيرًا حتى عندما لا توجد عمليات معلقة. SyncWorker الآن لا يعمل إذا كان Outbox فارغًا.
3. حذف جلسة في الإصدارات السابقة أنشأ 4 عمليات. قاعدة Supabase الفعلية تستخدم ON DELETE CASCADE من attendance_records وrecitation_entries وstudent_daily_notes وnotifications إلى sessions؛ لذلك حذف الجلسة أصبح عملية واحدة. وتمت إضافة compact migration لعمليات الحذف القديمة المعلقة.
4. فتح Session Roster كان يحسب Smart Plan لكل الطلاب قبل اكتمال أول إطار. الآن الحضور/التسميعات/الملاحظات المحلية تظهر أولاً، ثم حساب الخطة والتحديث الشبكي في الخلفية.
5. بعد حفظ تسميع من الخطة أو التحفيظ السريع كان يحصل forceRefresh شبكي. الآن يعاد الحساب مباشرة من Room/Outbox ثم تتصالح الشبكة بالخلفية.

## سلوك المزامنة الجديد

- كل كتابة تحفظ محليًا أولاً وتدخل Outbox.
- إذا كانت الشبكة validated والتطبيق مفتوحًا، يطلب Outbox flush فورًا عبر ConnectivitySyncCoordinator بالإضافة إلى WorkManager كضمان دائم.
- Supabase mutations الخاصة بالـOutbox تستخدم return=minimal.
- Manual Sync ينتظر فقط رفع Outbox، وليس التقارير/الترتيب/كل بيانات الحساب.
- FullSyncWorker يعمل للمصالحة الخلفية ومجدول كل 15 دقيقة، والمصالحة الفورية throttled حسب آخر pull.
- عند رجوع الإنترنت يرتفع syncEpoch فتتحدث الشاشات من Cache دون Restart.

## Session / Recitation UX

- Session roster أصبح compact cards بدل واجهة كبيرة لكل طالب.
- حضور وسلوك بقوائم anchored واضحة.
- حالات غائب/بعذر/متأخر لها ألوان دلالية واضحة.
- أزرار حفظ/مراجعة/سرد/ملاحظة مباشرة لكل طالب.
- التسميع تحول إلى ModalBottomSheet حديثة فيها نوع النشاط، النطاق القرآني، الأخطاء، التلقين، تقييم V6، السلامة، والملاحظة.
- نطاقات QCF4 ومحرك القرآن الحالي لم يتم استبدالهما.

## Acceptance / Regression gates

- rc86-v3-performance-session-ux-check.py
- RC8 phase-scoped gates
- Behavioral parity 34/34
- Full release parity 48/48
- UI/UX Pro Max
- Kotlin self-tests

التحقق الكامل من Android Gradle/APK يبقى ضمن GitHub Actions: compileDebugKotlin ثم tests/lint/assemble.
