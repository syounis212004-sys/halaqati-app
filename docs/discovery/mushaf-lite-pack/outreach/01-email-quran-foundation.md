# Email 1: Quran Foundation (draft for the app owner to send; not sent by the assistant)

To: developers@quran.com
Cc: info@quran.foundation
(Addresses verified from the official Developer Terms page s12 and the quran.foundation footer on 2026-11-25. Developer Console: https://dev-console.quran.foundation/projects)

Subject: Halaqati (free Quran memorisation app): questions on offline use, Content Sync, and font delivery

Assalamu alaikum, and thank you for the Quran Foundation APIs and fonts.

I develop "Halaqati", a free Android app used by Quran memorisation circles (halaqat). Many of our users are in the Gaza Strip, where internet access is slow, intermittent, and sometimes unavailable for days, so reading the mushaf fully offline matters a great deal to them. The app shows the 604-page Madani mushaf with your QCF V2 page fonts and the page data from the Quran.com API (words with line/page positions). I read the Developer Terms (last updated 2026-11-20) and would like to make sure we follow them exactly. I would be grateful for written answers to the following.

A. Offline page data and Content Sync
1. For a mobile app with an optional "download the mushaf for offline use" feature, is Content Sync (resource groups `quran_core` and `mushafs`) the correct and only permitted way to keep the words, line numbers and page mappings offline beyond one week?
2. How does an Android app obtain production access? Production Content Sync requires credentials; we do not want to embed secrets in the APK. Terms say a Developer may sync on its own backend and refresh device copies from it at least every 7 days. Is that the recommended pattern for a mobile app, and may our backend serve a device-ready copy of `quran_core` + `mushafs` to our app only?
3. What is the approximate size of a full copy of `mushafs` and `quran_core`, and is there a compact snapshot format? 
4. For long outages (weeks), the Terms allow continuing to use the previous synced copy. Please confirm there is no maximum age after which the offline copy must be removed while the device cannot reach your servers.
5. Is page data fetched from the public `api.quran.com` v4 `verses/by_page` endpoint covered by the same one-week rule? (We cache it for seven days today.)

B. Fonts
6. The Terms allow bundling font files obtained from documented CDN URLs "as an integrated part of the Application". Is it acceptable for the app to download the page fonts (per juz or all, on the user's request, directly from your CDN) and keep them in private app storage without a time limit? 
7. For users on weak networks, would the following be acceptable: our own backend serving the same font files, unmodified, to our app only (never as a public package), possibly losslessly compressed for transfer and restored to byte-identical files on the device? If not, is there an officially supported lighter delivery for mobile apps?
8. Your CDN also serves `v2/woff2/p{N}.woff2`. These are glyph subsets with re-encoded outlines. Are they intended for web use only? When rendered with TrueType hinting enabled, we observed mismatches between the retained hinting instructions and the outlines in some glyphs on some pages; unhinted rendering matches the TTF. Is there a recommended way to use them on Android, and are they considered equivalent in fidelity to the TTF files?
9. How can we learn when font files are corrected (version, ETag, or a changelog)? 
10. Is a single Uthmanic Hafs font with Unicode text (`text_uthmani`) and our own line layout acceptable to you for an optional lightweight mode, provided the words, lines and pages follow your published layout exactly? Is there an official per-line word assignment for the 15-line Madani pagination in Content Sync beyond the `mushafs` group?

C. Account and credit
11. We will create/confirm a Developer Console account for the app. Please confirm the exact credit wording you prefer (we plan: "Quran text and fonts provided by Quran Foundation; fonts by King Fahd Glorious Quran Printing Complex") and where it should appear.
12. Is there anything else we should change to be fully compliant?

[OPTIONAL paragraph for the owner to keep or remove: "Transparency: a recent version of our app kept previously cached page data on the device as a fallback when the user is offline, including data older than one week. We are preparing a change to move this to Content Sync and will keep nothing beyond what the Terms allow. Please tell us if you would like us to do anything specific meanwhile."]

Jazakum Allahu khayran,
[Name]
Halaqati app, [contact email], [website/store link]
