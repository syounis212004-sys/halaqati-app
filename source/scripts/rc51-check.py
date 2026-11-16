#!/usr/bin/env python3
"""RC51 static gate: single-flight token refresh with cool-down; a failed refresh is a session error, not invalid data."""
import sys
from pathlib import Path
A = Path('app/src/main/java/com/mohammedsamir/halaqati')
api = (A / 'data/SupabaseApi.kt').read_text(encoding='utf-8')
repo = (A / 'data/HalaqatiRepository.kt').read_text(encoding='utf-8')
errors = []
for t in ('refreshLock', 'synchronized(refreshLock)', 'refreshBlockedUntil', 'fun currentAccessToken', 'staleAccessToken'):
    if t not in api: errors.append('SupabaseApi missing ' + t)
for t in ('api.refresh(staleToken)', 'val staleToken = api.currentAccessToken()', 'authStopped'):
    if t not in repo: errors.append('HalaqatiRepository missing ' + t)
if errors:
    print('RC51 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC51 gate passed')
