#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/halaqati-kotlin-selftests"
rm -rf "$OUT"
mkdir -p "$OUT"

# RC11: every compiler/test process is bounded. A stalled kotlinc/test can never
# consume the whole CI job; the failing entry point is printed before execution.
echo '=== compile pure Kotlin self-tests (max 5 min) ==='
timeout 300s kotlinc \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/ui/presentation/SyncPresentationRules.kt" \
  "$ROOT/scripts/kotlin-selftest/SyncPresentationRulesSelfTest.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/SafetyScoring.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/ImportRules.kt" \
  "$ROOT/offline-db/src/main/java/com/mohammedsamir/halaqati/data/StudentTrackRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/DeepLinkRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/AuthCallbackRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/SmartPlanAlertRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/HomeMessages.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/SessionDeleteGuard.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/ui/scope/HalaqaScopeRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/LocalAccountScopeRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/GoogleAuthRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/ConflictRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/RuntimeStabilityRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/quran/QuranMetadata.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/quran/QuranRangeEngine.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/quran/RecitationParityRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanTagRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanHealthRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanCoreState.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanActivityRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/PlanRangeRebaseRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/PlanHistoryRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/OrgAttentionRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/RecitationGapRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/HifzSpanRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEligibility.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/WorkModeRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/OrgInsightRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/RecitationQualityRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/data/BackupRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/ui/theme/HalaqatiPalette.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SardAnchorResolver.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanPhaseEngine.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanUiRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/ui/sessions/SessionWorkflowRules.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DailyOperationStateEngine.kt" \
  "$ROOT/app/src/main/java/com/mohammedsamir/halaqati/documents/RecitationMatrix.kt" \
  "$ROOT/scripts/kotlin-selftest/RecitationMatrixSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/SafetyScoringSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/QuranMetadataSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/QuranRangeEngineSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/RecitationParityRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/SmartPlanTagRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/SmartPlanHealthRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/SmartPlanEngineSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/SmartPlanCoreStateSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/SmartPlanActivityRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/SmartPlanPhaseEngineSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/ImportRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/StudentTrackRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/DeepLinkRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/AuthCallbackRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/SmartPlanAlertRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/HomeMessagesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/HalaqaScopeRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/PlanRangeRebaseRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/PlanHistoryRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/OrgAttentionRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/SpanCompletionSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/RecitationGapRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/HifzSpanRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/WorkModeRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/BackupRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/OrgInsightRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/HalaqatiPaletteSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/SessionDeleteGuardSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/LocalAccountScopeRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/GoogleAuthRulesSelfTest.kt" \
  "$ROOT/scripts/kotlin-selftest/ConflictRulesSelfTest.kt" \
  "$ROOT/scripts/selftests/RC93RulesSelfTest.kt" \
  "$ROOT/scripts/selftests/RC93Fix3RulesSelfTest.kt" \
  "$ROOT/scripts/selftests/DailyOperationStateEngineSelfTest.kt" \
  -d "$OUT/classes.jar"

run_test() {
  local main="$1"
  echo "=== $main (max 30s) ==="
  timeout 30s kotlin -classpath "$OUT/classes.jar" "$main"
}

run_test RecitationMatrixSelfTestKt
run_test SafetyScoringSelfTestKt
run_test QuranMetadataSelfTestKt
run_test QuranRangeEngineSelfTestKt
run_test RecitationParityRulesSelfTestKt
run_test SmartPlanTagRulesSelfTestKt
run_test SmartPlanHealthRulesSelfTestKt
run_test SmartPlanEngineSelfTestKt
run_test SmartPlanCoreStateSelfTestKt
run_test SmartPlanActivityRulesSelfTestKt
run_test SmartPlanPhaseEngineSelfTestKt
run_test ImportRulesSelfTestKt
run_test StudentTrackRulesSelfTestKt
run_test DeepLinkRulesSelfTestKt
run_test AuthCallbackRulesSelfTestKt
run_test SmartPlanAlertRulesSelfTestKt
run_test HomeMessagesSelfTestKt
run_test SessionDeleteGuardSelfTestKt
run_test HalaqaScopeRulesSelfTestKt
run_test PlanRangeRebaseRulesSelfTestKt
run_test PlanHistoryRulesSelfTestKt
run_test OrgAttentionRulesSelfTestKt
run_test SpanCompletionSelfTestKt
run_test RecitationGapRulesSelfTestKt
run_test HifzSpanRulesSelfTestKt
run_test WorkModeRulesSelfTestKt
run_test BackupRulesSelfTestKt
run_test OrgInsightRulesSelfTestKt
run_test HalaqatiPaletteSelfTestKt
run_test LocalAccountScopeRulesSelfTestKt
run_test GoogleAuthRulesSelfTestKt
run_test ConflictRulesSelfTestKt
run_test RC93RulesSelfTestKt
run_test RC93Fix3RulesSelfTestKt
run_test DailyOperationStateEngineSelfTestKt

run_test SyncPresentationRulesSelfTestKt

echo 'All pure Kotlin self-tests PASS'
