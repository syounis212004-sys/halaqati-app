const {chromium}=require('/opt/node-tools/node_modules/playwright');
(async()=>{const b=await chromium.launch(); const files=process.argv.slice(2);
 const pg=await b.newPage({deviceScaleFactor:1});
 for(const f of files){await pg.goto('file://'+f); const el=await pg.$('svg'); await el.screenshot({path:f.replace('.svg','.png')});}
 await b.close();})();
