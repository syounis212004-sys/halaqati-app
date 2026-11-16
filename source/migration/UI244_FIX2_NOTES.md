# UI 2.44 Restoration – T6 FIX2

This checkpoint restores the user-reported shell and visual regressions without reverting the Native Kotlin/Jetpack Compose migration.

## Restored in this checkpoint

- 2.44 Golden Master palette and Cairo global typography.
- Right-side RTL navigation drawer with independently scrollable menu body and fixed footer/logout.
- Exact v2.44 navigation icon vectors ported to Android VectorDrawable resources.
- Role-aware v2.44 drawer menu ordering and labels.
- Five-destination role-aware bottom navigation using the restored vector icons.
- v2.44-style top app bar with role label, menu/back, notifications, sync state, and optional refresh.
- Native Google sign-in using Android Credential Manager instead of launching Chrome.
- Account-scoped Google ID-token exchange through Supabase Auth with nonce protection.
- v2.44 appearance presets (green / blue / brown / purple), light/dark/system modes, density controls, reset appearance.
- Password change restored in Settings.
- Rich student cards with direct “تسميع اليوم”, “الملف الشخصي”, “الخطة”, and “تعديل البيانات” actions.
- v2.44-like session cards and daily-session controls.
- Restored feature hero styling for Report Studio and Certificate Center.
- UI/UX Pro Max static guard extended to the new navigation shell.

## Build status

Local source-contract, parity, navigation, Cairo, offline-account isolation, auth/privacy, Compose-risk, UI 2.44 restoration, UI/UX Pro Max, and Kotlin pure self-tests pass.

A full Android/Kotlin/Gradle compile is intentionally left to the paired GitHub Actions workflow because this local execution environment does not provide the Android SDK/Gradle toolchain. The workflow refuses to upload an APK unless Kotlin compilation, unit tests, lint, assemble, and APK verification all pass.

## Still not final parity

This is a checkpoint, not the final 2.46 release. The parity matrix still contains incomplete feature areas (for example full Report Studio parity, guardian competition/achievements, account-name update, app-update flow, some student archive/override management and remaining page-by-page UI details). Those remain required by the approved Golden Master contract.
