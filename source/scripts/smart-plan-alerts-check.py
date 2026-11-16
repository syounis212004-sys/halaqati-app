#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
errors=[]

def read(rel):
    p=root/rel
    if not p.exists():
        errors.append(f'missing {rel}')
        return ''
    return p.read_text(encoding='utf-8', errors='ignore')

repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
graph=read('app/src/main/java/com/mohammedsamir/halaqati/data/AppGraph.kt')
rules=read('app/src/main/java/com/mohammedsamir/halaqati/data/SmartPlanAlertRules.kt')
legacy=read('migration/legacy-web-v2.44.0/src/features/plans/runtime.js')

for token in ('async function checkSmartPlanAlerts()', 'smart-plan-delay:', 'smart-plan-prep:', "new Date().getHours()>=17"):
    if token not in legacy: errors.append(f'legacy smart-plan alert reference missing: {token}')
for token in ('fun runSmartPlanAlerts(', 'fun sendStudentEvent(', '"event_student"', 'smart-plan-health:', 'smart-plan-phase-started:', 'ZoneId.of("Asia/Gaza")'):
    if token not in repo: errors.append(f'Native smart-plan alert parity missing: {token}')
for token in ('PeriodicWorkRequestBuilder<SmartPlanAlertsWorker>(6, TimeUnit.HOURS)', '"halaqati-smart-plan-alerts"', 'class SmartPlanAlertsWorker'):
    if token not in graph: errors.append(f'WorkManager smart-plan alert scheduling missing: {token}')
for token in ('shouldSendDelay', 'shouldSendHomePrep', 'preferredPlanType', 'assignmentLabel'):
    if token not in rules: errors.append(f'Pure alert rule missing: {token}')
if 'scheduled_plan_home_prep' in repo or 'smart_plan_schedules' in repo:
    errors.append('Native runtime must not depend on stale scheduled_plan_home_prep/smart_plan_schedules backend contract')

if errors:
    print('Smart-plan alert parity check FAILED')
    for e in errors: print(' -', e)
    sys.exit(1)
print('Smart-plan alert parity check passed (legacy delay + evening prep via idempotent event_student WorkManager flow)')
