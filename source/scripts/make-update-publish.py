from pathlib import Path
import json, os, shutil, hashlib, re

config = Path('app/build.gradle.kts').read_text(encoding='utf-8')
names = re.findall(r'^\s*versionName\s*=\s*"([^"\n]+)"\s*$', config, re.M)
codes = re.findall(r'^\s*versionCode\s*=\s*([0-9]+)\s*$', config, re.M)
if len(names) != 1 or len(codes) != 1:
    raise SystemExit('Expected exactly one literal versionName and versionCode in app/build.gradle.kts')
apk=Path('app/build/outputs/apk/release/app-release.apk')
out=Path('update-publish');out.mkdir(exist_ok=True)
target=out/'Halaqati-latest.apk'
shutil.copy2(apk,target)
sha=hashlib.sha256(apk.read_bytes()).hexdigest()
manifest={
    'versionName':names[0],
    'versionCode':int(codes[0]),
    'apkUrl':'https://syounis212004-sys.github.io/halaqati-web/Halaqati-latest.apk',
    'sha256':sha,
    'native':True,
    # RC30: a forced update locks EVERY installed copy out until it updates, so it is a per-release choice made in the
    # workflow (input "mandatory"). Default is optional; use mandatory only for releases that fix wrong data or security.
    'mandatory':os.environ.get('HALAQATI_MANDATORY','false').strip().lower()=='true',
    'notes':[
        "تحسين إدارة الجلسات والطلاب والبرامج والتقارير.",
        "حماية التقييمات والمزامنة من فقدان التعديلات المعلقة."
    ]
}
(out/'version.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print('Update package prepared:', manifest['versionName'], manifest['versionCode'])
