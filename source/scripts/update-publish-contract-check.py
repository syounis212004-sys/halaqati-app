#!/usr/bin/env python3
from pathlib import Path
import tempfile, subprocess, sys, json, hashlib, os

script = Path(__file__).with_name('make-update-publish.py').resolve()
with tempfile.TemporaryDirectory() as td:
    root = Path(td)
    apk = root/'app/build/outputs/apk/release/app-release.apk'
    apk.parent.mkdir(parents=True)
    apk.write_bytes(b'test APK content')
    config = root/'app/build.gradle.kts'
    config.write_text('    versionCode = 29120\n    versionName = "2.91.19"\n')
    subprocess.run([sys.executable, str(script)], cwd=root, env={**os.environ, 'HALAQATI_MANDATORY':'false'}, check=True)
    data = json.loads((root/'update-publish/version.json').read_text())
    assert (data['versionName'], data['versionCode']) == ('2.91.19', 29120), data
    assert data['sha256'] == hashlib.sha256(apk.read_bytes()).hexdigest()
    assert data['mandatory'] is False
    assert (root/'update-publish/Halaqati-latest.apk').read_bytes() == apk.read_bytes()
    config.write_text('versionName = "2.91.19"\nversionName = "2.91.20"\nversionCode = 29120\n')
    assert subprocess.run([sys.executable, str(script)], cwd=root, capture_output=True).returncode != 0
print('Manifest regression checks passed')
