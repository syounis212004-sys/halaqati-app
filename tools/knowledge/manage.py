"""Small local integrity/freshness interface; never print the complete graph."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "graphify-out"
VERSION = "0.9.121"


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


def graph():
    path = OUT / "graph.json"
    if path.stat().st_size > 48 * 1024 * 1024:
        raise ValueError("Unexpected graph size; inspect locally, never dump it to context")
    return read_json(path)


def freshness():
    from graphify.detect import detect_incremental
    from graphify.extract import _get_extractor
    result = detect_incremental(ROOT, str(OUT / "manifest.json"), kind="ast")
    changed = [str(Path(f).relative_to(ROOT))
               for values in result.get("new_files", {}).values() for f in values
               if _get_extractor(Path(f)) is not None]
    deleted = [str(f) for f in result.get("deleted_files", [])]
    return {"fresh": not changed and not deleted,
            "changed": changed, "deleted": deleted}


def provenance():
    try:
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD:source"], cwd=ROOT, text=True,
            stderr=subprocess.DEVNULL).strip()
    except subprocess.CalledProcessError:
        return None


def verify():
    data = graph()
    nodes = data["nodes"]
    ids = {n["id"] for n in nodes}
    edges = data.get("links", data.get("edges", []))
    assert nodes and edges and len(ids) == len(nodes), "Empty/duplicate nodes"
    assert all(e["source"] in ids and e["target"] in ids for e in edges), "Dangling edge"
    assert all(e.get("confidence") in {"EXTRACTED", "INFERRED", "AMBIGUOUS"}
               for e in edges), "Missing confidence provenance"
    from graphify.detect import ignored_predicate
    ignored = ignored_predicate(ROOT, gitignore=True)
    sources = {str(x.get("source_file") or "") for x in [*nodes, *edges]} - {""}
    for source in sources:
        path = Path(source)
        assert not path.is_absolute() and ".." not in path.parts, "Nonportable source path"
        assert (ROOT / path).is_file(), f"Missing source: {source}"
        assert not ignored(ROOT / path), f"Excluded source indexed: {source}"
    manifest = read_json(OUT / "manifest.json")
    assert all(not Path(k).is_absolute() for k in manifest), "Nonportable manifest"
    # No raw source, credentials or user datasets belong in a portable index.
    text = json.dumps(data, ensure_ascii=False)
    forbidden = [r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
                 r"\bgh[pousr]_[A-Za-z0-9]{25,}\b",
                 r"\bsbp_[a-f0-9]{32,}\b",
                 r"\beyJ[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\b"]
    assert not any(re.search(pattern, text) for pattern in forbidden), "Credential-like value in graph"
    return {"nodes": len(nodes), "relationships": len(edges),
            "source_files": len(sources), "source_tree": provenance(),
            "graphify_version": VERSION, "directed": data.get("directed"),
            "documentation_mode": "native sections/links for all supported docs; selected semantic decisions",
            "semantic_nodes": sum(n.get("_origin") == "semantic" for n in nodes),
            "limitations": ["AST coverage is partial for 21 existing parser-warning files; not compiler errors",
                            "SQL without recognized definitions may have no symbol nodes",
                            "Documentation sections/links are indexed locally; only selected decisions have semantic enrichment",
                            "Codex PreToolUse hook is an official no-op; mandatory query-first policy is in AGENTS.md",
                            "Historical release ZIPs, bundled Web code, binaries and sensitive files are excluded"],
            "semantic_api_calls": 0, "host_agent_token_usage": "not available"}


def snapshot():
    result = verify()
    OUT.mkdir(exist_ok=True)
    path = OUT / "coverage.json"
    path.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    return result


def refresh():
    os.chdir(ROOT)
    # A missing report after clone is not a reason to re-extract unchanged code.
    report = OUT / "GRAPH_REPORT.md"
    if (OUT / "graph.json").exists() and not report.exists():
        report.write_text("# Halaqati Android\n\nUse bounded graphify query/path/explain commands.\n")
    env = dict(os.environ, GRAPHIFY_VIZ_NODE_LIMIT="0")
    result = subprocess.run(["graphify", "update", "."], check=False, env=env)
    if result.returncode:
        raise SystemExit(result.returncode)
    details = snapshot()
    state = freshness()
    print(json.dumps({"fresh": state["fresh"], "changed_count": len(state["changed"]),
                      "deleted_count": len(state["deleted"]), **details}, ensure_ascii=False))
    if not state["fresh"]:
        raise SystemExit("Graph remains stale; run scoped diagnosis before source work")


def main():
    os.chdir(ROOT)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["status", "verify", "snapshot", "refresh"])
    action = parser.parse_args().command
    if action == "refresh":
        refresh()
    elif action == "status":
        state = freshness()
        print(json.dumps({"fresh": state["fresh"], "changed_count": len(state["changed"]),
                          "deleted_count": len(state["deleted"]),
                          "sample": state["changed"][:5]}, ensure_ascii=False))
    else:
        print(json.dumps(snapshot() if action == "snapshot" else verify(), ensure_ascii=False))


if __name__ == "__main__":
    main()
