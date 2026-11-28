# 01 Context: what exists, what is fixed

Skills applied (read-and-applied from `.claude/skills/<name>/SKILL.md`; the Skill tool cannot run them): thinking-first-principles (stage 2). Stage 1 is direct code reading.

## Current system (RC111, published 2026-11-25)  [FACT, code]
- `source/app/src/main/java/com/mohammedsamir/halaqati/quran/MushafStore.kt`: page data from `https://api.quran.com/api/v4/verses/by_page/{n}?words=true&word_fields=code_v2,text_uthmani,page_number,line_number&per_page=50` (line 226); one QCF V2 TTF per page from `https://verses.quran.foundation/fonts/quran/hafs/v2/ttf/p{n}.ttf` (line 224); loaded with `Typeface.createFromFile` (line 109). Page JSON cached on disk; RC111 added `downloadPages`, `deletePack`, `packStatus`, and a stale-page fallback when the network fails (lines 61-81).
- Each word already carries `text_uthmani`, `line_number`, `page_number`, `v2_page`, `code_v2` (lines 95-100). A one-font renderer needs no new fields.
- Header comment of the class (line 21-22) says page data "is cached on the device for at most one week (Quran Foundation terms)". RC111's stale fallback contradicts it (see F-COMP-1 in 03-research.md).
- No Quran Foundation or KFGQPC attribution exists in any user-visible string; grep over `source/app/src/main` finds the names only in code comments [MEASURED: `grep -rniE "quran foundation|quran.com|KFGQPC|مجمع الملك فهد"` -> only MushafStore.kt and MushafEngine.kt].
- Repo hooks (`.claude/enforce`) gate edits by required skills; `.claude/settings.json` is protected.

## Fixed requirements (from the user)
Exact Quranic text; same words on the same lines and pages as the current layout; zoom, night mode, tap on word, ayah highlight; offline after download; Kotlin/Compose engine; licences respected; weak-network friendly (Gaza); target full pack under 50 MB, preferably under 30 MB.
Not authorised: editing mushaf code, merging to main, publishing an APK before approval.

## Baseline size [MEASURED]
605 TTF files = 207,963,984 bytes (208.0 MB, 198 MiB). The CDN serves them without transfer compression (`Accept-Encoding: gzip|br` returns identical 346,212 bytes for p300; `Accept-Ranges: bytes`, `cache-control: max-age=25600000`).
