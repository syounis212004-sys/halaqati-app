from pathlib import Path

root = Path(__file__).resolve().parents[1]
models = (root / "app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt").read_text()
sync = (root / "app/src/main/java/com/mohammedsamir/halaqati/data/SyncErrorRules.kt").read_text()
repo = (root / "app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt").read_text()

for field in ("student_id", "category_id", "parent_plan_id"):
    assert f'optNullableString("{field}")' in models, f"PlanRow must null-normalize {field}"

assert 'listOf("student_id", "category_id", "parent_plan_id", "created_by")' in sync
assert 'text.equals("null", true)' in sync
assert 'text.equals("undefined", true)' in sync
assert 'genericId ?: id' in repo, "generic PATCH entityId must be preserved"
assert 'private fun repairLegacySmartPlanUuidQueue(owner: String)' in repo
assert 'repairLegacySmartPlanUuidQueue(owner)' in repo
assert 'SyncErrorRules.sanitizeSmartPlan(raw)' in repo
assert 'dedupe = item.dedupe' in repo
print("RC17.1 smart-plan UUID-null regression check PASS")
