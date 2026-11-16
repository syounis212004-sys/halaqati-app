# RC17.1 BUILD FIX 2 — Smart Plan UUID NULL Repair

## العطل
عند تعديل خطة ذكية قد تصل قيمة `parent_plan_id` من cache قديم كالنص الحرفي `"null"` بدل JSON null، فيرفض PostgreSQL الحقل UUID بخطأ `22P02 invalid input syntax for type uuid: "null"`.

## الإصلاح
- `PlanRow.from()` يستخدم `optNullableString()` لكل UUID اختياري (`student_id`, `category_id`, `parent_plan_id`).
- `SyncErrorRules.sanitizeSmartPlan()` يحول `null`/`undefined`/blank النصية في UUIDs الاختيارية إلى JSON null قبل PostgREST.
- `patch()` يحفظ `entityId` الحقيقي لكل PATCH، وليس للطلاب فقط.
- `flushQueue()` يصلح تلقائياً العمليات القديمة/المحجوبة من smart_plans ويعيدها pending بنفس dedupe بعد تنظيف payload، لذلك لا يحتاج المستخدم لحذف العملية يدوياً.
- لا يحتاج هذا الإصلاح Migration جديدة في Supabase؛ الخطأ كان في payload المحلي قبل وصوله لقاعدة البيانات.
