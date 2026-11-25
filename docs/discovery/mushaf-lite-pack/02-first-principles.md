# 02 First principles

Applied: `.claude/skills/thinking-first-principles/SKILL.md` (read and applied; steps 1-6).

| Claimed constraint | Class | Verdict |
|---|---|---|
| "Pack must stay QCF V2 look" | convention (user preference), not a requirement in the second brief (which fixes text, pages, lines) | Test the cost; keep as a product choice, not a hard gate. Falsifier: teachers accept a different look (needs the user's eyes: image produced, see 09). |
| "Lossless compression floor is about 93 MB" | was a measurement claim | DROPPED. The 93 MB figure is QF's own WOFF2 subset, which is not a lossless transcoding. Measured lossless floors: 107-111 MB (xz/zstd), 119.5 MB (fontTools WOFF2). |
| "Fonts cannot be modified" | regulation/contract | Binding for KFGQPC fonts (EULA: "Modified, Altered" forbidden; subsetting/format conversion unaddressed). Official QF files are a separate case. |
| "Offline copy of page data is fine for any period" | assumed | FALSE per QF Developer Terms s3.1 (see 03). |
| "Self-hosted compressed packs are fine" | assumed | UNADDRESSED/at risk: terms forbid offering fonts "separately ... asset package, or standalone download". |
| "Size is dominated by glyph outlines" | measured fact | TRUE: glyf is 98.4% of 207.8 MB; no shared glyphs across pages (32 of 701). |
| "Unused glyphs are a big share" | assumed | FALSE: per-page own-glyph subsetting saves only 9% (207.8 -> 188.9 MB). |

Irreducible primitives: (1) ink per page is unique (~125k outline points per page, 5.3 bits/point needed for 50 MB); (2) the QF terms and KFGQPC EULA; (3) text+positions must come through Content Sync for offline use; (4) a one-font renderer must justify lines with spacing or kashida.
Cheapest kill tests run: own-page subset size; WOFF2 fidelity; Hafs one-font render; terms text read from source. See 09.
