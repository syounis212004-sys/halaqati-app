#!/usr/bin/env python3
from pathlib import Path
import json,re,sys
root=Path(__file__).resolve().parents[1]
errors=[]

def read(rel):
    p=root/rel
    if not p.exists():
        errors.append(f'missing {rel}')
        return ''
    return p.read_text(encoding='utf-8',errors='ignore')

build=read('app/build.gradle.kts')
release_note=read('RELEASE_2_60_0_RC24_CERTIFICATE_ARCHIVE_RENDERING_CORE_AR.md')
rc24_migration=read('migration/20261009_v2600_rc24_certificate_archive_rendering_core.sql')
contract_text=read('migration/source-contract.json')
try: contract=json.loads(contract_text)
except Exception as e:
    contract={}; errors.append(f'invalid source-contract.json: {e}')

checks={
    'applicationId = "com.mohammedsamir.halaqati"':'applicationId',
    'namespace = "com.mohammedsamir.halaqati"':'namespace',
    'compileSdk = 36':'compileSdk',
    'minSdk = 23':'minSdk',
    'targetSdk = 36':'targetSdk',
    'versionCode = 29110':'versionCode',
    'versionName = "2.91.10"':'versionName',
}
for token,label in checks.items():
    if token not in build: errors.append(f'build contract missing {label}: {token}')
if contract.get('native_version')!='2.91.10': errors.append('source contract native_version mismatch')
if not release_note: errors.append('RC24 release note missing')
if 'PDF' not in release_note or 'محلي' not in release_note or 'لا تُرفع إلى Supabase' not in release_note: errors.append('RC24 release note does not state local binary asset policy')
if '20261009_v2600_rc24_certificate_archive_rendering_core.sql' not in release_note or not rc24_migration: errors.append('RC24 migration/release-note contract missing')
if contract.get('application_id')!='com.mohammedsamir.halaqati': errors.append('source contract application_id mismatch')
required_tables={
    'profiles','halaqas','halaqa_teachers','halaqa_supervisors','student_categories','students','student_guardians','student_halaqa_memberships',
    'sessions','attendance_records','recitation_entries','student_daily_notes','student_goals','monthly_comments',
    'announcements','forum_posts','forum_comments','student_points','app_settings','smart_plans','smart_plan_events','smart_plan_phases','smart_plan_phase_states','smart_plan_phase_templates',
    'user_devices','notification_preferences','notifications','supervisor_visits','exam_settings','exam_examiners','student_exams','centers','data_import_jobs',
    'certificate_templates','certificates','certificate_series','certificate_revisions','certificate_number_counters'
}
tables=set(contract.get('tables',[]))
missing=sorted(required_tables-tables)
if missing: errors.append(f'source contract missing tables: {missing}')
if len(tables)<28: errors.append(f'expected at least 28 app tables, found {len(tables)}')
required_views={'leaderboard_daily','leaderboard_monthly','monthly_student_stats'}
views=set(contract.get('views',[]))
missing_views=sorted(required_views-views)
if missing_views: errors.append(f'source contract missing views: {missing_views}')
if len(views)!=4: errors.append(f'expected 3 compatibility views + RC15 consistency view, found {len(views)}')
required_rpc={
    'admin_delete_account','delete_halaqa_cascade','guardian_competition_board_range','guardian_student_dashboard_range',
    'list_guardian_accounts','set_student_primary_guardian','student_rank_snapshot','rc112_capabilities','rc112_reconcile_plan_phase_states','rc112_transition_plan_students','rc112_update_plan_phase','rc113_capabilities','rc113_bulk_configure_plan_cycles','rc23_get_or_create_session','rc24_issue_certificate','rc24_create_certificate_revision','rc24_void_certificate','rc24_import_legacy_certificate','rc24_next_certificate_number'
}
missing_rpc=sorted(required_rpc-set(contract.get('rpc',[])))
if missing_rpc: errors.append(f'source contract missing RPCs: {missing_rpc}')
if len(set(contract.get('rpc',[])))<13: errors.append('expected at least 13 required RPC functions')

# Every statically named table/RPC referenced by Native source must exist in the declared backend contract.
source_text='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in (root/'app/src/main/java').rglob('*.kt'))
table_patterns=(
    r'api\.select\(\s*"([a-zA-Z0-9_]+)"',
    r'write\(\s*"([a-zA-Z0-9_]+)"',
    r'patch\(\s*"([a-zA-Z0-9_]+)"',
    r'api\.delete\(\s*"([a-zA-Z0-9_]+)"',
)
referenced_tables=set()
for pattern in table_patterns:
    referenced_tables.update(re.findall(pattern, source_text))
unknown_tables=sorted(referenced_tables - tables - views)
if unknown_tables: errors.append(f'Native source references undeclared backend tables/views: {unknown_tables}')
referenced_rpc=set(re.findall(r'api\.rpc\(\s*"([a-zA-Z0-9_]+)"', source_text))
unknown_rpc=sorted(referenced_rpc - set(contract.get('rpc',[])))
if unknown_rpc: errors.append(f'Native source references undeclared RPCs: {unknown_rpc}')

runtime_files=[p for p in (root/'app/src/main').rglob('*') if p.is_file()]
runtime='\n'.join(p.read_text(encoding='utf-8',errors='ignore') for p in runtime_files)
for forbidden in ('Capacitor','BridgeActivity','cordova'):
    if forbidden.lower() in runtime.lower(): errors.append(f'forbidden runtime dependency/reference: {forbidden}')
# RC13 explicitly allows a WebView only inside the isolated local document renderer.
webview_outside=[]
for p in runtime_files:
    txt=p.read_text(encoding='utf-8',errors='ignore')
    if 'android.webkit.WebView' in txt and '/documents/' not in p.as_posix():
        webview_outside.append(str(p.relative_to(root)))
if webview_outside: errors.append(f'WebView escaped the isolated document renderer: {webview_outside}')
tracks=read('app/src/main/java/com/mohammedsamir/halaqati/data/StudentTrackRules.kt')
if 'setOf(GENERAL_HIFZ, TATHBIT)' not in tracks: errors.append('student track contract is not exactly two tracks')
if '"sard"' not in tracks or 'GENERAL_HIFZ' not in tracks: errors.append('legacy sard track normalization missing')

# RC27.2: Cairo is a variable font; every weight must pin its own wght axis value or Compose renders one weight.
_typo = (Path('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/CairoTypography.kt').read_text(encoding='utf-8')
         if 'Path' in globals() else open('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/CairoTypography.kt', encoding='utf-8').read())
for _token in ('FontVariation.weight(drawn)', 'cairo(FontWeight.Bold, drawn = 700)', 'cairo(FontWeight.SemiBold, drawn = 600)', 'cairo(FontWeight.ExtraBold, drawn = 800)', '@file:OptIn(ExperimentalTextApi::class)'):
    if _token not in _typo:
        errors.append('Cairo variable-font weights not pinned: missing ' + _token)

if errors:
    print('Release contract FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('Release contract passed (2.91.10, Native runtime + RC26 Plan Range & Daily Duty)')
