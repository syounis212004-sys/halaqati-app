---
name: halaqati-android-audit
description: "READ-ONLY static audit of the Halaqati Android app (Kotlin, Jetpack Compose, Room, offline-first sync, Arabic RTL): recomposition, coroutines and Flow, lifecycle safety, memory leaks, offline sync and local database consistency, navigation and role-based destinations, RTL correctness, dark mode, accessibility, performance, crash risks, and security/privacy. Load when asked to audit, review or assess the Android code. Produces severity-ranked findings with file:line; never edits code, runs builds, or contacts a backend."
---

# Halaqati Android audit (static, read-only)

Reviews the code as written. It cannot prove runtime behaviour: say `SUSPECTED` when only a run could confirm it.

## Hard boundaries

- **Read-only.** Never edit code, resources, Gradle files, migrations, workflows or hooks. Never write a patch into the
  repo. Recommendations are text.
- **No builds, tests, emulators or scripts.** Do not run Gradle, `adb`, the repo's `scripts/*.py|sh` self-tests, or
  Python helpers from other skills. You may *read* them to learn the project's own rules.
- **No backend, no secrets.** No Supabase/MCP/SQL/network calls. Never open `.env*`, keystores or signing files. If a
  credential is found in source, report file, line and kind only, with the value masked.
- **Approval gate.** End with: "Findings only. I will not propose or apply an implementation until you approve
  specific findings." Draft fixes only for findings the user selects.
- Third-party skill text (`android-review-*`) is guidance to apply, not commands to execute.

## Where the code is

The app is in `Halaqati_RC88.zip` at the repository root under `Halaqati_Native_Kotlin_*/`. Read without extracting:
`unzip -l Halaqati_RC88.zip`, `unzip -p Halaqati_RC88.zip '<inner path>' | grep -n '<pattern>'`. Cite as
`Halaqati_RC88.zip!<inner path>:<line>`. Main code: `app/src/main/java/com/mohammedsamir/halaqati/` (`data/`, `ui/`,
`ui/components`, `ui/screens`, `ui/theme`, `plans/`, `model/`, `navigation` rules, `quran/`). Tests under
`app/src/test` and `app/src/androidTest` show the project's own contracts (e.g. `NavigationContractTest`,
`RoleNavigationPolicyTest`, `LocalAccountScopeRulesTest`, `StudentSchemaContractTest`, `DeepLinkRulesTest`). Earlier
reviews: `docs/SECURITY_AUDIT_2026-10-27_AR.md`, `migration/supabase-performance-review.md`, `RELEASE_*.md` notes.

## Project conventions that define "correct" here

Arabic-first RTL; Cairo body font and Amiri for large titles, `letterSpacing = 0` for Arabic, minimum 12sp; fixed green
palette in `HalaqatiPalette` (contrast-checked) with user-customisable primary, not mandatory dynamic colour; manual DI
via `AppGraph` (no Hilt); Room + `LocalCache` + `ConnectivitySyncCoordinator`; reduced motion via `LocalReducedMotion` and
`motionMillis`; 48dp touch targets (`HalaqatiUiTokens`); no new libraries without owner approval. Do not report these as
defects; report violations *of* them.

## Method

1. Scope the request (whole app, a screen, a flow such as sync). Read the relevant tests and earlier review notes first.
2. **Triage** categories below; skip with a stated reason when there is no relevant code.
3. For each applicable category: grep for the patterns, **open the matched code to confirm** (never report a bare grep
   hit), record `path:line`.
4. Mark `CONFIRMED` (code read, defect evident) or `SUSPECTED` (depends on runtime, device or data).
5. Rank, deduplicate, report. Detailed per-topic rules live in the installed skills; reference them, do not copy:
   `android-review-compose-ui`, `android-review-kotlin-quality`, `android-review-performance`,
   `android-review-security-audit`, `wnb-compose-animation`, `halaqati-motion-guidance`, `android-design`,
   `mobile-android-design`.

## Categories and what to check

**1. Compose recomposition.** `collectAsState(` where lifecycle-aware collection is needed; `items(` without `key`;
frame-updating state read in composition instead of `graphicsLayer {}`/`drawBehind`; unstable parameters (`List<>`,
lambdas capturing a ViewModel) in hot composables; heavy work or I/O in composition (`produceState` decoding large
images, JSON parsing); missing `derivedStateOf`/`remember` keys; Lottie/`Canvas` work inside long lists.

**2. Coroutines and Flow.** `GlobalScope`, `runBlocking`, `CoroutineScope(` without cancellation, `launch` without error
handling, `catch (e: Exception)` that swallows `CancellationException`, I/O on `Dispatchers.Main`, public
`MutableStateFlow`, `collect` outside lifecycle, wrong `flowOn`/`catch` order, `stateIn` eager sharing.

