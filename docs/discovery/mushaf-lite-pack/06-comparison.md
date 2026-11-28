# 06 Comparison

Applied: `.claude/skills/thinking-kepner-tregoe/SKILL.md` Decision Analysis (read and applied): MUSTs first, then weighted WANTs, then downside. Scores are judgement (1-5), not measurements; the measured inputs are in 09.

## MUSTs
M1 exact Quran text and the same words/lines/pages. M2 works fully offline after download. M3 no unauthorised font modification and only distribution paths the terms allow or leave open. M4 fits Kotlin/Compose. M5 offline page data obtained through Content Sync (QF terms).
| Option | M1 | M2 | M3 | M4 | M5 (as designed) | Passes? |
|---|---|---|---|---|---|---|
| 1 status quo + fix | yes | yes | yes (QF CDN, credit needed) | yes | after fix | yes (needs fix) |
| 2 own-server packs | yes | yes | NO until QF confirms ("asset package") | yes | after fix | no (for now) |
| 3 QF WOFF2 | yes | yes | yes (documented URL); decode unaddressed | yes | after fix | yes |
| 4 Lite Hafs | yes if line test passes | yes | yes (font unmodified) | yes | after fix | yes |
| 5 raster | words ok | yes | unaddressed (derived images) | yes | after fix | no (size) |
| 6 lossy codec | words ok | yes | NO (modifies outlines) | yes | after fix | no |
| 7 hybrid | yes | yes | yes | yes | after fix | yes |
| 8 DigitalKhatt lite | yes if tested | yes | yes (OFL) | heavy | after fix | yes (high effort) |
| 9 delivery only | yes | yes | yes | yes | after fix | yes |

## Measured inputs per surviving option
| Option | Full pack | Per juz | V2 look | Zoom/night/tap | Other measurement |
|---|---|---|---|---|---|
| 1 | 208.0 MB | 6.9-8.7 MB | exact | unchanged | stale fallback issue |
| 3 | 97.8 MB (-53%) | about 3-5 MB (est.) | glyph outlines visually identical unhinted (see 09); hinted rendering garbles some glyphs | unchanged if unhinted | decoder needed; Android hinting behaviour not tested |
| 4 | 1.7 MB (mushaf-text) | n/a | different: gaps 2.0x median (agent A) to 2.55x (own render p300) | zoom perfect (vector), night easy, tap per-word | font size 91% of V2 at same width |
| 7 | 1.7 MB + optional | optional | user choice | both | adds complexity |
| 8 | about 3 MB est. | n/a | closer to print | vector | HarfBuzz fork + port: weeks, untested |
| 9 | 208 MB | read-driven | exact | unchanged | no size reduction |

## WANTs (importance 1-10) and scores (1-5)
| WANT | w | 1 | 3 | 4 | 7 | 8 | 9 |
|---|---|---|---|---|---|---|---|
| Small total size | 9 | 1 | 2 | 5 | 5 | 5 | 1 |
| Same look as V2 | 7 | 5 | 5 | 2 | 4 (user choice) | 3 | 5 |
| Low effort | 6 | 5 | 3 | 3 | 2 | 1 | 5 |
| Low legal risk | 8 | 3 | 4 | 4 | 4 | 4 | 3 |
| Performance (RAM/time) | 5 | 4 | 3 | 5 | 4 | 3 | 4 |
| Reversibility | 5 | 5 | 4 | 4 | 4 | 3 | 5 |
| Weighted total (sum of w x score) | | 143 | 138 | 154 | 157 | 134 | 143 |

Arithmetic: Option 1: 9+35+30+24+20+25 = 143. Option 3: 18+35+18+32+15+20 = 138. Option 4: 45+14+18+32+25+20 = 154. Option 7: 45+28+12+32+20+20 = 157. Option 8: 45+21+6+32+15+15 = 134. Option 9: 9+35+30+24+20+25 = 143.
Ranking: 7 (157) > 4 (154) > 1 = 9 (143) > 3 (138) > 8 (134). The weights and scores are the author's judgement and the user should set the weights. Sensitivity: with w = weight of "Same look as V2", Option 1 = 108 + 5w and Option 7 = 129 + 4w, so Option 1 overtakes Option 7 only when w > 21 (7 was used, on a 1-10 scale). Option 4 = 140 + 2w, so Option 1 overtakes Option 4 once w > 10.7, which is the case for a user who values the printed look above everything else.
Downside test (K-T step 6): Option 4/7's adverse consequence is user rejection of the Lite look (probability medium, impact high); Option 7 limits it because the exact tier stays. Option 3's adverse consequence is a decoder crash or hinting garble on some devices (probability unknown, impact high for those pages).
