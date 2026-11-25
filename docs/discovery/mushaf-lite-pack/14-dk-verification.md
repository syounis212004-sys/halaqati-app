# 14 DigitalKhatt verification (what was actually run)

Inputs: `https://digitalkhatt.org/assets/layouts/madina.json` (47,129,116 B; keys classes, glyphs (570 outlines), pages (604), suras (114)); the app's current data (api.quran.com by_page, 604 pages, fields code_v2, line_number, v2_page, text_uthmani); outlines and algorithm from the MIT repo (`ClientApp/src/app/components/precomputed/layout.ts`).

## 1 Layout equality vs the app's V2 layout [MEASURED, `experiments/dk_layout_equality.py`]
Word counts per line, lines matched by line number (header and basmala lines have type 1/2 and no API words, 226 skipped). 8,820 lines compared, **8,809 equal (99.88%)**, 597 of 604 pages fully equal. Differing pages: 27, 177, 254, 400, 443, 451, 589 (line differences of one or two words; line breaks differ between the 1441H and 1421H editions or the counting of a glued mark). A first attempt that used only the single API page undercounted (words with v2_page = page also sit in neighbouring API pages); the app itself builds a page from n-1, n, n+1, as done here.

## 2 Text integrity [MEASURED, `experiments/dk_text_verify_marks.py`; letters and marks, no Quran text printed]
Method: per word, derive from the DK glyph names (a) letter units (ligature glyphs count their letters) and (b) the mark sequence (harakat, shadda, sukun, maddah, small letters, waqf signs by name), and from the API `text_uthmani` the same two quantities (precomposed hamza forms decomposed; U+06E2/U+06ED unified with DK's tanween-idgham and iqlab encodings). Ayah-end markers are excluded; 108 words in lines whose word counts differ were skipped.
- Words compared 77,332. **Letter-unit counts equal 99.99%.**
- Letters and marks equal after unifying encodings: **76,971 words = 99.53%**; 364 of 604 pages have every word equal.
- Residual 361 words: **350 differ only in a waqf sign** (examples: API has the qaf-lam-alef sign where DK has jeem, 88 words; API has the lam-alef sign where DK has none, 67; sad where DK has none, 54; sad vs jeem 41 and the reverse 26; others), plus 11 other words. They span 235 pages. List: run the script with the residual output.
- Visual spot check against the V2 fonts the app shows now (pages 9, 5, 21): page 9 line 14: V2 shows the qaf-lam-alef waqf sign while DigitalKhatt shows jeem (a real edition difference, as printed); pages 5 and 21 looked equal. The edition question must be decided by the owner/scholarly reviewer.
- Not proven: dots are not compared; the base letter identity for letters with dots; correctness of Arabic shaping.

## 3 Visual comparison [produced by the author with Chromium and FreeType]
Triples V2 | V4 | DigitalKhatt for pages 1, 2, 50, 77, 255, 355, 589, 604 (sent to the owner; they contain Quran text so are not committed). DK pages render the surah banner text line and the basmala line as printed; V2/V4 in the comparison HTML only show word lines. DK strokes are slightly heavier and its kashida differs from the KFGQPC print; line contents match. IoU against V2 is not meaningful (different drawings), so judgement is visual.

## 4 Size [MEASURED]
Asset pack built by `labs/mushaf-dk-lab/tools/make_assets.py`: 603 gzip page files + `glyphs.json.gz` (227,386 B) = **2,998,155 B**.
