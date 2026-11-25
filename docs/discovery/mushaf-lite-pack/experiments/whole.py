import os,io,zipfile,zstandard as zstd,time
d='v2all'; fs=sorted(os.listdir(d),key=lambda x:(len(x),x)); fs=[f for f in fs if f.endswith('.ttf')]
data=[open(f'{d}/{f}','rb').read() for f in fs]; raw=sum(map(len,data))
zi=io.BytesIO()
with zipfile.ZipFile(zi,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for f,x in zip(fs,data): z.writestr(f,x)
print('files',len(fs),'raw %.1f MB'%(raw/1e6),'zip-deflate9 %.1f MB'%(len(zi.getvalue())/1e6),flush=True)
t=time.time()
c=zstd.ZstdCompressor(compression_params=zstd.ZstdCompressionParameters.from_level(19,window_log=27,enable_ldm=True,threads=4)).compress(b''.join(data))
print('zstd19 solid long27 %.1f MB'%(len(c)/1e6),'%.0fs'%(time.time()-t))
