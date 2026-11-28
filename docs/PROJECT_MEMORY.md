# Halaqati Android — project memory

Keep this short; use Graphify for symbols/relationships and verify actual source.
Source base for knowledge setup: `e900ccb` (RC114 / 2.91.14); code is in `source/`.

## Decisions to preserve
- Native Kotlin/Compose + Arabic RTL; preserve application identity and signing.
- Room/account-scoped durable outbox is local-first; Supabase is cloud authority.
  Keep pending writes across restarts/signout, explicit `updated_at` conflict
  resolution, and old databases during migrations. See RC7 offline foundation.
- Phase records are authoritative; `smart_plans` is compatibility storage. Keep
  revision conflicts, frozen results and atomic/idempotent transitions; avoid
  ambiguous historical backfills. See `POWER_RC11_2_CORE_PHASE_CONSISTENCY_AR.md`.
- Keep scoped RLS, admin-only contest approvals and pinned SECURITY DEFINER
  search paths. Review-only SQL is not an instruction to apply a migration.
- Release workflows use a pinned ZIP, not automatically the `source/` directory;
  updating source alone does not publish an application release.

## Implemented / documented fixes
- Mushaf word marks stay in `MushafEngine.Session`. The Compose renderer must
  draw from the ID-indexed snapshot derived from `session` and `tick` in
  `MushafScreenContent`. Keep the mark data out of DigitalKhatt bitmap cache
  keys and page-loading effects. Per-word positions are session-only.
  Android instrumentation reproduced zero stroke pixels after a registered touch
  with the old mutable renderer. Direct bitmap redraws can hide that regression.
  Keep the prompt underline dashed and use the dedicated high-contrast dark blue.
- Knowledge bootstrap resolves pipx Python shebang flags (`-E`) separately from
  interpreter paths; guarded by `test_pipx_python_shebang_options`.
- RC100 documents device-bound auth callbacks, preservation of pending writes,
  trusted update hashes/hosts and ordered durable synchronization.
- RC114 documents scoped guardian sponsorship, admin-approved staff contests,
  contributions and local WorkManager reminders. Contest writes need connectivity;
  cached lists can remain readable offline.
- These are repository facts, not independent verification of production rollout.

- Execution v1.2 candidate separates guardian account review from verified links,
  and scopes guardian summaries and program recitation independently of memorization.
  See [staging and rollback](EXECUTION_V12.md). Production ledger copies are historical.
- Exiting the Android session editor leaves unobserved attendance unchanged.
  New session dialogs derive activity choices from the selected scope.


- Phase-one Android follow-up: unified admin accounts/approval hub keeps old approval route for deep links; teachers and supervisors retain scoped inboxes. Guardian relationship list uses student_guardians, not primary or email. Legacy single-guardian writes removed from student editor; selective unlink requires acknowledged RPC and reason.
- Production migrations 20261128111716 (legacy association backfill) and 20261128112140 (explicit equal guardian links) are already applied. Never replay them. Proposed program_stage migration 20261128120300 is separate and not applied by Android development. See [phase-one plan](superpowers/plans/2026-11-28-v12-phase-one-ui.md).

- Phase two remains on the same development branch. Scope-specific student detail avoids cross-program statistics and personal edits from a secondary membership. New online enrollment uses proposed invoker RPC 20261128120400, atomic with additional program memberships; stage before release. Existing offline student creation still uses legacy outbox pending full reconciliation. See [phase two plan](superpowers/plans/2026-11-28-v12-phase-two-enrollment.md).
- Graphify 0.9.121 on main matches main's source tree; never copy a graph generated against a different source tree.

- Phase three: weekly session days (ISO Mon=1..Sun=7) and automatic/manual policy, distinct absent/excused, roster summary independent of search, confirmed close/reopen without creating attendance, read-only closed roster in new Android client. Proposed 20261128120500 migration; not applied. Existing direct cancellation shortcut removed from session list (server cancelled trigger is destructive). Old-client/server-wide immutability, atomic deletion and full restore remain pending. See [phase three plan](superpowers/plans/2026-11-28-v12-phase-three-sessions.md).

