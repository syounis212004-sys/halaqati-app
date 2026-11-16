#!/usr/bin/env python3
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def read(p): return (ROOT/p).read_text(encoding='utf-8')
errors=[]
def need(ok,msg):
    if not ok: errors.append(msg)
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
api=read('app/src/main/java/com/mohammedsamir/halaqati/data/SupabaseApi.kt')
graph=read('app/src/main/java/com/mohammedsamir/halaqati/data/AppGraph.kt')
core=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
need('return=minimal' in api and 'Fire-and-forget mutation used by the durable Outbox' in api, 'Outbox mutations are still downloading representations')
need('fun fullRefreshNow()' in repo and 'fun syncNow()' in repo and 'backgroundRefreshDue' in repo, 'foreground/background sync split or throttling missing')
need('compactLegacySessionDeletes' in repo and 'ON DELETE CASCADE' in repo, 'legacy four-operation session deletes are not compacted')
need('delete("sessions", "id=eq.$sessionId"' in repo and 'session-recitation-delete:' not in repo[repo.find('fun deleteSession'):repo.find('private fun warmRecentSessionDetails')], 'new session delete is not single-request cascade path')
need('if (AppGraph.repo.pendingQueueSize() > 0) AppGraph.repo.syncNow()' in graph, 'empty Outbox still wakes foreground sync worker')
need('AppGraph.connectivity.requestImmediateSync()' in repo, 'new local writes do not kick immediate foreground upload')
need('allowNetwork = false' in core and 'refreshSessionDetails(session.id)' in core, 'roster is not cache-first/background-refresh')
need('loadRosterLocal(includePlans = false)' in core and 'launch { loadRosterLocal(includePlans = true) }' in core, 'Smart Plan calculation still blocks first roster frame')
need('SessionChoiceMenu' in core and all(x in core for x in ['غائب بعذر','يحتاج انتباه','تم التسميع']), 'compact attendance/behavior roster UX missing')
need('recitationSheetState = rememberModalBottomSheetState' in core and 'sheetState = recitationSheetState' in core, 'recitation editor is not bottom-sheet based')
need('تقييم V6 تلقائي' in core and 'سلامة ${plainNumber(rec.safetyPercentage' in core, 'recitation evaluation/safety summary missing')
need('scope.launch { reload(force = false) }' in core, 'Smart Plan save still forces blocking network reload')
if errors:
    print('RC8.6 V3 performance/session UX gate FAILED')
    for e in errors: print(' -',e)
    raise SystemExit(1)
print('RC8.6 V3 performance/session UX gate PASS')
