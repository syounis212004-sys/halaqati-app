"""Verify offline build isolation without launching Gradle or contacting Firebase."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class GradleWrapperTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.root = Path(self.directory.name) / "repo"
        tool = self.root / "tools/codex"
        tool.mkdir(parents=True)
        self.project = self.root / "project"
        (self.project / "app").mkdir(parents=True)
        self.config = self.project / "app/google-services.json"
        self.script = tool / "gradle.py"
        for name in ["gradle.py", "firebase-offline-test.json"]:
            shutil.copy2(Path(__file__).with_name(name), tool / name)
        (tool / "native-source.py").write_text(f"print({str(self.project)!r})\n")
        self.tools = Path(self.directory.name) / "toolchain"
        binary = self.tools / "gradle-8.13/bin/gradle"
        binary.parent.mkdir(parents=True)
        binary.write_text("#!/usr/bin/env python3\nfrom pathlib import Path\nimport sys\nassert Path('app/google-services.json').exists()\nsys.exit(7)\n")
        binary.chmod(0o755)

    def tearDown(self):
        self.directory.cleanup()

    def run_wrapper(self, *args):
        env = {key: value for key, value in os.environ.items() if not key.startswith("HALAQATI_FIREBASE_")}
        env.update(HALAQATI_TOOLS=str(self.tools), HALAQATI_OFFLINE_ANDROID_TEST="1")
        return subprocess.run(["python3", str(self.script), *args], env=env, capture_output=True, text=True)

    def test_synthetic_config_removed_even_when_build_fails(self):
        result = self.run_wrapper(":app:testDebugUnitTest")
        self.assertEqual(result.returncode, 7)
        self.assertIn("OFFLINE TEST MODE", result.stdout)
        self.assertFalse(self.config.exists())

    def test_offline_mode_rejects_release_task(self):
        result = self.run_wrapper(":app:assembleRelease")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("restricted", result.stderr)
        self.assertFalse(self.config.exists())

    def test_existing_configuration_is_preserved_after_failure(self):
        original = '{"existing":"local binding"}'
        self.config.write_text(original)
        self.assertEqual(self.run_wrapper(":app:lintDebug").returncode, 7)
        self.assertEqual(self.config.read_text(), original)


if __name__ == "__main__":
    unittest.main()
