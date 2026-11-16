#!/usr/bin/env python3
"""RC43 static gate: recitation grid redesign, weekly summary, absences, bulk end of sard, month picker, tathbit line fallback."""
import sys
from pathlib import Path
B = Path('app/src/main/java/com/mohammedsamir/halaqati')
def t(p): return (B / p).read_text(encoding='utf-8')
errors = []
r = t('documents/ReportHtmlRenderer.kt')
if '" ← "' not in r or '→' in r.split('private fun recitationRange')[1].split('private fun headerSubtitle')[0]: errors.append('ranges must use the left arrow (RTL)')
for tok in ('rm-abs', 'weeklySection', 'rm-legend', '<bdi>', ':not(.rm-table)', 'rm-total'):
    if tok not in r: errors.append('renderer missing ' + tok)
m = t('documents/RecitationMatrix.kt')
if 'class Absence' not in m or 'fun weekly(' not in m: errors.append('matrix must support absences and weekly')
if 'fun reportAbsences(' not in t('data/HalaqatiRepository.kt'): errors.append('absence query missing')
ui = t('ui/screens/ReportsAndTools.kt')
if 'weeklyTable(detailRows)' not in ui or 'ReportDocumentType.WEEKLY' not in ui: errors.append('weekly report not wired')
if 'إنهاء السرد لعدة طلاب' not in t('ui/screens/SmartPlanPhasesUi.kt'): errors.append('bulk end of sard missing')
core = t('ui/screens/CoreScreens.kt')
if 'rollError' not in core or 'rollNext' not in core: errors.append('month picker / in-dialog error missing')
if 'previewPagesFrom' not in t('data/HalaqatiRepository.kt').split('private fun canonicalPlanLineKeys')[1].split('private fun PlanRow.toEnginePlan')[0]: errors.append('tathbit line-key fallback missing')
if not Path('migration/20261101_v2790_rc43_month_roll_fixes.sql').exists(): errors.append('migration record missing')
if errors:
    print('RC43 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC43 gate passed')
