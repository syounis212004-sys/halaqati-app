# Offline mushaf pack: discovery report

- Date: 2026-11-25. Tier: deep (stages 1-12; stages 1, 5 and 12 done by the author without a dedicated skill).
- Status: awaiting user approval. No app code changed, nothing merged, nothing published.

## 1. Problem and fixed requirements
Cut the offline mushaf pack (208 MB, 605 QCF V2 fonts, RC111) toward under 50 MB, preferably 30 MB, keeping the exact Quran text and the same words on the same lines and pages, zoom, night mode, tap and ayah highlight, offline use, Kotlin/Compose, and licence compliance.

## 2. Sources
Quran Foundation Developer Terms (2026-11-20) and Content Sync docs [FACT]; KFGQPC EULA embedded in UthmanicHafs1Ver18.ttf [FACT]; mushaf-text, DigitalKhatt madinafont, quran-image-generator source [FACT]. Details with quotes and URLs: `03-research.md`.

## 3. Alternatives
Nine options in `04-options.md`: status quo + fix; own-server packs; QF WOFF2; one-font Lite; raster; lossy codec; hybrid; DigitalKhatt Lite; delivery-only improvements.

## 4. Comparison
`06-comparison.md`: MUST gates first, then weighted scores (author judgement). Measured inputs:
- [MEASURED] Lossless floors: ZIP 136.0 MB, zstd solid 111.0 MB, fontTools WOFF2 119.5 MB. Per juz about 3.2-3.9 MB (zstd/brotli solid).
- [MEASURED] Own-page glyph subsetting saves only about 9% (207.8 -> 188.9 MB).
- [MEASURED] QF CDN WOFF2 (97.8 MB per ChatGPT's tally; 93.22 MiB pages) renders identically to the TTF when unhinted (31 of 8,812 sampled glyphs flagged, worst = 1 px shift) but hinted rendering garbles 701 glyphs (21 of 60 pages).
- [MEASURED] One-font Lite: font 91% of V2 size at equal width; word gaps need 2.55x median (page 300), 2.80x (page 10); agent A: 2.01x median over 20 pages.
- [MEASURED by agent C, extrapolated] Raster 78-196 MB (29 MB only for aliased 1-bit); lossy codec 46 MB only at q about 24 (0.77 px at 4x).
- [FACT] CDN gives no transfer compression; supports Range.

## 5. Risks
`08-critique.md`: pre-mortem top eight with gates. Highest: Quran Foundation approval/terms (M x H), Lite look rejected (M x H), word/line mismatch (L x H).

## 6. Recommended solution
**Option 7: two tiers.** (a) **Lite** tier: one Hafs font plus words/lines from Content Sync, a few MB total, always available offline. (b) **Authentic** tier: the existing V2 per-page fonts from the QF CDN per juz (RC111 mechanism), optionally WOFF2 later. Rationale: only path that reaches the size goal without modifying fonts or breaking the terms, while keeping the exact look as a user choice. Weighted score 157 (Option 4 alone 154, status quo 143).
It does NOT meet "under 50 MB with exactly the V2 look": no tested option does so within the licence and terms.

## 7. Engineering design
`11-design.md`: Content Sync via our backend (Supabase edge function), device repository as single source of words/lines, Lite Compose renderer, unchanged Authentic engine, compliance items.

## 8. Implementation plan
P0 compliance and questions to Quran Foundation; P1 Lite prototype and user sign-off; P2 Content Sync backend and 604-page equality test; P3 integration; P4 optional WOFF2 / kashida research. Gates in `11-design.md`.

## 9. Skills, agents, experiments actually used
| Stage | Skill/tool | Mode | Evidence |
|---|---|---|---|
| 1 | direct code reading | none | 01-context.md |
| 2 | thinking-first-principles | read-and-applied | 02 |
| 3 | WebFetch via agent B, curl, saved Terms HTML | tools | 03 |
| 4-5 | brainstorming and TRIZ done manually | none (skills not invoked) | 04 |
| 6 | thinking-systems | read-and-applied | 05 |
| 7 | thinking-kepner-tregoe | read-and-applied | 06 |
| 8 | Agent tool x3 (A, B, C) | tool | 07 |
| 9 | thinking-steel-manning, thinking-red-team, thinking-pre-mortem | read-and-applied | 08 |
| 10 | thinking-scientific-method; experiments E1-E5 | read-and-applied + commands | 09, experiments/ |
| 11 | re-score by the author | none (bmad-advanced-elicitation not invoked) | 10 |
| 12 | design and plan by the author | none (writing-plans, bmad-architecture not invoked) | 11 |
Not used: research, bmad-deep-recon, bmad-brainstorming, superpowers:brainstorming, superpowers:writing-plans, bmad-architecture, bmad-review, bmad-party-mode. The Skill tool was not used for engineering-discovery in this session (it was auto-invoked in a separate fresh headless test).

## 10. Not verified
Content Sync real access, credentials, snapshot size; whether the owner has a Developer Console account; Android behaviour (hinting, Hafs shaping, performance, RAM) on any device; DigitalKhatt rendering; a 604-page WOFF2 audit; whether Content Sync `mushafs` assignments equal `by_page`; Tarteel terms text; legal interpretation of any clause. The Terms were read by the author and a sub-agent; this is not legal advice.

## 11. Decisions needed from the user
- D1: authorise me to draft emails to Quran Foundation (Content Sync access, mobile/backend flow, asset-pack clause, stale-data question) and to KFGQPC (subset/pack permission). You send them.
- D2: confirm whether a Developer Console account exists for the app and whether a visible credit may be added.
- D3: decide the interim handling of RC111's stale fallback (keep until QF answers, or remove in a small compliance fix).
- D4: approve building the Lite prototype on a separate branch (no merge) and judging it by a side-by-side on real pages.
