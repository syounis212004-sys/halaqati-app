#!/usr/bin/env python3
"""RC40 static gate: close-session auto marking, new bottom bar with the smart add button, home shortcuts, photos, observer breakdown."""
import sys
from pathlib import Path
B = Path('app/src/main/java/com/mohammedsamir/halaqati')
def t(p): return (B / p).read_text(encoding='utf-8')
errors = []
core = t('ui/screens/CoreScreens.kt')
for tok in ('fun finalizeAndClose()', 'SessionWorkflowRules.StudentState.UNMARKED -> {', 'saveAttendance(session.id, student.id, "absent")', 'participation = SessionWorkflowRules.NOT_RECITED_MARKER,', 'finalizeAndClose()'):
    if tok not in core: errors.append('session close: missing ' + tok)
pol = t('ui/navigation/RoleNavigationPolicy.kt')
if 'AppDestination.Home, AppDestination.Sessions, AppDestination.Add, AppDestination.Community, AppDestination.More' not in pol:
    errors.append('staff bottom bar must be Home, Sessions, Add, Community, More')
if 'AddActionBus.consume()' not in core or 'bus.request("session")' not in t('ui/HalaqatiApp.kt'):
    errors.append('smart add button must reach the sessions page')
if 'HomeShortcuts(' not in t('ui/screens/DashboardScreen.kt'): errors.append('home shortcuts missing')
if 'fun StudentAvatar(' not in t('ui/components/StudentAvatar.kt'): errors.append('student avatar missing')
if 'showStudentPhotos' not in t('data/UiPreferencesStore.kt'): errors.append('photo setting missing')
if 'rc40_observer_breakdown' not in t('data/HalaqatiRepository.kt'): errors.append('observer breakdown RPC missing')
for place in ('ui/components/HalaqatiMotion.kt', 'ui/HalaqatiApp.kt', 'ui/screens/AuthScreens.kt', 'ui/components/GoldenShell.kt'):
    if 'الشرقية — خانيونس' in t(place): errors.append('place line must be gone from ' + place)
if not Path('migration/20261030_v2760_rc40_observer_breakdown.sql').exists(): errors.append('migration record missing')
if errors:
    print('RC40 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC40 gate passed')
