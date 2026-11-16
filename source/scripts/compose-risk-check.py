#!/usr/bin/env python3
from pathlib import Path
import re,sys
root=Path(__file__).resolve().parents[1]
errors=[]
for p in (root/'app/src/main/java').rglob('*.kt'):
    txt=p.read_text(encoding='utf-8',errors='ignore')
    rel=p.relative_to(root)
    # Previously observed compile failures.
    if re.search(r'AssistChip\s*\(\s*\{',txt): errors.append(f'{rel}: positional AssistChip call can break overload resolution')
    if re.search(r'OutlinedTextField\([^\n]*label\s*=\s*\{[^\n]*\},\s*Modifier\.',txt): errors.append(f'{rel}: positional Modifier after named label in OutlinedTextField')
    if 'ExperimentalMaterial3Api' in txt or any(tok in txt for tok in ('ModalBottomSheet(', 'CenterAlignedTopAppBar(', 'SingleChoiceSegmentedButtonRow(')):
        if '@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)' not in txt and 'ExperimentalMaterial3Api::class' not in txt:
            errors.append(f'{rel}: Material3 experimental API without opt-in')
    if 'GenericDataScreen' in txt: errors.append(f'{rel}: generic fallback remains')
    if 'import androidx.compose.foundation.layout.weight' in txt:
        errors.append(f'{rel}: do not import layout.weight directly; use RowScope/ColumnScope Modifier.weight')
if errors:
    print('Compose risk check FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('Compose compile-risk check passed')
