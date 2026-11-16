# 2.51.0 (RC16) — Final Polish + Legacy Update

- Reports: invisible table headers fixed (Arabic-locale hex colour → Locale.US), track-aware columns/KPIs, null → "—",
  ayah-based ranges ("سورة الأنعام: 1 → 35" / "الأنعام 150 → الأعراف 12"), no duplicated subtitle,
  break-before pages (no blank trailing page), repeated thead, compact student identity.
- Performance: collectAsStateWithLifecycle on app/dashboard state, memoized dashboard buckets,
  keyed lists, profileinstaller so the bundled baseline profile applies to sideloaded APKs.
- Settings: home appearance (modern/compact) + motion (normal/reduced) in addition to theme/density.
- Update over 2.44: same applicationId com.mohammedsamir.halaqati, versionCode 25100 > 24400, same
  HALAQATI_KEYSTORE_BASE64 secret + EXPECTED_CERT digest check, no destructive local-data calls.
  Legacy WebView localStorage (session/offline queue) cannot be read natively: sync fully in 2.44
  before updating; users sign in once after the update. Supabase data is untouched.
