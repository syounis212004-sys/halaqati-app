> Current release: **2.49.0-RC14 — Guardian + Management & Document Reliability**

# Halaqati Native Android 2.46.3-RC11.3

هذه الحزمة هي انتقال Native من Halaqati v2.44.0 (Vite + Capacitor) إلى Kotlin + Jetpack Compose، مع الحفاظ على `applicationId = com.mohammedsamir.halaqati` وSupabase production contract الحالي.

## خصائص البنية
- Runtime Android أصلي: لا Capacitor ولا WebView.
- Jetpack Compose + Material 3 + RTL.
- Supabase Auth/REST/RPC/Edge Functions عبر HTTPS بنفس RLS والجداول الحالية.
- RC7 Offline-first: Room 2.8.5 + durable account-scoped Outbox + WorkManager؛ القراءة Local-first والكتابة الحرجة تحفظ على الجهاز قبل Supabase.
- RC8 Quran Core: `QuranRangeEngine` موحد + QCF4 page/line metadata مثبت بالـSHA + حساب أجزاء الصفحات واتجاه الحفظ/السرد بدون `abs/min/max` لتغيير معنى النطاق.
- Firebase Cloud Messaging بنفس user_devices/notifications contract.
- Native PDF للتقارير والشهادات، بدون Chromium/WebView.
- versionCode 24630 لكي يكون أعلى من RC11.1 / 24613 ومن 2.44.0 / 24400.
- package id والتوقيع يجب أن يبقيا نفسيهما لترقية APK فوق النسخة القديمة.

## Build configuration
القيم الافتراضية تتضمن Supabase URL والمفتاح publishable الموجود أصلًا في v2.44.0. يمكن تجاوزها بـ Gradle properties أو GitHub Secrets: `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`.
Firebase يحتاج `app/google-services.json`، وWorkflow المرفق ينشئه من السر الحالي.

## ملاحظة نزاهة الترحيل
الحزمة تحتوي `migration/parity-matrix.md` و`migration/source-contract.json` لتدقيق كل ميزة. لا تتضمن WebView fallback. احتُفظ بالمصدر الأصلي فقط كمرجع في `migration/legacy-reference` ولا يدخل في APK.

## Gradle on GitHub
The supplied workflow installs Gradle 8.13 with `gradle/actions/setup-gradle`, so the source ZIP intentionally does not depend on a binary `gradle-wrapper.jar`. This keeps the delivered source auditable and avoids embedding an unverified binary wrapper.

## Release hardening
- Auth callback is matched exactly at `com.mohammedsamir.halaqati://auth/callback` and MainActivity uses `singleTop` for safe deep-link reuse.
- Offline state is account-scoped in Room؛ RC6 cache/outbox are imported once without deleting the old databases, and unscoped legacy rows are never replayed.
- Static release gates include manifest/resource validation and offline account-isolation migration simulation before Gradle.


## Full parity gate (RC2)
- `scripts/full-release-parity-check.py`: 48/48 tracked v2.44 feature surfaces required before CI.
- `scripts/release-parity-contract-check.py`: 9/9 highest-risk parity contracts.
- `scripts/ui244-restoration-check.py` + `scripts/uiux-pro-max-check.py`: visual/navigation guardrails.
- Google sign-in is native Credential Manager first, with an in-app Partial Custom Tab OAuth fallback for devices/configurations that return no credentials.


## POWER RC7 — Offline Foundation
- `migration/POWER_RC7_OFFLINE_FOUNDATION_AR.md` يوثق عقد RC7 واختبار القطع الكامل للشبكة.
- `scripts/rc7-offline-contract-check.py` و`rc7-durability-check.py` بوابات إلزامية قبل Gradle.
- Student editor يستخدم Date Picker وقوائم الحلقة/الفئة بالأسماء، ولا يعرض UUID للمستخدم.
- Sync Center يدعم تعارض `updated_at` بقرار صريح بين النسخة المحلية ونسخة الخادم.


## POWER RC8 — Quran Core + Recitation Engine
- `migration/POWER_RC8_QURAN_CORE_AR.md` يوثق قواعد الاتجاه والحساب والتخزين.
- `scripts/rc8-integration-check.py` + `QuranRangeEngineSelfTest` بوابات إلزامية قبل Gradle.
- مصدر QCF4 مثبت على commit/blob محددين؛ build يرفض أي محتوى مختلف.
- لا توجد DB migration في RC8؛ schema الحالية لـ `recitation_entries` تستوعب endpoints/selection_data/QCF line keys.
- إشعار ترخيص البيانات: `migration/THIRD_PARTY_NOTICES_RC8.md`.

## RC8.3 Smart Plan + Quran UX Parity
راجع `POWER_RC8_3_SMART_PLAN_QURAN_UX_AR.md`. هذه المرحلة لا تبدأ RC9؛ تغلق عرض الآيات/السور، اختيار الآية داخل الصفحة، اتجاه RTL، وفلاتر الطلاب المدمجة.

## RC8.4 Direction + Offline + Rankings UX
راجع `POWER_RC8_4_DIRECTION_OFFLINE_UX_AR.md`. الحفظ العام افتراضيًا من الناس إلى البقرة بين السور مع آيات تصاعدية داخل السورة، والتثبيت افتراضيًا بترتيب المصحف مع خيار تغيير الاتجاه. كما تضيف المرحلة fallback محليًا لأخطاء DNS، warming لبيانات الشاشات الأساسية، وقوائم ترتيب/فترة مدمجة بدل Chips الكبيرة.

