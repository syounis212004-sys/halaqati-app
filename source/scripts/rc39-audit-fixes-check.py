#!/usr/bin/env python3
"""RC39 static gate: no-content recitations store no safety value; the tathbit roll function is guarded in the recorded migration."""
import sys
from pathlib import Path
repo = Path('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text(encoding='utf-8')
mig = Path('migration/20261028_v2750_rc39_guard_tathbit_functions.sql').read_text(encoding='utf-8')
errors = []
if 'if (actualPages <= 0.0 && evaluation == "not_rated") JSONObject.NULL else safety' not in repo:
    errors.append('a recitation with no content must not store safety 0')
for tok in ("'admin','supervisor','teacher'", 'rc152_roll_tathbit_months_core', 'revoke all on function public.rc152_roll_tathbit_months_core()'):
    if tok not in mig:
        errors.append('migration record missing: ' + tok)
if errors:
    print('RC39 audit-fixes gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC39 audit-fixes gate passed')
