#!/usr/bin/env bash
# Rehydrate a fresh Codex checkout; never download Android dependencies or data.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.."
export PATH="$HOME/.local/bin:$PATH"
export GRAPHIFY_VIZ_NODE_LIMIT=0
if ! command -v graphify >/dev/null 2>&1 && [[ -x .venv-graphify/bin/graphify ]]; then
  export PATH="$PWD/.venv-graphify/bin:$PATH"
fi
version=0.9.121
needs_install=1
if command -v graphify >/dev/null 2>&1 && [[ "$(graphify --version)" == "graphify $version" ]]; then
  interpreter="$(head -n 1 "$(command -v graphify)")"
  interpreter="${interpreter#\#!}"
  if "$interpreter" -c 'import graphify, tree_sitter_sql' 2>/dev/null; then needs_install=0; fi
fi
if [[ "$needs_install" == 1 ]]; then
  if command -v uv >/dev/null 2>&1; then
    uv tool install --force "graphifyy[sql]==$version"
  elif command -v pipx >/dev/null 2>&1; then
    pipx install --force "graphifyy[sql]==$version"
  else
    python3 -m venv .venv-graphify
    .venv-graphify/bin/python -m pip install "graphifyy[sql]==$version"
    export PATH="$PWD/.venv-graphify/bin:$PATH"
  fi
fi
interpreter="$(head -n 1 "$(command -v graphify)")"
interpreter="${interpreter#\#!}"
mkdir -p graphify-out
"$interpreter" -c 'import pathlib,sys; p=pathlib.Path("graphify-out"); (p/".graphify_python").write_text(sys.executable); (p/".graphify_root").write_text(str(pathlib.Path.cwd()))'
if [[ ! -f .codex/skills/graphify/SKILL.md ]]; then
  graphify install --project --platform codex
fi
if [[ ! -e .agents/skills/graphify ]]; then
  mkdir -p .agents/skills
  ln -s ../../.codex/skills/graphify .agents/skills/graphify
fi
# Official installer preserves existing Git hooks and is idempotent.
graphify hook install >/dev/null
"$interpreter" tools/knowledge/manage.py refresh
