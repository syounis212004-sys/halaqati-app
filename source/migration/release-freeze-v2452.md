# Halaqati Native 2.45.2 — Release Freeze Audit

This file tracks only gates that can be proven from the source tree before a real Android build.
It is not a substitute for Gradle, signing, emulator/device, or production-role regression.

## Source freeze gates
- 30 Kotlin runtime files under `app/src/main/java`.
- 17 explicit Native routes; no `GenericDataScreen` fallback.
- Runtime source contains no WebView/Capacitor dependency.
- Student tracks exposed by Native are exactly `general_hifz` and `tathbit`; legacy stored `sard` is normalized on read.
- Supabase contract checks cover 25 tables, 3 views, and the RPCs currently used by Native.
- Auth callback is exact `com.mohammedsamir.halaqati://auth/callback`; look-alike paths/authorities are rejected.
- Student deep links require a UUID before opening a student profile.
- MainActivity is `singleTop` so notification/auth deep links reuse the visible task safely.

## Offline data-integrity gate
SQLite queue v4 adds `owner_user_id` to every new offline write. Queue reads, counts, retries,
discards, and replay are scoped to the current authenticated account. The composite unique index is
`(owner_user_id, dedupe)`. Legacy v1-v3 rows with unknown ownership are quarantined instead of being
replayed under a possibly different account.

This specifically prevents the failure mode:
1. account A records attendance/recitation offline;
2. A logs out before sync;
3. account B logs in on the same phone;
4. B's bearer token replays A's queued request.

That sequence must never write data in Native 2.45.2.

## Push/privacy gate
- Device registration/unregistration remains server-authorized through `halaqati-notify`.
- Logout rotates/deletes the local Firebase token after best-effort server unregister.
- Native foreground/data-message handling rejects a mismatched `recipient_id` when supplied by the server.
- Full stale-token suppression for background notifications still requires the production notify function to
  send Native 2.45.2 devices account-bound data messages; this backend rollout must be verified separately.

## Still required before release
1. `:app:compileDebugKotlin`
2. `:app:compileReleaseKotlin`
3. `:app:testDebugUnitTest`
4. `:app:lintRelease`
5. `:app:assembleRelease`
6. `apksigner verify --verbose --print-certs`
7. Exact signing certificate SHA-256 equality with the currently installed production app.
8. `aapt` package/version/minSdk/targetSdk/manifest verification.
9. Device regression: auth/recovery, all roles, session write, offline→online sync, account switch isolation,
   smart plans, reports PDF, certificates PDF, FCM, deep links, logout/login, and in-place update.
