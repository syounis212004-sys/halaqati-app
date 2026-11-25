---
name: engineering-discovery
description: Evidence-based engineering discovery before building. Use when asked to study an idea, design or scope a feature, compare technical approaches, reduce size/cost/risk, or solve a hard engineering problem, and before proposing a design. Runs 12 logged stages (understand, first principles, web research, brainstorm and TRIZ, systems, compare, parallel sub-agents, red team, pre-mortem, isolated experiments, re-evaluation, architecture) and ends with a report. Arabic triggers - دراسة، تصور هندسي، عصف ذهني، بدائل، جدوى.
when_to_use: User says study, investigate, design a solution, brainstorm, find alternatives, "what is the best approach", feasibility, or gives a problem with no agreed design yet. Not for routine edits, a clear bug fix (use systematic-debugging) or a task whose design is already approved.
---

# Engineering discovery

A protocol, not a checklist. Each stage produces a file under `docs/discovery/<slug>/` and an entry in `evidence-log.md` that says what was **actually done**. Use `templates/` to start; `scripts/check_discovery.py` validates the folder.

## Hard rules

1. **Do only what you log.** A stage is complete only when its log entry names the real action: skill file read, search query, command run, agent used, experiment output. Never claim a skill, test, or source you did not use.
2. **Tag every claim**: `[FACT]` (with URL), `[MEASURED]` (method + command + result), `[ASSUMPTION]` (temporary, with how to test it).
3. **Criticize the user's idea with evidence.** Do not agree by default. Challenge the brief before building on it.
4. **Do not stop at the first workable answer.** Stop when the design is evidence-supported and the viable alternatives are exhausted.
5. **Missing information**: research more, state a temporary assumption, continue. Ask the user only for decisions that are theirs (scope, risk appetite, approval).
6. **A tool or skill is unavailable**: record why, use the closest alternative, continue.
7. **No app-code edits, merges, or releases before the user approves the design.** Experiments live in `/tmp` or a throwaway branch, never on `main` or the build gates.
8. Fixed requirements stay fixed (for example exact Quran text, page and line layout, licences). A design that breaks one is rejected, not "traded off" silently.

## How to use skills (token economy)

- Pick the **one best** skill per stage, two only when roles differ. Never load all of them.
- Unsure which frame fits: apply `thinking-model-router` first (it returns none or one primary).
- Author-disabled skills (`disable-model-invocation: true`, the whole `thinking-*` set): the Skill tool cannot run them. **Read** `.claude/skills/<name>/SKILL.md` with the Read tool and execute its Procedure and Verification sections, then log "read and applied <path>". This reading approach is our workaround; it is not an officially documented invocation path. The user can also run them directly with `/<name>`.
- Skills the Skill tool can run (research, bmad-*, codebase-design, prototype, superpowers:*): invoke them normally and log the invocation.
- Depth tiers: **light** (stages 1, 3, 4, 7, 12), **standard** (add 2, 5, 6, 9), **deep** (all 12 with sub-agents and experiments). Choose by cost of being wrong; say which tier you chose and why.

## Stages

Details, procedures, outputs, and skill maps per stage: `references/stages.md`. Stage-to-skill table: `references/skill-map.md`.

| # | Stage | Output file |
|---|---|---|
| 1 | Understand project, code, architecture, real constraints | `01-context.md` |
| 2 | First principles: decompose, challenge assumptions | `02-first-principles.md` |
| 3 | Deep web and official-source research, GitHub | `03-research.md` |
| 4 | Branching brainstorm and TRIZ, at least 5 distinct options | `04-options.md` |
| 5 | Traditional, inventive, and hybrid solutions | `04-options.md` (extend) |
| 6 | Systems impact on the rest of the system | `05-systems.md` |
| 7 | Compare on performance, security, cost, complexity, compatibility, maintainability, UX | `06-comparison.md` |
| 8 | Independent sub-agents study one option each and attack it | `07-agents/` |
| 9 | Red team, then pre-mortem | `08-critique.md` |
| 10 | Hypothesis tests and isolated experiments | `09-experiments.md` |
| 11 | Re-brainstorm and re-score using critique and results | `10-reevaluation.md` |
| 12 | Final engineering design and implementation plan | `11-design.md`, `REPORT.md` |

## Finish

Produce `REPORT.md` from `templates/report.md`: problem, sources, alternatives, comparison, risks, recommendation, engineering design, implementation plan, skills and experiments actually used (and which were only read). Run `python3 .claude/skills/engineering-discovery/scripts/check_discovery.py docs/discovery/<slug>`. List only the decisions that need the user's approval, then stop.
