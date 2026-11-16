# RC17.1 BUILD FIX 4 — Shared label visibility compile fix

سبب فشل BUILD FIX 3 في GitHub Actions كان خطأ Kotlin compile محددًا: شاشة CoreScreens تستخدم `recitationRangeLabel` و`quranLocationLabel` بينما الدالتان كانتا `private` داخل ملف SmartPlanPhasesUi.kt.

تم الإصلاح بتحويل الدالتين إلى `internal` على مستوى الموديول، مع إضافة regression assertions إلى `compiler-regression-check.py` لمنع رجوعهما إلى file-private مستقبلًا. لا يوجد تغيير في قاعدة البيانات أو منطق الخطة أو Supabase في هذا الإصلاح.

النص المعتمد للهوية يبقى:

`Designed by : Dr. Mohammed Samir Abu Younis`
