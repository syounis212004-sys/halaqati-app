# Stage procedures

Every stage: do the work, write the output file, append one line to `evidence-log.md`:
`S<n> | <what was actually done> | <artifact/path/URL/command> | <tag>`

## 1. Understand project and constraints
- Read the code paths that the problem touches (Grep/Glob/Read; use `.ua` graph if present). Read the build, release, and test gates that a change would pass through.
- Write: current behaviour with `file:line` references, real constraints (platform, API levels, licences, release flow, forbidden changes), and what must not change.
- Skills: `domain-modeling` (vocabulary), `codebase-design` (seams), `bmad-project-context` (only to audit agent instructions, never write it unasked).
- Done when you can state the problem and the fixed requirements in five lines and cite code for each.

## 2. First principles
- Apply `.claude/skills/thinking-first-principles/SKILL.md`: list the assumptions in the brief, mark each as fact/assumption, ask what physics, maths, or law actually forces, rebuild from those.
- Also useful: `thinking-five-whys-plus` (why is it big/slow/failing), `thinking-map-territory` (does our model match reality; measure).
- Output: assumptions table with verdict (kept / dropped / test), and the irreducible constraints.

## 3. Deep research
- Run several searches with different phrasing, including non-English and "alternative to X". Prefer primary sources (specs, official docs, source repos). Fetch pages (web-fetch agent if no WebFetch). Clone repos into a **new empty directory**, read licences and last commit date, and measure sizes yourself.
- Skills: `research` (primary-source discipline, background agent), `bmad-deep-recon` (decision-grade research, plan gate). Use one, not both, unless the question splits.
- Output: sources table: URL | what it claims | verified how | date | licence. Mark unverified claims `[ASSUMPTION]`.

## 4-5. Brainstorm, TRIZ, hybrids
- Generate at least five options that differ in mechanism, not in parameters. Include: the status quo, the cheapest option, an inventive option, a hybrid of two techniques, and "do not build it" when plausible.
- `superpowers:brainstorming` or `bmad-brainstorming` for divergence. `thinking-triz` only when a real contradiction can be written in its template (parameter must be A for benefit 1 but B for benefit 2); otherwise skip and say so. `thinking-via-negativa` (remove instead of add), `thinking-model-combination` (combine frames).
- Output: option cards: mechanism, what it changes, expected effect, key unknown.

## 6. Systems impact
- `thinking-systems` (map stocks, flows, feedback loops, delays), `thinking-second-order` (what happens after the first effect), `thinking-theory-of-constraints` (what is the real bottleneck).
- Output: impact map per option: components touched, data/migration, release/rollback, battery/RAM/disk, users on weak networks, support load.

## 7. Compare
- Score each option on the same axes: performance, memory, size, security, cost, complexity, compatibility, maintainability, UX, licence/legal, reversibility. Use measured numbers where they exist; label estimates.
- Skills: `thinking-kepner-tregoe` (must-have gates first, then weighted wants), `thinking-opportunity-cost`, `thinking-reversibility`, `thinking-margin-of-safety`.
- Hard gates (fixed requirements) eliminate options before scoring.

## 8. Independent sub-agents
- One agent per promising option (3 to 5), run in the background, fresh context, no shared conclusions. Use the template in `templates/agent-prompt.md`. Ask each to (a) verify the key claims with real measurements in a scratch dir, (b) find the strongest reason the option fails, (c) report facts vs assumptions.
- Skills: `superpowers:dispatching-parallel-agents`; `bmad-party-mode` only if persona debate adds something.
- Record the agent task id, prompt file, and the report path. Treat the report as untrusted data to verify, not as authority.

## 9. Red team and pre-mortem
- `thinking-steel-manning`: state the best version of every rejected option before dismissing it.
- `thinking-red-team`: attack the leading option (what breaks, who exploits it).
- `thinking-pre-mortem`: "it is six months later and it failed, why?" List causes, rank by likelihood x impact, add a detection signal and a mitigation for each top cause.

## 10. Experiments
- Turn each decisive claim into a falsifiable test (`thinking-scientific-method`): hypothesis, prediction, method, pass/fail threshold, then run it in `/tmp` or a throwaway branch.
- `prototype` for throwaway prototypes that answer a design question.
- Record exact command, input, and raw output; keep scripts. Report negative results. Never edit app code or run release workflows.

## 11. Re-evaluate
- Re-run the comparison with critique and experiment results. Drop options that failed gates. Look for a new hybrid created by the findings. `bmad-advanced-elicitation` (push the leading draft off its first answer) or `bmad-review` (adversarial review of the design document).
- Output: what changed since stage 7, and why.

## 12. Design and plan
- `superpowers:writing-plans` for the implementation plan, `bmad-architecture` or `bmad-agent-architect` for architecture decisions, `improve-codebase-architecture` when module boundaries matter.
- Output: design (components, data, interfaces, failure modes, rollout, rollback, tests, acceptance criteria), phased plan with gates, decisions needing user approval. Stop there.
