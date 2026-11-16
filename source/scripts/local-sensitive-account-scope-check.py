#!/usr/bin/env python3
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
reports = (root / 'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt').read_text()
router = (root / 'app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt').read_text()
rules = (root / 'app/src/main/java/com/mohammedsamir/halaqati/data/LocalAccountScopeRules.kt').read_text()

required = [
    'fun CertificatesScreen(profile: Profile, pending: Int, onRefresh: () -> Unit)',
    'val ownerUserId = profile.id',
    'private fun scoped(ownerUserId: String, suffix: String): String = LocalAccountScopeRules.ownerKey(ownerUserId, suffix)',
    'fun loadStyle(context: Context, ownerUserId: String)',
    'fun saveStyle(context: Context, ownerUserId: String',
    'fun listArchive(context: Context, ownerUserId: String)',
    'fun archive(context: Context, ownerUserId: String',
    'fun loadAssets(context: Context, ownerUserId: String)',
    'fun importAsset(context: Context, ownerUserId: String',
    'fun exportArchiveIndex(context: Context, ownerUserId: String',
    'fun importArchiveIndex(context: Context, ownerUserId: String',
    '.put("version", 4)',
    '.put("assets", assets)',
    'MAX_SOURCE_ASSET_BYTES = 5_000_000',
    'MAX_STORED_ASSET_BYTES = 800_000',
    'NativeReportCache.key(profile.id, from, to, track, halaqaId, sessionType)',
]
missing = [token for token in required if token not in reports]
if missing:
    raise SystemExit('Missing local account-isolation tokens:\n- ' + '\n- '.join(missing))

if not re.search(r'\"certificates\"\s*->\s*CertificatesScreen\(profile\s*,\s*sync\.pending\s*,\s*onSync\)', router):
    raise SystemExit('Certificates route must pass the authenticated profile for local account scoping')

if 'archive_v2' in reports or 'style_v1' in reports:
    raise SystemExit('Legacy unscoped certificate preference keys must not be read by the native release')

# Report cache is sensitive but key-scoped by profile id. Certificate prefs must never use raw global style/archive/assets keys.
for bad in [
    'getString(STYLE_KEY,', 'putString(STYLE_KEY,',
    'getString(ARCHIVE_KEY,', 'putString(ARCHIVE_KEY,',
    'getString(ASSETS_KEY,', 'putString(ASSETS_KEY,',
]:
    if bad in reports:
        raise SystemExit(f'Unscoped certificate SharedPreferences access detected: {bad}')

# Every certificate SharedPreferences key access should either use scoped(...) or a local variable derived from scoped(...).
cert_block = reports.split('object NativeCertificate {', 1)[1].split('\n}\n\n@Composable\nfun QuranCalculatorScreen', 1)[0]
raw_pref_lines = [line.strip() for line in cert_block.splitlines() if 'getSharedPreferences(PREFS' in line]
if not raw_pref_lines:
    raise SystemExit('Certificate preferences store not found')

if 'fun ownerKey(ownerUserId: String, suffix: String)' not in rules or 'return "$PREFIX|$owner|$part"' not in rules:
    raise SystemExit('LocalAccountScopeRules owner key contract is missing')

# Ensure no additional plaintext preference stores were introduced silently.
java_root = root / 'app/src/main/java'
pref_refs = []
for path in java_root.rglob('*.kt'):
    text = path.read_text()
    if 'getSharedPreferences(' in text or 'EncryptedSharedPreferences.create' in text:
        pref_refs.append(path.relative_to(root).as_posix())
allowed_suffixes = {
    'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt',
    'app/src/main/java/com/mohammedsamir/halaqati/data/SecureSessionStore.kt',
    # Device-local visual preferences contain no account/student data.
    'app/src/main/java/com/mohammedsamir/halaqati/data/UiPreferencesStore.kt',
    # Keyed by user id (last chosen halaqa per signed-in user); stores only an id.
    'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqaScopeStore.kt',
    # Device-level forced-update flag; no account or student data.
    'app/src/main/java/com/mohammedsamir/halaqati/ui/update/NativeAppUpdater.kt',
    # RC64: side-menu favourites, keyed by user id; stores only page routes (no account/student data).
    'app/src/main/java/com/mohammedsamir/halaqati/ui/components/GoldenShell.kt',
    # RC79: refresh timestamps keyed by user id, and per-student photo version stamps keyed by user id; no student content.
    'app/src/main/java/com/mohammedsamir/halaqati/data/RefreshGate.kt',
    'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt',
}
unexpected = sorted(set(pref_refs) - allowed_suffixes)
if unexpected:
    raise SystemExit('Unexpected local preferences store requires account-scope review:\n- ' + '\n- '.join(unexpected))



# RC7 native read cache must be owner-scoped at the Room schema boundary, not only at call sites.
cache_path = root / 'app/src/main/java/com/mohammedsamir/halaqati/data/LocalCache.kt'
db_path = root / 'app/src/main/java/com/mohammedsamir/halaqati/data/OfflineDatabase.kt'
if not cache_path.exists() or not db_path.exists():
    raise SystemExit('RC7 Room cache/database is missing')
cache = cache_path.read_text()
db = db_path.read_text()
for token in [
    'primaryKeys = ["ownerUserId", "cacheKey"]',
    'Index(value = ["ownerUserId", "savedAt"])',
    'ownerUserId: String',
    'cacheKey: String',
    'WHERE ownerUserId=:owner AND cacheKey=:key',
    'WHERE ownerUserId=:owner AND cacheKey LIKE :prefix',
]:
    if token not in db:
        raise SystemExit(f'RC7 Room cache account-scope contract missing: {token}')
for token in [
    'fun put(ownerUserId: String, key: String, json: String',
    'fun get(ownerUserId: String, key: String, maxAge: Duration?',
]:
    if token not in cache:
        raise SystemExit(f'LocalCache API account-scope contract missing: {token}')
for forbidden in ['access_token', 'refresh_token', 'service_role']:
    if forbidden in (cache + db).lower():
        raise SystemExit(f'Offline Room must never persist credentials: {forbidden}')

print('Local sensitive-data account scope check passed (reports + certificates + portable assets + RC7 Room cache)')
