#!/usr/bin/env python3
"""RC13 browser/PDF visual quality gate.

Creates a representative certificate and report with the same production CSS/layout
contract, renders them with Chromium, then checks A4 geometry, selectable text,
reasonable file size, and DOM layout markers. No network is used.
"""
from __future__ import annotations
import argparse, base64, html, io, json, os, re, shutil, subprocess, sys, tempfile, unicodedata
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
CERT_CSS = ROOT / "app/src/main/assets/documents/certificate.css"
REPORT_CSS = ROOT / "app/src/main/assets/documents/report.css"
CENTER_LOGO = ROOT / "app/src/main/res/drawable-nodpi/center_logo.jpg"
HALAQA_LOGO = ROOT / "app/src/main/res/drawable-nodpi/halaqa_logo.jpg"

def h(s): return html.escape(str(s), quote=True)

def image_data(path: Path, max_px=520, quality=84):
    with Image.open(path) as im:
        im = im.convert("RGB")
        im.thumbnail((max_px, max_px), Image.Resampling.LANCZOS)
        buf = io.BytesIO(); im.save(buf, "JPEG", quality=quality, optimize=True, progressive=True)
    return "data:image/jpeg;base64," + base64.b64encode(buf.getvalue()).decode("ascii")

def pattern_svg():
    return '''<svg class="cert-geo-bg" viewBox="0 0 1122 794" preserveAspectRatio="none" aria-hidden="true"><defs><pattern id="certGirihPattern" width="96" height="96" patternUnits="userSpaceOnUse"><g fill="none" stroke="currentColor" stroke-width="1"><path d="M48 4 64 20 87 20 87 43 92 48 87 53 87 76 64 76 48 92 32 76 9 76 9 53 4 48 9 43 9 20 32 20Z"/><path d="M48 14 58 28 76 30 68 48 76 66 58 68 48 82 38 68 20 66 28 48 20 30 38 28Z"/><path d="M0 48h96M48 0v96M14 14l68 68M82 14 14 82" opacity=".65"/><circle cx="48" cy="48" r="10"/></g></pattern></defs><rect width="1122" height="794" fill="url(#certGirihPattern)"/></svg>'''

def corner(cls):
    return f'''<svg class="cert-corner {cls}" viewBox="0 0 180 180" aria-hidden="true"><g fill="none" stroke-linejoin="round" stroke-linecap="round"><path class="cert-corner-line primary" d="M8 8H172M8 8V172M22 22H148M22 22V148"/><path class="cert-corner-line accent" d="M12 36H126M36 12V126"/><g transform="translate(22 22)"><polygon class="cert-corner-line accent cert-star-fill" points="58,4 68,35 96,20 81,48 112,58 81,68 96,96 68,81 58,112 48,81 20,96 35,68 4,58 35,48 20,20 48,35"/><polygon class="cert-corner-line primary" points="58,14 86,30 102,58 86,86 58,102 30,86 14,58 30,30"/><rect class="cert-corner-line accent" x="30" y="30" width="56" height="56" transform="rotate(45 58 58)"/><circle class="cert-corner-line primary" cx="58" cy="58" r="18"/><path class="cert-corner-line accent" d="M58 14V102M14 58H102M27 27l62 62M89 27 27 89"/></g><path class="cert-corner-line primary" d="M116 22h32v32M22 116v32h32"/><path class="cert-corner-line accent" d="M124 30h16v16M30 124v16h16"/></g></svg>'''

def rosette(cls):
    return f'''<svg class="cert-side-rosette {cls}" viewBox="0 0 64 64" aria-hidden="true"><g fill="none"><path class="primary" d="M32 3 39 22 58 14 50 32 61 40 40 39 32 61 24 40 3 40 14 32 6 14 25 22Z"/><circle class="accent" cx="32" cy="32" r="13"/><path class="accent" d="M32 8v48M8 32h48M15 15l34 34M49 15 15 49"/></g></svg>'''

