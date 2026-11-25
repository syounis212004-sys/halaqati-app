# 18. Full 604-page integrity comparison, DK vs QF V2/1421H (Phase 2) — agent result, spot-checked by the lead

Reference: the V2/1421H edition as encoded by the QF API (`text_uthmani`, `v2_page`, `line_number`). DK has no Unicode text; words were rebuilt from glyph names (code-point mapping tables only; no Quran text typed). No V4/1441H marks mixed.

## Counts (agent-measured; lead re-checked: 3,238 rows in differences.json/csv, class totals, UI opens with no console errors)
- 77,433 words compared; **3,232 words differ (591 pages); 13 pages have no text difference.**
- Letter units equal on every page (325,386 each side). Ayah-end markers 6,236 each side, sequence correct.
- **The claim "all differing words are waqf-only" is FALSE: only 340 / 3,232 (10.5%) are waqf-only.**

| class (provisional machine label) | words |
|---|---|
| tanween (1,870 = API U+06E2 iqlab meem vs DK open-tanween glyph mapped to tanween+U+06ED; 328 tanween vs plain vowel + meem sign) | 2,189 |
| hamza encoding (684 of 690 rows incl. mixed = hamza on yeh seat under kasra) | 612 (+90 mixed) |
| waqf only (e.g. U+06D7→U+06DA 85, U+06D9→none 61, U+06D6→none 54) | 340 |
| mixed tanween+waqf / hamza+waqf | 9 / 3 |
| line-break differences | 6 |
| OTHER (needs specialist): p223 11:13 w3 (alef vs wasla+alef-maksura+small alef: the only letter-level difference); p236 12:11 (U+06EB vs U+06EC); p282 17:7 (U+06E5 vs U+08F3) | 3 |

Also: page 117 5:52 API splits one word in two (merged); 12 words differ only in mark order (not compared elsewhere). Sajdah: DK has 15 signs, API text 15, but API `sajdah_number` lists 14 (22:77 p341 has no metadata). Juz/hizb/rub come from the API only; DK carries none (41 of 240 rub boundaries have no DK glyph).

## What this does NOT prove
Counts/IoU do not prove integrity. Two mapping assumptions (open-tanween→tanween+U+06ED; `roundedfilledlow` U+065C ≈ U+06EA) could hide or create differences. Visual position of marks verified only for sampled line images (e.g. p4 L2 iqlab looks identical). Reference is the API's encoding, not the printed mushaf. The labels are not scholarly judgements.

## Consequence
DK is **not** text-identical to the reference. It must not become default or be released until a qualified specialist decides every class (esp. the 1,870 iqlab and 684 hamza cases and the 3 OTHER words). Review UI: `labs/mushaf-dk-lab/verification/review.html` (offline, Accept/Reject/Needs-specialist, notes, localStorage, JSON/CSV export; line images are generated locally by `render_all.py`, not committed).
