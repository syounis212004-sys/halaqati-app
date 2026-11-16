#!/usr/bin/env python3
"""RC36 static gate: Sard progress is one continuous first-ayah..last-ayah span (like Hifz), so repeated ayahs count once."""
import sys
from pathlib import Path
src = Path('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt').read_text(encoding='utf-8')
errors = []
if 'plan.planType != "hifz" && plan.planType != "sard") return null' not in src:
    errors.append('spanCompletion must apply to Sard as well as Hifz')
if errors:
    print('RC36 sard span gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC36 sard span gate passed')