def certificate_html(css, logo1, logo2, long=False):
    name = "أحمد حسن وصفي أبو عنزة" if not long else "أحمد عبد الرحمن محمد حسن وصفي أبو عنزة الفلسطيني"
    achievement = "تقديرًا لإتمامه بحمد الله سرد ثلاثة أجزاء متتابعة من الجزء الثامن والعشرين إلى الجزء الثلاثين من كتاب الله تعالى" if not long else "تقديرًا لإتمامه بحمد الله سرد ثلاثة أجزاء متتابعة من الجزء الثامن والعشرين إلى الجزء الثلاثين من كتاب الله تعالى مع إظهار التزام متميز واستمرار منتظم في الحفظ والمراجعة والسرد خلال الفترة المعتمدة"
    variables = ("--cert-primary:#0B5F5B;--cert-secondary:#8A4D22;--cert-accent:#B9954A;--cert-paper:#FBF7EB;--cert-ink:#18211E;"
                 + ("--cert-title-size:64px;--cert-student-size:58px;--cert-body-size:28px;--cert-logo-size:230px;" if long else "--cert-title-size:45px;--cert-student-size:39px;--cert-body-size:19px;--cert-logo-size:145px;")
                 + "--cert-border-width:2px;--cert-ornament-opacity:.72;--cert-font:'Noto Sans Arabic';")
    article = f'''<article class="halaqati-certificate cert-template-emerald" dir="rtl" style="{variables}">
{pattern_svg()}<div class="cert-frame"></div>{corner('c1')}{corner('c2')}{corner('c3')}{corner('c4')}{rosette('r1')}{rosette('l1')}
<header class="cert-head"><div class="cert-logo-slot"><img class="cert-logo" src="{logo2}" alt=""></div><div class="cert-org"><h3>مركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم</h3><h4>حلقة معاوية بن أبي سفيان</h4><div class="cert-orn-line"><span></span></div></div><div class="cert-logo-slot cert-logo-slot-center"><img class="cert-logo" src="{logo1}" alt=""><div class="cert-number cert-number-logo"><span class="cert-number-label">رقم الشهادة:</span><span class="cert-number-code" dir="ltr">HAL-CERT-2026-00001</span></div></div></header>
<div class="cert-kicker">شهادة تقدير وإنجاز</div><h1 class="cert-main-title">إتمام سرد أجزاء من القرآن الكريم</h1><p class="cert-intro">تتشرف حلقة معاوية بن أبي سفيان التابعة لـمركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم بمنح هذه الشهادة للطالب</p><div class="cert-name-box"><strong>{h(name)}</strong></div><p class="cert-achievement">{h(achievement)}</p><p class="cert-dua">سائلين الله تعالى أن يثبت القرآن في صدره، وأن يجعله له نورًا وهدى ورفعةً في الدنيا والآخرة.</p>
<div class="cert-dates"><span class="cert-date-hijri">16 ربيع الآخر 1448 هـ</span><span class="cert-date-gregorian">27 سبتمبر 2026م</span></div>
<div class="cert-signatures"><div class="cert-sign"><div class="cert-sign-line"></div><span>المحفظ</span><b>د. محمد سمير أبو يونس</b></div><div class="cert-stamp"><span>الختم الرسمي</span></div><div class="cert-sign"><div class="cert-sign-line"></div><span>المشرف</span><b>الشيخ محمد حلمي أبو عنزة</b></div></div>
<footer class="cert-footer"><div></div><div class="cert-verse">﴿ وَقُلْ رَبِّ زِدْنِي عِلْمًا ﴾</div><div></div></footer></article>'''
    js = r'''(function(){window.__HALAQATI_READY__=false;window.__HALAQATI_LAYOUT_OK__=true;
function lines(e){if(!e||!e.firstChild)return 0;var r=document.createRange();r.selectNodeContents(e);var rs=[...r.getClientRects()].filter(x=>x.width>.5&&x.height>.5),ys=[];rs.forEach(x=>{if(!ys.some(y=>Math.abs(y-x.top)<2))ys.push(x.top)});return ys.length||1}
function inside(e){if(!e)return true;var r=document.createRange();r.selectNodeContents(e);var t=r.getBoundingClientRect(),b=e.getBoundingClientRect();return t.left>=b.left-2&&t.right<=b.right+2}
function shrink(el,min,maxLines){if(!el)return;let s=parseFloat(getComputedStyle(el).fontSize)||18,g=0;while(g++<80&&s>min&&(lines(el)>maxLines||!inside(el))){s-=.5;el.style.fontSize=s+'px'}if(lines(el)>maxLines||!inside(el))window.__HALAQATI_LAYOUT_OK__=false;}
function flowNodes(){return ['.cert-kicker','.cert-main-title','.cert-intro','.cert-name-box','.cert-achievement','.cert-dua'].map(s=>document.querySelector(s)).filter(Boolean)}
function contentBottom(){let ks=flowNodes();return ks.length?Math.max(...ks.map(e=>e.getBoundingClientRect().bottom)):0}
function overlap(a,b,pad){if(!a||!b)return false;const A=a.getBoundingClientRect(),B=b.getBoundingClientRect();return !(A.bottom+pad<=B.top||B.bottom+pad<=A.top||A.right<=B.left||B.right<=A.left)}
function run(){shrink(document.querySelector('.cert-org h3'),18,1);shrink(document.querySelector('.cert-org h4'),16,1);shrink(document.querySelector('.cert-main-title'),30,2);shrink(document.querySelector('.cert-intro'),13,2);shrink(document.querySelector('.cert-name-box strong'),25,2);shrink(document.querySelector('.cert-achievement'),13,3);shrink(document.querySelector('.cert-dua'),11,2);
let ps=[...document.querySelectorAll('.cert-dates,.cert-signatures,.cert-footer')],protectedTop=Math.min(...ps.map(e=>e.getBoundingClientRect().top));let cert=document.querySelector('.halaqati-certificate'),requestedLogo=parseFloat(getComputedStyle(cert).getPropertyValue('--cert-logo-size'))||168,safeLogo=requestedLogo,lg=0;while(lg++<60&&contentBottom()>protectedTop-8&&safeLogo>174){safeLogo=Math.max(174,safeLogo-2);cert.style.setProperty('--cert-logo-size',safeLogo+'px')}if(safeLogo<requestedLogo)document.documentElement.dataset.logoAutoFit=Math.round(safeLogo)+'';let mins={'.cert-main-title':28,'.cert-intro':13,'.cert-name-box strong':26,'.cert-achievement':13,'.cert-dua':10.5},g=0;while(g++<48&&contentBottom()>protectedTop-8){Object.keys(mins).forEach(sel=>{let e=document.querySelector(sel);if(!e)return;let s=parseFloat(getComputedStyle(e).fontSize)||16,min=mins[sel];if(s>min)e.style.fontSize=Math.max(min,s-.5)+'px'})}if(contentBottom()>protectedTop-5)window.__HALAQATI_LAYOUT_OK__=false;let ordered=flowNodes();for(let i=0;i<ordered.length-1;i++){if(overlap(ordered[i],ordered[i+1],-1))window.__HALAQATI_LAYOUT_OK__=false}for(let j=0;j<ps.length-1;j++){if(overlap(ps[j],ps[j+1],2))window.__HALAQATI_LAYOUT_OK__=false}document.documentElement.dataset.layoutOk=window.__HALAQATI_LAYOUT_OK__?'1':'0';window.__HALAQATI_READY__=true;}
let done=()=>requestAnimationFrame(()=>requestAnimationFrame(run));if(document.fonts&&document.fonts.ready)document.fonts.ready.then(done).catch(done);else done();})();'''
    return f'''<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=1122,initial-scale=1"><style>{css.replace("'Cairo',Tahoma", "'Noto Sans Arabic','Cairo',Tahoma")}</style></head><body>{article}<script>{js}</script></body></html>'''

