import json,sys,time,urllib.request,os
def get(n):
    p=f'api/p{n}.json'
    if os.path.exists(p) and os.path.getsize(p)>1000: return
    u=f"https://api.quran.com/api/v4/verses/by_page/{n}?words=true&word_fields=code_v2,text_uthmani,page_number,line_number,v2_page&per_page=50"
    for a in range(4):
        try:
            d=urllib.request.urlopen(urllib.request.Request(u,headers={'User-Agent':'mushaf-size-study'}),timeout=30).read()
            json.loads(d); open(p,'wb').write(d); return
        except Exception as e: time.sleep(1+a)
    print('FAILED',n)
lo,hi=int(sys.argv[1]),int(sys.argv[2])
for n in range(lo,hi+1): get(n); time.sleep(0.15)
