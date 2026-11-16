#!/usr/bin/env python3
"""RC30 static gate: forced updates are a per-release choice, and the problem-report button exists and is wired."""
import sys
from pathlib import Path

errors = []
rd = lambda p: Path(p).read_text(encoding='utf-8')

pub = rd('scripts/make-update-publish.py')
if "HALAQATI_MANDATORY" not in pub or "'mandatory':True" in pub:
    errors.append('version.json mandatory flag must come from HALAQATI_MANDATORY (default optional)')
if pub.count("\n        '") > 12:
    errors.append('version.json notes must stay short (newest releases only)')

base = 'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/'
if 'fun SupportSettingsCard' not in rd(base + 'SettingsParitySections.kt'):
    errors.append('SupportSettingsCard missing')
if 'SupportSettingsCard()' not in rd(base + 'ReportsAndTools.kt'):
    errors.append('SupportSettingsCard must be shown in Settings')
card = rd(base + 'SettingsParitySections.kt')
card = card[card.index('fun SupportSettingsCard'):]
for forbidden in ('fullName', 'student', 'email', 'userId', 'phone'):
    if forbidden in card.split('internal fun', 1)[0]:
        errors.append('the problem report must not include personal data: ' + forbidden)

if errors:
    print('RC30 release-safety gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC30 release-safety gate passed')
