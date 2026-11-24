from pathlib import Path
import json, os, shutil, hashlib
apk=Path('app/build/outputs/apk/release/app-release.apk')
out=Path('update-publish');out.mkdir(exist_ok=True)
target=out/'Halaqati-latest.apk'
shutil.copy2(apk,target)
sha=hashlib.sha256(apk.read_bytes()).hexdigest()
manifest={
    'versionName':'2.91.9',
    'versionCode':29109,
    'apkUrl':'https://syounis212004-sys.github.io/halaqati-web/Halaqati-latest.apk',
    'sha256':sha,
    'native':True,
    # RC30: a forced update locks EVERY installed copy out until it updates, so it is a per-release choice made in the
    # workflow (input "mandatory"). Default is optional; use mandatory only for releases that fix wrong data or security.
    'mandatory':os.environ.get('HALAQATI_MANDATORY','false').strip().lower()=='true',
    'notes':[
        "شريط أدوات المصحف الجديد: أزرار مربعة مستديرة بأسماء عربية، يُطوى ويُوسَّع بالسحب أو الضغط، وفي الوضع الأفقي يصير شريطًا جانبيًا",
        "صفحة المصحف تحافظ على أبعادها المطبوعة (14:20) وتستفيد من المساحة أعلى الشريط وأسفله دون تمديد",
        "أيام السرد: خيارات جاهزة (الأحد–الجمعة / أيام الحلقة / الجمعة فقط) مع تنبيه فوري إذا وقع الهدف كله على يوم واحد، وتعديل أيام السرد للمحددين دفعة واحدة دون إنشاء مراحل جديدة",
    ]
}
(out/'version.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print('Update package prepared:', manifest['versionName'], manifest['versionCode'])
