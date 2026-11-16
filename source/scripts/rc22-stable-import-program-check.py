from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
build=(root/'app/build.gradle.kts').read_text(encoding='utf-8')
version_code_match=re.search(r'versionCode\s*=\s*(\d+)', build)
version_name_match=re.search(r'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"', build)
version_code=int(version_code_match.group(1)) if version_code_match else 0
version_name=tuple(map(int, version_name_match.groups())) if version_name_match else (0,0,0)
assert version_code >= 25900 and version_name >= (2,59,0), f'app/build.gradle.kts: expected v2.59+, got code={version_code}, name={version_name}'
checks={
'migration/20261005_v2580_rc22_program_metadata_import_ux.sql':['program_specialization','program_level','assessment_type','affects_primary_plan'],
'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt':['program_specialization','program_level','assessment_type','affects_primary_plan','تأهيل محفظين','علاج ضعف القراءة'],
'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt':['displayName(context, uri)','zipEntries','name.endsWith(".xlsx")','verticalScroll(rememberScrollState())'],
'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt':['program_specialization','tajweed_applied','intensive_hifz'],
}
count=0
for rel, needles in checks.items():
    text=(root/rel).read_text(encoding='utf-8')
    for n in needles:
        assert n in text, f'{rel}: missing {n}'
        count+=1
print(f'RC22 source gate: {count}/{count} PASS')
