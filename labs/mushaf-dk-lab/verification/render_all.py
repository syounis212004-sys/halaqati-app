"""Render line crops: DK (from madina.json outlines -> SVG -> Chromium PNG) and V2 (QCF2 page fonts via FreeType) for every page that has a difference."""
import json,sys,os,subprocess,collections
import numpy as np,freetype
from PIL import Image
sys.path.insert(0,'/tmp/claude-0/agentB')
raw=json.load(open('/tmp/claude-0/agentB/_raw.json')); rows=raw['rows']
need=collections.defaultdict(set)
for r in rows:
    need[r['page']].add(('v',int(r['line'])))
    need[r['page']].add(('d',int(r['dk_line'] or r['line'])))
pages=sorted(need)
W=1000; LH=W*232/1440
S2='/tmp/claude-0/study2/'
# --- DK svgs
if 'dk' in sys.argv:
    import render_dk_svg as R
    fl=[]
    for n in pages:
        svg,s=R.page_svg(n,W); f=f'/tmp/claude-0/agentB/svg/dk_p{n}.svg'; open(f,'w').write(svg); fl.append(f)
    for i in range(0,len(fl),100):
        subprocess.run(['node','/tmp/claude-0/study2/shot.js']+fl[i:i+100],check=True)
    print('dk pages rendered',len(fl))
def crop(img,line):
    y0=int(10+(line-1)*LH)-12; y1=int(10+line*LH)+4
    return img.crop((0,max(0,y0),W,min(img.height,y1)))
if 'dk' in sys.argv or 'crop' in sys.argv:
    for n in pages:
        im=Image.open(f'/tmp/claude-0/agentB/svg/dk_p{n}.png').convert('L')
        for k,l in need[n]:
            if k=='d': crop(im,l).save(f'/tmp/claude-0/agentB/img/dk_p{n}_l{l}.png',optimize=True)
# --- V2 via freetype
if 'v2' in sys.argv:
    FL=freetype.FT_LOAD_RENDER|freetype.FT_LOAD_NO_HINTING|freetype.FT_LOAD_NO_AUTOHINT|freetype.FT_LOAD_NO_BITMAP
    def blit(canvas,face,ch,x,base,px):
        face.set_pixel_sizes(0,px); face.load_char(ch,FL); g=face.glyph; b=g.bitmap
        if not b.rows: return
        a=np.array(b.buffer,dtype=np.uint8).reshape(b.rows,b.pitch)[:,:b.width]
        x0=int(round(x+g.bitmap_left)); y0=int(round(base-g.bitmap_top)); h,w=a.shape; H,Wc=canvas.shape
        xs,ys=max(0,x0),max(0,y0); xe,ye=min(Wc,x0+w),min(H,y0+h)
        if xe>xs and ye>ys: canvas[ys:ye,xs:xe]=np.maximum(canvas[ys:ye,xs:xe],a[ys-y0:ye-y0,xs-x0:xe-x0])
    for n in pages:
        lines={}; seen=set()
        for m in (n-1,n,n+1):
            if 1<=m<=604:
                d=json.load(open(S2+f'api/p{m}.json'))
                for v in d['verses']:
                    for w in v['words']:
                        if (w.get('v2_page') or w['page_number'])==n and (v['verse_key'],w['position']) not in seen:
                            seen.add((v['verse_key'],w['position'])); lines.setdefault(w['line_number'],[]).append((int(v['verse_key'].split(':')[0]),int(v['verse_key'].split(':')[1]),w['position'],w['code_v2']))
        f2=freetype.Face(f'/tmp/claude-0/study/v2all/p{n}.ttf'); upem=f2.units_per_EM
        if all(os.path.exists(f'/tmp/claude-0/agentB/img/v2_p{n}_l{l}.png') for k,l in need[n] if k=='v'): continue
        _c={}
        def adv(ch):
            if ch not in _c:
                f2.load_char(ch,freetype.FT_LOAD_NO_SCALE|freetype.FT_LOAD_NO_HINTING); _c[ch]=f2.glyph.advance.x
            return _c[ch]
        for k in lines: lines[k].sort()
        nat={k:sum(adv(c) for w in ws for c in w[3]) for k,ws in lines.items()}
        px=W/(max(nat.values())/upem)
        H=int(LH*15+30); cv=np.zeros((H,W),np.uint8)
        for k,ws in lines.items():
            x=W-(W-nat[k]/upem*px)/2 if k in (1,2) and n in (1,2) else W
            base=10+(k-1)*LH+LH*0.72
            for w in ws:
                for ch in w[3]:
                    x-=adv(ch)/upem*px; blit(cv,f2,ch,x,base,int(round(px)))
        im=Image.fromarray(255-cv)
        for k,l in need[n]:
            if k=='v': crop(im,l).save(f'/tmp/claude-0/agentB/img/v2_p{n}_l{l}.png',optimize=True)
    print('v2 done')
