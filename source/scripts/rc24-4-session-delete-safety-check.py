import sys
from pathlib import Path
root = Path(__file__).resolve().parents[1]
k = root/'app/src/main/java/com/mohammedsamir/halaqati/data'
errors = []
repo = (k/'HalaqatiRepository.kt').read_text(encoding='utf-8')
for tok in ('SessionDeleteGuard.sessionIdFromPath', 'serverSessionHasData', 'entityTable == "sessions"'):
    if tok not in repo: errors.append('repository missing '+tok)
if not (k/'SessionDeleteGuard.kt').exists(): errors.append('SessionDeleteGuard.kt missing')
sql = (root/'migration/20261012_v2604_rc24_4_session_delete_snapshot.sql').read_text(encoding='utf-8')
for tok in ('deleted_session_snapshots', 'before delete on public.sessions', 'enable row level security', 'is_admin()'):
    if tok not in sql: errors.append('sql missing '+tok)
if not (root/'RELEASE_2_60_4_RC24_4_SESSION_DELETE_SAFETY_AR.md').exists(): errors.append('release note missing')
if errors:
    print('\n'.join(errors)); sys.exit(1)
print('RC24.4 session delete safety check passed')
