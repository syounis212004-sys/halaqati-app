# UI 2.44 Restoration T6 FIX3

- Root cause from GitHub Actions run 35861965000: googleid 1.2.1 declares minSdk 24 while Halaqati intentionally supports minSdk 23.
- Kept app minSdk at 23 and pinned Google ID SDK to 1.1.1, which still provides Credential Manager Google ID token APIs used by NativeGoogleSignIn.
- Added a regression guard to ui244-restoration-check.py so a future dependency bump cannot silently raise the Android support floor.
- No UI behavior or OAuth browser fallback was reintroduced; native Credential Manager sign-in remains the intended flow.
