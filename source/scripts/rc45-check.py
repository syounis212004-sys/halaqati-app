#!/usr/bin/env python3
"""RC45 static gate: guardian plan figures come from the plan engine snapshot."""
import sys
from pathlib import Path
repo = Path('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text(encoding='utf-8')
errors = []
for tok in ('unifyGuardianWithPlan(studentId, json)', 'private fun unifyGuardianWithPlan', 'pp.completedPages', 'localizeGuardianMonth'):
    if tok not in repo: errors.append('missing ' + tok)
if errors:
    print('RC45 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC45 gate passed')
