#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

errors = []
def need(ok: bool, message: str):
    if not ok:
        errors.append(message)

engine = read("app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt")
core = read("app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanCoreState.kt")
repo = read("app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt")
runner = read("scripts/run-kotlin-selftests.sh")
unit = read("app/src/test/java/com/mohammedsamir/halaqati/plans/SmartPlanCoreStateTest.kt")
doc = read("POWER_RC9_1_SMART_PLAN_CORE_AR.md")

need("data class WeeklyProgress" in engine, "canonical WeeklyProgress is missing")
need("cumulativePlannedAmount" in engine and "weeklyProgress(" in engine, "daily/weekly calculation source is not unified")
need("val weekly: WeeklyProgress" in engine, "SmartPlan Progress does not expose weekly state")
need("object SmartPlanCoreStateEngine" in core and "data class State" in core, "RC9.1 canonical state engine is missing")
for field in [
    "targetPages", "requiredByToday", "completedPages", "remainingPages",
    "aheadBySchedule", "behindBySchedule", "scheduleDebt", "weekly",
    "lastMemorizationAnchor", "nextAssignment",
]:
    need(field in core, f"RC9.1 core state field missing: {field}")
need('it.activityType.lowercase() in HIFZ_ACTIVITIES' in core, "memorization anchor is not isolated from sard/review")
need("SmartPlanEngine.isPassing(it, plan)" in core, "failed recitations can move the memorization anchor")
need("weeklyTargetOverride" in core and "targetPagesWeekly" in repo, "student weekly target is not connected to canonical state")
need("val coreState: SmartPlanCoreStateEngine.State" in repo, "PlanSnapshot does not expose the canonical state")
need("SmartPlanCoreStateEngine.build(" in repo, "repository does not build canonical state")
need("SmartPlanCoreStateSelfTest.kt" in runner, "RC9.1 self-test is not in the pure Kotlin gate")
need("failedHifzAndSardCannotMoveMemorizationAnchor" in unit, "anchor isolation regression test is missing")
need("RC9.2" in doc and "لا توجد DB migration" in doc, "RC9.1 scope/DB contract is not documented")

if errors:
    print("RC9.1 Smart Plan Core gate FAILED")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)
print("RC9.1 Smart Plan Core gate PASS")
