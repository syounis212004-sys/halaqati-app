# RC28 interface redesign

Approved scope: the user requested implementation of the preceding design proposal in full, keeping Kotlin and Jetpack Compose. Deliver a complete source ZIP and matching GitHub Actions YAML. Attempt upload; supply files if write permission remains unavailable.

## Product constraints
Preserve every existing destination, role restriction, callback, persisted format, student track and Quran assignment rule. No database or signing identity change. Keep Cairo, offline functionality, configurable density, font scaling and reduced motion. Never replace real totals with invented demo data.

## Visual contract
Use a warm neutral page with white surfaces in light mode, layered charcoal surfaces in dark mode, deep green primary and sparing gold achievement accents. Cards 16dp radius, page gutters 16dp, 48dp minimum interaction targets. One primary action per section; secondary actions use text/icon buttons. Distinct Cairo weights for headers, names and supporting copy. Preserve Arabic RTL and full long-name access.

## Screen contract
- Shared chrome: compact toolbar, stable five-item navigation, grouped More/drawer, consistent icons and status feedback.
- Daily session: compact overview/search/filter area and compact student cards. Selected student reveals assignment and actions. Quick actions/notes/attendance use bottom sheets; complex recitation editor remains full-screen. Keep all attendance and recitation callbacks.
- Students: compact scan-friendly list, overflow actions, full details on demand.
- Home: clear current-day focus, current session/actions and attention list from existing data; retain role-specific destinations.
- Student profile: meaningful overview and existing detail sections, with clear section navigation.
- Plans: show Hifz/Sard phase hierarchy and existing targets/status clearly without calculating new assignments.
- Sync: Arabic operation descriptions instead of internal RPC names; raw details available on demand. Errors remain visible with recovery actions.
- Other screens including guardians/reports inherit typography, cards and controls, with scoped polish where needed.

## Validation
Run targeted pure Kotlin/domain regressions and new sync presentation tests. Attempt Android compile/test/lint with existing dependencies and tools; report unavailable checks honestly. Verify workflow YAML, SHA-256 binding, source version 2.64.0/code 26400, archive completeness and absence of build/signing secrets. A final independent code review is required.
