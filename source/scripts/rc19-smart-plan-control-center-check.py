#!/usr/bin/env python3
from pathlib import Path
import json, re, sys
root=Path(__file__).resolve().parents[1]
errors=[]
def read(rel):
    p=root/rel
    if not p.exists(): errors.append(f'missing {rel}'); return ''
    return p.read_text(encoding='utf-8',errors='ignore')
def need(cond,msg):
    if not cond: errors.append(msg)
def version_tuple(v):
    try: return tuple(int(x) for x in v.split('.')[:3])
    except Exception: return (0,0,0)

build=read('app/build.gradle.kts')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
core=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
health=read('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanHealthRules.kt')
mig=read('migration/20261002_v2550_rc19_smart_plan_control_center.sql')
contract=json.loads(read('migration/source-contract.json') or '{}')
vm=re.search(r'versionName\s*=\s*"([0-9.]+)"',build)
vc=re.search(r'versionCode\s*=\s*(\d+)',build)
need(bool(vm and version_tuple(vm.group(1)) >= (2,55,0) and vc and int(vc.group(1)) >= 25500),'RC19-compatible version >= 2.55.0')
need('SmartPlanAccordionCard' in core and 'KeyboardArrowDown' in core and 'عرض الخطة' in core,'accordion plan cards')
need('إدارة الشهر' in core and 'showPlanActions' in core and 'PlanHealthSummaryChip' in core,'compact smart-plan management UI')
need('SmartPlanHealthRules.BLUE' in core and 'SmartPlanHealthRules.RED' in core,'four-color health UI')
need('ratio > 1.10' in health and 'ratio >= 0.90' in health and 'ratio >= 0.75' in health,'health thresholds 110/90/75')
need('redScheduledDayStreak >= 2' in health,'two-red-day notification rule')
need('rc19_start_month' in repo and 'rc19_record_plan_health' in repo and 'rc19_mark_plan_health_notified' in repo,'repository RC19 RPC wiring')
need('val hifzWindow' in repo and 'hifzWindow && weekday in effectiveWeekdays' in repo,'guardian red-streak alert is restricted to scheduled Hifz days')
need('only a genuinely active phase may drive the current card/assignment' in repo,'future/context Sard cannot masquerade as current phase')
need('create table if not exists public.smart_plan_health_daily' in mig,'health snapshot table')
need('create or replace function public.rc19_record_plan_health' in mig and 'v_streak>=2' in mig,'server red-streak engine')
need('create or replace function public.rc19_start_month' in mig,'monthly create+repair RPC')
need("'hifz','مرحلة الحفظ'" in mig and "'sard','مرحلة السرد'" in mig,'missing phase repair')
need('start_date=v_month_start,end_date=v_hifz_end' in mig,'Hifz 1-22 repair')
need('start_date=v_sard_start,end_date=v_month_end' in mig and 'target_units=25,target_pages=25' in mig,'Sard 23-end fixed 25')
need('status=v_hifz_state' in mig and 'status=v_sard_state' in mig,'stale phase-state repair')
need('archived_duplicate_plans' in mig and "status='archived'" in mig,'duplicate monthly plan archival')
need(version_tuple(str(contract.get('native_version','0.0.0'))) >= (2,55,0),'source contract RC19-compatible version')
need('smart_plan_health_daily' in contract.get('tables',[]),'health table declared')
for rpc in ('rc19_record_plan_health','rc19_mark_plan_health_notified','rc19_start_month'):
    need(rpc in contract.get('rpc',[]),f'{rpc} declared in source contract')

if errors:
    print('RC19 Smart Plan Control Center gate FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RC19 Smart Plan Control Center gate PASS')
