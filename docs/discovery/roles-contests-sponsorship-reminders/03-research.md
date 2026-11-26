# 03 — البحث (مصادر رسمية)

الاستعلام: صفحات أندرويد الرسمية لجدولة المنبّهات و WorkManager، قرأها وكيل web-fetch وأعاد الاقتباسات.

- [FACT] https://developer.android.com/develop/background-work/services/alarms/schedule — لتطبيق يستهدف أندرويد 12+ يلزم إذن «التنبيهات والتذكيرات» وإلا `SecurityException`؛ `SCHEDULE_EXACT_ALARM` «لا يُمنح مسبقًا للتثبيتات الجديدة» على أندرويد 13+ ويمنحه المستخدم؛ `USE_EXACT_ALARM` يُمنح تلقائيًا لكنه «محدود بحالات استخدام ومشمول بسياسة Google Play»؛ «تُلغى كل المنبّهات عند إيقاف الجهاز» ويجب إعادة ضبطها بـ `RECEIVE_BOOT_COMPLETED`؛ وتوصي الصفحة بمنبّه غير دقيق «كلما أمكن».
- [FACT] https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work — أقل فاصل دوري 15 دقيقة؛ وقت التنفيذ «غير دقيق» ويتأثر بتحسينات النظام؛ يمكن ضبط تأخير ابتدائي `initialDelay`.
- [ASSUMPTION] بقاء أعمال WorkManager المجدولة بعد إعادة التشغيل: لم تذكره الصفحتان؛ أعرفه من تصميم المكتبة (قاعدة بيانات داخلية)، يُختبر بتجربة على الجهاز قبل الاعتماد عليه.
- [ASSUMPTION] دقة «نحو الوقت المختار ±عدة دقائق» كافية لتذكير بكفالة أو مسابقة (ليست مناداة صلاة).
