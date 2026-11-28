# 09 Experiments (all in /tmp scratch or throwaway branch; app code untouched)

Applied: `thinking-scientific-method` (read-and-applied): symptom, competing hypotheses, falsifiers, cheapest discriminating checks. Scripts are in `experiments/`.

## E1 Lossless baselines for the unmodified fonts  [MEASURED]
Command: `experiments/whole.py` (605 TTF from the QF CDN) and `experiments/juz.py`.
- Raw 207,963,984 B; ZIP/Deflate-9 136.0 MB; Zstd-19 solid with long window 111.0 MB. Per juz (solid): juz 1 (21 files, 8.67 MB raw) zstd 3.90 MB, brotli-11 3.67 MB; juz 15 (6.90 -> 3.85 / 3.66); juz 30 (6.87 -> 3.40 / 3.21). Page 300 (346,212 B) decode time zstd 0.6 ms, brotli 1.8 ms, zlib 2.4 ms on the build host (not a phone).
- Independent (ChatGPT, not re-run): XZ -9e 106,914,112 B over 604 TTF with SHA-256 match after decode.

## E2 Own-page glyph subsetting  [MEASURED]
Hypothesis: a large share of each TTF is glyphs the page does not use. Method: for all 604 pages fetched `by_page` JSON, took the `code_v2` characters of words whose `v2_page` equals the page, subset each TTF with fontTools (layout features kept; hinting kept or dropped).
Result: 207.8 MB -> 188.9 MB with hinting (91%) and 187.4 MB without (90%). Hypothesis falsified: unused glyphs are only about 9%. Own codes per page: min 36, mean 145.7, max 188.
Consequence: QF's -53% WOFF2 size does not come from subsetting alone.

## E3 QF CDN WOFF2 vs original TTF  [MEASURED, 60 pages: every 10th from page 5; decoded with google/woff2 `woff2_decompress`, built from source]
- Own-code set: CDN WOFF2 cmap equals the page's own `code_v2` set (p3 142, p300 147, p604 73); all own codes present; 0 codes absent.
- Unhinted FreeType render at 120 px: 8,812 glyphs compared; 31 flagged (0.35%) across 18 pages; at 200 px relative ink difference: median 0, p99 1.4%, max 30%; the two worst glyphs inspected visually: identical shapes with a 1-pixel offset (image in /tmp, not committed).
- Hinted (TrueType bytecode on): 701 flagged glyphs on 21 of the 60 pages; e.g. p3 renders garbled because instructions no longer match the re-encoded outlines. (Our first view of this was a PIL hinted render and looked like 5/60 broken pages; unhinted shows they are not broken outlines.)
- Not tested: whether Android's text stack applies bytecode hinting to these fonts. Treat as the central risk of Option 3.
Script: `experiments/woff2_fidelity.py`.

## E4 One-font Lite render vs V2, same page and width  [MEASURED]
Method: `experiments/render_cmp.py`, Hafs v0.18 + uharfbuzz 0.57, words shaped separately, gaps stretched so every line fills the width, font size chosen so the widest line fits.
- Page 300: Hafs em = 57.9 px vs V2 64.0 px (91%); needed gap multiplier median 2.55, p90 2.95, max 3.31. Page 10: 90%; median 2.80, p90 3.69, max 3.98.
- Visual check by the author (a first render was wrong because HarfBuzz returns RTL glyphs in visual order: fixed and re-rendered): correct words on correct lines; gap rhythm visibly looser than V2, and verse markers sit as separate circles. The side-by-side PNG is at `/tmp/claude-0/study2/cmp_p300.png` (it contains Quran text rendered from fonts, so it is not committed to the repo).

## E5 CDN transfer  [MEASURED]
`curl -H "Accept-Encoding: gzip|br"` on `.../ttf/p300.ttf`: identical 346,212 bytes, no `content-encoding`; `accept-ranges: bytes`. So no free transfer-compression win; resume by Range works.

## E6 Source terms  [FACT]
Original Developer Terms and Content Sync page re-read from saved HTML (03-research).

## Not run
Android device measurements (no device); DigitalKhatt rendering (needs the HarfBuzz fork); full 604-page WOFF2 audit; real Content Sync calls (no credentials).
