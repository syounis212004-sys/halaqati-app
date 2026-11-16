#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p): return (ROOT/p).read_text(encoding='utf-8')
errors=[]
def need(cond,msg):
    if not cond: errors.append(msg)
core=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
picker=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/QuranPicker.kt')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
models=read('app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt')
rank=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
engine=read('app/src/main/java/com/mohammedsamir/halaqati/quran/QuranRangeEngine.kt')
rules=read('app/src/main/java/com/mohammedsamir/halaqati/quran/RecitationParityRules.kt')
# 2.44 selector parity (RC8.3 keeps the four behaviors with compact one-line labels)
for text in ['"آية"','"صفحات"','"سور"','"أجزاء"','RangeSlider(','selectedJuzFilter','surahsForJuz','يمكن اختيار سور غير متجاورة','يمكن اختيار جزء أو أكثر']:
    need(text in picker, f'missing 2.44 Quran picker behavior: {text}')
# exact QCF pages and persisted range metadata
need('qcfLineKeys.distinct().size.toDouble() / 15.0' in rules, 'QCF4 15-line page calculation missing')
need('RecitationParityRules.effectivePages' in models, 'recitation rows must prefer QCF line keys')
need('selection_data' in repo and 'qcf_line_keys' in repo, 'repository must read QCF selection data')
need('start_surah,start_ayah,end_surah,end_ayah' in repo, 'plan/session recitations must fetch exact ayah endpoints')
need('endpointsForLineKeys' in engine, 'smart-plan assignment must resolve QCF lines back to Quran endpoints')
# session parity
for text in ['SessionSmartPlanCard','المقرر الذكي اليوم','آخر تسميع','غدًا/القادم','RecitationCounter','نوع التسميع']:
    need(text in core, f'missing session/recitation parity: {text}')
need('RecitationParityRules.quranRangeLabel' in core, 'session must show exact surah/ayah ranges')
# ranking parity: day/month plus separated program/activity. RC8.4 renders these as compact dropdowns.
for text in ['var periodMode','"day" to "يوم محدد"','"month" to "شهر محدد"',
             'var programTrack','"general_hifz" to "الحفظ العام"','"tathbit" to "التثبيت"',
             'var rankingTrack','"hifz" to "الحفظ"','"review" to "المراجعة"','"sard" to "السرد"']:
    need(text in rank, f'missing 2.44 ranking filter behavior: {text}')
need('activityMatchesRanking' in repo and 'canonicalProgramTrack' in repo, 'ranking must not mix activities/program tracks')
need('periodDays' not in rank[rank.find('fun RankingsScreen'):rank.find('private fun activityTrackAr')], 'legacy 7/14/30 ranking window still present')
if errors:
    print('RC8.2 recitation parity gate FAILED')
    for e in errors: print(' -', e)
    raise SystemExit(1)
print('RC8.2 recitation parity gate PASS')
