#!/usr/bin/env python3
"""48-surface Golden Master parity evidence gate.
Checks that every v2.44 contract has an explicit Native implementation surface.
It complements, not replaces, Gradle/device acceptance testing.
"""
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
def r(rel):
 p=root/rel
 return p.read_text(encoding='utf-8',errors='ignore') if p.exists() else ''

def contains(text,*tokens): return all(t in text for t in tokens)
nav=r('app/src/main/java/com/mohammedsamir/halaqati/ui/navigation/RoleNavigationPolicy.kt')
shell=r('app/src/main/java/com/mohammedsamir/halaqati/ui/components/GoldenShell.kt')
app=r('app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt')
core=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
mgmt=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt')
reports=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
guard=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/GuardianParityScreens.kt')
dash=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/DashboardScreen.kt')+r('app/src/main/java/com/mohammedsamir/halaqati/ui/components/TodayHero.kt')
dvm=r('app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DashboardViewModel.kt')
repo=r('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
plans=r('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt')
settings=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SettingsParitySections.kt')+reports
reportstudio=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportStudioControls.kt')+r('app/src/main/java/com/mohammedsamir/halaqati/documents/ReportHtmlRenderer.kt')+r('app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt')+reports
exports=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/NativeStudentExport.kt')+reports
sync=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SyncCenterScreen.kt')+r('app/src/main/java/com/mohammedsamir/halaqati/data/OfflineQueue.kt')
auth=r('app/src/main/java/com/mohammedsamir/halaqati/ui/auth/NativeGoogleSignIn.kt')+r('app/src/main/java/com/mohammedsamir/halaqati/ui/AuthScreen.kt')+repo
quran='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in (root/'app/src/main/java/com/mohammedsamir/halaqati/quran').rglob('*.kt')) + reports + core

checks={
'shell.bottom.admin_supervisor_teacher': contains(nav,'AppDestination.Home, AppDestination.Sessions, AppDestination.Add, AppDestination.Community, AppDestination.More') and contains(shell,'fun GoldenBottomBar(','destination.iconKey','destination.label'),
'shell.bottom.guardian': contains(nav,'AppDestination.Home, AppDestination.GuardianProfile, AppDestination.GuardianAchievements, AppDestination.Community, AppDestination.More'),
'shell.more.menu': contains(shell,'fun GoldenDrawerContent(','LazyColumn(','RoleNavigationPolicy.drawerItems') and contains(nav,'drawerItems(role: UserRole)'),
'home.dashboard': contains(dash,'لوحة اليوم','ابدأ جلسة اليوم','التحفيظ السريع','عرض القائمة كاملة'),
'home.daily_supervisor_board': contains(dash+dvm,'لم يسمّع اليوم','غائب اليوم','متأخر عن الخطة','يحتاج مراجعة','تجاوز هدفه'),
'notifications.center': contains(mgmt,'fun NotificationsNativeScreen(','إشعارات','read_at'),
'notifications.push_preferences': contains(mgmt+reports,'NotificationPreferencesDialog','notification_preferences','تفضيلات الإشعارات') and 'Text("حفظ")' in mgmt,
'community.announcements': contains(mgmt,'fun CommunityNativeScreen(','announcements','إعلان'),
'community.forum_comments': contains(mgmt,'forum_posts','forum_comments','تعليق'),
'halaqas.manage': contains(mgmt,'fun HalaqasManagementScreen(','إضافة حلقة','تعديل الحلقة','delete_halaqa_cascade'),
'categories.manage': contains(mgmt,'student_categories','إضافة فئة','فئات '),
'sessions.list': contains(core,'fun SessionsScreen(','الجلسات اليومية','جلسة اليوم'),
'sessions.start_today': contains(core+repo,'ensureTodaySession(','ابدأ جلسة اليوم'),
'sessions.attendance': contains(core+repo,'attendanceForSession','الحضور','arrivalMinutesLate'),
'sessions.recitation': contains(core+repo,'RecitationDialog(','saveRecitation') and (contains(core,'RecitationCounter("عدد الأخطاء"','RecitationCounter("عدد النقرات / التلقينات"') or contains(core,'RecitationCounter("الأخطاء"','RecitationCounter("التلقين"')),
'sessions.tathbit_activity': contains(core,'review_near','review_far','التثبيت'),
'sessions.sard_activity': contains(core,'"sard"','سرد'),
'sessions.daily_notes': contains(core+repo,'DailyNote','teacher_comment','parent_visible'),
'students.list': contains(core,'بحث بالاسم أو الهوية','كل الحلقات','كل الفئات','كل المسارات','ترتيب أبجدي'),
'students.detail': contains(core,'StudentDetailSheet(','الملف الشخصي','البيانات الأساسية'),
'students.edit': contains(core,'StudentFullEditDialog(','تعديل البيانات','guardian_phone','date_of_birth'),
'students.archive_restore': contains(core,'المنسحبون / الأرشيف','استعادة الطالب','withdrawn'),
'plans.list': contains(core,'fun PlansScreen(','إدارة الخطط','خطة جديدة'),
'plans.smart_hifz': contains(core+plans,'الحفظ العام','مقرر اليوم','dailyTarget','assignment'),
'plans.smart_tathbit': contains(core+plans,'التثبيت','reviewTier','nearPages','farPages'),
'plans.student_override': contains(core,'تجاوز خاص بالطالب','parentPlanId','isStudentOverride'),
'plans.guardian_view': contains(core,'خطط الأبناء','مقرر اليوم','guardianChildren','أرشيف خطط'),
'reports.list': contains(reports,'fun ReportsScreen(','هذا الشهر مقابل الشهر الماضي','ReportDocumentType.HALAQA','ReportDocumentType.LEADERS','ReportDocumentType.POINTS','ReportDocumentType.ATTENDANCE','ReportDocumentType.RECITATIONS','ReportDocumentType.STUDENTS'),
'reports.studio': contains(reportstudio,'ReportStudioControls','عنوان التقرير','تذييل التقرير','أفقي A4','visibleColumns') and contains(r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/NativeReportExport.kt')+reports,'exportXlsx','exportWord','exportPng','shareText'),
'reports.guardian': contains(reports,'تقارير أولياء الأمور','profile.role == UserRole.GUARDIAN'),
'certificates.editor': contains(reports,'fun CertificatesScreen(','نصوص وهوية الشهادة','عنوان الشهادة','المقدمة','الدعاء / الخاتمة','HtmlDocumentPreview','CertificateHtmlRenderer.build','الأرشيف الرسمي للشهادات','إنشاء نسخة مصححة','إلغاء رسمي'),
'quran.calculator_picker': contains(quran,'QuranRangePickerDialog','fun QuranCalculatorScreen()','الحاسبة السريعة بالصفحات'),
'ranking.staff': contains(reports,'fun RankingsScreen(','الترتيب والإحصائيات','RankingFilterSelect','"general_hifz" to "الحفظ العام"','"hifz" to "الحفظ"'),
'guardian.competition': contains(guard+repo+app,'GuardianCompetitionNativeScreen','guardianCompetition','الطالب','نطاق المنافسة'),
'guardian.achievements': contains(guard+app,'GuardianAchievementsNativeScreen','الإنجازات','guardianDashboard'),
'guardian.profile': contains(mgmt+app,'GuardianProfileNativeScreen','ملف الطالب'),
'supervision.teacher_tracking': contains(mgmt,'SupervisionNativeScreen','متابعة المحفّظ','supervisor_visits'),
'users.permissions': contains(core,'fun UsersScreen(','الحسابات','الصلاحيات','approval_status'),
'data.import': contains(reports,'SmartImport','إضافة طلاب جماعياً' if 'إضافة طلاب جماعياً' in reports else 'اختيار ملف','تسميعات سابقة','الأهداف الشهرية'),
'data.export_backup': contains(exports,'exportXlsx','exportWord','exportPdf','نسخة احتياطية','تصدير الطلاب Excel'),
'sync.center': contains(sync,'fun SyncCenterScreen(','مزامنة','blocked'),
'settings.theme': contains(settings,'ThemeMode','UiDensity','إعادة ضبط المظهر'),
'settings.cairo_font': contains(r('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/CairoTypography.kt'),'Cairo') and 'Cairo' in reportstudio,
'settings.password': contains(settings,'updatePassword','تأكيد كلمة المرور'),
'settings.account_name': contains(settings+repo,'AccountNameSettingsCard','updateMyFullName'),
'settings.update': contains(settings,'AppUpdateSettingsCard','فحص التحديث','NativeAppUpdater'),
'auth.login_approval_recovery': contains(auth,'NativeGoogleSignIn','requestPasswordReset','approval_status') and 'googleOAuthUrl()' in repo,
'offline.account_scoped_queue': contains(sync+repo,'OfflineQueue','owner','account') or contains(sync+repo,'userId','pending'),
}

bad=[]
for k,v in checks.items():
 print(('PASS' if v else 'FAIL'),k)
 if not v: bad.append(k)
if len(checks)!=48:
 print('INTERNAL ERROR expected 48 checks, got',len(checks)); sys.exit(2)
if bad:
 print(f'Full release parity: {48-len(bad)}/48 PASS; missing={bad}')
 sys.exit(1)
print('Full release parity: 48/48 PASS')
