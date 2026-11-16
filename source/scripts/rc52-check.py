#!/usr/bin/env python3
"""RC52 static gate: reward flow, threads, donor signup type, guardian today card, month picker, sliding tabs."""
import sys
from pathlib import Path
A = Path('app/src/main/java/com/mohammedsamir/halaqati')
def rd(p): return (A / p).read_text(encoding='utf-8')
errors = []
rf = rd('ui/screens/RewardFlow.kt')
for t in ('RewardTracker', 'RewardRequestDialog', 'ThreadSheet', 'AdminRewardsPanel', 'DonorRewardsTab', 'rewardRequest', 'rewardDecide'):
    if t not in rf and t not in rd('data/HalaqatiRepository.kt'): errors.append('missing ' + t)
repo = rd('data/HalaqatiRepository.kt')
for t in ('rc52_reward_request', 'rc52_thread_post', 'rc52_thread_get', 'guardAssignmentAfterLastRecitation', 'attachGuardianWard', 'last_day'):
    if t not in repo: errors.append('repo missing ' + t)
auth = rd('ui/screens/AuthScreens.kt')
if auth.count('"observer" to "مانح"') < 2: errors.append('signup donor type missing (both screens)')
seg = rd('ui/components/SegmentedTabs.kt')
for t in ('animateDpAsState', 'TabContentTransition'):
    if t not in seg: errors.append('segmented motion missing ' + t)
if 'MonthPicker' not in rd('ui/screens/DonorScreen.kt'): errors.append('month picker not used in donor awards')
if 'TodayDetailGrid' not in rd('ui/guardian/GuardianHomeScreen.kt'): errors.append('guardian today card missing')
if 'MONTH_PAGES_STEPS' not in rd('plans/BadgeRules.kt'): errors.append('monthly badges missing')
if errors:
    print('RC52 gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC52 gate passed')
