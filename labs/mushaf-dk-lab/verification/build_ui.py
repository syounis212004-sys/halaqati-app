import json
rows=json.load(open('/tmp/claude-0/agentB/differences.json'))
st=json.load(open('/tmp/claude-0/agentB/stats.json'))
meta=dict(reference='Reference edition = the V2 / 1421H Madinah mushaf as encoded in the Quran Foundation API (api.quran.com): text_uthmani for the text, v2_page + line_number (code_v2) for page/line layout. DigitalKhatt (DK) = madina.json glyph layout. Marks of any other edition (V4/1441H, KFGQPC Hafs file) are not mixed in.',
 method='Each DK glyph run is reconstructed into letters+marks from glyph names (dots are separate glyphs and are folded back into the letter; ligatures expanded). Both sides are reduced to per-letter (letter, sorted marks). Words are aligned per page (difflib on canonical keys). Differing code points are listed in U+ notation. Labels (waqf_only, hamza_encoding, tanween, ...) are provisional machine groupings of code-point differences.',
 limits=['Equal word counts / IoU do not prove text integrity; this tool compares code points reconstructed from glyph names, not shaping or visual correctness.',
  'DK open-tanween glyphs (fathatanidgham etc.) were mapped to tanween + U+06ED: this is an assumption; the 1,870 "U+06E2 vs U+06ED" rows may be the same visible sign or a real iqlab/idgham sign difference. Check the images.',
  'Mark order inside one letter is not compared (marks are sorted); 12 words differ only in raw mark order.',
  'Tatweel, ZWJ/ZWNJ and spacing are ignored. Ayah-number digits are compared numerically.',
  'The reference (QF API) itself may differ from the printed 1421H mushaf; this checks DK against the API encoding of that edition, not against paper.',
  'A qualified specialist must decide every difference. No text is changed by this tool.'])
open('/tmp/claude-0/agentB/review_data.js','w',encoding='utf-8').write('window.REVIEW_DATA='+json.dumps(dict(meta=meta,stats=st,rows=rows),ensure_ascii=False)+';')
print('ok',len(rows))
