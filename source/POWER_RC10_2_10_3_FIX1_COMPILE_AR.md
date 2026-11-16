# RC10.2/RC10.3 FIX1 — Dashboard compile fix

## السبب
فشل GitHub في `:app:compileDebugKotlin` بسبب استخدام `Icons.Default.PersonSearch` في `DashboardScreen.kt` بدون استيراد `androidx.compose.material.icons.filled.PersonSearch`.

## الإصلاح
- إضافة الاستيراد الناقص فقط؛ لا تغيير في منطق Student Intelligence أو Follow-up أو Supabase.
- إضافة `scripts/rc10-23-dashboard-icon-regression-check.py` لمنع رجوع نفس خطأ الـcompile.

## لا تغييرات بنيوية
لا migration، لا تعديل RLS، لا تغيير QuranRangeEngine، ولا تغيير في قواعد الخطة.
