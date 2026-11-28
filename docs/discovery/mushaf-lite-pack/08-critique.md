# 08 Critique: steel-man, red team, pre-mortem

Applied (read-and-applied): `thinking-steel-manning`, `thinking-red-team` (adapted: the target is a design and its operating chain, no live system attacked), `thinking-pre-mortem`.

## Steel-man of the rejected options (what would make each win)
- **Option 2 (own-server packs, 111 MB)**: strongest case: byte-exact, fastest to implement, half the download, resume per juz, fits poor networks. Would be accepted if Quran Foundation confirms in writing that an in-app asset pack served from our backend is "an integrated part of the Application". Decision: not rejected, blocked; ask QF (D1).
- **Option 5 (raster)**: strongest case: simplest renderer, exact V2 look, tap data from bounds.db. Overturned only if the target becomes about 80-130 MB or images are fetched on demand. Measured 78-196 MB with soft zoom. Decision: reaffirm rejection.
- **Option 6 (lossy codec)**: strongest case: only path to 46 MB with V2 glyph shapes. Needs q about 24 (0.77 px at 4x zoom), custom drawing, and it modifies the outlines (EULA). Decision: reaffirm rejection.
- **Option 3 (QF WOFF2)**: strongest case: official files, -53%, no licence change. Fails the size goal (97.8 MB) and adds a native decoder. Decision: keep as an optional optimisation of the authentic tier.
- **Option 4 alone (Lite only)**: strongest case: 1.7 MB and perfect zoom. Fails if users reject the gap rhythm. Decision: revise: keep as a tier, not a replacement (Option 7).

## Red team of the leading design (Option 7) 
Scope: design and operating chain only. Adversaries: (1) a user with a rooted device, (2) a compromised or lapsed backend credential, (3) the data provider changing URLs or terms.
- Entry: sync backend (Supabase edge function) holding Content Sync credentials. Path: leaked secret -> attacker pulls content under our account -> account suspended (terms s5). Mitigation: secrets in the function environment only, per-request rate limit, no client-side credentials.
- Entry: device snapshot tampering (rooted user edits stored text). Impact: a user sees altered Quranic text in their own copy; app marks show wrong words. Mitigation: SHA-256 over snapshot, verified at load; fall back to refetch. (The terms stress that "The text of the Quran is not modified in any way.")
- Entry: provider moves or changes font files ("receive corrections"). Impact: layout drift between cached text and fonts. Mitigation: store the Content Sync sequence with the font fetch time; refetch fonts when sequence major changes; ETag check.
- Anti-fabrication gate: no live exploitation was attempted; these are design-level findings with a path (entry -> steps -> impact) and a fix; none was exercised.

## Pre-mortem (frame: "It is 2027-02 and the new offline mode failed.")
| # | Failure reason | L x I | Prevention gate |
|---|---|---|---|
| 1 | Quran Foundation declines to approve our Content Sync production access or the pack delivery | M x H | Ask first (D1); keep stale fallback OFF by default after the decision; gate: no release until written reply or fallback design is accepted |
| 2 | Lite look rejected by teachers (gaps 2.5x) | M x H | Visual sign-off on 20 pages by the user before Lite becomes visible; Lite stays optional; DigitalKhatt kashida is a later R&D track |
| 3 | A word or line differs between Lite/Authentic and the printed layout | L x H | Automated test over all 604 pages: per-line word-id lists identical in both tiers and equal to the by_page data; zero tolerance |
| 4 | Backend 7-day sync stops silently (job fails) and devices drift | M x M | Sync job alert; device shows last-sync age; allowed to continue after outages per the terms |
| 5 | Android renders Hafs shaping differently from HarfBuzz tests (marks misplaced) | M x M | Device screenshot comparison on 3 phones (Android 8, 12, 14) before default |
| 6 | Decoder (Option 3) crashes or hinting garbles a share of pages | M x H | Do not ship until a 604-page audit passes on device with hinting off |
| 7 | Credit/Developer Console requirements unmet | H x M | Add credit screen; confirm account (D2) |
| 8 | Storage evictions delete packs | L x M | Keep under `filesDir`; re-check on launch |
Stop rule: each top risk has a bound gate; further reasons were generic restatements.