def report_html(css, logo1, logo2):
    names = [
        "إبراهيم عصمة البريم","أحمد حسن وصفي أبو عنزة","محمد أحمد عبد الرحمن أبو عودة","معتصم علاء أبو شب","عز الدين عمار أبو العلا","مالك مؤنس أبو حمد","عبد الرحمن أحمد أبو شرقية","رائد محمد أبو مرزوق",
        "يوسف محمود عبد الله الطويل","براء محمد عبد الكريم أبو مصطفى","مصعب أحمد حسن أبو زيد","عبد الله سامر خليل أبو عيشة","أنس محمد عبد الرحمن شاهين","حمزة مؤمن إبراهيم أبو سعدة","ياسين أحمد جميل أبو عودة","معاذ محمد حسن أبو خليل","أكرم عبد الرحمن يوسف أبو صفية","طارق سامح محمود أبو عاصي","أسامة عاطف عبد الله أبو سمرة","خالد محمد عبد الرحمن أبو شمالة",
        "محمود ياسر إبراهيم أبو جزر","سليم أحمد خليل أبو ماضي","محمد سامر حسن أبو كويك","عدي محمود أحمد أبو رحمة","إياد وسيم يوسف أبو خاطر","بلال أمجد محمد أبو العيش","أحمد خالد عبد الكريم أبو مصطفى","أنس وليد مؤنس أبو حمد","سفيان محمد ياسر أبو شنب","محمد هاني أحمد أبو طير","عبد الرحمن سامي محمود أبو عون","مؤمن محمد رائد أبو شعبان","يوسف أحمد محمد أبو هاشم","خالد سامر عبد الرحمن أبو خاطر","أمير محمود محمد أبو زيد","هاشم أحمد وليد أبو سمرة","زين الدين محمد سامي أبو شمالة","عمر ياسر عبد الرحمن أبو زيد","عبد الله أحمد محمود أبو طير","مصطفى رائد محمد أبو رحمة"
    ]
    rows=''.join(f'<tr><td class="compact">{i}</td><td class="name">{h(n)}</td><td class="num">{60-i%17}</td><td class="num">{22+(i%9)}</td><td class="num">{8+(i%6)}</td><td>{["ممتاز","جيد جدًا","جيد"][i%3]}</td><td class="num">{95-i%13:.0f}</td></tr>' for i,n in enumerate(names,1))
    vars="--report-primary:#0B5F5B;--report-gold:#B9954A;--report-text:#18211E;--report-scale:1;"
    page=f'''<section class="pro-report-page"><div class="pro-head"><div class="pro-head-logo"><img src="{logo2}"></div><div class="pro-head-title"><h1>التقرير الشامل للحلقة</h1><p>حلقة معاوية بن أبي سفيان - مركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم</p><div class="teacher">المحفظ: د. محمد سمير أبو يونس</div></div><div class="pro-head-logo"><img src="{logo1}"></div></div><div class="pro-meta"><span><b>الفترة:</b> 1 سبتمبر - 30 سبتمبر 2026</span><span><b>المسار:</b> الحفظ العام</span><span><b>عدد الطلاب:</b> 40</span></div><div class="pro-kpis"><div class="pro-kpi"><b>40</b><span>طالبًا</span></div><div class="pro-kpi"><b>58</b><span>لقاء</span></div><div class="pro-kpi"><b>1,224</b><span>صفحة حفظ</span></div><div class="pro-kpi"><b>910</b><span>صفحة مراجعة</span></div><div class="pro-kpi"><b>320</b><span>صفحة سرد</span></div><div class="pro-kpi"><b>92%</b><span>الحضور</span></div></div><div class="pro-section-title">تفاصيل الطلاب</div><div class="pro-table-wrap"><table class="pro-table"><thead><tr><th>#</th><th>اسم الطالب</th><th>الحفظ</th><th>المراجعة</th><th>السرد</th><th>التقييم</th><th>النقاط</th></tr></thead><tbody>{rows}</tbody></table></div><div class="pro-footer"><span>حلقاتي - RC13 Reports Studio</span><span>تقرير متجه قابل للبحث والتحديد</span></div></section>'''
    js=r'''(function(){window.__HALAQATI_READY__=false;window.__HALAQATI_LAYOUT_OK__=true;function fitTable(t){var hs=[...(t.tHead?.rows?.[0]?.cells||[])];if(!hs.length)return;var ws=[6,29,11,11,10,15,10],sum=ws.reduce((a,b)=>a+b,0),cg=document.createElement('colgroup');ws.forEach(w=>{var c=document.createElement('col');c.style.width=(w*100/sum)+'%';cg.appendChild(c)});t.insertBefore(cg,t.firstChild);[...t.querySelectorAll('.name')].forEach(c=>{var s=parseFloat(getComputedStyle(c).fontSize)||9.5,g=0;while(c.scrollWidth>c.clientWidth+1&&s>7.1&&g++<14){s-=.25;c.style.fontSize=s+'px'}})}function run(){document.querySelectorAll('.pro-table').forEach(fitTable);document.querySelectorAll('.pro-report-page').forEach(p=>{if(p.scrollWidth>p.clientWidth+3)window.__HALAQATI_LAYOUT_OK__=false});document.documentElement.dataset.layoutOk=window.__HALAQATI_LAYOUT_OK__?'1':'0';window.__HALAQATI_READY__=true;}let done=()=>requestAnimationFrame(()=>requestAnimationFrame(run));if(document.fonts&&document.fonts.ready)document.fonts.ready.then(done).catch(done);else done();})();'''
    return f'''<!doctype html><html lang="ar" dir="rtl" style="{vars}"><head><meta charset="utf-8"><style>{css.replace("'Cairo',Tahoma", "'Noto Sans Arabic','Cairo',Tahoma")}\n@page{{size:A4 landscape;margin:0!important}}</style></head><body><main class="pro-report" style="{vars}">{page}</main><script>{js}</script></body></html>'''

