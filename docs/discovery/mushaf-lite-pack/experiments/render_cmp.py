import json,sys,numpy as np,freetype,uharfbuzz as hb
from PIL import Image
n=int(sys.argv[1]); W=1000; LH=round(W*232/1440)
V2=f'/tmp/claude-0/study/v2all/p{n}.ttf'; HF='/tmp/claude-0/agentA/repos/mushaf-text/mushaf/src/main/res/font/uthmanic_hafs.ttf'
d=json.load(open(f'api/p{n}.json')); lines={}
for v in d['verses']:
    for w in v['words']:
        if (w.get('v2_page') or w['page_number'])==n: lines.setdefault(w['line_number'],[]).append(w)
for k in lines: lines[k].sort(key=lambda w:w['position'])
FL=freetype.FT_LOAD_RENDER|freetype.FT_LOAD_NO_HINTING|freetype.FT_LOAD_NO_AUTOHINT|freetype.FT_LOAD_NO_BITMAP
def blit(canvas,face,gid_or_char,x,base,px,is_gid):
    face.set_pixel_sizes(0,px)
    if is_gid: face.load_glyph(gid_or_char,FL)
    else: face.load_char(gid_or_char,FL)
    g=face.glyph; b=g.bitmap
    if not b.rows: return g.advance.x/64
    a=np.array(b.buffer,dtype=np.uint8).reshape(b.rows,b.pitch)[:,:b.width]
    x0=int(round(x+g.bitmap_left)); y0=int(round(base-g.bitmap_top))
    h,w=a.shape; H,Wc=canvas.shape
    xs,ys=max(0,x0),max(0,y0); xe,ye=min(Wc,x0+w),min(H,y0+h)
    if xe>xs and ye>ys: canvas[ys:ye,xs:xe]=np.maximum(canvas[ys:ye,xs:xe],a[ys-y0:ye-y0,xs-x0:xe-x0])
    return g.advance.x/64
# ---- V2
f2=freetype.Face(V2); upem2=f2.units_per_EM
adv=lambda ch: (lambda: (f2.load_char(ch,freetype.FT_LOAD_NO_SCALE|freetype.FT_LOAD_NO_HINTING), f2.glyph.advance.x)[1])()
nat2={k:sum(adv(c) for w in ws for c in w['code_v2']) for k,ws in lines.items()}
px2=W/ (max(nat2.values())/upem2)
H=LH*15+20; c2=np.zeros((H,W),np.uint8)
for k,ws in lines.items():
    x=W-(W-nat2[k]/upem2*px2)/2 if k in (1,2) and n in (1,2) else W; base=(k-1)*LH+int(LH*0.72)+10
    for w in ws:
        for ch in w['code_v2']:
            x-=adv(ch)/upem2*px2
            blit(c2,f2,ch,x,base,int(round(px2)),False)
# ---- Hafs: per-word shaping, gaps stretched to fit the widest line at the size that makes the widest natural line fit W
fh=freetype.Face(HF); uh=fh.units_per_EM; blob=hb.Blob.from_file_path(HF); face=hb.Face(blob); font=hb.Font(face)
def shape(txt):
    buf=hb.Buffer(); buf.add_str(txt); buf.direction='rtl'; buf.script='Arab'; buf.language='ar'; hb.shape(font,buf,{}) ; return buf.glyph_infos,buf.glyph_positions
words={}
for k,ws in lines.items():
    words[k]=[shape(w['text_uthmani']) for w in ws]
wid=lambda sh: sum(p.x_advance for p in sh[1])
space=0.62*(lambda: wid(shape(' ')))()
nat={k:sum(wid(s) for s in v)+space*(len(v)-1) for k,v in words.items()}
pxh=W/(max(nat.values())/uh)
c1=np.zeros((H,W),np.uint8); stretch=[]
for k,v in words.items():
    gaps=len(v)-1; slack=W/pxh*uh-nat[k]; extra=slack/gaps if gaps else 0
    stretch.append((slack/(space*gaps) if gaps else 0)+1)
    x=W; base=(k-1)*LH+int(LH*0.72)+10
    for sh in v:
        xl=x-wid(sh)/uh*pxh; pen=0.0
        for info,pos in zip(*sh):
            blit(c1,fh,info.codepoint,xl+pen+pos.x_offset/uh*pxh,base-pos.y_offset/uh*pxh,int(round(pxh)),True)
            pen+=pos.x_advance/uh*pxh
        x=xl-(space+extra)/uh*pxh
st=np.array(stretch)
print(f'page {n}: V2 em={px2:.1f}px Hafs em={pxh:.1f}px ({100*pxh/px2:.0f}% of V2 size); gap multiplier median {np.median(st):.2f} p90 {np.percentile(st,90):.2f} max {st.max():.2f}')
img=Image.new('L',(2*W+40,H),255)
for i,c in enumerate((c2,c1)):
    img.paste(Image.fromarray(255-c),(i*(W+40),0))
img.save(f'cmp_p{n}.png'); print('saved',f'cmp_p{n}.png',img.size)
