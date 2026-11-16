#!/usr/bin/env python3
"""RC42 static gate: matrix recitation log, quick-add sheet, per-account logos, observer detail, insights page."""
import sys
from pathlib import Path
B = Path('app/src/main/java/com/mohammedsamir/halaqati')
def t(p): return (B / p).read_text(encoding='utf-8')
errors = []
if 'object RecitationMatrix' not in t('documents/RecitationMatrix.kt'): errors.append('matrix logic missing')
if 'RecitationMatrix.build(entries, ' not in t('documents/ReportHtmlRenderer.kt') or 'rm-table' not in t('documents/ReportHtmlRenderer.kt'): errors.append('recitation report must be a matrix')
if 'matrixTable(detailRows, absenceRows)' not in t('ui/screens/ReportsAndTools.kt'): errors.append('word/excel export must use the matrix')
if 'fun QuickAddSheet(' not in t('ui/components/QuickAddSheet.kt') or 'quickAddOpen = true' not in t('ui/HalaqatiApp.kt'): errors.append('quick add sheet missing')
if 'LogoPolicy.builtIn' not in t('documents/ReportHtmlRenderer.kt') and 'builtInLogos' not in t('documents/ReportHtmlRenderer.kt'): errors.append('report logos must be per account')
if 'LogoPolicy.builtIn' not in t('documents/CertificateHtmlRenderer.kt'): errors.append('certificate logos must be per account')
if 'rc42_observer_student_detail' not in t('data/HalaqatiRepository.kt') or 'breakdownError' not in t('ui/screens/ObserverScreens.kt'): errors.append('observer detail / error surfacing missing')
if 'fun InsightsScreen(' not in t('ui/screens/InsightsScreen.kt') or '"insights"' not in t('ui/HalaqatiApp.kt'): errors.append('insights page missing')
if 'InsightMemo' not in t('ui/components/OrgInsightCards.kt'): errors.append('insight cards must remember their last result')
if not Path('migration/20261030_v2780_rc42_observer_fix_and_detail.sql').exists(): errors.append('migration record missing')
if errors:
    print('RC42 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC42 gate passed')
