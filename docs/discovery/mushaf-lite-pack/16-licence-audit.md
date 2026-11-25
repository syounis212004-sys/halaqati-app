# 16. Licence and source audit (Phase 1) — independent agent report, condensed

Status: output of an independent sub-agent (not legal advice). Items marked [VERIFIED-BY-ME] were re-checked by the lead; everything else is the agent's claim and must be re-verified before relying on it. Not cleared for shipping where marked UNKNOWN.

| # | Component | Finding | Cleared? |
|---|---|---|---|
| 1 | Madina font (madinafont) | OFL 1.1 (Amine Anane, Tarteel Inc.), no Reserved Font Name. Exception: V1 `digitalkhatt.otf` is AGPL-3.0 + embedding exception: do not use. Live-site `madina.otf` is not byte-identical to the repo copy (two builds). | Yes for OFL fonts, with notices |
| 2 | Engine / site code | Repo-level MIT, but 49 dkorg files carry AGPL-3.0 headers; `layout.ts` (the renderer we ported) has no header. Author clarification needed. | Partly |
| 3 | Quran text in DK | No source or licence statement in any DK repo. | UNKNOWN |
| 4 | Page/line layout (`madina.json` pages) | No metadata, no licence, no provenance. QUL id 21 calls it "exact copy of KFGQPC V2 (1421H) layout". Agent's line test vs quran-qcf4 (1441H): page equal for 6,236 ayahs, line differs for 736 (consistent with not being 1441H). | UNKNOWN |
| 5 | Generated packs | Inherit weakest link (3, 4). | Not cleared |
| 6 | QUL layouts page | 12 layouts; every Licence tab (incl. DK id 21, DK fonts/scripts) says "We don't have copyright information for this resource." QUL is not a licence source. | n/a |
| 7 | fonts.quran.ws | Aggregator; labels DK items "OFL-1.1"; OFL on a text bundle is a category error; no terms page; bundle link 404. | Not a primary source |
| 8 | `qcf4-layout-v2.bin` | Derived from quran-qcf4 `verses.json` @5130511 (blob SHA matches the app manifest); that repo's LICENSE.md: JSON data MIT (c) 2026 Mohamad Hajj Rabee, fonts excluded. Upstream origin of page/line facts UNKNOWN (only "chapter metadata" is declared derived from Quran.com). 18 KB integer table, low practical risk. | MIT verified; upstream UNKNOWN |

## Unknown / not done
1. KFGQPC current terms (archived page unreachable, old URL 404).
2. Author's intended licence for `madina.json`, DK text, and the 49 AGPL-headed files.
3. Source and terms of the DK Quran text.
4. Whether DK outlines derive from KFGQPC artwork.
5. Exact print edition of `madina.json` (1420H/1421H wording inconsistent; not 1441H breaks).
6. Upstream provenance of the quran-qcf4 page/line data.
7. GitHub issues/discussions not read (github.com HTML and API return 403 here); clones are shallow.
8. tarteel.ai/terms only keyword-searched.

## Consequence for the project
- DK engine stays an **experimental, default-off** lab path. No public release of any pack containing `madina.json` content, DK text or AGPL-headed code until the author answers in writing.
- No DK 1441H layout exists; for 1441H line breaks the only open MIT-labelled source found is quran-qcf4.
- Contacts: DigitalKhatt / Amine Anane (https://digitalkhatt.org), Tarteel as sponsor; KFGQPC info@qurancomplex.gov.sa; quran-qcf4 issue tracker. The user sends all messages himself.
