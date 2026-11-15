"""Exercise archive safety and edit preservation using disposable source projects."""
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile


class NativeSourceTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name) / "repo"
        (self.root / "tools/codex").mkdir(parents=True)
        self.script = self.root / "tools/codex/native-source.py"
        shutil.copy2(Path(__file__).with_name("native-source.py"), self.script)
        self.archive = self.root / "Halaqati_RC89.zip"
        with zipfile.ZipFile(self.archive, "w") as bundle:
            bundle.writestr("Project/settings.gradle.kts", 'include(":app")')
            bundle.writestr("Project/app/src/main.kt", "original")

    def tearDown(self):
        self.directory.cleanup()

    def run_tool(self, *args):
        return subprocess.run(["python3", str(self.script), *args], capture_output=True, text=True)

    def test_reextract_preserves_edits_and_export_excludes_build_outputs(self):
        original = hashlib.sha256(self.archive.read_bytes()).hexdigest()
        self.assertEqual(self.run_tool("extract").returncode, 0)
        project = self.root / ".codex-work/native/Project"
        (project / "app/src/main.kt").write_text("reviewed edit")
        (project / "app/build").mkdir()
        (project / "app/build/generated.bin").write_text("build output")
        (project / "local.properties").write_text("private local configuration")
        (project / "app/google-services.json").write_text("private Firebase binding")
        (project / ".env").write_text("private environment binding")
        self.assertEqual(self.run_tool("extract").returncode, 0)
        self.assertEqual((project / "app/src/main.kt").read_text(), "reviewed edit")
        output = Path(self.directory.name) / "review.zip"
        self.assertEqual(self.run_tool("export", "--output", str(output)).returncode, 0)
        with zipfile.ZipFile(output) as bundle:
            self.assertEqual(bundle.read("Project/app/src/main.kt"), b"reviewed edit")
            self.assertNotIn("Project/app/build/generated.bin", bundle.namelist())
            self.assertNotIn("Project/local.properties", bundle.namelist())
            self.assertNotIn("Project/app/google-services.json", bundle.namelist())
            self.assertNotIn("Project/.env", bundle.namelist())
        self.assertEqual(hashlib.sha256(self.archive.read_bytes()).hexdigest(), original)

    def test_changed_archive_refuses_to_overwrite_extraction(self):
        self.assertEqual(self.run_tool("extract").returncode, 0)
        with zipfile.ZipFile(self.archive, "a") as bundle:
            bundle.writestr("Project/new.kt", "new upstream source")
        result = self.run_tool("extract")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Preserve local edits", result.stderr)

    def test_traversal_and_symlink_members_are_rejected(self):
        for name, symlink in [("../escape.txt", False), ("Project/link", True)]:
            with self.subTest(name=name):
                with zipfile.ZipFile(self.archive, "w") as bundle:
                    bundle.writestr("Project/settings.gradle.kts", "")
                    info = zipfile.ZipInfo(name)
                    if symlink:
                        info.external_attr = 0o120777 << 16
                    bundle.writestr(info, "outside")
                self.assertNotEqual(self.run_tool("extract").returncode, 0)
                self.assertFalse((Path(self.directory.name) / "escape.txt").exists())


if __name__ == "__main__":
    unittest.main()
