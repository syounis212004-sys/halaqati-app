#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p): return (ROOT/p).read_text(encoding='utf-8')
errors=[]
picker=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/QuranPicker.kt')
core=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
engine=read('app/src/main/java/com/mohammedsamir/halaqati/quran/QuranRangeEngine.kt')
gradle=read('app/build.gradle.kts')
gen=read('scripts/generate-qcf4-layout.py')

def need(cond,msg):
    if not cond: errors.append(msg)
need('onConfirm: (QuranRangeResult) -> Unit' in picker, 'picker must return QuranRangeResult')
need('QuranCore.engine().calculate' in picker, 'picker must use QuranCore engine')
for mode in ['mode == \"ayahs\"','mode == \"pages\"','mode == \"surahs\"','mode == \"juz\"']:
    need(mode in picker, f'missing picker mode {mode}')
need('selected.exactPages' in core, 'recitation dialog must use exactPages')
need('effectiveQcfLineKeys = selected.qcfLineKeys' in core and 'qcfLineKeys = authoritativeQuranRange?.qcfLineKeys ?: effectiveQcfLineKeys' in core, 'recitation must persist QCF line keys')
need('startSurah = authoritativeQuranRange?.start?.surah' in core and 'endAyah = authoritativeQuranRange?.end?.ayah' in core, 'recitation must persist ayah endpoints')
need('traversalDirection = authoritativeQuranRange?.resolvedTraversal?.name' in core, 'recitation must persist traversal direction')
need('val authoritativeQuranRange = selectedQuranRange ?: manualPageRange ?: runCatching' in core, 'manual page entry must be resolved through QuranRangeEngine before save')
need('QuranSelection.Pages(startPage, endPage)' in core, 'manual page save must use the central page range engine')
need('qcfLineKeys = authoritativeQuranRange?.qcfLineKeys' in core, 'manual page save must persist engine QCF lines')
need('minOf(a.startPage, a.endPage)' not in core and 'maxOf(a.startPage, a.endPage)' not in core, 'UI must not flip stored traversal for display')
for field in ['start_surah','start_ayah','end_surah','end_ayah','ayahs_count','surahs_count','selected_surahs']:
    need(f'"{field}"' in repo, f'repository missing {field}')
need('quran_traversal_direction' in repo, 'repository missing traversal selection metadata')
need('quran_segments' in repo, 'repository missing segment metadata')
need('line_model' in repo, 'repository missing line model metadata')
need('distinct().size.toDouble() / 15.0' in repo, 'repository must derive pages from unique QCF lines')
need('5130511027e769f0a8f4eeb7f00f46bde3788d60' in gen, 'QCF source commit not pinned')
need('568f4820cefe142ccd3f75b89fa99b1a2ad01fe9' in gen, 'QCF blob SHA not pinned')
need('HALAQATI_ALLOW_QCF4_FETCH' in gen and 'network regeneration disabled' in gen, 'standard RC8 build must fail fast instead of fetching QCF data when bundled asset is invalid')
need('dependsOn(prepareQcf4Layout)' in gradle, 'preBuild must prepare QCF asset')
need('REVERSE_WITHIN_SURAH' in engine and 'REVERSE_SURAH_ORDER' in engine, 'direction engine incomplete')
if errors:
    print('RC8 integration gate FAILED')
    for e in errors: print(' -',e)
    raise SystemExit(1)
print('RC8 integration gate PASS')
