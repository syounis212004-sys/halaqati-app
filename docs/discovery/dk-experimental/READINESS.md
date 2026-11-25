# Publication-readiness report: experimental DigitalKhatm viewer (2026-11-25)

**Verdict: NOT ready for publication.** It ships only as an opt-in, default-off experimental path in a private APK. Supporting studies 16-18 (licence, seven pages, 604-page integrity) are in `docs/discovery/mushaf-lite-pack/` on branch `docs/mushaf-research-round2` (PR #105).

## What exists
- Code: `quran/dk/DkData.kt`, `DkPack.kt` (pack detection, per-process data, id lookup, `PAGES_NEEDING_REVIEW={177}`), `ui/screens/MushafDkPage.kt`, integration in `MushafScreen.kt` (`PageState.Dk`; toggle in the jump dialog, shown only when the pack is present).
- The pack encodes the Quran text, so it is never committed (`.gitignore`); `scripts/dk/make_dk_assets.py` generates it from `madina.json` at build time. A production build has no pack: `DkPack.available()` is false, no toggle, nothing runs.
- Word identity: ids (`sura:ayah:pos|end`) derived from the DK stream (ayah-end glyphs, sura-header lines) with 4 merges (DK splits بعد ما x3, إل ياسين). `scripts/dk/verify_ids.py`: 0 of 604 pages differ from the QF API id sequence (measured).
- Marking uses the same session/tally as the current viewer (tap, long-press kind menu, drag highlight/erase), same palette, RTL and frame.
- Private APK: `.github/workflows/Halaqati_DK_experimental.yml` (dispatch only, artifact only, versionCode 29112 / `2.91.11-dk`, no release job).

## Tests (local, Robolectric/JVM)
DkPackTest 6/6 (incl. all 604 pages ids == DK word rects; page 300 render), MushafEngineTest 9/9, MushafOfflineDialogTest 1/1; standard source gates pass except 4 environment-only tools (python playwright / missing args).

## Blocking items before any publication
1. Text integrity: 3,232 of 77,433 words differ from the V2/1421H reference (340 waqf-only; 2,189 tanween incl. 1,870 iqlab; ~700 hamza encoding; 3 OTHER). A qualified specialist must decide every class via `labs/mushaf-dk-lab/verification/review.html` (research branch). No text/waqf altered by guess.
2. Licence: layout and text provenance of `madina.json` unknown; 49 AGPL-headed DK files; KFGQPC terms unread; written answers needed (outreach drafts in the research branch; the user sends them).
3. Page 177: DK puts 8:6:10 on line 12 (same as the app's `LINE_OVERRIDES`; API/QUL say line 11): needs a printed-page check. The page stays on the current viewer.
4. Not measured: device/emulator open time, FPS, RAM/CPU, leaks, installed size (pack 3.0 MB). `source/benchmark` exists but was not run. Only JVM draw correctness is verified.
5. Not implemented in the DK path: pinch zoom, dedicated night mode (uses app palette ink/paper), bookmarks, jump to ayah/juz, per-word accessibility labels.
6. V4 option not built (byte-exact Kotlin WOFF2 decoder exists in the research branch, `labs/woff2-kt`); compliance migration (Content Sync, outage-safe mechanism) is plan only.

## Update 2026-11-25 (reader polish, approved by the user)
Done: standalone "المصحف" menu entry (continue reading, juz/surah jump, offline download, credit lines) for admin/teacher/supervisor/guardian; reader mode without marking tools; swipe to turn pages (finger right = next, Arabic book direction; active in the Tap tool and in reading mode, drag stays for highlight/erase); jump dialog lists the assigned range under "ضمن المقرر" plus juz and surah jumps; sura-name frame and coloured ayah numbers / pause signs in the new viewer; equal page margins; dock count badges inside their buttons; settings: update, account, appearance and admin diagnostics (incl. measurements) are folded rows; the new viewer is the mushaf viewer whenever the pack is present (production builds have no pack, so they are unchanged); the word "DigitalKhatm" is gone from the UI, attribution stays on the mushaf entry screen (OFL/MIT notices must remain).
Tests: 269 unit tests, 0 failures (new: MushafReaderPolishTest, GuardianMistakesTest, DkPagePreviewTest, nav policy). Guardian: sees the child's mistakes/prompts of the last day (counts), unchanged; word-level view for guardians would need persisting marks (not built).
Still open: integrity review (3,232 words), licence answers, page 177, device measurements, V4/tajweed colours, zoom/night/bookmarks in the new viewer.

## Update 2 (2026-11-25): combined update on branch feat/mushaf-v2-polish
Done and tested (286 unit tests, 0 failures; source gates pass):
- Mushaf: sura-name ornament (own vector drawing; web search found no confirmed CC0 banner), dock rebuilt with Material 3 `BadgedBox`/`Badge` and tonal toggle buttons (corner morphs on selection), page turning with a pager (fade + partial travel, never two pages side by side, reduced motion = swap at mid point), pinch zoom 1x-4x in the new viewer, bookmarks (per device), jump to surah + ayah, jump dialog with the assigned range and bookmarks, quick appearance row (dark mode + colour) at the top of Settings.
- A review test found and fixed a real bug: invisible neighbour pages pulled back by the parallax sat on top of the current page and stole touches.
- Donor contests (agent, branch merged): migration `source/migration/20261125_v2920_donor_contests.sql` (NOT applied; checked read-only against the live schema: notifications.kind has no check constraint; student memberships filtered to active), donor/admin/read-only screens, notifications, tests.
- Performance harness (agent, merged): `DkPerfTest` + workflow `Halaqati_DK_perf.yml` (emulator, artifact only). No numbers yet; it must be run.
Not done: V4 tajweed colours (needs verification of colour-font rendering on a real phone), guardian word-level mistakes (needs DB change in the save flow), zoom in the classic viewer, rejected-contest editing.
