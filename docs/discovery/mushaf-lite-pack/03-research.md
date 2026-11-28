# 03 Research: sources and verified facts

Skills: stage 3 used web fetch + sub-agent B (terms) + own reads of the original Terms HTML. `research`/`bmad-deep-recon` were not invoked (not needed for this question; logged honestly).
Tags: [FACT] cited, [MEASURED] we ran it.

## Quran Foundation terms (read from the saved original, last updated 2026-11-20)  [FACT]
Source: https://api-docs.quran.foundation/legal/developer-terms/ (agent B fetched; we re-read the text and quote it here).
- s3.1 "Developer must NOT: ... Cache or store Quran Foundation Content longer than 1 week unless it is obtained and maintained through the Content Sync APIs listed under Content Available for Offline Sync, or consists of font files or Mushaf images cached or bundled as described below, or recitation audio downloaded for offline listening ..."
- "If a content resource is available through Content Sync, Content Sync is the only permitted path for obtaining and maintaining an offline copy of that resource; do not build an offline copy from regular API responses. ... perform a next sync at least every 7 days when connectivity ... permits."
- "A Developer may obtain Quran Foundation Content through the Content Sync APIs on its own backend and refresh the Application's device copies from that backend, provided ... device copies are refreshed from it at least every 7 days when connectivity permits."
- Fonts: "A Developer may cache or bundle font files and Mushaf images obtained through Quran Foundation APIs or documented CDN URLs ... if the Developer maintains an active account in the Developer Console and credits Quran Foundation in a reasonably accessible place in the Application ... The files may be distributed only as an integrated part of the Application. They may not be offered separately to others—for example, through the Developer's own API, asset package, or standalone download."
- Content Sync docs https://api-docs.quran.foundation/docs/tutorials/content-sync/getting-started/ : resource group `mushafs` = "Mushaf metadata, page mappings, and positioned words. Font files and images are not included." Production needs "approved production access" and matching credentials; base URL https://apis.quran.foundation/content/api/v4.
- Font tutorial https://api-docs.quran.foundation/docs/tutorials/fonts/font-rendering/ lists documented URLs for v2 in `ttf`, `woff`, `woff2`.

### Findings that affect the shipped RC111
- **F-COMP-1 (important).** RC111 keeps page JSON longer than one week and serves it as a stale fallback when offline. By the quoted text, page words/positions (`mushafs` group) may be kept beyond one week only through Content Sync. Fonts are covered by the bundling clause. This is our reading of the text, not legal advice, and is unconfirmed by Quran Foundation.
- **F-COMP-2.** The bundling clause requires an active Developer Console account and a visible credit. No user-visible credit exists in the app (01-context). Whether an account exists is unknown to us.
- **F-COMP-3.** Self-hosted compressed font packs are UNADDRESSED for a backend but "asset package ... standalone download" is named as forbidden; treat as not allowed until QF confirms in writing.

## KFGQPC licence  [FACT, agent B, from the font's own name table]
UthmanicHafs1Ver18.ttf nameID 13: "Permission is hereby granted, Free of Cost ... Use, Copy, Distribute ... cannot be Sold, Modified, Altered, Translated, Reverse Engineered, Decompiled ..." The QCF V2 page fonts carry no EULA text in their name tables. Subsetting, format conversion, compression, rendering to images are UNADDRESSED in every text read. QUL licence tab for the V2 font: "We don't have copyright information for this resource."
Contacts (official pages): developers@quran.com, info@quran.foundation, info@qurancomplex.gov.sa, +966 14 861 5600.

## Technical sources
- QF CDN WOFF2 is a per-page glyph subset with re-encoded outlines: p3 has 142 glyphs vs 479 in the TTF; identical own-page code set [MEASURED, 09].
- mushaf-text (https://github.com/sherifshabans/mushaf-text, MIT code; Hafs font KFGQPC v0.18 bundled unmodified): justification by inter-word spacing only; one font size per page chosen so the widest line fits; assets about 1.7 MB (agent A, read from source `MushafPage.kt placeTokens()`).
- DigitalKhatt madinafont (https://github.com/DigitalKhatt/madinafont, OFL 1.1): kashida via a `justification` lookup that needs their HarfBuzz fork; engine is TypeScript (`just.service.ts`, 1131 lines); stock Android shaping cannot run it (agent A).
- quran-image-generator (https://github.com/NedaaDevs/quran-image-generator, MIT): per-line/page PNG/WebP + bounds.db; README says "not proofread against a printed Mushaf" (agent C).
- Prior art on images: Quran.com's quran_android ships downloaded page images; no size numbers found (agent C).
- Earlier reports from other AIs (ChatGPT, Gemini) were compared: several Gemini claims were wrong or unmeasured (qcf_quran is QCF4 not V1; quran_library measured at 68.7 MB V4 Gzip; DigitalKhatt 3.45 MB is HTML assets, not a mushaf pack).

## Not verified
Content Sync full-copy size and real credential flow; whether the app owner has a Developer Console account; Tarteel terms body (JS-rendered, unreadable).
