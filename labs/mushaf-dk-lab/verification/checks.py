"""Structural checks over all 604 pages: sura/ayah sequence, ayah-end markers, sura headers/basmala, sajdah, rub/hizb/juz.
DK side comes only from madina.json (glyph names / line types); reference side only from API json."""
import json,collections,sys
sys.path.insert(0,'/tmp/claude-0/agentB')
from lib import *
api={n:json.load(open(S2+f'api/p{n}.json')) for n in range(1,605)}
out={}
# ---- reference verse table (unique by verse_key), with first-word location in V2 layout
verses={}; first={}; last_end={}
for n in range(1,605):
    for v in api[n]['verses']:
        verses.setdefault(v['verse_key'],dict(juz=v['juz_number'],hizb=v['hizb_number'],rub=v['rub_el_hizb_number'],sajdah=v['sajdah_number'],vnum=v['verse_number'],words=len(v['words'])))
    for v in api[n]['verses']:
        for w in v['words']:
            pg=w.get('v2_page') or w['page_number']
            k=(v['verse_key'])
            loc=(pg,w['line_number'],w['position'])
            if k not in first or (pg,w['line_number'],w['position'])<first[k]: first[k]=loc
            if w['char_type_name']=='end': last_end[k]=(pg,w['line_number'])
keys=sorted(verses,key=lambda s:tuple(map(int,s.split(':'))))
out['api_verse_count']=len(keys)
cnt=collections.Counter(int(k.split(':')[0]) for k in keys)
out['api_surah_verse_counts']={s:cnt[s] for s in range(1,115)}
# contiguity per sura
bad=[s for s in range(1,115) if sorted(int(k.split(':')[1]) for k in keys if int(k.split(':')[0])==s)!=list(range(1,cnt[s]+1))]
out['api_noncontiguous_suras']=bad
# ---- DK walk: headers, basmala, end markers
sura=0; exp=0; dk_ends=[]; dk_hdr=[]; dk_bsm=[]; seq_err=[]
for pi,p in enumerate(P):
    n=pi+1
    for li,L in enumerate(p['lines'],start=1):
        if L['type']==1: sura+=1; exp=1; dk_hdr.append((sura,n,li))
        elif L['type']==2: dk_bsm.append((sura,n,li))
        else:
            for g in L['glyphs']:
                nm=GN[g['codepoint']]
                if nm.startswith('endofaya') and nm!='endofaya':
                    v=int(nm[8:]); dk_ends.append((sura,v,n,li))
                    if v!=exp: seq_err.append((n,li,sura,v,exp))
                    exp=v+1
out['dk_headers']=len(dk_hdr); out['dk_basmala_lines']=len(dk_bsm); out['dk_end_markers']=len(dk_ends)
out['dk_sequence_errors']=seq_err[:20]; out['dk_sequence_error_count']=len(seq_err)
dkc=collections.Counter(s for s,_,_,_ in dk_ends)
out['dk_surah_end_counts_equal_api']=all(dkc[s]==cnt[s] for s in range(1,115))
out['dk_surah_end_count_mismatch']=[(s,dkc[s],cnt[s]) for s in range(1,115) if dkc[s]!=cnt[s]]
mism=[]
for s_,v,n,li in dk_ends:
    ref=last_end.get(f'{s_}:{v}')
    if ref!=(n,li): mism.append(dict(sura=s_,ayah=v,dk=(n,li),api=ref))
out['end_marker_location_mismatch']=mism
hdr=[]
txtlines=[(pi+1,li) for pi,p in enumerate(P) for li,L in enumerate(p['lines'],start=1) if L['type']==0]
for s_,n,li in dk_hdr:
    f=first[f'{s_}:1']; has_b=any(b[0]==s_ for b in dk_bsm)
    nxt=next(x for x in txtlines if x>(n,li))
    hdr.append(dict(sura=s_,dk_page=n,dk_line=li,api_first_word=f,dk_first_text_line=nxt,dk_has_basmala_line=has_b,consistent=(f[0],f[1])==nxt))
