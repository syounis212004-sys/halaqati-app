# Offline mushaf pack, round 2: final report (2026-11-25)

Status: awaiting the owner's approval. Nothing was released; no mushaf engine code was changed; `main` received only PR #103 (skills). Tier: deep.

## 1 Problem and fixed requirements
Shrink the offline mushaf pack (208 MB today) toward under 10 MB (ideal), 25 MB (excellent), 50 MB (acceptable) while keeping the exact Quran text, harakat, waqf signs, and the word/line/page layout; zoom, night mode, tap, highlight; offline; Kotlin/Compose; no modification of fonts whose licence forbids it; the single Hafs font is not accepted as the end state.

## 2 Sources
Quran Foundation Developer Terms (2026-11-20) and Content Sync tutorials [FACT]; QF font CDN documentation [FACT]; DigitalKhatt repositories (madinafont OFL 1.1, digitalkhatt.org MIT) [FACT]; five independent research agents and one PR reviewer; two other-AI reports compared earlier. Details and quotes: `03-research.md`, `12-round2-research.md`.

## 3 Alternatives and measurements (full matrix: `13-options-round2.md`)
| Option | Size [MEASURED unless noted] | Tier |
|---|---|---|
| Current V2 TTF per page | 208.0 MB | none |
| Best lossless re-encoding of V2 (R2A, estimate for the combination) | about 85-100 MB; floor 11 bits/point | none |
| QF V2 woff2 | 97.9 MB | none |
| QF V1 woff2 | 48.07 MB, but **565 of 604 pages have different line breaks** from V2 (361 words on another page) | fails layout rule |
| **QF V4 colrv1 woff2** | **50.88 MB** (48.5 MiB); same code_v2 data works (604/604 pages covered); decodes byte-for-function identical to V4 TTF; tajweed colours; re-drawn 1441H print (advances 0.81-1.33 x V2, line widths +3%) | borderline acceptable |
| Lossy codec q24 (R2A/R2C) | about 46 MB, 0.77 px error at 4x zoom; modifies KFGQPC outlines (forbidden) | rejected |
| Raster pages | 78-196 MB | rejected |
| Hafs + spacing stretch | 1.7 MB; gaps 2.5x median | rejected as end state |
| **DigitalKhatm precomputed vector pack** | **3.0 MB** (all 604 pages + 570 outlines) | **ideal** |

## 4 What was verified for the leading options
- **DigitalKhatm layout vs the app's current V2 layout:** 8,809 of 8,820 lines have the same word count (99.88%); 597 of 604 pages are fully equal; differing pages 27, 177, 254, 400, 443, 451, 589.
- **DigitalKhatm text integrity** (letters and marks derived from glyph names vs QF text, 77,332 words): letters 99.99%; letters and marks equal in **99.53%**; 350 of the 361 residual words differ only in a **waqf sign**, across 235 pages. Visual check: for page 9 line 14, V2 shows the qaf-lam-alef sign and **both V4 (official 1441H) and DigitalKhatm show jeem**; page 5 line 3 shows a boxed sign in V4 that neither V2 nor DK has. So the residual is part edition update, part DK omission; it needs a qualified reviewer.
- **Visual comparison V2 | V4 | DigitalKhatm** on pages 1, 2, 50, 77, 255, 355, 589, 604 (sent to the owner, not committed because they contain Quran text). DK letterforms are Madina-style, close to the print but not the KFGQPC outlines; strokes slightly heavier; lines, kashida, verse markers and the surah banner and basmala lines render as printed.
- **Kotlin renderer (lab app)** executed with Robolectric native graphics: page 300 ink 153,156 px vs the Chromium reference 153,118 px, IoU 0.9948. Two defects that would have crashed the app on a device were found and fixed by this test.
- **V4 fidelity:** V4 woff2 decoded vs the official V4 TTF: 0 of 3,060 sampled glyphs differ, hinted or unhinted (20 pages).

