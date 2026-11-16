# RC15 (2.50.0) — التثبيت نشاط مستقل + سلامة الخطط والمزامنة

**طبّق أولًا** `migration/20260928_v2500_rc15_tathbit_first_class_sync_integrity.sql` على Supabase، ثم ثبّت APK.

- التثبيت نشاط حقيقي: طالب tathbit → خطة tathbit → مرحلة tathbit → تسميع tathbit (مع الاختبار فقط).
- حُذف تحويل tathbit → review_far عند الكتابة. review_far القديم يُقرأ للتوافق فقط، والتسميعات التاريخية لا تُعدّل.
- `TrackActivityPolicy` قاعدة مركزية في المحرك + Triggers في قاعدة البيانات كحماية ثانية.
- تطبيع نطاق الخطة (student/category/halaqa) في التطبيق وفي طابور المزامنة وفي Trigger.
- الطابور: لا تُرسل مرحلة لخطة فشلت، ولا حالة لمرحلة فشلت. تصنيف الأخطاء: 401 تجديد جلسة، 23514 بيانات (بلا إعادة تلقائية)، 42501 صلاحيات/سجل أب.
- CancellationException لا تُعرض ولا تُسجَّل كفشل (رسالة The coroutine scope left the composition).
- RPC ذرّية `rc15_create_smart_plan_bundle` (SECURITY INVOKER — RLS كما هي).
- عرض `rc15_tathbit_consistency_issues` + تنبيه في مركز المزامنة.
- يتضمن إصلاح محرك PDF (RC14.1).
