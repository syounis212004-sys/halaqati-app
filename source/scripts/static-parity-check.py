import subprocess, sys
from pathlib import Path
required=[
'app/src/main/java/com/mohammedsamir/halaqati/MainActivity.kt',
'app/src/main/java/com/mohammedsamir/halaqati/data/SupabaseApi.kt',
'app/src/main/java/com/mohammedsamir/halaqati/data/OfflineQueue.kt',
'app/src/main/java/com/mohammedsamir/halaqati/data/ImportRules.kt',
'app/src/main/java/com/mohammedsamir/halaqati/data/StudentTrackRules.kt',
'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt',
'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt',
'migration/source-contract.json','migration/parity-matrix.md']
missing=[x for x in required if not Path(x).exists()]
assert not missing,missing
runtime_files=[p for p in Path('app/src/main').rglob('*') if p.is_file()]
alltext='\n'.join(p.read_text(errors='ignore') for p in runtime_files)
assert 'Capacitor' not in alltext
for p in runtime_files:
    txt=p.read_text(errors='ignore')
    if 'android.webkit.WebView' in txt:
        assert '/documents/' in p.as_posix(), f'WebView outside document renderer: {p}'
assert 'GenericDataScreen' not in alltext
build=Path('app/build.gradle.kts').read_text()
assert 'com.mohammedsamir.halaqati' in build
import re
assert re.search(r'versionCode = \d+', build)
assert re.search(r'versionName = "\d+\.\d+\.\d+"', build)
assert 'compileSdk = 36' in build and 'targetSdk = 36' in build and 'minSdk = 23' in build
print('Native structure/parity smoke check passed')

subprocess.run([sys.executable, 'scripts/parity-matrix-check.py', '--allow-incomplete'], check=True)
