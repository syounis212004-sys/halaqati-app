# حلقتي v2.59.0 RC23 — Student / Session / Plan Consistency

## ما تم إصلاحه

- البحث عن الطلاب يستخدم الآن تطبيعًا عربيًا موحدًا، والبحث المباشر يكشف الطالب الموقوف بدل أن يبدو كأنه محذوف.
- تم تصحيح حالة مجد الدين علاء إبراهيم أبو شاب في قاعدة الإنتاج إلى `active` مع إبقاء خطة أكتوبر فعالة.
- الخطة الذكية لم تعد تنشئ «اختبارًا مرحليًا» أو توقف مقرر الحفظ بانتظار اعتماد اختبار؛ الاختبار يبقى نشاطًا/جلسة مستقلة فقط.
- أي خطة طالب مباشر لطالب غير نشط تُوقف تلقائيًا بدل استمرارها بصمت.
- أصبح لكل حلقة في اليوم الواحد جلسة واحدة فقط من كل نوع: تسميع واحد، سرد واحد، مراجعة واحدة… مع السماح بالأنواع المختلفة في اليوم نفسه.
- جميع مسارات فتح جلسة اليوم تستخدم RPC ذريًا يعيد الجلسة الموجودة أو ينشئ واحدة فقط، لمنع سباقات جهازين أو ضغط الزر أكثر من مرة.
- أضيف `tilawah` إلى enum الجلسات في Supabase ليتطابق مع الخيار الموجود أصلًا في التطبيق.

## قاعدة البيانات

Migration:
`migration/20261008_v2590_rc23_student_session_plan_consistency.sql`

المخطط:
- يعطّل `milestone_mode` و`milestone_requires_approval` في الخطط الحالية والمستقبلية.
- يوقف خطط الطلاب المباشرة عندما تصبح حالة الطالب غير نشطة.
- ينظف الجلسات اليومية المكررة الفارغة ثم يضيف unique index على `(halaqa_id, session_date, session_type)` للجلسات غير الملغاة.
- يضيف RPC: `rc23_get_or_create_session`.
- يضيف `tilawah` إلى `session_type`.

> Migration RC23 مطبقة بالفعل على قاعدة الإنتاج الحالية. احتفظ بالملف لإعادة الإنتاج أو البيئات الجديدة، ولا تعِد تشغيله يدويًا على نفس البيئة دون حاجة.

## التحقق المحلي

- RC23 source gate: PASS
- RC22 regression gate: 19/19 PASS
- RC21 regression gate: 10/10 PASS
- Compiler regression source gate: PASS
- Compile sanity: PASS
- Full release parity: 48/48 PASS
- UI/UX Pro Max: PASS
- Release contract: PASS
- Pure Kotlin self-tests: PASS

Android Gradle compile/lint/assemble/signing يبقى ضمن GitHub Actions لأن بيئة العمل المحلية الحالية لا تحتوي Android SDK/Gradle كاملين.
