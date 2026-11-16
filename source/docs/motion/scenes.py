"""
Halaqati "Eastern" motion scenes (RC27.15): ambient pattern, role art, milestone, sync and empty-state variants.

Motifs come from the Sharqiya center identity: the olive wreath, the Quran on its rehal with rays of light, the
mosque dome and minaret, the Rub el Hizb star, and the ring of the halaqa (eight students and one gold teacher).
Everything is drawn with the fill/stroke names "Primary" and "Accent" so the app can recolor it; on a colored band
the app passes onBand = true (Primary becomes onPrimary, Accent becomes a light gold).
"""
import math
import re

import generate as G
from generate import Comp, anim, fill, grp, prop, stroke, PAL

HEX = {"Primary": PAL["primary"], "Accent": PAL["accent"], "OnPrimary": "#FFFFFF"}


# ------------------------------------------------------------------ SVG path -> Lottie contours
def parse_path(d):
    toks = re.findall(r"[MmLlHhVvCcQqZz]|-?\d*\.?\d+(?:e-?\d+)?", d)
    out, cur, pos, start, i, cmd = [], None, (0.0, 0.0), (0.0, 0.0), 0, None

    def num():
        nonlocal i
        v = float(toks[i]); i += 1
        return v

    def begin(p):
        nonlocal cur
        cur = {"v": [list(p)], "i": [[0, 0]], "o": [[0, 0]], "c": False}
        out.append(cur)

    def line(p):
        cur["v"].append(list(p)); cur["i"].append([0, 0]); cur["o"].append([0, 0])

    def cubic(c1, c2, p):
        a = cur["v"][-1]
        cur["o"][-1] = [c1[0] - a[0], c1[1] - a[1]]
        cur["v"].append(list(p)); cur["i"].append([c2[0] - p[0], c2[1] - p[1]]); cur["o"].append([0, 0])

    while i < len(toks):
        if re.match(r"[A-Za-z]", toks[i]):
            cmd = toks[i]; i += 1
        rel, c = cmd.islower(), cmd.upper()
        ox, oy = pos if rel else (0.0, 0.0)
        if c == "M":
            p = (num() + ox, num() + oy); begin(p); pos = start = p; cmd = "l" if rel else "L"
        elif c == "L":
            p = (num() + ox, num() + oy); line(p); pos = p
        elif c == "H":
            p = (num() + ox, pos[1]); line(p); pos = p
        elif c == "V":
            p = (pos[0], num() + oy); line(p); pos = p
        elif c == "C":
            c1 = (num() + ox, num() + oy); c2 = (num() + ox, num() + oy); p = (num() + ox, num() + oy)
            cubic(c1, c2, p); pos = p
        elif c == "Q":
            q = (num() + ox, num() + oy); p = (num() + ox, num() + oy)
            cubic((pos[0] + 2 / 3 * (q[0] - pos[0]), pos[1] + 2 / 3 * (q[1] - pos[1])),
                  (p[0] + 2 / 3 * (q[0] - p[0]), p[1] + 2 / 3 * (q[1] - p[1])), p)
            pos = p
        elif c == "Z":
            cur["c"] = True
            v = cur["v"]
            if len(v) > 1 and abs(v[-1][0] - v[0][0]) < 1e-6 and abs(v[-1][1] - v[0][1]) < 1e-6:
                cur["i"][0] = cur["i"][-1]; v.pop(); cur["i"].pop(); cur["o"].pop()
            pos = start
    for c in out:
        for k in ("v", "i", "o"):
            c[k] = [[round(a, 2), round(b, 2)] for a, b in c[k]]
    return out


def sh(c):
    return {"ty": "sh", "nm": "Path", "ks": {"a": 0, "k": c}}


def ell(x, y, w, h=None):
    return {"ty": "el", "nm": "Ellipse", "d": 1, "s": prop([w, h or w]), "p": prop([x, y])}


