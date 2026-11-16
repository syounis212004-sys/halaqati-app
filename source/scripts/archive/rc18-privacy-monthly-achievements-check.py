from pathlib import Path
import re

root=Path(__file__).resolve().parents[1]
repo=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text(encoding='utf-8')
vm=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/guardian/GuardianHomeViewModel.kt').read_text(encoding='utf-8')
parity=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/GuardianParityScreens.kt').read_text(encoding='utf-8')
core=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt').read_text(encoding='utf-8')
management=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt').read_text(encoding='utf-8')
mig=(root/'migration/20261002_v2540_rc18_privacy_monthly_achievements.sql').read_text(encoding='utf-8')
build=(root/'app/build.gradle.kts').read_text(encoding='utf-8')
update=(root/'scripts/make-update-publish.py').read_text(encoding='utf-8')

checks={
    'version 2.54.2':'versionCode = 25402' in build and 'versionName = "2.54.2"' in build,
    'guardian home uses authoritative children':'repo.guardianChildren()' in vm and 'repo.students()' not in vm,
    'guardian parity uses authoritative children':'guardianChildren(forceRefresh = force)' in parity,
    'guardian child repository RPC':'fun guardianChildren(' in repo and 'api.rpc("guardian_children"' in repo,
    'guardian fallback is link-filtered':'guardian_id=eq.$owner' in repo and 'id=in.(${ids.joinToString(",")})' in repo,
    'guardian overview locally guarded':'guardianOwnsCachedChild(studentId)' in repo,
    'guardian plan screen filters by authoritative ids':'loadedSnapshots.filter { it.student.id in allowed }' in core,
    'guardian plan header compacted':'خطط أبنائي الذكية' not in core[core.index('fun PlansScreen'):core.index('private fun PlansManagerSheet')],
    'monthly rollover action exposed':'إنهاء الشهر السابق وبدء خطة الشهر' in core and 'rollMonthlySmartPlans' in core,
    'monthly archive UI exposed':'أرشيف الشهور' in core and 'PlanMonthHistorySheet' in core,
    'real badge UI':'شارات الإنجاز الموثقة' in parity and 'studentAchievementBadges' in parity,
    'halaqa edit/delete controls':'تعديل الحلقة' in management and 'حذف الحلقة نهائيًا' in management,
    'category edit/delete controls':'تعديل الفئة' in management and 'حذف الفئة' in management and 'editingCategoryId' in management,
    'announcement edit/delete controls':'تعديل الإعلان' in management and 'حذف الإعلان' in management,
    'migration guardian function':'create or replace function public.guardian_children()' in mig,
    'migration restrictive student policy':'rc18_guardian_students_privacy' in mig and 'as restrictive' in mig,
    'migration monthly archive':'create table if not exists public.smart_plan_month_archives' in mig,
    'migration monthly rollover':'create or replace function public.rc18_start_month' in mig,
    'migration badge engine':'create table if not exists public.student_badge_awards' in mig and 'rc18_refresh_student_badges' in mig,
    'migration audit trail':'create table if not exists public.management_audit_log' in mig,
    'update manifest version':"'versionName':'2.54.2'" in update and "'versionCode':25402" in update,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL'), k)
if failed: raise SystemExit('RC18 source check failed: '+', '.join(failed))
print('RC18 privacy/monthly/achievements source gate: PASS')
