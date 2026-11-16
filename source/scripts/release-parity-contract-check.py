#!/usr/bin/env python3
"""Golden-master release parity gate for the nine surfaces left after the T6 parity pass.
This is deliberately semantic/source-based: it does not replace Gradle/device tests.
"""
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]

def read(rel):
    p=root/rel
    return p.read_text(encoding='utf-8',errors='ignore') if p.exists() else ''

nav=read('app/src/main/java/com/mohammedsamir/halaqati/ui/navigation/RoleNavigationPolicy.kt')
shell=read('app/src/main/java/com/mohammedsamir/halaqati/ui/components/GoldenShell.kt')
app=read('app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt')
dash=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/DashboardScreen.kt')
dvm=read('app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DashboardViewModel.kt')
core=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
reports=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
cert=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportStudioControls.kt') + reports
exports=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/NativeStudentExport.kt') + reports
guardian=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/GuardianParityScreens.kt')

checks=[]
def require(name, ok, detail): checks.append((name, bool(ok), detail))

require('shell.bottom.admin_supervisor_teacher',
        'AppDestination.Home, AppDestination.Sessions, AppDestination.Students, AppDestination.Plans, AppDestination.More' in nav and
        'fun GoldenBottomBar(' in shell and 'destination.label' in shell and 'destination.iconKey' in shell,
        'golden 5-tab staff bottom bar')
require('home.dashboard',
        all(x in dash for x in ('لوحة اليوم','ابدأ جلسة اليوم','التحفيظ السريع','عرض القائمة كاملة')),
        'golden hero + full-list actions')
require('home.daily_supervisor_board',
        all(x in dash+dvm for x in ('غائب اليوم','متأخر عن الخطة','يحتاج مراجعة','تجاوز هدفه')),
        'five daily intervention buckets')
require('students.list',
        all(x in core for x in ('بحث بالاسم أو الهوية','كل الحلقات','كل الفئات','كل المسارات','ترتيب أبجدي')),
        'golden student filters/search')
require('plans.smart_hifz',
        all(x in core for x in ('الحفظ العام','التثبيت','المقرر الذكي اليوم','متأخر','تجاوز خاص بالطالب')),
        'smart hifz/tathbit progress + assignment + override')
require('plans.guardian_view',
        all(x in core for x in ('خطط الأبناء','مقرر اليوم','guardianChildren','الأرشيف')),
        'privacy-scoped guardian child selector, today assignment, and monthly archive')
require('reports.guardian',
        'تقارير أولياء الأمور' in reports and 'ReportStudioControls' in reports,
        'guardian scoped reports + studio controls')
require('certificates.editor',
        all(x in cert for x in ('نصوص وهوية الشهادة','عنوان الشهادة','المقدمة','الدعاء / الخاتمة','Cairo')),
        'certificate identity/text/style editor')
require('data.export_backup',
        all(x in exports for x in ('تصدير الطلاب Excel','تصدير الطلاب Word','تصدير الطلاب PDF','نسخة احتياطية','exportXlsx','exportWord','exportPdf')),
        'student xlsx/word/pdf + backup')

bad=[c for c in checks if not c[1]]
for n,ok,d in checks: print(('PASS' if ok else 'FAIL'),n,'-',d)
if bad:
    print(f'Release parity gate: {len(checks)-len(bad)}/{len(checks)} PASS')
    sys.exit(1)
print(f'Release parity gate: {len(checks)}/{len(checks)} PASS')
