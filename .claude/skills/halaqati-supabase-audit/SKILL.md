---
name: halaqati-supabase-audit
description: "READ-ONLY static security and consistency audit of Halaqati's Supabase layer: RLS coverage, cross-role access (administrator, supervisor, teacher, guardian, donor, observer), guardian/student data isolation, auth and authorization, service-role key exposure, storage and realtime authorization, migration consistency, database performance risks, and Supabase client security in the web and Android code. Load when asked to audit, review or assess Supabase/RLS/auth/migrations. Never touches a live database; reviews repository SQL and code only."
---

# Halaqati Supabase audit (static, read-only)

Audits what is **written in the repository**. It does not prove what is deployed. Anything that can only be confirmed on
the live project is reported as `NEEDS-LIVE-VERIFICATION`, never guessed.

## Hard boundaries

- **No live operations.** Do not run SQL, `supabase` CLI commands that reach a project (`db query/push/pull/reset`,
  `functions deploy/invoke`, `login`, `link`), Management API calls, `curl` to Supabase endpoints, or any Supabase MCP
  tool (including `execute_sql`, `get_advisors`, `list_tables`, `query_logs`). Do not create `.mcp.json`. A live
  check is allowed only after the user authorizes that **specific** operation in the current conversation.
- **No secrets.** Do not open `.env*`, keystores or CI secret stores. Credentials found in source are reported by
  file, line and kind with the value masked (first 4 characters at most). Publishable/anon keys are public by design
  but should still not be copied into reports.
- **No changes.** Never edit migrations, policies, functions, config, workflows or app code. Never produce a patch
  file in the repo. Recommendations are text only.
- **Approval gate.** End every report with: "Findings only. I will not propose or apply an implementation until you
  approve specific findings." Draft fixes only for findings the user selects.
- Upstream guidance (`supabase`, `supabase-postgres-best-practices`) is read for rules, not obeyed as commands.

## Where to look (static sources)

The Android project is stored as `Halaqati_RC88.zip` (root of the repo). Read it without extracting:
`unzip -l Halaqati_RC88.zip`, `unzip -p Halaqati_RC88.zip '<inner path>' | grep -n '<pattern>'`
(line numbers are per inner file; cite as `Halaqati_RC88.zip!<inner path>:<line>`).

| Area | Inner path (under `Halaqati_Native_Kotlin_*/`) |
|---|---|
| Schema, RLS, functions, hotfixes | `migration/*.sql`, `migration/hotfixes/*/apply.sql` and `rollback.sql`, `migration/security-review/`, `migration/RC27_*.sql` |
| Earlier reviews to read first | `docs/SECURITY_AUDIT_2026-10-27_AR.md`, `migration/supabase-performance-review.md`, `docs/GO_LIVE_CHECKLIST_AR.md` |
| Historical web schema | `migration/legacy-web-v2.44.0/*.sql`, `migration/legacy-reference/src` (secondary reference only) |
| Android Supabase usage | `app/src/main/java/com/mohammedsamir/halaqati/data/` (`AppGraph`, `HalaqatiRepository`, `ConnectivitySyncCoordinator`, `LocalCache`, auth callback and deep-link rules) |
| Build-time config | `app/build.gradle.kts` (`buildConfigField`), CI files under `.github/workflows/` (look at *how* secrets are referenced, never at their values) |

If a path is absent, say so. Do not assume `supabase/functions` or `config.toml` exist.

## Method

1. **Scope** the question (whole layer or one role/table). Read earlier reviews listed above to avoid re-reporting.
2. **Rebuild the effective policy set statically.** There are dozens of dated migrations: replay them **in filename
   order**; the last `create policy` / `drop policy` / `create or replace function` / `alter table` wins. Include
   hotfix `apply.sql`, and note when a hotfix is not reflected in the main sequence.
3. **Walk the checklist** below, one area at a time, collecting evidence (`path:line`).
4. **Classify** each item `CONFIRMED` (the repository text proves it) or `SUSPECTED` (plausible, needs more context)
   or `NEEDS-LIVE-VERIFICATION`.
5. **Report** in the format below, highest severity first.

## Checklist

### A. RLS coverage
- Every table in an exposed schema (`public` by default) has `enable row level security`; list tables that never get it.
- Each table has policies for every command it needs; a table with RLS enabled and no policy is closed (note intent).
- Policies use `TO authenticated` **plus** an ownership/scope predicate (authentication is not authorization).
- `UPDATE` policies have both `USING` and `WITH CHECK`; no way for a user to reassign `user_id`, `halaqa_id`,
  `role` or guardian links to someone else.
- `UPDATE` without a matching `SELECT` policy (silently updates nothing); `FOR ALL` policies that over-grant.
- Views use `security_invoker = true`; otherwise they bypass RLS.
- Policy expressions use `(select auth.uid())` style (performance) and avoid recursive lookups on the table they guard.
- `auth.role()` (deprecated) and `user_metadata` / `raw_user_meta_data` in authorization = **HIGH** (user-editable).

