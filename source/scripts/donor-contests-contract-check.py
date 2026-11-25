#!/usr/bin/env python3
"""Donor contests DB contract: RLS enabled, explicit policies, definer functions pin search_path, anon revoked, idempotent."""
from pathlib import Path
import re, sys

root = Path(__file__).resolve().parents[1]
p = root / 'migration/20261125_v2920_donor_contests.sql'
if not p.exists():
    print('Donor contests DB contract FAILED: migration missing'); sys.exit(1)
s = p.read_text(encoding='utf-8')
low = s.lower()
errors = []

if 'create table if not exists public.donor_contests' not in low: errors.append('table donor_contests not created idempotently')
if 'alter table public.donor_contests enable row level security' not in low: errors.append('RLS not enabled on donor_contests')
for pol in ['donor_contests_donor_select', 'donor_contests_admin_select', 'donor_contests_related_select',
            'donor_contests_donor_insert', 'donor_contests_donor_update', 'donor_contests_admin_update']:
    if f'create policy {pol} on public.donor_contests' not in low: errors.append('missing policy ' + pol)
    if f'drop policy if exists {pol} on public.donor_contests' not in low: errors.append('policy not re-runnable: ' + pol)
for token in ["status in ('pending','approved','rejected')", 'reject_reason', 'decided_by', 'decided_at', 'starts_on', 'ends_on',
              "status = 'approved'", "status = 'pending'", 'public.is_admin()', 'donor_halaqas']:
    if token.lower() not in low: errors.append('missing token: ' + token)
if re.search(r'\bgrant\s+(all|delete)\b[^;]*donor_contests', low): errors.append('table grants too wide (no delete / all)')
if 'revoke all on public.donor_contests from anon, authenticated' not in low: errors.append('table privileges not reset')

# every function must be security definer with a pinned search_path and revoked from anon
funcs = re.findall(r'create or replace function (public\.\w+)\(([^)]*)\)(.*?)(?=\ncreate or replace function|\n-- \d\)|\nrevoke all on function|\Z)', s, flags=re.S | re.I)
names = {f[0] for f in funcs}
expected = {'public.contest_is_donor', 'public.contest_donor_linked', 'public.contest_user_halaqas', 'public.contest_donor_halaqas',
            'public.contest_submit', 'public.contest_my', 'public.contest_admin_list', 'public.contest_visible', 'public.contest_decide'}
for missing in sorted(expected - names): errors.append('missing function ' + missing)
for name, args, body in funcs:
    b = body.lower()
    if 'security definer' not in b: errors.append(name + ': not security definer')
    if 'set search_path = public, pg_temp' not in b: errors.append(name + ': search_path not pinned to public, pg_temp')
    if not re.search(r'revoke all on function ' + re.escape(name) + r'\(', low): errors.append(name + ': no revoke')
    if not re.search(r'revoke all on function ' + re.escape(name) + r'\([^)]*\) from public, anon', low): errors.append(name + ': anon not revoked')
    if not re.search(r'grant execute on function ' + re.escape(name) + r'\([^)]*\) to authenticated', low): errors.append(name + ': no grant to authenticated')
if re.search(r'set search_path to \'public\'\s', low) and 'pg_temp' not in low: errors.append('weak search_path')

# gates inside the RPCs
if "is_admin()" not in re.search(r'function public\.contest_decide.*?end \$\$;', s, re.S | re.I).group(0).lower(): errors.append('contest_decide must check is_admin()')
if 'contest_pending' not in low or 'contest_decision' not in low or 'contest_approved' not in low: errors.append('notification kinds missing')
if 'on conflict (recipient_id, source_key) do nothing' not in low: errors.append('notifications not idempotent')
if re.search(r'create (unique )?index (?!if not exists)', low) or re.search(r'create table (?!if not exists)', low): errors.append('non-idempotent create')

# Kotlin side calls the same RPC names
kt = (root / 'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text(encoding='utf-8')
for rpc in ['contest_my', 'contest_admin_list', 'contest_visible', 'contest_donor_halaqas', 'contest_submit', 'contest_decide']:
    if f'"{rpc}"' not in kt: errors.append('Kotlin repository does not call ' + rpc)

if errors:
    print('Donor contests DB contract FAILED')
    for e in errors: print(' -', e)
    sys.exit(1)
print('Donor contests DB contract PASS: RLS on, 6 policies, 9 definer functions with pinned search_path, anon revoked, idempotent')
