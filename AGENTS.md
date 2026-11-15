# Halaqati Android — agent instructions

## Scope and architecture

This Git root is `syounis212004-sys/halaqati-app`. The independent web repository is
`syounis212004-sys/halaqati-web`; use a sibling checkout, never a subdirectory of this Git root.
Both have equal priority. Plan shared API, database, and design changes together, then make
separate branches, commits, checks, and PRs. Inspect local changes before editing.

The tracked Android source is **Halaqati_RC89.zip**, containing
`Halaqati_Native_Kotlin_v2.50.0_RC15/`. It is native Kotlin/Jetpack Compose,
AGP 8.13.2, Kotlin/Compose compiler 2.2.21, Gradle 8.13, Java target 17,
compile/target SDK 36, min SDK 23. This is not the web/Capacitor application.
`app/src/main/java/com/mohammedsamir/halaqati/` contains UI, domain/data code,
Room persistence, networking and synchronization. `migration/` contains SQL;
`design/tokens.json` is the existing shared token contract.

## Setup and commands

Run from this repository root:

```sh
bash tools/codex/bootstrap.sh
python3 tools/codex/native-source.py extract
source tools/codex/env.sh
python3 tools/codex/gradle.py :app:testDebugUnitTest
python3 tools/codex/gradle.py :app:lintDebug
python3 tools/codex/gradle.py :app:assembleDebug
python3 tools/codex/workspace.py --root ..
python3 tools/codex/design-parity.py --workspace ..
bash tools/codex/prepare-pr.sh
```

`gradle.py` respects the Cloud proxy and system Java trust store. It does not patch
application dependencies. Kotlin and Compose tooling is supplied by the existing Gradle
plugins; Android Lint and JUnit/Compose testing dependencies are already in the source.

Normal builds need `app/google-services.json`, or the private environment binding
`HALAQATI_FIREBASE_GOOGLE_SERVICES_JSON` (the `_BASE64` form is also supported).
The wrapper restores/removes a bound file for that process without printing its contents.
For isolated compilation/unit/lint checks only, explicitly set
`HALAQATI_OFFLINE_ANDROID_TEST=1`; it uses the clearly synthetic setup fixture.
This mode is restricted to the three debug/local check tasks above and does not verify
Firebase, login or push runtime behavior. PR CI uses this mode without release secrets.

The extraction under `.codex-work/native/` is **ignored**. Never claim edits there are
included in a PR. `native-source.py status` shows archived source changes; `export
--output /path/outside/checkout/review.zip` prepares a new archive for review. Before
replacing the tracked archive, review its contents/diff and update the matching SHA-256
in the relevant release workflow; do not trigger release workflows during setup.
Extraction refuses an archive change rather than overwriting existing edits.

## Engineering and product standards

Read the closest existing code and `.agents/skills/halaqati-app-development/SKILL.md`
before domain work. Follow established Kotlin/Compose patterns, preserve public contracts,
keep composables focused, hoist state, and keep database/network work off the main thread.
Preserve the original `.claude/skills/` instructions; Codex adapters are under `.agents/skills/`.
The inspected code wins over a stale skill's stack assumptions.

Arabic and RTL are the default. Preserve Cairo typography, existing colors/tokens, both
themes, logical start/end spacing, semantic icons, font scaling, contrast and 48dp touch
targets. Model loading/empty/error/disabled states. See `tools/codex/UI-QA.md`.
Progress uses pages only; preserve exactly `مسار الحفظ العام` and `مسار التثبيت`.
Persisted smart-plan assignments are authoritative. Rankings remain points based.
Totals must derive from synchronized persisted data. Never fabricate next assignments.

## Validation and security

Run focused JUnit tests, Android Lint and debug assembly for native changes. Use existing
`app/src/androidTest` Compose tests on an available test device; check `adb devices -l`.
An APK build is not emulator/UI verification. SQL parsing is local only:
`$HOME/.local/share/halaqati/sql-venv/bin/python tools/codex/sql-check.py <sql-path>`.
Check `tools/codex/VERIFICATION.md` for the setup baseline; preserve failing checks.

Do not modify production Supabase data, apply migrations, weaken RLS, commit credentials,
or put service-role keys in a client. Use synthetic test accounts/data and block remote
traffic in browser fixtures. Connected Supabase access during setup is read only.
Store private configuration in environment settings or ignored local files.

## Git and PRs

Work on a short-lived branch; never commit directly to main, force-push, or discard user
edits. Review `git diff` and staged files. Push and merge require explicit user approval.
Prepare a separate Android PR with changes, commands/results, failures and limitations.
Existing publishing workflows can write to the web repository; PR validation uses
`.github/workflows/codex-checks.yml` and must not publish or access release secrets.
