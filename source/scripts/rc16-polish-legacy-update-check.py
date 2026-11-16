#!/usr/bin/env python3
"""RC16 gate: report/PDF polish + legacy 2.44 update compatibility (same package, higher versionCode)."""
from pathlib import Path
import re, sys
root = Path(__file__).resolve().parents[1]
fails = []
build = (root/'app/build.gradle.kts').read_text(encoding='utf-8')
LEGACY_PACKAGE, LEGACY_VERSION_CODE = 'com.mohammedsamir.halaqati', 24400  # v2.44.0 (capacitor appId + APP_VERSION_CODE)
if f'applicationId = "{LEGACY_PACKAGE}"' not in build: fails.append('applicationId must equal legacy 2.44 package')
if 'applicationIdSuffix' in build: fails.append('no applicationIdSuffix allowed (would install as a second app)')
m = re.search(r'versionCode = (\d+)', build)
if not m or int(m.group(1)) <= LEGACY_VERSION_CODE: fails.append('versionCode must exceed legacy 24400')
if 'signingConfig = signingConfigs.getByName("release")' not in build: fails.append('release must use the legacy keystore signing config')
src = root/'app/src/main/java'
alltext = '\n'.join(p.read_text(encoding='utf-8', errors='ignore') for p in src.rglob('*.kt'))
for bad in ['clearApplicationUserData', 'deleteAllData()']:
    if bad in alltext: fails.append('destructive local-data call found: '+bad)
rr = (src/'com/mohammedsamir/halaqati/documents/ReportHtmlRenderer.kt').read_text(encoding='utf-8')
for t in ['String.format(Locale.US,"#%06X"', 'private fun relevant(', 'private fun txt(', 'سورة ${surahName(ss)}: $sa ← $endA', '"tathbit" to "تثبيت"', 'headerSubtitle(']:
    if t not in rr: fails.append('report renderer missing '+t)
css = (root/'app/src/main/assets/documents/report.css').read_text(encoding='utf-8')
if 'break-before:page' not in css or 'page-break-after:always' in css: fails.append('report pages must break before, never after (blank trailing page)')
if 'androidx.profileinstaller:profileinstaller' not in build: fails.append('baseline profile installer missing')
if fails:
    print('\n'.join('FAIL: '+f for f in fails)); sys.exit(1)
print('RC16 polish + legacy update compatibility gate: OK')
