"""Do the V4 (colrv1 woff2, decoded with google/woff2) page fonts contain every code_v2 of the page's own words? Needs v4/p{n}.ttf and own_codes.json.
Result: 604/604 pages covered, 0 missing codes; V4 totals 50,878,540 B of woff2 (167.3 MB decoded). Advance widths differ from V2 (ratio 0.81-1.33 per glyph)."""
import json
from fontTools.ttLib import TTFont
own = json.load(open('/tmp/claude-0/study2/own_codes.json')); miss = 0
for n in range(1, 605):
    miss += len(set(own[str(n)]) - set(TTFont(f'/tmp/claude-0/study2/v4/p{n}.ttf').getBestCmap()))
print('missing codes over 604 pages:', miss)
