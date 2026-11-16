from pathlib import Path
root=Path(__file__).resolve().parents[1]
mg=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt').read_text()
core=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt').read_text()
build=(root/'app/build.gradle.kts').read_text()
repo=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text()
errors=[]
def need(ok,msg):
    if not ok: errors.append(msg)
need('versionCode = 25402' in build and 'versionName = "2.54.2"' in build,'v2.54.2 version contract')
need('Icons.Default.MoreVert' in mg and 'contentDescription = "إجراءات الحلقة"' in mg,'halaqa always-visible actions menu')
for token in ['تعديل الحلقة','تعيين الطاقم','حذف الحلقة','إيقاف الحلقة','تفعيل الحلقة']:
    need(token in mg,f'missing halaqa action: {token}')
need('إدارة دورة الشهر' in core,'monthly management card is not visible at top of plans')
need('confirmRollMonth' in core and 'بدء دورة شهر جديدة' in core,'monthly rollover confirmation dialog')
need(core.count('إنهاء الشهر السابق وبدء خطة الشهر') >= 1,'monthly rollover action label')
need('rollMonthlySmartPlans()' in core and 'rc182_start_month' in repo,'monthly rollover wiring')
need('fun guardianChildren' in repo and 'هذا الطالب غير مرتبط بحساب ولي الأمر' in repo,'guardian privacy guard')
if errors:
    raise SystemExit('v2.54.2 visibility gate FAILED:\n- ' + '\n- '.join(errors))
print('v2.54.2 RC18.1 visibility gate PASS')
