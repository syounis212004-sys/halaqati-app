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
