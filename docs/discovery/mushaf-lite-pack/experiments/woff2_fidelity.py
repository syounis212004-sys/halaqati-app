"""Compare QF CDN WOFF2 (decoded with google/woff2 woff2_decompress) against the original TTF, glyph by glyph.
Renders with FreeType unhinted and hinted (bytecode) at 120 px and reports glyphs whose bitmaps differ (> 64/255 or shape mismatch).
Inputs: <orig_dir>/p{n}.ttf and <dec_dir>/p{n}.ttf (decoded). Needs freetype-py, fontTools, numpy.   Usage: woff2_fidelity.py ORIG_DIR DEC_DIR"""
import freetype, numpy as np, glob, re, sys
from fontTools.ttLib import TTFont
NH = freetype.FT_LOAD_RENDER | freetype.FT_LOAD_NO_HINTING | freetype.FT_LOAD_NO_AUTOHINT
HI = freetype.FT_LOAD_RENDER | freetype.FT_LOAD_NO_AUTOHINT
def r(path, ch, flags, px=120):
    f = freetype.Face(path); f.set_pixel_sizes(0, px); f.load_char(chr(ch), flags); b = f.glyph.bitmap
    a = np.array(b.buffer, dtype=np.int32).reshape(b.rows, b.pitch)[:, :b.width] if b.rows else np.zeros((0, 0), np.int32)
    return a, (f.glyph.bitmap_left, f.glyph.bitmap_top)
orig, dec = sys.argv[1], sys.argv[2]; res = []
for p in sorted(glob.glob(f'{dec}/p*.ttf'), key=lambda x: int(re.findall(r'\d+', x)[-1])):
    n = int(re.findall(r'\d+', p)[-1]); o = f'{orig}/p{n}.ttf'; cm = sorted(TTFont(p).getBestCmap()); out = []
    for flags in (NH, HI):
        bad = 0
        for c in cm:
            a, pa = r(o, c, flags); b, pb = r(p, c, flags)
            if a.shape != b.shape or pa != pb: bad += 1; continue
            bad += (int(np.abs(a - b).max()) if a.size else 0) > 64
        out.append(bad)
    res.append((n, len(cm), *out))
print('pages', len(res), 'glyphs', sum(x[1] for x in res))
print('unhinted differing glyphs', sum(x[2] for x in res), 'pages affected', sum(1 for x in res if x[2]))
print('hinted differing glyphs', sum(x[3] for x in res), 'pages affected', sum(1 for x in res if x[3]))
