#!/usr/bin/env python3
"""RC29 static gate: design system (theme ladder, readable accents, typography) and the recitation-quality rule."""
import re, sys
from pathlib import Path

B = 'app/src/main/java/com/mohammedsamir/halaqati/'
errors = []
def rd(p):
    return Path(p).read_text(encoding='utf-8')

theme = rd(B + 'ui/theme/Theme.kt')
pal = rd(B + 'ui/theme/HalaqatiPalette.kt')
typo = rd(B + 'ui/theme/CairoTypography.kt')

# Every Material 3 surface role is set explicitly in BOTH schemes (unset roles fall back to the purple-grey baseline).
for role in ('surfaceDim', 'surfaceBright', 'surfaceContainerLowest', 'surfaceContainerLow', 'surfaceContainer',
             'surfaceContainerHigh', 'surfaceContainerHighest', 'inverseSurface', 'inverseOnSurface', 'surfaceTint'):
    if theme.count(role + ' =') < 2:
        errors.append('Theme.kt must set ' + role + ' in the light and the dark scheme')
for field in ('surfaceLowest', 'surfaceLow', 'surfaceContainer', 'surfaceHigh', 'surfaceHighest'):
    if len(re.findall(field + r' = 0xFF', pal)) < 2:
        errors.append('HalaqatiPalette must define ' + field + ' for light and dark')
if 'readableAccent' not in theme or 'contrastRatio' not in theme:
    errors.append('custom primary/accent must be contrast-corrected (readableAccent)')
if 'darkReadableAccent' in theme:
    errors.append('old darkReadableAccent must not return')

# Typography: Arabic is never letter-spaced and keeps a centred line box.
if 'letterSpacing = 0.sp' not in typo or 'LineHeightStyle' not in typo:
    errors.append('typography must pin letterSpacing and use a centred LineHeightStyle')
if re.search(r'fontSize = (1[01]|[0-9])\.sp', typo):
    errors.append('no text style below 12sp')

# Components: selected nav label and chips use onSurface/onPrimaryContainer, never primary on a tinted container.
shell = rd(B + 'ui/components/GoldenShell.kt')
if 'contentColor = MaterialTheme.colorScheme.onSurface' not in shell:
    errors.append('bottom bar selected label must be onSurface')
comp = rd(B + 'ui/components/Components.kt')
# SPEC CHANGE (RC97/RC98, deliberate): up to RC96 the chosen chip was a 26% primary tint over the surface with an onSurface label.
# The intent of this rule never changed: the chosen chip's label must be readable on its own fill and the choice must be obvious.
# The chosen chip is now a SOLID brand fill, so its label must be the paired onPrimary (a role pair readableOn() guarantees).
# This gate only matches text; the readability itself is measured by ContrastTest (both themes x 5 presets, WCAG 4.5:1 / 3:1).
if not ('selectedContainerColor = MaterialTheme.colorScheme.primary,' in comp and 'selectedLabelColor = MaterialTheme.colorScheme.onPrimary' in comp):
    errors.append('filter chip selected label must be onPrimary on a primary container')
ct = Path('app/src/test/java/com/mohammedsamir/halaqati/ui/ContrastTest.kt')
if not ct.exists():
    errors.append('ContrastTest.kt (real contrast measurements) is missing')
else:
    ctt = ct.read_text(encoding='utf-8')
    for name in ('rolePairsAreReadable', 'heroStripTextIsReadableInBothThemes', 'statTilesAreReadableAtBothGradientEnds', 'filterChipStatesAreDistinguishable', 'defaultButtonGradientKeepsItsLabelReadable'):
        if name not in ctt:
            errors.append('ContrastTest lost ' + name)
uc = rd(B + 'ui/screens/UnifiedCards.kt')
if 'contentColor = MaterialTheme.colorScheme.onSurface' not in uc:
    errors.append('a transparent unified card must set contentColor = onSurface (it otherwise inherits its parent\'s content colour)')
if 'cardHairline' not in rd(B + 'ui/screens/UnifiedCards.kt'):
    errors.append('unified cards must use the dark hairline')

# Quality: average safety, not the share of recitations that are not "weak".
rules = rd(B + 'plans/OrgInsightRules.kt')
if 'POOR' in rules or 'qualityPercent(rec)' not in rules or 'EVALUATION_SAFETY' not in rules:
    errors.append('group quality must be the average recitation safety')
cards = rd(B + 'ui/components/OrgInsightCards.kt')
if cards.count('safety_percentage') < 2:  # RC37: parsing moved into one shared helper
    errors.append('comparison queries must select and parse safety_percentage')

if errors:
    print('RC29 design gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC29 design gate passed')