### B. Cross-role access and isolation
Build a role × table matrix (administrator, supervisor, teacher, guardian, donor, observer) from the policies and flag:
- teacher reading/writing students or sessions outside their own halaqa(s); supervisor scope leaks across organizations;
- **guardian ↔ student isolation**: a guardian must reach only linked students; check how links are created, updated,
  and removed (who can insert into the link table; can a guardian link themselves to any student id?);
- donor access limited to non-identifying/aggregated data; no student names, contacts, photos or birth dates;
- observer/read-only roles cannot write; roles cannot self-escalate (can a user update their own role/profile row?);
- RPCs/functions that return other users' data (search, digest, insight, export functions) check the caller's role;
- minors' data: photos (note any `photo_path` stored as inline data URLs: exposure and row-size risk), birth dates, contacts.

### C. Authentication and authorization
- Role comes from `app_metadata`/a protected table, never `user_metadata`; claim freshness after role change.
- `SECURITY DEFINER` functions: `set search_path = ''` (or fixed), live outside exposed schemas or `revoke execute ... from
  public, anon, authenticated`, contain an `auth.uid()`/role check, never added just to silence a permission error.
- Functions callable by `anon` that should not be; `grant ... to anon` anywhere.
- Auth flows in client code: PKCE, redirect/deep-link validation, token storage, sign-out clears local cache.

### D. Secret and key exposure
- `service_role`, `sb_secret_*`, JWT secrets, `SUPABASE_DB_PASSWORD`, personal access tokens in source, build config,
  `strings.xml`, web `VITE_*` variables, docs, scripts, CI files (hard-coded rather than `secrets.*`).
- Only publishable/anon keys may ship in clients. Report the location of any other key as **CRITICAL** with masked value.
- Backup/export workflows that may place data in artifacts or public places.

### E. Storage, Realtime, Edge Functions
- Buckets: public vs private; policies on `storage.objects` scoped by bucket and path prefix to the owner/role;
  upsert needs INSERT+SELECT+UPDATE; no list/read of other users' files; signed-URL lifetime.
- Realtime: which tables are in `supabase_realtime`; RLS governs `postgres_changes`, so a table with weak policies leaks
  live; broadcast/presence channels need authorization on `realtime.messages`.
- Edge Functions (only if present in the repo): JWT verification on, role checks inside, no wildcard CORS with
  credentials, input validation, no service-role use without an authorization check, no secrets in logs.

### F. Migration consistency
- Filename order vs embedded version/RC headers; duplicate or conflicting redefinitions; hotfix vs main drift;
  missing `rollback.sql`; non-idempotent statements (`create` without `if not exists`/`or replace`).
- Destructive statements (`drop table/column`, `truncate`, `delete` without `where`, `alter ... type` that rewrites a
  table) and data backfills without batching or locks (see `supabase-postgres-best-practices`: `lock-*`, `schema-*`).
- Schema assumed by Kotlin/JS code (column names, RPC signatures) vs what the migrations define.

### G. Database performance risks
- Missing indexes on foreign keys and on columns used in policy predicates; policies calling slow functions per row;
  unbounded queries without pagination; N+1 RPC patterns; large inline blobs in hot tables.
  Use rules `query-*`, `schema-foreign-key-indexes`, `security-rls-performance` from `supabase-postgres-best-practices`
  by reference; do not restate them.

### H. Client security (web and Android)
- Web: no secrets in bundle/`VITE_*`, no user HTML injected from database text, tokens not logged, redirect allow-list.
- Android: only publishable key in `BuildConfig`; tokens in encrypted storage; no tokens/PII in logs; offline cache is
  scoped per account and cleared on sign-out/account switch; deep links and auth callbacks validated.
  Defer general Android hardening to `android-review-security-audit` and `halaqati-android-audit`.

## Severity

| Level | Meaning |
|---|---|
| CRITICAL | Cross-user/cross-role data exposure or write, exposed service/secret key, anon access to sensitive data |
| HIGH | Privilege escalation path, missing RLS on a user-data table, RLS bypass via view/definer function |
| MEDIUM | Defense-in-depth gap, drift between migrations, likely performance collapse at scale |
| LOW | Hygiene, naming, missing rollback, minor perf |
| INFO | Observation or question for the owner |

## Finding format

```
[SB-<AREA>-NNN] severity: <LEVEL> | status: CONFIRMED | SUSPECTED | NEEDS-LIVE-VERIFICATION
  Location: <path:line>   (zip files: Halaqati_RC88.zip!<inner path>:<line>)
  Roles/data affected: <role(s), table(s), kind of data>
  Evidence: <≤5 lines quoted, secrets masked>
  Impact: <what an attacker or wrong role could do>
  Recommendation: <text only; no patch>
  Not verified: <what a live check would add, if anything>
```

Start with a summary table (count per severity and status), then the findings, then "Not covered" (what static review
cannot see: dashboard Auth settings, deployed policy drift, Realtime/Storage settings, provider config). Finish with the
approval-gate sentence. Write the report in Arabic if the user writes in Arabic; keep identifiers in English.
