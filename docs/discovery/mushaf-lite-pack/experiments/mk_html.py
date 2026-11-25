import json,sys,collections
sel=json.load(open('diverse_pages.json'))['selected']
api={n:json.load(open(f'api/p{n}.json')) for n in range(1,605)}
def lines(n):
    seen=set(); L=collections.defaultdict(list)
    for m in (n-1,n,n+1):
        if 1<=m<=604:
            for v in api[m]['verses']:
                for w in v['words']:
                    if (w.get('v2_page') or w['page_number'])==n:
                        k=(v['verse_key'],w['position'])
                        if k in seen: continue
                        seen.add(k); L[w['line_number']].append(w['code_v2'])
    return L
def html(n,src,fmt,px=62):
    L=lines(n)
    rows=''.join(f'<div class="l" style="height:{round(1000*232/1440)}px">{"".join(L[k])}</div>' for k in sorted(L))
    return f'''<!doctype html><meta charset=utf-8><style>@font-face{{font-family:F;src:url("file://{src}") format("{fmt}")}}
body{{margin:0;background:#fff;width:1000px}}.l{{direction:rtl;white-space:nowrap;font-family:F;font-size:{px}px;line-height:{round(1000*232/1440)}px;color:#000;overflow:visible}}</style>{rows}'''
for n in sel:
    open(f'cmp/v2_p{n}.html','w').write(html(n,f'/tmp/claude-0/study/v2all/p{n}.ttf','truetype'))
    open(f'cmp/v4_p{n}.html','w').write(html(n,f'/tmp/claude-0/study2/v4/p{n}.woff2','woff2'))
print(len(sel))
