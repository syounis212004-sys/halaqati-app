# RC8 Build Fix — Phase-scoped CI gates

## سبب فشل GitHub Actions
فشل التشغيل `97497439396` قبل مرحلة `Compile Kotlin`. جميع بوابات RC8 وRC7 التي سبقت الفشل نجحت، ثم كان الـworkflow يشغّل تلقائيًا كل ملف يطابق `scripts/*check.py`.

هذا أدخل `stable-release-check.py` في بناء RC8 رغم أن هذا الملف يفحص متطلبات مستقبلية تخص RC13 وRC15 ونسخة Stable، ومنها Report Studio instrumentation وBaseline Profile وStable acceptance contract. لذلك ظهر فشل لا علاقة له بجودة RC8 أو Quran Core.

## الإصلاح
- إضافة `scripts/rc8-ci-gates.py` كقائمة صريحة للبوابات التابعة لـRC8 وما يرثه من RC7/parity.
- منع auto-discovery من نوع `scripts/*check.py` في Workflow RC8.
- إبقاء `stable-release-check.py` داخل المشروع كما هو لاستخدامه في مرحلته الصحيحة لاحقًا، وعدم التحايل عليه أو تحويل متطلباته إلى PASS وهمي.
- تبقى اختبارات Kotlin الذاتية وGradle compile/unit/lint/assemble إلزامية بعد البوابات الثابتة.

## النتيجة المتوقعة
RC8 لن يفشل بسبب متطلبات RC13/RC15/Stable، لكنه سيظل يفشل فورًا إذا فشلت أي بوابة تخص RC8/RC7 أو فشل Compile/Unit/Lint/Assemble الفعلي.
