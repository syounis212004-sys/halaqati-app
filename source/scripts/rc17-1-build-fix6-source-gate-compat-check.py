#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT = Path(__file__).resolve().parents[1]
errors=[]

def text(rel):
    p=ROOT/rel
    if not p.exists():
        errors.append(f'missing {rel}')
        return ''
    return p.read_text(encoding='utf-8', errors='ignore')

rc10=text('scripts/rc10-23-student-intelligence-check.py')
rc11=text('scripts/archive/rc11-2-phase-consistency-check.py')
rc12=text('scripts/archive/rc12-2-certificates-studio-check.py')
core=text('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
controls=text('app/src/main/java/com/mohammedsamir/halaqati/ui/components/AdaptiveControls.kt')
rules=text('app/src/main/java/com/mohammedsamir/halaqati/data/StudentGuardianRules.kt')
reports=text('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')

for token in ('HalaqatiLazySelectField(', 'searchable = true'):
    if token not in rc10: errors.append('RC10 legacy gate not aligned with lazy guardian selector: '+token)
for token in ('fun matches(', 'fun filter('):
    if token not in rules: errors.append('guardian rules missing '+token)
if 'LazyColumn' not in controls: errors.append('shared selector is not visibly LazyColumn-backed')
if 'هذه الشاشة تعدّل بيانات الخطة العامة مثل الاسم والسياسات.' not in rc11:
    errors.append('RC11.2 gate still expects stale managed-phases copy')
if 'enabled = !phaseManaged' not in core:
    errors.append('phase-managed plan guard missing')
if 'items(studentCandidates' not in rc12:
    errors.append('RC12.2 gate still expects eager student iteration')
if 'items(studentCandidates' not in reports:
    errors.append('certificate student candidates are not using LazyColumn items')

if errors:
    print('RC17.1 BUILD FIX 6 source-gate compatibility FAILED')
    for e in errors: print(' -', e)
    sys.exit(1)
print('RC17.1 BUILD FIX 6 source-gate compatibility PASS')
