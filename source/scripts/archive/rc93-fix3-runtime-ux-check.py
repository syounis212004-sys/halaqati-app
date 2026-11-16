#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def r(p): return (ROOT/p).read_text(encoding='utf-8')
errors=[]
def need(ok,msg):
    if not ok: errors.append(msg)
repo=r('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
appvm=r('app/src/main/java/com/mohammedsamir/halaqati/ui/AppViewModel.kt')
dash=r('app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DashboardViewModel.kt')
core=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
sync=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SyncCenterScreen.kt')
rules=r('app/src/main/java/com/mohammedsamir/halaqati/data/RuntimeStabilityRules.kt')
ui=r('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanUiRules.kt')
queue=r('app/src/main/java/com/mohammedsamir/halaqati/data/OfflineQueue.kt')
db=r('app/src/main/java/com/mohammedsamir/halaqati/data/OfflineDatabase.kt')

need('fun cachedProfile()' in repo and 'repo.cachedProfile()' in appvm, 'startup cache-first profile path missing')
need(appvm.find('repo.cachedProfile()') < appvm.find('repo.profileWithRefresh()'), 'cached profile must be published before network refresh')
need('milestoneEventKey' in rules and 'RuntimeStabilityRules.milestoneEventKey' in repo, 'stable milestone idempotency key missing')
need('queueSnapshot(2000)' in repo[repo.find('private fun planEvents'):repo.find('private fun defaultPlanRouteDirection')], 'pending plan events are not overlaid into Smart Plan calculation')
need('upsertPlanEventCache' in repo and 'client_event_id' in repo, 'optimistic plan-event cache missing')
need('catch (e: CancellationException)' in core and 'throw e' in core[core.find('catch (e: CancellationException)'):core.find('catch (e: CancellationException)')+120], 'Compose cancellation must not be surfaced as an error')
need('localMutationEpoch' in repo and 'repository.localMutationEpoch.collectLatest' in dash, 'dashboard local mutation refresh bridge missing')
need('attendanceForSession(session.id, allowNetwork = false)' in dash, 'dashboard local attendance reconciliation must not wait for network')
need('lastError=:error' in db and 'fun fail(id: Long, ownerUserId: String, error: String)' in queue, 'pending sync failures must retain last error')
need('pendingRows = rows.count' in sync and 'blockedRows = rows.count' in sync, 'sync center counters must derive from visible outbox source')
need('آخر محاولة:' in sync, 'pending retry diagnostic missing from sync center')
need('showFilters' in core and 'FilterAlt' in core and 'فلترة الخطط' in core, 'compact Smart Plan filter sheet missing')
need('maxLines = 2' in core[core.find('private fun SmartPlanOverviewCard'):core.find('private fun PlanHealthBadge')], 'student full name must support two lines')
for label in ('ممتاز','جيد جدًا','جيد','يحتاج متابعة'):
    need(label in ui and label in core, f'legacy performance band missing: {label}')
need('performanceBand' in ui and 'schedulePerformancePercent' in ui, 'schedule-based performance classification missing')

if errors:
    print('RC9.3 FIX3 Runtime & UX gate FAILED')
    for e in errors: print(' -',e)
    raise SystemExit(1)
print('RC9.3 FIX3 Runtime & UX gate PASS')
