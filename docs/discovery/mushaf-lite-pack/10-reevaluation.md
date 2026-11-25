# 10 Re-evaluation after critique and experiments

Applied: `bmad-advanced-elicitation` was NOT invoked (logged); the re-score below is the author's, using the Decision Analysis in 06 with the new facts.

What changed versus the earlier plan (pre-discovery notes in the chat on 2026-11-25):
1. **Retracted:** "lossless floor is about 93 MB". The 93 MB figure is QF's own re-encoded per-page subset, not a lossless transcoding. Measured lossless floor is 107-111 MB.
2. **Downgraded:** Option 2 (zstd packs on our server) was the earlier recommendation. The terms name "asset package, or standalone download" as forbidden, so it is blocked pending written confirmation (D1).
3. **Upgraded to viable-but-insufficient:** Option 3 (QF WOFF2): outlines are visually identical when rendered unhinted, but hinted rendering garbles pages and 97.8 MB misses the target.
4. **New MUST found (M5):** offline page words/positions must come through Content Sync. RC111's stale fallback conflicts with the terms as read (F-COMP-1), and a visible credit is required (F-COMP-2).
5. **Dropped:** the earlier proposed "Phase 0: WebP prototype on ten pages". Agent C measured raster at 78-196 MB; no prototype needed.
6. **New leader:** Option 7 (Lite always + authentic optional) at 157 vs 154 (Option 4 alone), driven by size and by keeping the exact tier.
7. **Hybrid idea examined and kept:** Lite as the offline baseline and V2 per-juz packs as an enhancement (progressive: Lite shows immediately when a V2 page is not yet downloaded).

Open assumptions (temporary): [ASSUMPTION] Content Sync `mushafs` rows contain the same `line_number`/`position` assignment as `by_page`; test: compare all 604 pages. [ASSUMPTION] Our backend may hold Content Sync credentials under the "own backend" clause; test: written QF confirmation. [ASSUMPTION] Android does not apply bytecode hinting; test: device screenshots.