def browser_exe():
    for x in [os.getenv("CHROME_BIN"), shutil.which("google-chrome"), shutil.which("google-chrome-stable"), shutil.which("chromium"), shutil.which("chromium-browser")]:
        if x and Path(x).exists(): return x
    raise SystemExit("RC13 visual gate: Chromium/Chrome executable not found")

def run(cmd, check=True):
    p=subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if check and p.returncode: raise SystemExit(f"Command failed ({p.returncode}): {' '.join(map(str,cmd))}\n{p.stdout[-4000:]}")
    return p.stdout

def render_with_playwright(chrome, html_path, pdf_path, png_path=None, viewport=(1122,794)):
    try:
        from playwright.sync_api import sync_playwright
    except Exception as e:
        raise SystemExit("RC13 visual gate requires Python playwright: "+str(e))
    source=html_path.read_text(encoding="utf-8")
    with sync_playwright() as pw:
        browser=pw.chromium.launch(headless=True, executable_path=chrome, args=["--no-sandbox","--disable-dev-shm-usage","--disable-gpu"])
        page=browser.new_page(viewport={"width":viewport[0],"height":viewport[1]})
        page.set_content(source, wait_until="load", timeout=15000)
        try:
            page.wait_for_function("window.__HALAQATI_READY__ === true", timeout=15000)
        except Exception as e:
            browser.close(); raise SystemExit(f"Fixture ready timeout for {html_path.name}: {e}")
        ok=page.evaluate("document.documentElement.dataset.layoutOk")
        if ok!="1":
            details=page.evaluate("JSON.stringify({sw:document.documentElement.scrollWidth,cw:document.documentElement.clientWidth,sh:document.documentElement.scrollHeight,ch:document.documentElement.clientHeight})")
            browser.close(); raise SystemExit(f"DOM layout preflight failed for {html_path.name}: {details}")
        if png_path:
            page.screenshot(path=str(png_path), full_page=True)
        page.pdf(path=str(pdf_path), format="A4", landscape=True, print_background=True, prefer_css_page_size=True, display_header_footer=False, margin={"top":"0","bottom":"0","left":"0","right":"0"})
        browser.close()
