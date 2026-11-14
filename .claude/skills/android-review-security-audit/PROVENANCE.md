# Provenance

- Upstream skill: `security-audit`
- Installed as: `android-review-security-audit`
- Source repository: https://github.com/liuyi0808/android-review
- Source path: `skills/security-audit`
- Source commit: `26fbdc85360e0445699a6f1e5a11cfe52005cb84`
- Maintainer / author: liuyi (liuyi0808/android-review)
- License: MIT (copy in `LICENSE`)
- Upstream `SKILL.md` sha256 (before local edits): `441a559707dbcf79616ace97068d08fd3af44e640d4bb29ce2554bcfd0d746ef`

## Local modifications (the only ones)

1. Frontmatter `name` changed from `security-audit` to `android-review-security-audit` to avoid name collisions.
2. A block delimited by `HALAQATI-LOCAL-BLOCK:BEGIN/END` was inserted right after the frontmatter. It contains
   Halaqati's read-only safety rules. Everything after the `END` marker is the upstream text, unchanged.
3. `LICENSE` and this file were added. All other files are byte-identical copies of upstream.

Reviewed before installation: no executable scripts, no network calls made by the skill itself, no credential access,
no auto-delete/auto-modify behaviour, no prompt-injection text found (see the installation report).