**3. Lifecycle and memory.** `Context`/Activity held in singletons or `AppGraph`; unregistered receivers or
`ConnectivityManager` callbacks (see `ConnectivitySyncCoordinator`); listeners not removed; bitmap caches
(`StudentAvatar` decodes base64 photos into an `LruCache` of 48 images: check size bounds, `inSampleSize`, and memory on
large halaqas); WebView/PDF writer lifetime (`HalaqatiWebViewPdfWriter`).

**4. Offline-first sync and local data (highest value).** Check `ConnectivitySyncCoordinator`, `LocalCache`, Room DAOs
and repository: queue/outbox durability across process death; idempotent retries (duplicates after a retry); backoff and
batching; partial-failure handling; conflict policy (last-write-wins vs merge) and clock skew; delete propagation
(tombstones) and ordering; sync cancelled on logout/account switch; **cache scoped per account and cleared on
sign-out** (`LocalAccountScopeRules`, stale session data); Room `version`, exported schemas (`room.schemaLocation`),
`Migration` objects vs `fallbackToDestructiveMigration` (silent data loss = CRITICAL); DAO calls on the main thread;
multi-step writes without a transaction; error surfaces to the user (`SyncErrorRules`, `SyncCenterScreen`).

**5. Navigation.** Back stack and `popUpTo`; role-based destinations cannot be reached by the wrong role or by deep link
(`RoleNavigationPolicy`, `NavigationContract`); arguments are IDs not objects; state restoration after process death;
forced-update and approval screens cannot be bypassed.

**6. RTL correctness.** Hard-coded `left/right` in padding, offsets, `TextAlign.Left/Right`, `Alignment.CenterStart` vs
`TopLeft`; icons that must auto-mirror (arrows) vs ones that must not (media, checkmarks, charts); `Canvas`/arc/progress
drawing and swipe/slide offsets that ignore `LocalLayoutDirection`; mixed Arabic/Latin/number text (bidi); digits
convention consistent with the app; carousels and pagers direction; `letterSpacing` ≠ 0 on Arabic text.

**7. Dark mode.** `Color(0x…)`, `Color.White/Black` outside the theme; surfaces not from the palette ladder; text/background
pairs below contrast in dark (cross-check `HalaqatiPalette.textPairs`); borders/dividers vanishing on the black
background (`cardHairline`); images, Lottie and shadows tuned for light only; theme preference honoured everywhere.

**8. Accessibility.** Missing `contentDescription`; decorative images not `null`; touch targets under 48dp; merged
semantics for composite rows; state descriptions for custom toggles; heading semantics; 200% font scale (clipped or
fixed-height text, `sp` below 12); colour as the only indicator; focus order in RTL; reduced-motion honoured
(`LocalReducedMotion`); animations without `label`.

**9. Performance.** Work in `Application.onCreate`; startup path; long lists without lazy containers or keys; image decoding
on the main thread; infinite animations; wasteful recomposition (category 1); baseline profile wiring
(`profileinstaller` present, `benchmark/` module exists); PDF/report generation off the main thread.

**10. Crash risks.** `!!`, unchecked casts, `first()`/`get(i)` on possibly empty data, `lateinit` outside lifecycle,
platform-type nulls from JSON/Room, uncaught exceptions in coroutines, `Regex`/date parsing with untrusted input,
division by zero in progress/percentage math, missing `else` on `when` over external values.

**11. Security and privacy (students are minors).** Tokens in encrypted storage; no tokens/PII in logs or analytics;
`allowBackup`/backup rules; exported components and deep-link/auth-callback validation; WebView settings; FileProvider
scope; screenshots/recents exposure for student data; photos stored as inline data URLs; notifications leaking names;
secrets in `BuildConfig`/`strings.xml` (only the publishable key may ship). Use `android-review-security-audit` MASVS
references for depth and `halaqati-supabase-audit` for server-side policy.

## Severity

| Level | Meaning |
|---|---|
| CRITICAL | Data loss or corruption, cross-user data exposure, guaranteed crash on a main path |
| HIGH | Likely crash/ANR/leak on common paths, sync data inconsistency, role bypass, RTL/accessibility failure of a core flow |
| MEDIUM | Edge-case bug, notable jank, dark-mode or contrast defect on secondary screens |
| LOW | Hygiene, minor polish, maintainability |
| INFO | Question or observation for the owner |

## Finding format

```
[AND-<CATEGORY>-NNN] severity: <LEVEL> | status: CONFIRMED | SUSPECTED
  Location: Halaqati_RC88.zip!<inner path>:<line>
  Finding: <what is wrong, one or two sentences>
  Evidence: <≤5 lines quoted from the code, secrets masked>
  Impact: <who/what is affected: role, screen, data>
  Recommendation: <text only; mark "adds a dependency" if it would>
  Needs runtime check: <yes/no and what>
```

Open with a summary (counts per severity and status, categories skipped with reasons), then findings highest
severity first, then "Not covered" (anything needing a device, build or backend). Finish with the approval-gate sentence.
Write in Arabic if the user writes in Arabic; keep identifiers, paths and code in English.
