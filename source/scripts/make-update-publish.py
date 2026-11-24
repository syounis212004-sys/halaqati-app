from pathlib import Path
import json, os, shutil, hashlib
apk=Path('app/build/outputs/apk/release/app-release.apk')
out=Path('update-publish');out.mkdir(exist_ok=True)
target=out/'Halaqati-latest.apk'
shutil.copy2(apk,target)
sha=hashlib.sha256(apk.read_bytes()).hexdigest()
manifest={
    'versionName':'2.91.8',
    'versionCode':29108,
    'apkUrl':'https://syounis212004-sys.github.io/halaqati-web/Halaqati-latest.apk',
    'sha256':sha,
    'native':True,
    # RC30: a forced update locks EVERY installed copy out until it updates, so it is a per-release choice made in the
    # workflow (input "mandatory"). Default is optional; use mandatory only for releases that fix wrong data or security.
    'mandatory':os.environ.get('HALAQATI_MANDATORY','false').strip().lower()=='true',
    'notes':[
        "تحسين المصحف التفاعلي: يتبع الثيم المختار، صفحة بكامل المساحة، شريط أدوات جانبي وأيقونات جديدة للخطأ والتلقين",
        "تحميل أنعم بتأثير Shimmer، وأشرطة تقدم تنزلق إلى القيمة الجديدة",
        "تقييم V7: الصفحة التي تتجاوز الحد (خطأ واحد وتلقينان) تُحتسب إعادة، ويطابق التطبيق الخادم والموقع",
    ]
}
(out/'version.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print('Update package prepared:', manifest['versionName'], manifest['versionCode'])
