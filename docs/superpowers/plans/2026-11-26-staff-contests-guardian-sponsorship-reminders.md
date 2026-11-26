# Staff contests, guardian sponsorship, local reminders, calendar picker — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (native, single session). Steps use checkbox syntax.

**Goal:** One update (2.91.14 / RC114): calendar date picker in the contest form; teachers, supervisors and the admin create contests; guardians sponsor ("تكفّل") a halaqa of their children; a "المساهمة" page per role with local, schedulable, switch-off-able reminders that never read the database.

**Architecture:** Reuse, do not duplicate. Contests: the existing `donor_contests` table and `contest_*` RPCs are generalized by a creator role (`creator_role`). Sponsorship: the RC48/49 pledge stack is opened to guardians through one gate function `rc50_is_sponsor()` and a guardian halaqa scope. Reminders: `WorkManager` one-time chain + `SharedPreferences`, no server.

**Tech Stack:** Kotlin/Compose (Material 3 `DatePickerDialog`/`TimePicker`), WorkManager (already a dependency), Supabase SQL (SECURITY DEFINER, `search_path = public, pg_temp`), Robolectric tests.

**Spec:** docs/discovery/roles-contests-sponsorship-reminders/REPORT.md and DECISIONS.md (the owner delegated every open decision; they are recorded there).

## Global Constraints
- All user-facing text Arabic; RTL; no Quran text typed.
- DB changes only through one idempotent migration `source/migration/20261126_v2921_staff_contests_guardian_sponsorship.sql`, tested inside a transaction that is rolled back before it is applied.
- Reminder content/schedule never read from the database; stored in `SharedPreferences("halaqati_reminders")`.
- Version 2.91.14 / 29114; RC114 pinned archive; no release publish without the owner's go-ahead for the web step.

## Review Focus
- Guardian must never see a halaqa that is not a child's (tested in SQL under rollback).
- A teacher must not create a contest for a halaqa he does not teach (SQL).
- Reminder time computation across week boundary, DST-free Gaza zone, and "today but time already passed" (unit tests).
- Reminders do nothing when notifications permission is denied or the switch is off.
- Date picker result is ISO and end date cannot precede start (unit + UI test).

### Task 1: Migration (staff contests + guardian sponsorship)
**Files:** Create `source/migration/20261126_v2921_staff_contests_guardian_sponsorship.sql`; Create `source/scripts/staff-contests-contract-check.py`.
- [ ] Write SQL: `contest_creator_role()`, `contest_creator_halaqas()`, `creator_role` column, rewritten `contest_submit/my/donor_halaqas/admin_list/visible/decide`; `rc50_is_sponsor()`, `rc50_guardian_halaqas()`, rewritten `rc48_donor_scope_ok`, `rc48_support_pledge`, `rc49_support_cards`, `rc49_my_pledges`, `rc49_support_submit_transfer`.
- [ ] Run inside `begin … rollback` on the project with simulated JWT claims for: teacher (own/other halaqa), supervisor, admin, guardian (child's/other halaqa), donor (unchanged). Expected: allowed/denied as in Review Focus.
- [ ] Apply; re-run the read-only checks; commit.

### Task 2: Contest form — calendar + staff creators
**Files:** Modify `ui/screens/ContestsScreens.kt`, `data/ContestRules.kt`, `data/HalaqatiRepository.kt`; Test `ContestRulesTest`, `ContestFormUiTest`.
**Interfaces:** Produces `ContestRules.formatDate(LocalDate): String` (dd-MM-yyyy), `Contest.creatorRole: String`, `ContestRules.noHalaqaMessage(role: UserRole): String`.
- [ ] Tests for formatDate/parse round trip and the role messages.
- [ ] `DateField` composable (calendar icon → `DatePickerDialog`, end date picker disallows dates before start).
- [ ] Contests screen per role: donor/teacher/supervisor → "مسابقاتي" + "مسابقات حلقاتي" tabs; admin → existing list + "مسابقة جديدة" (auto-approved).

### Task 3: Reminders (local)
**Files:** Create `reminders/ReminderPrefs.kt`, `reminders/ReminderScheduler.kt`, `reminders/ReminderWorker.kt`, `ui/screens/ReminderPanel.kt`; Modify `push/NotificationHelper.kt` (channel `halaqati_reminders`), `AndroidManifest.xml` (none new), `AppGraph` (reschedule on start); Test `ReminderScheduleTest`, `ReminderPanelTest`.
**Interfaces:** `ReminderPrefs.load(ctx): Reminder`, `ReminderScheduler.nextAt(now: ZonedDateTime, r: Reminder): ZonedDateTime`, `ReminderScheduler.apply(ctx)`.
- [ ] Failing tests for `nextAt` (same day later, same day passed → next chosen day, multiple days, disabled → null).
- [ ] Implement prefs, scheduler (unique OneTime work, `initialDelay`), worker (shows notification then reschedules), channel.
- [ ] Panel: switch, weekday chips, `TimePicker` dialog, permission request on enable, "إرسال تذكير تجريبي الآن".

### Task 4: Page "المساهمة" + navigation + guardian sponsorship UI
**Files:** Create `ui/screens/ContributeScreen.kt`; Modify `AppDestination.kt`, `RoleNavigationPolicy.kt`, `HalaqatiApp.kt`, Settings row; Test `RoleNavigationPolicyTest`, `ContributeScreenTest`.
- [ ] Guardian: tabs "تكفّل بحلقة" (reuse `DonorSupportTab`), "مسابقات", "تذكيراتي". Teacher/supervisor/admin: "مسابقات" + "تذكيراتي".
- [ ] Deep link `contribute` opens the page; reminder notification uses it.

### Task 5: Release RC114
- [ ] Bump version files and notes; gates; pinned `Halaqati_RC114.zip` + workflow; full test suite; run workflow (release); verify digests; hold the web publish for the owner's go-ahead.
