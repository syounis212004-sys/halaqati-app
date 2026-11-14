# Provenance

- Upstream skill: `supabase`
- Installed as: `supabase`
- Source repository: https://github.com/supabase/agent-skills
- Source path: `skills/supabase`
- Source commit: `c9be0e931b7930f7d02126d04774d904c381e7d7`
- Maintainer / author: Supabase (supabase/agent-skills)
- License: MIT (copy in `LICENSE`)
- Upstream `SKILL.md` sha256 (before local edits): `697e0e949f97621c6fc1784169eb3ccbf673747d6bb8299a52962d3d84d93dc6`

## Local modifications (the only ones)

1. None to the frontmatter.
2. A block delimited by `HALAQATI-LOCAL-BLOCK:BEGIN/END` was inserted right after the frontmatter. It contains
   Halaqati's read-only safety rules. Everything after the `END` marker is the upstream text, unchanged.
3. `LICENSE` and this file were added. All other files are byte-identical copies of upstream.

Reviewed before installation: no executable scripts, no network calls made by the skill itself, no credential access,
no auto-delete/auto-modify behaviour, no prompt-injection text found (see the installation report).
