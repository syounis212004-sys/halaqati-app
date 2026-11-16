#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
vm=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/AppViewModel.kt').read_text()
conn=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/ConnectivitySyncCoordinator.kt').read_text()
act=(root/'app/src/main/java/com/mohammedsamir/halaqati/MainActivity.kt').read_text()
checks={
 'refreshSync moves Room reads to IO':'viewModelScope.launch(Dispatchers.IO)' in vm and 'pending = repo.pendingQueueSize()' in vm,
 'syncEpoch does not query Room on Main':'refreshSync(lastSync = repo.lastSyncInstant())' not in vm,
 'startup sync waits for foreground':'foregroundReady' in conn and 'if (!nowOnline || !foregroundReady.get()) return' in conn,
 'Application init no immediate heavy sync':'if (_online.value) requestImmediateSync()' not in conn,
 'Activity arms sync after resume':'AppGraph.connectivity.onForegroundReady()' in act,
 'Activity disarms foreground sync on stop':'AppGraph.connectivity.onForegroundStopped()' in act,
}
failed=[k for k,v in checks.items() if not v]
for k,v in checks.items(): print(('PASS' if v else 'FAIL')+': '+k)
if failed: raise SystemExit(1)
print(f'RC8.6 startup crash regression: {len(checks)}/{len(checks)} PASS')
