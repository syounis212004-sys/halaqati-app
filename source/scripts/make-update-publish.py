from pathlib import Path
import json, os, shutil, hashlib
apk=Path('app/build/outputs/apk/release/app-release.apk')
out=Path('update-publish');out.mkdir(exist_ok=True)
target=out/'Halaqati-latest.apk'
shutil.copy2(apk,target)
sha=hashlib.sha256(apk.read_bytes()).hexdigest()
manifest={
    'versionName':'2.91.12',
    'versionCode':29113,
    'apkUrl':'https://syounis212004-sys.github.io/halaqati-web/Halaqati-latest.apk',
    'sha256':sha,
    'native':True,
    # RC30: a forced update locks EVERY installed copy out until it updates, so it is a per-release choice made in the
    # workflow (input "mandatory"). Default is optional; use mandatory only for releases that fix wrong data or security.
    'mandatory':os.environ.get('HALAQATI_MANDATORY','false').strip().lower()=='true',
    'notes':[
        "المصحف بدون إنترنت: من نافذة «بدون إنترنت» في المصحف تنزّل جزءًا أو عدة أجزاء أو المصحف كاملًا (نحو ٢٠٠ م.ب) مرة واحدة على Wi-Fi، وتُفتح صفحاتها بلا اتصال",
        "إذا انقطع الإنترنت تُفتح الصفحة المحمّلة سابقًا بدل رسالة الخطأ، ويمكن حذف المصحف المحمّل من النافذة نفسها",
        "علامات التسميع والمعادلات وقاعدة البيانات دون تغيير"
    ]
}
(out/'version.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print('Update package prepared:', manifest['versionName'], manifest['versionCode'])
