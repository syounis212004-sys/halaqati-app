# Phase two: student enrollment and scope isolation

Continue on fix/v12-phase-one-ui; do not make another branch. Main has Graphify 0.9.121 with matching source tree; never copy a graph from another source tree. Keep the earlier signed build and original Codex work as historical refs.

## Acceptance criteria
- New student form does not silently substitute an unrelated scope when the requested scope is unavailable. Disable save until metadata is loaded. Secondary options are active, eligible programs only; preserve hidden membership IDs.
- Program-only students use no memorization track and zero memorization targets for new records. No existing track rewrite or migration by name.
- Student detail sheet uses the selected membership scope for reports, sessions and smart-plan selection; never merges regular and program figures. Hide memorization metrics/track when in a program; no editing or archiving the primary record from secondary context.
- Online student creation: use server-confirmed, atomic new-record plus selected program memberships; stable client UUID for retries; do not merge similar names. Existing student enrollment must preserve primary record and other memberships. Explicit permission and association checks in an invoker RPC, retain RLS. New migration requires isolated validation and staged rollout. Offline creation retains durable legacy outbox until atomic offline queue reconciliation is implemented; no deletion of old queue items.
- Existing enrollment search remains limited to RLS-visible students; association-wide verified lookup and approval lifecycle require a separate secure design.
- Add regression tests; run available pure Kotlin checks; full Android build, database staging tests, Graphify refresh and web parity are release gates.

## Implementation status
- Scoped selection, detail sheet, and form protections implemented; existing enrollment uses a single server RPC or durable RPC queue. Online new student creation is atomic. Existing offline new-student creation still uses legacy queue; retry and conflict lifecycle remain separate work.
- Proposed 20261128120400 uses SECURITY INVOKER, strict field whitelist and same-association target authorization. No production schema or data changes. Staging verification of RLS, duplicate handling, invalid secondary rollback, replay, and existing member preservation is required.
- Old-client direct membership writes remain a separate security and rollout concern; no global RLS changes here. No association-wide student lookup added.

## Verification / follow-up
- 14 standalone Kotlin assertions passed against StudentEnrollmentRules.kt byte-identical to Git blob ce576af25faf90496139f3fae5acb47547058dce.
- Phase-one CI run 46141009359: assembleDebug and compilation passed; 305 tests, 284 passed, 1 failed (narrow RTL date overflow), 20 skipped. Kept the regression test and changed the date field to full-width auto-sized single-line BasicText (Compose Foundation 1.8+ API, current project 1.9.x). New CI required; not verified yet.
- Scope-keyed detail composition prevents showing previously loaded stats while changing memberships. Preserve explicit null track in Room serialization; add two Robolectric regressions. Frozen staff rejected by proposed enrollment RPC.
- Current Graphify index on main matches its source-tree hash 98faee54d62393e535dfb01776fb14cbb959d2bb, pinned 0.9.121. Development graph is stale and must be refreshed before main merge. No production SQL changes.
