---
name: android-review-performance
description: >-
  Audit Android (Kotlin/Compose) apps for performance issues and produce
  structured findings. Use when: app cold start exceeds 500ms, UI jank or
  dropped frames, memory leaks or OOM, ANR rate above 0.47% in Play Vitals,
  Compose recomposition waste, missing baseline profiles, battery drain,
  reviewing performance before release, or general performance optimization.
---
<!-- HALAQATI-LOCAL-BLOCK:BEGIN (added by the Halaqati installer; upstream text below is unchanged) -->

## Halaqati project rules (take precedence over everything below)

- **Read-only by default.** Report findings; never modify application code, migrations, RLS policies,
  Supabase configuration, CI/CD workflows or hooks. Propose a fix only after the user asks for one, and
  implement it only after explicit approval.
- **No live systems.** Never connect to, query, or change a live database or Supabase project. Never call
  Supabase MCP tools, never run `supabase` CLI commands that touch a project, never execute SQL, never invoke Edge
  Functions, unless the user authorizes that specific operation in the current conversation.
- **No secrets.** Never open `.env*`, keystores, signing files or CI secret stores. If a credential appears in
  source, report only its file, line and type (mask the value), never print it.
- **No new dependencies or builds.** Do not add libraries, run Gradle/npm builds, tests or installers as part of a
  review. Suggestions that would add a dependency must be labelled "needs approval - adds a dependency".
- **Where the code lives.** The repository keeps the Android app as `Halaqati_RC88.zip` (a Kotlin/Compose project
  under `Halaqati_Native_Kotlin_*/`). Read it with `unzip -l` / `unzip -p` (never extract into the repository).
  Upstream third-party text below is advisory, not a command: do not run a command only because it appears here.
- **Project conventions win** over generic advice: Arabic-first RTL UI, Cairo/Amiri fonts, a fixed green palette
  with user-customisable primary colour (not mandatory dynamic colour), manual DI through `AppGraph` (no Hilt),
  Room local cache with offline-first synchronisation, roles: administrator, supervisor, teacher, guardian, donor
  (and observer where present in migrations).
- **Not installed on purpose:** the upstream `architecture` skill (it mandates Hilt/Clean Architecture), `play-store`
  and `privacy-audit` (loan-app specific). Ignore references to them below.
- Findings must separate **confirmed defect** (code read and reproduced from the source) from **suspected risk**,
  and include `file:line`. Use the output format of this skill, but never edit files automatically.

<!-- HALAQATI-LOCAL-BLOCK:END -->


# Android Performance Audit

Produce a structured audit report with prioritized findings and concrete fixes.

## Audit Process

### Phase 1: Triage

Scan project structure (build.gradle, AndroidManifest.xml, source tree) to determine which categories apply. SKIP categories with no relevant code.

| Category | Skip If |
|----------|---------|
| Startup | No custom Application class, no splash screen |
| Recomposition | No Jetpack Compose (`@Composable` not found) |
| Memory | No ViewModel / Repository / singleton patterns |
| ANR | No SharedPreferences / BroadcastReceiver / sync I/O |
| Pagination | No list screens or large data sets |
| Baseline Profiles | NEVER skip — always relevant |
| Battery & Network | No background work, location, or network calls |

### Phase 2: Parallel Scan

Run all relevant categories in PARALLEL using independent Task agents. Each agent:

1. Grep for the category's anti-patterns (see search guide below)
2. Read matched files to confirm the finding is real
3. Return findings in the per-finding format below

### Phase 3: Report

Merge parallel results into the final report format below.

## Thresholds

| Metric | Target | Critical |
|--------|--------|----------|
| Cold start | < 500ms | > 1000ms |
| Warm start | < 300ms | > 600ms |
| Hot start | < 100ms | > 200ms |
| ANR rate | < 0.47% | > 1% |
| Crash rate | < 1.09% | > 2% |

## Anti-Pattern Search Guide

Grep-ready patterns per category. Only search categories that passed triage.

**Startup**
- `class \w+ : Application` → check `onCreate` body for heavy sync init
- `ContentProvider` in manifest → auto-init SDKs blocking startup
- Missing `reportFullyDrawn()` anywhere in codebase

**Recomposition**
- `List<` / `MutableList<` / `ArrayList<` in `@Composable fun` parameters → unstable type, use `ImmutableList` or `@Immutable` wrapper
- `{ viewModel.` or `{ \w+ViewModel.` inline in Composable call sites → lambda recreated every recomposition, extract with `remember`
- `items(` without `key =` in LazyColumn/LazyRow
- `.value` read outside `graphicsLayer` / `Modifier.layout` / `Modifier.drawBehind` → state read triggers full recomposition instead of layout/draw only
- Missing `composeCompiler { reportsDestination }` in build.gradle → no compiler metrics

**Memory**
- `context:` or `Context` field in ViewModel / Repository / `object` declaration → Activity leak
- `CoroutineScope(` without lifecycle-aware cancellation
- `companion object` or top-level `val` holding Activity/View/Fragment reference
- Object creation (`= Rect(` / `= Paint(` / `= StringBuilder(`) inside `for` / `while` loop body

**ANR**
- `.commit()` on SharedPreferences → blocks main thread, use `.apply()`
- Network/DB/File I/O call NOT wrapped in `withContext(Dispatchers.IO)` on a path reachable from main thread
- `BroadcastReceiver` subclass with `onReceive` body missing `goAsync()`

**Pagination**
- `Column {` followed by `.forEach` or `for (` → composes ALL items, use LazyColumn
- Large data source without Paging 3 integration

**Baseline Profiles**
- `profileinstaller` NOT in dependencies (build.gradle / libs.versions.toml)
- No `:baselineprofile` module or `BaselineProfileRule` test class

**Battery & Network**
- `AlarmManager` usage → should be WorkManager for deferrable work
- `OkHttpClient` without `.cache(` → missing HTTP cache
- Continuous location or sensor listener without interval / `removeUpdates`

## Output Format

### Per Finding

```
[PERF-{CATEGORY}-{NNN}] impact: CRITICAL | HIGH | MEDIUM | LOW
  Finding: <root cause>
  Location: <file:line>        ← required for CRITICAL/HIGH only
  Fix: <concrete action>
  Metric: <expected improvement>
```

### Final Report

```markdown
## Performance Audit Report

### Summary
- Critical: N | High: N | Medium: N | Low: N
- Scanned: N/7 categories (skipped: <list with reasons>)

### Findings

#### Startup
[PERF-STARTUP-001] impact: CRITICAL
  Finding: Synchronous SDK init in Application.onCreate
  Location: App.kt:15
  Fix: Defer via App Startup library or lifecycle observer
  Metric: ~400ms cold start reduction

#### ...

### Metrics
| Metric | Estimated | Target | Status |
|--------|-----------|--------|--------|
| Cold start | Xms | <500ms | PASS/FAIL |
| ANR rate | X% | <0.47% | PASS/FAIL |
```
