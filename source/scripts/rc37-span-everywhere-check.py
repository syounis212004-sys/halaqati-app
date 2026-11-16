#!/usr/bin/env python3
"""RC37 static gate: Hifz and Sard pages use the first-ayah..last-ayah span in every client calculation that totals pages."""
import sys
from pathlib import Path
rd = lambda p: Path('app/src/main/java/com/mohammedsamir/halaqati/' + p).read_text(encoding='utf-8')
errors = []
repo = rd('data/HalaqatiRepository.kt')
for tok in ('sardSpanEntries', 'phase.phaseType == "hifz" || phase.phaseType == "sard"', 'rankingTrack == "sard" && row.optString("activity_type") == "sard"'):
    if tok not in repo:
        errors.append('repository missing: ' + tok)
if 'HifzSpanRules.pages' not in rd('ui/screens/CoreScreens.kt') or 'activity_type") == "sard" }.map(::hifzSpanEntryOf)' not in rd('ui/screens/CoreScreens.kt'):
    errors.append('student sheet must use the span for Sard')
org = rd('ui/components/OrgInsightCards.kt')
if 'spanAwareRecitations' not in org or 'start_surah,start_ayah,end_surah,end_ayah' not in org:
    errors.append('organisation comparison must use span pages for Hifz and Sard')
if errors:
    print('RC37 span-everywhere gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC37 span-everywhere gate passed')
