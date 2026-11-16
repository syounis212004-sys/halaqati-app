#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]; nav=root/'app/src/main/java/com/mohammedsamir/halaqati/ui/navigation'; app=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt').read_text()
errors=[]
for n in ['AppDestination.kt','RoleNavigationPolicy.kt','NavigationContract.kt']:
    if not (nav/n).exists(): errors.append('missing typed navigation file: '+n)
if not errors:
    d=(nav/'AppDestination.kt').read_text(); p=(nav/'RoleNavigationPolicy.kt').read_text(); c=(nav/'NavigationContract.kt').read_text()
    for r in ['home','notifications','halaqas','community','sessions','students','plans','certificates','reports','quran','rankings','importexport','sync_center','supervision','users','guardian_profile','guardian_competition','guardian_achievements','settings','more']:
        if f'route="{r}"' not in d and f'route = "{r}"' not in d: errors.append('AppDestination missing route: '+r)
    for token in ['bottom(role:UserRole,','moreSections(role:UserRole)','destinations(role:UserRole)']:
        if token not in p.replace(' ',''): errors.append('RoleNavigationPolicy missing '+token)
    for action in ['open_users','open_importexport','open_competition','open_achievements']:
        if f'"{action}"' not in c: errors.append('NavigationContract missing '+action)
for r in ['guardian_profile','guardian_competition','guardian_achievements']:
    if f'"{r}"->' not in app.replace(' ',''): errors.append('FeatureRouter missing explicit route: '+r)
if 'RoleNavigation.items' in app or 'RoleNavigation.bottom' in app: errors.append('legacy RoleNavigation is still used by MainShell')
if 'else->UnknownRouteScreen' not in app.replace(' ',''): errors.append('unknown route must fail closed')
if errors:
    print('Navigation contract FAILED'); [print(' -',e) for e in errors]; sys.exit(1)
print('Navigation contract passed (typed role-aware destinations, distinct guardian routes, fail-closed)')
