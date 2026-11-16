#!/usr/bin/env python3
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
checks=[]
def need(name, cond, detail):
    checks.append((name, bool(cond), detail))

def text(rel):
    return (ROOT/rel).read_text(encoding='utf-8')

queue=text('app/src/main/java/com/mohammedsamir/halaqati/data/OfflineQueue.kt')
repo=text('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
core=text('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
reports=text('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
gradle=text('app/build.gradle.kts')

need('RC10 pending quick undo queue primitive', 'fun discardDedupe(' in queue, 'OfflineQueue must expose account-scoped dedupe cancellation')
need('RC10 repository undo contract', 'fun undoPendingQuickSave(' in repo, 'repository must atomically cancel the immediate pending attendance/recitation pair')
need('RC10 recitation id returned', 'fun saveRecitationLocalFirst(draft: RecitationDraft): String' in repo, 'quick undo needs the exact generated recitation id')
need('RC10 quick UI undo', 'تراجع عن آخر حفظ' in core and 'undoPendingQuickSave' in core, 'Quick Memorization must expose immediate Undo')
need('RC13 single scroll surface', 'ReportStudioScrollableSurface' in reports and 'REPORT_STUDIO_LAST_OPTION_TAG' in reports, 'Report Studio controls + preview + results must share one scroll surface')
uitest=ROOT/'app/src/androidTest/java/com/mohammedsamir/halaqati/ui/screens/ReportStudioScrollTest.kt'
need('RC13 swipe UI regression test', uitest.exists() and ('swipeUp' in uitest.read_text(encoding='utf-8') if uitest.exists() else False), 'instrumented regression test must swipe to the final report option')
need('RC13 android test runner', 'testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"' in gradle and 'ui-test-junit4' in gradle, 'Compose instrumentation test dependencies must compile')
profile=ROOT/'app/src/main/baseline-prof.txt'
need('RC15 baseline profile', profile.exists() and profile.stat().st_size > 100, 'ship baseline profile hints for hot startup/navigation paths')
need('Stable version label source gate', (ROOT/'migration/POWER_STABLE_246_AR.md').exists(), 'stable acceptance contract document must exist')

failed=[x for x in checks if not x[1]]
for name, ok, detail in checks:
    print(('PASS' if ok else 'FAIL') + ': ' + name + ('' if ok else ' — '+detail))
print(f'\nStable source gate: {len(checks)-len(failed)}/{len(checks)}')
if failed:
    sys.exit(1)
