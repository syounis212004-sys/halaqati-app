#!/usr/bin/env python3
"""RC33 static gate: every scrolling list keeps its position across navigation, and tab switches do not slide sideways."""
import sys, re
from pathlib import Path
errors = []
rd = lambda p: Path(p).read_text(encoding='utf-8')
b = 'app/src/main/java/com/mohammedsamir/halaqati/ui/'
nav = rd(b + 'components/HalaqatiNavMotion.kt')
if 'tabDirection' in nav:
    errors.append('tab switches must not slide sideways (cross-fade only)')
bare = 0
for f in list(Path(b + 'screens').glob('*.kt')) + list(Path(b + 'components').glob('*.kt')):
    s = f.read_text(encoding='utf-8')
    for m in re.finditer(r'LazyColumn\(', s):
        head = s[max(0, s.rfind('\n', 0, m.start())):m.start()]
        if 'import' in head or 'fun ' in head:
            continue
        depth, i = 1, m.end()
        while i < len(s) and depth:
            depth += {'(': 1, ')': -1}.get(s[i], 0); i += 1
        args = s[m.end():i - 1]
        first = args.strip().split('\n')[0].strip()
        named_first = re.match(r'^[a-zA-Z]+\s*=', first) is not None or first == ''
        if named_first and not re.search(r'(^|[\s,(])state\s*=', args):
            bare += 1
if bare:
    errors.append(f'{bare} full-screen LazyColumn(s) without a saved list state')
if errors:
    print('RC33 navigation-feel gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC33 navigation-feel gate passed')
