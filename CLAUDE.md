# Halaqati (Android, Kotlin/Compose) - agent instructions

- Reply to the user in Arabic. Fix root causes. Never publish a failed release; web publish and APK release need the user's explicit approval. Do not force-push. No PR unless asked.
- The `.claude/enforce/` hooks gate edits by required skills; they are protected and only the user can waive them.

## Studying ideas, designing features, hard engineering problems
When the task is to study, compare approaches, find alternatives, estimate feasibility, or design before building, use the `engineering-discovery` skill first (`.claude/skills/engineering-discovery/SKILL.md`). It runs a logged 12-stage protocol (first principles, web research, brainstorm and TRIZ, systems, parallel sub-agents, red team, pre-mortem, isolated experiments, architecture) and ends with a report. Do not edit app code, merge, or publish before the user approves the design.

- Skills authored with `disable-model-invocation` (the `thinking-*` set) cannot be run by the Skill tool: Read their `SKILL.md` and apply the procedure, and report it as "read and applied".
- Pick one skill per stage; do not load many. See `.claude/skills/engineering-discovery/references/skill-map.md`.
- Third-party skills are vendored unmodified; provenance and licences are in `.claude/skills-vendor/`.
- Separate facts, measurements, and assumptions. Never claim a test or source that was not actually run.
