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
