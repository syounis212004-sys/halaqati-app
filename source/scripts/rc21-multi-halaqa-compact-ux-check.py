#!/usr/bin/env python3
from pathlib import Path
import re
root=Path(__file__).resolve().parents[1]
def read(rel):
    p=root/rel
    return p.read_text('utf-8',errors='ignore') if p.exists() else ''
core=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
mgmt=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
session=read('app/src/main/java/com/mohammedsamir/halaqati/ui/sessions/SessionWorkflowRules.kt')
build=read('app/build.gradle.kts')
mig=read('migration/20261004_v2570_rc21_multi_halaqa_compact_sessions.sql')
version_code_match = re.search(r'versionCode\s*=\s*(\d+)', build)
version_name_match = re.search(r'versionName\s*=\s*"(\d+)\.(\d+)\.(\d+)"', build)
version_code = int(version_code_match.group(1)) if version_code_match else 0
version_name = tuple(map(int, version_name_match.groups())) if version_name_match else (0, 0, 0)
checks={
  'v2.57+': version_code >= 25700 and version_name >= (2, 57, 0),
  'membership schema': all(x in mig for x in ['student_halaqa_memberships','halaqa_type','rc21_secondary_student_access','students_secondary_membership_select']),
  'repo membership roster': all(x in repo for x in ['studentsForHalaqa','saveStudentSecondaryMemberships','setStudentHalaqaMembership']),
  'student secondary memberships': 'الحلقات والبرامج الإضافية' in core,
  'student compact accordion': all(x in core for x in ['فتح تفاصيل الطالب','طي تفاصيل الطالب','فلاتر الطلاب']),
  'session compact controls': all(x in core for x in ['خيارات الجلسة','حالة الجلسة والفلترة']),
  'independent course recitation': 'مستقلة عن تقدم خطة الحفظ الأساسية' in core and ('"tajweed", "tilawah" -> "tilawah"' in core or 'program_specialization' in core),
  'manual tilawah session': '"tilawah"' in session,
  'halaqa program type': all(x in mgmt for x in ['نوع الحلقة / البرنامج','إدارة المشاركين في البرنامج','Mشاركو'.replace('M','م')]) if False else all(x in mgmt for x in ['نوع الحلقة / البرنامج','إدارة المشاركين في البرنامج','مشاركو ${halaqa.optString("name", "الحلقة")}']),
  'management compose imports': 'import androidx.compose.foundation.BorderStroke' in mgmt and 'import androidx.compose.foundation.shape.RoundedCornerShape' in mgmt,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'),k)
if failed:
    raise SystemExit('RC21 FAIL: '+', '.join(failed))
print('RC21 Multi-Halaqa + Compact UX gate PASS:',len(checks),'checks')
