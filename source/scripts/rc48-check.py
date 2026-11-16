#!/usr/bin/env python3
"""RC48 static gate: support cards, delivery proof, donor halaqa scope."""
import sys
from pathlib import Path
A = Path('app/src/main/java/com/mohammedsamir/halaqati')
def rd(p): return (A / p).read_text(encoding='utf-8')
errors = []
for f, toks in [
    ('data/HalaqatiRepository.kt', ['rc48_donors', 'rc48_set_donor_halaqas', 'rc48_support_pledge', 'rc48_support_confirm']),
    ('ui/screens/SupportScreens.kt', ['fun SupportAdminScreen', 'fun DonorSupportTab', 'إثبات التسليم', 'تأكيد الاستلام', 'تحديد الحلقات', 'أتكفّل']),
    ('ui/screens/DonorScreen.kt', ['DonorSupportTab(', 'حلقتك:']),
    ('ui/navigation/AppDestination.kt', ['Support']),
    ('ui/HalaqatiApp.kt', ['SupportAdminScreen(']),
    ('ui/components/HomeShortcuts.kt', ['"support"']),
]:
    s = rd(f)
    for t in toks:
        if t not in s: errors.append(f'{f}: missing {t}')
if 'Support' not in rd('ui/navigation/RoleNavigationPolicy.kt'): errors.append('policy: Support missing')
contract = Path('migration/source-contract.json').read_text(encoding='utf-8')
for n in ('rc48_donors', 'rc48_set_donor_halaqas', 'rc48_support_save_card', 'rc48_support_cards', 'rc48_support_pledge', 'rc48_support_cancel', 'rc48_support_confirm', 'rc48_support_deliver', 'rc48_my_pledges', 'rc48_support_proof'):
    if n not in contract: errors.append('contract: ' + n)
if errors:
    print('RC48 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC48 gate passed')
