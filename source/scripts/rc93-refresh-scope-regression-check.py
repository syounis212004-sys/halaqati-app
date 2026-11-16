#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CORE = ROOT / 'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt'
text = CORE.read_text(encoding='utf-8')

def section(start_marker, end_marker):
    start = text.index(start_marker)
    end = text.index(end_marker, start)
    return text[start:end]

students = section('fun StudentsScreen(', 'private fun StudentDetailSheet(')
sessions = section('fun SessionsScreen(', 'private fun GoldenStudentCard(')
plans = section('fun PlansScreen(', 'private fun PlansManagerSheet(')

errors = []
if 'refreshingPlans' in students:
    errors.append('StudentsScreen must not reference PlansScreen refresh state')
if 'refreshingPlans' in sessions:
    errors.append('SessionsScreen must not reference PlansScreen refresh state')
if 'var refreshingPlans by remember' not in plans:
    errors.append('PlansScreen refresh state declaration missing')
if 'if (force && rows.isNotEmpty()) refreshingPlans = true' not in plans:
    errors.append('PlansScreen force refresh must start refresh indicator')
if 'refreshingPlans = false' not in plans:
    errors.append('PlansScreen reload must always clear refresh indicator')

if errors:
    print('RC9.3 refresh-scope regression FAILED')
    for error in errors:
        print(' -', error)
    raise SystemExit(1)

print('RC9.3 refresh-scope regression PASS')
