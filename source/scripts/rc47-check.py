#!/usr/bin/env python3
"""RC47 static gate: the iOS-style redesign (pattern, hero gradient, soft cards, pill chips, new donor page)."""
import sys
from pathlib import Path
A = Path('app/src/main/java/com/mohammedsamir/halaqati')
def rd(p): return (A / p).read_text(encoding='utf-8')
errors = []
for f, toks in [
    ('ui/components/IslamicPattern.kt', ['fun Modifier.islamicPattern', 'fun heroBrush', 'fun Modifier.heroBackground']),
    ('ui/components/Components.kt', ['heroBackground(heroBrush(', 'shadowElevation = 1.5.dp', 'RoundedCornerShape(999.dp)']),
    ('ui/components/HomeShortcuts.kt', ['Icons.Rounded.', 'halaqatiPressable', 'halaqatiEnter']),
    ('ui/theme/Theme.kt', ['RadiusXl', 'RadiusSheet', 'surfaceContainerHighest = if (dark)']),
    ('ui/theme/HalaqatiPalette.kt', ['0xFFF1EFE8']),
    ('ui/screens/DonorScreen.kt', ['fun Podium', 'ModalBottomSheet', 'animateDpAsState']),
]:
    s = rd(f)
    for t in toks:
        if t not in s: errors.append(f'{f}: missing {t}')
for f in ('ui/screens/DonorScreen.kt', 'ui/screens/AwardsScreen.kt'):
    if 'tathbit' in rd(f): errors.append(f'{f}: tathbit section must be gone')
if errors:
    print('RC47 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC47 gate passed')
