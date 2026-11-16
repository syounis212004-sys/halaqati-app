# 2.53.0 (RC17) — Role Experience & Performance Core

1. شغّل `migration/20260929_v2520_rc17_guardian_experience.sql` (إضافية، بلا تعديل جداول).
2. ابنِ عبر `RC17_2_52_0_ROLE_EXPERIENCE.yml`.

- ولي الأمر: رئيسية مستقلة (GuardianHomeScreen + ViewModel + نماذج مطبوعة)، طلب واحد لكل ابن، عرض المحفوظ أولًا، سحب للتحديث.
- عزل الأدوار: `follow_up` و`student/{id}` غير موجودة أصلًا في مسار ولي الأمر.
- التثبيت: المنافسة والإنجازات تتبع مسار الطالب.
- الشريط السفلي: هامش النظام خارج ارتفاع الشريط. الشريط العلوي: بلا اسم الدور، وحالة الاتصال تظهر عند الحاجة فقط.
- رئيسية المحفّظ: تحميل متوازي، وبدون جلب الطلاب والخطط مرتين.
- القياس: benchmark/JourneyBenchmarks.kt (FrameTimingMetric + BaselineProfileGenerator) — يُشغَّل يدويًا على جهاز مسجّل دخول.
