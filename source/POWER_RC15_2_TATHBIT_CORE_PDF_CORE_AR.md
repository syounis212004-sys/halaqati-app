# RC15.2 — Tathbit Core + Certificate/PDF Core

## إصلاح البناء
- `SmartPlanPhasesUi.kt`: `plan` → `snapshot.plan`، وتعريف `tathbitPlan` داخل ورقة المراحل.

## مسار التثبيت (Core)
- المسار دائم `track_code = tathbit`، والخطة شهرية `plan_type = tathbit` بلا أي `smart_plan_phases`.
- خطة التثبيت تفتح `TathbitMonthlyPlanSheet`: الشهر، النطاق، الهدف، أيام التسميع، المنجز/المتبقي/النسبة، آخر موضع.
- لا حفظ/سرد/مرحلة/انتقال جماعي/قالب حلقة في منطق خطة التثبيت.
- تسميع التثبيت لا يحمل `phase_id`؛ السيرفر يربطه بـ`plan_id` الشهري تلقائيًا.
- `rc152_roll_tathbit_months()`: تجميد نتيجة الشهر المنتهي (`frozen_summary`) وفتح الشهر الجديد. pg_cron يوميًا + استدعاء احتياطي من التطبيق مرة يوميًا.
- `rc152_tathbit_consistency()`: فحص سلامة (bad_plans / live_phases_on_tathbit / foreign_recitations = 0).
- الخطط القديمة hifz/sard لطالب تثبيت تُعلَّم `legacy` وتُؤرشف دون حذف.

## الشهادات والتقارير (Core)
- الطبقة 0: التصدير من **نفس WebView المعاينة** (مرفقة، ظاهرة، مرسومة) → Vector PDF.
- عند فشل Chromium: الشهادة تُرسم من المعاينة نفسها إلى `PdfDocument` (مستقل عن محرك الطباعة).
- المحركات المعزولة القديمة تبقى طبقة أخيرة فقط.
- قواعد تكبير المعاينة `@media screen` فقط، و`@media print` تعيد تخطيط A4 الأصلي.
- `DocumentExportManager`: التصدير خارج نطاق Compose فلا يُلغى بإعادة التركيب.
- حفظ ذري: cache → تحقق (>1KB و`%PDF-`) → Downloads → إعادة قراءة الحجم.

## النشر
1. شغّل `migration/20260929_v2502_rc15_2_tathbit_core_monthly.sql` بعد RC15 وRC15.1.
2. تحقق: `select public.rc152_tathbit_consistency();`
3. ابنِ الـAPK عبر `RC15_2_TATHBIT_CORE_PDF_CORE.yml`.
