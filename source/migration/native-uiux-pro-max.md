# Native UI/UX Pro Max implementation — 2.46.0 FULL PARITY RC2

مرجع الترحيل: `legacy-web-v2.44.0/V2200_UIUX_PRO_MAX_AR.md` مع مراجعات UX Pilot.

## مطبق الآن
- RTL مفروض على Compose.
- Design tokens أصلية للمسافات 4/8/12/16/24، الحواف 12/16/22، وTouch target 48dp.
- Typography floor: النص الثانوي 12sp والأساسي 14sp فأعلى.
- Light/Dark color schemes متناسقة مع هوية حلقاتي.
- ProFilterChip بارتفاع لمس 48dp وحالة اختيار واضحة.
- Skeleton loading وحالات info/success/warning/error موحدة.
- Reports: فلاتر الفترة + الحلقة + المسار + نوع اللقاء، معاينة ملخص/تفاصيل، Cache كامل آخر نجاح، PDF Native.
- Certificates: إصدار فردي وجماعي، multi-select، تحديد الكل، بحث الطلاب، ترقيم متسلسل، PDF متعدد الصفحات، أرشيف محلي وبحث بالأرشيف.
- Sync Center: حالات online/offline/pending/blocked، رسالة "تم الحفظ محليًا"، Skeleton، retry/discard بأهداف لمس 48dp، badges غير قابلة للنقر بدل AssistChip الوهمي.
- Import Center: Preview/Mapping/Validation/Commit مع أهداف لمس كبيرة ورفض OCR التلقائي غير المراجع.
- Students: فلاتر الحالة/المسار/الحلقة/الفئة، حالة Empty قابلة لإعادة التعيين، Bottom Sheet لتفاصيل الطالب، ملخص الشهر، آخر التسميعات، الخطة الذكية، تعديل سريع، وCTA مباشر للتسميع عند الاتصال.
- ProFilterChip يضيف علامة اختيار تلقائية للحالة النشطة، مع guardrails تمنع raw FilterChip وAssistChip الوهمي وأزرار IconButton دون 48dp داخل الشاشات.

## تحسينات إضافية مطبقة
- Guardian: حالة اليوم البارزة، Offline صريح، كثافة أقل للرسم، وBottom spacing آمن.
- Supervision: فلترة حسب الحلقة، أسباب المتابعة كـStatusPill غير قابل للنقر، وTouch targets 48dp.
- Accounts: Skeleton/StateNotice، حوار ربط ولي الأمر، وأزرار destructive/role actions بأهداف لمس 48dp.
- Certificates: خمسة قوالب محلية، تحكم بالأحجام/الشعارات/الإطار/الزخارف/إظهار العناصر، موضع رقم الشهادة، وتصدير/استيراد فهرس الأرشيف JSON.

## مراجعات UX Pilot المستخدمة
- Reports review: touch targets، contrast، skeleton، active filters.
- Reports persona audit للمحفّظ/المشرف: فلاتر الحلقة/المسار/نوع اللقاء + preview + 48dp.
- Certificates review/persona audit: multi-select، bulk PDF، search، serial number، 48dp.
- Students + Student Details + Smart Plans reviews: 48dp، وضوح الفلتر النشط، Empty/reset، CTA للتسميع، RTL progress، وحالة الاتصال/المزامنة.
- Missing-screen generation request prepared on the same UX Pilot page; لا تُعتبر الشاشات الجديدة مولّدة حتى تظهر كتصاميم completed.

## لم يُثبت بعد
- Full Android Gradle compile / instrumentation / device accessibility scan.
- Cairo is pinned and fetched/embedded by the supplied workflow; the Cairo contract gate verifies global Compose/PDF usage before Gradle.
- Pixel-perfect device parity still needs screenshot/device QA, but the 48-surface Golden Master contract and UI 2.44 restoration gates are now green.

## Release-freeze UX additions
- Import Center now has a dedicated bottom-sheet column mapper for students, historical recitations and monthly goals; every new source resets mapping state so a same-header file cannot inherit stale mapping.
- Structural preflight classifies every preview row as valid / skipped / needs correction before any write, and unknown activity/track values are not silently coerced.
- Unknown navigation routes fail closed to a dedicated state; the old GenericDataScreen database fallback has been removed completely.

- Auth recovery: dedicated Native password-reset state/screen, duplicate-password validation, explicit busy/error state, and a 48dp primary CTA; auth callbacks are not routed into ordinary feature navigation.
- Notification diagnostics in Settings: current Android notification state, shortcut to system notification settings, and server-backed “اختبار Push” action with visible success/failure feedback.
- Notification deep links preserve the intended student for multi-child guardian accounts instead of defaulting to the first child.

## Release-freeze account-switch UX
- Sync Center no longer hides existing rows behind a blocking top progress bar during refresh; it keeps current state visible and uses a non-blocking status notice.
- Settings shows pending/blocked offline counts for the current account.
- Logging out with unsynced work opens an explicit confirmation explaining that queued data remains local and account-isolated; the user can stay and trigger synchronization or leave while retaining the scoped queue.
- Deep-link callback matching is exact and `MainActivity` uses `singleTop`, reducing duplicate-task behavior when opening auth links or notification links repeatedly.


## Google authentication UX
- Primary path uses Credential Manager with `GetGoogleIdOption` and the configured Web OAuth client ID.
- `googleid:1.1.1` is intentionally retained to preserve minSdk 23.
- If Credential Manager reports no eligible credentials, the app opens the existing Supabase Google OAuth flow in a resizable Partial Custom Tab bottom sheet instead of launching a separate full Chrome task.
- Supabase redirects back to the exact `com.mohammedsamir.halaqati://auth/callback` deep link.

## RC8.6 UX compact pass
- تم إنشاء مرجع Figma باسم `Halaqati RC8.6 UX Reference` لشاشة Smart Plan المدمجة وفلاتر الترتيب.
- أزيل Hero المكرر من شاشة الترتيب؛ الفلاتر في بطاقة مدمجة بصفين وتبدأ النتائج أعلى الشاشة.
- `bodyLarge` و`bodyMedium` يستخدمان Cairo Medium لقراءة أوضح، مع إبقاء secondary captions أخف.
- Smart Plan يستخدم وحدات دلالية متقاربة بدل سلسلة Cards كبيرة، مع Touch targets 48dp على عناصر التحكم.
- Offline/Refreshing لا يمسح البيانات القديمة لصالح Skeleton عندما توجد لقطة محلية.
