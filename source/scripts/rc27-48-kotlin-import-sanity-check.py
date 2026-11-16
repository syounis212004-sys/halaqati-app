#!/usr/bin/env python3
"""RC27.48: catches the build failure of 2.63.47 (a Kotlin file using `15.dp` without importing androidx.compose.ui.unit.dp)."""
import glob, re, sys
errors = []
for f in glob.glob('app/src/main/java/**/*.kt', recursive=True):
    s = open(f, encoding='utf-8').read()
    for unit in ('dp', 'sp'):
        if re.search(r'(?<![\w.])\d+(\.\d+)?\.' + unit + r'\b', s) and f'androidx.compose.ui.unit.{unit}' not in s and 'androidx.compose.ui.unit.*' not in s:
            errors.append(f'{f}: uses .{unit} without importing androidx.compose.ui.unit.{unit}')
if errors:
    print('Kotlin import sanity FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('Kotlin import sanity passed')
