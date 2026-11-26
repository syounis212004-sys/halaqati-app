from pathlib import Path
import json, os, shutil, hashlib
apk=Path('app/build/outputs/apk/release/app-release.apk')
out=Path('update-publish');out.mkdir(exist_ok=True)
target=out/'Halaqati-latest.apk'
shutil.copy2(apk,target)
sha=hashlib.sha256(apk.read_bytes()).hexdigest()
manifest={
    'versionName':'2.91.14',
    'versionCode':29115,
    'apkUrl':'https://syounis212004-sys.github.io/halaqati-web/Halaqati-latest.apk',
    'sha256':sha,
    'native':True,
    # RC30: a forced update locks EVERY installed copy out until it updates, so it is a per-release choice made in the
    # workflow (input "mandatory"). Default is optional; use mandatory only for releases that fix wrong data or security.
    'mandatory':os.environ.get('HALAQATI_MANDATORY','false').strip().lower()=='true',
    'notes':[
        "صفحة «المساهمة»: ولي الأمر يتكفّل بحلقة أبنائه، والمحفظ والمشرف ومدير النظام ينشئون المسابقات",
        "تذكير أسبوعي من التطبيق نفسه (يوم الخميس 6 مساءً افتراضيًا): تغيّر اليوم والوقت أو تطفئه من صفحة المساهمة أو الإعدادات",
        "اختيار تاريخ المسابقة من التقويم، وقبول الكتابة بصيغة يوم-شهر-سنة",
        "المسابقات تتحدّث فور فتح الشاشة، وقائمة حلقات المانح تُقرأ من الخادم عند إنشاء المسابقة",
        "علامات التسميع والمعادلات دون تغيير"
    ]
}
(out/'version.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print('Update package prepared:', manifest['versionName'], manifest['versionCode'])
