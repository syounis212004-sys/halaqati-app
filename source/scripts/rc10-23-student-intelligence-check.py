from pathlib import Path
import sys
checks=[]
errors=[]

def req(path, needles, label):
    s=Path(path).read_text()
    for n in needles:
        if n not in s:
            errors.append(f'{label}: missing {n!r}')

repo='app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt'
core='app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt'
profile='app/src/main/java/com/mohammedsamir/halaqati/ui/screens/StudentProfileScreen.kt'
app='app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt'
dash='app/src/main/java/com/mohammedsamir/halaqati/ui/screens/DashboardScreen.kt'
follow='app/src/main/java/com/mohammedsamir/halaqati/ui/screens/StudentFollowUpScreen.kt'
schema='app/src/main/java/com/mohammedsamir/halaqati/data/StudentSchemaContract.kt'
guardian_rules='app/src/main/java/com/mohammedsamir/halaqati/data/StudentGuardianRules.kt'

a=Path(schema).read_text()
if 'guardian_name' in a.split('writableColumns')[1].split(')')[0]: errors.append('students schema contract must not write guardian_name')
req(repo, ['repairLegacyStudentSchemaQueue', 'StudentSchemaContract.sanitize', 'pendingPrimaryGuardianId', '/rest/v1/rpc/set_student_primary_guardian', 'dedupe = "student-guardian:$studentId"'], 'repository')
req(core, ['GuardianAutocompleteField(', 'HalaqatiLazySelectField(', 'searchable = true', 'يمكن ربط ولي الأمر نفسه بأكثر من طالب', 'openStudentProfile(student.id)'], 'student UI')
# BUILD FIX 6: the guardian picker moved from the eager legacy autocomplete to the shared
# LazyColumn-backed selector in BUILD FIX 5. Keep validating the normalized guardian filtering
# rules themselves without requiring the UI to call the old eager filter implementation.
req(guardian_rules, ['fun matches(', 'fun filter('], 'guardian search rules')
req(profile, ['fun StudentProfileScreen(', 'الخطة الشهرية', 'هذا الأسبوع', 'آخر 4 أسابيع', 'جودة التسميع', 'الحضور', 'لماذا يحتاج متابعة؟', 'ملاحظة متابعة الشهر', 'صحة البيانات'], 'student profile')
req(app, ['route = "student/{studentId}"', 'StudentProfileScreen(', 'navigate("student/$id")', 'composable("follow_up")', 'StudentFollowUpScreen('], 'navigation')
req(dash, ['navigate("student:${followItem.studentId}")', 'FollowUpRules.group', 'navigate("follow_up")'], 'dashboard follow-up')
req(follow, ['fun StudentFollowUpScreen(', 'FollowUpRules.group', 'ابحث باسم الطالب أو سبب المتابعة', 'navigate("student:${row.studentId}")'], 'follow-up screen')

# Production payload builders must not re-introduce the removed column.
for p in [repo, core, 'app/src/main/java/com/mohammedsamir/halaqati/data/OfflineDatabase.kt']:
    s=Path(p).read_text()
    if '.put("guardian_name"' in s:
        errors.append(f'{p}: writes unsupported guardian_name')

if errors:
    print('RC10.2/10.3 Student Intelligence gate FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RC10.2/10.3 Student Intelligence gate passed')
