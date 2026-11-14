# Provenance

- Upstream skill: `compose-ui`
- Installed as: `android-review-compose-ui`
- Source repository: https://github.com/liuyi0808/android-review
- Source path: `skills/compose-ui`
- Source commit: `26fbdc85360e0445699a6f1e5a11cfe52005cb84`
- Maintainer / author: liuyi (liuyi0808/android-review)
- License: MIT (copy in `LICENSE`)
- Upstream `SKILL.md` sha256 (before local edits): `8ceae7197721ca9e2eb62eee00557d9b000fe3c5afb1f5e4ad1fd42a65c25e4b`

## Local modifications (the only ones)

1. Frontmatter `name` changed from `compose-ui` to `android-review-compose-ui` to avoid name collisions.
2. A block delimited by `HALAQATI-LOCAL-BLOCK:BEGIN/END` was inserted right after the frontmatter. It contains
   Halaqati's read-only safety rules. Everything after the `END` marker is the upstream text, unchanged.
3. `LICENSE` and this file were added. All other files are byte-identical copies of upstream.

Reviewed before installation: no executable scripts, no network calls made by the skill itself, no credential access,
no auto-delete/auto-modify behaviour, no prompt-injection text found (see the installation report).
