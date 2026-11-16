#!/usr/bin/env python3
"""RC44 static gate: the plan is the only source of the daily hifz ward and of the weekly tile."""
import sys
from pathlib import Path
B = Path('app/src/main/java/com/mohammedsamir/halaqati')
def t(p): return (B / p).read_text(encoding='utf-8')
errors = []
rules = t('plans/SmartPlanActivityRules.kt')
hifz = rules.split('"hifz" -> maxOf(')[1].split('"sard"')[0]
if 'weeklyDaily' in hifz: errors.append('weekly student target must not raise the hifz ward')
repo = t('data/HalaqatiRepository.kt')
if '"hifz" -> null' not in repo.split('val weeklyTargetOverride')[1].split('val coreState')[0]: errors.append('hifz weekly tile must come from the plan')
if 'أساسي ' not in t('ui/screens/CoreScreens.kt'): errors.append('daily figure explanation missing')
if 'weekly student target leaked' not in Path('scripts/kotlin-selftest/SmartPlanActivityRulesSelfTest.kt').read_text(encoding='utf-8'): errors.append('self-test missing')
if errors:
    print('RC44 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC44 gate passed')