- Phase-three review found that the existing edit RPC rejects tilawah; proposed 20261128120600 keeps the signature and prohibits changing scope/type with dependent rows; stage-test before deployment. Prior phase 1/2 Android validation 46141876527 passed. Current phase-three tests pending; graph checksum gate still required.

## Known limitations / unfinished work
- External CircleCI reports "No configuration was found"; this already occurs
  on baseline `e900ccb`. The independent knowledge GitHub Actions job is the
  relevant tooling gate; do not mistake CircleCI configuration failure for tests.
- Contribution Home shortcut remains deferred in discovery decisions.
- Historical security-review findings require current-code verification; do not
  report them as present production vulnerabilities without reproducing them.
- Graphify 0.9.121 has partial AST results for 21 existing files and cannot infer
  all SQL/runtime relationships. Check `graphify-out/coverage.json`, then source.
- All supported documentation has structural sections/links; seven selected
  architecture/decision documents have semantic enrichment. No full-text LLM
  rescan is needed for ordinary future tasks.

## Knowledge workflow
- Mandatory: bootstrap/status → bounded Graphify query → targeted source read.
- After substantive changes: `graphify update .`, refresh affected semantic
  decisions if needed, verify/snapshot, commit reviewed graph+manifest.
- Preserve 64 existing Claude skills and the 15 Codex skills restored from the
  still-independent setup branch; Graphify is skill 16 in `.agents/skills/`.
- Never commit secrets, personal data, machine paths or raw extraction caches.
- Record a confirmed fix or lasting decision here with a source reference;
  do not save whole conversations or duplicate the graph.

## Phase-four development checkpoint (2026-11-28)
- On existing development PR #112, phase-four plan and checkpoint are in `docs/superpowers/plans/2026-11-28-v12-phase-four.md`. Compact UI, independent educational ratings and scoped views implemented in development; new SQL 120700/120800 not applied. Staff detach blocked rather than delete assignments. Association deletion and null-scope permissions, server closure, full integration tests remain open. No release or production change.
- Follow-up: 120700 protects assessed sessions from halaqa/type changes at the database boundary and rechecks authorization after row locking. Disposable SQL regression fixture source/migration/tests/educational_assessments_v12.sql is wired into execution-v12-validation.yml (education-sql); not yet verified by CI. Read-only production catalog confirmed 120700/120800 are absent, association_payments.association_id is NOT NULL with ON DELETE CASCADE, and centers/halaqas/profiles/students have nullable association FKs with NO ACTION. No separate Supabase staging branch available. Keep deletion blocked until payment preservation, independent-scope access and staging tests are verified; no production changes.
- Phase-four follow-up: review-only lifecycle proposal (source/migration/proposals/phase_four_lifecycle.sql) prevents association payment cascade and closes past started sessions using Gaza date, without attendance writes; private reopen exemptions preserve explicit reopen; separate cron activation script is not deployed. Disposable lifecycle and education SQL jobs passed on d2a101c. Full staging and legacy compatibility still needed.
- Reports now scope actual session halaqa IDs by kind, not just student membership, and segregate cache by exact scope. No scope means no data; unscoped student bonus points are not attributed to an activity. Program exports distinguish tilawah/tajweed/test pages; current guardian SQL tests are pending. Production read-only catalog showed broad independent-scope admin permissions and null-association RPC rejection; association removal remains disabled until role isolation and cross-scope assignments are preserved. No production writes or signing/release.
- Private association retirement proposal now retains a suspended historical association, records original relationship IDs in a restricted ledger, detaches only the association FK on each center/halaqa/student/profile and refuses cross-scope or inconsistent links. Retirement fixture passed before ledger addition (run 46151962554); ledger regression added and requires fresh CI. This is not an exposed production RPC: role matrix, legacy endpoints, cross-scope handling, active-organization UI and safe restoration are still blockers.
- Production activity_type enum has no tajweed value: tajweed is stored as tilawah in a tajweed session. Report exports, summary and guardian SQL now derive the display category from session_type; corrected fixture mirrors actual enum. New Android/SQL results pending after change. No production writes.
- Phase-four independent-scope follow-up: baseline head 632dd5d passed all CI. Proposed source/migration/proposals/phase_four_independent_scope.sql narrows admin/teacher/supervisor scope using exact association or explicit assignment, adjusts session RLS to actual halaqa (not creator), adds restrictive student and session-record checks, and updates v12 enrollment/transition/edit signatures without replaying applied migrations. Disposable role fixture passed 46153598615 on initial proposal, then direct-insert/primary-student-update checks were added; fresh results required. It is not a complete production RLS replacement: review profiles, centers, memberships, historical joins, related tables and every legacy SECURITY DEFINER path on full-schema staging before enabling retirement/transfer.
- Education outbox bulk fill now uses Room INSERT IGNORE with existing owner/dedupe index, preserves pending and blocked individual scores, while individual changes can replace earlier bulk. Tests cover durable reopen, account isolation, stale ack ID. Single-row phase-four PATCH checks compact returned id; empty/missing/mismatched response keeps the item blocked for review. New Android results pending; no Room schema migration or production changes.
- Owner requested a single end-of-day closure at 00:00 Asia/Gaza instead of five-minute polling. Production pg_cron is GMT; proposal uses 21:00/22:00 UTC candidates, a local-midnight guard and private per-day ledger. Only one actual closure can commit per local day, processing all batches; existing manual reopen exemptions, archive/plan handling, attendance and recitation preservation unchanged. No cron activation in production; new winter/summer and duplicate/batch fixture requires CI. No global cron timezone change.