out['header_check']=hdr
out['header_inconsistent']=[h for h in hdr if not h['consistent']]
out['basmala_expected_missing_for']=[1,9]
out['basmala_suras_with_line']=sorted({b[0] for b in dk_bsm})
# ---- sajdah
saj_api=[(k,v['sajdah'],first[k]) for k,v in verses.items() if v['sajdah']]
out['api_sajdah_verses']=len(saj_api)
sk=[]
for pi,p in enumerate(P):
    for li,L in enumerate(p['lines'],start=1):
        if L['type']!=0: continue
        for g in L['glyphs']:
            if GN[g['codepoint']]=='placeofsajdah': sk.append((pi+1,li))
out['dk_sajdah_glyphs']=len(sk); out['dk_sajdah_locations']=sk
out['api_sajdah_numbers']=sorted(v for _,v,_ in saj_api)
out['api_sajdah_list']=[(k,n,first[k]) for k,n,_ in sorted(saj_api,key=lambda x:x[1] if x[1] else 0)]
# ---- juz/hizb/rub
seq=[(verses[k]['juz'],verses[k]['hizb'],verses[k]['rub']) for k in keys]
out['juz_values']=sorted({a for a,_,_ in seq}); out['hizb_n']=len({b for _,b,_ in seq}); out['rub_n']=len({c for _,_,c in seq})
out['juz_hizb_rub_monotonic']=all(seq[i]<=seq[i+1] for i in range(len(seq)-1))
bound=[k for i,k in enumerate(keys) if i==0 or verses[k]['rub']!=verses[keys[i-1]]['rub']]
out['rub_boundaries']=len(bound)
# DK rub glyph words
dk_rub=[]
for pi,p in enumerate(P):
    for li,L in enumerate(p['lines'],start=1):
        if L['type']==0:
            for g in L['glyphs']:
                if GN[g['codepoint']]=='rubelhizb': dk_rub.append((pi+1,li))
out['dk_rub_glyphs']=len(dk_rub)
bl={}
for k in bound: bl[k]=first[k]
rub_loc=set(dk_rub)
out['rub_boundaries_with_dk_glyph_on_same_page_line']=sum(1 for k,(pg,ln,ps) in bl.items() if (pg,ln) in rub_loc)
out['rub_boundaries_without_glyph']=[k for k,(pg,ln,ps) in bl.items() if (pg,ln) not in rub_loc][:60]
# which boundaries: classify hizb-start (rub%4==1) vs other
out['glyph_count_by_boundary_kind']=dict(
  hizb_start=sum(1 for k in bound if (verses[k]['rub']-1)%4==0 and (verses[k]['pos'] if False else True) and (first[k][0],first[k][1]) in rub_loc),
  hizb_start_total=sum(1 for k in bound if (verses[k]['rub']-1)%4==0),
  other_boundary_with_glyph=sum(1 for k in bound if (verses[k]['rub']-1)%4!=0 and (first[k][0],first[k][1]) in rub_loc),
  other_boundary_total=sum(1 for k in bound if (verses[k]['rub']-1)%4!=0))
# glyph locations not at any boundary
bset={(a,b) for a,b,_ in bl.values()}
out['dk_rub_glyphs_not_at_boundary']=[x for x in dk_rub if x not in bset]
# juz boundaries page: first page of each juz (API)
jp={}
for k in keys:
    j=verses[k]['juz']; jp.setdefault(j,first[k][:2])
out['juz_start_page_line_api']=jp
# page-level API verse span per page for metadata cross-check
pagespan={}
for n in range(1,605):
    vs=sorted({k for k,loc in first.items() if loc[0]==n},key=lambda s:tuple(map(int,s.split(':'))))
    pagespan[n]=(vs[0],vs[-1]) if vs else None
out['page_first_last_verse_starting_on_page']=pagespan
# page line structure: DK lines per page and types
out['dk_lines_per_page']=collections.Counter(len(p['lines']) for p in P)
out['dk_lines_per_page']={str(k):v for k,v in out['dk_lines_per_page'].items()}
json.dump(out,open('/tmp/claude-0/agentB/structural_checks.json','w'),indent=1,default=str)
for k,v in out.items():
    if k in('api_surah_verse_counts','header_check','page_first_last_verse_starting_on_page','api_sajdah_list','dk_sajdah_locations','juz_start_page_line_api','rub_boundaries_without_glyph'): continue
    print(k,':',str(v)[:300])
