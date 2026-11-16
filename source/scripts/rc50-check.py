#!/usr/bin/env python3
"""RC50 static gate: donor summary home, donor bottom bar pages, light pickers."""
import sys
from pathlib import Path
A = Path('app/src/main/java/com/mohammedsamir/halaqati')
def rd(p): return (A / p).read_text(encoding='utf-8')
errors = []
for f, toks in [
    ('ui/navigation/AppDestination.kt', ['DonorAwards', 'DonorSupport', 'DonorImpact', 'DonorStats']),
    ('ui/navigation/RoleNavigationPolicy.kt', ['AppDestination.DonorAwards', 'AppDestination.DonorImpact']),
    ('ui/HalaqatiApp.kt', ['DonorAwardsPage(', 'DonorSupportPage(', 'DonorImpactPage(', '"donor_stats"']),
    ('ui/screens/DonorScreen.kt', ['fun DonorHomeScreen', 'ملخص حلقتك', 'fun DonorAwardsPage', 'MonthPicker', 'SegmentedTabs(']),
    ('ui/components/SegmentedTabs.kt', ['fun PillChip']),
    ('ui/components/GoldenShell.kt', ['"support"', '"impact"', '"stats"']),
]:
    s = rd(f)
    for t in toks:
        if t not in s: errors.append(f'{f}: missing {t}')
d = rd('ui/screens/DonorScreen.kt')
if 'DonorStat(' in d: errors.append('the winners-only stat tiles must be gone')
for res in ('ic_gm_support.xml', 'ic_gm_impact.xml', 'ic_gm_stats.xml'):
    if not Path('app/src/main/res/drawable/' + res).exists(): errors.append('missing drawable ' + res)
if errors:
    print('RC50 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC50 gate passed')
