#!/usr/bin/env python3
"""RC32 static gate: observer role is wired end to end, the observer never reaches staff routes, dark-mode text uses
the shared accent helper, and list screens keep their position when a session/profile is opened and closed."""
import sys, re
from pathlib import Path

errors = []
rd = lambda p: Path(p).read_text(encoding='utf-8')
b = 'app/src/main/java/com/mohammedsamir/halaqati/'

if 'OBSERVER' not in rd(b + 'model/Models.kt'):
    errors.append('UserRole.OBSERVER missing')
pol = rd(b + 'ui/navigation/RoleNavigationPolicy.kt')
m = re.search(r'private val observer = listOf\((.*?)\)\n', pol, re.S)
if not m:
    errors.append('observer destination list missing')
else:
    for forbidden in ('Students', 'Sessions', 'Plans', 'Users', 'Approvals', 'Halaqas', 'ImportExport', 'Certificates'):
        if 'AppDestination.' + forbidden in m.group(1):
            errors.append('observer must not open ' + forbidden)
if 'ObserverRequests' not in pol or 'UserRole.OBSERVER' not in pol:
    errors.append('policy does not route observer / observer requests')
app = rd(b + 'ui/HalaqatiApp.kt')
for token in ('DonorHomeScreen(', 'ObserverRequestsAdminScreen(', '"observer_requests"'):
    if token not in app:
        errors.append('HalaqatiApp missing ' + token)
repo = rd(b + 'data/HalaqatiRepository.kt')
for rpc in ('rc32_observer_overview', 'rc32_observer_submit_request', 'rc32_observer_requests', 'rc32_observer_resolve'):
    if rpc not in repo:
        errors.append('repository missing ' + rpc)
sql = rd('migration/20261027_v2680_rc32_observer_role.sql')
for token in ('add value if not exists', 'observer_requests', 'enable row level security', 'security definer', 'revoke all on function'):
    if token not in sql.lower().replace("'observer'", "'observer'"):
        errors.append('observer SQL missing ' + token)
if re.search(r'grant\s+(select|all)[^;]*on\s+public\.(students|recitation_entries|profiles)', sql, re.I):
    errors.append('observer SQL must not grant direct table reads')
if 'phone' in sql.lower() or 'national_id' in sql.lower():
    errors.append('observer overview must not expose phone or national id')

core = rd(b + 'ui/screens/CoreScreens.kt')
if 'sessionsListState' not in core or 'rememberSaveable { mutableStateOf<String?>(null) }' not in core:
    errors.append('Sessions screen must keep scroll position and open session across navigation')
if 'studentsListState' not in core:
    errors.append('Students screen must keep scroll position')
if 'fun textAccent' not in rd(b + 'ui/theme/Theme.kt'):
    errors.append('textAccent helper missing')

if errors:
    print('RC32 observer/navigation gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC32 observer/navigation gate passed')
