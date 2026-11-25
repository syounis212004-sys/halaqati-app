# Third-party skills vendored in `.claude/skills/`

Copies of the author's skill directories, unmodified except that non-runtime `tests/` and `evals/` directories are omitted (independent review of PR #103 flagged them as dead weight). Integrity: `manifest.json` stores a sha256 per skill directory; run `python3 .claude/skills-vendor/verify.py` to recompute and compare (49/49 match; the hashes were also checked equal to the source commits with the same exclusions). Licences are in `licenses/`.

| Source | Commit | Licence | Skills installed |
|---|---|---|---|
| https://github.com/tjboudreaux/cc-thinking-skills | `7b8fece345dfaa11773be7152ccd194589cb5437` | MIT (c) 2025 TJ Boudreaux | all 28 `thinking-*` |
| https://github.com/mattpocock/skills | `d65391149dbb5fe76478e76fa8d5122b2aa5a70e` | MIT (c) 2026 Matt Pocock | research, grilling, grill-me, grill-with-docs, codebase-design, improve-codebase-architecture, domain-modeling, prototype, wait-what, handoff |
| https://github.com/bmad-code-org/BMAD-METHOD | `e435a22574fe55722841086e5566420178b98df3` | MIT (c) 2025 BMad Code, LLC; trademark terms in `licenses/BMAD-METHOD.TRADEMARK.md` | bmad-brainstorming, bmad-advanced-elicitation, bmad-forge-idea, bmad-deep-recon, bmad-review, bmad-party-mode, bmad-architecture, bmad-agent-architect, bmad-agent-analyst, bmad-project-context, bmod-core-tools |
| https://github.com/obra/superpowers | `22f0b463e7a5e579904dd6211a520321035526f7` | MIT (c) 2025 Jesse Vincent | not vendored: installed as the Claude Code plugin `superpowers@anthropic-plugin-directory` (same commit) |

`engineering-discovery` is our own skill (not third-party).

## Safety review (2026-11-25)
- cc-thinking-skills: 28 Markdown files, no scripts; author validator passes 28/28.
- mattpocock selection: no network, `rm`, `sudo`, `eval`, or push instructions; excluded `misc/git-guardrails` (hooks), issue-tracker skills (need setup), and `in-progress/`.
- BMAD selection: seven Python helper scripts; only `subprocess` use is `git` and `uv run` of the project's resolver; no network, `eval`, or deletion.
- BMAD name stays unmodified and is used only to describe the vendored software; we do not present anything as an official BMad product.

## Policies
- Overlapping descriptions (`research`, `prototype`, `bmad-brainstorming`, `grilling`) can trigger alongside project and superpowers skills; if that happens, the user can set them to `name-only` or `user-invocable-only` via `skillOverrides` in settings (the repo's settings file is protected, so this needs the owner).
- Do not edit vendored files. To change behaviour, write a wrapper or a new skill. To update, replace the directory from a newer commit and refresh `manifest.json`.
- 33 skills carry the author's `disable-model-invocation: true` (all `thinking-*`, several others). They add no description tokens to the skill listing; the model cannot call them with the Skill tool. `engineering-discovery` reads them with the Read tool, which is our workaround and not an officially documented invocation path. You can always run one with `/<name>`.
- Overlap is managed in `.claude/skills/engineering-discovery/references/skill-map.md`.
- BMAD skills expect a `_bmad/` setup in the project. It is intentionally not installed here (see PR notes).
