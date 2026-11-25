# 17. Seven differing pages: root cause (Phase 3) — independent agent report, condensed

Status: agent findings; [VERIFIED-BY-ME] only where stated. Not checked against a scanned printed page.

Result: the 11 lines are NOT 11 layout differences.
| Page / line | Cause | Class |
|---|---|---|
| 27 L14, 451 L2 | DK splits one API word that contains an internal space (بعد ما / إل ياسين) into two words; same line break | counting artefact |
| 177 (+1), 254 (+1) | same split (بعد ما, 8:6:4 and 13:37:8) | counting artefact |
| 589 L13/L14 | QF API puts end marker 84:21:7 on L13 although 84:21:6 is on L14 (only such marker in 604 pages) | QF data bug |
| 254 L6/L7, 400 L5/L6, 443 L12/L13 | QF API `line_v2` disagrees with QUL "V2 layout"; DK equals QUL V2 | V2-source conflict, DK not wrong |
| 177 L11/L12 | word 8:6:10 is on L12 in DK; API line_v2/v1, QUL V2/V4 say L11 | only real DK deviation vs those sources |

- DK vs QUL V2: 8,818/8,820 lines, 603/604 pages equal. No flagged line is a 1421H-vs-1441H difference.
- DK breaks are hard-coded input; its engine never moves words between lines. DK itself compresses (xscale<1) 29% of lines (min 0.78): equal lines do not mean "no distortion".
- Page 177 L11 at the V2 break needs 3.7% compression (min width 17,026 vs 16,400); no non-distorting fit exists.

## Cross-check by the lead [VERIFIED-BY-ME from source]
The app's own `MushafEngine.LINE_OVERRIDES` (already shipped, derived earlier from the printed layout) moves exactly these words to the lines DK uses: 27 (2:181:5→15), 177 (8:6:10→12), 254 (13:37:8→6), 400 (29:33:11→6), 443 (36:52:10→13), 589 (84:21:end→14). So against the app's adopted printed layout DK agrees on all seven; the disagreement is with the raw API / QUL V2 line data. Which source is right for 177 still needs a human check against a printed 1421H page.

## Consequences for the experimental build
- Word identity: when mapping DK words to s:a:pos, merge the DK pairs (بعد ما ×3, إل ياسين). Never use word counts as proof of text equality.
- Page 177 stays flagged "needs human review": until decided, the DK renderer may fall back to the current viewer for flagged pages.
