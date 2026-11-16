#!/usr/bin/env python3
"""
Halaqati motion identity (RC27.14): Lottie generator built from the official logo.

The mark is an open book whose two pages carry olive leaves, with an olive branch rising from the spine
("جذور الشرق"). Its vector outlines live in mark.json (traced from the approved logo, 652x636 space).

Run:  python3 generate.py      Output: app/src/main/res/raw/halaqati_*.json

Runtime recoloring (HalaqatiMotion.kt) targets fill/stroke names:
  "Primary"  -> theme primary   (leaves, page tint)
  "Accent"   -> theme tertiary  (gold: edges, stem, branch, olives)
  "OnPrimary"-> theme onPrimary (check mark)
Everything named "Brand*" keeps the logo's own colors (splash tile).
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "..", "app", "src", "main", "res", "raw")
MARK = json.load(open(os.path.join(HERE, "mark.json")))["layers"]

PAL = {
    "brand": "#3B4929", "paper": "#FBF5EA", "leaf": "#536236", "gold": "#BE994F", "goldl": "#CCB075",
    "primary": "#3B4929", "accent": "#86621F", "onprimary": "#FFFFFF",  # fallbacks only; the app recolors
}
FR = 60
BCX, BCY = 326.4, 338.0          # centre of the mark's bounding box
EASE = {
    "lin": (0.0, 0.0, 1.0, 1.0),
    "out": (0.215, 0.61, 0.355, 1.0),
    "inout": (0.645, 0.045, 0.355, 1.0),
    "back": (0.175, 0.885, 0.32, 1.275),
}


def rgb(h):
    h = h.lstrip("#")
    return [round(int(h[i:i + 2], 16) / 255, 4) for i in (0, 2, 4)] + [1]


def prop(v):
    if isinstance(v, dict):
        return v
    return {"a": 0, "k": list(v) if isinstance(v, (list, tuple)) else v}


def anim(keys):
    out = []
    for n, (t, v, e) in enumerate(keys):
        d = {"t": t, "s": list(v) if isinstance(v, (list, tuple)) else [v]}
        if n < len(keys) - 1:
            ox, oy, ix, iy = EASE[e]
            d["o"] = {"x": ox, "y": oy}
            d["i"] = {"x": ix, "y": iy}
        out.append(d)
    return {"a": 1, "k": out}


def tr(p=(0, 0), a=(0, 0), s=(100, 100), r=0, o=100):
    return {"ty": "tr", "nm": "Transform", "p": prop(p), "a": prop(a), "s": prop(s),
            "r": prop(r), "o": prop(o), "sk": prop(0), "sa": prop(0)}


def grp(name, items, **t):
    return {"ty": "gr", "nm": name, "it": items + [tr(**t)], "np": len(items), "cix": 2, "bm": 0, "ix": 1, "hd": False}


def fill(hexc, name, o=100, rule=1):
    return {"ty": "fl", "nm": name, "c": prop(rgb(hexc)), "o": prop(o), "r": rule, "bm": 0}


def stroke(hexc, w, name, o=100):
    return {"ty": "st", "nm": name, "c": prop(rgb(hexc)), "o": prop(o), "w": prop(w), "lc": 2, "lj": 2, "ml": 4, "bm": 0}


def shape(c):
    return {"ty": "sh", "nm": "Path", "ks": {"a": 0, "k": {"i": c["i"], "o": c["o"], "v": c["v"], "c": True}}}


def part(name, picks, hexc, fname, o=100, rule=1, outline=None):
    """One colored group made of contours from mark.json: picks = [(layer, index), ...]."""
    items = [shape(MARK[l][i]) for l, i in picks]
    if outline:
        items.append(stroke(*outline))
    items.append(fill(hexc, fname, o, rule))
    return grp(name, items)


def at(name, items, pivot, **t):
    """Group transformed about a pivot (position = anchor = pivot)."""
    return grp(name, items, p=t.pop("p", pivot), a=pivot, **t)


class Comp:
    def __init__(self, name, size, op):
        self.name, self.w, self.h, self.op, self.layers = name, size, size, op, []

    def layer(self, nm, shapes, p=None, a=(0, 0), s=(100, 100), o=100):
        self.layers.append({
            "ddd": 0, "ind": len(self.layers) + 1, "ty": 4, "nm": nm, "sr": 1,
            "ks": {"o": prop(o), "r": prop(0), "p": p if isinstance(p, dict) else prop(list(p or (self.w / 2, self.h / 2))),
                   "a": prop(list(a)), "s": prop(s)},
            "ao": 0, "shapes": shapes, "ip": 0, "op": self.op, "st": 0, "bm": 0})

    def mark_layer(self, nm, shapes, scale, p=None, o=100):
        self.layer(nm, shapes, p=p, a=(BCX, BCY), s=(scale, scale), o=o)

    def json(self):
        return {"v": "5.7.0", "fr": FR, "ip": 0, "op": self.op, "w": self.w, "h": self.h, "nm": self.name,
                "ddd": 0, "assets": [], "layers": self.layers, "markers": []}


# contour indexes in mark.json
PAGE = {"L": dict(paper=1, leaf=[2, 3], gold=1, goldl=1, tip=(296, 456)),
        "R": dict(paper=0, leaf=[0, 1], gold=0, goldl=0, tip=(357, 456))}
SPINE_BOTTOM = (326, 522)
STEM_BOTTOM = (326, 569)
BRANCH_BASE = (326, 350)
OLIVE_LOW = (326, 322)
OLIVE_MID = (344, 255)


def page(side, colors, t0, glow=True, leaves=True, tint=None, outline=None):
    """colors: dict of (hex, fill name) for paper/leaf/gold/goldl. tint = paper opacity. Returns the page group."""
    d = PAGE[side]
    leaf = at("Leaves " + side, [part("Leaf", [("leaf", i) for i in d["leaf"]], *colors["leaf"], rule=2)], d["tip"],
              s=anim([(t0 + 20, (70, 70), "back"), (t0 + 50, (100, 100), "back")]) if glow else (100, 100),
              o=anim([(t0 + 20, 0, "out"), (t0 + 36, 100, "out")]) if glow else 100)
    edges = at("Edges " + side, [
        part("Gold", [("gold", d["gold"])], *colors["gold"]),
        part("GoldLight", [("goldlight", d["goldl"])], *colors["goldl"]),
    ], (326, 480),
        p=anim([(t0 + 22, (326, 496), "out"), (t0 + 46, (326, 480), "out")]) if glow else (326, 480),
        o=anim([(t0 + 22, 0, "out"), (t0 + 44, 100, "out")]) if glow else 100)
    paper = part("Paper", [("paper", d["paper"])], colors["paper"][0], colors["paper"][1], o=tint if tint else 100,
                 outline=outline)
    items = ([edges, leaf] if leaves else [edges]) + [paper]
    if glow:
        return at("Page " + side, items, SPINE_BOTTOM,
                  p=anim([(t0, (326, 546), "out"), (t0 + 34, SPINE_BOTTOM, "out")]),
                  s=anim([(t0, (6, 100), "back"), (t0 + 34, (100, 100), "back")]),
                  o=anim([(t0, 0, "out"), (t0 + 10, 100, "out")]))
    return grp("Page " + side, items)


def branch_parts(colors, sway=None, grow=None):
    """Branch (leaves + stem tip) and two olives. Returns [olive2, olive1, branch]."""
    gold = colors["goldl"]
    olive_lo = at("Olive low", [part("Olive", [("gold", 2)], *colors["gold"])], OLIVE_LOW,
                  s=grow["olive_lo"] if grow else (100, 100))
    olive_mid = at("Olive mid", [part("Olive", [("gold", 3)], *colors["gold"])], OLIVE_MID,
                   s=grow["olive_mid"] if grow else (100, 100))
    br = at("Branch", [part("Branch light", [("goldlight", 2)], *gold),
                       part("Branch leaf", [("gold", 4)], *colors["gold"])], BRANCH_BASE,
            s=grow["branch"] if grow else (100, 100), r=grow["rot"] if grow else (sway or 0))
    return [olive_lo, olive_mid, br]


def stem(colors, grow=None):
    return at("Stem", [part("Stem", [("stem", 0)], *colors["gold"])], STEM_BOTTOM, s=grow if grow else (100, 100))


BRAND = {"paper": (PAL["paper"], "BrandPaper"), "leaf": (PAL["leaf"], "BrandLeaf"),
         "gold": (PAL["gold"], "BrandGold"), "goldl": (PAL["goldl"], "BrandGoldLight")}
THEME = {"paper": (PAL["primary"], "Primary"), "leaf": (PAL["primary"], "Primary"),
         "gold": (PAL["accent"], "Accent"), "goldl": (PAL["accent"], "Accent")}


def build_splash():
    c = Comp("Halaqati splash", 512, 132)
    grow = {"olive_lo": anim([(96, (0, 0), "back"), (114, (100, 100), "back")]),
            "olive_mid": anim([(102, (0, 0), "back"), (120, (100, 100), "back")]),
            "branch": anim([(70, (0, 0), "back"), (106, (100, 100), "back")]),
            "rot": anim([(70, -14, "out"), (106, 0, "out")])}
    shapes = branch_parts(BRAND, grow=grow) + [
        stem(BRAND, grow=anim([(50, (100, 0), "inout"), (84, (100, 100), "inout")])),
        page("R", BRAND, 18), page("L", BRAND, 14)]
    s = 0.72 * 100
    c.mark_layer("Mark", shapes, s, p=(256, 258))
    tile = at("Tile", [{"ty": "rc", "nm": "Rect", "d": 1, "s": prop([512, 512]), "p": prop([0, 0]), "r": prop(112)},
                       fill(PAL["brand"], "Brand")], (0, 0), p=(256, 256),
              s=anim([(0, (72, 72), "back"), (26, (100, 100), "back")]),
              o=anim([(0, 0, "out"), (10, 100, "out")]))
    c.layer("Tile", [tile], p=(0, 0))
    return c


def sway_keys(amp, op, n=4):
    step = op / n
    out = []
    for k in range(n + 1):
        sign = [0, -1, 0, 1, 0][k % 5]
        out.append((round(k * step), amp * sign, "inout"))
    return anim(out)


def build_loading():
    c = Comp("Halaqati loading", 512, 96)
    breathe_l = anim([(0, (100, 100), "inout"), (24, (106, 106), "inout"), (48, (100, 100), "inout"), (96, (100, 100), "inout")])
    breathe_r = anim([(0, (100, 100), "inout"), (36, (100, 100), "inout"), (60, (106, 106), "inout"), (84, (100, 100), "inout"), (96, (100, 100), "inout")])
    pl, pr = page("L", THEME, 0, glow=False, tint=22), page("R", THEME, 0, glow=False, tint=22)
    # breathing leaves: rebuild leaf groups with animated scale
    for pg, br in ((pl, breathe_l), (pr, breathe_r)):
        for it in pg["it"]:
            if it.get("nm", "").startswith("Leaves"):
                it["it"][-1]["s"] = br
    shapes = branch_parts(THEME, grow={"olive_lo": (100, 100), "olive_mid": (100, 100), "branch": (100, 100),
                                       "rot": sway_keys(5, 96)}) + [stem(THEME), pr, pl]
    c.mark_layer("Mark", shapes, 84)
    return c


def build_empty():
    c = Comp("Halaqati empty", 512, 180)
    out = lambda col: (col, 8, "Primary", 70)
    pl = page("L", THEME, 0, glow=False, leaves=False, tint=14, outline=out(PAL["primary"]))
    pr = page("R", THEME, 0, glow=False, leaves=False, tint=14, outline=out(PAL["primary"]))
    shapes = branch_parts(THEME, grow={"olive_lo": (100, 100), "olive_mid": (100, 100), "branch": (100, 100),
                                       "rot": sway_keys(3, 180)}) + [stem(THEME), pr, pl]
    float_y = anim([(0, (256, 258), "inout"), (90, (256, 250), "inout"), (180, (256, 258), "inout")])
    c.mark_layer("Mark", shapes, 80, p=float_y)
    return c


def build_success():
    c = Comp("Halaqati success", 512, 120)
    cy, d = 332, 208
    grow = {"olive_lo": anim([(70, (0, 0), "back"), (88, (100, 100), "back")]),
            "olive_mid": anim([(76, (0, 0), "back"), (94, (100, 100), "back")]),
            "branch": anim([(34, (0, 0), "back"), (70, (100, 100), "back")]),
            "rot": anim([(34, -16, "out"), (70, 0, "out")])}
    c.mark_layer("Branch", branch_parts(THEME, grow=grow), 68, p=(256, cy - d / 2 - 2))
    ring = at("Ring", [{"ty": "el", "nm": "Ellipse", "d": 1, "s": prop([d, d]), "p": prop([0, 0])},
                       stroke(PAL["accent"], 8, "Accent", 100)], (0, 0), p=(256, cy),
              s=anim([(24, (100, 100), "out"), (62, (150, 150), "out")]),
              o=anim([(0, 0, "lin"), (23, 0, "lin"), (24, 80, "out"), (62, 0, "out")]))
    c.layer("Ring", [ring], p=(0, 0))
    check = grp("Check", [
        {"ty": "sh", "nm": "Path", "ks": {"a": 0, "k": {"i": [[0, 0]] * 3, "o": [[0, 0]] * 3,
                                                          "v": [[-48, 4], [-14, 38], [50, -30]], "c": False}}},
        stroke(PAL["onprimary"], 22, "OnPrimary"),
        {"ty": "tm", "nm": "Trim", "s": prop(0), "e": anim([(24, 0, "inout"), (48, 100, "inout")]), "o": prop(0), "m": 1},
    ], p=(256, cy), o=anim([(0, 0, "lin"), (23, 0, "lin"), (24, 100, "lin")]))
    c.layer("Check", [check], p=(0, 0))
    disc = at("Disc", [{"ty": "el", "nm": "Ellipse", "d": 1, "s": prop([d, d]), "p": prop([0, 0])},
                       fill(PAL["primary"], "Primary")], (0, 0), p=(256, cy),
              s=anim([(0, (0, 0), "back"), (26, (100, 100), "back")]))
    c.layer("Disc", [disc], p=(0, 0))
    return c


def write(name, comp):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, f"halaqati_{name}.json")
    with open(path, "w", encoding="utf-8") as f:
        json.dump(comp.json(), f, separators=(",", ":"))
    print(f"{path}: {os.path.getsize(path) / 1024:.1f} KB")


if __name__ == "__main__":
    write("splash", build_splash())
    write("loading", build_loading())
    write("success", build_success())
    write("empty", build_empty())
    import scenes
    scenes.write_all(write)
