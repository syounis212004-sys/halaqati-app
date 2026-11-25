"""Compare DigitalKhatt's per-line word counts (madina.json) with the app's current V2 layout (api.quran.com by_page, words filtered by v2_page).
Needs: /tmp/claude-0/study2/dk/madina.json (curl https://digitalkhatt.org/assets/layouts/madina.json) and api/p{1..604}.json (fetch_api.py).
Result recorded in 09/12: 8,809 of 8,820 lines equal (99.88%), 597/604 pages fully equal; differing pages 27,177,254,400,443,451,589."""
import json, collections
dk = json.load(open('/tmp/claude-0/study2/dk/madina.json'))['pages']
def dk_words(line):
    n = 0; inw = False
    for g in line['glyphs']:
        if g['codepoint'] == 32: inw = False
        elif not inw: n += 1; inw = True
    return n
api = {n: json.load(open(f'/tmp/claude-0/study2/api/p{n}.json')) for n in range(1, 605)}
tot = same = okp = hdr = 0; diffp = {}
for n in range(1, 605):
    cnt = collections.Counter(); seen = set()
    for m in (n - 1, n, n + 1):
        if 1 <= m <= 604:
            for v in api[m]['verses']:
                for w in v['words']:
                    if (w.get('v2_page') or w['page_number']) == n:
                        k = (v['verse_key'], w['position'])
                        if k in seen: continue
                        seen.add(k); cnt[w['line_number']] += 1
    L = dk[n - 1]['lines']; ok = True
    for k in range(1, len(L) + 1):
        a = cnt.get(k, 0); d = dk_words(L[k - 1])
        if a == 0 and L[k - 1].get('type', 0) != 0: hdr += 1; continue
        tot += 1
        if a == d: same += 1
        else: ok = False; diffp.setdefault(n, []).append((k, a, d))
    okp += ok
print('lines', tot, 'equal', same, '(%.2f%%)' % (100 * same / tot), '| header lines skipped', hdr, '| pages fully equal', okp, '/604 | differing pages', sorted(diffp))
