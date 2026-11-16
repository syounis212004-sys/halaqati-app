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

rules=need('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanActivityRules.kt',
    '"tathbit" -> "tathbit"','activity == "tathbit" -> 1')
phase=need('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanPhaseEngine.kt',
    'START_LAST_ACTIVITY = "last_activity"','usesHistoricalActivityAnchor')
repo=need('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt',
    'fun latestAcceptedActivityRecitation(','rc171_latest_activity_anchor','p_plan_id',
    'every activity respects the configured terminal','studentRecitations = studentRecitations',
    'generatedRange.qcfLineKeys','keepExplicitEnd = draft.goalMode == "quran_range"')
ui=need('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SmartPlanPhasesUi.kt',
    '"last_activity" to latestLabel','آخر تسميع حفظ','آخر تثبيت','معاينة نطاق المرحلة قبل الحفظ',
    'ResponsiveChipGrid(items = goalOptions','ResponsiveChipGrid(items = startOptions')
core=need('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt',
    'effectiveStudentTrack','latestAcceptedActivityRecitation(','استخدم النقطة التالية',
    'طالب الحفظ العام يستخدم الحفظ، ويمكن إضافة السرد كنشاط/مرحلة داخل خطة الحفظ.',
    'ResponsiveChipGrid(items = weekdayLabels.mapIndexed')
daily=need('app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DailyOperationStateEngine.kt',
    'TATHBIT_DAY','Activity.TATHBIT','"يوم تثبيت"')
vm=need('app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DashboardViewModel.kt',
    '"tathbit" -> DailyOperationStateEngine.Activity.TATHBIT')
sql=need('migration/20260930_v2522_rc17_1_build_fix3_smart_plan_continuity.sql',
    "'last_activity'",'rc171_latest_activity_anchor','p_plan_id uuid default null',
    "p.scope_type = 'category'",'rc152_roll_tathbit_months','continuation_mode',
    "case when v_next is null then r.range_end_page else null end")

# Sard must not be offered as a normal new standalone plan type.
if 'add("sard" to "سرد")' in core:
    errors.append('CoreScreens: standalone Sard is still offered as a new plan type')
# Historical legacy support is allowed, but new default phase writes must use last_activity.
if 'startMode = "last_sard"' in ui:
    errors.append('SmartPlanPhasesUi: new phase still writes legacy last_sard')

if errors:
    print('RC17.1 BUILD FIX 3 continuity gate FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RC17.1 BUILD FIX 3 continuity gate PASS')
