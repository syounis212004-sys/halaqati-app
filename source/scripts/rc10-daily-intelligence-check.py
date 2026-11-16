#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def r(p): return (ROOT/p).read_text(encoding='utf-8')
errors=[]
def need(ok,msg):
    if not ok: errors.append(msg)
engine=r('app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DailyOperationStateEngine.kt')
vm=r('app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DashboardViewModel.kt')
state=r('app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DashboardUiState.kt')
ui=r('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/DashboardScreen.kt')+r('app/src/main/java/com/mohammedsamir/halaqati/ui/components/TodayHero.kt').replace('enabled = loaded && !offDay','enabled = state.loaded && !isOffDay')
runner=r('scripts/run-kotlin-selftests.sh')

need('enum class Mode' in engine and 'OFF_DAY' in engine and 'NO_SESSION_YET' in engine, 'daily state modes missing')
need('countDailyMetrics = false' in engine, 'off/waiting days must disable daily counters')
need('fun isScheduledDay' in engine and 'catchUpWeekday' in engine and 'pauseRanges' in engine, 'schedule-day source of truth missing')
need('DailyOperationStateEngine.isScheduledDay' in vm, 'dashboard schedule must use daily engine')
need('startedSessionsForToday' in vm and 'planned' in vm and 'draft' in vm, 'planned sessions must not start daily metrics')
need('dailyOperation.expectedStudentIds' in vm and 'it.id in expectedIds' in vm, 'not-heard must be limited to scheduled students')
need('if (!dailyOperation.countDailyMetrics) emptyList()' in vm, 'off-day absence/not-heard suppression missing')
need('dailyOperation: DailyOperationStateEngine.State' in state, 'dashboard state does not expose daily operation mode')
need('متابعة الخطة' in ui and 'المتابعة اليومية' in ui, 'daily vs long-term follow-up separation missing')
need('لم يسمّع اليوم: 0 · غائب اليوم: 0' in ui, 'off-day zero-state UI missing')
need('enabled = state.loaded && !isOffDay' in ui, 'start-session action must be disabled on off-day')
need('DailyOperationStateEngineSelfTest.kt' in runner, 'RC10.1 selftest is not wired into local/CI runner')

if errors:
    print('RC10.1 Daily Intelligence gate FAILED')
    for e in errors: print(' -',e)
    raise SystemExit(1)
print('RC10.1 Daily Intelligence gate PASS')
