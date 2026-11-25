#!/usr/bin/env python3
"""Turns the emulator perf run output (dk-perf-results.json + size files) into a Markdown summary and sizes.json. Reads only; no network."""
import json, re, sys
from pathlib import Path

out = Path(sys.argv[1])
pack = Path(sys.argv[2]) if len(sys.argv) > 2 else None  # generated pack dir on the runner, for the on-disk size
lines = ['# DigitalKhatm performance run (emulator, not a phone)', '']
sizes = {}
for label in ('release', 'debug'):
    f = out / f'sizes-{label}.txt'
    if not f.exists():
        continue
    t = f.read_text(errors='replace')
    m = re.search(r'apk_file_bytes=(\d+)', t)
    d = re.search(r'--- du -sk code dir\n(\d+)\s', t)
    sizes[label] = {'apk_bytes': int(m.group(1)) if m else None, 'installed_code_dir_kib': int(d.group(1)) if d else None}
if pack and pack.is_dir():
    files = [p for p in pack.iterdir() if p.is_file()]
    sizes['pack_on_disk_bytes_generated'] = sum(p.stat().st_size for p in files)
    sizes['pack_files'] = len(files)
(out / 'sizes.json').write_text(json.dumps(sizes, indent=2))

lines += ['## Sizes', '', '| item | value |', '|---|---|']
for k, v in sizes.items():
    lines.append(f'| {k} | {v} |')
errs = out / 'errors.txt'
if errs.exists():
    lines += ['', '## Errors', '```', errs.read_text(errors='replace'), '```']
res = out / 'dk-perf-results.json'
if res.exists() and res.stat().st_size:
    j = json.loads(res.read_text())
    dv = j['device']
    lines += ['', '## Device', f"{dv['manufacturer']} {dv['model']} - API {dv['sdk_int']} - {', '.join(dv['abis'])} - {dv['cpu_count']} CPUs - emulator guess: {dv['is_emulator_guess']}"]
    g = j['glyph_load']
    lines += ['', '## Glyph load (ms)', f"cold {g['cold_ms']}, warm {g['warm_ms_each']}"]
    def row(name, s):
        return f"| {name} | {s['min']} | {s['median']} | {s['p95']} | {s['max']} | {s['sum']} |"
    for title, key in (('Pass 1 (cold path cache), 604 pages, ms', 'pass1_cold_cache'), ('Passes 2-5 (warm), ms per page', 'passes_2_to_5_warm')):
        lines += ['', f'## {title}', '', '| phase | min | median | p95 | max | sum |', '|---|---|---|---|---|---|']
        for ph in ('load_ms', 'alloc_bitmap_ms', 'draw_ms', 'word_rects_ms', 'total_ms'):
            lines.append(row(ph, j[key][ph]))
    m = j['memory']
    lines += ['', '## Memory', f"growth pass 2 to 5 after GC: java {m['growth_pass2_to_pass5_java_bytes']} B (limit {m['java_limit_bytes']}), native {m['growth_pass2_to_pass5_native_bytes']} B (limit {m['native_limit_bytes']}), PSS {m['growth_pass2_to_pass5_pss_kb']} KiB",
              f"assertions: {j['passed_assertions']}", f"failures: {j['failures']}"]
    lines += ['', '## Limits of this measurement'] + [f'- {x}' for x in j['limits']]
(out / 'summary.md').write_text('\n'.join(lines) + '\n')
print('\n'.join(lines))