def poly(points, closed=False):
    n = len(points)
    return sh({"i": [[0, 0]] * n, "o": [[0, 0]] * n,
               "v": [[round(a, 2), round(b, 2)] for a, b in points], "c": closed})


def art(name, geoms, fill_=None, line=None, trim=None, **t):
    """geoms: shapes or path strings. fill_=(role, opacity[, rule]); line=(role, width, opacity); t = transform args."""
    items = []
    for g in geoms:
        items += [sh(c) for c in parse_path(g)] if isinstance(g, str) else [g]
    if fill_:
        items.append(fill(HEX[fill_[0]], fill_[0], o=fill_[1], rule=(fill_[2] if len(fill_) > 2 else 1)))
    if line:
        items.append(stroke(HEX[line[0]], line[1], line[0], o=line[2]))
    if trim is not None:
        items.append({"ty": "tm", "nm": "Trim", "s": prop(0), "e": trim, "o": prop(0), "m": 1})
    return grp(name, items, **t)


# ------------------------------------------------------------------ animation helpers
def wave(op, lo, hi, phase=0, two=True, n=12):
    """Seamless cosine pulse lo..hi with its peak at `phase`. Returns an animated value (scalar or [v, v])."""
    keys = []
    for k in range(n + 1):
        t = round(op * k / n)
        v = lo + (hi - lo) * (1 + math.cos(2 * math.pi * (t - phase) / op)) / 2
        v = round(v, 3)
        keys.append((t, (v, v) if two else v, "lin"))
    return anim(keys)


def swing(op, amp, cycles=1):
    return anim([(round(op * k / (4 * cycles)), [0, amp, 0, -amp][k % 4], "inout") for k in range(4 * cycles)] + [(op, 0, "inout")])


def window(op, w0, w1, hi=100, fade=12):
    """Opacity 0, rises at w0, falls at w1, 0 again. Times must be strictly increasing."""
    keys = [(0, 0, "lin")]
    for t, v in ((w0, 0), (w0 + fade, hi), (w1 - fade, hi), (w1, 0), (op, 0)):
        if t > keys[-1][0]:
            keys.append((t, v, "lin"))
    return anim(keys)


def spin(op, deg):
    return anim([(0, 0, "lin"), (op, deg, "lin")])


# ------------------------------------------------------------------ shapes
def leaf_d(length, wid):
    return (f"M0,0 C{.25 * length:.1f},{-wid} {.7 * length:.1f},{-wid} {length},0 "
            f"C{.7 * length:.1f},{wid} {.25 * length:.1f},{wid} 0,0Z")


def star4_d(r):
    k = r * .22
    return (f"M0,{-r} L{k},{-k} L{r},0 L{k},{k} L0,{r} L{-k},{k} L{-r},0 L{-k},{-k} Z")


def rays_d(cx, cy, n, r0, r1, half):
    d = ""
    for k in range(n):
        a = 2 * math.pi * k / n
        p0 = (cx + r0 * math.cos(a), cy + r0 * math.sin(a))
        p1 = (cx + r1 * math.cos(a - half), cy + r1 * math.sin(a - half))
        p2 = (cx + r1 * math.cos(a + half), cy + r1 * math.sin(a + half))
        d += f"M{p0[0]:.1f},{p0[1]:.1f} L{p1[0]:.1f},{p1[1]:.1f} L{p2[0]:.1f},{p2[1]:.1f} Z "
    return d


def hizb_d(cx, cy, side):
    h = side / 2
    d = side * math.sqrt(2) / 2
    return [f"M{cx - h},{cy - h} L{cx + h},{cy - h} L{cx + h},{cy + h} L{cx - h},{cy + h} Z",
            f"M{cx},{cy - d} L{cx + d},{cy} L{cx},{cy + d} L{cx - d},{cy} Z"]


def sparkle(cx, cy, r, op, phase):
    return art("Sparkle", [star4_d(r)], fill_=("Accent", 100), p=(cx, cy), a=(0, 0),
               s=wave(op, 0, 100, phase), r=spin(op, 90))


