#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1]
p=root/'migration/20261009_v2600_rc24_certificate_archive_rendering_core.sql'
if not p.exists():
    print('RC24 DB contract FAILED: migration missing');sys.exit(1)
s=p.read_text(encoding='utf-8')
errors=[]
for key,code in [('general','G'),('sard','S'),('khatm','K'),('monthly','M'),('ideal','I'),('rank','R'),('custom','C')]:
    if f"('{key}','{code}'" not in s: errors.append(f'missing series {key}/{code}')
for token in [
    'primary key(issue_year,series_key)',
    'unique(certificate_id, revision_no)',
    'unique(certificate_id, content_hash)',
    "set search_path=pg_catalog, public",
    'rc24_next_certificate_number',
    'rc24_issue_certificate',
    'rc24_create_certificate_revision',
    'rc24_void_certificate',
    'rc24_import_legacy_certificate',
    'certificate_revisions_read',
    'legacy_partial',
    'metadata_only_no_pdf_no_assets',
]:
    if token not in s: errors.append('missing token: '+token)
for fn in [
    'public.rc24_next_certificate_number(text,date)',
    'public.rc24_issue_certificate(uuid,text,date,jsonb)',
    'public.rc24_create_certificate_revision(uuid,jsonb,text)',
    'public.rc24_void_certificate(uuid,text)',
    'public.rc24_import_legacy_certificate(uuid,uuid,text,text,date,text,jsonb)',
]:
    if f'revoke all on function {fn} from public, anon' not in s: errors.append('anon revoke missing: '+fn)
if "certificate_number ~ '^HAL-CERT-[0-9]{4}-[0-9]{5,}$'" not in s: errors.append('legacy number preservation marker missing')
if 'update public.certificates\nset certificate_number' in s.lower(): errors.append('migration appears to rewrite existing numbers')
if errors:
    print('RC24 DB contract FAILED')
    [print(' -',e) for e in errors]
    sys.exit(1)
print('RC24 DB contract PASS — series, counters, revisions, legacy preservation, RLS and RPC security present')
