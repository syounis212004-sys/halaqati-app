#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
reports=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt').read_text(encoding='utf-8')
workflow=Path('/mnt/data/halaqati_work/Halaqati_v2_56_0_RC20_UI_DOCUMENT_CONSISTENCY.yml').read_text(encoding='utf-8')
checks={
 'date_field_imported': 'import com.mohammedsamir.halaqati.ui.components.HalaqatiDateField' in reports,
 'visual_check_not_before_playwright_install': workflow.find("python3 -m pip install --disable-pip-version-check --no-cache-dir 'pillow==12.3.0' 'playwright==1.63.0' 'pypdf==6.19.0'") < workflow.find("python3 scripts/rc20-report-layout-visual-check.py") if 'python3 scripts/rc20-report-layout-visual-check.py' in workflow else True,
}
failed=[k for k,v in checks.items() if not v]
print(checks)
if failed:
    raise SystemExit('RC20 BUILD FIX 1 regression failed: '+', '.join(failed))
print('RC20 BUILD FIX 1 regression PASS')
