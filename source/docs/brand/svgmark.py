import json, os
HERE=os.path.dirname(os.path.abspath(__file__))
M=json.load(open(os.path.join(HERE,'..','motion','mark.json')))
def d_of(c):
    v,i,o=c['v'],c['i'],c['o']; n=len(v)
    s=f"M{v[0][0]:.1f},{v[0][1]:.1f}"
    for k in range(n):
        a=(k+1)%n
        s+=f"C{v[k][0]+o[k][0]:.1f},{v[k][1]+o[k][1]:.1f} {v[a][0]+i[a][0]:.1f},{v[a][1]+i[a][1]:.1f} {v[a][0]:.1f},{v[a][1]:.1f}"
    return s+"Z"
def layer_d(name): return "".join(d_of(c) for c in M['layers'][name])
C=dict(bg1="#42502D",bg2="#2C381E",paper="#FBF5EA",leaf="#536236",gold="#BE994F",gl="#CCB075")
def mark_svg(bg=True):
    s=""
    s+=f'<path d="{layer_d("paper")}" fill="{C["paper"]}"/>'
    s+=f'<path d="{layer_d("leaf")}" fill="{C["leaf"]}" fill-rule="evenodd"/>'
    s+=f'<path d="{layer_d("goldlight")}" fill="{C["gl"]}"/>'
    s+=f'<path d="{layer_d("gold")}" fill="{C["gold"]}"/>'
    s+=f'<path d="{layer_d("stem")}" fill="{C["gold"]}"/>'
    return s
