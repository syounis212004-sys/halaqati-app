"""Integrity check: DigitalKhatt layout (madina.json) vs Quran Foundation API text, word by word, on LETTER COUNT and MARK sequence
(harakat, shadda, sukun, maddah, small letters, waqf signs). Dots are not compared (they are separate glyphs in DK).
Prints counts and mismatch examples as page:line:verse:position with code points only (no Quran text)."""
import json,collections,unicodedata,sys
D=json.load(open('/tmp/claude-0/study2/dk/madina.json')); G=D['glyphs']; P=D['pages']
MARK_API={0x64B:'fathatan',0x64C:'dammatan',0x64D:'kasratan',0x64E:'fatha',0x64F:'damma',0x650:'kasra',0x651:'shadda',0x652:'sukun',0x653:'maddahabove',0x654:'hamzaabove',0x655:'hamzabelow',0x670:'smallalef',
 0x6D6:'waqf.sad',0x6D7:'waqf.qaf',0x6D8:'waqf.meem',0x6D9:'waqf.lam',0x6DA:'waqf.jeem',0x6DB:'waqf.smallhighthreedots',0x6DC:'smallhighseen',0x6DF:'smallhighroundedzero',0x6E0:'rectangularzero',0x6E1:'headkhah',0x6E2:'meemiqlab',0x6E3:'smalllowseen',0x6E5:'smallwaw',0x6E6:'smallyeh',0x6E7:'smallhighyeh',0x6E8:'smallhighnoon',0x6E9:'placeofsajdah',0x6EA:'roundedfilledlow',0x6EB:'roundedfilledhigh',0x6EC:'roundedfilledhigh',0x6ED:'smalllowmeem'}
DECOMP={0x622:[(0x627,0),(0x653,1)],0x623:[(0x627,0),(0x654,1)],0x624:[(0x648,0),(0x654,1)],0x625:[(0x627,0),(0x655,1)],0x626:[(0x64A,0),(0x0,2)],0x671:[(0x627,0),(0x0,1)]}
DK_MARKS={'fatha','damma','kasra','fathatan','dammatan','kasratan','shadda','sukun','maddahabove','hamzaabove','hamzabelow','smallalef','wasla','smallhighseen','smallhighroundedzero','rectangularzero','headkhah','meemiqlab','smalllowseen','smallwaw','smallyeh','smallhighyeh','smallhighnoon','placeofsajdah','roundedfilledhigh','roundedfilledlow','smalllowmeem','minikaf','waqf'}
DK_IGNORE={'rubelhizb','onedotup','onedotdown','twodotsup','twodotsdown','one_dot','two_dots','three_dots','tatweel','space','cgj','zwj','null','notdef','linefeed','expaform'}
UNITS={'allah':4,'lamalef':2,'lambeh':2,'behmediyehfina':2}
def dk_tokens(names):
    marks=[];letters=0
    for n in names:
        b=n.split('.')[0]
        if b.endswith('idgham'): b=b[:-6]; marks.append(b); marks.append('MEEMSIGN'); continue
        if b=='waqf': marks.append(n if n.startswith('waqf.') else 'waqf'); continue
        if b in DK_IGNORE or b.startswith('endofaya') or b.endswith('indic'): continue
        if b in DK_MARKS: marks.append(b); continue
        letters+=UNITS.get(b,1)
    return letters,marks
