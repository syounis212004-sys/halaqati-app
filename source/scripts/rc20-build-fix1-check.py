#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
reports=(root/"app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt").read_text(encoding="utf-8")
adaptive=(root/"app/src/main/java/com/mohammedsamir/halaqati/ui/components/AdaptiveControls.kt").read_text(encoding="utf-8")
checks={
    "date_field_component_exists": "fun HalaqatiDateField(" in adaptive,
    "reports_import_date_field": "import com.mohammedsamir.halaqati.ui.components.HalaqatiDateField" in reports,
    "reports_use_date_field": reports.count("HalaqatiDateField(") >= 2,
}
failed=[k for k,v in checks.items() if not v]
print(checks)
if failed:
    raise SystemExit("RC20 BUILD FIX 1 gate failed: "+", ".join(failed))
print("RC20 BUILD FIX 1 gate PASS")
