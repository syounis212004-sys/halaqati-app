# 04 Options (stage 4-5)

Skills: brainstorming done manually (superpowers:brainstorming / bmad-brainstorming not invoked; logged). TRIZ: `thinking-triz` template tried on the real contradiction below.

TRIZ contradiction: "pack size must be small (under 50 MB) for weak networks BUT the page ink must be the unique V2 outlines (about 340 KB per page)." Separation tried: time (download only what you read: already RC111 per-juz), space (two tiers: shared single-font lite + optional per-page ink), condition (Wi-Fi, per juz), scale (words vs glyphs). Result: separation in space yields the hybrid (Option 7); no inventive transform removes the ink. Residual trade-off documented.

## Option 1: Status quo plus compliance fix (V2 TTF per page from the QF CDN, per juz)
Mechanism: as RC111, with page data moved to Content Sync and a visible credit. Size 208 MB full, about 6-10 MB per juz.

## Option 2: Compressed per-juz packs on our own server (zstd/xz of the same bytes)
Mechanism: lossless archive, byte-identical after decompression. Measured 111.0 MB (zstd solid), 106.9 MB (xz, ChatGPT), 136.0 MB (zip). About 3.4-3.9 MB per juz.

## Option 3: Quran Foundation's own WOFF2 files (documented CDN URLs), decoded on device
Mechanism: download `v2/woff2/p{n}.woff2` (97.84 MB for 604 pages + surah names, ChatGPT measurement; 93.22 MiB for pages), decode to TTF on open (google/woff2, MIT, via NDK) with an LRU file cache.

## Option 4: Lite mode: one Hafs Unicode font + words/lines from the API (no per-page fonts)
Mechanism: render each line from `text_uthmani` with KFGQPC Hafs (about 0.24 MB), lines/pages from `line_number`; justify with word spacing (mushaf-text style). About 1.7-5 MB total.

## Option 5: Raster pages or lines (WebP/AVIF/PNG) with a word-bounds database
Mechanism: images rendered from V2 fonts; tint for night mode.

## Option 6: Custom lossy vector codec (quantised, delta-coded outlines drawn as Compose paths)
Mechanism: quantise coordinates (grid q in font units), entropy-code, draw with Path.

## Option 7: Hybrid two-tier: Lite (Option 4) always, plus optional authentic V2 packs (Option 1 or 3)
Mechanism: first-open and offline baseline is tiny; "authentic mushaf" per-juz download remains for users who want the exact printed look. Page words/positions come via Content Sync in both tiers.

## Option 8: Lite with DigitalKhatt kashida justification
Mechanism: OFL font with variable axes and a justification engine; needs the HarfBuzz fork through the NDK and a port of the TypeScript justifier.

## Option 9: Do not shrink; improve delivery only (resume, prefetch by reading position, per-juz defaults)
Mechanism: keep 208 MB total but never download more than the user reads; resume by HTTP Range.