## RC8.5 Multi-Activity Smart Plan + Performance
راجع `POWER_RC8_5_MULTI_ACTIVITY_SMART_PLAN_AR.md`. تضيف هذه المرحلة Anchors مستقلة للحفظ والسرد داخل نفس الخطة، توليد المقرر من آخر نشاط فعلي من نفس النوع، Cache-first لفتح الخطط والجلسات، فلترة سور الجزء داخل Quran Picker، وإلغاء/أرشفة/حذف الجلسات عبر Outbox دون اشتراط الاتصال.

## RC8.6 Cache Sync + Smart Plan Logic + Compact UX
راجع `POWER_RC8_6_CACHE_SYNC_SMART_PLAN_UX_AR.md`. تضيف المرحلة Stale‑While‑Revalidate فعليًا للخطط والجلسات والحسابات، مزامنة فورية عند عودة الشبكة عبر Android NetworkCallback، تصحيح مقدار المقرر من Anchor آخر نشاط ناجح بدل نطاق آية واحدة، انتقال مباشر لطالب «يحتاج متابعة»، وواجهة Figma/Compose أكثر كثافة للإحصائيات والخطة الذكية مع Cairo Medium للنص الأساسي.

## RC9.1 Smart Plan Core
راجع `POWER_RC9_1_SMART_PLAN_CORE_AR.md`. تبدأ هذه المرحلة RC9 بإضافة `SmartPlanCoreStateEngine` كمصدر حقيقة واحد للمطلوب/المنجز/المتبقي/التقدم/التأخر وحالة الأسبوع وAnchor الحفظ والمقرر، مع إبقاء Quran Core وSupabase schema وOffline foundation دون تغيير. لا تدخل Adaptive Catch-up/Surplus/Teacher Override الخاصة بـRC9.2 ولا إعادة تصميم RC9.3.

## RC9.3 Final Stabilization + Premium Smart Plan UX
راجع `POWER_RC9_3_FINAL_STABILIZATION_AR.md`. تضيف هذه المرحلة حالة الإكمال المبكر والإنجاز الإضافي، واجهة Smart Plan تفاعلية محسنة، بطاقات جلسة مضغوطة قابلة للتوسع، Pull-to-Refresh، حذف تسميع مع Undo، وتوحيد Plan-history cache مع Session cache/Outbox بدون DB migration.

### RC9.3 FIX3 Runtime & UX Stabilization
راجع `POWER_RC9_3_FIX3_RUNTIME_UX_STABILIZATION_AR.md`. يصلح الإقلاع البطيء عبر Cache-first، اعتماد الـMilestone والمزامنة المحلية، تحديث غياب Dashboard من البيانات المحلية، ويضغط فلاتر Smart Plan مع استعادة تصنيفات ممتاز/جيد جدًا/جيد/يحتاج متابعة وفصلها عن حالة التقدم.

## RC10.2 + RC10.3 Student Intelligence & Follow-up
راجع `POWER_RC10_2_10_3_STUDENT_INTELLIGENCE_AR.md`. يثبت عقد `students` مع Supabase الحقيقية، يضيف اختيار ولي الأمر بالبحث عبر `student_guardians`، ملف طالب كامل، مقارنة أسبوعية/شهرية، جودة وحضور وأسباب متابعة، وشاشة Follow-up مستقلة مع فتح مباشر لملف الطالب.

## RC11 — Smart Plan Phases & Sard Cycle
راجع `POWER_RC11_SMART_PLAN_PHASES_AR.md`. يحول الخطة الشهرية إلى مراحل حفظ/سرد مع حالة مستقلة لكل طالب، تجميد نتيجة الحفظ، Sard Anchor مستقل، توزيع مرن حقيقي للمقرر، أهداف صفحات/أجزاء/نطاق/مفتوحة، انتقال جماعي مع Preview واستثناءات، ترحيل نقص اختياري فقط، Timeline، فصل الحفظ والسرد في صفحة الطالب والتقرير، وإشعارات ولي الأمر. Migration الإضافية: `migration/20260926_v2461_rc11_smart_plan_phases.sql`.


## RC11.1 — Phase UX Fix
راجع `migration/POWER_RC11_1_PHASE_UX_FIX2_AR.md`. حسّن شاشة المراحل والانتقال الجماعي، Date Picker، أسماء الأيام الكاملة، البحث عن الحلقة/الطالب، هدف السرد الافتراضي 25 صفحة، Sard Anchor المرئي، ومعاينة نطاق السرد قبل الحفظ.

## RC11.2 — Core Phase Consistency
راجع `POWER_RC11_2_CORE_PHASE_CONSISTENCY_AR.md`. يجعل المراحل المصدر التنفيذي للخطة بعد اعتمادها، يحفظ الإنجاز التاريخي عند تعديل التواريخ، يضيف Lifecycle reconciliation، يمنع التداخل وتعدد مراحل النوع نفسه، يوحّد Sard Anchor، يجعل اتجاه سرد الحفظ العام افتراضيًا من الناس إلى البقرة مع إمكانية تغييره، ويعيد بناء أسبوع الطالب من الجلسات/الحضور/التسميع. كما يضيف انتقالًا جماعيًا ذريًا، optimistic revision conflicts، backfill آمن لـ`phase_id`، وتقارير phase-aware. Migration الإضافية: `migration/20260926_v2462_rc11_2_phase_consistency.sql` وتُشغّل بعد Migration RC11 الأصلية.
