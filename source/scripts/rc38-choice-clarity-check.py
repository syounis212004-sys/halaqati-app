#!/usr/bin/env python3
"""RC38 static gate: a chosen option is unmistakable (border + fill + weight), dark outlines are visible, guardian figures are computed on the device."""
import sys
from pathlib import Path
rd = lambda p: Path('app/src/main/java/com/mohammedsamir/halaqati/' + p).read_text(encoding='utf-8')
errors = []
chip = rd('ui/components/Components.kt')
chip = chip[chip.index('fun ProFilterChip'):]
chip = chip[:chip.index('fun StatusPill')]
if 'border = null' in chip:
    errors.append('ProFilterChip must draw a border')
# SPEC CHANGE (RC98, deliberate): this gate used to require 'compositeOver' (the chosen fill was a 26% primary tint over the surface).
# Intent kept: a chosen option is unmistakable by (1) a thicker border, (2) a visibly stronger fill, (3) bolder text, never by hue alone.
# (2) is now a solid primary fill, which is stronger than the tint. The tokens for (1) and (3) are unchanged. That the fill really
# stands out (>= 3:1 against the surface) and its label reads (>= 4.5:1) is measured by ContrastTest.filterChipStatesAreDistinguishable.
for tok in ('if (selected) 2.dp else 1.dp', 'FontWeight.Bold', 'selectedContainerColor = MaterialTheme.colorScheme.primary,', 'selectedLabelColor = MaterialTheme.colorScheme.onPrimary'):
    if tok not in chip:
        errors.append('ProFilterChip missing ' + tok)
if 'FilterChip(' in rd('ui/components/AdaptiveControls.kt').replace('ProFilterChip(', ''):
    errors.append('AdaptiveControls must use ProFilterChip')
pal = rd('ui/theme/HalaqatiPalette.kt')
if 'outlineVariant = 0xFF2B3933' in pal:
    errors.append('dark outlineVariant is too faint')
repo = rd('data/HalaqatiRepository.kt')
if 'private fun localizeGuardianOverview' not in repo or repo.count('localizeGuardianOverview(') < 3:
    errors.append('guardian overview must be localized on the device')
if errors:
    print('RC38 choice-clarity gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC38 choice-clarity gate passed')
