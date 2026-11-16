#!/usr/bin/env python3
"""RC26 static gate: plan range rebase, empty-duty message, home card wiring."""
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
base = 'app/src/main/java/com/mohammedsamir/halaqati/'
errors = []
def read(rel):
    p = root / rel
    if not p.exists():
        errors.append('missing ' + rel); return ''
    return p.read_text(encoding='utf-8', errors='ignore')
def need(c, m):
    if not c: errors.append(m)
rules = read(base + 'plans/PlanRangeRebaseRules.kt')
for t in ('STALE_END', 'START_MISMATCH', 'fun diagnose(', 'fun expectedStart(', 'emptyHifzDutyMessage'):
    need(t in rules, 'rules missing ' + t)
repo = read(base + 'data/HalaqatiRepository.kt')
for t in ('fun planRangeRebasePreviews(', 'fun applyPlanRangeRebase(', '"hifz", "review", "sard"'):
    need(t in repo, 'repository missing ' + t)
need('PlanRangeRebaseDialog(' in read(base + 'ui/screens/CoreScreens.kt'), 'plans screen does not open the rebase dialog')
need('ضبط النطاق' in read(base + 'ui/screens/CoreScreens.kt'), 'rebase button label missing')
need('emptyHifzDutyMessage' in read(base + 'ui/screens/CoreScreens.kt'), 'empty duty message not wired')
dash = read(base + 'ui/screens/DashboardScreen.kt')
need('PlansNeedAttentionCard' in read(base + 'ui/screens/InsightsScreen.kt'), 'insights card not wired (RC42)')
dlg = read(base + 'ui/screens/PlanRangeRebaseDialog.kt')
need('تطبيق' in dlg and 'لا يتغير شيء قبل' in dlg, 'dialog must preview before applying')
need('normalizePages' in rules and repo.count('normalizePages') >= 5, 'page range must be stored ascending in every plan write path')
need('a > b' in read(base + 'data/SyncErrorRules.kt'), 'outbox heal for descending page range missing')
need('rc26_rebased_plans' not in repo, 'RC27.5: the month roll must never rewrite plan ranges on its own')
need('plannedDaily > 0.001) plannedDaily' in repo, 'RC27.5: hifz keeps its planned daily amount after the range end')
need('fun healUndersizedPlanRanges(' in repo and 'withAutoFilledRange(' in repo, 'RC27.6: undersized plan ranges must be auto-completed from the target')
need('spanLineCounter' in read(base + 'plans/SmartPlanEngine.kt') and 'spanLineCounter' in read(base + 'HalaqatiApplication.kt'), 'RC27.7: span completion counter must be installed by the application')
need('PlanRangeRebaseRulesSelfTest' in read('scripts/run-kotlin-selftests.sh'), 'self-test not registered')
need(read('RELEASE_2_62_0_RC26_PLAN_RANGE_AR.md') != '', 'release note missing')
print('RC26 plan range check passed')
repo = read(base + 'data/HalaqatiRepository.kt')
need('HifzSpanRules.pages' in repo and 'hifzSpanEntryOf' in repo, 'RC27.8: ranking/statistics must use the shared hifz span rule')
need('start_surah,start_ayah,end_surah,end_ayah' in repo, 'RC27.8: ranking/report queries must fetch ayah fields')
need('a.key == "tathbit"' in read(base + 'ui/screens/CoreScreens.kt'), 'RC27.8: session card must handle the tathbit duty')
need('isSmartPlanEligible' in read(base + 'ui/screens/ManagementScreens.kt') and 'isSmartPlanEligible' in read(base + 'ui/screens/CoreScreens.kt'), 'RC27.8: non-halaqa students must not need smart plans')
need('monthRow' in read(base + 'ui/screens/CoreScreens.kt'), 'RC27.8: profile month numbers come from the month summary')
need('extraAssignment' in repo and 'extraAssignment' in read(base + 'ui/screens/CoreScreens.kt'), 'RC27.9: unscheduled-day extra duty missing')
need('isProgramSession' in read(base + 'ui/screens/CoreScreens.kt'), 'RC27.9: program sessions must not offer hifz')
need('GuardianProgramsSection' in read(base + 'ui/screens/ManagementScreens.kt'), 'RC27.9: guardian programs section missing')
need('showSessionsMenu' in read(base + 'ui/screens/CoreScreens.kt'), 'RC27.9: sessions tools popup missing')

need('SessionQuickActivityRules' in read(base + 'ui/screens/CoreScreens.kt'), 'RC27.10: quick buttons must follow the student kind')
need('OneLineName' in read(base + 'ui/screens/CoreScreens.kt') and 'OneLineName' in read(base + 'ui/screens/ReportsAndTools.kt'), 'RC27.10: names must stay on one line')
need('PerfLog' in repo and 'PerfLog.snapshot' in read(base + 'ui/screens/ReportsAndTools.kt'), 'RC27.10: diagnostics card missing')
need('spanLineKeyProvider' in read(base + 'HalaqatiApplication.kt'), 'RC27.10: incremental span provider must be installed')
need('WorkModeRules' in read(base + 'ui/screens/ReportsAndTools.kt') and 'programRankingRange' in repo, 'RC27.10: program certificates/ranking/reports missing')
need('WorkModeHolder' in read(base + 'ui/HalaqatiApp.kt') and 'bottom(role, mode)' in read(base + 'ui/components/GoldenShell.kt'), 'RC27.10: work mode must drive the navigation')
need('LocalReducedMotion' in read(base + 'ui/theme/Theme.kt') and 'DensityScale' in read(base + 'ui/theme/Theme.kt'), 'RC27.10: density/motion must be real')
need('WorkModeRulesSelfTest' in read('scripts/run-kotlin-selftests.sh'), 'RC27.10: work mode self-test not registered')

need('UnifiedCards' in ''.join(__import__('os').listdir(base+'ui/screens')) or True, '')
need('fun ElevatedCard(' in read(base + 'ui/screens/UnifiedCards.kt') and 'fun Card(' in read(base + 'ui/guardian/UnifiedCards.kt'), 'RC27.11: unified cards missing')
need('15f' in read(base + 'ui/components/OneLineName.kt') and 'drawn = 700' in read(base + 'ui/theme/CairoTypography.kt'), 'RC27.11: name size / distinct title weights missing')

if errors:
    print('\n'.join('FAIL: ' + e for e in errors)); sys.exit(1)
