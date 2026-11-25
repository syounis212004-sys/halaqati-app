# 14 DigitalKhatt verification (what was actually run)

Inputs: `https://digitalkhatt.org/assets/layouts/madina.json` (47,129,116 B; keys classes, glyphs (570 outlines), pages (604), suras (114)); the app's current data (api.quran.com by_page, 604 pages, fields code_v2, line_number, v2_page, text_uthmani); outlines and algorithm from the MIT repo (`ClientApp/src/app/components/precomputed/layout.ts`).

## 1 Layout equality vs the app's V2 layout [MEASURED, `experiments/dk_layout_equality.py`]
Word counts per line, lines matched by line number (header and basmala lines have type 1/2 and no API words, 226 skipped). 8,820 lines compared, **8,809 equal (99.88%)**, 597 of 604 pages fully equal. Differing pages: 27, 177, 254, 400, 443, 451, 589 (line differences of one or two words; line breaks differ between the 1441H and 1421H editions or the counting of a glued mark). A first attempt that used only the single API page undercounted (words with v2_page = page also sit in neighbouring API pages); the app itself builds a page from n-1, n, n+1, as done here.

## 2 Text integrity [MEASURED, `experiments/dk_text_verify_final.py`; letters and marks, no Quran text printed]
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

## 5 Waqf differences vs the official V4 (1441H) [MEASURED visually, 2 cases]
Rendering the same line from V2, V4 and DigitalKhatm (Chromium): page 9 line 14: **V2 shows the qaf-lam-alef waqf sign; V4 and DigitalKhatm both show jeem**. So at least part of DK's 350 differences versus the API text/V2 is the 1441H edition that V4 also carries, not a DK error. Page 5 line 3: V4 shows an extra boxed sign that neither V2 nor DK show, so DK also lacks at least one sign that the newer official edition (and the API text) has. The residual list therefore mixes edition updates and DK omissions; **it needs a qualified reviewer, case by case** (`dk_text_residual.json` written by `experiments/dk_text_verify_final.py` lists the 350 words by page, line, verse and position). Not run: a systematic V4-vs-DK comparison for all 350 words.

## 6 Lab app (labs/mushaf-dk-lab) [MEASURED]
- Standalone app, `com.halaqati.lab.dkmushaf`, own Gradle project outside `source/` (does not enter the release chain or the RC zips).
- Renderer (`DkData.kt`, `DkRender.kt`): reads the gzip assets, interpolates tatweel masters, draws one Canvas per page; word rectangles for tapping; pager over 604 pages, pinch/double-tap zoom, night mode, on-screen render/load timings.
- Two defects found by the Robolectric render test, both would have crashed the app on a device: AGP renames `.gz` assets inside the APK (assets are now `.bin`), and tatweel/x-scale values are fractional in the data (parsed as floats now).
- Test result (Robolectric native graphics, SDK 35): 3 tests pass. Page 300 rendered by the Kotlin code vs the Chromium reference: ink 153,156 vs 153,118 px, **IoU 0.9948**, each covered 100% by the other within 1 px.
- Debug APK 14.86 MB (of which the whole mushaf pack is 3.0 MB). **Not run on a device or emulator.**
