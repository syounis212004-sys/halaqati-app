#!/usr/bin/env python3
"""Guard the source patterns repaired after the FIR safe-call OOM (not a compiler test)."""
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt').read_text()
assert '.verticalScroll(' not in s or 'import androidx.compose.foundation.verticalScroll' in s, 'verticalScroll usage requires explicit androidx.compose.foundation.verticalScroll import'
editor=s.split('private fun SmartPlanEditorDialog(',1)[1].split('\n@Composable',1)[0]
assert 'remember(current?.id)' not in editor, 'Repeated nullable key/inline initializer graph returned'
assert len(re.findall(r'by remember\(planKey\) \{ initialPlan',editor)) == 26, '26 fields keep the lightweight initialPlan initializer after RC23 removes milestone controls; scope/title are override-aware'
assert 'isStudentOverride' in editor and 'source?.scopeType' in editor and '· تخصيص' in editor, 'Student override initializers missing'
assert len(re.findall(r'private fun initialPlan\w+\(',s)) == 30
assert 'initialPlanHalaqaId(current, halaqas, halaqaId)' not in s, 'Self-reference before initialization'
assert 'current?.settings?.' not in editor, 'Resolve settings before the large constructor'
assert 'track = track,' not in editor, 'SmartPlan Quran picker must not reference an out-of-scope track variable'
assert 'track = effectiveStudentTrack' in editor, 'SmartPlan Quran picker must derive Quran track from the selected student track, not from an unrelated activity type'

phases=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SmartPlanPhasesUi.kt').read_text()
assert 'internal fun quranLocationLabel(' in phases, 'quranLocationLabel is shared by CoreScreens and cannot be file-private'
assert 'internal fun recitationRangeLabel(' in phases, 'recitationRangeLabel is shared by CoreScreens and cannot be file-private'
assert 'private fun quranLocationLabel(' not in phases, 'Shared Quran location formatter regressed to file-private visibility'
assert 'private fun recitationRangeLabel(' not in phases, 'Shared recitation range formatter regressed to file-private visibility'

repo=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text()
assert 'cachedSessions(owner, maxAge = null).orEmpty().map { it.id }' in repo, 'syncNow must safely map nullable cachedSessions via orEmpty()'

reports=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt').read_text()
assert 'BorderStroke(' not in reports or 'import androidx.compose.foundation.BorderStroke' in reports, 'BorderStroke usage requires explicit androidx.compose.foundation.BorderStroke import'
assert 'RoundedCornerShape(' not in reports or 'import androidx.compose.foundation.shape.RoundedCornerShape' in reports, 'RoundedCornerShape usage requires explicit androidx.compose.foundation.shape.RoundedCornerShape import'
calculator=reports.split('fun QuranCalculatorScreen()',1)[1].split('fun RankingsScreen',1)[0]
assert 'QuranRangePickerDialog(' in calculator, 'Quran calculator picker missing'
assert 'track = "tathbit"' in calculator, 'Quran calculator picker must pass a traversal track'
assert 'activityType = "review_near"' in calculator, 'Quran calculator picker must pass an activity type'
app=(root/'app/build.gradle.kts').read_text()
assert app.count('JsonOutput.toJson(conf(')==3, 'BuildConfig must use escaped Java string literals for Supabase + Google client IDs'
for name in ('SUPABASE_URL','SUPABASE_PUBLISHABLE_KEY','GOOGLE_WEB_CLIENT_ID'):
    assert f'buildConfigField("String", "{name}", JsonOutput.toJson(conf(' in app, f'Missing escaped BuildConfig string: {name}'
assert 'kotlinOptions' not in app
assert 'implementation("androidx.fragment:fragment:1.8.9")' in app, 'ActivityResult lint guard requires Fragment 1.8.9 to override stale transitive 1.1.0'
native_google=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/auth/NativeGoogleSignIn.kt').read_text()
assert not ('googleid:1.1.1' in app and 'GetSignInWithGoogleOption' in native_google), 'googleid 1.1.1 does not expose GetSignInWithGoogleOption; keep GetGoogleIdOption + secure fallback for minSdk23'
properties=(root/'gradle.properties').read_text()
keys=[line.split('=',1)[0] for line in properties.splitlines() if '=' in line and not line.startswith('#')]
assert len(keys)==len(set(keys)), 'Duplicate Gradle properties'
print('Compiler regression source checks passed (not an APK compile)')
