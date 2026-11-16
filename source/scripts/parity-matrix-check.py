#!/usr/bin/env python3
from pathlib import Path
import argparse, json, sys
root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(); p.add_argument('--allow-incomplete',action='store_true'); a=p.parse_args()
path=root/'migration/parity/v244-parity-matrix.json'
errs=[]
if not path.exists(): errs.append('missing migration/parity/v244-parity-matrix.json'); rows=[]
else:
    try: rows=json.loads(path.read_text(encoding='utf-8'))
    except Exception as e: rows=[]; errs.append(f'invalid JSON: {e}')
required={'id','golden_surface','roles','native_destination','actions','offline','permissions','status','evidence'}
allowed={'MATCHED','IMPROVED','IMPROVED_COMPATIBLE','PARTIAL','MISSING','BROKEN'}
seen=set()
for r in rows if isinstance(rows,list) else []:
    if not isinstance(r,dict): errs.append('entry is not object'); continue
    ident=r.get('id','<unknown>')
    if ident in seen: errs.append(f'duplicate id {ident}')
    seen.add(ident)
    miss=required-r.keys()
    if miss: errs.append(f'{ident}: missing {sorted(miss)}')
    if r.get('status') not in allowed: errs.append(f'{ident}: invalid status {r.get("status")}')
    if not isinstance(r.get('roles'),list) or not r.get('roles'): errs.append(f'{ident}: roles must be non-empty list')
    if not isinstance(r.get('actions'),list): errs.append(f'{ident}: actions must be list')
    if not isinstance(r.get('evidence'),list) or not r.get('evidence'): errs.append(f'{ident}: evidence must be non-empty list')
    if not a.allow_incomplete and r.get('required',True) and r.get('status') in {'PARTIAL','MISSING','BROKEN'}:
        errs.append(f'release blocker {ident}: {r.get("status")}')
if not isinstance(rows,list) or not rows: errs.append('matrix must be non-empty array')
if errs:
    print('PARITY MATRIX FAILED'); [print(' -',x) for x in errs]; sys.exit(1)
from collections import Counter
c=Counter(r['status'] for r in rows)
print(f'Parity matrix valid: {len(rows)} features ({"implementation" if a.allow_incomplete else "release"} mode)')
print('Status: '+', '.join(f'{k}={v}' for k,v in sorted(c.items()) if v))
