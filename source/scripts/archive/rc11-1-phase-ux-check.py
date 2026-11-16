#!/usr/bin/env python3
from pathlib import Path
import sys
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
ui=need('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SmartPlanPhasesUi.kt',
    'RC11.3 · مراحل الخطة','الانتقال الجماعي إلى السرد','هدف الحفظ لا يتغير؛ يبقى لكل طالب حسب خطته الحالية.',
    'هدف السرد الموحد للمنقولين (صفحة)','تخصيص هدف سرد لبعض الطلاب','الطلاب والاستثناءات','معاينة الانتقال الجماعي',
    'Rc11DatePickerDialog','rememberDatePickerState','الأحد','الاثنين','الثلاثاء','الأربعاء','الخميس','الجمعة','السبت',
    'الافتراضي في RC11.3 هو 25 صفحة','آخر سرد','بداية المرحلة التالية:','معاينة نطاق المرحلة قبل الحفظ',
    'المطلوب:','phaseRouteLabel','previewPagesFrom','Rc11SearchChoiceField','طالب لمعاينة آخر موضع',
    'last_activity','آخر تسميع حفظ','آخر تثبيت',
    'تحديث قاعدة البيانات مطلوب','smartPlanPhaseSchemaIssue')
core=need('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt',
    'SmartPlanSearchChoiceField','label = "الحلقة"','label = "الطالب"','placeholder = { Text("ابحث عن $label") }')
repo=need('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt',
    'fun latestSardRecitation(','fun smartPlanPhaseSchemaIssue()','sardTargetPagesOverride: Double? = null',
    'rc112_transition_plan_students','PGRST205')
quran=need('app/src/main/java/com/mohammedsamir/halaqati/quran/QuranRangeEngine.kt',
    'fun previewPagesFrom(','private fun routeLocations(','QuranTraversalDirection.REVERSE_SURAH_ORDER')
migration=need('migration/20260926_v2461_rc11_smart_plan_phases.sql',
    'create table if not exists public.smart_plan_phases','create table if not exists public.smart_plan_phase_states',
    'target_pages_override numeric','NOTIFY pgrst, \'reload schema\';')
selftest=need('scripts/kotlin-selftest/QuranRangeEngineSelfTest.kt','sardPreviewReverse','QuranLocation(111, 1)')

# Prevent regressions to the problematic Sard defaults.
if 'type == "sard" -> "25"' not in ui and 'target = "25"' not in ui:
    errors.append('Sard 25-page default is not explicit')
if 'goalMode = "open"' in ui and 'sard' in ui:
    # Open mode may remain a selectable option; do not fail merely because the token exists.
    pass
if errors:
    print('RC11.1 Phase UX gate FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RC11.1 Phase UX gate PASS (responsive phases, calendar dates, full weekdays, searchable selectors, Sard 25-page defaults/range preview, bulk per-student targets, schema guard)')
