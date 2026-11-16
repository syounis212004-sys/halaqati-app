# Feature parity matrix

Source audited: Halaqati v2.44.0 SYNC_REFRESH_FIX.

| Feature | Native 2.49.0-RC14 implementation |
|---|---|
| Authentication & approval | Native email/password + Google OAuth + profile approval gate + Native password-recovery deep link/update flow; same profiles/roles. |
| Role navigation | Admin/Supervisor/Teacher/Guardian routes ported. |
| Halaqas/categories | Native management for halaqas/categories + teacher/supervisor assignments; same production tables. |
| Students | Native directory/create + status/track/halaqa/category filters + student detail sheet with month metrics, latest recitations, active smart-plan progress, quick status/track edit, and direct recitation CTA. |
| Sessions | Native create/open roster, attendance, recitation, offline queue. |
| Smart plans | Native engine + CRUD + daily assignment/progress/retry/catch-up/milestones + same smart_plans/events contract; V6 scoring preserved. |
| Reports | Native summary/detail preview, date/halaqa/track/session-type filters, zero-activity students, last-success local cache, Native PDF. |
| Certificates | Native individual + bulk multi-page PDF, sequential numbers, local archive/search/import/export, five local templates and display controls, certificate types + original logos. |
| Notifications | FCM service + server-authorized register/unregister/test via `halaqati-notify`, `halaqati_general` channel, per-account token rotation on logout, user_devices + notifications. |
| Rankings | student_points or guardian RPC. |
| Guardian | role-restricted nav + children-oriented pages/RPC data. |
| Import/export | XLSX/CSV/DOCX parser + JSON backup; unsafe PDF/image OCR refuses auto-commit. |
| Community | Native announcements/forum posts/comments CRUD using the same tables. |
| Supervision | Native supervisor visits + follow-up cards/metrics using supervisor_visits. |
| Accounts | Native approval/rejection/activate/role/guardian-link/delete flows; destructive deletion remains server-side RPC. |
| Quran calculator | Native Hafs metadata: 114 surahs, 604 pages, 30 juz, 240 rubs + precise range picker. |
| Offline sync | SQLite write queue v4 + WorkManager + Sync Center with pending/blocked/retry/discard, visible local-save state, and per-account replay isolation so queued writes cannot cross users after logout/login. |
| Pull refresh | Every data screen exposes explicit refresh; no WebView refresh dependency. |
| Updater compatibility | same package id; workflow emits version.json + Halaqati-latest.apk. |

## Important
- UI/UX Pro Max native layer is tracked in `migration/native-uiux-pro-max.md`.
- The static Golden Master release gate now reports all 48 tracked v2.44 feature surfaces as MATCHED/IMPROVED; CI/device regression against production RLS and real role accounts remains mandatory before calling the APK production-ready.
- Full Android Gradle compile/signing must still be proven by the supplied GitHub Actions workflow for this RC2 package.
- The project intentionally does not claim that static checks prove zero defects.

## Release-freeze contracts added in 2.45.2 working tree
- Exactly 17 explicit Native routes; `GenericDataScreen` fallback was removed. Unknown routes fail closed to a dedicated state instead of querying an arbitrary table.
- Student track contract is exactly `general_hifz` + `tathbit`; legacy stored `sard` is normalized to general_hifz, while sard remains an activity type.
- Import Center parsing/validation is centralized in pure Kotlin `ImportRules` and unknown activity/track values are rejected rather than silently coerced.
- CI contract requires debug/release Kotlin compilation, unit tests, release lint, signed release assembly, package/version/sdk checks and exact signing-certificate SHA-256 equality.

## Auth / push / privacy release-freeze additions
- Supabase OAuth and password recovery use the Native callback `com.mohammedsamir.halaqati://auth/callback`; query and fragment callbacks are parsed by pure Kotlin `AuthCallbackRules`, matched by exact scheme/host/path (not a permissive string prefix), and covered by self-tests/JUnit.
- `/auth/v1/recover` sends `redirect_to` as the encoded query parameter expected by Supabase Auth; the stale web `GOOGLE_REDIRECT_URL` BuildConfig field was removed.
- Production push registration is performed through `halaqati-notify` (`register_device` / `unregister_device`) rather than direct client upserts. Logout unregisters best-effort, clears the local association, and rotates the Firebase token. Native FCM handling also rejects a `recipient_id` that does not match the current session when the backend supplies that field.
- FCM channel is `halaqati_general` end-to-end and a monochrome status-bar icon is declared in the manifest.
- Sensitive session, report-cache, certificate asset, and offline-queue data are excluded from Android cloud/device-transfer backup rules.
- Notification student deep links retain the target student id through navigation so guardian links do not silently open a different child.

## Offline account-isolation release-freeze additions
- Offline queue schema is version 4 and stores `owner_user_id` on every newly queued write.
- Dedupe uniqueness is scoped by `(owner_user_id, dedupe)` rather than globally across all accounts on the phone.
- v1-v3 unscoped queue rows are quarantined during upgrade instead of being guessed/replayed with the next logged-in bearer token.
- Sync Center, session overlays, retry/discard, counts, and background flush all access the queue through the current-account repository boundary.
- Logging out with pending writes shows an explicit warning: the data remains local for that account and is never replayed under a different account.
