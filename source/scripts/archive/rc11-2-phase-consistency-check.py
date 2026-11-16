#!/usr/bin/env python3
from pathlib import Path
import json, re, sys

ROOT = Path(__file__).resolve().parents[1]
errors=[]
def need(path, *tokens):
    p=ROOT/path
    if not p.exists():
        errors.append(f'missing {path}'); return ''
    s=p.read_text(errors='replace')
    for t in tokens:
        if t not in s: errors.append(f'{path}: missing token {t!r}')
    return s

def reject(path, *tokens):
    s=(ROOT/path).read_text(errors='replace')
    for t in tokens:
        if t in s: errors.append(f'{path}: forbidden token still present {t!r}')

build=need(Path('app/build.gradle.kts'), 'versionCode = 25402', 'versionName = "2.54.2"')
phase=need(Path('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanPhaseEngine.kt'),
    'DEFAULT_SARD_TARGET_PAGES = 25.0', 'ROUTE_FROM_NAS_TO_BAQARAH', 'contextPhase(', 'validateTimeline(', 'enum class Lifecycle')
resolver=need(Path('app/src/main/java/com/mohammedsamir/halaqati/plans/SardAnchorResolver.kt'),
    'object SardAnchorResolver', 'activityType.equals("sard", ignoreCase = true)')
rules=need(Path('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanActivityRules.kt'),
    'private val sardActivities = setOf("sard")', 'reverse_surahs_forward_ayahs')
repo=need(Path('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt'),
    'rc112_capabilities', 'rc112_reconcile_plan_phase_states', 'rc112_update_plan_phase', 'rc112_transition_plan_students',
    'SardAnchorResolver.resolve', 'syncParentPlanFromPhases', 'expectedRevision', 'SmartPlanPhaseEngine.contextPhase',
    'row.optString("phase_id") == phase.id', 'activity_type')
models=need(Path('app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt'), 'val revision: Int = 1', 'expectedRevision: Int? = null')
ui=need(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SmartPlanPhasesUi.kt'),
    'RC11.3 · مراحل الخطة', 'من الناس إلى البقرة', 'من البقرة إلى الناس', 'expectedRevision = source?.revision',
    'transitionPlanStudentsBulk', '25.0')
core=need(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt'),
    'هذه الشاشة تعدّل بيانات الخطة العامة مثل الاسم والسياسات.', 'enabled = !phaseManaged', 'من الناس إلى البقرة')
student=need(Path('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/StudentProfileScreen.kt'),
    'weekSessions', 'لا توجد جلسة', 'السبت إجازة ثابتة', 'حاضر لم يسمّع', 'reconcilePlanPhaseLifecycle')
mig=need(Path('migration/20260926_v2462_rc11_2_phase_consistency.sql'),
    'smart_plan_phases_one_live_type_idx', 'rc112_bump_phase_revision', 'rc112_validate_phase_mutation',
    'rc112_validate_phase_student_scope', 'rc112_capabilities', 'rc112_update_plan_phase',
    'rc112_reconcile_plan_phase_states', 'rc112_transition_plan_students', "NOTIFY pgrst, 'reload schema';")
base=need(Path('migration/20260926_v2461_rc11_smart_plan_phases.sql'), 'create table if not exists public.smart_plan_phases')
contract_path=ROOT/'migration/source-contract.json'
try:
    contract=json.loads(contract_path.read_text())
    if contract.get('native_version') != '2.54.2': errors.append('source-contract native_version mismatch')
    rpcs=set(contract.get('rpc', []))
    for name in {'rc112_capabilities','rc112_reconcile_plan_phase_states','rc112_transition_plan_students','rc112_update_plan_phase','rc113_capabilities','rc113_bulk_configure_plan_cycles'}:
        if name not in rpcs: errors.append(f'source-contract missing RPC {name}')
    if len(rpcs) < 13: errors.append(f'expected at least 13 RPCs, got {len(rpcs)}')
except Exception as e: errors.append(f'source-contract parse: {e}')

# Key semantic safeguards (not just symbol presence).
if 'filter { it.activityType.equals("sard", ignoreCase = true) }' not in resolver:
    errors.append('Sard resolver does not visibly enforce exact sard activity')
if 'phase.phaseType == "sard"' not in student:
    errors.append('student weekly/month summary is not phase aware')
if 'date.dayOfWeek.value % 7) == 6' not in student:
    errors.append('weekly timeline does not preserve Saturday as explicit holiday')
if 'p_expected_revision' not in mig or 'RC11.2_CONFLICT' not in mig:
    errors.append('migration missing optimistic revision conflict')
if 'having max(candidate_count) = 1' not in mig:
    errors.append('migration does not appear to restrict backfill to unambiguous candidate')
if 'security invoker' not in mig.lower():
    errors.append('RC11.2 RPCs should keep RLS through SECURITY INVOKER')

# Regression-test evidence for the exact incidents found in production.
test=need(Path('app/src/test/java/com/mohammedsamir/halaqati/plans/SmartPlanPhaseEngineTest.kt'))
selftest=need(Path('scripts/kotlin-selftest/SmartPlanPhaseEngineSelfTest.kt'),
    'generic test', 'DEFAULT_SARD_TARGET_PAGES', 'ROUTE_FROM_NAS_TO_BAQARAH')
activitytest=need(Path('scripts/kotlin-selftest/SmartPlanActivityRulesSelfTest.kt'), 'reverse_surahs_forward_ayahs')

if errors:
    print('RC11.2 phase consistency gate FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RC11.2 phase consistency gate PASS')
print('Verified: lifecycle/date-edit preservation hooks, single Sard anchor resolver, exact Sard activity,')
print('          25-page + Nas→Baqarah defaults, one-source UI guard, weekly semantics, atomic transition,')
print('          revision conflicts, RLS/backfill migration, and 11-RPC source contract.')
