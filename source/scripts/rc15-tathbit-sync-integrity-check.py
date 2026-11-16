#!/usr/bin/env python3
"""RC15 gate: tathbit first-class activity, plan/phase integrity, sync error taxonomy."""
from pathlib import Path
root=Path(__file__).resolve().parents[1]; J=root/'app/src/main/java/com/mohammedsamir/halaqati'
errors=[]
def need(path,*toks):
    t=(J/path if not str(path).startswith('migration') and not str(path).startswith('app') else root/path).read_text(encoding='utf-8')
    for tok in toks:
        if tok not in t: errors.append(f'{path}: missing {tok!r}')
    return t
need('data/StudentTrackRules.kt','object TrackActivityPolicy','fun recitationActivityFor','fun planTypeFor')
need('data/SyncErrorRules.kt','"23514"','"42501"','fun sanitizeSmartPlan','PARENT_MISSING')
need('data/SupabaseApi.kt','val sqlState: String? = null')
repo=need('data/HalaqatiRepository.kt','failedPlanIds','SyncErrorRules.sanitizeSmartPlan','TrackActivityPolicy.planTypeFor','"hifz", "sard", "tathbit"','rc15_create_smart_plan_bundle','rc15_tathbit_consistency_issues')
need('plans/SmartPlanPhaseEngine.kt','TYPE_TATHBIT','tathbit_mixed')
need('ui/screens/SyncCenterScreen.kt','kotlinx.coroutines.CancellationException','tathbitConsistencyIssues')
core=(J/'ui/screens/CoreScreens.kt').read_text(encoding='utf-8')
if '"tathbit" -> "review_far"' in core or '"tathbit" -> "review_far"' in repo: errors.append('tathbit is still written as review_far')
need('migration/20260928_v2500_rc15_tathbit_first_class_sync_integrity.sql',"add value if not exists 'tathbit'","('hifz','sard','tathbit')",'rc15_create_smart_plan_bundle','security invoker','rc15_tathbit_consistency_issues')
if errors:
    print('RC15 gate FAILED'); [print(' -',e) for e in errors]; raise SystemExit(1)
print('RC15 Tathbit + Sync Integrity gate PASS')
