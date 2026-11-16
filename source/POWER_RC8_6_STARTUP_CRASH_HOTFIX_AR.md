# RC8.6 Startup Crash Hotfix V3

## المشكلة
بعد تسجيل الدخول يصبح account id محفوظًا. كان AppViewModel يجمع StateFlow الاتصال على Main ثم يستدعي refreshSync()، والتي كانت تنفذ DAO متزامنًا من Room لقراءة pending/blocked/lastSync. Room لا يسمح بالاستعلامات المتزامنة على Main، لذلك يمكن أن يسقط التطبيق فور التشغيل بعد وجود جلسة مستخدم محفوظة.

## الإصلاح
- نقل كل قراءات SyncState من Room إلى Dispatchers.IO.
- إزالة قراءة lastSyncInstant من collector على Main.
- منع full sync من الانطلاق داخل Application startup قبل ظهور الواجهة.
- تفعيل immediate sync عند onResume وتعطيله كـforeground trigger عند onStop؛ WorkManager يبقى fallback في الخلفية.
- إضافة rc86-startup-crash-regression-check.py.
