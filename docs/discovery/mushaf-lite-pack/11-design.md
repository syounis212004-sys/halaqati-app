# 11 Engineering design and plan (proposal; nothing implemented)

Applied: `superpowers:writing-plans` and `bmad-architecture` were NOT invoked; this is the author's plan using the findings above (logged).

## Goal
Reliable offline mushaf for weak networks, compliant with the Quran Foundation terms, with a tiny always-available tier and an optional exact-V2 tier. Fixed: exact text, same words per line per page.

## Architecture
```
Quran Foundation Content Sync (production)
        | (credentials only on backend)
   Supabase edge function  ->  snapshot store (quran_core + mushafs), SHA-256, sequence
        | HTTPS, device refresh at least every 7 days when online
   Device: MushafTextRepository (Room/OfflineDatabase)  <- single source of words, lines, pages
        |                         |
   Lite renderer (Compose Canvas)     Authentic renderer (existing engine)
   Hafs font (0.24 MB)                V2 page fonts from the QF CDN (per juz, RC111 mechanism)
```
- **Data**: replace `by_page` calls with the repository; keep a migration that deletes legacy `pages-v2` JSON.
- **Lite renderer**: per-word measured placement (mushaf-text style, MIT code; either dependency or reimplementation after licence check), spacing-stretch justification with a cap; one font size per page; per-word rectangles for tap/highlight/marks; night mode by colour scheme.
- **Authentic tier**: unchanged glyph rendering; optional switch to QF WOFF2 only after a 604-page device audit with hinting off.
- **Compliance**: visible credit ("Quran fonts provided by Quran Foundation", KFGQPC), Developer Console account, no self-hosted font packs, stale text policy per terms.
- **Resilience**: per-file download with HTTP Range resume, SHA-256 of fonts recorded after first download, retries with backoff, packs in `filesDir`.

## Phases and gates
| Phase | Work | Gate (must pass to continue) |
|---|---|---|
| P0 Compliance (no new features) | Email QF (developers@quran.com): Content Sync production access; mobile/backend flow; confirm stale-fallback and asset-pack questions. Add credit; confirm Console account. | Written answer or a user-accepted fallback; decision on the RC111 stale fallback (D3) |
| P1 Lite prototype (separate branch, no merge) | Compose renderer for 20 sample pages; device screenshots on 3 phones | User visual sign-off (D4) |
| P2 Content Sync backend | Supabase function + device repository + 7-day refresh + integrity check | 604-page test: per-line word ids equal between repository and `by_page`; sync job alert works |
| P3 Integration | Lite tier in the offline dialog; Authentic tier reads the repository; migration of old caches | Unit/instrumentation tests, gates green, no engine regressions |
| P4 Optional | WOFF2 audit; DigitalKhatt kashida research | Device audit passes |

## Acceptance criteria
- Lite offline pack at most 5 MB including data (to be measured; mushaf-text reports 1.7 MB assets). No page uses a different word-per-line assignment (100% over 604 pages).
- Page open p95 at most 150 ms on a mid-range phone (target; unmeasured).
- Zoom 1x to 5x without softness; night mode; tap on word; highlight; marks.
- No retention of Content Sync-eligible data beyond the sync rule without a successful sync.
- Rollback: the Lite tier is behind a setting and can be disabled remotely; Authentic tier untouched.

## Not in scope / not recommended
Raster pages, custom lossy outline codec, self-hosted font packs (until QF confirms), modifying KFGQPC fonts.