def _pdf_reader(pdf):
    try:
        from pypdf import PdfReader
    except Exception as e:
        raise SystemExit("RC13 visual gate requires Python pypdf: " + str(e))
    try:
        return PdfReader(str(pdf), strict=False)
    except Exception as e:
        raise SystemExit(f"Cannot read PDF {pdf}: {e}")

def pdf_size_points(pdf):
    reader = _pdf_reader(pdf)
    if not reader.pages:
        raise SystemExit(f"PDF has no pages: {pdf}")
    box = reader.pages[0].mediabox
    w, h = float(box.width), float(box.height)
    return len(reader.pages), w, h, f"pypdf:{len(reader.pages)} pages {w:.2f}x{h:.2f} pt"

def extract_text(pdf):
    reader = _pdf_reader(pdf)
    return "\n".join((page.extract_text() or "") for page in reader.pages)

def canonical_text(value):
    # Chromium/PDF extraction differs slightly across Linux Chrome/font builds:
    # Arabic Presentation Forms, bidi marks, diacritics and glyph spacing can all
    # vary even when the text is genuinely selectable. Keep this normalization
    # conservative for diagnostics/readability, then use semantic token checks
    # below for the actual selectable-text assertion.
    value = unicodedata.normalize("NFKC", value or "")
    value = re.sub(r"\s*-\s*", "-", value)
    value = re.sub(r"[\s\u200e\u200f\ufeff]+", " ", value)
    return value.strip()

