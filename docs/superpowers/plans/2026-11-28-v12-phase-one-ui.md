# Halaqati v1.2 follow-up: phase one

Base: signed 2.91.16 candidate f54851ae55dc8ffaee9c1311e89f03d4233733ea. User requested one coherent update, not a rewrite, preserving Arabic RTL, layout identity, offline durability and RLS. No release, main merge, or production SQL in this Android-only slice.

## Approved requirements and acceptance criteria

1. Admin accounts and approvals become one UI with two tabs, one menu entry. The existing approvals route remains a backwards-compatible entry opening the request tab (notifications/dashboard). Teachers and supervisors keep their existing scoped inbox, never gain account administration. Pending accounts use the existing request RPC, not a parallel direct approval route. Test RoleNavigationPolicy; verify direct notification links, staff scope, and tab navigation on Android.
2. Explicit guardian links only. List all approved relationships without a primary marker. Remove the email-based account guess and old single-link write from the student editor. Preserve contact fields independently. List current relationships in profile and management; only an admin can remove a selected link with a required reason using rc_unlink_guardian; require server acknowledgment, keep other links. Keep old RPCs and queued data intact; do not silently discard rejected legacy ops. Tests for missing account metadata and duplicate IDs, manual multi-guardian/unlink and offline/failure verification.
3. Fix date wrapping in program dialog: full-width stacked fields with nonwrapping LTR ISO date within RTL field. Verify narrow phone, large font, dark/light and picker.
4. Separate educational family, delivery kind, specialization, educational stage and generic level. Recitation/tajweed stages: reading foundations, Noorani/golden rule options, introductory, qualifying, advanced, sanad preparation, sanad/ijazah, qiraat; stage is optional and institution-defined; no automatic promotion or universal prerequisite. Historical training type is teacher training, not qualifying tajweed. Add nullable validated program_stage via reviewed forward migration; no automatic backfill based on names. UI requires migration before release; older clients do not send stage. Check actual current records and configure existing qualifying course explicitly. No change to scoring or memorization tracks.
5. Remaining release-wide tasks: atomic student enrollment and RLS 403; full membership lifecycle and permissions; weekly schedules and Saturday; real session closing, atomic delete/restore; scope-aware profile, reports, rankings, certificates and guardian selectable memberships; Web parity; offline rejection reconciliation. Do not mark these complete because first slice builds.

## Production SQL already applied (do not replay)
- 20261128111716 halaqati_backfill_verified_legacy_association
- 20261128120000 guardian_link_review_scope
- 20261128120100 recitation_program_context
- 20261128120200 guardian_overview_primary_scope
- 20261128112140 halaqati_explicit_equal_guardian_links_compatibility
Last migration preserves is_primary as legacy display pointer only, removes automatic email links, denies legacy replacement/deletion, and exposes rc_unlink_guardian(student, guardian, reason). New UI must not rely on is_primary. Keep historical migration versions unchanged.

## Method and gates
Read and apply repository Ponytail, Ponytail-review, Halaqati development/UI/QA, test-driven-development and Superpowers writing-plans/executing-plans/verification-before-completion. Reuse existing components. Write regressions first; then implementation; verify actual commands. Graphify CLI and Android Gradle are unavailable in chat container (network clone failed); regenerate graph and manifest in full checkout before merge, do not disable knowledge gate. CI: existing execution-v12 validation workflow (assembleDebug/testDebugUnitTest, fake backend), then signed candidate and lint only after review. Do not claim success before results. Staging migration and real-client role/RTL tests are mandatory for release.

## Sources for educational review
- https://quran.iugaza.edu.ps/ (recitation and qiraat courses, levels including advanced and sanad)
- https://quranpedia.net/book-attachment/26088/83588 (Palestinian Dar al-Quran study documenting introductory, qualifying, advanced)
These support the distinction; the detailed configurable stage sequence reflects the user's institutional policy, not a universal curriculum.
