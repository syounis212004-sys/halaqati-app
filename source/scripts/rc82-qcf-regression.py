#!/usr/bin/env python3
"""Golden examples copied from the v2.44 QCF4 selector screenshots/behavior."""
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
asset=ROOT/'app/src/main/assets/quran/qcf4-layout-v2.bin'
data=asset.read_bytes()
COUNTS=[7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6]
prefix=[0]
for n in COUNTS: prefix.append(prefix[-1]+n)
def global_ayah(s,a): return prefix[s-1]+a
def keys(gs,ge):
    out=set()
    for g in range(gs,ge+1):
        i=(g-1)*3; page=(data[i]<<8)|data[i+1]; b=data[i+2]; first=b>>4; last=b&15
        for line in range(first,last+1): out.add(f'{page}:{line}')
    return out
def pages_for(s,a,e,b): return len(keys(global_ayah(s,a),global_ayah(e,b)))/15
# 2.44 golden examples
assert len(data)==6236*3
assert round(pages_for(1,1,1,7),2)==0.47, pages_for(1,1,1,7)
assert round(pages_for(78,1,78,40),2)==1.33, pages_for(78,1,78,40)
# Juz 8 and 13 in v2.44 screenshot: 39.6 pages / 296 ayat.
JUZ=[0,1,149,260,386,517,641,751,900,1042,1201,1328,1479,1649,1803,2030,2215,2484,2674,2876,3215,3386,3564,3733,4090,4265,4511,4706,5105,5242,5673,6237]
union=set(); ayahs=set()
for j in (8,13):
    for g in range(JUZ[j],JUZ[j+1]):
        ayahs.add(g); union |= keys(g,g)
assert len(ayahs)==296, len(ayahs)
assert round(len(union)/15,2)==39.6, len(union)/15
print('RC8.2 QCF4 golden regressions PASS: Fatiha=0.47, Naba=1.33, Juz8+13=39.6/296')
