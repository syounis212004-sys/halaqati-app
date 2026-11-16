# Halaqati 2.46 — RC9.1 Smart Plan Core

هذه الحزمة تبدأ RC9 فوق RC8.6 V3 المستقرة. نطاقها متعمد ومحدود: **توحيد حالة الخطة وحسابات الفترة/الأسبوع والـAnchor في مصدر واحد قابل للاختبار**، بدون إدخال Adaptive Catch-up/Surplus/Teacher Override الخاصة بـRC9.2 وبدون إعادة تصميم واجهة RC9.3.

## 1) SmartPlanCoreStateEngine — مصدر حقيقة واحد
أضيف `SmartPlanCoreStateEngine` كطبقة Domain خالصة (بدون Android) فوق `SmartPlanEngine`، وتنتج State واحدة غير قابلة للتغيير تحتوي:
- هدف الخطة.
- المطلوب حتى اليوم.
- المنجز.
- المتبقي.
- مقدار التقدم/التأخر عن الجدول.
- دين الجدول الحالي (`scheduleDebt`) من deficit الموجود في المحرك.
- نسبة الإنجاز.
- حالة الأسبوع.
- آخر Anchor حفظ ناجح.
- المقرر الحالي/التالي الفعلي بعد إثراء Repository.
- Status موحد: `NOT_STARTED / ON_TRACK / AHEAD / BEHIND / COMPLETE`.

**سبب التعديل البنيوي:** كانت نفس الحقيقة موزعة بين `Progress` و`PlanSnapshot` وحسابات UI/Repository. RC9.1 يجعل الشاشات اللاحقة تعتمد على State واحدة بدل إعادة تفسير الأرقام كل مرة.

## 2) WeeklyProgress داخل SmartPlanEngine
أضيفت حالة أسبوعية canonical من الأحد إلى السبت، وتحسب فقط أيام الخطة الفعلية:
- `target`
- `requiredByToday`
- `completed`
- `remaining`
- `aheadBy / behindBy`
- عدد أيام الخطة في الأسبوع، المنقضي منها، والمتبقي.

تم استخراج `cumulativePlannedAmount()` كمصدر موحد لحساب المطلوب التراكمي؛ حساب اليوم والأسبوع يستخدمان نفس القاعدة حتى لا تختلف الأرقام بين بطاقة وأخرى.

إذا كان للطالب `target_pages_weekly` مخصص، فإن `SmartPlanCoreStateEngine` يستخدمه كهدف أسبوعي authoritative ويعمل prorating فقط إذا كانت الخطة تبدأ/تنتهي في منتصف الأسبوع. لا تتم محاسبة الطالب على أيام قبل بداية الخطة أو بعدها.

## 3) Anchor الحفظ مستقل
`lastMemorizationAnchor` لا يقبل إلا `new_hifz/hifz` الناجح حتى تاريخ الحالة. السرد/الاختبار/المراجعة لا يمكن أن يحرك Anchor الحفظ، والتسميع الفاشل لا يصبح Anchor.

هذه الطبقة لا تغيّر QuranRangeEngine ولا قواعد الاتجاه في RC8.6 V3.

## 4) Repository integration
`PlanSnapshot` أصبح يحمل `coreState` جاهزة بعد أن ينتهي Repository من:
1. حساب `SmartPlanEngine.Progress`.
2. توليد Assignment الحقيقي من Quran Anchor/QCF4.
3. اختيار Weekly target الخاص بالطالب إن وجد.
4. بناء `SmartPlanCoreStateEngine.State` النهائية.

بهذا ترى كل واجهة لاحقًا نفس الحالة التي أنتجها Repository، ولا تحتاج لإعادة حساب الخطة داخل Compose.

## 5) ماذا يحدث بعد كل تحفيظ في RC9.1؟
مسار الحفظ الموجود في RC8.6 V3 يبقى Local-first/Outbox. بعد حفظ التسميع وإبطال `planSnapshotMemory`، إعادة قراءة الخطة تبني تلقائيًا:
- الإنجاز الجديد.
- المطلوب حتى اليوم.
- المتبقي.
- تقدم/تأخر الجدول.
- حالة الأسبوع.
- Anchor الحفظ الجديد إذا كان التسميع ناجحًا ومن نوع حفظ.
- Assignment التالي من المحرك القرآني الحالي.

لا توجد DB migration في RC9.1 ولا جدول Snapshot جديد؛ البيانات المشتقة تبقى مشتقة من السجلات الأصلية. هذا يحافظ على Supabase/RLS الحالي ويجنب ازدواج مصادر الحقيقة.

## 6) ما لم يدخل RC9.1 عمدًا
- `PLAN_COMPLETE_EARLY` وSurplus بعد إكمال الهدف: RC9.2.
- Adaptive catch-up الجديد: RC9.2 (الموجود حاليًا في RC8 يبقى دون تغيير).
- Teacher Override الجديد وسبب القرار: RC9.2.
- واجهة Smart Plan الجديدة وSnapshot persistence الأوسع: RC9.3.

RC9.1 يستخدم `COMPLETE` عند بلوغ هدف الخطة حتى لا يظهر متبقي سالب، لكنه لا يبدأ وضع الإنجاز الإضافي قبل RC9.2.

## 7) Regression / Acceptance
- `SmartPlanCoreStateTest`
- `SmartPlanCoreStateSelfTest`
- `SmartPlanEngineTest` لحالة الأسبوع Sunday→Saturday.
- `scripts/rc91-smart-plan-core-check.py`
- كل RC8 gates القديمة تبقى إلزامية.

سيناريوهات RC9.1 المغطاة:
- هدف أسبوعي 40 صفحة => 40 في أسبوع كامل من 5 أيام.
- المطلوب حتى الثلاثاء = 3/5 من الهدف الأسبوعي.
- سرد أحدث من الحفظ لا يحرك Anchor الحفظ.
- تسميع حفظ فاشل لا يحرك Anchor.
- إكمال الهدف => remaining=0 وstatus=COMPLETE ولا Assignment وهمي.
