#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]

def read(rel):
    return (root / rel).read_text(encoding='utf-8')

def require(cond, message):
    if not cond:
        print(f'FAIL: {message}')
        sys.exit(1)

adaptive = read('app/src/main/java/com/mohammedsamir/halaqati/ui/components/AdaptiveControls.kt')
quran = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/QuranPicker.kt')
core = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
phases = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SmartPlanPhasesUi.kt')
models = read('app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt')
repo = read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
policy = read('app/src/main/java/com/mohammedsamir/halaqati/data/StudentTrackRules.kt')
auth = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/AuthScreens.kt')
guardian = read('app/src/main/java/com/mohammedsamir/halaqati/ui/guardian/GuardianUiState.kt')
components = read('app/src/main/java/com/mohammedsamir/halaqati/ui/components/Components.kt')

require('LazyColumn' in adaptive and 'HalaqatiLazySelectField' in adaptive, 'lazy selector component missing')
require('HalaqatiLazySelectField' in quran, 'Quran picker is not using lazy selector')
require('ExposedDropdownMenu' not in quran, 'Quran picker still eagerly composes exposed dropdown menus')
require('ResponsiveChipGrid' in core and 'ResponsiveChipGrid' in phases, 'responsive chip grids missing from plan editors')
require('maxLines = 1' in components and 'TextOverflow.Ellipsis' in components, 'filter chips are not protected from Arabic wrapping')
require('تعديل بيانات الخطة' in core and 'إدارة مراحل الحفظ والسرد' in core, 'plan metadata and phase-management actions are not distinct')
require('tilawahAssessment: JSONObject?' in models, 'tilawah assessment model missing')
require('tilawah_assessment' in repo and 'overall_score' in repo and 'TILAWAH_V1' in repo, 'tilawah assessment persistence/scoring missing')
require('لحن جلي' in core and 'لحن خفي' in core and 'الوقف والابتداء' in core and 'المخارج والصفات' in core, 'manual tilawah rubric incomplete')
require('if (activityType == "tilawah") return null' in core, 'tilawah may still inherit smart-plan assignment')
require('setOf(TATHBIT, "tilawah", "test")' in policy, 'tilawah is not available to tathbit students')
require('"tilawah" -> "تلاوة"' in guardian, 'guardian still groups tilawah as review')
require('Designed by : Dr. Mohammed Samir Abu Younis' in auth, 'brand attribution is incorrect')
require('صُنع بأيدٍ شرقية' in auth and 'الشرقية — خانيونس' in auth, 'eastern Khan Younis login identity missing')
require((root / 'app/src/main/res/drawable-nodpi/brand_eastern_khan_younis.webp').exists(), 'eastern identity asset missing')
require((root / 'app/src/main/res/drawable-nodpi/launcher_legacy.png').exists(), 'launcher brand asset missing')

print('RC17.1 BUILD FIX 5 UX/TILAWAH/PERFORMANCE GATE: PASS')
