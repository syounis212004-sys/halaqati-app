import json,collections,difflib,csv,unicodedata,sys
sys.path.insert(0,'/tmp/claude-0/agentB')
from lib import *
api={n:json.load(open(S2+f'api/p{n}.json')) for n in range(1,605)}
WAQF={0x6D6,0x6D7,0x6D8,0x6D9,0x6DA,0x6DB}
MADDA={0x653,0x6E4}
HAMZA={0x654,0x655}
TANC={0x64B,0x64C,0x64D,0x6E2,0x6ED,0x8F0,0x8F1,0x8F2}
SEAT={0x627,0x648,YA,0x621}
def cls_token(kind,x):
    if kind=='L':
        return 'hamza' if x in SEAT and False else 'letter'
    if isinstance(x,tuple): return 'tanween'
    if x in WAQF: return 'waqf'
    if x in MADDA: return 'small_madda'
    if x in HAMZA: return 'hamza'
    if x in TANC: return 'tanween'
    return 'other_mark'
def api_tokens_for(w):
    if w['ct']=='end':
        dig=int(''.join(str(unicodedata.digit(c)) for c in w['txt']))
        return [('END',w['vnum'])],dig
    if w['txt']=='۞': return [('M',0x6DE)],None
    return api_word_tokens(w['txt']),None
def dk_tokens_for(w):
    t,unk=dk_word_tokens(w['names']); return t,unk
def merge_marks(ws,field):
    out=[]
    for w in ws:
        kinds={k for k,_ in w['tok']}
        solo_rub=[t for t in w['tok']]==[('M',0x6DE)]
        if out and kinds<={'M'} and not solo_rub and out[-1]['line']==w['line'] and not any(k in('END','NUM') for k,_ in out[-1]['tok']):
            p=out[-1]; p['tok']=p['tok']+w['tok']; p['canon']=canon(p['tok']); p['merged']=p.get('merged',0)+1
            p[field]=(p[field]+' '+w[field]) if field=='txt' else p[field]+w[field]
            if 'unk' in p: p['unk']=p['unk']+w.get('unk',[])
        else: out.append(w)
    return out
def flat_unsorted(tok):
    out=[];cur=None
    for k,v in tok:
        out.append((k,v))
    return [x for x in out]
