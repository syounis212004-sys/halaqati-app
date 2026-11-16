#!/usr/bin/env python3
"""RC25 static gate: halaqa scope, month grouping, self-healing, restore flow, SQL."""
from pathlib import Path
import re, sys
root = Path(__file__).resolve().parents[1]
errors = []
def read(rel):
    p = root / rel
    if not p.exists():
        errors.append(f'missing {rel}'); return ''
    return p.read_text(encoding='utf-8', errors='ignore')
def need(cond, msg):
    if not cond: errors.append(msg)

base = 'app/src/main/java/com/mohammedsamir/halaqati/'
rules = read(base + 'ui/scope/HalaqaScopeRules.kt')
for t in ('object MonthGrouping', 'defaultExpanded', 'object HalaqaScopeRules', 'object HalaqaOverview', 'fun resolve('):
    need(t in rules, 'scope rules missing ' + t)
core = read(base + 'ui/screens/CoreScreens.kt')
for t in ('HalaqaScopeBar(', 'MonthSectionHeader(', 'HalaqaOverviewCard(', 'PlanHistoryLine(', 'AppGraph.halaqaScope', 'DeletedSessionsDialog('):
    need(t in core, 'CoreScreens missing ' + t)
need('lateinit var halaqaScope' in read(base + 'data/AppGraph.kt'), 'AppGraph.halaqaScope missing')
need('halaqati_scope_v1' in read(base + 'data/HalaqaScopeStore.kt'), 'scope store missing')
app = read(base + 'ui/HalaqatiApp.kt')
need(app.count('role=profile.role.name.lowercase()') >= 3, 'router must pass role to sessions/students')
repo = read(base + 'data/HalaqatiRepository.kt')
need('recreateMissingParentSession' in repo and '"23503"' in repo, 'self-healing missing')
need('deletedSessionSnapshots' in repo and 'rc25_restore_deleted_session' in repo, 'restore repo calls missing')
need(('AdminHalaqaTodayCard' in read(base + 'ui/screens/InsightsScreen.kt')) or ('FollowUpBoard' in read(base + 'ui/screens/InsightsScreen.kt')), 'admin insights card not wired (RC42: cards live on the insights page)')
need('activity in setOf("hifz", "review", "sard") && configuredTerminal != null' in repo, 'daily-duty fallback past stale range end missing')
sql = read('migration/20261012_v2610_rc25_scale_and_rls_performance.sql')
for t in ('(select auth.uid())', 'notifications_session_id_idx', 'rc25_restore_deleted_session', 'security definer'):
    need(t in sql, 'SQL missing ' + t)
need(not re.search(r'(?im)^\s*(drop\s+table|truncate)\b', sql), 'SQL must not drop tables/truncate')
need('rc25-halaqa-scope' in read('scripts/run-kotlin-selftests.sh').lower() or 'HalaqaScopeRulesSelfTest' in read('scripts/run-kotlin-selftests.sh'), 'self-test not registered')
need(read('RELEASE_2_61_0_RC25_HALAQA_SCOPE_AR.md') != '', 'release note missing')
need(read('RELEASE_2_62_0_RC26_PLAN_RANGE_AR.md') != '', 'RC26 release note missing')
if errors:
    print('\n'.join('FAIL: ' + e for e in errors)); sys.exit(1)
print('RC25 halaqa scope check passed')
