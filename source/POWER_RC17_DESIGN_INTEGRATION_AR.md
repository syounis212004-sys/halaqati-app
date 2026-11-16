# RC17 — دمج واجهة Halaqati Design Package

هذا الإصدار يحافظ على منطق RC17 v2.53.0 وطبقة البيانات وSupabase والمزامنة والخطط والتقارير، ويستبدل طبقة العرض الرئيسية بهوية تصميم Halaqati Design Package داخل Jetpack Compose.

## ما تم تحديثه
- نظام الألوان: أخضر عميق + عاجي + ذهبي، مع دعم الوضع الداكن.
- Cairo Typography، المسافات، الحواف، بطاقات القياسات، الحالات والشرائح.
- Top App Bar وBottom Navigation وDrawer وانتقالات التنقل.
- واجهة المحفّظ الرئيسية مع Hero وإجراءات الجلسة والتحفيظ السريع وملخص اليوم.
- واجهة ولي الأمر بصيغة قراءة فقط مع اختيار الابن وملخص الحضور/التسميع/التثبيت والخطة.
- واجهات تسجيل الدخول والجلسات وعناصر الحالة العامة.
- إعداد المظهر الافتراضي ليتوافق مع الهوية الجديدة مع الحفاظ على الألوان المخصصة للمستخدم إن وجدت.

## ما لم يتغير
- مخطط قاعدة البيانات والمهاجرات وRLS وRPCs.
- Repository / ViewModels / Offline Queue / Sync Engine.
- Quran Range Engine وSmart Plan وحسابات الصفحات.
- قواعد المسارين: الحفظ العام والتثبيت فقط، والسرد نشاط وليس مسارًا ثالثًا.
- التقارير والشهادات والاستيراد/التصدير والصلاحيات والإشعارات.

## التحقق
- Full release parity: 48/48 PASS.
- Behavioral parity: 34/34 PASS.
- Pure Kotlin self-tests: PASS.
- RC17 role experience, UI/UX, navigation, auth, offline, reports, certificates, Quran/Smart Plan and release-contract source gates: PASS.
