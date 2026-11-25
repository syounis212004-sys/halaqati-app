"""V1 (1405H) vs V2 layout from the API fields line_v1/line_v2/v1_page/v2_page (api1/ = by_page with word_fields=code_v1,code_v2,line_v1,line_v2,v1_page,v2_page,...).
Result: only 39/604 pages have identical per-line word counts; 7,614 of 83,665 words sit on another line; 361 words on another page."""
import json, collections
api = {n: json.load(open(f'/tmp/claude-0/study2/api1/p{n}.json')) for n in range(1, 605)}
def counts(ed, n):
    seen = set(); c = collections.Counter()
    for m in (n - 1, n, n + 1):
        if 1 <= m <= 604:
            for v in api[m]['verses']:
                for w in v['words']:
                    if w[f'v{ed}_page'] == n:
                        k = (v['verse_key'], w['position'])
                        if k not in seen: seen.add(k); c[w[f'line_v{ed}']] += 1
    return c
same = sum(counts(1, n) == counts(2, n) for n in range(1, 605))
pg = ln = tot = 0
for n in range(1, 605):
    for v in api[n]['verses']:
        for w in v['words']:
            tot += 1
            if w['v1_page'] != w['v2_page']: pg += 1
            elif w['line_v1'] != w['line_v2']: ln += 1
print('pages equal', same, '/604 | words', tot, '| other page', pg, '| same page other line', ln)
