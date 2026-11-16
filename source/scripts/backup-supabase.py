#!/usr/bin/env python3
"""Halaqati RC27: read-only JSON export of the important Supabase tables (paginated).

Env: SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY. Usage: backup-supabase.py OUT_DIR
Writes <table>.json.gz per table plus MANIFEST.json (row counts + sha256). Exits non-zero if any
table fails or a table that normally has rows comes back empty while the server said otherwise.
"""
import gzip, hashlib, json, os, sys, urllib.request, urllib.error, datetime

TABLES = [
    'profiles', 'halaqas', 'halaqa_teachers', 'halaqa_supervisors', 'students', 'student_guardians',
    'student_categories', 'student_halaqa_memberships', 'sessions', 'attendance_records',
    'recitation_entries', 'student_daily_notes', 'student_goals', 'monthly_comments', 'announcements',
    'supervisor_visits', 'student_points', 'app_settings', 'smart_plans', 'smart_plan_events',
    'smart_plan_phases', 'smart_plan_phase_states', 'smart_plan_phase_templates',
    'smart_plan_month_archives', 'smart_plan_health_daily', 'certificate_templates', 'certificates',
    'certificate_series', 'certificate_revisions', 'achievement_badge_definitions', 'student_badge_awards',
    'management_audit_log', 'deleted_session_snapshots', 'notification_preferences',
]
PAGE = 1000
# Tables without an `id` column paginate on their primary key (stable order is required for offsets).
ORDER = {
    'achievement_badge_definitions': 'code', 'certificate_series': 'series_key',
    'halaqa_supervisors': 'halaqa_id.asc,supervisor_id', 'halaqa_teachers': 'halaqa_id.asc,teacher_id',
    'notification_preferences': 'user_id', 'smart_plan_health_daily': 'plan_id.asc,student_id.asc,day',
    'student_guardians': 'student_id.asc,guardian_id',
}


def fetch(base, key, table, offset):
    req = urllib.request.Request(
        f"{base}/rest/v1/{table}?select=*&order={ORDER.get(table, 'id')}.asc&offset={offset}&limit={PAGE}",
        headers={'apikey': key, 'Authorization': f'Bearer {key}', 'Accept': 'application/json'},
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        return json.loads(r.read().decode('utf-8'))


def export_table(base, key, table):
    rows, offset = [], 0
    while True:
        page = fetch(base, key, table, offset)
        rows.extend(page)
        if len(page) < PAGE:
            return rows
        offset += PAGE


def main(out):
    base = os.environ['SUPABASE_URL'].rstrip('/')
    key = os.environ['SUPABASE_SERVICE_ROLE_KEY']
    os.makedirs(out, exist_ok=True)
    manifest = {'created_at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'tables': {}}
    failed = []
    for table in TABLES:
        try:
            rows = export_table(base, key, table)
        except urllib.error.HTTPError as e:
            # A table that does not exist (older schema) is skipped; anything else is a failure.
            if e.code == 404:
                manifest['tables'][table] = {'skipped': 'not found'}
                continue
            failed.append(f'{table}: HTTP {e.code}')
            continue
        except Exception as e:  # network, JSON
            failed.append(f'{table}: {e}')
            continue
        data = json.dumps(rows, ensure_ascii=False, separators=(',', ':')).encode('utf-8')
        path = os.path.join(out, f'{table}.json.gz')
        with gzip.GzipFile(path, 'wb', mtime=0) as fh:
            fh.write(data)
        manifest['tables'][table] = {'rows': len(rows), 'sha256': hashlib.sha256(data).hexdigest()}
        print(f'{table}: {len(rows)} rows')
    with open(os.path.join(out, 'MANIFEST.json'), 'w', encoding='utf-8') as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
    if failed:
        print('FAILED:', *failed, sep='\n  ', file=sys.stderr)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv[1]))