def _arabic_fold(value):
    value = unicodedata.normalize("NFKC", value or "")
    # Drop combining marks (harakat/tashkeel) because PDF extractors disagree on
    # whether they are emitted as separate glyphs. Fold common alef/ya variants.
    value = "".join(ch for ch in value if unicodedata.category(ch) != "Mn")
    value = value.translate(str.maketrans({
        "أ":"ا", "إ":"ا", "آ":"ا", "ٱ":"ا", "ى":"ي", "ـ":"",
    }))
    value = re.sub(r"[\u200e\u200f\u202a-\u202e\ufeff]", "", value)
    value = re.sub(r"[^\u0600-\u06FF0-9A-Za-z]+", " ", value)
    return re.sub(r"\s+", " ", value).strip()

def _ascii_fold(value):
    value = unicodedata.normalize("NFKC", value or "").upper()
    return re.sub(r"[^A-Z0-9]+", "", value)

def assert_selectable_text(extracted, expected, label):
    # Latin identifiers are deterministic after stripping separators.
    if re.search(r"[A-Za-z0-9]", expected) and not re.search(r"[\u0600-\u06FF]", expected):
        if _ascii_fold(expected) not in _ascii_fold(extracted):
            raise SystemExit(f"{label} selectable text missing: {expected!r}")
        return

    # For Arabic, require every meaningful word to be present in the extracted
    # text. This still proves real/selectable glyph content while avoiding false
    # failures from platform-dependent bidi ordering or spaces between glyph runs.
    folded = _arabic_fold(extracted)
    expected_folded = _arabic_fold(expected)
    # Some Chrome/font combinations emit a space between individual Arabic glyph
    # clusters. Compare each expected word against a compacted stream as well as
    # the normal word stream so genuine vector text does not fail on that quirk.
    compact = folded.replace(" ", "")
    words = [w.replace(" ", "") for w in expected_folded.split() if len(w.replace(" ", "")) >= 2]

    # pypdf/Chromium can expose Arabic glyph runs in visual order on some Linux
    # builds. Example observed on GitHub: أحمد -> حمدا, وصفي -> يصفو,
    # أبو -> بوا, عنزة -> ةعنز.  The text is still real/selectable; only the
    # extracted order inside that token changes. Keep exact matching first, then
    # accept a token only when it has the exact same multiset of characters.
    # This is deliberately token-scoped (not whole-stream anagram matching), so
    # a missing/extra/substituted Arabic character still fails the gate.
    # Build signatures from contiguous Arabic LETTER runs only.  pypdf can glue
    # table/rank digits to the visual-order Arabic run (for example ``2ةعنز``),
    # or glue a following score/rating to it.  Treat digits/punctuation as
    # boundaries instead of part of the Arabic token.  This keeps the fallback
    # strict: each accepted run still has exactly the same Arabic letters and
    # multiplicities as the expected word.
    def _arabic_letter_runs(value):
        runs, current = [], []
        for ch in value:
            is_arabic_letter = ("\u0600" <= ch <= "\u06FF" and unicodedata.category(ch).startswith("L"))
            if is_arabic_letter:
                current.append(ch)
            elif current:
                runs.append("".join(current)); current = []
        if current:
            runs.append("".join(current))
        return runs

    arabic_runs = [r for r in _arabic_letter_runs(folded) if len(r) >= 2]
    run_sigs = {"".join(sorted(r)) for r in arabic_runs}

    def _present(word):
        if word in compact:
            return True
        expected_runs = _arabic_letter_runs(word)
        if not expected_runs:
            return False
        target = expected_runs[0]
        if target in arabic_runs:
            return True
        return "".join(sorted(target)) in run_sigs

    missing = [w for w in words if not _present(w)]
    if missing:
        sample = folded[:800]
        raise SystemExit(
            f"{label} selectable text missing words {missing!r} from {expected!r}; "
            f"normalized extract sample={sample!r}"
        )

