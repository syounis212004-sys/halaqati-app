# RC17.1 BUILD FIX 6 — Source Gate Compatibility

هذا الإصلاح خاص بعقود CI/الاختبارات القديمة ولا يغيّر منطق التطبيق أو قاعدة البيانات.

## لماذا فشل BUILD FIX 5 رغم أن APK بُني بنجاح؟
أظهر سجل GitHub أن Kotlin compile وAndroidTest compile والـunit tests وLint وAssemble وAPK verification كلها نجحت، لكن `source-gates` فشل بسبب اختبارات مصدر قديمة تعتمد على نصوص/تنفيذ واجهة تم تغييره عمدًا في BUILD FIX 5.

## الإصلاحات
1. **ولي الأمر:** اختبار RC10.2/10.3 كان يشترط وجود `StudentGuardianRules.filter` داخل `CoreScreens.kt`. في FIX5 انتقلت الواجهة إلى `HalaqatiLazySelectField` المبني على `LazyColumn`. الاختبار الآن يتحقق من الـLazy selector والبحث، ويستمر في التحقق من وجود قواعد `matches/filter` نفسها.
2. **الخطة مقابل المراحل:** اختبار RC11.2 كان يشترط العبارة القديمة «هذه الخطة أصبحت مُدارة بمراحل RC11.3». FIX5 فصل «تعديل بيانات الخطة» عن «مراحل الخطة» حسب التصميم الجديد، لذلك صار الاختبار يتحقق من النص الجديد ومن بقاء حقول المرحلة محمية عبر `phaseManaged`.
3. **اختيار طالب الشهادة:** اختبار RC12.2 كان يشترط `studentCandidates.forEach`. FIX5 استبدله بـLazyColumn `items(studentCandidates)` لتحسين الأداء؛ تم تحديث الاختبار ليقبل التنفيذ الكسول مع بقاء الفرز/الفلترة حسب الفئة.

## قاعدة البيانات
لا يوجد Migration جديد ولا تغيير Supabase في BUILD FIX 6.
