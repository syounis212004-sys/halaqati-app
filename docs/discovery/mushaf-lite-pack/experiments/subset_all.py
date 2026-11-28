import json,os,sys
from multiprocessing import Pool
from fontTools import subset
from fontTools.ttLib import TTFont
own=json.load(open('own_codes.json'))
def run(n):
    src=f'/tmp/claude-0/study/v2all/p{n}.ttf'
    out=[]
    for tag,hint in (('keep',True),('nohint',False)):
        o=subset.Options(); o.layout_features=['*']; o.hinting=hint; o.notdef_outline=True; o.glyph_names=False
        o.name_IDs=['*']; o.drop_tables+=[] ; o.legacy_kern=True; o.recalc_bounds=True
        f=TTFont(src); s=subset.Subsetter(o); s.populate(unicodes=own[str(n)]); s.subset(f)
        p=f'sub_{tag}/p{n}.ttf'; f.save(p); out.append(os.path.getsize(p))
    return n,os.path.getsize(src),out[0],out[1]
if __name__=='__main__':
    with Pool(4) as pool: res=pool.map(run,range(1,605))
    json.dump(res,open('subset_sizes.json','w'))
    a=sum(r[1] for r in res); b=sum(r[2] for r in res); c=sum(r[3] for r in res)
    print('orig %.1f MB | subset(keep hinting) %.1f MB (%.0f%%) | subset(no hinting) %.1f MB (%.0f%%)'%(a/1e6,b/1e6,100*b/a,c/1e6,100*c/a))