def wreath(cx, cy, R, n=7, phi0=16, phi1=160, leaf=16, op=None, t0=None, sway=1.6, role="Primary", wid=2.6):
    """Two olive branches around a circle, open at the top (like the Eastern center seal).
    op -> looping idle (leaves breathe, ring sways). t0 -> one-shot entrance starting at that frame."""
    parts = []
    for side in (-1, 1):
        pts, phi = [], phi0
        while phi <= phi1 + .1:
            a = math.radians(phi)
            pts.append((cx + side * R * math.sin(a), cy + R * math.cos(a)))
            phi += 4
        trim = anim([(t0, 0, "inout"), (t0 + 36, 100, "inout")]) if t0 is not None else None
        for k in range(n):
            phi_k = phi0 + 10 + (phi1 - phi0 - 14) * k / max(1, n - 1)
            a = math.radians(phi_k)
            px, py = cx + side * R * math.sin(a), cy + R * math.cos(a)
            tang = math.degrees(math.atan2(-math.sin(a), side * math.cos(a)))
            for j, off in enumerate((-40, 40)):
                if t0 is not None:
                    sc = anim([(t0 + 6 + k * 3 + j, (0, 0), "back"), (t0 + 24 + k * 3 + j, (100, 100), "back")])
                elif op:
                    sc = wave(op, 92, 108, phase=(k * 17 + j * 40) % op)
                else:
                    sc = (100, 100)
                parts.append(art("Leaf", [leaf_d(leaf, leaf * .27)], fill_=(role, 100), p=(px, py), a=(0, 0), r=tang + off, s=sc))
        parts.append(art("Branch", [poly(pts)], line=(role, wid, 100), trim=trim))
    return grp("Wreath", parts, p=(cx, cy), a=(cx, cy), r=swing(op, sway) if op else 0)


def pages(top, spread, depth, name="Pages", tint=20):
    l = (f"M100,{top + 6} C88,{top - 6} 68,{top - 8} {100 - spread},{top} L{100 - spread},{top + depth} "
         f"C68,{top + depth - 8} 88,{top + depth - 6} 100,{top + depth + 8} Z")
    r = (f"M100,{top + 6} C112,{top - 6} 132,{top - 8} {100 + spread},{top} L{100 + spread},{top + depth} "
         f"C132,{top + depth - 8} 112,{top + depth - 6} 100,{top + depth + 8} Z")
    return [art(name, [l, r], fill_=("Primary", tint), line=("Primary", 3, 100)),
            art("Spine", [poly([(100, top + 6), (100, top + depth + 8)])], line=("Accent", 3, 100))]


def text_lines(top, spread, op=None):
    out = []
    for k in range(3):
        y = top + 15 + k * 9
        out.append(art("Line", [poly([(100 - spread + 10, y), (100 - 14, y + 5)]), poly([(100 + 14, y + 5), (100 + spread - 10, y)])],
                       line=("Primary", 2.4, 55)))
    return out


def layer(c, shapes):
    c.layer("Art", shapes, p=(0, 0))


# ------------------------------------------------------------------ scenes
def build_ambient():
    op = 1200
    c = Comp("Halaqati ambient", 400, op)
    ring = []
    for k in range(8):
        a = math.radians(45 * k - 90)
        ring.append(art("Teacher" if k == 0 else "Student", [ell(0, 0, 19 if k == 0 else 13)],
                        fill_=("Accent" if k == 0 else "Primary", 100), p=(200 + 170 * math.cos(a), 200 + 170 * math.sin(a)), a=(0, 0)))
    outer = hizb_d(200, 200, 150)
    inner = hizb_d(200, 200, 96)
    layer(c, [
        grp("Halaqa", ring, p=(200, 200), a=(200, 200), r=spin(op, 360)),
        art("Star outer", outer, line=("Primary", 2.4, 100), p=(200, 200), a=(200, 200), r=spin(op, 90)),
        art("Star inner", inner, line=("Accent", 2, 100), p=(200, 200), a=(200, 200), r=spin(op, -90)),
        art("Ring", [ell(200, 200, 340)], line=("Primary", 1.4, 60)),
    ])
    return c


