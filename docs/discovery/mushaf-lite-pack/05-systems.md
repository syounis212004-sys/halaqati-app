# 05 Systems impact

Applied: `.claude/skills/thinking-systems/SKILL.md` (read and applied; steps 1-7).

Boundary: user device (storage, RAM, Compose renderer), QF CDN and API, our Supabase backend, the release flow, the QF/KFGQPC terms, the user's learning flow (Quran memorisation sessions with word marks).

Stocks: bytes of fonts on device; bytes of page JSON; sync token age (7-day clock); marks/progress data keyed to pages and words. Flows: user reading position -> prefetch; Wi-Fi availability -> download; QF corrections -> font and data updates.
Loops/delays:
- Balancing: the 7-day sync obligation keeps device text fresh; a long outage (Gaza) is explicitly allowed to continue on the previous sync copy ("If connectivity ... is unavailable for longer than seven days, the Application may continue using its previously synced Content Sync copy").
- Reinforcing risk: more offline data -> more storage -> more evictions by the OS cleaning cache. Put packs in `filesDir`, not cache.
- Delay: QF font corrections ("receive corrections and improvements") vs our cached fonts, which are kept without limit (stale glyphs). Needs a refresh/invalidate strategy tied to release or ETag.
Archetype check: "Fixes that Fail": the stale-page fallback fixed the offline error but created the terms exposure (delayed side effect).
Per option impact:
- Option 1/2/3 keep the renderer, marks, highlight and word geometry untouched (glyph metrics identical or near-identical). Option 3 adds a native decoder (APK +size, crash surface).
- Option 4/7/8 change the renderer: word rectangles, highlight and tap geometry must be recomputed; session marks that key on word positions stay valid if `position`/`line_number` are the keys. Page and line assignment must be verified against the V2 layout for all 604 pages (automated test needed).
- Backend (Content Sync) adds a Supabase edge function with QF credentials (secret management) and a refresh job.
Leverage ranking (thinking-systems step 6): (1) fix information rule: where text and positions come from (Content Sync); (2) add a balancing tier (Lite always available); (3) parameters (WOFF2, compression) are low leverage here.
