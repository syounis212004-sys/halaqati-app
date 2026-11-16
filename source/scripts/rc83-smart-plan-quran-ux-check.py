#!/usr/bin/env python3
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
def read(path): return (ROOT / path).read_text(encoding='utf-8')
errors=[]
def need(cond,msg):
    if not cond: errors.append(msg)
core=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
picker=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/QuranPicker.kt')
rules=read('app/src/main/java/com/mohammedsamir/halaqati/quran/RecitationParityRules.kt')
engine=read('app/src/main/java/com/mohammedsamir/halaqati/quran/QuranRangeEngine.kt')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
management=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt')
reports=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
# Quran-first display everywhere relevant
for text in ['fun smartWeekRangeText(','smartRecitationRangeText(last)','planAssignmentText(next.assignment)','rangeLineKeys = selected.qcfLineKeys','اختيار النطاق من المصحف']:
    need(text in core, f'missing Smart Plan/recitation Quran-first behavior: {text}')
need('RecitationParityRules.quranRangeLabel(nullableInt(row, "start_surah")' in core, 'student recent recitations must prefer exact surah/ayah fields')
need('memorizationLineKeys = rangeLineKeys' in core, 'Smart Plan editor must persist QCF4 line keys chosen by the Quran picker')
need('initialStartSurah = rangeStartSurah' in core and 'initialEndAyah = rangeEndAyah' in core, 'Smart Plan picker must reopen exact ayah endpoints')
# Compact Students filters, matching v2.44 mobile 2-column intent
need('CompactFilterSelect("الحالة"' in core and 'CompactFilterSelect("المسار"' in core, 'compact student status/track filters missing')
need('"الحلقة", halaqaFilter' in core and 'CompactFilterSelect("الفئة"' in core, 'compact student halaqa/category filters missing')
# Picker UX: compact tabs + exact ayah selection inside page mode
for text in ['QuranModeTab(mode == "ayahs", "آية"','QuranModeTab(mode == "pages", "صفحات"','QuranModeTab(mode == "surahs", "سور"','QuranModeTab(mode == "juz", "أجزاء"','pageAyahs(page: Int)','آية البداية في الصفحة','آية النهاية في الصفحة','QuranSelection.Ayahs(a, b) to "pages"']:
    need(text in picker, f'missing RC8.3 Quran picker UX: {text}')
need('return@ProFilterChip' not in picker, 'unsafe labeled return remains in Quran picker')
need('outlinedButtonBorder' not in picker, 'unstable Material button border API remains in Quran picker')
# Arabic range language must not use ambiguous LTR arrows in the Quran/plan UI
for name,text in [('CoreScreens',core),('QuranPicker',picker),('RecitationParityRules',rules),('QuranRangeEngine',engine)]:
    need('→' not in text, f'{name} still contains a left-to-right Quran range arrow')
# Milestone is separate; next duty should describe what follows approval
need('events + SmartPlanEngine.Event(type = "milestone_approved", milestoneKey = milestone.key)' in repo, 'next duty does not simulate milestone approval to expose following Quran assignment')
# Guardian/tools must not regress to page-number range UI
need('RecitationParityRules.pageRangeQuranLabel(start, end)' in management, 'guardian plan still uses page-number range language')
need('الصفحات ${selected.startPage}' not in reports, 'Quran calculator still exposes start/end page-number range as primary label')
if errors:
    print('RC8.3 Smart Plan + Quran UX gate FAILED')
    for e in errors: print(' -',e)
    raise SystemExit(1)
print('RC8.3 Smart Plan + Quran UX gate PASS')
