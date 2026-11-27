# Persistent Android knowledge

Official tool: https://github.com/Graphify-Labs/graphify (PyPI `graphifyy[sql]`,
pinned to **0.9.121**). Official project installer: `graphify install --project
--platform codex`; native skill is in `.codex/skills/graphify`, with an `.agents`
discovery link. The previous 15 Codex skills were recovered unchanged from
`chore/codex-development-environment`; existing Claude skills are untouched.

Fresh checkout: `bash tools/knowledge/bootstrap.sh`. It reuses compatible installs,
supports uv/pipx/venv, restores all four official Git hooks and performs an
incremental local refresh. No API key, Android build, live database or hosted
Graphify service is required. Git hooks are local and must be restored per clone.

Use `graphify query "specific component"` (default 900-token cap), `graphify path
"DashboardViewModel" "HalaqatiOfflineDatabase"`, and `graphify explain
"HalaqatiRepository"`. Disambiguate duplicate SQL definitions by node ID or path.
After code/doc changes: `graphify update .`; snapshot with the saved interpreter:
`"$(cat graphify-out/.graphify_python)" tools/knowledge/manage.py snapshot`.

Tests: `"$(cat graphify-out/.graphify_python)" -m unittest discover -s tools/knowledge/tests`.
`coverage.json` records extraction limits. All supported Markdown sections/links
are indexed structurally; seven selected decisions have semantic enrichment.
Refresh only affected semantics with the skill. Semantic usage in host agents is
not measurable here; no separate model API calls are made by the local updater.
Never commit raw extraction caches, secrets, personal data or machine paths.
