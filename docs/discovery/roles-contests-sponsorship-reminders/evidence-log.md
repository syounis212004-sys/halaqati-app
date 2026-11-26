# Evidence log
S1 | قراءة RoleNavigationPolicy.kt وNotificationHelper.kt وAndroidManifest.xml وSupportScreens.kt وContestsScreens.kt | مسارات المصدر في halaqati-app/source/app/src/main | FACT
S1 | قراءة تعريف rc48_support_pledge وrc48_support_cards وrc48_donor_scope_ok وcontest_is_donor من قاعدة الإنتاج (قراءة فقط) | mcp supabase-halaqati execute_sql: pg_get_functiondef | FACT
S1 | قراءة أعمدة support_cards وsupport_pledges | mcp supabase-halaqati execute_sql: information_schema.columns | FACT
S3 | قراءة وثائق أندرويد الرسمية لجدولة المنبّهات وتعريف عمل WorkManager | وكيل web-fetch a7c93c7e2de1beb60: developer.android.com/develop/background-work/services/alarms/schedule ، .../persistent/getting-started/define-work | FACT
S4 | تسعة بدائل في ثلاث مجموعات قرار | docs/discovery/roles-contests-sponsorship-reminders/04-options.md | ASSUMPTION
S7 | مقارنة على محاور الأمان والتعقيد والصيانة والتجربة | 06-comparison.md | ASSUMPTION
S12 | التصميم وخطة المراحل | 11-design.md | ASSUMPTION
