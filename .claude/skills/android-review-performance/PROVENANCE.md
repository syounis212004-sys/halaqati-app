# Provenance

- Upstream skill: `performance`
- Installed as: `android-review-performance`
- Source repository: https://github.com/liuyi0808/android-review
- Source path: `skills/performance`
- Source commit: `26fbdc85360e0445699a6f1e5a11cfe52005cb84`
- Maintainer / author: liuyi (liuyi0808/android-review)
- License: MIT (copy in `LICENSE`)
- Upstream `SKILL.md` sha256 (before local edits): `a1f0a68cb7ede052fe136461c5e6d8738918d184cfdb97cf657886dafebbd8ff`

## Local modifications (the only ones)

1. Frontmatter `name` changed from `performance` to `android-review-performance` to avoid name collisions.
2. A block delimited by `HALAQATI-LOCAL-BLOCK:BEGIN/END` was inserted right after the frontmatter. It contains
   Halaqati's read-only safety rules. Everything after the `END` marker is the upstream text, unchanged.
3. `LICENSE` and this file were added. All other files are byte-identical copies of upstream.

Reviewed before installation: no executable scripts, no network calls made by the skill itself, no credential access,
no auto-delete/auto-modify behaviour, no prompt-injection text found (see the installation report).
