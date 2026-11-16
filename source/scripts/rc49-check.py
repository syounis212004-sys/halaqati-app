#!/usr/bin/env python3
"""RC49 static gate: payment details and transfer proof, capped percentages, segmented donor tabs, impact page."""
import sys
from pathlib import Path
A = Path('app/src/main/java/com/mohammedsamir/halaqati')
def rd(p): return (A / p).read_text(encoding='utf-8')
errors = []
for f, toks in [
    ('data/HalaqatiRepository.kt', ['rc49_support_cards', 'rc49_my_pledges', 'rc49_support_submit_transfer', 'rc49_support_defaults', 'rc49_donor_impact', 'rc49_donor_report', 'rc49_news_save', 'rc49_donor_suggest', 'rc49_suggestions']),
    ('ui/components/SegmentedTabs.kt', ['fun SegmentedTabs']),
    ('ui/screens/SupportExtras.kt', ['fun PaymentInfoCard', 'fun TransferDialog', 'fun DefaultsDialog', 'USD', 'JOD', 'ILS']),
    ('ui/screens/SupportScreens.kt', ['SegmentedTabs(', 'TransferDialog(', 'DefaultsDialog(', 'PaymentFormFields(', 'رسالة شكر للمانح', 'كفّل مرة أخرى']),
    ('ui/screens/DonorImpactScreen.kt', ['fun DonorImpactTab', 'صندوق الاقتراحات', 'أثر دعمك', 'مشاركة الملخص']),
    ('ui/screens/DonorScreen.kt', ['DonorImpactTab(', 'SegmentedTabs(', 'تجاوز الخطة', 'كافئ هذا الطالب', 'coerceAtMost(100.0)']),
]:
    s = rd(f)
    for t in toks:
        if t not in s: errors.append(f'{f}: missing {t}')
d = rd('ui/screens/DonorScreen.kt')
if '* 10, 100.0' in d: errors.append('donor sheet must not multiply behavior/quality by 10')
contract = Path('migration/source-contract.json').read_text(encoding='utf-8')
for n in ('rc49_support_defaults', 'rc49_support_set_defaults', 'rc49_support_save_card', 'rc49_support_cards', 'rc49_my_pledges', 'rc49_support_submit_transfer', 'rc49_support_deliver', 'rc49_support_proof', 'rc49_donor_impact', 'rc49_donor_report', 'rc49_news_list', 'rc49_news_save', 'rc49_donor_suggest', 'rc49_suggestions'):
    if n not in contract: errors.append('contract: ' + n)
if errors:
    print('RC49 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC49 gate passed')
