#!/usr/bin/env python3
"""RC27.18 gate: deleting/cancelling/archiving a session must drop every cached read model built from it."""
import sys
from pathlib import Path
root = Path(__file__).resolve().parents[1]
t = (root / 'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text(encoding='utf-8')
errors = []
for tok in ('SESSION_DERIVED_TABLES', 'private fun invalidateSessionDerivedCaches(', 'Duration.ofSeconds(90)'):
    if tok not in t: errors.append(f'missing {tok}')
for fn in ('fun deleteSession', 'fun cancelSession', 'fun setSessionArchived', 'fun updateSessionDetails', 'fun restoreDeletedSession'):
    i = t.find(fn)
    if i < 0: errors.append(f'{fn} not found'); continue
    j = t.find('\n    fun ', i + 10)
    if 'invalidateSessionDerivedCaches()' not in t[i:j if j > 0 else len(t)]: errors.append(f'{fn} does not invalidate derived caches')
rep = (root / 'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt').read_text(encoding='utf-8')
if 'sessionCachesDirty' not in t: errors.append('offline cache must not be dropped (RC27.23)')
if 'method.equals("DELETE", ignoreCase = true)' not in t: errors.append('ordinary saves must not drop derived caches (RC27.24)')
if 'val memberIds = if (halaqaId == "all")' not in t: errors.append('reportSummary ignores program participants from other halaqat')
if 'studentsForHalaqa(halaqaId)' not in rep or 'bundle.memberHalaqas' not in rep: errors.append('report screen ignores program participants')
if errors:
    print('RC27.18 stale cache gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC27.18 stale cache gate passed')