## 5 Risks (pre-mortem, see `08-critique.md` and `13`)
1. Quran Foundation terms: RC111's stale page-data fallback and the missing credit (C3, C5 in `15-compliance-plan.md`) [FACT as read, not confirmed by QF]. 
2. DK waqf/edition differences and upstream text issues (small madda omitted in 15 sajdah verses reported upstream, issue #26 with no reply).
3. DK layout data derives from KFGQPC page data: DK issue #26 asks the same question; ask DK and KFGQPC in writing.
4. V4 colours and night mode on Android are untested; COLR rendering below Android 10 unknown; the decoder needs an NDK library (503 KB arm64) or a Kotlin port.
5. Everything on Android is untested on a device (no device).

## 6 Recommendation
**Two tiers (Option K):**
1. **Default offline tier: DigitalKhatm vector pack, 3.0 MB.** It is the only tested option that reaches the ideal tier with the current page/line layout, OFL/MIT licences, any API level, no NDK, and a renderer that already passes a real render test. It is conditional on the owner's visual approval, on a scholarly review of the 350 waqf-sign differences (and fixing them in the data, never by the assistant), and on written replies from DigitalKhatm and KFGQPC about the line data.
2. **Optional "authentic print" tier: QF V4 woff2, 50.88 MB** (per juz download as today; decoder library; colour policy for night mode), if the owner wants the official KFGQPC glyphs. V2 stays as the legacy path until V4 is verified on devices.
3. Compliance first (independent of the above): Content Sync via a backend, credit text, owner decision on the RC111 stale fallback (`15-compliance-plan.md`).

It does not reach "exactly V2 outlines under 50 MB": no tested option does within the licences.

## 7 Engineering design and plan (proposal)
See `11-design.md` (round 1) amended by: renderer in `labs/mushaf-dk-lab` (promote to the app only after approval), pack generator `tools/make_assets.py` (assets are generated, never committed), word-identity mapping from Content Sync `mushafs` rows to DK words by order, tests: layout equality (604 pages), text-integrity script per release, Robolectric render checks, device screenshot checks on 3 phones.
Phases: P0 compliance and emails; P1 device test of the lab APK + owner's visual sign-off + scholar review list; P2 Content Sync backend; P3 integrate DK tier behind a setting; P4 V4 optional tier (decoder, palettes, night mode); P5 retire V2 after V4 passes.

## 8 Skills, agents and experiments actually used (honest)
| Item | Use |
|---|---|
| `engineering-discovery` | followed manually; auto-invocation verified earlier in a fresh headless session; not invoked through the Skill tool in this session |
| thinking-first-principles, pre-mortem, red-team, steel-manning, kepner-tregoe, systems, scientific-method | read-and-applied in round 1; round 2 relied on independent agents for critique |
| ui-ux-pro-max, android-design | invoked (Skill tool) before the UI change in the compliance branch |
| Sub-agents | R2A, R2B, R2C, R2D (two runs), R2E, PR #103 reviewer |
| Experiments | `experiments/` (layout equality, text integrity, V1/V2 layout, V4 coverage and fidelity, renders) and `labs/mushaf-dk-lab` tests |
Not used: bmad-* skills, research, bmad-deep-recon, superpowers:brainstorming/writing-plans (the author did brainstorming and planning).

## 9 Not verified
Any behaviour on a device or emulator; real Content Sync access and whether `mushafs` rows carry glyph codes; V4 colour/palette/night-mode rendering on Android; the 350 waqf cases one by one; KFGQPC licence texts (official pages unreachable by the agents); DK's KFGQPC-derived line data permission; V4 vs V2 differences beyond the pages viewed; legal interpretation of any clause.

## 10 Decisions needed from the owner
D1 send the two emails (drafts in `outreach/`); D2 confirm whether a Developer Console account exists; D3 interim handling of the RC111 stale fallback; D4 look at the DK pages (images and the lab APK) and say whether the Madina-style letterforms are acceptable; D5 who reviews the waqf-sign list; D6 whether V4 (colours) is wanted as the authentic tier.
