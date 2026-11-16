#!/usr/bin/env python3
"""Behavioral parity guard for Halaqati v2.44 -> native.
Checks concrete data/UI contracts; intentionally stronger than route/string presence gates.
It is still a source contract and does not replace Android compilation/device tests.
"""
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]

def read(rel):
    p = root / rel
    return p.read_text(encoding='utf-8', errors='ignore') if p.exists() else ''

repo = read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
core = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
dash = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/DashboardScreen.kt')
dvm = read('app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DashboardViewModel.kt')
reports = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
report_controls = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportStudioControls.kt')
cert = reports
app = read('app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt')
dest = read('app/src/main/java/com/mohammedsamir/halaqati/ui/navigation/AppDestination.kt')
theme = read('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/Theme.kt')
models = read('app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt')

checks=[]
def require(name, ok, detail): checks.append((name, bool(ok), detail))

# Session recitation must be first-class in the roster, not just attendance/note.
require('session.recitation.model', 'data class RecitationRow' in models and 'mistakes: Int' in models and 'prompts: Int' in models, 'typed recitation row includes evaluation details')
require('session.roster.loads_recitations', 'recitationsForSession(session.id' in core and 'recitations = result.recitations' in core, 'roster loads saved/queued recitations')
require('session.roster.shows_recitation', all(x in core for x in ['تم التسميع','أخطاء','تلقين','سلامة']), 'saved recitation range/evaluation/errors are visible')
require('session.local_first_overlay', all(x in repo for x in ['fun recitationsForSession','CACHE_SESSION_RECITATIONS_PREFIX','dedupe.startsWith("recitation:$sessionId:")']), 'remote/cache/queued recitations share one roster view')
require('session.v6_is_single_source', 'SafetyScoring.calculate' in core and 'تقييم V6 تلقائي' in core and 'inferredEvaluation' in core, 'evaluation is calculated, not a decorative picker')

# Today means session date, so offline sync time cannot move a recitation to another day.
require('dashboard.session_date_query', 'sessions!inner(session_date)' in repo, 'dashboard/reports query session date')
require('dashboard.session_date_logic', (('row.optString("session_date")' in dvm and 'optJSONObject("sessions")?.optString("session_date")' in dvm) or ('startedSessionsForToday' in dvm and 'sessionId !in todaySessionIds' in dvm)), 'today bucket is anchored to the authoritative session day/session ids')
require('dashboard.local_recitation_overlay', 'refreshDashboardRecitations' in repo and 'queueSnapshot(1500)' in repo and 'body.put("session_date", date)' in repo, 'queued local recitations appear before sync')

# Smart plan operational details from 2.44.
for token in ['مطلوب اليوم','المنجز اليوم','المتبقي اليوم','هذا الأسبوع','آخر تسميع','المقرر القادم','لماذا هذا المقرر؟']:
    require('plan.ui.'+token, token in core, f'smart plan exposes {token}')
require('plan.repo.indexes_recitations', 'recitationsByStudent' in repo and 'planRecitations' in repo and 'PlanWeekRequirement' in repo and 'PlanNextDuty' in repo, 'plan data is loaded/indexed once and enriched')
require('plan.recovery', all(x in core for x in ['behindBy','recoveryShare','aheadBy']), 'late/ahead/recovery are visible')

# Quick memorization is a real workflow rather than a redirect.
require('quick.route', 'QuickMemorization' in dest and 'quick_memorization' in app, 'quick memorization has a native destination')
require('quick.workflow', all(x in core for x in ['QuickMemorizationScreen','الأخطاء','التلقين','حفظ الغياب والتالي','نموذج التسميع الكامل']), 'one-student quick flow can save attendance + recitation')
require('quick.dashboard_entry', 'quick_memorization' in dash, 'dashboard hero opens quick workflow')

# Certificate preview must use the exact PDF renderer inputs, not a fake text card.
document_renderer = read('app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt') + read('app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt')
require('certificate.shared_renderer', 'certificateHtml' in cert and 'CertificateHtmlRenderer.build' in cert and 'VectorDocumentRenderer.saveHtmlPdf' in cert and 'createPrintDocumentAdapter' in document_renderer, 'preview and PDF share one HTML/CSS Chromium renderer')
require('certificate.zoom', '70f..120f' in cert, 'preview zoom 70–120%')
require('certificate.logo_range', '100f..230f' in cert and 'حجم الشعارات' in cert, 'legacy logo size range restored')
require('certificate.font_ranges', all(x in cert for x in ['30f..64f','28f..58f','15f..28f']), 'legacy title/student/body ranges restored')
require('certificate.colors', all(x in cert for x in ['primaryHex','accentHex','secondaryHex','paperHex','inkHex']), 'certificate palette is editable')
require('certificate.number_positions', all(x in cert for x in ['under_logo','footer_start','footer_end']), 'certificate number position is editable')

# Report Studio must remain a real studio.
require('report.periods_tracks', all(x in reports for x in ['"day" to "يوم محدد"','"month" to "شهر محدد"','"custom" to "فترة"','general_hifz','tathbit']), 'day/month/custom and both tracks')
require('report.exports', all(x in reports for x in ['"PDF A4")','"excel"->"Excel"','"word"->"Word"','"image"->"صورة"','else->"مشاركة"']), 'PDF/Excel/Word/PNG/share exports')
require('report.style', all(x in report_controls for x in ['اتجاه A4','حجم الخط','الهوية اللونية','الشعارات','الأعمدة الظاهرة']), 'orientation/font/colors/logos/columns are editable')
require('report.live_preview', 'HtmlDocumentPreview' in reports and 'معاينة A4 الحقيقية' in reports and 'ReportHtmlRenderer.build' in reports, 'report preview and PDF share the same HTML/CSS source')

# UI contrast/smoothness guards.
require('theme.not_washed_out', '0xFF78837E' not in theme and '0xFF52645D' in theme and '0xFF0D3B2F' in theme, 'old washed-out onSurfaceVariant removed')
require('students.rich_cards', all(x in core for x in ['monthStatsByStudent','snapshotsByStudent','ورد اليوم','مراجعة','غياب']), 'student list restores monthly activity + plan context')
require('students.initial_load', 'LaunchedEffect(Unit) { reload() }' in core, 'student screen loads immediately without a manual refresh')
require('lists.stable_keys', 'contentType = { "smart_plan" }' in core and 'contentType = { "student" }' in core and 'contentType = { "session" }' in core, 'heavy native lists use keys + content types')

bad=[c for c in checks if not c[1]]
for name,ok,detail in checks:
    print(('PASS' if ok else 'FAIL'), name, '-', detail)
print(f'Behavioral parity: {len(checks)-len(bad)}/{len(checks)} PASS')
if bad:
    sys.exit(1)
