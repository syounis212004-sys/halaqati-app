# RC28 daily workflow UI implementation

## Files
- `app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt`
- This report. No shared theme, component, data or other screen file was edited by this implementer.

## Behavior and layout
- Student cards use quieter 16dp surfaces and smaller identity marks. The entire card and the existing expansion control open details. The scan view retains track/program, status, plan progress and an accessible missing-link-data indicator; expanded details retain all missing-data explanations, statistics and actions, show the full untruncated name, and offer one full-width primary recitation action.
- Session cards have reduced vertical padding, quieter outlines and two-line titles; every edit/archive/restore/cancel/delete menu callback remains present.
- Roster cards use smaller identity marks and no outer outline, retain attendance/behavior/sync status, and explicitly label the last recorded recitation. Opening a card reveals the full name and all existing actions. The header separates completed/unrecited totals from attendance counts and names the filter control clearly.
- Smart duty presentation uses one quiet grouping surface and flat activity rows. The latest linked plan recitation appears before today's duty. Exactly one eligible scheduled activity (or the first extra activity when no activity is scheduled) receives a filled primary action; every other original action remains available as a text action. All assignment/route/track decisions and original activity callbacks remain unchanged.
- Attendance and behavior choices now use scrollable bottom sheets with 48dp targets and selected-state icons. Late/excused detail entry uses a keyboard-aware scrollable sheet and preserves validation/default values and save arguments.
- The existing complete student note form uses a scrollable, keyboard-aware sheet, preserving behavior, optional score, comment, homework, guardian visibility and the exact save callback. Recitation and complex student/plan editors were not changed.
- Plan accordion cards show existing phase targets and today's existing assignment before expansion. Current phase and completed/target values are emphasized, with tinted flat phase panels. Expanded history, diagnostics and role-restricted management/recitation actions are unchanged. No domain progress or assignment formula was added or changed.

## Validation
Passed:
- `python scripts/compile-sanity-check.py`
- `python scripts/compose-risk-check.py`
- `python scripts/compiler-regression-check.py`
- `python scripts/rc21-multi-halaqa-compact-ux-check.py`
- `python scripts/rc23-student-session-plan-consistency-check.py`
- `python scripts/rc85-smart-plan-activity-performance-check.py`
- `python scripts/rc86-v3-performance-session-ux-check.py`
- `git diff --check`
- Compared the before/after repository operation sequence, plus complete bodies of `saveStatus`, `saveBehavior`, `markNotRecited`, `loadRosterLocal` and `refreshRosterInBackground`: unchanged.

## Limitations
- `rc24-3-compact-ui-check.py` reports `ScreenHorizontal not 14dp`; that legacy source gate checks a shared token outside this task's ownership, while the approved RC28 contract requires 16dp page gutters. Root has been informed.
- This checkout has no `gradlew`, and neither `gradle` nor `kotlinc` is installed on PATH. The passing source checks are not an Android/Compose compile. Integrated Android build and emulator/device checks (RTL, large font, small screen and IME sheet reachability) remain release validation tasks.

## Independent review fixes
- Corrected the smart-duty last-recitation hint to combine each plan snapshot's primary last recitation with every linked activity's last recitation, deduplicate by recitation ID, and select by session date followed by creation timestamp. This covers later Sard/review records and same-day ordering. Assignment selection and formulas are unchanged.
- Moved all bounded generic recitation activities, the existing gated “لم يُسمّع” action and note launch into a scrollable bottom sheet opened by “إجراءات الطالب السريعة”. Every launch dismisses the sheet before opening the existing editor/action. Sheet controls retain at least 48dp targets; note/not-recited controls span the full width. Existing delete-recitation confirmation remains unchanged outside the sheet.
- Re-ran compile-sanity, compose-risk, compiler-regression, RC21 compact UX, RC23 consistency, RC85 activity and RC86 session checks: all passed. `git diff --check` passed. Android compiler/device limitations above still apply.