## Seven-point follow-up (2026-11-28)
- On PR #112: previous tilawah lookup now distinguishes unavailable from a known empty cached/server result, preserves legacy JSON fields, offers explicit offline continuation and retry, and records lookup status. Session form restricts new activity options by session and defaults test/tilawah before student track. Updated old standalone session self-test; reduced mushaf frame and outer padding without altering 14:20 page geometry. Android CI and physical-screen checks still required; independent tilawah marks, persistent drafts, role isolation, web/server parity and signed release remain open. No production writes.
- Tilawah development: independent typed marks ledger and JSON preserving unknown rows/fields; same DK page gestures and dedicated tools, local account/session/student/range-scoped Room cache, stable client id, existing recitation outbox. Manual extras remain distinct from derived marks; corrected status does not reduce historical counts. Restore requires reselecting the same range. Prior-position linkage, full-form draft restore, exact line-range edge validation, batch previous-history loads, conflict policy, guardian summaries and RLS/end-to-end/visual tests remain open. No production database changes or release.
- CI run 46162470102 found Kotlin LocalDate/String mismatch in SessionSmartPlanCard. Parse the session date once, use typed eligibleLatest guard with future/mismatched/invalid-date tests; show explicit empty sard history. New recitation save rejects session/activity mismatch while existing history can still be edited. A local isolated Kotlin smoke harness passed ledger repeat, correction and manual-count checks; full Android build/Room tests remain pending.
- Tilawah full local draft now stores range/ratings/ward/notes/manual counters/history and marks together under one account/scope/session/student/record key, with serialized local writes and explicit save-draft action. Repeated marks can refer to prior record/mark ID; explicit follow-up acknowledgement is kept separate from historical error counts. Guardian selection clears old overview synchronously. Same-day history and concurrent-device server conflicts still need validation; no production deployment.
- Production read-only catalog verified restrictive phase4 and guardian record policies; can_view_profile depends on accessible students. Review-only additive scoped RPC proposals provide linked staff names, same-day ordered batch history, and guardian counts-only v13 summary with v12 fallback. Not deployed; SQL and Android integration require CI and staging.
- Gradle build logs 46162967038/46163322343/46163394946: 4 GiB in-process heap exhausted. Increase default heap and dynamically size CI (one worker, separate compile/test daemons). Review-only V2 scoring/revision guard proposal runs after existing V7 trigger, protects history and detects stale revisions without overwrite; client saves revision/base and blocks conflicts with preserved Outbox. No migration applied or main merge until CI gates pass.
