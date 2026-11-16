#!/usr/bin/env python3
"""RC53 static gate: supervisor UI, offline visits, frozen accounts, staff monitor, role phrases, slim offline strip."""
import sys
from pathlib import Path
A = Path('app/src/main/java/com/mohammedsamir/halaqati')
def rd(p): return (A / p).read_text(encoding='utf-8')
errors = []
sup = rd('ui/screens/SupervisorScreens.kt')
for t in ('SupervisorHomeScreen', 'SupervisorHalaqasScreen', 'SupervisorVisitsScreen', 'SupAlertDialog', 'trim() == "حذف"', 'repo.write(', 'repo.patch(', 'repo.delete('):
    if t not in sup: errors.append('supervisor screens missing ' + t)
if 'writeConfirmed' in sup: errors.append('visits must be saved through the offline outbox, not writeConfirmed')
if 'object VisitOverlay' not in rd('ui/screens/SupervisorData.kt'): errors.append('VisitOverlay missing')
for t in ('StaffMonitorScreen', 'متابعة المحفظين والمشرفين'):
    if t not in rd('ui/screens/StaffMonitorScreen.kt'): errors.append('staff monitor missing ' + t)
if 'SupervisorInsightsScreen' not in rd('ui/screens/SupervisorMoreScreens.kt'): errors.append('supervisor insights missing')
nav = rd('ui/navigation/RoleNavigationPolicy.kt')
if 'AppDestination.Visits' not in nav: errors.append('Visits destination missing from navigation')
sup_block = nav[nav.index('private val supervisor'):nav.index('private val teacher')]
for bad in ('AppDestination.Plans', 'AppDestination.Certificates', 'AppDestination.QuickMemorization'):
    if bad in sup_block: errors.append('supervisor must not reach ' + bad)
if 'AuthState.Frozen' not in rd('ui/AppViewModel.kt') or 'FrozenAccountScreen' not in rd('ui/screens/AuthScreens.kt'): errors.append('frozen account flow missing')
if 'لا يوجد اتصال حاليًا، لا تسجّل خروجًا' not in rd('ui/components/Components.kt'): errors.append('slim offline strip text missing')
if 'forRole' not in rd('ui/dashboard/RolePhrases.kt') or 'RolePhrases.forRole' not in rd('ui/screens/DashboardScreen.kt'): errors.append('role welcome card missing')
if 'recipient_id=eq.${profile.id}' not in rd('ui/screens/ManagementScreens.kt'): errors.append('notifications must be limited to the signed-in recipient')
if '"donors"' not in rd('ui/screens/BroadcastCenter.kt'): errors.append('donor audience missing from the notification centre')
if 'AppSupportInfoCard' not in rd('ui/screens/AppSupportInfoCard.kt'): errors.append('support info card missing')
if errors:
    print('RC53 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC53 gate passed')
