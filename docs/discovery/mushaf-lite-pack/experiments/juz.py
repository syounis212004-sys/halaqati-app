import glob,os,io,time,zlib,lzma,brotli,zstandard as zstd,zipfile,resource
d='/tmp/claude-0/study/v2all'
def f(n):
    p=f'{d}/p{n}.ttf'
    return open(p,'rb').read()
for name,(a,b) in {'juz1(1-21)':(1,21),'juz15(~282-301)':(282,301),'juz30(582-604)':(582,604)}.items():
    fs=[f(n) for n in range(a,b+1) if os.path.exists(f'{d}/p{n}.ttf')]
    raw=sum(map(len,fs)); cat=b''.join(fs)
    zi=io.BytesIO()
    with zipfile.ZipFile(zi,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for i,x in enumerate(fs): z.writestr(str(i),x)
    r={'raw':raw,'zip':len(zi.getvalue()),
       'zstd19_solid':len(zstd.ZstdCompressor(level=19).compress(cat)),
       'brotli9_each':sum(len(brotli.compress(x,quality=9)) for x in fs),
       'brotli11_solid':len(brotli.compress(cat,quality=11,lgwin=24)),
       'xz_solid':len(lzma.compress(cat,preset=6))}
    print(name,len(fs),{k:f'{v/1e6:.2f}MB ({100*v/raw:.0f}%)' for k,v in r.items()},flush=True)
# decode speed / RAM on one page
x=f(300); 
bz=brotli.compress(x,quality=9); zz=zlib.compress(x,9); zc=zstd.ZstdCompressor(level=19).compress(x)
def t(fn,n=50):
    s=time.perf_counter()
    for _ in range(n): fn()
    return (time.perf_counter()-s)/n*1000
print('page300 raw',len(x),'zlib',len(zz),'brotli',len(bz),'zstd19',len(zc))
print('decode ms: zlib %.2f brotli %.2f zstd %.2f'%(t(lambda:zlib.decompress(zz)),t(lambda:brotli.decompress(bz)),t(lambda:zstd.ZstdDecompressor().decompress(zc))))
print('maxrss MB',resource.getrusage(resource.RUSAGE_SELF).ru_maxrss/1024)
