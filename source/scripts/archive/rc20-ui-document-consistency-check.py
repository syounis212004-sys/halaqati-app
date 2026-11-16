#!/usr/bin/env python3
from pathlib import Path
import json, sys
root=Path(__file__).resolve().parents[1]
errors=[]
def read(rel):
    p=root/rel
    if not p.exists(): errors.append(f'missing {rel}'); return ''
    return p.read_text(encoding='utf-8',errors='ignore')
def need(c,m):
    if not c: errors.append(m)

build=read('app/build.gradle.kts')
drawer=read('app/src/main/java/com/mohammedsamir/halaqati/ui/components/GoldenShell.kt')
adaptive=read('app/src/main/java/com/mohammedsamir/halaqati/ui/components/AdaptiveControls.kt')
reports=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
report_controls=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportStudioControls.kt')
core=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
mgmt=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt')
report_css=read('app/src/main/assets/documents/report.css')
cert_css=read('app/src/main/assets/documents/certificate.css')
report_renderer=read('app/src/main/java/com/mohammedsamir/halaqati/documents/ReportHtmlRenderer.kt')
cert_renderer=read('app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt')
mig=read('migration/20261002_v2560_rc20_ui_document_consistency.sql')
update_publish=read('scripts/make-update-publish.py')
contract=json.loads(read('migration/source-contract.json') or '{}')

need('versionCode = 25600' in build and 'versionName = "2.56.0"' in build,'v2.56.0 version')
need(contract.get('native_version')=='2.56.0','source contract v2.56.0')
need('MaterialTheme.colorScheme.primaryContainer' in drawer,'drawer follows app theme')
need('۞' not in drawer,'obsolete drawer ornament removed')
need('fun HalaqatiDatePickerDialog' in adaptive and 'fun HalaqatiDateRangePickerDialog' in adaptive and 'fun HalaqatiMonthPickerDialog' in adaptive,'shared date/day/month/range picker controls')
need('showDayPicker' in reports and 'showMonthPicker' in reports and 'showRangePicker' in reports,'report picker state wiring')
need('HalaqatiDateRangePickerDialog' in reports and 'HalaqatiMonthPickerDialog' in reports,'report range/month picker dialogs')
need('.student-report-card.no-photo' in report_css,'no-photo report layout')
need('grid-template-columns:repeat(3,minmax(0,1fr))' in report_css,'readable 3-column student metadata')
need('.student-info-grid{grid-template-columns:repeat(6' not in report_css,'old six-column student metadata removed')
need('student-report-name' in report_css and 'no-photo' in report_renderer,'long student name report layout')
need('reportFontStack' in report_renderer and 'body{font-family:${reportFontStack(style.fontFamily)}!important}' in report_renderer,'real report font stack applied to document body')
need('if (track == "all" && sr.isEmpty() && dr.isEmpty() && nr.isEmpty()) continue' in report_renderer,'empty report track sections skipped')
need("listOf(\"Cairo\" to \"Cairo\", \"sans-serif\" to \"Sans\", \"serif\" to \"Serif\", \"monospace\" to \"Mono\")" in report_controls,'report font choices map to real stacks')
need('certificateFontStack' in cert_renderer and '--cert-font' in cert_css,'certificate HTML font engine')
need('resolveCertificateTypeface' in reports and '"monospace"' in reports,'certificate raster font mapping')
need('students.filter { it.status == "active" && it.halaqaId == session.halaqaId }' in core,'session roster exact halaqa isolation')
need('firstHalaqaId()' not in core,'session creation no longer silently uses first halaqa')
need('HalaqatiLazySelectSheet' in core and 'todayHalaqaPicker' in core,'multi-halaqa today-session chooser')
need('private fun NewSessionDialog' in core and 'HalaqatiDateField("تاريخ الجلسة"' in core,'new/edit session date pickers')
need('private fun StudentNoteDialog' in core and '.verticalScroll(rememberScrollState()).imePadding()' in core,'student note keyboard-safe scrolling')
need('archived_at' in mig and 'halaqas_archived_at_idx' in mig,'halaqa soft archive schema')
need('أرشفة الحلقة' in mgmt and 'خيارات متقدمة: حذف نهائي' in mgmt,'archive-first halaqa management')
need('horizontalScroll(' not in reports,'report/certificate studio has no hidden horizontal action strips')
need('horizontalScroll(' not in core,'core screens have no hidden horizontal action strips')
# One horizontally scrolling chart in ManagementScreens is intentional; other UI action strips are not.
need(mgmt.count('horizontalScroll(') <= 1 and 'private fun GuardianTrend' in mgmt,'only intentional guardian trend chart may scroll horizontally')
need('Halaqati_Backup_' in reports and '2.54.2' not in reports,'backup filename no longer hardcodes obsolete release')
need("'versionName':'2.56.0'" in update_publish and "'versionCode':25600" in update_publish,'direct-update manifest generator matches v2.56.0')
need((root/'scripts/rc20-report-layout-visual-check.py').exists(),'RC20 no-photo visual regression gate exists')

if errors:
    print('RC20 UI + Document Consistency gate FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RC20 UI + Document Consistency gate PASS')
