from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
repo=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text(encoding='utf-8')
engine=(root/'app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt').read_text(encoding='utf-8')
gradle=(root/'app/build.gradle.kts').read_text(encoding='utf-8')
mig=(root/'migration/20261002_v2551_rc19_1_month_phase_isolation_hotfix.sql').read_text(encoding='utf-8')
vm=re.search(r'versionName\s*=\s*"([0-9.]+)"',gradle)
vc=re.search(r'versionCode\s*=\s*(\d+)',gradle)
def vt(v):
    try:return tuple(map(int,v.split('.')[:3]))
    except:return (0,0,0)
checks={
 'tagged phase still date scoped': 'rec.taggedPhaseId == activePhase.id -> inEffectiveDates' in repo,
 'plan events parse event_date': 'date = row.optString("event_date")' in repo,
 'events scoped to effective dates': 'val scopedEvents = events.filter' in engine and 'pendingMilestone(plan, completion, scopedEvents)' in engine,
 'migration isolates cross-month phases': 'rc191_isolate_cross_month_phases' in mig and 'phase_order=phase_order+100' in mig,
 'version remains RC19.1-compatible': bool(vm and vt(vm.group(1)) >= (2,55,1) and vc and int(vc.group(1)) >= 25501),
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'), k)
if failed: raise SystemExit('RC19.1 gate failed: '+', '.join(failed))
print('RC19.1 month isolation gate PASS')
