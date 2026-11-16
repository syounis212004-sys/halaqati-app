#!/usr/bin/env python3
from pathlib import Path
import argparse, json, shutil
from playwright.sync_api import sync_playwright

parser=argparse.ArgumentParser()
parser.add_argument('--out',default='ci-logs/rc20-report-layout')
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
out=(root/args.out).resolve() if not Path(args.out).is_absolute() else Path(args.out)
out.mkdir(parents=True,exist_ok=True)
css=(root/'app/src/main/assets/documents/report.css').read_text(encoding='utf-8')
html=f'''<!doctype html><html dir="rtl" lang="ar"><head><meta charset="utf-8"><style>
@font-face{{font-family:Cairo;src:url("file://{(root/'app/src/main/assets/documents/cairo.ttf').as_posix()}") format("truetype")}}
html,body{{margin:0;background:#fff;font-family:Cairo,sans-serif}}body{{--report-scale:1;--report-primary:#164d3d;--report-accent:#704328}}
{css}
.fixture{{width:1120px;padding:32px;box-sizing:border-box}}
</style></head><body><div class="fixture"><section class="report-page"><div class="student-report-card no-photo"><div><h2 id="student-name" class="student-report-name">أحمد حسن وصفي أبو عنزة عبد الرحمن</h2><div id="student-grid" class="student-info-grid">
<div>المسار<b>الحفظ العام</b></div><div>الفئة<b>الرواد</b></div><div>تاريخ الميلاد<b>2012-03-15</b></div><div>الحفظ<b>37.5 ص</b></div><div>المراجعة<b>22 ص</b></div><div>السرد<b>25 ص</b></div>
</div></div></div><table><thead><tr><th>التاريخ</th><th>النوع</th><th>النطاق</th><th>صفحات</th><th>أخطاء</th><th>نقاط</th></tr></thead><tbody><tr><td>2026-10-02</td><td>تسميع</td><td>سورة الفتح 1–18</td><td>3.2</td><td>1</td><td>12</td></tr></tbody></table></section></div></body></html>'''
with sync_playwright() as pw:
    exe=shutil.which('chromium') or shutil.which('google-chrome') or shutil.which('google-chrome-stable')
    browser=pw.chromium.launch(headless=True, executable_path=exe if exe else None, args=['--no-sandbox'])
    page=browser.new_page(viewport={'width':1200,'height':800}, device_scale_factor=1)
    page.set_content(html, wait_until='load')
    metrics=page.evaluate('''() => {
      const n=document.getElementById('student-name'); const g=document.getElementById('student-grid');
      const cs=getComputedStyle(g); const cols=cs.gridTemplateColumns.split(' ').filter(Boolean);
      return {
        nameClientWidth:n.clientWidth, nameScrollWidth:n.scrollWidth, nameHeight:n.getBoundingClientRect().height,
        gridColumns:cols.length, gridColumnWidths:cols.map(Number).filter(Number.isFinite),
        bodyScrollWidth:document.body.scrollWidth, bodyClientWidth:document.body.clientWidth
      };
    }''')
    page.screenshot(path=str(out/'student-no-photo.png'),full_page=True)
    browser.close()
checks={
    'name_wide': metrics['nameClientWidth'] >= 700,
    'name_no_horizontal_overflow': metrics['nameScrollWidth'] <= metrics['nameClientWidth'] + 2,
    'name_not_vertical_stack': metrics['nameHeight'] <= 80,
    'three_metadata_columns': metrics['gridColumns'] == 3,
    'no_body_horizontal_overflow': metrics['bodyScrollWidth'] <= metrics['bodyClientWidth'] + 2,
}
result={'metrics':metrics,'checks':checks}
(out/'results.json').write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding='utf-8')
failed=[k for k,v in checks.items() if not v]
if failed:
    raise SystemExit('RC20 report visual layout gate failed: '+', '.join(failed)+'\n'+json.dumps(result,ensure_ascii=False,indent=2))
print('RC20 report visual layout gate PASS')
print(json.dumps(result,ensure_ascii=False,indent=2))
