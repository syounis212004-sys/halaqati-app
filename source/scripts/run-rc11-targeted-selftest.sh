#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/halaqati-rc11-targeted"
rm -rf "$OUT" && mkdir -p "$OUT"
echo '=== RC11 targeted compile (max 2 min) ==='
timeout 120s kotlinc \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/quran/QuranMetadata.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/quran/QuranRangeEngine.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanTagRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SardAnchorResolver.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanPhaseEngine.kt" \
  "$ROOT/scripts/kotlin-selftest/SmartPlanPhaseEngineSelfTest.kt" \
  -d "$OUT/classes.jar"
echo '=== RC11 phase engine self-test (max 30s) ==='
timeout 30s kotlin -classpath "$OUT/classes.jar" SmartPlanPhaseEngineSelfTestKt