def verify_a4_landscape(pdf, exactly_one=None):
    pages,w,h,info=pdf_size_points(pdf)
    # PDF printers may report 841.68 x 595.20 or 841.89 x 595.28.
    if not (835 <= w <= 848 and 590 <= h <= 602 and w>h):
        raise SystemExit(f"Not A4 landscape: {pdf.name}: {w}x{h} pt")
    if exactly_one is True and pages!=1: raise SystemExit(f"Expected one page: {pdf.name}, got {pages}")
    return pages,w,h

def _selftest_selectable_normalization():
    # Reproduce the extraction variants seen between local Chromium and GitHub
    # hosted Chrome. These must pass without weakening the real-content checks.
    assert_selectable_text("أ ح م د   ح س ن   و ص ف ي   أ ب و   ع ن ز ة", "أحمد حسن وصفي أبو عنزة", "Selftest")
    assert_selectable_text("ﺃﺣﻤﺪ \u200fﺣﺴﻦ وﺻﻔﻲ أﺑﻮ ﻋﻨﺰة", "أحمد حسن وصفي أبو عنزة", "Selftest")
    # Reproduce the exact visual-order token extraction seen on GitHub Actions.
    assert_selectable_text("ةعنز بوا يصفو حسن حمدا", "أحمد حسن وصفي أبو عنزة", "Selftest-visual-order")
    # Report tables can attach the ranking digit directly to the surname and
    # numeric metrics directly to Arabic tokens; this exact form caused FIX3's
    # false failure on the hosted runner.
    assert_selectable_text("94 2ةعنز بوا يصفو حسن حمدا582410جيد93", "أحمد حسن وصفي أبو عنزة", "Selftest-report-row")
    assert_selectable_text("HAL - CERT - 2026 - 00001", "HAL-CERT-2026-00001", "Selftest")
    try:
        assert_selectable_text("ةعنز بوا يصفو حسن حمسا", "أحمد حسن وصفي أبو عنزة", "Selftest-negative")
    except SystemExit:
        pass
    else:
        raise SystemExit("Selectable text selftest failed to reject a missing Arabic word")

