#!/usr/bin/env python3
from pathlib import Path
import sqlite3

root = Path(__file__).resolve().parents[1]
queue = (root / 'app/src/main/java/com/mohammedsamir/halaqati/data/OfflineQueue.kt').read_text(encoding='utf-8')
db_src = (root / 'app/src/main/java/com/mohammedsamir/halaqati/data/OfflineDatabase.kt').read_text(encoding='utf-8')
repo = (root / 'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text(encoding='utf-8')
app_vm = (root / 'app/src/main/java/com/mohammedsamir/halaqati/ui/AppViewModel.kt').read_text(encoding='utf-8')
all_kotlin = '\n'.join(p.read_text(encoding='utf-8') for p in (root / 'app/src/main/java').rglob('*.kt'))

required_db = [
    'tableName = "outbox"',
    'ownerUserId: String',
    'Index(value = ["ownerUserId", "dedupe"], unique = true)',
    'Index(value = ["ownerUserId", "state", "id"])',
    'WHERE ownerUserId=:owner AND state=\'pending\'',
    'WHERE ownerUserId=:owner ORDER BY CASE state',
]
for token in required_db:
    assert token in db_src, f'missing RC7 Room account-scope contract: {token}'

for token in [
    'require(ownerUserId.isNotBlank())',
    'db.outbox().replaceDedupe(',
    'db.outbox().pending(ownerUserId, limit)',
    'db.outbox().snapshot(ownerUserId, limit)',
]:
    assert token in queue, f'missing RC7 Outbox wrapper contract: {token}'

required_repo = [
    'private fun queueOwnerId()',
    'queue.add(queueOwnerId(), method, path, obj?.toString(), dedupe, baseUpdatedAt, entityTable, entityId)',
    'fun pendingQueueSize()',
    'fun blockedQueueSize()',
    'fun queueSnapshot(limit: Int = 200)',
    'val owner = currentUserId() ?: return',
    'queue.all(owner)',
    'queue.done(item.id, owner)',
    'queue.markBlocked(item.id, owner,',
]
for token in required_repo:
    assert token in repo, f'missing repository account-scope contract: {token}'

# RC9.3 FIX3 extends fail() with a persisted transient error while preserving owner scoping.
assert 'queue.fail(item.id, owner' in repo, 'missing repository account-scoped queue.fail contract'

for token in [
    'fun openOrCreateTodaySession(',
    'fun saveAttendanceLocalFirst(',
    'fun saveDailyNoteLocalFirst(',
    'fun saveRecitationLocalFirst(',
    'dedupe = "session-day:${SessionWorkflowRules.dailySessionKey(halaqaId, date, type)}"',
]:
    assert token in repo, f'missing local-first critical session contract: {token}'

assert 'pending = repo.pendingQueueSize()' in app_vm
assert 'blocked = repo.blockedQueueSize()' in app_vm

for p in (root / 'app/src/main/java/com/mohammedsamir/halaqati/ui').rglob('*.kt'):
    text = p.read_text(encoding='utf-8')
    assert 'repo.queue.' not in text, f'raw queue access bypasses account scope in {p.relative_to(root)}'

for bad in ['queue.add(method, path', 'queue.all()', 'queue.snapshot()', 'queue.size()', 'queue.blockedSize()']:
    assert bad not in all_kotlin, f'unscoped offline queue call returned: {bad}'

# Model the composite uniqueness Room enforces: the same dedupe key is legal for different accounts,
# but never twice for the same account.
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE outbox(id INTEGER PRIMARY KEY, ownerUserId TEXT NOT NULL, dedupe TEXT NOT NULL, state TEXT NOT NULL);
CREATE UNIQUE INDEX outbox_owner_dedupe ON outbox(ownerUserId,dedupe);
INSERT INTO outbox(ownerUserId,dedupe,state) VALUES('user-a','same-key','pending');
INSERT INTO outbox(ownerUserId,dedupe,state) VALUES('user-b','same-key','pending');
''')
assert db.execute("select count(*) from outbox where ownerUserId='user-a'").fetchone()[0] == 1
assert db.execute("select count(*) from outbox where ownerUserId='user-b'").fetchone()[0] == 1
try:
    db.execute("insert into outbox(ownerUserId,dedupe,state) values('user-a','same-key','pending')")
    raise AssertionError('duplicate same-account dedupe was accepted')
except sqlite3.IntegrityError:
    pass

print('Offline account-scope check passed (RC7 Room Outbox, per-user replay isolation)')
