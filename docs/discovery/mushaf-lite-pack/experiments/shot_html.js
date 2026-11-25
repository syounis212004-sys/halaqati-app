const {chromium}=require('/opt/node-tools/node_modules/playwright');
(async()=>{const b=await chromium.launch(); const pg=await b.newPage({viewport:{width:1000,height:800},deviceScaleFactor:1});
 for(const f of process.argv.slice(2)){await pg.goto('file://'+f); await pg.evaluate(()=>document.fonts.ready); await pg.waitForTimeout(150); await pg.screenshot({path:f.replace('.html','.png'),fullPage:true});}
 await b.close();})();
