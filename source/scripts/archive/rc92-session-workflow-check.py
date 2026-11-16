#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

errors = []
def need(ok: bool, message: str):
    if not ok:
        errors.append(message)

core = read("app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt")
repo = read("app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt")
models = read("app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt")
rules = read("app/src/main/java/com/mohammedsamir/halaqati/ui/sessions/SessionWorkflowRules.kt")
test = read("app/src/test/java/com/mohammedsamir/halaqati/ui/sessions/SessionWorkflowRulesTest.kt")
selftest = read("scripts/selftests/SessionWorkflowRulesSelfTest.kt")
doc = read("POWER_RC9_2_SESSION_WORKFLOW_AR.md")

# Full-page session workflow and teacher navigation.
need("SessionRosterScreen(" in core and "active?.let { session ->" in core, "full-page SessionRosterScreen integration is missing")
need("ابحث عن طالب للوصول السريع" in core, "student quick-search is missing")
for label in ["لم يُرصد", "لم يُسمّع", "حاضر بلا تسميع", "تم التسميع", "الغياب", "الأولوية", "أبجدي"]:
    need(label in core, f"session filter/sort label missing: {label}")
need("FontWeight.ExtraBold" in core or "FontWeight.Bold" in core, "student name emphasis is missing")

# Explicit not-recited state is not a zero-page recitation.
need('const val NOT_RECITED_MARKER = "halaqati:not_recited"' in rules, "explicit NOT_RECITED marker is missing")
need("StudentState.NOT_RECITED" in rules and '"not_recited"' in rules, "NOT_RECITED filter/state is missing")
need("fun markNotRecited" in core and "saveDailyNote(" in core, "teacher cannot explicitly mark a student as not recited")
need("participation = SessionWorkflowRules.NOT_RECITED_MARKER" in core, "NOT_RECITED marker is not persisted in existing notes channel")
need('participation = ""' in core and "currentNote.copy(participation = \"\")" in core, "NOT_RECITED marker is not cleared after a real recitation")
need("explicitNotRecitedIsDifferentFromUnmarked" in test, "NOT_RECITED vs UNMARKED regression test is missing")
need("NOT_RECITED_MARKER" in selftest, "pure Kotlin session state self-test is missing")

# Historical/current recitation editing: same row id, original creation order preserved, plan is invalidated/rebuilt.
need("existing: RecitationRow? = null" in core, "recitation dialog cannot edit an existing row")
need("clientId = existing?.id" in core, "recitation edit does not upsert the existing row id")
need("createdAt = existing?.createdAt" in core, "recitation edit does not preserve chronological created_at")
need("val createdAt: String? = null" in models, "RecitationDraft cannot preserve historical created_at")
need("smartPlanId" in models and "smartPlanType" in models and "smartPlanDate" in models, "historical Smart Plan linkage is not exposed on RecitationRow")
need("invalidatePlanSnapshotMemory()" in repo, "recitation changes do not invalidate Smart Plan snapshots")
need("تم تعديل التسميع وإعادة حساب الخطة" in core, "edit/recalculation UX feedback is missing")

# Schedule-aware assignments: derive from plan schedule, never hard-code Friday.
need("val scheduledToday: Boolean = false" in repo and "scheduledToday = activeToday" in repo, "activity schedule state is not exposed")
need("SmartPlanEngine.planDates(plan)" in repo, "today scheduling does not derive from the Smart Plan schedule")
need("لا يوجد حفظ مقرر اليوم" in core, "no-hifz-today card is missing")
need("مقرر السرد اليوم" in core, "scheduled sard duty is not surfaced")
need("Map<String, List<PlanSnapshot>>" in core, "session roster cannot retain multiple activity plans per student")
need("snapshotForActivity" in core and "it.key == key && it.scheduledToday" in core, "manual actions do not prefer the activity plan scheduled today")
need('dayOfWeek == DayOfWeek.FRIDAY' not in core and '"FRIDAY"' not in core, "session UI hard-codes Friday instead of plan schedule")

# Undo behavior for both new recitation and session deletion.
need("fun undoPendingRecitation" in repo, "new recitation Undo path is missing")
need('actionLabel = "تراجع"' in core and "undoPendingRecitation" in core, "quick recitation Undo Snackbar is missing")
need("تم حذف الجلسة مؤقتًا" in core and "NonCancellable + Dispatchers.IO" in core, "deferred session-delete Undo flow is missing")
need("rows = beforeDelete" in core, "session list is not restored on Undo/delete failure")

# Closing safety and note integrity.
need("يوجد طلاب لم يُرصدوا" in core and "إغلاق على أي حال" in core, "close-session unmarked warning is missing")
need("participation = existing?.participation.orEmpty()" in core, "editing a note can overwrite the NOT_RECITED state")
need("لا توجد DB migration" in doc and "deferred" in doc.lower(), "RC9.2 structural/DB/delete rationale is not documented")

if errors:
    print("RC9.2 Session Workflow gate FAILED")
    for error in errors:
        print(" -", error)
    raise SystemExit(1)
print("RC9.2 Session Workflow gate PASS")
