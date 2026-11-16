#!/usr/bin/env python3
"""RC17 gate: role isolation, guardian experience, cache-first, shell insets, no double fetch."""
from pathlib import Path
import re, sys
root = Path(__file__).resolve().parents[1]
src = root/'app/src/main/java/com/mohammedsamir/halaqati'
fails = []
def need(p, t, m):
    if t not in (src/p).read_text(encoding='utf-8'): fails.append(m)
app = 'ui/HalaqatiApp.kt'
need(app, 'if (!guardianMode) composable("follow_up")', 'guardian graph must not contain follow_up')
need(app, 'if (!guardianMode) composable(\n                        route = "student/{studentId}"', 'guardian graph must not contain teacher student profile')
need(app, 'GuardianHomeScreen(navigate) else DashboardScreen', 'guardian home must be separate from teacher dashboard')
g = src/'ui/guardian'
for f in ['GuardianHomeScreen.kt', 'GuardianHomeViewModel.kt', 'GuardianUiState.kt']:
    if not (g/f).exists(): fails.append('missing '+f)
home = (g/'GuardianHomeScreen.kt').read_text(encoding='utf-8')
for bad in ['JSONObject', 'JSONArray', 'session_today', 'quick_memorization', 'ابدأ جلسة', 'التحفيظ السريع']:
    if bad in home: fails.append('guardian home must not contain '+bad)
need('ui/guardian/GuardianHomeViewModel.kt', 'cachedGuardianChildOverview', 'guardian home must be cache-first')
need('data/HalaqatiRepository.kt', '"guardian_child_overview"', 'overview RPC missing')
need('data/HalaqatiRepository.kt', 'setOf("hifz", "review", "sard", "tathbit")', 'competition must accept tathbit')
need('ui/screens/GuardianParityScreens.kt', 'val effectiveTrack = if (tathbitChild) "tathbit" else track', 'competition must be track-aware')
need('ui/screens/GuardianParityScreens.kt', 'LaunchedEffect(Unit) { runCatching { loadChildren() }', 'children must load once')
need('ui/navigation/RoleNavigationPolicy.kt', 'AppDestination.Home, AppDestination.GuardianProfile, AppDestination.GuardianAchievements, AppDestination.Community, AppDestination.More', 'guardian bottom bar')
need('ui/components/GoldenShell.kt', 'navigationBarsPadding()', 'RC28 native navigation bar must handle system insets')
need('ui/components/GoldenShell.kt', 'role = Role.Tab', 'bottom navigation must expose selected semantics (RC90 capsule bar)')
need('ui/components/Components.kt', 'if (count > 0) Badge', 'healthy connection must not show a pending badge')
need('ui/components/Components.kt', 'chrome.openSyncCenter()', 'sync status must remain accessible')
need('ui/dashboard/DashboardViewModel.kt', 'baseInputsFresh = studentsResult.isSuccess && plansResult.isSuccess', 'dashboard must not fetch students/plans twice')
need('ui/dashboard/DashboardViewModel.kt', 'async(Dispatchers.IO)', 'dashboard inputs must load concurrently')
mgmt = (src/'ui/screens/ManagementScreens.kt').read_text(encoding='utf-8')
if 'ستظهر بيانات ولي الأمر بعد استعادة الاتصال' in mgmt: fails.append('guardian profile must show cached data offline')
mig = (root/'migration/20260929_v2520_rc17_guardian_experience.sql').read_text(encoding='utf-8')
for t in ['guardian_child_overview', "p_track = 'tathbit'", 'student is not linked to this guardian', 'security definer']:
    if t not in mig: fails.append('migration missing '+t)
if not (root/'benchmark/src/main/java/com/mohammedsamir/halaqati/benchmark/JourneyBenchmarks.kt').exists(): fails.append('journey benchmarks missing')
build = (root/'app/build.gradle.kts').read_text()
if int(re.search(r'versionCode = (\d+)', build).group(1)) <= 24400: fails.append('versionCode must exceed legacy 24400')
if fails:
    print('\n'.join('FAIL: '+f for f in fails)); sys.exit(1)
print('RC17 role experience + performance gate: OK')
