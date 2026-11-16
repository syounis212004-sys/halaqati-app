# Halaqati v2.47.1-RC12.1 — Stability Consolidation + Certificates Core

هذا الإصدار يقفل الديون الحرجة التي ظهرت أثناء تشغيل RC11 ويحوّل إصلاحات Supabase اليدوية إلى عقد رسمي داخل الكود والـMigration، ثم يضيف الأساس الخادمي للشهادات تمهيدًا لـRC12.2.

## تثبيت RC11 داخل الكود

- `phase_id` أصبح المرجع الأعلى لملكية التسميع داخل المرحلة؛ `smart_plan_id` القديم لا يستطيع إسقاط تسميع صحيح إذا كان فارغًا أو `null` نصية.
- تطبيع `null / "null" / undefined / blank` لجميع معرفات الخطة القديمة.
- أي تسميع جديد يحمل `smart_plan_id + smart_plan_phase_id + phase_id` عندما توجد مرحلة.
- المزامنة ونجاح RPC وRefresh للمراحل تلغي Smart Plan snapshot القديم فورًا.
- إصلاح Retry/Outbox والـRPC المتسلسل من RC11.3 مثبت في السلسلة الرسمية.
- إصلاح `plan_id is ambiguous` في `rc113_bulk_configure_plan_cycles` مثبت في Migration RC12.1.
- جلسة السبت الوهمية ما زالت ممنوعة من المسار التلقائي؛ الجلسة الاستثنائية تبقى قرارًا يدويًا.
- اتجاه السرد للحفظ العام يبقى افتراضيًا من الناس إلى البقرة مع آيات تصاعدية داخل السورة.

## Repair آمن للبيانات القديمة

Migration RC12.1 تمر على أي `recitation_entries` لها `phase_id` صحيح وتعيد كتابة metadata بحيث تطابق Parent Plan/Phase، بدون تغيير عدد الصفحات أو التسميع نفسه.

## Certificates Core

تمت إضافة:

- `certificates`
- `certificate_templates`
- `certificate_number_counters`
- `rc121_issue_certificate`
- `rc121_next_certificate_number`
- `rc121_capabilities`

الأنواع الأساسية: `hifz / sard / achievement`.

Draft قابل للحفظ محليًا/في Outbox، أما الاعتماد النهائي فيحتاج اتصالًا حتى يحصل على رقم فريد. عند الاعتماد تُثبت Snapshot الشهادة ولا يمكن تعديل محتواها بصمت بعد ذلك؛ التصحيح لاحقًا يكون بشهادة جديدة/بديلة في RC12.2.

RLS يسمح للمحفظ/المشرف/الإدارة بإدارة شهادات حلقاتهم، وولي الأمر بقراءة الشهادات الصادرة للطلاب المرتبطين به فقط.

## ما يبقى لـRC12.2

واجهة Certificates Studio النهائية، ربط القوالب الخادمية بالمحرر المرئي، اعتماد الرقم الخادمي مباشرة في PDF/Preview، Reissue كامل، مشاركة/طباعة/أرشيف خادمي موحد وتوقيعات/شعارات متقدمة.
