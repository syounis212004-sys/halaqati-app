#!/usr/bin/env python3
"""يولّد design/generated/tokens.css و Tokens.kt.txt من design/tokens.json. غير مربوط بالبناء عمدًا."""
import json, pathlib
root = pathlib.Path(__file__).resolve().parent.parent
t = json.loads((root / "design" / "tokens.json").read_text(encoding="utf-8"))
out = root / "design" / "generated"; out.mkdir(exist_ok=True)
css = [":root{"]
for grp in ("size", "radius", "space", "font"):
    for k, v in t[grp].items(): css.append(f"  --t-{grp}-{k}:{v}px;")
for k, v in t["tone"].items(): css.append(f"  --t-tone-{k}:{v};")
for k, v in t["family"].items(): css.append(f"  --t-family-{k}:'{v}',system-ui,sans-serif;")
css.append("}")
(out / "tokens.css").write_text("\n".join(css) + "\n", encoding="utf-8")
kt = ["// مولَّد من design/tokens.json — لا تعدّله يدويًا", "object DesignTokens {"]
for grp in ("size", "radius", "space", "font"):
    for k, v in t[grp].items(): kt.append(f"    const val {grp.upper()}_{k.upper()} = {v}")
for k, v in t["tone"].items(): kt.append(f"    const val TONE_{k.upper()} = 0xFF{v[1:]}")
kt.append("}")
(out / "Tokens.kt.txt").write_text("\n".join(kt) + "\n", encoding="utf-8")
print("ok")
