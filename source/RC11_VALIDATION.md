# RC11 validation record — 2026-09-26

Version: `2.46.1-RC11` / `versionCode 24611`.

## Targeted validation

- `scripts/run-rc11-targeted-selftest.sh`: PASS.
- `SmartPlanPhaseEngineSelfTest`: PASS, including flexible redistribution, optional carried shortfall, manual start behavior, independent Sard anchor, early completion, open Sard, and per-student Hifz exception.

## Full pure-Kotlin validation

`scripts/run-kotlin-selftests.sh`: PASS for all 19 self-test entry points. Every compiler/test process is bounded (`kotlinc` max 300s, each test max 30s).

## Static / contract regression validation

36 bounded static gates passed, including release/parity contracts, manifest/auth/offline/privacy checks, RC7, RC8.x, RC9.x, RC10.x, and RC11.

Key final RC11 checks cover:
- only `general_hifz` + `tathbit` student tracks;
- Smart Plan parent + Hifz/Sard phases + per-student phase state;
- independent Sard/ Memorization anchors;
- phase-aware real Sard assignment using equal/flexible redistribution;
- manual/range start overriding historical Sard anchor;
- range endpoint cap;
- frozen Hifz/Sard report metrics;
- optional, never automatic, previous Sard shortfall carry;
- bulk transition Preview + exceptions;
- phase edit impact Preview;
- student profile Hifz/Sard separation;
- guardian phase notifications;
- additive migration/RLS/source contract.

## Android Gradle verification policy

This local sandbox has `kotlinc` but does not have a usable Gradle installation/wrapper or the Android dependency cache, so no claim is made that an Android Gradle compile was executed locally. The supplied GitHub Actions workflow performs the remaining Android compiler/JUnit/lint/APK gates with a strict maximum of 540 seconds per Gradle command, heartbeat output, diagnostic capture near timeout, and no `clean` task.

The CI order is targeted-first, then comprehensive: RC11 source self-test → static gates → Kotlin compile → targeted `SmartPlanPhaseEngineTest` → complete unit tests → lint → assemble → APK identity/signing verification.

## GitHub compile follow-up — BUILD FIX1

The first GitHub Actions RC11 run reached `:app:compileDebugKotlin` and failed after about 2m13s with one Kotlin compiler error in `HalaqatiRepository.kt`: `phaseRow` is nullable when `phaseAwarePlan(...)` is called with an engine-phase override, but the UI title fallback dereferenced it directly. BUILD FIX1 changes the title expression to a null-safe fallback (`phaseRow?.title?... ?: baseRow.title`). No Smart Plan, QuranRangeEngine, anchor, migration, schema, or track behavior was changed by this fix.

After the patch, the bounded local RC11 structural gate, targeted phase-engine self-test, all 19 pure-Kotlin self-tests, and the available comprehensive static/contract gates all pass. Android Gradle compile/JUnit/lint/assemble still require the next GitHub Actions run for confirmation; this record does not claim those post-fix Android gates already passed.
