#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

def need(path, *parts):
    text = (ROOT / path).read_text(encoding='utf-8')
    missing = [p for p in parts if p not in text]
    if missing:
        raise SystemExit(f"RC8.5 gate failed in {path}: missing {missing}")

need('app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanActivityRules.kt',
     'fun enabledActivities', 'fun latestAnchor', 'fun nextLocation',
     'reverse_surahs_forward_ayahs', 'fun activityMatches')
need('app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt',
     'enabledActivities', 'sardTargetPages', 'sardRouteDirection', 'enabled_activities')
need('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt',
     'activitySnapshotsForPlan', 'activityAssignmentFromAnchor', 'PlanActivitySnapshot',
     'sard_target_pages', 'enabled_activities', 'fun cancelSession', 'fun archiveSession', 'fun deleteSession',
     'cache.get(owner, CACHE_PLAN_RECITATIONS, maxAge = null)')
need('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt',
     'أنشطة الخطة', 'هدف السرد خلال الخطة بالصفحات', 'تحديد بداية ${state.label}',
     'خيارات الجلسة', 'Modifier.size(HalaqatiUiTokens.TouchTarget)',
     'Column(Modifier.weight(1f))')
need('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/QuranPicker.kt',
     'val juzSurahs = remember(selectedJuzFilter)', 'surahsForJuz(selectedJuzFilter)')
need('scripts/run-kotlin-selftests.sh', 'SmartPlanActivityRulesSelfTestKt')
print('RC8.5 Smart Plan multi-activity/performance/UX gate PASS')
