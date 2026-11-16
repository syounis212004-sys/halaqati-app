# Halaqati 2.46 — RC9.3 Final Stabilization + Premium Smart Plan UX

RC9.3 هي مرحلة تثبيت RC9 النهائية فوق RC9.1 وRC9.2. لا تغيّر QuranRangeEngine/QCF4 ولا المسارين ولا Supabase schema؛ تركيزها هو صحة حالة الخطة بعد التعديلات التاريخية، Offline/cache consistency، وتحويل Smart Plan والجلسة إلى واجهة عربية أسرع وأكثر كثافة ووضوحًا.

## 1) COMPLETE_EARLY + Surplus كحالة Canonical
أضيفت حالة `COMPLETE_EARLY` إلى `SmartPlanCoreStateEngine` مع:
- `surplusPages`: الإنجاز فوق هدف الخطة، بدون متبقي سالب.
- `completedEarly`: هل تحقق الهدف قبل تاريخ نهاية الخطة.
- `daysRemainingInPlan`: الأيام المتبقية حتى النهاية.

عند إكمال الهدف قبل النهاية يبقى الهدف الأصلي كما هو، ويستمر النشاط من Anchor الحقيقي. أي صفحات فوق الهدف تظهر كـ **الإنجاز الإضافي** ولا تُحوّل إلى دين أو متبقي سالب.

## 2) Smart Plan Premium Overview
أضيف `SmartPlanOverviewCard` ليكون الواجهة الموحدة للحالة الأساسية:
- اسم الطالب بخط ExtraBold.
- Chip للحالة: على الخطة / متقدم / يحتاج تعويض / أتم الخطة مبكرًا / مكتملة.
- هدف / منجز / متبقي أو إنجاز إضافي.
- Progress شهري وأسبوعي.
- بطاقة خاصة عند الإكمال المبكر.
- أيقونات Material موحدة للحالة، الأسبوع، المقرر، التاريخ، التنبيه والإنجاز.

كما تحولت تفاصيل الخطة من AlertDialog مزدحم إلى `ModalBottomSheet` موسع يعيد استخدام نفس الـOverview ويعرض المقرر وسبب القرار والنطاق والحد اليومي والملاحظات بكتل واضحة.

## 3) Explainable Smart Assignment بعد إكمال الهدف
`لماذا هذا المقرر؟` أصبحت واعية بالإكمال المبكر. إذا انتهى الهدف قبل الموعد يوضح التطبيق أن الاستمرار من آخر موضع هو إنجاز إضافي ولا يرفع هدف الشهر تلقائيًا.

## 4) Session compact interactive roster
بطاقة الطالب في الجلسة أصبحت Compact افتراضيًا:
- الاسم أثقل وأوضح.
- الحالة والسلوك والمزامنة في سطر مختصر.
- آخر تسميع ظاهر بدون فتح التفاصيل.
- زر توسع/طي، مع `AnimatedVisibility` و`animateContentSize`.
- داخل التوسع تظهر الحضور والسلوك وSmart Plan والتسميعات والأزرار.

هذا يقلل طول قائمة الأربعين طالبًا ويترك التفاصيل عند الحاجة فقط.

## 5) بحث عربي أفضل + Session Summary واحد
أضيفت قواعد بحث Android-free في `SessionWorkflowRules.matchesSearch`:
- إزالة التشكيل.
- توحيد أشكال الألف.
- توحيد ى/ي وة/ه.
- تجاهل المسافات والرموز الزائدة.

كما أضيف `SessionWorkflowRules.summary` ليكون المصدر الموحد لأعداد: غير مرصود / لم يسمع / سمّع / حاضر / غائب. تستخدمه الشاشة وشريط الملخص ونافذة الإغلاق بدل إعادة الحساب بعدة طرق.

## 6) Pull-to-Refresh
الجلسة والخطة الذكية تدعمان Pull-to-Refresh مع الحفاظ على Cache-first. السحب يحدث البيانات ويعيد الدمج، ولا يمسح البيانات المحلية قبل وصول الشبكة.

## 7) حذف التسميع + Undo deferred
أي تسميع في أي جلسة يمكن حذفه. الحذف لا يرسل للسيرفر فور الضغط:
1. تأكيد حذف.
2. اختفاء التسميع مؤقتًا من الواجهة.
3. Snackbar: `تم حذف التسميع مؤقتًا — تراجع`.
4. عند التراجع يعود بدون mutation.
5. عند انتهاء المهلة فقط ينفذ `deleteRecitation`.
6. بعد الحذف يعاد بناء Smart Plan من التاريخ المتبقي.

هذا يطابق فلسفة Undo لحذف الجلسة ويمنع حذفًا لا رجعة فيه بسبب لمسة خاطئة.

## 8) إصلاح بنيوي: Plan-history cache consistency
أثناء RC9.3 ظهر خلل بنيوي مهم: Session cache كان يتحدث عند تعديل/حذف/Undo التسميع، لكن `CACHE_PLAN_RECITATIONS` قد يبقى بإصدار أقدم بعد انتهاء Outbox. هذا قد يجعل Smart Plan يعيد حساب نفسه من تاريخ قديم بعد المزامنة.

تم الإصلاح بإضافة:
- `upsertPlanRecitationCacheRow`: الحفظ/التعديل يحدث Session cache وPlan-history cache بنفس ID.
- `removePlanRecitationCacheRow`: الحذف وUndo يزيلان السجل من Plan-history cache أيضًا.
- Pending delete tombstone overlay (`recitation-delete:*`) داخل `planRecitations` حتى لا يعيد Network cache سجلًا محذوفًا أثناء انتظار المزامنة.
- `invalidatePlanSnapshotMemory()` بعد mutations ذات الصلة.

**سبب التعديل:** ضمان أن UI، Offline Outbox، Session history وSmart Plan rebuild يقرؤون نفس التاريخ المنطقي قبل المزامنة وبعدها.

## 9) إغلاق الجلسة
زر الإغلاق يعرض دائمًا ملخصًا قبل الخروج. إذا بقي `UNMARKED` يظهر التحذير بوضوح مع خيار العودة للرصد أو الإغلاق على أي حال. `NOT_RECITED` حالة مقصودة ولا تعامل كطالب منسي.

## 10) لا توجد DB migration
لا توجد DB migration في RC9.3. لم نضف جدولًا أو عمودًا أو RLS جديدًا. كل التثبيت تم فوق الـOutbox والـCache والـIDs الموجودة، لتقليل مخاطر الترحيل والحفاظ على توافق Supabase الحالي.

## 11) Performance / Compose
- LazyColumn keys ثابتة.
- البطاقات الثقيلة لا توسع كل الطلاب افتراضيًا.
- Smart Plan calculations لا تنفذ داخل الـComposables كمصدر مستقل؛ الواجهة تقرأ `coreState` المشتقة.
- Pull-to-refresh منفصل عن Local-first rendering.
- Touch targets تبقى 48dp على الأقل للأفعال الرئيسية.

## 12) Regression gates
أضيفت/حدثت:
- `SmartPlanUiRulesTest`.
- `SmartPlanCoreStateTest` لحالات COMPLETE_EARLY/Surplus.
- `SessionWorkflowRulesTest` للبحث والملخص.
- `scripts/selftests/RC93RulesSelfTest.kt`.
- `scripts/rc93-final-stabilization-check.py`.
- `scripts/run-kotlin-selftests.sh` يشغل RC9.3 pure rules ضمن الحزمة الكاملة.

كل بوابات RC8/RC8.6/RC9.1/RC9.2 تبقى Regression gates قبل قبول RC9.3.
