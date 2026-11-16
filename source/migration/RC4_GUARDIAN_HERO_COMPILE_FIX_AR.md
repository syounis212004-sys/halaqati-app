# RC4 — إصلاح تجميع شاشة ولي الأمر

- إصلاح استدعاءات `GoldenFeatureHero` في `GuardianParityScreens.kt` بحيث تستخدم `badge: String?` بدل تمرير `ImageVector` غير المدعوم.
- إضافة Regression Guard في `scripts/compile-sanity-check.py` لمنع عودة `icon=` أو تمرير `Icons.Default.*` كوسيط ثالث لـ `GoldenFeatureHero` داخل شاشة ولي الأمر.
- لا تغيير في العقود الوظيفية: Full parity = 48/48، وRelease parity gate = 9/9 في الفحوصات المصدرية المحلية.
