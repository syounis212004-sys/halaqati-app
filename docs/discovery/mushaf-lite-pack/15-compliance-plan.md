# 15 Quran Foundation compliance plan (offline data, credit, delivery)

Status: plan only. Nothing here is implemented in the shipped app. Reading of the Developer Terms (updated 2026-11-20) and the Content Sync tutorials (client flow, offline cache patterns, full copies); not legal advice and not confirmed by Quran Foundation.

## 1 Findings (facts and observations)
| ID | Finding | Source |
|---|---|---|
| C1 | Offline copies of QF content older than 1 week are allowed only through Content Sync (or as fonts/images under the bundling clause, or downloaded recitation audio). If a resource is available through Content Sync it is the only permitted offline path; "do not build an offline copy from regular API responses". | Terms s3.1 "Storage and offline copies"; Content Sync getting-started |
| C2 | The `mushafs` resource group (mushaf metadata, page mappings, positioned words) and `quran_core` (canonical text, juz/hizb boundaries) are in Content Sync. | getting-started table |
| C3 | RC111 stores `by_page` API responses (words with line and page positions) and keeps them as a stale fallback beyond one week when offline. By C1 this is not covered. The class comment itself says "at most one week". | MushafStore.kt:21-22, 61-81 |
| C4 | Fonts: bundling/caching allowed with an active Developer Console account and a visible credit; only as an integrated part of the app; not offered separately as an own API, asset package or standalone download. | Terms s3.1 "Prepackaged content" |
| C5 | No credit string exists in the app UI and nothing in the repo, workflows or the six Supabase edge functions refers to a Quran Foundation / Developer Console account or Content Sync credentials (secret values were not inspected; only names and code). The app calls the public `api.quran.com/api/v4` without authentication. | grep over source and .github; `list_edge_functions` |
| C6 | Content Sync production needs approved production access and credentials; a developer may sync on its own backend and refresh device copies from it at least every 7 days when connectivity permits; after longer outages the previous synced copy may keep being used. | Terms; Content Sync docs |
| C7 | Unknown: whether `mushafs:1` rows include the glyph code fields used by V2/V4 fonts (`code_v2`). The docs say "positioned words"; the field list is in the API reference, not read. If glyph codes are missing, an offline V2/V4 renderer cannot be driven from Content Sync data alone; a self-contained layout (DigitalKhatt) would not need them. | docs gap |

## 2 Goals
(1) Stop keeping non-Content-Sync QF data beyond the allowed period. (2) Keep reading offline after a legitimate sync, including long outages (Gaza). (3) No secrets in the APK. (4) No sudden deletion of user data or breaking the mushaf. (5) Show the credit. (6) Independent branch, reviewed before release.

## 3 Design
```
QF Content Sync (production)  <--  Supabase edge function `quran-content-sync` (credentials only in function secrets)
        |   scheduled sync at least every 7 days, snapshots for quran_core:1 and mushafs:<id>
        v
 snapshot store (Postgres/Storage, per resource, sync sequence, SHA-256)
        |   device refresh when online, at least every 7 days  (HTTPS, app auth)
        v
 Device repository (OfflineDatabase): rows keyed (group, resourceId, recordType, recordKey), one transaction per resource, token kept only after the full copy succeeds
        v
 MushafTextRepository -> Authentic renderer (V2/V4 fonts from the QF CDN) and Lite/DigitalKhatt renderer (word identity only)
```
- Client contract = `docs/offline-cache-patterns`: per-resource transaction, unique key, idempotent apply, store the token after the final page only, re-bootstrap on token rejection.
- Device never holds QF credentials. The backend serves only the app (no public API, dataset or download endpoint) to stay within the redistribution wording ("Serving ... from a Developer-controlled backend ... is not, by itself, redistribution").
- Backend freshness alert: if the scheduled sync fails twice, notify the owner; devices continue on the last copy (allowed during outages).

## 4 Migration of the shipped cache (no sudden loss)
1. Release N (compliance prep): add visible credit; show "data last synced" age; no behaviour change to stored data; owner decision on the stale fallback (below).
2. Release N+1: Content Sync repository behind a flag; on first successful sync, import into the new store, then delete legacy `pages-v2` JSON in one step. Until then legacy pages stay readable only as the owner decides.
3. Release N+2: remove the legacy cache code and the stale fallback.

## 5 Options for the stale fallback until Content Sync is in place (D3, owner decides)
| Option | Effect | Compliance | Risk to users |
|---|---|---|---|
| Keep as is until QF answers | none | not covered by C1 as read | none now |
| Remove stale fallback, purge pages older than 7 days at launch | offline mushaf works only for the first week after a download | complies | high for weak-network users (breaks the feature) |
| Keep but stop new pack downloads and add the notice | existing copies stay, no growth | reduces exposure only | low |
Recommendation: choose after sending the QF email; if no reply or a negative one, move to the third option immediately and accelerate Content Sync.

## 6 Credit text (proposed wording, to be confirmed by QF)
"Quran text and fonts provided by Quran Foundation (quran.foundation). Page fonts by King Fahd Glorious Quran Printing Complex." Place in the mushaf offline dialog and the app's About/credits screen (Terms: "in a reasonably accessible place in the Application or its associated credits"). The lab app shows its own credits (DigitalKhatt OFL/MIT).

## 7 Verification steps once credentials exist
Call bootstrap for `quran_core` and `mushafs`; read one snapshot; check fields (C7), size, and `line`/`page` identity versus the by_page layout for all 604 pages; measure sync duration; test a token rejection and outage path.

## 8 What is not done
No Developer Console credentials were seen or used; no edge function was deployed; no Content Sync call was made.
