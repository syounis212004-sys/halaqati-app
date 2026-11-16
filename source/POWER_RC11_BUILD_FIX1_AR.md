# RC11 BUILD FIX1 — إصلاح تجميع Kotlin

سبب فشل أول تشغيل GitHub لـ RC11 لم يكن تعليق Gradle ولم يكن مشكلة dependencies. وصل البناء إلى `:app:compileDebugKotlin` ثم توقف بخطأ Compiler واحد في `HalaqatiRepository.kt` عند السطر الخاص بعنوان المرحلة.

`phaseRow` يمكن أن يكون nullable عندما يتم تمرير `phaseEngineOverride`، بينما كان الكود يقرأ `phaseRow.title` مباشرة. تم إصلاحه إلى fallback آمن: عنوان المرحلة إن وُجد وكان غير فارغ، وإلا عنوان الخطة الأم.

هذا الإصلاح لا يغير سلوك RC11 ولا QuranRangeEngine ولا الـ anchors ولا الجداول أو RLS، وإنما يصلح خطأ null-safety يمنع تجميع التطبيق.

بعد الإصلاح نجحت الاختبارات المحلية المحددة لـ RC11، وجميع 19 Kotlin self-tests، وفحوص البنية والعقود المتاحة. إثبات Android Gradle/JUnit/Lint/APK النهائي يتم في تشغيل GitHub التالي.
