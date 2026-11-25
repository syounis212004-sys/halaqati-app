# Mushaf polish + donor contests + extras: one update

> For agentic workers: use superpowers:subagent-driven-development (contests, benchmark) and executing-plans (mushaf UI). Steps use checkboxes.

**Goal:** one separate update containing everything the user approved on 2026-11-25.
**Architecture:** mushaf/UI work on branch `feat/mushaf-v2-polish` (lead); donor contests on `feat/donor-contests` (agent, own worktree); device-free performance harness on `feat/mushaf-benchmark` (agent, own worktree). Lead merges after tests; nothing is published; DigitalKhatm stays gated (pack only in the experimental APK).
**Tech stack:** Kotlin/Compose M3 (BOM 2025.10), Robolectric, Supabase SQL migrations in `source/migration/`, GitHub Actions.
**Spec:** the user's messages of 2026-11-25 (Arabic).

## Global Constraints
- Reply to the user in Arabic; fix root causes; no force-push; no PR unless asked; web publish / APK release only on the user's "انشر".
- No Quran text typed or committed (packs are generated at build time); credit/attribution lines stay.
- Supabase migrations are written as files and are NOT applied to production without approval; UI must degrade gracefully when an RPC/table is missing.
- Motion follows `halaqati-motion-guidance`; reduced-motion honoured; RTL everywhere; touch targets >= 48dp.
- Next real version must use versionCode >= 29113 (29112 was used by the experimental APK).

## Review Focus
- Donor without any linked halaqa: cannot pick one, sees an explanatory message, admin can assign (test).
- Donor tries another halaqa's id via RPC/policy: rejected (policy contract test).
- Swipe/zoom/drag conflicts: pinch zoom must not break tap/long-press marking or page swipe; swipe disabled while zoomed (test).
- Page transition never shows two pages side by side; reduced motion = instant swap (test).
- Bookmarks survive page change and restart; deleting one never touches marks (test).

## Tasks
1. Mushaf visuals (lead): ornament frame for the sura name (open-licence/own vector), dock redesign with M3 Badge/ToggleButton group. Tests: node bounds, preview PNGs.
2. Page transition (lead): HorizontalPager with RTL, per-page `graphicsLayer` fade+parallax (no side-by-side look), reduced-motion branch. Replaces `pageSwipe`.
3. Reader features (lead): bookmarks (SharedPreferences store `MushafBookmarks`), pinch zoom (zoom state per page, swipe off while zoomed), jump to ayah (surah+ayah -> `QuranMetadata.pageFor`).
4. Settings top row (lead): one row with dark mode + colour.
5. Donor contests (agent A): `donor_contests` table + RLS + notifications, donor screen, admin approval, read-only card for halaqa members (teacher, supervisor, guardian, student); donor without halaqa waits for admin assignment.
6. Performance harness (agent B): androidTest on emulator via GitHub Actions: pack load, draw of all 604 pages, heap growth over repeated navigation, APK/installed size; results as artifact; state limits.
7. V4 tajweed colours (lead, experimental only): V4 fonts via WOFF2 decoder; only in the experimental APK; verified on the user's phone, not in Robolectric.
8. Guardian word-level mistakes (lead, last): persist marks per session; only if the save flow can be extended safely; otherwise report.
9. Review + merge: ponytail-review, full unit suite, source gates, merge to main, experimental APK via workflow.
