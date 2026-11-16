from pathlib import Path
root=Path(__file__).resolve().parents[1]
repo=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text()
mgmt=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt').read_text()
core=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt').read_text()
parity=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/GuardianParityScreens.kt').read_text()
build=(root/'app/build.gradle.kts').read_text()
publish=(root/'scripts/make-update-publish.py').read_text()
mig=(root/'migration/20261002_v2540_rc18_privacy_monthly_achievements.sql').read_text()

def need(c,m):
    if not c: raise AssertionError(m)
need('versionCode = 25402' in build and 'versionName = "2.54.2"' in build,'version contract')
# Keep the confirmed v2.53 management fixes.
need('fun writeConfirmed(' in repo and 'fun patchConfirmed(' in repo and 'fun deleteConfirmed(' in repo,'confirmed writes')
need('AppGraph.repo.writeConfirmed("halaqas", obj)' in mgmt,'halaqa confirmed create')
need('AppGraph.repo.writeConfirmed("announcements", obj)' in mgmt,'announcement confirmed create')
need('announcementSaving' in mgmt and 'halaqaSaving' in mgmt,'duplicate submit guards')
# RC18 privacy, monthly lifecycle, archive and badge engine.
need('fun guardianChildren(' in repo and 'guardian_children' in repo,'guardian child api')
need('loadedSnapshots.filter { it.student.id in allowed }' in core,'guardian plan filtering')
need('إنهاء الشهر السابق وبدء خطة الشهر' in core and 'rollMonthlySmartPlans' in core,'month rollover UI')
need('PlanMonthHistorySheet' in core and 'أرشيف الشهور' in core,'month archive UI')
need('شارات الإنجاز الموثقة' in parity and 'studentAchievementBadges' in parity,'real badge UI')
need('rc18_guardian_students_privacy' in mig and 'as restrictive' in mig,'restrictive RLS')
need('smart_plan_month_archives' in mig and 'rc18_start_month' in mig,'month server engine')
need('student_badge_awards' in mig and 'rc18_refresh_student_badges' in mig,'badge server engine')
need('management_audit_log' in mig,'audit log')
need("'versionName':'2.54.2'" in publish and "'versionCode':25402" in publish,'update manifest')
print('v2.54.2 RC18 release gate PASS')
