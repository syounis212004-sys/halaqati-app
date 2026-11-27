# Halaqati Android — persistent project knowledge

Repository: `syounis212004-sys/halaqati-app`. Limit work to the repository requested by
the user; do not open the independent Web repository without task authorization.
The current editable native project is `source/` (Kotlin/Compose, Room, Supabase).
Release workflows consume pinned `Halaqati_RC*.zip` archives; do not confuse an old
archive or README version with authoritative current source. Preserve all existing
Claude skills and the 15 restored Codex skills; Graphify is an additional skill.

## Mandatory start and token budget

1. Run `bash tools/knowledge/bootstrap.sh` in a fresh environment. It reuses the
   pinned installation, restores local hooks and incrementally checks the graph.
   On a reused environment run the interpreter saved in
   `graphify-out/.graphify_python` with `tools/knowledge/manage.py status`.
2. Read the short `docs/PROJECT_MEMORY.md`. For **every programming task**, query
   Graphify **before** source searches or exploration: `graphify query "precise
   component/question" --budget 900`. Use `graphify explain "Component"` or
   `graphify path "Source" "Target"` for one component/relationship. Add
   `--undirected` only to inspect connectivity, not to claim a call direction.
   If a symbol is ambiguous (especially SQL migrations/rollbacks), retry with the
   returned full node ID or `path::symbol`; do not guess which version is current.
3. Never load/dump `graphify-out/graph.json`, its full JSON/manifest/cache, or the
   full generated report into model context. Parse them locally to return small
   aggregates or exact matches only. Do not read the whole repository, repeat
   prior analyses, or rebuild already indexed portions without a concrete reason.
4. Use returned paths/line ranges; read **only** necessary source before editing.
   The graph is a map, not proof of runtime behavior. Verify signatures, callers,
   permissions and data contracts in current code. Recorded parser warnings mean
   some Kotlin/SQL relationships can be missing. Expand one query/search at a time
   only when the graph is insufficient, stale, ambiguous or partially parsed;
   state the reason briefly. Accuracy and security take priority over token savings.
5. After meaningful changes run `GRAPHIFY_VIZ_NODE_LIMIT=0 graphify update .`, then run
   `tools/knowledge/manage.py snapshot` with the saved Graphify interpreter and
   include the reviewed graph/manifest in the same change. Native Git hooks check
   commits, checkouts, merges and rebases. Recheck freshness after branch/pull.
   Markdown headings/links update locally; refresh semantic decisions only for
   affected documents using the installed skill, never all documents routinely.
6. Update `docs/PROJECT_MEMORY.md` after important work: decisions/rationale,
   verified fixes, known issues and remaining tasks with source references.
   Keep it under 100 lines; no transcripts, copied graph facts, secrets or PII.
   Avoid repeated reports; report only changes, checks and material limitations.

The official installed skill is `.codex/skills/graphify/SKILL.md`, also discoverable
through `.agents/skills/graphify`. Invoke it as `$graphify .` for an explicitly
requested initial build, or `$graphify query ...` for exploration. Codex's official
PreToolUse hook is a no-op in this version; these mandatory instructions enforce
the workflow, not a claim that the host technically blocks all raw reads.

## Application and Git safeguards

Preserve Arabic/RTL, existing product tracks and persisted-data contracts. Keep
account-scoped Room/outbox durability, RLS, authentication and migrations intact.
Do not apply SQL, change production data, expose credentials, publish an APK or
modify UI/business logic during knowledge/tooling tasks. Use a separate branch and
reviewed PR, preserve user edits/history and existing skills. Push/merge/deploy need
user authorization; explicit task authorization applies without asking twice.
No Android rebuild is required for knowledge-only changes verified to leave
`source/`, release archives and workflows byte-identical.

## graphify

This project has a knowledge graph at graphify-out/ with god nodes, community structure, and cross-file relationships.

When the user types `/graphify`, use the installed graphify skill or instructions before doing anything else.

Rules:
- When graphify-out/graph.json exists, run `graphify query "<question>"` before your first grep, search or source-file read for a codebase question, and put this rule in every subagent prompt that explores code. Read raw files only for lines the graph or an earlier read already located. Use `graphify path "<A>" "<B>"` for relationships and `graphify explain "<concept>"` for focused concepts. When the answer is under a Markdown heading, `graphify section "<heading>"` prints that section's text, so the .md need not be opened. These return a scoped subgraph, usually much smaller than GRAPH_REPORT.md or raw grep output.
- Dirty graphify-out/ files are expected after hooks or incremental updates; dirty graph files are not a reason to skip graphify. Only skip graphify if the task is about stale or incorrect graph output, or the user explicitly says not to use it.
- If graphify-out/wiki/index.md exists, use it for broad navigation instead of raw source browsing.
- Read graphify-out/GRAPH_REPORT.md only for broad architecture review or when query/path/explain do not surface enough context.
- After modifying code, run `graphify update .` to keep the graph current (AST-only, no API cost).
- A committed graphify-out/graph.json is rewritten whole on every build, so a large diff to it in a pull request is expected. Do not flag it as churn or suggest skipping the graph rebuild or its hooks.
