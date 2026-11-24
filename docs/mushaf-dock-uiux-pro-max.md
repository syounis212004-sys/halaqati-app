# Mushaf marking dock — UI/UX Pro Max run and review (2.91.9)

Skill: `.claude/skills/ui-ux-pro-max` (searched with `scripts/search.py`, stack `jetpack-compose`, plus `references/pro-rules.md`).
Queries run: touch target spacing, selected/pressed feedback, dark-mode and colour-only meaning, RTL, Jetpack Compose stack.

## Rules taken and where they are applied
| Rule | Applied as |
|---|---|
| Touch targets >= 48dp with >= 8px gaps | Every dock cell is >= 48dp wide and tall (squircle plate 46-54dp); asserted by `MushafHitAccuracyTest.dock_touch_targets_*` |
| Selected / pressed feedback without layout shift | Plate colour, ring width and scale (spring) animate; scale uses `graphicsLayer`, so nothing re-lays out |
| No colour-only meaning | Mistake = solid red badge with a cross, prompt = dashed blue ring with the letter ت, highlighter keeps its pencil icon and Arabic label; counts are numbers |
| Contrast in each theme separately | Colours come from the theme palette (`MushafPalette`), selected labels use the tool colour on the dock surface in light and dark |
| Text >= 12sp, large font scale | Labels 12sp, one line; the dock caps the font scale at 1.2 so a huge system font cannot break the row |
| Safe areas, 360dp, landscape | Dock lives inside the system-bars padding; <48dp cells fold undo/redo into the strip; landscape uses a side rail (1-3 columns by available height) |
| Reduced motion | `LocalReducedMotion` swaps every spring/tween for an instant change |
| RTL | The whole screen is forced to RTL, first cell is on the right; undo/redo icons are auto-mirrored |

## References used
Material 3 floating toolbar guidance and the `HorizontalFloatingToolbar` API (not in material3 1.4.0, so the equivalent is built from Foundation, no upgrade), Excalidraw's toolbar for tool grouping and a compact collapsed state.

## Review rounds (rendered with Robolectric + the real QCF V2 fonts)
Round 1 (411dp light, 360dp dark, 800dp tablet, landscape): dock sits below the page, page keeps 14:20, tablet dock stays 540dp wide. Found: landscape rail clipped its last row (two columns were not enough for 7 cells).
Round 2: rail now picks 1-3 columns from the real row height (76dp per row + 64dp for the handle); all seven tools are visible at 411dp landscape. Collapsed bar checked at 360dp and 411dp: type, active tool, undo/redo and the expand chevron stay.

## Not changed
Quran text, fonts, verse layout, scoring, mark persistence, database schema, Supabase.
