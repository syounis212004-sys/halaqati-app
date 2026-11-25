"""Render a mushaf page from DigitalKhatt's precomputed layout (madina.json) to SVG, following precomputed/layout.ts (MIT).
Usage: render_dk_svg.py PAGE WIDTH_PX OUT.svg"""
import json,sys
D=json.load(open('/tmp/claude-0/study2/dk/madina.json'))
G=D['glyphs']; P=D['pages']
def glyph_d(cp,lt,rt):
    gi=G[str(cp)]; lim=gi.get('limits') or [0,0,0,0]
    lt=max(lim[0],min(lim[1],lt or 0)); rt=max(lim[2],min(lim[3],rt or 0))
    ls=(lt/lim[0] if lim[0] else 0) if lt<0 else ((lt/lim[1] if lim[1] else 0) if lt>0 else 0)
    rs=(rt/lim[2] if lim[2] else 0) if rt<0 else ((rt/lim[3] if lim[3] else 0) if rt>0 else 0)
    def interp(v,i,j,k):
        x=v
        if lt<0: x+=(gi['minLeft'][i]['path'][j][k]-v)*ls
        elif lt>0: x+=(gi['maxLeft'][i]['path'][j][k]-v)*ls
        if rt<0: x+=(gi['minRight'][i]['path'][j][k]-v)*rs
        elif rt>0: x+=(gi['maxRight'][i]['path'][j][k]-v)*rs
        return x
    d=[]
    for i,dp in enumerate(gi['default']):
        for j,e in enumerate(dp['path']):
            if len(e)==2: d.append('M%.2f %.2f'%(interp(e[0],i,j,0),interp(e[1],i,j,1)))
            elif len(e)==6: d.append('C'+','.join('%.2f %.2f'%(interp(e[a],i,j,a),interp(e[a+1],i,j,a+1)) for a in (0,2,4)))
    return ''.join(d)
def page_svg(n,W):
    lines=P[n-1]['lines']; widths=[]; data=[]
    for L in lines:
        pos=-L.get('x',0); items=[]
        for g in L['glyphs']:
            pos-=g.get('x_advance',0)
            items.append((pos+g.get('x_offset',0),g.get('y_offset',0),glyph_d(g['codepoint'],g.get('lefttatweel',0),g.get('righttatweel',0))))
        xs=L.get('xscale',1) or 1; widths.append(-pos*xs); data.append((items,xs))
    s=W/max(widths); LH=W*232/1440; H=int(LH*len(lines)+20)
    out=[f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}"><rect width="100%" height="100%" fill="white"/>']
    for idx,(items,xs) in enumerate(data):
        base=10+idx*LH+LH*0.72
        out.append(f'<g transform="translate({W},{base:.2f}) scale({s*xs:.6f},{-s:.6f})" fill="black">')
        for x,y,d in items:
            if d: out.append(f'<path transform="translate({x:.2f} {y:.2f})" d="{d}"/>')
        out.append('</g>')
    out.append('</svg>'); return '\n'.join(out),s
if __name__=='__main__':
    n,W,o=int(sys.argv[1]),int(sys.argv[2]),sys.argv[3]; svg,s=page_svg(n,W); open(o,'w').write(svg); print(n,'scale',round(s,4),'bytes',len(svg))
