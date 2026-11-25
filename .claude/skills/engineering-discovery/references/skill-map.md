# Stage to skill map

`Skill` = invoke with the Skill tool. `Read` = author set `disable-model-invocation: true`, so read `.claude/skills/<name>/SKILL.md` and follow its Procedure; log it as "read and applied".

| Stage | Primary | Optional | Mode |
|---|---|---|---|
| 1 Understand | domain-modeling, codebase-design | bmad-project-context | Skill |
| 2 First principles | thinking-first-principles | thinking-five-whys-plus, thinking-map-territory | Read |
| 3 Research | research | bmad-deep-recon | Skill |
| 4-5 Options | superpowers:brainstorming | bmad-brainstorming (Skill); thinking-triz, thinking-via-negativa, thinking-model-combination (Read) | mixed |
| 6 Systems | thinking-systems | thinking-second-order, thinking-theory-of-constraints | Read |
| 7 Compare | thinking-kepner-tregoe | thinking-opportunity-cost, thinking-reversibility, thinking-margin-of-safety | Read |
| 8 Agents | superpowers:dispatching-parallel-agents | bmad-party-mode | Skill |
| 9 Critique | thinking-red-team, thinking-pre-mortem | thinking-steel-manning | Read |
| 10 Experiments | thinking-scientific-method | prototype (Skill) | Read / Skill |
| 11 Re-evaluate | bmad-advanced-elicitation | bmad-review | Skill |
| 12 Design | superpowers:writing-plans | bmad-architecture, bmad-agent-architect, improve-codebase-architecture | Skill |
| Routing | thinking-model-router | | Read |

## Overlap rules (do not duplicate procedures)
- Pre-mortem: use `thinking-pre-mortem`. Do not also run the pre-mortem method inside `bmad-advanced-elicitation` in the same pass.
- Research: `research` or `bmad-deep-recon`, one per question.
- Brainstorm: `superpowers:brainstorming` for dialogue with the user, `bmad-brainstorming` for solo divergence; not both.
- Critique of a person's idea: `thinking-steel-manning` then `thinking-red-team`. `grilling`/`grill-me` interrogate the user and are used only when the user asks to be grilled.
- Debugging stays with `superpowers:systematic-debugging`; TDD with `superpowers:test-driven-development`.
- BMAD skills expect `_bmad/` project setup. Without it they offer setup; decline it unless the user approved BMAD setup for this repo, and fall back to the generic procedure.