def build_role_teacher():
    op = 180
    c = Comp("Halaqati role teacher", 200, op)
    book = grp("Quran", pages(86, 48, 40) + text_lines(86, 48), p=(100, 110), a=(100, 110), s=wave(op, 100, 104))
    rehal = art("Rehal", [poly([(64, 178), (124, 132)]), poly([(136, 178), (76, 132)])], line=("Accent", 7, 100))
    rays = art("Rays", [rays_d(100, 106, 12, 20, 70, .09)], fill_=("Accent", 28), p=(100, 106), a=(100, 106), r=spin(op, 30))
    layer(c, [sparkle(48, 56, 7, op, 0), sparkle(150, 60, 6, op, 60), sparkle(100, 32, 8, op, 120), book, rehal, rays,
              wreath(100, 100, 82, op=op)])
    return c


def build_role_admin():
    op = 180
    c = Comp("Halaqati role admin", 200, op)
    dome = "M40,152 L40,128 C40,102 62,86 86,78 C110,86 132,102 132,128 L132,152 Z"
    minaret = "M140,152 L140,104 L143,104 L143,97 L155,97 L155,104 L158,104 L158,152 Z"
    cone = "M141,97 L149,72 L157,97 Z"
    door = "M76,152 L76,130 C76,118 96,118 96,130 L96,152 Z"
    wins = [art("Window", [f"M{x - 7},142 L{x - 7},128 C{x - 7},118 {x + 7},118 {x + 7},128 L{x + 7},142 Z"],
                fill_=("Accent", 100), o=wave(op, 45, 100, phase=k * 60, two=False)) for k, x in enumerate((56, 116))]
    crescent = art("Crescent", ["M86,52 C76,52 70,60 70,69 C76,65 84,65 88,71 C92,63 92,56 86,52 Z"],
                   fill_=("Accent", 100), p=(86, 68), a=(86, 68), r=swing(op, 5))
    layer(c, [sparkle(46, 60, 7, op, 20), sparkle(150, 40, 6, op, 100), crescent] + wins + [
        art("Door", [door], fill_=("Accent", 35), line=("Accent", 2.6, 100)),
        art("Cone", [cone], fill_=("Primary", 20), line=("Primary", 3, 100)),
        art("Minaret", [minaret], fill_=("Primary", 20), line=("Primary", 3, 100)),
        art("Finial", [poly([(86, 78), (86, 62)])], line=("Accent", 3, 100)),
        art("Dome", [dome], fill_=("Primary", 20), line=("Primary", 3, 100)),
        art("Ground", [poly([(34, 152), (186, 152)])], line=("Accent", 4, 100)),
        art("Rays", [rays_d(100, 118, 12, 44, 84, .06)], fill_=("Accent", 16), p=(100, 118), a=(100, 118), r=spin(op, 30)),
        wreath(100, 100, 82, op=op),
    ])
    return c


