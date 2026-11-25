import json,csv,collections,os
raw=json.load(open('/tmp/claude-0/agentB/_raw.json')); chk=json.load(open('/tmp/claude-0/agentB/structural_checks.json'))
rows=raw['rows']; out=[]
def img(kind,p,l):
    f=f'img/{kind}_p{p}_l{l}.png'
    return f if os.path.exists('/tmp/claude-0/agentB/'+f) else ''
for i,r in enumerate(rows):
    o=dict(id=i,page=r['page'],line=r['line'],dk_line=r['dk_line'],sura=r['sura'],ayah=r['ayah'],word_position=r['pos'],
      reference_text=r['ref'],dk_text=r.get('dk_text',r['dk']),reference_codepoints=r['ref_cp'],dk_codepoints=r['dk_cp'],
      api_only_codepoints=r['api_only'],dk_only_codepoints=r['dk_only'],category=r['cat'],classification=r['cls'],subtype=r.get('subtype',''),
      dk_glyph_names=r['dk_glyphs'],api_code_v2=r['api_codes'],
      image_v2=img('v2',r['page'],int(r['line'])),image_dk=img('dk',r['page'],int(r['dk_line'] or r['line'])))
    out.append(o)
json.dump(out,open('/tmp/claude-0/agentB/differences.json','w'),ensure_ascii=False,indent=1)
cols=['id','page','line','dk_line','sura','ayah','word_position','reference_text','dk_text','api_only_codepoints','dk_only_codepoints','classification','subtype','category','reference_codepoints','dk_codepoints','dk_glyph_names','image_v2','image_dk']
with open('/tmp/claude-0/agentB/differences.csv','w',newline='',encoding='utf-8-sig') as f:
    w=csv.DictWriter(f,fieldnames=cols,extrasaction='ignore'); w.writeheader()
    for o in out: w.writerow(o)
# per-page summary
pg=raw['pages']
for p in pg:
    p['text_diff_rows']=sum(1 for o in out if o['page']==p['page'] and o['category']=='text')
json.dump(pg,open('/tmp/claude-0/agentB/pages_summary.json','w'),ensure_ascii=False)
with open('/tmp/claude-0/agentB/pages_summary.csv','w',newline='') as f:
    keys=['page','api_words','dk_words','api_end','dk_end','api_letters','dk_letters','api_marks','dk_marks','api_waqf','dk_waqf','api_sajdah_mark','dk_sajdah_mark','api_rub_glyph','dk_rub_glyph','api_line_count_text','dk_line_count_text','text_diff_rows','layout_diffs']
    w=csv.DictWriter(f,fieldnames=keys,extrasaction='ignore'); w.writeheader(); [w.writerow(p) for p in pg]
# stats
tr=[o for o in out if o['category']=='text']
cls=collections.Counter(o['classification'] for o in tr)
stats=dict(words_compared=raw['tot_words'],text_difference_words=len(tr),layout_rows=len(out)-len(tr),classes=dict(cls),
  pages_with_text_diff=len({o['page'] for o in tr}),pages_without=604-len({o['page'] for o in tr}),
  totals=dict(api_letters=sum(p['api_letters'] for p in pg),dk_letters=sum(p['dk_letters'] for p in pg),api_marks=sum(p['api_marks'] for p in pg),dk_marks=sum(p['dk_marks'] for p in pg),
    api_waqf=sum(p['api_waqf'] for p in pg),dk_waqf=sum(p['dk_waqf'] for p in pg),api_words=sum(p['api_words'] for p in pg),dk_words=sum(p['dk_words'] for p in pg),
    api_end=sum(p['api_end'] for p in pg),dk_end=sum(p['dk_end'] for p in pg)),
  pages_letter_count_differ=[p['page'] for p in pg if p['api_letters']!=p['dk_letters']],
  pages_wordcount_differ=[p['page'] for p in pg if p['api_words']!=p['dk_words'] or p['api_end']!=p['dk_end']],
  order_only_equal_sorted=len(raw['order_info']),
  waqf_pairs=[[list(k),v] for k,v in collections.Counter((o['api_only_codepoints'],o['dk_only_codepoints']) for o in tr if o['classification']=='waqf_only').most_common()],
  subtypes=collections.Counter(o['subtype'] for o in tr).most_common(12))
json.dump(stats,open('/tmp/claude-0/agentB/stats.json','w'),ensure_ascii=False,indent=1)
print(json.dumps(stats,ensure_ascii=False,indent=1)[:3500])
