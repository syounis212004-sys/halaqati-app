#!/usr/bin/env python3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
repo = (root/'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text()
phase_ui = (root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SmartPlanBulkCycleUi.kt').read_text()
core = (root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt').read_text()
engine = (root/'app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt').read_text()
activity = (root/'app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanActivityRules.kt').read_text()
dash = (root/'app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DashboardViewModel.kt').read_text()
mig = (root/'migration/20260927_v2463_rc11_3_bulk_cycle_stability.sql').read_text()

def need(cond, msg):
    if not cond: raise SystemExit('RC11.3 FAIL: '+msg)

need('versionName = "2.54.2"' in (root/'app/build.gradle.kts').read_text(), 'version name')
need('SmartPlanBulkCycleManagerSheet' in phase_ui and 'إدارة جماعية للحفظ والسرد' in phase_ui, 'halaqa-wide bulk UI')
for label in ('بداية الحفظ','نهاية الحفظ','بداية السرد','نهاية السرد'):
    need(label in phase_ui, 'missing date control '+label)
need('هدف السرد الموحد' in phase_ui or 'هدف السرد' in phase_ui, 'unified Sard target')
need('ROUTE_FROM_NAS_TO_BAQARAH' in phase_ui, 'default/editable Nas to Baqarah')
need('تخصيص هذا الطالب فقط' in phase_ui, 'per-student customization')
need('hifzStartDate' in repo and 'rc113_bulk_configure_plan_cycles' in repo, 'bulk repository contract')
need('synchronized(pushLock)' in repo and 'queue.discardDedupe(owner, dedupe)' in repo, 'serialized RPC/outbox success cleanup')
need('اليوم السبت إجازة' in repo, 'phantom Saturday session guard')
need('sardRouteDirection = if (phase.type == "sard") phaseRoute' in repo, 'phase Sard route propagation')
need('terminalSurah("sard"' not in activity or 'return' in activity, 'activity helper readable')
need('fun terminalSurah' in activity and 'activity in setOf("hifz", "sard") -> 2' in activity, 'Nas to Baqarah terminal')
need('"sard" to setOf("sard")' in engine and '"sard" to setOf("sard", "test")' not in engine, 'test excluded from Sard accounting')
need('"test" -> "hifz"' in core, 'generic test not bound to Sard snapshot')
need('phaseAwareSnapshots' in dash, 'dashboard phase-aware activity')
for token in ('rc113_bulk_configure_plan_cycles','rc112_transition_plan_students','rc112_reconcile_plan_phase_states','students_track_code_check','sard_route_direction'):
    need(token in mig, 'migration missing '+token)
need("set statement_timeout='30s'" in mig, 'bounded bulk DB timeout')
need("track_code in ('general_hifz','tathbit')" in mig, 'two-track DB invariant')
need("phase_started" in mig and "sard_start<=current_date" in mig, 'future Sard lifecycle')
need("re.phase_id is null" in mig and "c.matches=1" in mig, 'safe legacy phase backfill')
print('RC11.3 Unified Cycle & Stability gate PASS')