def build_role_supervisor():
    op = 180
    c = Comp("Halaqati role supervisor", 200, op)
    ticks = []
    for k, (x, y) in enumerate(((62, 116), (84, 124), (116, 124), (138, 116))):
        peak = 24 + k * 34
        sc = anim([(0, (0, 0), "back"), (peak, (0, 0), "back"), (peak + 16, (100, 100), "back"),
                   (op - 14, (100, 100), "inout"), (op, (0, 0), "inout")])
        ticks.append(art("Tick", [poly([(x - 6, y), (x - 1, y + 6), (x + 8, y - 6)])], line=("Accent", 3.6, 100), p=(x, y), a=(x, y), s=sc))
    lens = grp("Lens", [
        art("Glass", [ell(0, 0, 44)], fill_=("Primary", 12), line=("Accent", 6, 100)),
        art("Handle", [poly([(16, 16), (36, 36)])], line=("Accent", 8, 100)),
    ], p=anim([(0, (70, 92), "inout"), (op // 2, (128, 100), "inout"), (op, (70, 92), "inout")]), a=(0, 0))
    layer(c, [lens] + ticks + pages(94, 58, 44, tint=16) + [wreath(100, 100, 82, op=op)])
    return c


def build_role_student():
    op = 180
    c = Comp("Halaqati role student", 200, op)
    mote_moves = []
    for k, (x, w0) in enumerate(((82, 0), (120, 55), (101, 108))):
        w1 = min(op, w0 + 70)
        keys = [(0, (x, 118), "lin")]
        if w0 > 0: keys.append((w0, (x, 118), "lin"))
        keys += [(w1, (x, 54), "out")]
        if w1 < op: keys.append((op, (x, 54), "lin"))
        mote_moves.append(art("Mote", [ell(0, 0, 5.5)], fill_=("Accent", 100), a=(0, 0), p=anim(keys), o=window(op, w0, w1, 90)))
    stem = art("Stem", [poly([(100, 112), (100, 72)])], line=("Accent", 5, 100))
    leaves = [art("Leaf", [leaf_d(40, 11)], fill_=("Primary", 100), p=(100, 76), a=(0, 0), r=swing(op, 6) if False else anim(
        [(0, base - 6, "inout"), (op // 2, base + 6, "inout"), (op, base - 6, "inout")]))
        for base in (-140, -40)]
    layer(c, mote_moves + [sparkle(60, 60, 7, op, 30), sparkle(146, 52, 6, op, 110)] + leaves + [stem] + pages(106, 52, 36) +
          [wreath(100, 100, 82, op=op)])
    return c


def build_role_guardian():
    op = 96
    c = Comp("Halaqati role guardian", 200, op)
    heart = ("M100,152 C40,110 52,64 84,66 C94,67 100,74 100,74 C100,74 106,67 116,66 C148,64 160,110 100,152 Z")
    beat = anim([(0, (100, 100), "out"), (10, (109, 109), "inout"), (20, (100, 100), "out"), (31, (106, 106), "inout"),
                 (46, (100, 100), "out"), (op, (100, 100), "lin")])
    layer(c, [sparkle(52, 58, 7, op, 0), sparkle(150, 64, 6, op, 48),
              art("Heart", [heart], fill_=("Accent", 34), line=("Accent", 4.5, 100), p=(100, 112), a=(100, 112), s=beat),
              art("Leaf", [leaf_d(30, 8)], fill_=("Primary", 100), p=(100, 118), a=(0, 0), r=-50),
              art("Leaf", [leaf_d(30, 8)], fill_=("Primary", 100), p=(100, 118), a=(0, 0), r=-130),
              art("Rays", [rays_d(100, 112, 12, 54, 78, .05)], fill_=("Accent", 18), p=(100, 112), a=(100, 112), r=spin(op, 30)),
              wreath(100, 100, 82, op=op)])
    return c


def build_milestone():
    op = 150
    c = Comp("Halaqati milestone", 300, op)
    burst = art("Burst", [rays_d(150, 146, 24, 34, 140, .05)], fill_=("Accent", 55), p=(150, 146), a=(150, 146),
                s=anim([(4, (30, 30), "out"), (44, (100, 100), "out"), (op, (100, 100), "lin")]),
                o=anim([(4, 0, "out"), (14, 100, "out"), (60, 0, "out"), (op, 0, "lin")]))
    star = grp("Star", [art("Square", [hizb_d(0, 0, 70)[0]], fill_=("Accent", 100)),
                        art("Diamond", [hizb_d(0, 0, 70)[1]], fill_=("Accent", 100)),
                        art("Core", [ell(0, 0, 30)], fill_=("Primary", 100))],
               p=(150, 146), a=(0, 0),
               s=anim([(8, (0, 0), "back"), (36, (100, 100), "back")]),
               r=anim([(8, -90, "out"), (40, 0, "out")]))
    ribbon = art("Ribbon", ["M66,238 L234,238 L222,258 L234,278 L66,278 L78,258 Z"], fill_=("Primary", 100), p=(150, 258), a=(150, 258),
                 s=anim([(44, (0, 100), "out"), (70, (100, 100), "back")]))
    ribbon_line = art("RibbonLine", [poly([(96, 258), (204, 258)])], line=("Accent", 4, 100), p=(150, 258), a=(150, 258),
                      s=anim([(60, (0, 100), "out"), (84, (100, 100), "out")]))
    conf = []
    for k in range(10):
        x = 40 + k * 24 + (k % 3) * 5
        w0 = 18 + k * 9
        w1 = min(op - 1, w0 + 70)
        conf.append(art("Confetti", [leaf_d(18, 5)], fill_=("Accent" if k % 2 else "Primary", 100), a=(0, 0),
                        p=anim([(w0, (x, 20), "out"), (w1, (x + (14 if k % 2 else -14), 190), "inout")]),
                        r=anim([(w0, 0, "lin"), (w1, 240 if k % 2 else -240, "lin")]),
                        o=anim([(0, 0, "lin"), (w0, 0, "lin"), (w0 + 6, 100, "lin"), (w1 - 12, 100, "lin"), (w1, 0, "lin"), (op, 0, "lin")])))
    layer(c, conf + [ribbon_line, ribbon, star, wreath(150, 146, 104, n=8, leaf=24, t0=16), burst])
    return c


def build_sync():
    op = 72
    c = Comp("Halaqati sync", 200, op)
    leaves = []
    for k in range(8):
        a = math.radians(45 * k)
        tx, ty = -math.sin(a), math.cos(a)  # clockwise tangent
        px, py = 100 + 58 * math.cos(a) - 13 * tx, 100 + 58 * math.sin(a) - 13 * ty
        leaves.append(art("Leaf", [leaf_d(26, 7)], fill_=("Primary", round(100 - k * 11)), p=(px, py), a=(0, 0),
                          r=math.degrees(a) + 90))
    hizb = hizb_d(100, 100, 40)
    layer(c, [art("Core", hizb, fill_=("Accent", 100), p=(100, 100), a=(100, 100), r=spin(op, -90)),
              grp("Chase", leaves, p=(100, 100), a=(100, 100), r=spin(op, 360))])
    return c


def build_empty_people():
    op = 180
    c = Comp("Halaqati empty people", 200, op)
    seats = []
    for k in range(8):
        a = math.radians(45 * k - 90)
        x, y = 100 + 58 * math.cos(a), 100 + 58 * math.sin(a)
        if k == 0:
            seats.append(art("Teacher", [ell(0, 0, 18)], fill_=("Accent", 100), p=(x, y), a=(0, 0), s=wave(op, 100, 128, 0)))
        else:
            seats.append(art("Seat", [ell(x, y, 15)], line=("Primary", 3, 70)))
    sprout = grp("Sprout", [art("Stem", [poly([(100, 124), (100, 98)])], line=("Accent", 4, 100)),
                            art("L", [leaf_d(22, 6)], fill_=("Primary", 100), p=(100, 100), a=(0, 0), r=anim([(0, -146, "inout"), (op // 2, -134, "inout"), (op, -146, "inout")])),
                            art("R", [leaf_d(22, 6)], fill_=("Primary", 100), p=(100, 100), a=(0, 0), r=anim([(0, -34, "inout"), (op // 2, -46, "inout"), (op, -34, "inout")]))],
                 p=(100, 124), a=(100, 124), s=wave(op, 96, 104))
    layer(c, [sprout] + seats + [art("Ring", [ell(100, 100, 116)], line=("Primary", 1.6, 35))])
    return c


def build_empty_bell():
    op = 180
    c = Comp("Halaqati empty bell", 200, op)
    bell = ("M100,52 C76,52 66,72 66,96 C66,120 56,132 50,140 L150,140 C144,132 134,120 134,96 C134,72 124,52 100,52 Z")
    sw = anim([(0, 0, "inout"), (20, -14, "inout"), (50, 11, "inout"), (80, -7, "inout"), (110, 4, "inout"), (140, -1, "inout"), (op, 0, "inout")])
    g = grp("Bell", [art("Body", [bell], fill_=("Primary", 22), line=("Primary", 4, 100)),
                     art("Rim", [poly([(46, 142), (154, 142)])], line=("Primary", 4, 100)),
                     art("Clapper", [ell(100, 154, 16)], fill_=("Accent", 100)),
                     art("Top", [ell(100, 46, 10)], fill_=("Accent", 100))],
            p=(100, 44), a=(100, 44), r=sw)
    layer(c, [sparkle(48, 62, 7, op, 20), sparkle(154, 56, 6, op, 100), g,
              art("Leaf", [leaf_d(26, 7)], fill_=("Primary", 100), p=(100, 178), a=(0, 0), r=-30),
              art("Leaf", [leaf_d(26, 7)], fill_=("Primary", 100), p=(100, 178), a=(0, 0), r=-150)])
    return c


def build_empty_search():
    op = 180
    c = Comp("Halaqati empty search", 200, op)
    page = art("Page", ["M62,44 L128,44 C134,44 138,48 138,54 L138,146 C138,152 134,156 128,156 L72,156 C66,156 62,152 62,146 Z"],
               fill_=("Primary", 16), line=("Primary", 3.6, 100))
    lines = [art("Line", [poly([(76, 68 + k * 22), (124 - (k % 2) * 18, 68 + k * 22)])], line=("Primary", 3, 50)) for k in range(4)]
    orbit = anim([(0, (108, 104), "inout"), (45, (96, 114), "inout"), (90, (88, 100), "inout"), (135, (100, 92), "inout"), (op, (108, 104), "inout")])
    lens = grp("Lens", [art("Glass", [ell(0, 0, 46)], fill_=("Accent", 12), line=("Accent", 6, 100)),
                        art("Handle", [poly([(17, 17), (36, 36)])], line=("Accent", 8, 100))], p=orbit, a=(0, 0))
    layer(c, [lens] + lines + [page])
    return c


def build_empty_doc():
    op = 180
    c = Comp("Halaqati empty doc", 200, op)
    sheet = art("Sheet", ["M42,56 L158,56 L158,144 L42,144 Z"], fill_=("Primary", 14), line=("Primary", 3.6, 100))
    lines = []
    for k, w in enumerate((70, 52, 62)):
        a, b = round(10 + k * 18), round(60 + k * 18)
        lines.append(art("Line", [poly([(60, 82 + k * 15), (60 + w, 82 + k * 15)])], line=("Primary", 3.4, 60),
                         trim=anim([(0, 0, "inout"), (a, 0, "inout"), (b, 100, "inout"), (op - 20, 100, "inout"), (op, 0, "inout")])))
    seal = grp("Seal", [art("Hizb", hizb_d(0, 0, 26), fill_=("Accent", 100)), art("Core", [ell(0, 0, 11)], fill_=("Primary", 100))],
               p=(136, 132), a=(0, 0), s=wave(op, 94, 108, 0), r=spin(op, 90))
    tails = art("Tails", ["M126,148 L120,176 L134,168 L138,178 L142,150 Z"], fill_=("Accent", 70))
    layer(c, [sparkle(160, 46, 6, op, 30), seal, tails] + lines + [sheet])
    return c


def build_trophy():
    op = 120
    c = Comp("Halaqati trophy", 200, op)
    bowl = "M66,48 L134,48 L134,84 C134,106 118,120 100,120 C82,120 66,106 66,84 Z"
    bob = anim([(0, (100, 100), "inout"), (60, (104, 104), "inout"), (op, (100, 100), "inout")])
    cup = grp("Cup", [
        art("Handle L", ["M66,58 C46,58 44,92 70,98"], line=("Accent", 5, 100)),
        art("Handle R", ["M134,58 C154,58 156,92 130,98"], line=("Accent", 5, 100)),
        art("Bowl", [bowl], fill_=("Accent", 30), line=("Accent", 5, 100)),
        art("Star", [star4_d(13)], fill_=("Primary", 100), p=(100, 80), a=(0, 0), s=wave(op, 90, 118, 0)),
        art("Stem", [poly([(100, 120), (100, 140)])], line=("Accent", 6, 100)),
        art("Base", ["M72,160 L128,160 L122,140 L78,140 Z"], fill_=("Primary", 40), line=("Primary", 4.5, 100)),
    ], p=(100, 100), a=(100, 100), s=bob)
    layer(c, [sparkle(48, 54, 7, op, 20), sparkle(154, 48, 6, op, 70), sparkle(160, 110, 5, op, 100), cup,
              art("Leaf", [leaf_d(26, 7)], fill_=("Primary", 100), p=(100, 176), a=(0, 0), r=-30),
              art("Leaf", [leaf_d(26, 7)], fill_=("Primary", 100), p=(100, 176), a=(0, 0), r=-150)])
    return c


def build_offline():
    op = 150
    c = Comp("Halaqati offline", 200, op)
    cloud = "M62,128 C44,128 38,104 56,98 C54,76 82,66 94,84 C104,62 140,70 138,98 C158,98 162,128 142,128 Z"
    drift = anim([(0, (100, 100), "inout"), (75, (100, 95), "inout"), (op, (100, 100), "inout")])
    dots = [art("Dot", [ell(0, 0, 8)], fill_=("Accent", 70), p=(78 + k * 22, 150), a=(0, 0), s=wave(op, 70, 120, k * 25)) for k in range(3)]
    body = grp("Cloud", [
        art("Body", [cloud], fill_=("Primary", 20), line=("Primary", 4, 100)),
        art("Slash", [poly([(70, 62), (130, 136)])], line=("Accent", 5.5, 100),
            trim=anim([(0, 0, "inout"), (25, 100, "inout"), (op, 100, "lin")])),
    ], p=drift, a=(100, 100))
    layer(c, [sparkle(46, 52, 6, op, 30)] + dots + [body])
    return c


def build_welcome():
    op = 150
    c = Comp("Halaqati welcome", 300, op)
    fan = anim([(0, 0, "out"), (40, 100, "out"), (op, 100, "lin")])
    layer(c, [art("Rays", [rays_d(150, 150, 16, 70, 132, .04)], fill_=("Accent", 22), p=(150, 150), a=(150, 150), r=spin(op, 40)),
              art("Star", hizb_d(150, 150, 72), line=("Accent", 3, 100), p=(150, 150), a=(150, 150), r=spin(op, 90),
                  trim=anim([(0, 0, "inout"), (50, 100, "inout"), (op, 100, "lin")])),
              art("Star2", hizb_d(150, 150, 46), line=("Primary", 3, 100), p=(150, 150), a=(150, 150), r=spin(op, -90),
                  trim=anim([(10, 0, "inout"), (60, 100, "inout"), (op, 100, "lin")])),
              wreath(150, 150, 104, n=8, phi0=10, phi1=170, leaf=18, op=op),
              sparkle(60, 70, 8, op, 30), sparkle(240, 84, 7, op, 80), sparkle(230, 230, 6, op, 120)])
    return c


SCENES = {
    "ambient": build_ambient,
    "role_teacher": build_role_teacher,
    "role_admin": build_role_admin,
    "role_supervisor": build_role_supervisor,
    "role_student": build_role_student,
    "role_guardian": build_role_guardian,
    "milestone": build_milestone,
    "sync": build_sync,
    "empty_people": build_empty_people,
    "empty_bell": build_empty_bell,
    "empty_search": build_empty_search,
    "empty_doc": build_empty_doc,
    "trophy": build_trophy,
    "offline": build_offline,
    "welcome": build_welcome,
}


def write_all(write):
    for name, builder in SCENES.items():
        write(name, builder())
