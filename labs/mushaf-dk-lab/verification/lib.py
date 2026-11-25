"""Shared library: DK glyph reconstruction + API tokenisation + canonical form.
Reference edition: QF API text_uthmani + code_v2 page/line layout (V2 / 1421H Madinah mushaf).
Only code-point tables (encoding metadata) are typed here; no Quran text."""
import json,collections,unicodedata
S2='/tmp/claude-0/study2/'
D=json.load(open(S2+'dk/madina.json')); G=D['glyphs']; P=D['pages']
GN={int(k):g['name'] for k,g in G.items()}

MARKCP={'fatha':0x64E,'damma':0x64F,'kasra':0x650,'fathatan':0x64B,'dammatan':0x64C,'kasratan':0x64D,'shadda':0x651,'sukun':0x652,
 'maddahabove':0x653,'hamzaabove':0x654,'hamzabelow':0x655,'smallalef':0x670,'wasla':0x671,'smallhighseen':0x6DC,'smallhighroundedzero':0x6DF,
 'rectangularzero':0x6E0,'headkhah':0x6E1,'meemiqlab':0x6E2,'smalllowseen':0x6E3,'smallwaw':0x6E5,'smallyeh':0x6E6,'smallhighyeh':0x6E7,
 'smallhighnoon':0x6E8,'placeofsajdah':0x6E9,'roundedfilledlow':0x6EA,'roundedfilledhigh':0x6EC,'smalllowmeem':0x6ED,'smallhighwaw':0x8F3,
 'minikaf':0x8F4,'rubelhizb':0x6DE,
 'waqf.sad':0x6D6,'waqf.qaf':0x6D7,'waqf.meem':0x6D8,'waqf.lam':0x6D9,'waqf.jeem':0x6DA,'waqf.smallhighthreedots':0x6DB}
TANIDG={'fathatanidgham':0x64B,'dammatanidgham':0x64C,'kasratanidgham':0x64D}
DOTS={'onedotup':'1u','onedotdown':'1d','twodotsup':'2u','twodotsdown':'2d','three_dots':'3','one_dot':'1','two_dots':'2'}
YA=0x649  # "dotless ya" class: U+0649, U+06CC and the seat of U+0626 fold here; U+064A kept apart
L={('ain',''):0x639,('ain','1u'):0x63A,('alef',''):0x627,('alefmaksura',''):YA,('dal',''):0x62F,('dal','1u'):0x630,('reh',''):0x631,('reh','1u'):0x632,
 ('sad',''):0x635,('sad','1u'):0x636,('tah',''):0x637,('tah','1u'):0x638,('seen',''):0x633,('seen','3'):0x634,('hah',''):0x62D,('hah','1d'):0x62C,('hah','1u'):0x62E,
 ('feh','1u'):0x641,('fehshape','1u'):0x641,('fehshape','2u'):0x642,('qaf','2u'):0x642,('kaf',''):0x643,('lam',''):0x644,('meem',''):0x645,('noon','1u'):0x646,
 ('heh',''):0x647,('heh','2u'):0x629,('waw',''):0x648,('behshape','1d'):0x628,('behshape','2u'):0x62A,('behshape','3'):0x62B,('behshape','1u'):0x646,
 ('behshape','2d'):0x64A,('yehshape','2d'):0x64A,('yehshape',''):YA,('behshape',''):YA,('hamza',''):0x621}
SPECIAL_ONGLYPH={'alef.maddahabove.isol':[0x627,0x653],'alef.hamzaabove.isol':[0x627,0x654],'waw.hamzaabove.isol':[0x648,0x654],'alef.hamzabelow.isol':[0x627,0x655],
 'alefmaksura.hamzaabove.isol':[YA,0x654],'alef.wasla.isol':[0x627,0x671]}
DK_SKIP={'tatweel','tatweel.fina','space','space.ii','cgj','zwj','null','notdef','linefeed','expaform.i','expaform.ii'}
LIGS=('lambeh','behmediyehfina','behsinna')

def dk_word_tokens(names):
    out=[]; unknown=[]
    for n in names:
        base=n.split('.')[0]
        if n in DK_SKIP or base in ('tatweel','space','cgj','zwj','expaform'): continue
        if base.startswith('endofaya'): out.append({'t':'END','v':int(base[8:] or 0)}); continue
        if base.endswith('indic'): out.append({'t':'NUM','n':n}); continue
        if base in DOTS and n==base:
            for o in reversed(out):
                if o['t'] in('L','LIG') and o.get('dotable'): o['dots'].append(DOTS[base]); break
            else: unknown.append('orphan-dot:'+n)
            continue
        if n in MARKCP or base in MARKCP:
            key=n if n in MARKCP else base
            out.append({'t':'M','cp':MARKCP[key]}); continue
        if base in TANIDG:
            out.append({'t':'M','cp':TANIDG[base]}); out.append({'t':'M','cp':0x6ED}); continue
        if n in SPECIAL_ONGLYPH:
            cs=SPECIAL_ONGLYPH[n]; out.append({'t':'L','fixed':cs[0],'dots':[],'dotable':False})
            for m in cs[1:]: out.append({'t':'M','cp':m})
            continue
        if base=='allah':
            for c in (0x627,0x644,0x644,0x647): out.append({'t':'L','fixed':c,'dots':[],'dotable':False})
            continue
        if base=='lamalef':
            out.append({'t':'L','fixed':0x644,'dots':[],'dotable':False}); out.append({'t':'L','fixed':0x627,'dots':[],'dotable':False}); continue
        if base in LIGS:
            out.append({'t':'LIG','n':base,'dots':[],'dotable':True}); continue
        parts=n.split('.'); emb=[DOTS[p] for p in parts[1:] if p in DOTS]
        out.append({'t':'L','base':base,'dots':emb,'dotable':True})
    res=[]
    for o in out:
        if o['t']=='L':
            if 'fixed' in o: cp=o['fixed']
            else:
                sig=''.join(sorted(o['dots']))
                cp=L.get((o['base'],sig))
                if cp is None: unknown.append('letter?%s+%s'%(o['base'],sig)); cp='?%s+%s'%(o['base'],sig)
            res.append(('L',cp))
        elif o['t']=='LIG': res.append(('L','LIG:%s+%s'%(o['n'],''.join(sorted(o['dots'])))))
        elif o['t']=='M': res.append(('M',o['cp']))
        elif o['t']=='END': res.append(('END',o['v']))
        else: res.append(('NUM',o['n']))
    return res,unknown

