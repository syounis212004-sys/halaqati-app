# Halaqati v2.46.0 — POWER RC6

إصلاح تجميعي فوق POWER RC5 بعد التحقق من GitHub Actions:

- إضافة الاستيراد الصريح `androidx.compose.foundation.BorderStroke` إلى `ReportsAndTools.kt`.
- إضافة الاستيراد الصريح `androidx.compose.foundation.shape.RoundedCornerShape` إلى `ReportsAndTools.kt`.
- إضافة regression guards تمنع استخدام المكوّنين دون imports الصحيحة مستقبلًا.
- لا تغييرات على منطق Behavioral Parity في POWER RC5.