order_info=[]
rows=[]; pages=[]; pg_wb=[]; unknowns=collections.Counter(); end_mismatch=[]
tot_words=0; equal=0
classcount=collections.Counter(); lettercount_api=0; lettercount_dk=0
for n in range(1,605):
    aw=api_words(api,n); dw=dk_words(n)
    A=[];Dk=[]
    for w in aw:
        t,dig=api_tokens_for(w); w['tok']=t; w['canon']=canon(t)
        if dig is not None and dig!=w['vnum']: end_mismatch.append((n,w['vk'],dig,w['vnum']))
    for w in dw:
        t,unk=dk_tokens_for(w); w['tok']=t; w['canon']=canon(t)
        for x in unk: unknowns[x]+=1
        w['unk']=unk
    aw=merge_marks(aw,'txt'); dw=merge_marks(dw,'names')
    ka=[key(w['canon']) for w in aw]; kd=[key(w['canon']) for w in dw]
    sm=difflib.SequenceMatcher(None,ka,kd,autojunk=False)
    pairs=[]  # (api idx|None, dk idx|None)
    for op,i1,i2,j1,j2 in sm.get_opcodes():
        if op=='equal':
            pairs+=[(i1+k,j1+k) for k in range(i2-i1)]
        else:
            la,ld=i2-i1,j2-j1
            m=min(la,ld)
            # pair in order; extra are unmatched
            pairs+=[(i1+k,j1+k) for k in range(m)]
            pairs+=[(i1+k,None) for k in range(m,la)]
            pairs+=[(None,j1+k) for k in range(m,ld)]
    # pair identical leftovers (word moved: different position/line) and merge API words split differently from DK
    ua=[p for p in pairs if p[1] is None]; ud=[p for p in pairs if p[0] is None]
    bykey=collections.defaultdict(list)
    for _,dj in ud: bykey[kd[dj]].append(dj)
    used=set(); newp=[]
    for ai,_ in ua:
        if bykey.get(ka[ai]): dj=bykey[ka[ai]].pop(0); used.add(dj); newp.append((ai,dj))
    pairs=[p for p in pairs if not((p[1] is None and any(p[0]==x[0] for x in newp)) or (p[0] is None and p[1] in used))]+newp
    pairs.sort(key=lambda p:((p[0] if p[0] is not None else 10**6),(p[1] if p[1] is not None else 10**6)))
    wb=[]
    i=0; pp=[]
    while i<len(pairs):
        ai,di=pairs[i]
        if ai is not None and di is not None and aw[ai]['canon']!=dw[di]['canon'] and i+1<len(pairs) and pairs[i+1][1] is None and pairs[i+1][0] is not None:
            a2=aw[pairs[i+1][0]]
            cm=canon(aw[ai]['tok']+a2['tok'])
            if cm==dw[di]['canon']:
                wb.append((ai,di,pairs[i+1][0])); i+=2; continue
            if [l for l,m in cm]==[l for l,m in dw[di]['canon']]:   # same letters, marks differ: merge, then diff normally
                aw[ai]=dict(aw[ai]); aw[ai]['tok']=aw[ai]['tok']+a2['tok']; aw[ai]['canon']=cm; aw[ai]['txt']=aw[ai]['txt']+' | '+a2['txt']; aw[ai]['wsplit']=True; a2['dup']=True
                pp.append((ai,di)); i+=2; continue
        pp.append((ai,di)); i+=1
    pairs=pp
    for ai,di,aj in wb:
        a=aw[ai]; a2=aw[aj]; d=dw[di]
        rows.append(dict(page=n,line=a['line'],dk_line=d['line'],sura=int(a['vk'].split(':')[0]),ayah=int(a['vk'].split(':')[1]),pos=a['pos'],ref=a['txt']+' | '+a2['txt'],
            dk='(one DK word)',ref_cp='',dk_cp='',api_only='',dk_only='',cat='layout',cls='word_boundary_difference',subtype='API has 2 words (pos %d,%d), DK glyph run has 1 word with identical letters/marks'%(a['pos'],a2['pos']),
            detail='',api_codes=a['code']+a2['code'],dk_glyphs=' '.join(d['names'])))
        pg_wb.append(1)
    pg=dict(page=n,api_words=sum(1 for w in aw if w['ct']!='end'),dk_words=0,api_end=sum(1 for w in aw if w['ct']=='end'),dk_end=0,
            word_diffs=0,layout_diffs=0)
    for w in dw:
        if any(t[0]=='END' for t in w['tok']): pg['dk_end']+=1
        else: pg['dk_words']+=1
    for ai,di in pairs:
        a=aw[ai] if ai is not None else None; d=dw[di] if di is not None else None
        if a and a['ct']!='end' and a['txt']!='۞': tot_words+=1
        if a and d and a['canon']==d['canon']:
            equal+=1
            if a['ct']!='end' and flat_unsorted(a['tok'])!=flat_unsorted(d['tok']): order_info.append((n,a['vk'],a['pos']))
            if a['line']!=d['line']:
                pg['layout_diffs']+=1
                rows.append(dict(page=n,line=a['line'],dk_line=d['line'],sura=int(a['vk'].split(':')[0]),ayah=int(a['vk'].split(':')[1]),pos=a['pos'],
                    ref=a['txt'],dk='(same text, on DK line %d)'%d['line'],ref_cp='',dk_cp='',api_only='',dk_only='',cat='layout',cls='line_break_difference',subtype='',detail='API line %d vs DK line %d'%(a['line'],d['line']),
                    api_codes=a['code'],dk_glyphs=' '.join(d['names'])))
            continue
        # differing / unmatched
        pg['word_diffs']+=1
        fa=flat_tokens(a['canon']) if a else []; fd=flat_tokens(d['canon']) if d else []
        ca=collections.Counter(key(x) for x in fa); cd=collections.Counter(key(x) for x in fd)
        ao=list((ca-cd).elements()); do=list((cd-ca).elements())
        back={key(x):x for x in fa+fd}
        aoT=[back[k] for k in ao]; doT=[back[k] for k in do]
        FAM={0x64B:0x64E,0x64C:0x64F,0x64D:0x650}
        tan_swap=0
        if a and d:
            ams=[x for l,m in a['canon'] for x in m]; dms=[x for l,m in d['canon'] for x in m]
            if any(x in (0x6E2,0x6ED) for x in ams) and any(x in (0x6E2,0x6ED) for x in dms):
                for t in list(aoT):
                    if t[0]=='M' and isinstance(t[1],int) and t[1] in FAM and ('M',FAM[t[1]]) in doT:
                        aoT.remove(t); doT.remove(('M',FAM[t[1]])); tan_swap+=1
        classes=set(cls_token(*x) for x in aoT+doT)
        if tan_swap: classes.add('tanween')
        if a is None or d is None: classes={'word_missing_on_one_side'}
        elif not aoT and not doT and not tan_swap: classes={'order_or_attachment_only'}
        if classes<= {'waqf'}: label='waqf_only'
        elif classes<={'small_madda'}: label='small_madda'
        elif classes<={'hamza'}: label='hamza_encoding'
        elif classes<={'tanween'}: label='tanween'
        elif classes<={'waqf','small_madda','hamza','tanween'}: label='multiple_known:'+'+'.join(sorted(classes))
        else: label='OTHER:'+'+'.join(sorted(classes))
        st=[]
        if tan_swap: st.append('tanween vowel (API) vs plain vowel (DK) with meem sign x%d'%tan_swap)
        if 0x6E2 in [x[1] for x in aoT if x[0]=='M'] and 0x6ED in [x[1] for x in doT if x[0]=='M']: st.append('API U+06E2 (iqlab meem) vs DK open-tanween/idgham U+06ED')
        if 0x654 in [x[1] for x in aoT if x[0]=='M'] and 0x655 in [x[1] for x in doT if x[0]=='M']: st.append('hamza above (API) vs hamza below (DK glyph)')
        if label.startswith('OTHER') or label.startswith('word_missing'): st.append('REQUIRES SPECIALIST: not waqf/madda/hamza/tanween')
        if a and a.get('wsplit'): st.append('API has this word split in two tokens (V2 words), DK has one word')
        subtype='; '.join(st)
        dk_unk=d['unk'] if d else []
        rows.append(dict(page=n,line=(a['line'] if a else d['line']),dk_line=(d['line'] if d else ''),
            sura=int(a['vk'].split(':')[0]) if a else '',ayah=int(a['vk'].split(':')[1]) if a else '',pos=a['pos'] if a else '',
            ref=a['txt'] if a else '',dk=' '.join(tok_label(*x) for x in fd) if d else '',
            ref_cp=' '.join(tok_label(*x) for x in fa),dk_cp=' '.join(tok_label(*x) for x in fd),
            api_only=' '.join(tok_label(*x) for x in aoT),dk_only=' '.join(tok_label(*x) for x in doT),
            cat='text',cls=label,subtype=subtype,detail=('DK glyph names: '+' '.join(d['names'])) if d else '',api_codes=a['code'] if a else '',dk_glyphs=' '.join(d['names']) if d else '',
            dk_text=''.join((chr(v) if isinstance(v,int) else '[%s]'%v) for k,v in d['tok'] if k in('L','M')) if d else '',
            dk_unmapped=';'.join(dk_unk)))
    # letter counts per page
    pg['api_letters']=sum(1 for w in aw if not w.get('dup') and w['ct']!='end' for l,m in w['canon'] if l not in('NUM','END','<nobase>'))
    pg['dk_letters']=sum(1 for w in dw for l,m in w['canon'] if l not in('NUM','END','<nobase>'))
    pg['api_waqf']=sum(1 for w in aw if not w.get('dup') for l,m in w['canon'] for x in m if x in WAQF)
    pg['dk_waqf']=sum(1 for w in dw for l,m in w['canon'] for x in m if x in WAQF)
    pg['api_marks']=sum(len(m) for w in aw if not w.get('dup') and w['ct']!='end' for l,m in w['canon'])
    pg['dk_marks']=sum(len(m) for w in dw for l,m in w['canon'] if l not in('END','NUM'))
    pg['api_sajdah_mark']=sum(1 for w in aw for l,m in w['canon'] for x in m if x==0x6E9)
    pg['dk_sajdah_mark']=sum(1 for w in dw for l,m in w['canon'] for x in m if x==0x6E9)
    pg['api_rub_glyph']=sum(1 for w in aw if w['txt']=='۞'); pg['dk_rub_glyph']=sum(1 for w in dw for l,m in w['canon'] for x in m if x==0x6DE)
    pg['api_line_count_text']=len({w['line'] for w in aw}); pg['dk_line_count_text']=len({w['line'] for w in dw})
    # line distribution: words per line
    la=collections.Counter(w['line'] for w in aw); ld=collections.Counter(w['line'] for w in dw)
    pg['lines_wordcount_differ']=sorted(k for k in set(la)|set(ld) if la[k]!=ld[k])
    pg['api_verses']=sorted({w['vk'] for w in aw},key=lambda s:tuple(map(int,s.split(':'))))
    pg['n_pairs_diff']=pg['word_diffs']
    pages.append(pg)
json.dump(dict(order_info=order_info,rows=rows,pages=pages,unknown=dict(unknowns),end_mismatch=end_mismatch,tot_words=tot_words,equal=equal),open('/tmp/claude-0/agentB/_raw.json','w'))
print('words (non-end,API side) compared',tot_words,'| equal pairs incl ends',equal)
tr=[r for r in rows if r['cat']=='text']
print('text diff rows',len(tr),'layout rows',len(rows)-len(tr))
print(collections.Counter(r['cls'] for r in tr).most_common())
print('unknown DK glyph mappings',dict(unknowns))
print('END digit mismatches',end_mismatch[:10],len(end_mismatch))
print('pages with 0 text diffs', sum(1 for p in pages if p['word_diffs']==0))
