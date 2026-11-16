# RC17.1 BUILD FIX 7 — Release Update Signing

- Release builds now require only the four legacy signing secrets already used by Halaqati 2.44.
- The workflow derives the expected SHA-256 signing certificate directly from the restored keystore and verifies the produced APK against it.
- `HALAQATI_EXPECTED_SIGNING_CERT_SHA256` is no longer required.
- A missing native Google Web Client ID no longer blocks the Android build. Native Google Credential Manager is used when configured; otherwise the app falls back to the existing Supabase Google OAuth custom-tab flow.
- No Supabase schema migration is required for this build fix.
