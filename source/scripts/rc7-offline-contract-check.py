#!/usr/bin/env python3
from pathlib import Path
root = Path(__file__).resolve().parents[1]

def read(rel): return (root/rel).read_text()

db = read('offline-db/src/main/java/com/mohammedsamir/halaqati/data/OfflineDatabase.kt')
repo = read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
graph = read('app/src/main/java/com/mohammedsamir/halaqati/data/AppGraph.kt')
ui = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
sync = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SyncCenterScreen.kt')
build = read('app/build.gradle.kts')
checks = {
    'Room 2.8.5 runtime': 'androidx.room:room-runtime:2.8.5' in build,
    'single Room db': 'class HalaqatiOfflineDatabase' in db and 'halaqati-rc7.db' in db,
    'account-scoped cache': 'primaryKeys = ["ownerUserId", "cacheKey"]' in db,
    'durable outbox': 'tableName = "outbox"' in db and 'const val CONFLICT = "conflict"' in db,
    'typed students': 'tableName = "students_local"' in db and 'remoteUpdatedAt' in db,
    'local-first students': 'offlineDb.students().all(owner)' in repo and 'upsertStudentLocal(owner, current, dirty = true)' in repo,
    'WorkManager connected': 'NetworkType.CONNECTED' in graph and 'SyncWorker' in graph,
    'unique sync KEEP': 'ExistingWorkPolicy.KEEP' in repo,
    'conflict choices': 'resolveConflictUseLocal' in repo and 'resolveConflictUseServer' in repo and 'اعتماد تعديلي' in sync,
    'pull to refresh': 'PullToRefreshBox' in ui,
    'named dropdowns': 'StudentNamedDropdown' in ui and 'label = "الحلقة"' in ui and 'label = "الفئة"' in ui,
    'date picker': 'DatePickerDialog' in ui and 'StudentDateField' in ui,
    'no raw uuid editor labels': 'معرّف الحلقة' not in ui and 'معرّف الفئة' not in ui,
}
failed=[k for k,v in checks.items() if not v]
if failed: raise SystemExit('RC7 offline contract failed:\n- '+'\n- '.join(failed))
print(f'RC7 offline contract: {len(checks)}/{len(checks)} PASS')
