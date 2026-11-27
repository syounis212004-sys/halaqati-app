"""Real CLI lifecycle checks in temporary directories; no Android changes/builds."""
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]


def run(*args, cwd):
    return subprocess.run(args, cwd=cwd, text=True, check=True,
                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                          env=dict(os.environ, GRAPHIFY_VIZ_NODE_LIMIT="0"))


class KnowledgeTests(unittest.TestCase):
    def test_portable_skill_and_preserved_skills(self):
        skills = list((ROOT / ".agents/skills").glob("*/SKILL.md"))
        self.assertGreaterEqual(len(skills), 16)
        self.assertGreaterEqual(len(list((ROOT / ".claude/skills").glob("*/SKILL.md"))), 64)
        self.assertIn("name: graphify", (ROOT / ".agents/skills/graphify/SKILL.md").read_text())
        self.assertLess(len((ROOT / "docs/PROJECT_MEMORY.md").read_text().splitlines()), 100)

    def test_real_graph_queries_and_paths(self):
        for args in [("query", "HalaqatiMigrations", "--budget", "900"),
                     ("query", "source_migration_20261117_v2910_rc100_security_hardening_public_protect_profile_security_fields", "--budget", "900"),
                     ("explain", "HalaqatiRepository"),
                     ("path", "DashboardViewModel", "HalaqatiOfflineDatabase"),
                     ("path", "HalaqatiRepository", "SupabaseApi")]:
            result = run("graphify", *args, cwd=ROOT)
            self.assertTrue(result.stdout.strip())
            if args[0] == "path":
                self.assertIn("Shortest path", result.stdout)

    def test_exclusions_and_incremental_lifecycle(self):
        from graphify.detect import detect, detect_incremental
        with tempfile.TemporaryDirectory(prefix="halaqati-knowledge-") as directory:
            root = Path(directory)
            shutil.copyfile(ROOT / ".graphifyignore", root / ".graphifyignore")
            (root / "Main.kt").write_text("class Repository { fun load() = Unit }\nfun refresh(repository: Repository) { repository.load() }\n")
            for path in [".env", "release.apk", "signing.jks", "backups/users.json",
                         "build/Generated.kt", "source/app/google-services.json",
                         ".ua/intermediate/state.json", "source/migration/legacy-web-v2.44.0/app.js"]:
                p = root / path
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_text("excluded test canary")
            detected = detect(root)
            indexed = {Path(f).relative_to(root).as_posix()
                       for values in detected["files"].values() for f in values}
            self.assertEqual(indexed, {"Main.kt"})
            run("graphify", "update", ".", cwd=root)
            graph_path = root / "graphify-out/graph.json"
            before = hashlib.sha256(graph_path.read_bytes()).hexdigest()
            unchanged = run("graphify", "update", ".", cwd=root)
            self.assertEqual(before, hashlib.sha256(graph_path.read_bytes()).hexdigest())
            self.assertNotIn("to extract", unchanged.stdout)
            added = root / "Added.kt"
            added.write_text("fun addedKnowledgeProbe() = Unit\n")
            pending = detect_incremental(root, str(root / "graphify-out/manifest.json"), kind="ast")
            self.assertEqual(pending["new_total"], 1)
            run("graphify", "update", ".", cwd=root)
            data = json.loads(graph_path.read_text())
            self.assertTrue(any("addedKnowledgeProbe" in n["label"] for n in data["nodes"]))
            self.assertTrue(all(not Path(n.get("source_file") or "").is_absolute() for n in data["nodes"]))
            added.unlink()
            run("graphify", "update", ".", cwd=root)
            data = json.loads(graph_path.read_text())
            self.assertFalse(any("addedKnowledgeProbe" in n["label"] for n in data["nodes"]))

    def test_official_hooks_preserve_existing_hook(self):
        with tempfile.TemporaryDirectory(prefix="halaqati-hooks-") as directory:
            root = Path(directory)
            run("git", "init", "-q", cwd=root)
            hook = root / ".git/hooks/post-commit"
            hook.write_text("#!/bin/sh\n# existing-local-hook\n")
            hook.chmod(0o755)
            run("graphify", "hook", "install", cwd=root)
            first = hook.read_text()
            run("graphify", "hook", "install", cwd=root)
            self.assertEqual(first, hook.read_text())
            self.assertIn("existing-local-hook", first)
            status = run("graphify", "hook", "status", cwd=root).stdout
            for name in ["post-commit", "post-checkout", "post-merge", "post-rewrite"]:
                self.assertIn(f"{name}: installed", status)


if __name__ == "__main__":
    unittest.main(verbosity=2)
