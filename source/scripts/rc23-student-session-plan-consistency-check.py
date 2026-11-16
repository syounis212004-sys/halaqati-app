#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
core=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt').read_text()
mgmt=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt').read_text()
repo=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text()
engine=(root/'app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt').read_text()
rules=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/sessions/SessionWorkflowRules.kt').read_text()
mig=(root/'migration/20261008_v2590_rc23_student_session_plan_consistency.sql')

def need(ok,msg):
    if not ok: raise SystemExit('RC23 FAIL: '+msg)
need('isReusableDailySessionStatus' in rules, 'daily-session status rule missing')
need('dailySessionKey' in rules, 'daily session identity missing')
need('SessionWorkflowRules.matchesSearch(it, needle)' in core, 'student screen does not use normalized Arabic search')
need('needle.isNotBlank() && statusFilter == "active" && student.status == "suspended"' in core, 'direct search does not surface suspended students')
need('SessionWorkflowRules.matchesSearch(student.fullName' in mgmt, 'membership search not normalized')
need('val milestone = null' in engine or 'pendingMilestone(' not in engine[engine.find('fun calculateProgress'):], 'plan milestone still blocks assignment')
need('.put("milestone_mode", "none")' in repo, 'saving plans can still enable milestone exams')
need('rc23_get_or_create_session' in repo, 'repository lacks atomic server get-or-create session')
need('isReusableDailySessionStatus' in repo, 'repository still ignores closed daily session')
need(mig.exists(), 'RC23 migration missing')
ms=mig.read_text() if mig.exists() else ''
need('sessions_one_type_per_halaqa_day' in ms, 'database unique daily session constraint missing')
need("milestone_mode = 'none'" in ms and 'milestone_requires_approval = false' in ms, 'existing plan tests not disabled')
need("add value if not exists 'tilawah'" in ms, 'tilawah enum parity missing')
print('RC23 student/session/plan consistency source gate PASS')
