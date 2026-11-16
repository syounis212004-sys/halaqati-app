#!/usr/bin/env python3
from pathlib import Path
import json, sys
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def need(rel,*tokens):
    p=ROOT/rel
    if not p.exists():
        errors.append(f'missing {rel}'); return ''
    s=p.read_text(encoding='utf-8',errors='ignore')
    for t in tokens:
        if t not in s: errors.append(f'{rel}: missing {t}')
    return s

build=need('app/build.gradle.kts','versionCode = 25402','versionName = "2.54.2"')
engine=need('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanPhaseEngine.kt',
    'TYPE_HIFZ = "hifz"','TYPE_SARD = "sard"','GOAL_OPEN = "open"','DISTRIBUTION_FLEXIBLE',
    'latestSardAnchor','effectiveActivePhase','previousShortfall','endDateOverride','carriedShortfallPages','configuredStart','usesHistoricalSardAnchor')
models=need('app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt',
    'data class SmartPlanPhaseRow','data class SmartPlanPhaseStateRow','data class SmartPlanPhaseTemplateRow',
    'smartPlanPhaseId')
repo=need('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt',
    'fun planPhases(','fun planPhaseStates(','fun savePlanPhase(','fun transitionPlanStudentPhase(',
    'fun completePlanPhaseForStudent(','fun applyPlanPhaseTemplate(','smart_plan_phase_id','phase_id',
    'continue_surplus','hifz_target_pages','sard_target_pages','smart-plan-phase-started:',
    'previousSardShortfalls','startOverride','phaseProgress?.suggestedTodayPages','frozenCompletedPages')
ui=need('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SmartPlanPhasesUi.kt',
    'الخط الزمني للخطة','إنهاء مرحلة الحفظ','اعتماد وإنهاء الحفظ','استمرار إضافي','اعتماد وبدء السرد',
    'الطلاب والاستثناءات','بالتاريخ','بالأسابيع','يدوي','QuranRangePickerDialog',
    'معاينة الانتقال الجماعي','إضافة السرد','ترحيل نقص السرد السابق','معاينة تأثير التعديل','آخر سرد:','المقرر التالي:')
core=need('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt',
    'SmartPlanPhasesManagerSheet','PlanPhaseTimelineInline','مرحلة السرد مفتوحة','لا يوجد سرد مقرر اليوم',
    'تخصيص مراحل طالب/خطة','إدارة مراحل الحفظ والسرد','اختر الخطة لإدارة المراحل')
report=need('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt','hifz_target_pages','sard_target_pages','quality_label')
profile=need('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/StudentProfileScreen.kt',
    'مراحل الشهر','الحفظ ·','السرد ·','frozenCompletedPages','phase_id')
migration=need('migration/20260926_v2461_rc11_smart_plan_phases.sql',
    'create table if not exists public.smart_plan_phases','create table if not exists public.smart_plan_phase_states',
    'create table if not exists public.smart_plan_phase_templates','add column if not exists phase_id',
    'enable row level security','phase_started','phase_completed','unique (plan_id, phase_order)')
contract=json.loads((ROOT/'migration/source-contract.json').read_text())
for t in ('smart_plan_phases','smart_plan_phase_states','smart_plan_phase_templates'):
    if t not in contract.get('tables',[]): errors.append(f'source contract missing {t}')
if contract.get('native_version')!='2.54.2': errors.append('native_version is not 2.54.2')
if len(contract.get('tables',[]))<28: errors.append(f'expected at least 28 tables, got {len(contract.get("tables",[]))}')
track=need('app/src/main/java/com/mohammedsamir/halaqati/data/StudentTrackRules.kt','setOf(GENERAL_HIFZ, TATHBIT)')
if 'setOf(GENERAL_HIFZ, TATHBIT, SARD)' in track: errors.append('Sard was incorrectly added as a student track')
selftests=need('scripts/run-kotlin-selftests.sh','SmartPlanPhaseEngine.kt','SmartPlanPhaseEngineSelfTest.kt','timeout 300s kotlinc','timeout 30s kotlin')
targeted=need('scripts/run-rc11-targeted-selftest.sh','timeout 120s kotlinc','SmartPlanPhaseEngineSelfTestKt')
if errors:
    print('RC11 Smart Plan Phases gate FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RC11 Smart Plan Phases gate PASS (phases, Sard anchor, templates, bulk exceptions, reports, notifications, RLS)')
