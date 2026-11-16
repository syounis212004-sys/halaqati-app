#!/usr/bin/env python3
from pathlib import Path
import hashlib, sys
root=Path(__file__).resolve().parents[1]
required=[
 root/'app/src/main/java/com/mohammedsamir/halaqati/ui/theme/CairoTypography.kt',
 root/'app/src/main/java/com/mohammedsamir/halaqati/data/UiPreferencesStore.kt',
 root/'scripts/fetch-cairo-font.py',
]
errors=[f'missing {p.relative_to(root)}' for p in required if not p.exists()]
if not errors:
    typography=required[0].read_text(encoding='utf-8')
    theme=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/theme/Theme.kt').read_text(encoding='utf-8')
    fetch=required[2].read_text(encoding='utf-8')
    if 'CairoFamily' not in typography or 'R.font.cairo' not in typography: errors.append('CairoTypography must use bundled Cairo resource')
    if 'typography = cairoTypography()' not in theme: errors.append('HalaqatiTheme must apply cairoTypography() globally')
    if 'google/fonts' not in fetch or '25641b1c828857698457bcd9f5a102616b4e8033' not in fetch: errors.append('Cairo fetch must be pinned to official google/fonts blob')
    font=root/'app/src/main/res/font/cairo.ttf'
    if font.exists():
        b=font.read_bytes(); blob=hashlib.sha1(b'blob '+str(len(b)).encode()+b'\0'+b).hexdigest()
        if blob!='25641b1c828857698457bcd9f5a102616b4e8033': errors.append('Cairo font blob SHA-1 mismatch')
if errors:
    print('CAIRO CONTRACT FAILED'); [print(' -',e) for e in errors]; sys.exit(1)
print('Cairo contract passed (official Cairo, globally applied, pinned build-time embed)')
