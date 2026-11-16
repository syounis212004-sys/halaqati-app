#!/usr/bin/env python3
"""RC31 static gate: unsent changes must be visible, retried periodically, and never block behind one stuck item."""
import sys
from pathlib import Path

errors = []
rd = lambda p: Path(p).read_text(encoding='utf-8')
base = 'app/src/main/java/com/mohammedsamir/halaqati/'

repo = rd(base + 'data/HalaqatiRepository.kt')
if 'fun oldestPendingMinutes' not in repo:
    errors.append('oldestPendingMinutes missing')
if 'status in 500..599' not in repo:
    errors.append('flushQueue must continue past a 5xx/408 item instead of stopping the whole queue')
if 'oldestPendingAt' not in rd(base + 'data/OfflineDatabase.kt') or 'oldestPendingAt' not in rd(base + 'data/OfflineQueue.kt'):
    errors.append('oldestPendingAt query missing')
graph = rd(base + 'data/AppGraph.kt')
if 'halaqati-outbox-guard' not in graph or 'PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)' not in graph:
    errors.append('15-minute outbox guard worker missing')
if 'stalePendingMinutes' not in rd(base + 'model/Models.kt') or 'stalePendingMinutes' not in rd(base + 'ui/components/Components.kt') or 'stalePendingMinutes' not in rd(base + 'ui/HalaqatiApp.kt'):
    errors.append('stale-pending banner not wired')
card = rd(base + 'ui/screens/SettingsParitySections.kt')
if 'Dispatchers.IO' not in card[card.index('fun SupportSettingsCard'):] or 'oldestPendingMinutes' not in card:
    errors.append('problem report must include sync diagnostics read off the main thread')

if errors:
    print('RC31 sync-visibility gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC31 sync-visibility gate passed')
