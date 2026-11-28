# Phase three: sessions, schedule and visual continuity

Continue the same fix/v12-phase-one-ui branch / draft PR #112. Main is not a test branch. Existing APK and historical refs retained.

## Design
Reuse the existing Cairo typography, golden navigation artwork, MaterialTheme palette, rounded cards, responsive chips, existing date field, StateNotice and status colors. RTL first; date is LTR. In the roster: full-scope attendance and activity figures, clear distinct absent/excused, visible state, primary finalization only after online acknowledgement. Search/filter does not change the entire-session denominator. For programs use activity terminology rather than hifz terms. Reduce-motion preferences remain respected; existing HalaqatiMotionEvents.celebrate() only after server confirms an actual close. No new motion dependency, no animated operational counters. Retain existing brand artwork and transitions.

## Acceptance gates
- Saturday is no longer hardcoded when explicit work days are configured; ISO day numbers Mon=1..Sun=7 independent of smart-plan weekday convention. Existing regular schedule defaults to Sun–Fri; existing programs default to all days when auto is enabled, but manual-only by default. Missing/unavailable scope must not create a phantom session. Manual creation remains allowed on off-days. Existing session returned before checking schedule.
- Settings: editable weekly days and auto/manual policy; no rewriting existing schedule_text or plan schedules. Nullable additive DB columns; old client reads unchanged. Do not infer historical dates or change existing data automatically.
- Distinguish final close from temporary exit. Closing/reopening changes only session status after server confirmation, requires no local pending session operations. Closing never marks unobserved students absent. Closed roster read-only on this Android client, explicit reopen; server-wide immutable closure/old-client enforcement not included yet. Existing archive/restore remains. Remove destructive cancel shortcut (production trigger deletes row when cancelled); deletion guarded as before. Atomic safe delete and complete restoration remain separate.
- Edit dialog scope-aware type choices, keep old existing type only in same scope, no silent conversion. Existing production edit RPC omits tilawah; proposed migration extends supported type and refuses changes to scope/type if linked attendance/recitation/notes exist. Date changes still use existing rebind logic; independent program rebind needs staging.
- Existing server session create still creates open rows, including future dates; planned scheduling and optional start/end clock times remain follow-up. Do not label future rows as planned until server supports it.
- Verify narrow screens, RTL, light/dark, large fonts, scope choice, role permissions, online/offline states, and real-data staging. No production SQL or APK publish. Merge only after Android checks, current Graphify and migration review.

## Skills used
Repository Halaqati UI/UX contract, Android design, Frontend Design, UI/UX Pro Max, Halaqati motion guidance, wnb-compose-animation, Ponytail/review, TDD, project knowledge and release checks. Web-only 60fps/micro-interaction/accessible-animation guidance used as design principles, not web APIs in Kotlin. Superpowers planning format and verification discipline; no claim of plugin invocation. Graphify CLI/full checkout unavailable in chat, so targeted reads of known paths; regenerate in full checkout before merge. No graphic asset replacement.

## Pending
Atomic delete/restore, server-wide closure enforcement, scheduling future sessions, start/end times, web parity, staged migrations, device previews and release checks.

## Verified evidence and review
- 16 standalone Kotlin assertions against byte-identical SessionScheduleRules.kt (Git blob 5a45d76d224b17e5b8b5d44f5b3137d7c235544c) passed: schedule defaults/explicit Saturday/empty days and exact pending-session IDs.
- Previous combined phase 1/2 Android run 46141876527 passed; narrow RTL date regression resolved in that revision.
- Live database inspection (read-only): sessions already has planned/open/closed/cancelled status, but cancelled triggers deletion. Existing edit RPC omits tilawah; 20261128120600 proposes to add tilawah, reject changing scope/type of a used session, retain explicit same-association checks, and avoid program date changes rebinding memorization. Full staging SQL validation still required.
- Knowledge integrity run 46142455248 generated a fresh graph but failed the committed graph checksum gate. Keep the gate; a restricted development-only refresh workflow can produce the portable graph files. Main remains unchanged until all gates pass.
- Incomplete: creation of future planned rows, optional clock times, server-wide closed-session write guards, full atomic deletion/restore, Web parity and real-device verification. No database changes in this phase.
