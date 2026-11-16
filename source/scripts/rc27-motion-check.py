#!/usr/bin/env python3
"""Static gate for the Halaqati motion identity (RC27.12+): Lottie files, runtime recolor names, wiring, dependency."""
import json, re, sys
from pathlib import Path
root = Path(__file__).resolve().parents[1]
errors = []
raw = root / 'app/src/main/res/raw'
for n in ('splash', 'loading', 'success', 'empty'):
    p = raw / f'halaqati_{n}.json'
    if not p.exists():
        errors.append(f'missing {p.name}'); continue
    try:
        d = json.loads(p.read_text(encoding='utf-8'))
    except Exception as e:
        errors.append(f'{p.name} invalid JSON: {e}'); continue
    for k in ('v', 'fr', 'ip', 'op', 'w', 'h', 'layers'):
        if k not in d: errors.append(f'{p.name} missing {k}')
    if d.get('op', 0) <= d.get('ip', 0): errors.append(f'{p.name} op <= ip')
    if not re.fullmatch(r'[a-z0-9_]+', p.stem): errors.append(f'{p.name} bad resource name')
    text = p.read_text(encoding='utf-8')
    if n == 'splash':
        if '"BrandLeaf"' not in text: errors.append('splash lost the logo leaf layer')
    elif '"Primary"' not in text: errors.append(f'{p.name} has no Primary recolor target')
    if n != 'splash' and '"Accent"' not in text: errors.append(f'{p.name} has no Accent recolor target')
    if n == 'success' and '"OnPrimary"' not in text: errors.append('success has no OnPrimary target')
res = root / 'app/src/main/res'
for rel in ('drawable/ic_launcher_foreground.xml', 'drawable/ic_launcher_background.xml', 'drawable/ic_launcher_monochrome.xml',
            'drawable/ic_stat_halaqati.xml', 'drawable-nodpi/launcher_legacy.png', 'mipmap-anydpi-v26/ic_launcher.xml',
            'mipmap-anydpi-v26/ic_launcher_round.xml'):
    if not (res / rel).exists(): errors.append(f'icon resource missing: {rel}')
for dpi in ('mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi'):
    for n in ('ic_launcher.png', 'ic_launcher_round.png'):
        if not (res / f'mipmap-{dpi}' / n).exists(): errors.append(f'legacy icon missing: mipmap-{dpi}/{n}')
if (res / 'mipmap-anydpi-v26/ic_launcher.xml').exists() and '<monochrome' not in (res / 'mipmap-anydpi-v26/ic_launcher.xml').read_text(encoding='utf-8'):
    errors.append('adaptive icon has no monochrome layer (themed icons)')
if (res / 'drawable/ic_launcher_foreground.xml').exists() and 'launcher_legacy' in (res / 'drawable/ic_launcher_foreground.xml').read_text(encoding='utf-8'):
    errors.append('adaptive foreground still wraps the old PNG')
for rel in ('drawable/ic_launcher_foreground.xml', 'drawable/ic_launcher_monochrome.xml', 'drawable/ic_stat_halaqati.xml'):
    f = res / rel
    if f.exists():
        import xml.dom.minidom
        try: xml.dom.minidom.parse(str(f))
        except Exception as e: errors.append(f'{rel} is not valid XML: {e}')
kt = (root / 'app/src/main/java/com/mohammedsamir/halaqati/ui/components/HalaqatiMotion.kt').read_text(encoding='utf-8')
for tok in ('R.raw.halaqati_splash', 'R.raw.halaqati_loading', 'R.raw.halaqati_success', 'R.raw.halaqati_empty',
            'LocalReducedMotion', 'delay(3200L)', 'derivedStateOf', 'HalaqatiMotionEvents'):
    if tok not in kt: errors.append(f'HalaqatiMotion.kt missing {tok}')
NEW = ('ambient', 'role_teacher', 'role_admin', 'role_supervisor', 'role_student', 'role_guardian', 'milestone', 'sync',
       'empty_people', 'empty_bell', 'empty_search', 'empty_doc', 'trophy', 'offline', 'welcome')
