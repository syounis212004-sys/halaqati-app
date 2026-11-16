#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def read(path): return (ROOT / path).read_text(encoding='utf-8')
errors=[]
def need(cond,msg):
    if not cond: errors.append(msg)

engine=read('app/src/main/java/com/mohammedsamir/halaqati/quran/QuranRangeEngine.kt')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
core=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
picker=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/QuranPicker.kt')
reports=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
models=read('app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt')
smart=read('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt')

# Direction contract: general hifz defaults An-Nas -> Al-Baqarah; tathbit defaults Quran order.
need('defaultPlanRouteDirection' in repo, 'missing centralized Smart Plan direction default')
need('general_hifz' in repo and 'reverse_surahs_forward_ayahs' in repo, 'general-hifz reverse-surah default missing')
need('QuranTraversalDirection.REVERSE_SURAH_ORDER' in repo and 'QuranTraversalDirection.FORWARD_QURAN' in repo, 'stored route direction is not mapped into Quran engine')
need('canonicalPlanLineKeys' in repo, 'legacy/wrong Smart Plan line-key order is not normalized')
need('routeDirection = effectiveRouteDirection' in repo, 'SmartPlanEngine settings do not receive effective route direction')
need('routeDirection: String? = null' in smart, 'SmartPlanEngine settings missing route direction')
need('routeDirection = routeDirection' in core, 'Smart Plan editor must persist route direction for hifz/tathbit')
need('direction = planTraversalDirection(routeDirection)' in core, 'Quran picker is not driven by selected Smart Plan direction')
need('type in setOf("hifz", "tathbit", "sard")' in core, 'direction selector must be available for hifz/tathbit/sard')

# Picker UX: direction selector and guaranteed 2-column Surah selector.
need('اتجاه الحفظ' in picker or 'اتجاه التقدم' in picker, 'Quran picker direction selector missing')
need('chunked(2)' in picker and 'QuranSurahChoice' in picker, 'Surah selector is not guaranteed two-column compact layout')

# Offline-first: stale cache survives validated-network/DNS failures and today session can be created offline.
for token in ['CACHE_PROFILE', 'CACHE_PLAN_EVENTS', 'CACHE_RANKING_PREFIX', 'CACHE_RANKING_SOURCE']:
    need(token in repo, f'missing offline cache key: {token}')
need('cachedPlanEvents' in repo, 'Smart Plan events have no offline fallback')
need('rankingCacheKey' in repo, 'ranking results have no offline cache key')
need('runCatching' in repo[repo.find('fun ensureTodaySession'):repo.find('fun createSession')], 'today-session lookup is not network-failure tolerant')
need('offlineDb.students().all(owner).firstOrNull' in repo[repo.find('fun firstHalaqaId'):repo.find('private fun queueOwnerId')], 'offline halaqa fallback from local students missing')
need('profileFromJson' in repo, 'profile does not support cached offline bootstrap')
need('val cached = cache.get(owner, key, null)' in repo and 'if (!forceRefresh && cached != null)' in repo and 'getOrElse { cached ?: JSONArray() }' in repo, 'generic table reads are not stale-cache-first / DNS tolerant')
need('planSnapshots(LocalDate.now(), forceRefresh = true)' in repo, 'sync does not warm Smart Plan data for offline reopening')
need('CACHE_RANKING_SOURCE' in repo and 'ranking:source' in repo, 'ranking source snapshot is not warmed for offline arbitrary periods')

# Ranking UX: compact menus and arbitrary day/month selection.
need('RankingFilterSelect' in reports, 'ranking compact dropdown component missing')
need('DatePickerDialog' in reports, 'ranking day picker missing')
need('YearMonth' in reports and 'selectedMonth' in reports, 'ranking month selection missing')
need('ProFilterChip(periodMode == "day"' not in reports[reports.find('fun RankingsScreen'):], 'old oversized ranking period chips still used')

if errors:
    print('RC8.4 Direction + Offline + UX gate FAILED')
    for e in errors: print(' -', e)
    raise SystemExit(1)
print('RC8.4 Direction + Offline + UX gate PASS')
