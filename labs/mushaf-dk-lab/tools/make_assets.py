#!/usr/bin/env python3
"""Generate the lab's compact assets from DigitalKhatt's precomputed layout (madina.json, MIT; outlines derived from the OFL Madina font).
Writes app/src/main/assets/dk/glyphs.bin and dk/p{1..604}.bin. NOTE: files are gzip data but must NOT end in .gz: AGP renames/decompresses .gz assets in the APK (found by the Robolectric test). The assets contain glyph streams (letter code points), i.e. Quran text in encoded
form, so they are gitignored and never committed: run this script before building.
Usage: make_assets.py [path/to/madina.json]   (downloads https://digitalkhatt.org/assets/layouts/madina.json when no path is given)"""
import gzip, json, os, sys, urllib.request
src = sys.argv[1] if len(sys.argv) > 1 else None
if src is None:
    src = "/tmp/madina.json"
    if not os.path.exists(src):
        urllib.request.urlretrieve("https://digitalkhatt.org/assets/layouts/madina.json", src)
D = json.load(open(src))
out = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "dk")
os.makedirs(out, exist_ok=True)
def r(v): return round(v, 1)
def path_list(paths): return [[[r(c) for c in e] for e in p["path"]] for p in paths]
G = {}
for cp, g in D["glyphs"].items():
    e = {"b": [r(x) for x in g["bbox"]], "d": path_list(g["default"])}
    if g.get("limits"): e["lim"] = g["limits"]
    for k, s in (("minLeft", "ml"), ("maxLeft", "xl"), ("minRight", "mr"), ("maxRight", "xr")):
        if g.get(k): e[s] = path_list(g[k])
    G[cp] = e
with gzip.open(os.path.join(out, "glyphs.bin"), "wt", encoding="utf-8") as f: json.dump(G, f, separators=(",", ":"))
total = os.path.getsize(os.path.join(out, "glyphs.bin"))
for i, p in enumerate(D["pages"], start=1):
    lines = []
    for L in p["lines"]:
        flat = []
        for g in L["glyphs"]:
            flat += [g["codepoint"], g.get("x_advance", 0), g.get("x_offset", 0), g.get("y_offset", 0), g.get("lefttatweel", 0), g.get("righttatweel", 0)]
        lines.append([L.get("type", 0), L.get("x", 0), L.get("xscale", 1), flat])
    fn = os.path.join(out, f"p{i}.bin")
    with gzip.open(fn, "wt", encoding="utf-8") as f: json.dump(lines, f, separators=(",", ":"))
    total += os.path.getsize(fn)
print("assets written to", out, "total bytes", total)