def main():
    _selftest_selectable_normalization()
    ap=argparse.ArgumentParser(); ap.add_argument("--out",default=str(ROOT/"build/rc13-fixtures")); ap.add_argument("--verify",action="store_true"); args=ap.parse_args()
    out=Path(args.out).resolve(); out.mkdir(parents=True,exist_ok=True)
    css_c=CERT_CSS.read_text(); css_r=REPORT_CSS.read_text(); logo1=image_data(CENTER_LOGO); logo2=image_data(HALAQA_LOGO)
    cert=out/"RC13_SAMPLE_CERTIFICATE.html"; cert.write_text(certificate_html(css_c,logo1,logo2),encoding="utf-8")
    cert_long=out/"RC13_STRESS_CERTIFICATE.html"; cert_long.write_text(certificate_html(css_c,logo1,logo2,True),encoding="utf-8")
    report=out/"RC13_SAMPLE_REPORT.html"; report.write_text(report_html(css_r,logo1,logo2),encoding="utf-8")
    chrome=browser_exe()
    cp=out/"RC13_SAMPLE_CERTIFICATE.pdf"; sp=out/"RC13_STRESS_CERTIFICATE.pdf"; rp=out/"RC13_SAMPLE_REPORT.pdf"
    render_with_playwright(chrome,cert,cp,out/"RC13_SAMPLE_CERTIFICATE.png")
    render_with_playwright(chrome,cert_long,sp,out/"RC13_STRESS_CERTIFICATE.png")
    render_with_playwright(chrome,report,rp,out/"RC13_SAMPLE_REPORT.png")
    p1=verify_a4_landscape(cp,True); p2=verify_a4_landscape(sp,True); pr=verify_a4_landscape(rp,None)
    if cp.stat().st_size>3_000_000: raise SystemExit(f"Certificate exceeds 3 MB: {cp.stat().st_size}")
    if sp.stat().st_size>3_000_000: raise SystemExit(f"Stress certificate exceeds 3 MB: {sp.stat().st_size}")
    ct=extract_text(cp); rt=extract_text(rp)
    # Guard against a raster-only PDF even before token assertions.
    if len(_arabic_fold(ct).replace(" ", "")) < 80:
        raise SystemExit("Certificate selectable Arabic extraction is unexpectedly sparse")
    if len(_arabic_fold(rt).replace(" ", "")) < 120:
        raise SystemExit("Report selectable Arabic extraction is unexpectedly sparse")
    for token in ["HAL-CERT-2026-00001","أحمد حسن وصفي أبو عنزة","إتمام سرد أجزاء من القرآن الكريم"]:
        assert_selectable_text(ct, token, "Certificate")
    for token in ["التقرير الشامل للحلقة","أحمد حسن وصفي أبو عنزة","40"]:
        assert_selectable_text(rt, token, "Report")
    result={"chrome":chrome,"certificate":{"bytes":cp.stat().st_size,"pages":p1[0],"page_points":[p1[1],p1[2]]},"stress_certificate":{"bytes":sp.stat().st_size,"pages":p2[0],"page_points":[p2[1],p2[2]]},"report":{"bytes":rp.stat().st_size,"pages":pr[0],"page_points":[pr[1],pr[2]]},"layout_preflight":"PASS","selectable_arabic":"PASS"}
    (out/"RC13_FIXTURE_RESULTS.json").write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
    print("RC13 browser/PDF fixture gate PASS")
    print(json.dumps(result,ensure_ascii=False,indent=2))

if __name__=="__main__": main()