def api_tokens(txt):
    marks=[];letters=0;other=[]
    for ch in txt:
        o=ord(ch)
        if o in DECOMP:
            for c2,k in DECOMP[o]:
                if k==0: letters+=1
                elif k==2: marks.append('HAMZASEAT')
                elif c2: marks.append(MARK_API[c2])
            if o==0x671: marks.append('wasla')
        elif o==0x6E2: marks.append('IQLAB')
        elif o==0x6ED: marks.append('MEEMSIGN')
        elif o in MARK_API: marks.append(MARK_API[o])
        elif o==0x640 or o in (0x200C,0x200D,0x200F,0x200E,0x20): continue
        elif unicodedata.category(ch)=='Lo' or o in (0x6CC,0x6A9,0x6D5): letters+=1
        elif unicodedata.category(ch).startswith('M') or o in (0x6D6,): other.append(hex(o))
        else: other.append(hex(o))
    out=[]
    for m in marks:
        if m=='IQLAB' and out and out[-1] in ('fathatan','dammatan','kasratan'):
            out[-1]={'fathatan':'fatha','dammatan':'damma','kasratan':'kasra'}[out[-1]]; out.append('meemiqlab')
        else: out.append(m if m!='IQLAB' else 'meemiqlab')
    return letters,out,other
api={n:json.load(open(f'/tmp/claude-0/study2/api/p{n}.json')) for n in range(1,605)}
def api_lines(n):
    seen=set(); lines=collections.defaultdict(list)
    for m in (n-1,n,n+1):
        if 1<=m<=604:
            for v in api[m]['verses']:
                for w in v['words']:
                    if (w.get('v2_page') or w['page_number'])==n:
                        k=(v['verse_key'],w['position'])
                        if k in seen: continue
                        seen.add(k); lines[w['line_number']].append((v['verse_key'],w['position'],w['char_type_name'],w['text_uthmani']))
    return lines
def dk_words(line):
    words=[];cur=[]
    for g in line['glyphs']:
        if g['codepoint']==32:
            if cur: words.append(cur); cur=[]
        else: cur.append(G[str(g['codepoint'])]['name'])
    if cur: words.append(cur)
    return words

# --- final pass: unify the tanween + small-meem encodings and list the residual words (no Quran text printed) ---
VOW={'fathatan':'fatha','dammatan':'damma','kasratan':'kasra','fatha':'fatha','damma':'damma','kasra':'kasra'}
def canon(marks):
    out=[]; i=0; m=list(marks)
    while i<len(m):
        x=m[i]
        if x in VOW and i+1<len(m) and m[i+1] in ('MEEMSIGN','meemiqlab','smalllowmeem'): out.append('TANMEEM_'+VOW[x]); i+=2
        elif x in ('MEEMSIGN','meemiqlab','smalllowmeem') and out and out[-1] in VOW.values(): out[-1]='TANMEEM_'+out[-1]; i+=1
        else: out.append(x); i+=1
    return out
tot=ok=0; sig=collections.Counter(); where=collections.defaultdict(list); pages_clean=0
for n in range(1,605):
    al=api_lines(n); clean=True
    for li,L in enumerate(P[n-1]['lines'],start=1):
        dw=dk_words(L); aw=al.get(li,[])
        if len(dw)!=len(aw): continue
        for (vk,pos,ct,txt),names in zip(aw,dw):
            if ct=='end': continue
            tot+=1; la,ma,_=api_tokens(txt); ld,md=dk_tokens(names); ma2=canon(ma); md2=canon(md)
            for _ in range(ma2.count('HAMZASEAT')):
                ma2.remove('HAMZASEAT')
                for c in ('hamzabelow','hamzaabove'):
                    if c in md2: md2.remove(c); break
            if la==ld and sorted(ma2)==sorted(md2): ok+=1
            else:
                clean=False; ca,cd=collections.Counter(ma2),collections.Counter(md2)
                key=(ld-la,tuple(sorted((ca-cd).elements())),tuple(sorted((cd-ca).elements()))); sig[key]+=1; where[str(key)].append((n,li,vk,pos))
    pages_clean+=clean
print('words compared',tot,'| equal',ok,'(%.2f%%)'%(100*ok/tot),'| pages fully equal',pages_clean,'/604 | residual',sum(sig.values()))
for k,c in sig.most_common(12): print(c,k)
json.dump(where,open('/tmp/claude-0/study2/dk_text_residual.json','w'))