for n in NEW:
    p2 = raw / f'halaqati_{n}.json'
    if not p2.exists(): errors.append(f'missing {p2.name}'); continue
    try: d2 = json.loads(p2.read_text(encoding='utf-8'))
    except Exception as e: errors.append(f'{p2.name} invalid JSON: {e}'); continue
    if d2.get('op', 0) <= d2.get('ip', 0) or not d2.get('layers'): errors.append(f'{p2.name} malformed')
    tx = p2.read_text(encoding='utf-8')
    if '"Primary"' not in tx or '"Accent"' not in tx: errors.append(f'{p2.name} lacks recolor names')
    if '"Brand' in tx: errors.append(f'{p2.name} uses fixed brand colors (must follow the theme)')
    if f'R.raw.halaqati_{n}' not in kt: errors.append(f'HalaqatiMotion.kt does not reference halaqati_{n}')
for tok in ('HalaqatiRoleArt', 'HalaqatiAmbient', 'HalaqatiMoment', 'fun milestone', 'fun saved', 'fun welcome', 'onBand'):
    if tok not in kt: errors.append(f'HalaqatiMotion.kt missing {tok}')
ui = root / 'app/src/main/java/com/mohammedsamir/halaqati/ui'
kit = ui / 'components/HalaqatiMotionKit.kt'
if not kit.exists(): errors.append('HalaqatiMotionKit.kt missing')
else:
    ktx = kit.read_text(encoding='utf-8')
    for tok in ('fun Modifier.halaqatiEnter', 'fun animatedCountText', 'fun Modifier.halaqatiPressable', 'fun Modifier.halaqatiRing', 'fun Modifier.halaqatiSpin', 'LocalReducedMotion'):
        if tok not in ktx: errors.append(f'HalaqatiMotionKit.kt missing {tok}')
for rel, toks in {'components/Components.kt': ('GoldenNavIcon', 'animatedCountText', 'emptyAnimationFor', 'halaqatiEnter'),
                  'components/GoldenShell.kt': ('GoldenNavIcon', 'drawerGroupOf', 'DrawerTile', 'halaqatiPressable'), 'screens/AuthScreens.kt': ('HalaqatiRoleArt', 'HalaqatiAmbient'),
                  'screens/SyncCenterScreen.kt': ('HalaqatiAnimation.Sync',), 'screens/DashboardScreen.kt': ('role = p.role',)}.items():
    s = (ui / rel).read_text(encoding='utf-8')
    for tok in toks:
        if tok not in s: errors.append(f'{rel} not wired: {tok}')
if 'Surface(' in kt: errors.append('success host must not use Surface (it consumes touches)')
gr = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
if 'com.airbnb.android:lottie-compose:' not in gr: errors.append('lottie-compose dependency missing')
main = (root / 'app/src/main/java/com/mohammedsamir/halaqati/MainActivity.kt').read_text(encoding='utf-8')
for tok in ('HalaqatiLaunchSplash', 'HalaqatiSuccessHost'):
    if tok not in main: errors.append(f'MainActivity missing {tok}')
nav = ui / 'components/HalaqatiNavMotion.kt'
if not nav.exists(): errors.append('HalaqatiNavMotion.kt missing')
else:
    nt = nav.read_text(encoding='utf-8')
    for tok in ('navEnter', 'navExit', 'navPopEnter', 'navPopExit', 'SlideDirection.Up', 'SlideDirection.Down', 'reduced'):
        if tok not in nt: errors.append(f'HalaqatiNavMotion.kt missing {tok}')
app = (ui / 'HalaqatiApp.kt').read_text(encoding='utf-8')
for tok in ('navEnter(', 'navExit(', 'navPopEnter(', 'navPopExit(', 'HalaqatiMotionEvents.welcome()'):
    if tok not in app: errors.append(f'HalaqatiApp.kt missing {tok}')
