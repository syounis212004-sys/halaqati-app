# Evidence log (one line per action actually performed)
S1 | read MushafStore.kt (lines 14-34, 95-135, 224-226) and grepped app for Quran Foundation credit | source/app/src/main/java/.../quran/MushafStore.kt; grep over source/app/src/main | FACT
S1 | re-read RC111 stale fallback and class header | MushafStore.kt:61-81, :21-22 | FACT
S2 | read and applied thinking-first-principles (Procedure 1-6) | .claude/skills/thinking-first-principles/SKILL.md | FACT
S3 | agent B fetched terms/licences; author re-read original Terms HTML and Content Sync page | /tmp/claude-0/agentB/4dd765.html; https://api-docs.quran.foundation/docs/tutorials/content-sync/getting-started/ | FACT
S3 | author tested CDN transfer encoding | curl Accept-Encoding gzip/br on ttf/p300.ttf | MEASURED
S3 | compared two other-AI reports (ChatGPT md, Gemini html) against own measurements | uploads | FACT
S4 | brainstorm and TRIZ attempt for contradiction, 9 options | 04-options.md (superpowers:brainstorming not invoked) | ASSUMPTION
S6 | read and applied thinking-systems | .claude/skills/thinking-systems/SKILL.md | FACT
S7 | read and applied thinking-kepner-tregoe Decision Analysis | .claude/skills/thinking-kepner-tregoe/SKILL.md; 06-comparison.md | FACT
S8 | three background sub-agents (A one-font, B terms, C raster/codec) | 07-agents/*.md | FACT
S9 | read and applied thinking-steel-manning, thinking-red-team, thinking-pre-mortem | .claude/skills/thinking-*/SKILL.md; 08-critique.md | FACT
S10 | E1 lossless baselines (zip, zstd solid, per-juz, decode times) | experiments/whole.py, juz.py | MEASURED
S10 | E2 own-page subsetting over 604 pages | experiments/fetch_api.py, subset_all.py | MEASURED
S10 | E3 QF WOFF2 fidelity, 60 pages, built google/woff2 from source | experiments/woff2_fidelity.py | MEASURED
S10 | E4 Hafs one-font render, page 300 and 10, visual check after fixing RTL placement bug | experiments/render_cmp.py | MEASURED
S10 | read and applied thinking-scientific-method | .claude/skills/thinking-scientific-method/SKILL.md | FACT
S11 | re-scored with new facts; retractions listed | 10-reevaluation.md | FACT
S12 | design and plan written by the author | 11-design.md | ASSUMPTION
