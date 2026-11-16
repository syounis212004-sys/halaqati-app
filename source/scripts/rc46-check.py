#!/usr/bin/env python3
"""RC46 static gate: monthly honour awards (admin page, donor home, RPC wrappers, certificate hand-off)."""
import sys
from pathlib import Path
A = Path('app/src/main/java/com/mohammedsamir/halaqati')
def rd(p): return (A / p).read_text(encoding='utf-8')
errors = []
checks = [
    ('data/HalaqatiRepository.kt', ['rc46_admin_awards', 'rc46_donor_awards', 'rc46_approve_awards', 'rc46_exclude_award', 'rc46_set_donor_contact', 'rc46_compute_awards', 'rc46_award_months']),
    ('ui/screens/AwardsScreen.kt', ['fun AwardsScreen', 'object AwardCertificateBus', 'اعتماد الشهر', 'استبعاد', 'إصدار شهادة']),
    ('ui/screens/DonorScreen.kt', ['fun DonorHomeScreen', 'المكرَّمون', 'SegmentedTabs(']),
    ('ui/screens/ObserverScreens.kt', ['embedded: Boolean = false']),
    ('ui/screens/ReportsAndTools.kt', ['AwardCertificateBus.pending']),
    ('ui/navigation/AppDestination.kt', ['Awards']),
    ('ui/HalaqatiApp.kt', ['AwardsScreen(', 'DonorHomeScreen(']),
]
for f, toks in checks:
    s = rd(f)
    for t in toks:
        if t not in s: errors.append(f'{f}: missing {t}')
if 'Awards' not in rd('ui/navigation/RoleNavigationPolicy.kt'): errors.append('policy: Awards missing')
contract = Path('migration/source-contract.json').read_text(encoding='utf-8')
for n in ('rc46_award_months', 'rc46_admin_awards', 'rc46_donor_awards', 'rc46_compute_awards', 'rc46_approve_awards', 'rc46_exclude_award', 'rc46_set_donor_contact'):
    if n not in contract: errors.append('contract: ' + n)
if errors:
    print('RC46 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC46 gate passed')
