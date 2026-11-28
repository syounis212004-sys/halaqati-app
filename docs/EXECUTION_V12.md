# Execution v1.2 candidate

This branch prepares Android 2.91.16 and three forward migrations. The tests in `source/migration/tests/` target empty PostgreSQL 17 databases with fictional data. Configure separate staging credentials for end-to-end acceptance. The GitHub validation workflow deliberately uses an unreachable Supabase URL and generates a debug-signed APK.

| Area | Implemented in this branch | Still required before production |
| --- | --- | --- |
| Guardian review | Accounts route to link review. The existing RPC checks reviewer and student scope, preserves valid approved accounts, and approves the profile and link in one transaction. New implicit email links are disabled. | Staging tests with both client versions and live-equivalent policies, request creation scope, explicit relationship management, historical dry-run and review. |
| Guardian overview | The legacy RPC and Android recalculation use the primary activity. Tilawah is separate from review. Program profiles omit memorization track and plan in the guardian home. | A versioned selectable membership view, Web display parity, separate week data and per-program reports. |
| Students and memberships | Android preselects a confirmed visible scope or asks for a choice. An existing visible student can be enrolled in a selected program without changing the student record. Both clients persist explicit membership deltas. | Reproduce the original RLS 403 with a scoped staging user, atomic registration, permission-aware search and membership lifecycle. |
| Sessions and plans | Android opens today's existing session, offers a choice when multiple exist, and uses the returned ID after explicit creation. Exiting leaves unobserved attendance untouched. Program recitations cannot attach to memorization plans. Program data is filtered out of Android plan snapshots. | Real weekly scheduling, planned/closed transitions, explicit closing options with atomic persistence, archive/delete/restore and Outbox reconciliation, and Web parity. |
| Program settings | Android uses actual database option keys and separates the legacy training type from recitation courses. | Define staged educational classification and capabilities, then migrate verified records selectively. Add independent intensive plans, assessments, and reports. |

## Staging and rollback

1. Preserve the pre-change definitions and ACLs of the functions and triggers touched by the three migrations. Take a scoped backup and test recovery before changing staging. Keep the four applied ledger entries intact.
2. Apply the three new migrations in numeric order on staging and exercise old and new clients, RLS identities across two associations, replay, concurrent review, rejection, link removal, and guarded reads. Exercise the local-first queue with a scoped user who loses permission while offline. Verify no implicit relinking after unbinding.
3. Test the Android package using the same signing identity as any build it must update. Use a new release archive and explicitly approved release workflow for distribution.
4. A client rollback restores the preceding source build. Preserve authoritative writes and queue records. For SQL rollback use a reviewed forward restoration of definitions and ACLs against the snapshot. Keep the guardian approval guard unless an equivalent protection is in place, and preserve links that were explicitly approved.
