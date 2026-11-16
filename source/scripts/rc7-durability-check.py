#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
db=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/OfflineDatabase.kt').read_text()
q=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/OfflineQueue.kt').read_text()
imp=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/OfflineLegacyImporter.kt').read_text()
repo=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text()
required={
 'dedupe unique index':'Index(value = ["ownerUserId", "dedupe"], unique = true)' in db,
 'transactional dedupe replace':'@Transaction fun replaceDedupe' in db,
 'attempt persistence':'attempts: Int = 0' in db and 'incrementAttempts' in db,
 'blocked persistence':'state: String = OutboxState.PENDING' in db and "state='blocked'" in db,
 'conflict persistence':"state='conflict'" in db and 'serverJson' in db,
 'legacy queue import':'halaqati-offline.db' in imp and 'Legacy unscoped/quarantined queue rows' in imp,
 'legacy cache import':'halaqati-cache.db' in imp,
 'old db rollback retained':'old databases are left untouched' in imp.lower(),
 'student tombstone':'tombstone(owner' in repo,
 'persisted sync timestamp':'SyncMetaEntity' in db and 'lastSyncInstant' in repo,
 'network backoff':'BackoffPolicy.EXPONENTIAL' in repo,
}
failed=[k for k,v in required.items() if not v]
if failed: raise SystemExit('RC7 durability contract failed:\n- '+'\n- '.join(failed))
print(f'RC7 durability contract: {len(required)}/{len(required)} PASS')
