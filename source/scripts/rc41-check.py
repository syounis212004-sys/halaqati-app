#!/usr/bin/env python3
"""RC41 static gate: Amiri headings are pinned and applied, gold hairlines exist, compact home is the default."""
import sys, hashlib
from pathlib import Path
B = Path('app/src/main/java/com/mohammedsamir/halaqati')
errors = []
fetch = Path('scripts/fetch-cairo-font.py').read_text(encoding='utf-8')
if '017afa9adb8ce417b08796d22d9204569ad211c7' not in fetch or 'ofl/amiri/Amiri-Bold.ttf' not in fetch: errors.append('Amiri fetch must be pinned')
typo = (B / 'ui/theme/CairoTypography.kt').read_text(encoding='utf-8')
if 'AmiriFamily' not in typo or 'R.font.amiri_bold' not in typo or 'titleLarge = style(FontWeight.Bold, 22, 32, AmiriFamily)' not in typo: errors.append('headings must use Amiri')
if 'bodyMedium = style(FontWeight.Normal, 15, 23)' not in typo: errors.append('body text must stay Cairo')
if 'HorizontalDivider' not in ((B / 'ui/components/Components.kt').read_text(encoding='utf-8') + (B / 'ui/components/Lattice.kt').read_text(encoding='utf-8')): errors.append('top bar hairline missing')
if 'homeStyle = HomeStyle.COMPACT' not in (B / 'data/UiPreferencesStore.kt').read_text(encoding='utf-8'): errors.append('compact home default missing')
if errors:
    print('RC41 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC41 gate passed')
