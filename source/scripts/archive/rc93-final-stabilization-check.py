#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def read(path): return (ROOT/path).read_text(encoding='utf-8')
errors=[]
def need(ok,msg):
    if not ok: errors.append(msg)

core=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
state=read('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanCoreState.kt')
session=read('app/src/main/java/com/mohammedsamir/halaqati/ui/sessions/SessionWorkflowRules.kt')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')

need('COMPLETE_EARLY' in state and 'surplusPages' in state and 'daysRemainingInPlan' in state, 'early-completion/surplus canonical state missing')
need('object SmartPlanUiRules' in read('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanUiRules.kt') if (ROOT/'app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanUiRules.kt').exists() else '', 'SmartPlan visual rules missing')
need('fun summary(' in session and 'fun matchesSearch(' in session, 'session summary/search rules missing')
need('PullToRefreshBox' in core, 'pull-to-refresh integration missing')
need('AnimatedVisibility' in core or 'animateContentSize' in core, 'interactive expansion/motion missing')
need('أتم الخطة مبكرًا' in core and 'الإنجاز الإضافي' in core, 'early completion UI missing')
need('لماذا هذا المقرر؟' in core, 'explainable plan UI missing')
need('حذف التسميع' in core and 'تم حذف التسميع مؤقتًا' in core, 'deferred recitation delete + undo missing')
need('fun deleteRecitation' in repo and 'invalidatePlanSnapshotMemory()' in repo[repo.find('fun deleteRecitation'):repo.find('fun currentUserId')], 'repository recitation delete/rebuild invalidation missing')
need('removePlanRecitationCacheRow' in repo[repo.find('fun deleteRecitation'):repo.find('fun currentUserId')], 'deleted recitation must be removed from plan-history cache before rebuild')
need('recitation-delete:' in repo[repo.find('private fun planRecitations'):repo.find('private fun cachedPlanEvents')], 'pending recitation delete tombstone missing from plan-history overlay')
need('upsertPlanRecitationCacheRow' in repo[repo.find('fun saveRecitationLocalFirst'):repo.find('fun undoPendingQuickSave')], 'save/edit recitation must update plan-history cache for stable rebuilds')
undo_slice=repo[repo.find('fun undoPendingQuickSave'):repo.find('fun currentUserId')]
need(undo_slice.count('removePlanRecitationCacheRow') >= 2, 'undo must remove optimistic recitation from plan-history cache')
need('SessionSummaryStrip' in core, 'session close/summary strip missing')
need('SmartPlanOverviewCard' in core or 'SmartPlanHeroCard' in core, 'premium Smart Plan overview redesign missing')
need('FontWeight.ExtraBold' in core, 'student hierarchy emphasis missing')

if errors:
    print('RC9.3 Final Stabilization gate FAILED')
    for e in errors: print(' -',e)
    raise SystemExit(1)
print('RC9.3 Final Stabilization gate PASS')