if 'enableOnBackInvokedCallback' not in (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8'): errors.append('predictive back not enabled in manifest')
# RC27.20: insight cards share one shell; welcome is a slim top toast; the Eastern identity line is present.
kit = ui / 'components/HalaqatiInsightKit.kt'
if not kit.exists(): errors.append('HalaqatiInsightKit.kt missing')
else:
    kt = kit.read_text(encoding='utf-8')
    for tok in ('InsightCardShell', 'InsightMeter', 'rememberInfiniteTransition', 'LocalReducedMotion', 'animatedCountText'):
        if tok not in kt: errors.append(f'HalaqatiInsightKit.kt missing {tok}')
for name in ('OrgInsightCards', 'OrgAttentionCard', 'AdminHalaqaTodayCard', 'PlansNeedAttentionCard'):
    if 'InsightCardShell(' not in (ui / f'components/{name}.kt').read_text(encoding='utf-8'): errors.append(f'{name}.kt not using InsightCardShell')
hm = (ui / 'components/HalaqatiMotion.kt').read_text(encoding='utf-8')
if 'size(250.dp)' in hm or 'welcome -> Alignment.TopCenter' not in hm: errors.append('Welcome must be a top toast, not a 250dp centered card')
gh = (ui / 'guardian/GuardianHomeScreen.kt').read_text(encoding='utf-8')
if 'Modifier.align(Alignment.TopEnd).padding(14.dp)' in gh: errors.append('guardian role art must not overlap text')
for f in ('screens/AuthScreens.kt', 'HalaqatiApp.kt', 'components/GoldenShell.kt'):
    # RC40: the place line was removed; the "made by Eastern hands" line stays.
    if 'صُنع بأيدٍ شرقية' not in (ui / f).read_text(encoding='utf-8'): errors.append(f'{f} missing Eastern identity line')
for tok in ('أدوات متابعة القرآن الكريم', 'verticalScroll', 'Designed by : Dr. Mohammed Samir Abu Younis'):
    if tok not in (ui / 'screens/AuthScreens.kt').read_text(encoding='utf-8'): errors.append(f'AuthScreens.kt missing {tok}')
if 'Designed by : Dr. Mohammed Samir Abu Younis' not in hm: errors.append('launch splash missing credits')
if 'DrawerTile(' not in (ui / 'components/GoldenShell.kt').read_text(encoding='utf-8'): errors.append('drawer must be the icon-tile hub')
if 'CompareRow(' not in (ui / 'components/OrgInsightCards.kt').read_text(encoding='utf-8'): errors.append('halaqa comparison must be the ranked list')
if 'frozenRank' not in (ui / 'screens/CoreScreens.kt').read_text(encoding='utf-8'): errors.append('session roster order must stay frozen while marking')
if 'TAB_ROUTES' not in app: errors.append('non-tab pages must be pushed over the origin page (Back returns to it)')
gr = (ui / 'guardian/GuardianReportText.kt').read_text(encoding='utf-8')
for t in ('object GuardianReportText', 'fun build(', 'مشاركة تقرير'):
    if t not in gr + (ui / 'guardian/GuardianHomeScreen.kt').read_text(encoding='utf-8'): errors.append('guardian report share missing ' + t)
if 'ACTION_SEND' not in (ui / 'guardian/GuardianHomeScreen.kt').read_text(encoding='utf-8'): errors.append('guardian report must use the share intent')
if 'fun inactiveStudents(' not in (root / 'app/src/main/java/com/mohammedsamir/halaqati/plans/OrgInsightRules.kt').read_text(encoding='utf-8'): errors.append('inactive students rule missing')
if ('InactiveStudentsCard' not in (ui / 'screens/InsightsScreen.kt').read_text(encoding='utf-8')) and ('FollowUpBoard' not in (ui / 'screens/InsightsScreen.kt').read_text(encoding='utf-8')): errors.append('inactive students card must be on the dashboard')
if 'كل الحلقات' not in (ui / 'screens/ManagementScreens.kt').read_text(encoding='utf-8'): errors.append('announcement dialog must offer all halaqas')
if 'GuardianPraise' not in (ui / 'guardian/GuardianHomeScreen.kt').read_text(encoding='utf-8'): errors.append('guardian praise card missing')
if '0xFF000000' not in (ui / 'theme/HalaqatiPalette.kt').read_text(encoding='utf-8'): errors.append('dark theme background must be true black')
if 'ModalDrawerSheet' in app: errors.append('side menu must be the full-width page, not a capped sheet')
if 'isHalaqaType' not in (ui / 'components/OrgInsightCards.kt').read_text(encoding='utf-8'): errors.append('halaqa comparison must exclude programs')
if 'COMPARE_PERIODS' not in (ui / 'components/OrgInsightCards.kt').read_text(encoding='utf-8'): errors.append('comparison needs period options')
if 'byCategory' not in (ui / 'components/OrgInsightCards.kt').read_text(encoding='utf-8'): errors.append('comparison needs a category level')
if 'competition_seasons' not in (ui / 'components/SeasonsCard.kt').read_text(encoding='utf-8'): errors.append('seasons card missing')
if 'SeasonsCard' not in (ui / 'screens/InsightsScreen.kt').read_text(encoding='utf-8'): errors.append('seasons card must be on the dashboard')
if not (root / 'migration/20261019_v2632_rc27_32_competition_seasons.sql').exists(): errors.append('seasons migration missing')
if 'SeasonsCard' not in (ui / 'guardian/GuardianHomeScreen.kt').read_text(encoding='utf-8'): errors.append('guardians must see seasons')
if 'shareSeasonResult' not in (ui / 'components/SeasonsCard.kt').read_text(encoding='utf-8'): errors.append('season congratulation share missing')
if not (root / 'migration/20261019_v2633_rc27_33_season_notifications.sql').exists(): errors.append('season notification migration missing')
dr = (root / 'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text(encoding='utf-8')
if 'FULL_REFRESH_MIN_GAP_MS' not in dr or 'RANKING_CACHE_DAYS' not in dr: errors.append('data-usage throttles missing')
if 'TimeUnit.HOURS' not in (root / 'app/src/main/java/com/mohammedsamir/halaqati/data/AppGraph.kt').read_text(encoding='utf-8'): errors.append('full sync worker must not run every 15 minutes')
if 'ForcedUpdateGate' not in (root / 'app/src/main/java/com/mohammedsamir/halaqati/MainActivity.kt').read_text(encoding='utf-8'): errors.append('forced update gate must wrap the app')
fug = (ui / 'update/ForcedUpdateGate.kt').read_text(encoding='utf-8')
if 'BackHandler' not in fug or 'cachedBlocking' not in fug: errors.append('forced update must block Back and remember the block')
if "'mandatory'" not in (root / 'scripts/make-update-publish.py').read_text(encoding='utf-8'): errors.append('update manifest needs mandatory')
if 'verifyDownload' not in fug or 'openInBrowser' not in fug: errors.append('forced update needs file verification and a browser fallback')
if 'GuardianBadgesCard' not in (ui / 'guardian/GuardianHomeScreen.kt').read_text(encoding='utf-8'): errors.append('guardian badges card missing')
if 'object BadgeRules' not in (root / 'app/src/main/java/com/mohammedsamir/halaqati/plans/BadgeRules.kt').read_text(encoding='utf-8'): errors.append('badge rules missing')
# RC27.38/39: gate cleanup — archived stale gates, no LTR arrow in plan UI
import os
if not os.path.isdir('scripts/archive') or not os.path.exists('scripts/archive/README.md'):
    errors.append('RC27.38: scripts/archive missing')
if '→' in open('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt',encoding='utf-8').read():
    errors.append('RC27.38: LTR arrow in CoreScreens')
# RC27.38: tab taps must land on the tapped tab (no restoreState of stale stacks)
_nav=open('app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt',encoding='utf-8').read()
if 'restoreState = true' in _nav or 'saveState = true' in _nav:
    errors.append('RC27.38: tab navigation must not save/restore state')
# RC27.39/40: plan history + rankings must not serve a never-expiring cached copy
_repo=open('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt',encoding='utf-8').read()
for tok in ('planMemoryValid','planRecDeltaAt','rankingTailAt','minus(java.time.Duration.ofDays(3))'):
    if tok not in _repo: errors.append('RC27.40: missing '+tok)
# RC27.41: reads larger than 1000 rows must be paged (PostgREST max-rows cap)
_api=open('app/src/main/java/com/mohammedsamir/halaqati/data/SupabaseApi.kt',encoding='utf-8').read()
for tok in ('SELECT_PAGE','selectOnce','offset='):
    if tok not in _api: errors.append('RC27.41: SupabaseApi paging missing '+tok)
_base='app/src/main/java/com/mohammedsamir/halaqati/'
_auth=open(_base+'ui/screens/AuthScreens.kt',encoding='utf-8').read()
_vm=open(_base+'ui/AppViewModel.kt',encoding='utf-8').read()
_repo=open(_base+'data/HalaqatiRepository.kt',encoding='utf-8').read()
_app=open(_base+'ui/HalaqatiApp.kt',encoding='utf-8').read()
for tok in ('CompleteSignupScreen',): 
    if tok not in _auth: errors.append('RC27.42: missing '+tok)
if 'NeedsSetup' not in _vm: errors.append('RC27.42: AuthState.NeedsSetup missing')
for tok in ('rc_complete_signup','rc_request_guardian_link','rc_pending_requests','rc_review_request','rc_my_signup_status'):
    if tok not in _repo: errors.append('RC27.42: repo RPC missing '+tok)
if '"approvals"->ApprovalsScreen' not in _app: errors.append('RC27.42: approvals route missing')
import os as _os
if not _os.path.exists(_base+'ui/screens/ApprovalsScreen.kt'): errors.append('RC27.42: ApprovalsScreen missing')
if not _os.path.exists('migration/20261020_v2642_rc27_42_approval_hierarchy.sql'): errors.append('RC27.42: SQL migration missing')
_aut=open(_base+'ui/screens/AuthScreens.kt',encoding='utf-8').read()
if 'AddChildCard(retry = true)' not in _aut: errors.append('RC27.43: retry link card missing on pending screen')
if 'item.attempts >= 6' not in _repo: errors.append('RC27.43: poison outbox guard missing')
_ident=open(_base+'ui/screens/StudentIdentitySection.kt',encoding='utf-8').read() if _os.path.exists(_base+'ui/screens/StudentIdentitySection.kt') else ''
if 'student-identity' not in _ident: errors.append('RC27.44: StudentIdentitySection missing')
if 'StudentIdentitySection(' not in open(_base+'ui/screens/ApprovalsScreen.kt',encoding='utf-8').read(): errors.append('RC27.44: identity section not shown')
if 'WeeklySummaryCard(overview)' not in open(_base+'ui/guardian/GuardianHomeScreen.kt',encoding='utf-8').read(): errors.append('RC27.44: weekly summary card missing')
for _t in ('data/SyncErrorRulesClassifyTest.kt','ui/screens/NormalizeBirthDateTest.kt','ui/guardian/GuardianWeeklySummaryTest.kt'):
    if not _os.path.exists('app/src/test/java/com/mohammedsamir/halaqati/'+_t): errors.append('RC27.45: missing unit test '+_t)
if 'push_approval_events' not in _repo: errors.append('RC27.46: pushApprovalEvents call missing')
if 'push_approval_events' not in open('migration/legacy-web-v2.44.0/supabase/functions/halaqati-notify/index.ts',encoding='utf-8').read(): errors.append('RC27.46: edge function action missing')
for _f in ('ui/screens/BirthDateField.kt','ui/screens/IdentityImport.kt'):
    if not _os.path.exists(_base+_f): errors.append('RC27.47: missing '+_f)
if 'BirthDateField(' not in _auth: errors.append('RC27.47: signup still types the birth date')
if 'BirthDateField(' not in open(_base+'ui/screens/ApprovalsScreen.kt',encoding='utf-8').read(): errors.append('RC27.47: add-child still types the birth date')
_core=open(_base+'ui/screens/CoreScreens.kt',encoding='utf-8').read()
if 'ناقص: ' not in _core: errors.append('RC27.47: missing-identity badge absent')
_appr=open(_base+'ui/screens/ApprovalsScreen.kt',encoding='utf-8').read()
if 'item(key = "identity")' not in _appr: errors.append('RC27.47: identity section must live inside the scrolling list')
_bc=open(_base+'ui/screens/BroadcastCenter.kt',encoding='utf-8').read() if _os.path.exists(_base+'ui/screens/BroadcastCenter.kt') else ''
if not _bc: errors.append('RC27.49: BroadcastCenter missing')
for _t in ('sendBroadcast','broadcastStats','broadcastDetail'):
    if _t not in _repo: errors.append('RC27.49: repo missing '+_t)
if 'manual_preview' not in _repo: errors.append('RC27.49: preview must use the separate manual_preview action')
if 'BroadcastCenter(profile)' not in open(_base+'ui/screens/ManagementScreens.kt',encoding='utf-8').read(): errors.append('RC27.49: center not reachable from notifications')
_edge=open('migration/legacy-web-v2.44.0/supabase/functions/halaqati-notify/index.ts',encoding='utf-8').read()
for _t in ("action === 'manual_preview'",'chunk(recipients, 150)','chunk(devices, 15)','.range(from, from + 999)'):
    if _t not in _edge: errors.append('RC27.49: edge function missing '+_t)
if 'delay(' in _bc or 'while (true)' in _bc: errors.append('RC27.49: broadcast center must not poll')
if not _os.path.exists('migration/20261021_v2649_rc27_49_broadcast_center.sql'): errors.append('RC27.49: SQL migration missing')
# RC27.50: the "tomorrow prep" notification must use the anchor-based activity assignment, never the raw plan assignment.
_rp = open('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt', encoding='utf-8').read()
_blk = _rp[_rp.index('if (now.hour >= 17)'):_rp.index('return delivered', _rp.index('if (now.hour >= 17)'))]
if 'val assignment = next.progress.assignment' in _blk: errors.append('RC27.50: home-prep notification must not use the raw progress assignment')
if 'dutyActivity?.assignment' not in _blk or 'extraAssignment' not in _blk: errors.append('RC27.50: home-prep notification must use the activity (anchor-based) assignment')

if errors:
    print('Motion gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('Motion gate passed')
