"""Regenerates the launcher icon (adaptive vector, monochrome, legacy PNGs, notification icon) from docs/motion/mark.json.
Needs: python3 + playwright (Chromium) for the PNGs.   Run: python3 build_assets.py"""
import json, os
from svgmark import M, d_of, layer_d, C, HERE
W,H=M['w'],M['h']
# bbox of the mark (vertices only is enough)
xs=[p[0] for l in ('paper','leaf','gold','goldlight','stem') for c in M['layers'][l] for p in c['v']]
ys=[p[1] for l in ('paper','leaf','gold','goldlight','stem') for c in M['layers'][l] for p in c['v']]
BX0,BX1,BY0,BY1=min(xs),max(xs),min(ys),max(ys); BCX=(BX0+BX1)/2; BCY=(BY0+BY1)/2; BW=BX1-BX0; BH=BY1-BY0
print('bbox',BX0,BX1,BY0,BY1,BW,BH)
OUT=os.path.join(HERE,'..','..','app','src','main','res'); os.makedirs(OUT,exist_ok=True)
def mark_group(scale,cx,cy,mono=None):
    """svg <g> placing the mark centred at (cx,cy) with width scale*BW."""
    t=f'translate({cx-scale*BCX:.2f},{cy-scale*BCY:.2f}) scale({scale:.5f})'
    if mono:
        s=f'<path d="{layer_d("paper")}{layer_d("leaf")}" fill="{mono}" fill-rule="evenodd"/>'
        s+=f'<path d="{layer_d("goldlight")}{layer_d("gold")}{layer_d("stem")}" fill="{mono}"/>'
    else:
        s=f'<path d="{layer_d("paper")}" fill="{C["paper"]}"/><path d="{layer_d("leaf")}" fill="{C["leaf"]}" fill-rule="evenodd"/>'
        s+=f'<path d="{layer_d("goldlight")}" fill="{C["gl"]}"/><path d="{layer_d("gold")}" fill="{C["gold"]}"/><path d="{layer_d("stem")}" fill="{C["gold"]}"/>'
    return f'<g transform="{t}">{s}</g>'
GRAD='<defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#42502D"/><stop offset="1" stop-color="#2C381E"/></linearGradient></defs>'
def svg_icon(size,shape,scale_w):
    s=f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {size} {size}" width="{size}" height="{size}">{GRAD}'
    if shape=='squircle': s+=f'<rect width="{size}" height="{size}" rx="{size*0.2:.1f}" fill="url(#g)"/>'
    elif shape=='circle': s+=f'<circle cx="{size/2}" cy="{size/2}" r="{size/2}" fill="url(#g)"/>'
    else: s+=f'<rect width="{size}" height="{size}" fill="url(#g)"/>'
    s+=mark_group(scale_w*size/BW,size/2,size/2+size*0.005)+'</svg>'
    return s
open(os.path.join(HERE,'logo_icon.svg'),'w').write(svg_icon(512,'squircle',0.74))
# ---- Android vector drawables (viewport 108) ----
def vd_path(name,color,fill_type=None):
    d=layer_d(name); ft=f' android:fillType="{fill_type}"' if fill_type else ''
    return d
SC=54.0/BW   # mark width 54 of 108 (safe zone 66)
tx=54-SC*BCX; ty=54+0.5-SC*BCY
def path_el(d,color,evenodd=False):
    return f'        <path android:fillColor="{color}"{" android:fillType=\"evenOdd\"" if evenodd else ""}\n            android:pathData="{d}" />\n'
grp=f'    <group android:translateX="{tx:.3f}" android:translateY="{ty:.3f}" android:scaleX="{SC:.5f}" android:scaleY="{SC:.5f}">\n'
fg='<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'+grp
fg+=path_el(layer_d('paper'),C['paper'])+path_el(layer_d('leaf'),C['leaf'],True)+path_el(layer_d('goldlight'),C['gl'])+path_el(layer_d('gold'),C['gold'])+path_el(layer_d('stem'),C['gold'])
fg+='    </group>\n</vector>\n'
mono='<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'+grp
mono+=path_el(layer_d('paper')+layer_d('leaf'),'#FF000000',True)+path_el(layer_d('goldlight')+layer_d('gold')+layer_d('stem'),'#FF000000')
mono+='    </group>\n</vector>\n'
bgx='''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">
    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient android:type="linear" android:startX="54" android:startY="0" android:endX="54" android:endY="108">
                <item android:offset="0" android:color="#FF42502D" />
                <item android:offset="1" android:color="#FF2C381E" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
'''
adaptive='''<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_monochrome" />
</adaptive-icon>
'''
# notification icon 24dp: book + branch silhouette (mono), scale to 20 wide
SN=20.0/BW
notif='<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android"\n    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
notif+=f'    <group android:translateX="{12-SN*BCX:.3f}" android:translateY="{12-SN*BCY:.3f}" android:scaleX="{SN:.5f}" android:scaleY="{SN:.5f}">\n'
notif+=path_el(layer_d('paper')+layer_d('leaf'),'#FFFFFFFF',True)+path_el(layer_d('goldlight')+layer_d('gold')+layer_d('stem'),'#FFFFFFFF')+'    </group>\n</vector>\n'
os.makedirs(f'{OUT}/drawable',exist_ok=True); os.makedirs(f'{OUT}/mipmap-anydpi-v26',exist_ok=True)
for n,t in (('ic_launcher_foreground.xml',fg),('ic_launcher_monochrome.xml',mono),('ic_launcher_background.xml',bgx),('ic_stat_halaqati.xml',notif)): open(f'{OUT}/drawable/{n}','w').write(t)
for n in ('ic_launcher.xml','ic_launcher_round.xml'): open(f'{OUT}/mipmap-anydpi-v26/{n}','w').write(adaptive)
# ---- PNGs ----
from playwright.sync_api import sync_playwright
sizes={'mdpi':48,'hdpi':72,'xhdpi':96,'xxhdpi':144,'xxxhdpi':192}
with sync_playwright() as p:
    exe="/opt/pw-browsers/chromium-1194/chrome-linux/chrome"
    b=p.chromium.launch(**({"executable_path":exe} if os.path.exists(exe) else {}),args=["--no-sandbox"])
    def shot(svg,size,path):
        pg=b.new_page(viewport={"width":size,"height":size}); pg.set_content(f'<body style="margin:0;background:transparent">{svg}</body>'); pg.screenshot(path=path,omit_background=True); pg.close()
    for d,s in sizes.items():
        os.makedirs(f'{OUT}/mipmap-{d}',exist_ok=True)
        shot(svg_icon(s,'squircle',0.80),s,f'{OUT}/mipmap-{d}/ic_launcher.png')
        shot(svg_icon(s,'circle',0.64),s,f'{OUT}/mipmap-{d}/ic_launcher_round.png')
    os.makedirs(f'{OUT}/drawable-nodpi',exist_ok=True)
    shot(svg_icon(512,'squircle',0.74),512,f'{OUT}/drawable-nodpi/launcher_legacy.png')
    shot(svg_icon(512,'full',0.62),512,os.path.join(HERE,'play_store_icon_512.png'))
    b.close()
print('done')
