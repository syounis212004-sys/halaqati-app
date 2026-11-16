#!/usr/bin/env python3
"""RC34 static gate: a Sard phase with no earlier Sard and no range of its own must not be measured against the parent's Hifz range."""
import sys
from pathlib import Path
src = Path('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text(encoding='utf-8')
errors = []
if 'val sardWithoutRange' not in src:
    errors.append('sardWithoutRange guard missing in phaseAwarePlan')
if 'if (sardWithoutRange) null else historicalStart ?: configuredStart()' not in src:
    errors.append('Sard start must not fall back to the parent Hifz start when there is no anchor')
if 'if (sardWithoutRange) null else explicitEnd ?: parentTerminal()' not in src:
    errors.append('Sard end must not fall back to the parent Hifz terminal when there is no anchor')
if errors:
    print('RC34 sard gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC34 sard gate passed')
