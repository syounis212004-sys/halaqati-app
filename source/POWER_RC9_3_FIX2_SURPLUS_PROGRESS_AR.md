# RC9.3 FIX2 — Surplus Progress Regression Fix

هذا الإصلاح مبني فوق RC9.3 FINAL STABILIZATION FIX1، ولا يغيّر واجهات RC9.3 أو قاعدة البيانات.

## السبب الجذري
كان `SmartPlanEngine.buildCompletion()` يقص قيمة الإنجاز الفعلي عند `targetPages` باستخدام `coerceIn(0.0, plan.targetPages)`.
لذلك إذا كان الهدف 5 صفحات وأنجز الطالب 7 صفحات، كانت `weekly.completed` ترى 7 صفحات بينما `Progress.completed` و`SmartPlanCoreState` يريان 5 فقط، فتضيع قيمة الإنجاز الإضافي.

## الإصلاح
- الاحتفاظ بالإنجاز الفعلي كاملًا بعد تجاوز الهدف.
- يظل `remaining = 0` بعد إكمال الهدف.
- يحسب `surplusPages = completed - targetPages` بصورة صحيحة.
- يظل `COMPLETE_EARLY` هو وضع الخطة قبل نهاية المدة.
- أضيف سيناريو Surplus إلى `SmartPlanCoreStateSelfTest` حتى يُكتشف محليًا قبل Gradle/GitHub.

لا توجد Migration أو تغييرات Supabase في FIX2.