API_DECOMP={0x622:[0x627,0x653],0x623:[0x627,0x654],0x624:[0x648,0x654],0x625:[0x627,0x655],0x626:[YA,0x654],0x671:[0x627,0x671]}
def api_word_tokens(txt):
    res=[]
    for ch in txt:
        o=ord(ch)
        if o in (0x640,0x200C,0x200D,0x200E,0x200F,0x20): continue
        if o in API_DECOMP:
            cs=API_DECOMP[o]; res.append(('L',cs[0]))
            for m in cs[1:]: res.append(('M',m))
        elif o==0x6CC: res.append(('L',YA))
        elif (0x621<=o<=0x64A) or o==0x6A9: res.append(('L',o))
        elif 0x660<=o<=0x669: res.append(('NUM',o))
        else: res.append(('M',o))
    return res
TAN={0x64B,0x64C,0x64D}
def canon(toks):
    """-> list of (letter, tuple(marks)) ; marks sorted; tanween+small-meem(U+06E2/U+06ED) folded into ('TANMEEM',vowel)."""
    out=[]; cur=None
    for kind,v in toks:
        if kind=='L': cur=[v,[]]; out.append(cur)
        elif kind=='M':
            if cur is None: cur=['<nobase>',[]]; out.append(cur)
            cur[1].append(v)
        elif kind in('NUM','END'): out.append([kind,[v]]); cur=None
    res=[]
    for l,m in out:
        r=[]; j=0
        while j<len(m):
            r.append(m[j]); j+=1
        r=sorted(r,key=lambda x:(1,x[1]) if isinstance(x,tuple) else (0,x) if isinstance(x,int) else (2,0))
        res.append((l,tuple(r)))
    return res
def key(c): return repr(c)
def tok_label(kind,x):
    if isinstance(x,tuple): return 'TANWEEN+MEEMSIGN(U+%04X + U+06E2/U+06ED)'%x[1]
    if isinstance(x,str): return x
    return 'U+%04X'%x
def flat_tokens(c):
    o=[]
    for l,m in c:
        o.append(('L',l))
        o.extend(('M',x) for x in m)
    return o

def api_words(api,n):
    """words of page n in V2 layout (v2_page==n) incl. verse metadata; leading U+06DE split into its own pseudo-word."""
    seen=set(); words=[]
    for m in (n-1,n,n+1):
        if 1<=m<=604:
            for v in api[m]['verses']:
                for w in v['words']:
                    if (w.get('v2_page') or w['page_number'])==n:
                        k=(v['verse_key'],w['position'])
                        if k in seen: continue
                        seen.add(k)
                        base=dict(vk=v['verse_key'],pos=w['position'],ct=w['char_type_name'],line=w['line_number'],code=w['code_v2'],
                                  juz=v['juz_number'],hizb=v['hizb_number'],rub=v['rub_el_hizb_number'],sajdah=v['sajdah_number'],vnum=v['verse_number'],wid=w['id'])
                        txt=w['text_uthmani']
                        if ' ' in txt:
                            parts=txt.split(' ')
                            for i,pp in enumerate(parts):
                                d=dict(base); d['txt']=pp; d['sub']=i; d['code']=w['code_v2'] if i==len(parts)-1 else ''; words.append(d)
                        else:
                            d=dict(base); d['txt']=txt; d['sub']=0; words.append(d)
    words.sort(key=lambda w:(w['line'],int(w['vk'].split(':')[0]),int(w['vk'].split(':')[1]),w['pos'],w['sub']))
    return words
def dk_words(n):
    words=[]
    for li,Ln in enumerate(P[n-1]['lines'],start=1):
        if Ln['type']!=0: continue
        cur=[]
        for g in Ln['glyphs']:
            if g['codepoint']==32:
                if cur: words.append(dict(line=li,names=cur)); cur=[]
            else:
                nm=GN[g['codepoint']]
                if nm=='rubelhizb':   # DK glues the rub-el-hizb sign to the next word; API has it as a separate token
                    if cur: words.append(dict(line=li,names=cur)); cur=[]
                    words.append(dict(line=li,names=[nm]))
                else: cur.append(nm)
        if cur: words.append(dict(line=li,names=cur))
    return words
