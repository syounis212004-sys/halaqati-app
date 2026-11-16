# RC28 verification — 2026-10-23

Baseline: syounis212004-sys/halaqati-app main 2c4c495e1162b17212b814c804cea72c7ed3e350, Halaqati_RC27_50.zip. Release: 2.64.0 / versionCode 26400.

## Passed
- All 20 nonvisual source/release gates invoked by the original workflow.
- Additional compile-sanity, Compose-risk, compiler-regression and Kotlin-import source checks. These source checks do not establish APK compilation.
- All 35 pure Kotlin self-test entrypoints, including six new sync-presentation cases (known RPCs, query stripping and safe unknown names). Palette tests report minimum 4.89:1 contrast.
- Independent reviews of CoreScreens and the full branch; identified aggregation, scrolling, navigation-selection and reduced-motion findings were addressed and rechecked. See RC28_REVIEW.md.
- Workflow YAML parsing and shell syntax for all 18 run blocks. Release archive checksum and extracted source inventory are validated at packaging.

## Limitations
- Chromium report/certificate fixture checks were attempted. The execution environment blocked the browser process with socket() Operation not permitted. The existing visual gates remain in GitHub Actions. No report CSS or certificate rendering engine changed.
- No physical-device or emulator visual check was available. Validate Arabic RTL, large font, keyboard, both themes, density settings, role navigation, notes/actions and reduced motion on the built app.
- No production database changes or new signing identity are required by this interface release. Prior server hotfix SQL is retained only for reproducibility.
- GitHub write attempt returned 403 Resource not accessible by integration; no uploaded commit or workflow run is claimed.

## Android compilation
- `:app:compileDebugKotlin` and `:app:testDebugUnitTest`: BUILD SUCCESSFUL; all 124 Android JVM tests passed, zero failures/errors/skips.
- Compilation used JDK 17, Gradle 8.13 and Android SDK 36 in a separate verification checkout; every application Kotlin source was byte-identical to the release source.
- Temporary local Google Services configuration was used only to enable compilation; it is excluded from the delivered source. Runtime Firebase, signed release APK and physical-device behavior were not validated locally. The workflow uses the existing real secrets and signing setup.
- `:app:compileDebugAndroidTestKotlin` and `:app:lintDebug`: BUILD SUCCESSFUL. Instrumented UI tests compiled; they were not executed on a device/emulator. Existing lint warnings are not treated as new runtime guarantees.
