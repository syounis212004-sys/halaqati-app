#!/usr/bin/env python3
"""RC24.1 certificate render fixtures.

Renders the certificate with the REAL stylesheet (certificate.css) and the REAL fit/gate script
(certificate-fit.js) in Chromium, both full size and under the same scale transform the in-app preview uses,
and asserts:
  * the gate verdict matches the fixture's expectation (blank achievement must fail, everything else must pass);
  * the verdict is identical at full size and scaled (measurements are scale independent);
  * zone geometry: kicker directly under the ornament diamond, body stack in order and above the dates,
    dates above the signatures, signatures above the footer, nothing outside the A4 page;
  * the exported PDF is exactly one A4-landscape page.
"""
from pathlib import Path
import argparse, json, re, shutil, sys
from playwright.sync_api import sync_playwright

root = Path(__file__).resolve().parents[1]
assets = root / 'app/src/main/assets/documents'
css = (assets / 'certificate.css').read_text(encoding='utf-8')
js = (assets / 'certificate-fit.js').read_text(encoding='utf-8')
renderer = (root / 'app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt').read_text(encoding='utf-8')


def kotlin_svg(name):
    m = re.search(r'private fun ' + name + r'\([^)]*\): String = """(.*?)"""', renderer, re.S)
    return m.group(1) if m else ''


PATTERN = kotlin_svg('islamicPatternSvg')
CORNER = kotlin_svg('islamicCornerSvg')
ROSETTE = kotlin_svg('islamicSideRosetteSvg')

INTRO = 'تتشرف حلقة معاوية بن أبي سفيان التابعة لـمركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم بمنح هذه الشهادة للطالب'
DUA = 'سائلين الله تعالى أن يثبت القرآن في صدره، وأن يجعله له نورًا وهدًى ورفعةً في الدنيا والآخرة.'
LONG_ACH = 'تقديرًا لما بذله من جهد متميز ومتواصل في حفظ كتاب الله تعالى ومراجعته وإتقان تلاوته، ولما أظهره من انضباط وأخلاق عالية وحرص على حضور الحلقة والمشاركة الفاعلة طوال الفترة المحددة، سائلين الله له التوفيق والسداد.'
REAL_ACH = 'تقديرًا لإتمامه بحمد الله سرد ثلاثة أجزاء متتابعة من الجزء الثامن والعشرون إلى الجزء الثلاثون من كتاب الله تعالى'

fixtures = [
    dict(key='real-sard', name='حمزة عمر عبد الكريم أبو سبت', title='إتمام سرد أجزاء من القرآن الكريم', achievement=REAL_ACH, expected=True),
    dict(key='real-sard-numbered', name='حمزة عمر عبد الكريم أبو سبت', title='إتمام سرد أجزاء من القرآن الكريم', achievement=REAL_ACH, expected=True, number='HAL-CERT-2026-S-00001'),
    dict(key='long-name', name='عبد الرحمن محمد إسماعيل إبراهيم عبد الكريم أبو عودة الفلسطيني', title='شهادة تقدير وإنجاز في حفظ القرآن', achievement='تقديرًا لتميزه في حفظ كتاب الله تعالى ومواصلته مسيرة الحفظ بإتقان وانضباط', expected=True),
    dict(key='long-achievement', name='أحمد حسن وصفي أبو عنزة', title='شهادة تميز في القرآن الكريم', achievement=LONG_ACH, expected=True),
    dict(key='two-line-title', name='طالب اختبار', title='شهادة تقدير وتميز في حفظ ومراجعة أجزاء كثيرة من القرآن الكريم', achievement='تقديرًا لتميزه في حفظ كتاب الله تعالى.', expected=True),
    dict(key='max-type', name='محمد خالد يوسف الأحمد', title='إتمام حفظ القرآن الكريم', achievement='تقديرًا لإتمامه حفظ كتاب الله تعالى كاملًا بإتقان وتجويد في الحلقة', expected=True, title_size=64, student_size=58, body_size=28, logo=230),
    dict(key='no-intro-no-dua', name='طالب بدون مقدمة', title='شهادة تقدير', achievement='تقديرًا لتميزه في حفظ كتاب الله تعالى.', expected=True, intro='', dua=''),
    dict(key='number-top', name='طالب موضع الرقم', title='شهادة تقدير', achievement='تقديرًا لتميزه.', expected=True, numberPos='top', number='HAL-CERT-2026-S-00001'),
    dict(key='number-footer-start', name='طالب موضع الرقم', title='شهادة تقدير', achievement='تقديرًا لتميزه.', expected=True, numberPos='footer_start', number='HAL-CERT-2026-S-00001'),
    dict(key='number-footer-end', name='طالب موضع الرقم', title='شهادة تقدير', achievement='تقديرًا لتميزه.', expected=True, numberPos='footer_end', number='HAL-CERT-2026-S-00001'),
    dict(key='legacy-partial', name='شهادة قديمة', title='بيانات أصلية غير مكتملة', achievement='بيانات التصميم الأصلية غير متوفرة؛ هذه معاينة أرشيفية وليست إعادة اختراع للنص القديم.', expected=True),
    dict(key='impossible-achievement', name='طالب الاختبار', title='شهادة', achievement=(LONG_ACH + ' ') * 6, expected=False),
    dict(key='blank-achievement', name='طالب الاختبار', title='شهادة تقدير وإنجاز', achievement='', expected=False),
]


def number_html(position, number):
    code = f'<span class="cert-number-label">رقم الشهادة:</span><span class="cert-number-code" dir="ltr">{number}</span>'
    top = f'<div class="cert-number cert-number-top">{code}</div>' if position in ('top', 'header') else ''
    under = f'<div class="cert-number cert-number-logo">{code}</div>' if position == 'under_logo' else ''
    fstart = f'<div class="cert-number cert-number-footer">{code}</div>' if position == 'footer_start' else '<div></div>'
    fend = f'<div class="cert-number cert-number-right cert-number-footer">{code}</div>' if position == 'footer_end' else '<div></div>'
    return top, under, fstart, fend


def html(f):
    top, under, fstart, fend = number_html(f.get('numberPos', 'under_logo'), f.get('number', 'سيخصص عند الإصدار'))
    intro = f.get('intro', INTRO)
    dua = f.get('dua', DUA)
    intro_html = f'<p class="cert-intro">{intro}</p>' if intro else ''
    dua_html = f'<p class="cert-dua">{dua}</p>' if dua else ''
    cls = ' cert-num-top' if top else ''
    logo = '<div class="cert-logo cert-logo-placeholder">شعار</div>'
    sign = lambda role, nm: f'<div class="cert-sign"><div class="cert-sign-line"></div><span>{role}</span><b>{nm}</b></div>'
    v = (f"--cert-primary:#0b5f5b;--cert-secondary:#8a4d22;--cert-accent:#b9954a;--cert-paper:#fbf7eb;--cert-ink:#18211e;"
         f"--cert-title-size:{f.get('title_size', 44)}px;--cert-student-size:{f.get('student_size', 38)}px;--cert-body-size:{f.get('body_size', 20)}px;"
         f"--cert-logo-size:{f.get('logo', 168)}px;--cert-border-width:3px;--cert-ornament-opacity:.82;--cert-font:'Cairo',sans-serif;")
    corners = ''.join(CORNER.replace('$cls', c) for c in ('c1', 'c2', 'c3', 'c4'))
    rosettes = ROSETTE.replace('$cls', 'r1') + ROSETTE.replace('$cls', 'l1')
    return f'''<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><style>{css}</style></head><body>
<article class="halaqati-certificate cert-template-royal{cls}" dir="rtl" style="{v}">{PATTERN}<div class="cert-frame"></div>{corners}{rosettes}{top}
<div class="cert-main"><header class="cert-head"><div class="cert-logo-slot cert-logo-slot-halaqa">{logo}</div><div class="cert-org"><h3>مركز إبراهيم خليل الرحمن لتحفيظ القرآن الكريم</h3><h4>حلقة معاوية بن أبي سفيان</h4><div class="cert-orn-line"><span></span></div><div class="cert-kicker">شهادة تقدير وإنجاز</div></div><div class="cert-logo-slot cert-logo-slot-center">{logo}{under}</div></header>
<section class="cert-body"><h1 class="cert-main-title">{f['title']}</h1>{intro_html}<div class="cert-name-box"><strong>{f['name']}</strong></div><p class="cert-achievement">{f['achievement']}</p>{dua_html}</section></div>
<div class="cert-dates"><span class="cert-date-hijri">29 ربيع الآخر 1448 هـ</span><span class="cert-date-gregorian">10 أكتوبر 2026م</span></div>
<div class="cert-signatures">{sign('المحفظ', 'د. محمد سمير أبو يونس')}<div class="cert-stamp"><span>الختم الرسمي</span></div>{sign('المشرف', 'الشيخ محمد حلمي أبو عنزة')}</div>
<footer class="cert-footer">{fstart}<div class="cert-verse">﴿ وَقُلْ رَبِّ زِدْنِي عِلْمًا ﴾</div>{fend}</footer></article><script>{js}</script></body></html>'''


GEOMETRY_JS = '''() => {
  const P = document.querySelector('.halaqati-certificate').getBoundingClientRect();
  const S = P.width / 1122;
  const R = (sel) => { const e = document.querySelector(sel); if (!e) return null; const r = e.getBoundingClientRect();
    return { l: (r.left - P.left) / S, t: (r.top - P.top) / S, r: (r.right - P.left) / S, b: (r.bottom - P.top) / S }; };
  const errors = [];
  const diamond = R('.cert-orn-line span'), kicker = R('.cert-kicker');
  if (diamond && kicker) { const gap = kicker.t - diamond.b; if (gap < -6 || gap > 40) errors.push('kicker-not-under-diamond gap=' + gap.toFixed(1)); }
  const stack = ['.cert-main-title', '.cert-intro', '.cert-name-box', '.cert-achievement', '.cert-dua'].map(s => [s, R(s)]).filter(x => x[1]);
  for (let i = 0; i + 1 < stack.length; i++) if (stack[i][1].b > stack[i + 1][1].t + 1) errors.push('body-order ' + stack[i][0] + ' overlaps ' + stack[i + 1][0]);
  const head = R('.cert-head'), dates = R('.cert-dates'), sigs = R('.cert-signatures'), foot = R('.cert-footer');
  if (stack.length && head && stack[0][1].t < head.b - 1) errors.push('body-starts-inside-head');
  if (stack.length && dates && stack[stack.length - 1][1].b > dates.t + 1) errors.push('body-overlaps-dates');
  if (dates && sigs && dates.b > sigs.t + 1) errors.push('dates-overlap-signatures');
  if (sigs && foot && sigs.b > foot.t + 1) errors.push('signatures-overlap-footer');
  for (const [name, b] of stack) if (b.l < 40 || b.r > 1082) errors.push('outside-frame ' + name);
  return errors;
}'''


def gate_ok(g):
    return all(g.get(k) for k in ('ready', 'layout_ok', 'required_nodes_present', 'required_nodes_visible', 'required_nodes_inside_page', 'no_protected_overlap'))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--verify', action='store_true')
    ap.add_argument('--out', default='/tmp/rc24-certificate-fixtures')
    args = ap.parse_args()
    out = Path(args.out).resolve()
    out.mkdir(parents=True, exist_ok=True)
    cairo = assets / 'cairo.ttf'
    if cairo.exists():
        shutil.copy(cairo, out / 'cairo.ttf')
    executable = next((shutil.which(x) for x in ('chromium', 'chromium-browser', 'google-chrome', 'google-chrome-stable') if shutil.which(x)), None)
    # No system browser: fall back to Playwright's own Chromium (PLAYWRIGHT_BROWSERS_PATH).
    results = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True, executable_path=executable, args=['--no-sandbox'])
        page = browser.new_page(viewport={'width': 1122, 'height': 794})
        for f in fixtures:
            row = {'fixture': f['key'], 'expected_valid': f['expected']}
            for mode in ('full', 'scaled'):
                path = out / f"{f['key']}-{mode}.html"
                path.write_text(html(f), encoding='utf-8')
                page.goto(path.as_uri(), wait_until='load')
                page.evaluate('() => document.fonts && document.fonts.ready')
                if mode == 'scaled':
                    page.evaluate("() => { const c = document.querySelector('.halaqati-certificate'); c.style.transformOrigin = 'top left'; c.style.transform = 'scale(.58)'; c.style.position = 'relative'; }")
                page.wait_for_timeout(120)
                g = page.evaluate('() => window.__HALAQATI_CERTIFICATE_GATE__()')
                row[mode] = {'valid': gate_ok(g), 'invalid_sections': g['invalid_sections']}
                if f['expected']:
                    row[mode]['geometry'] = page.evaluate(GEOMETRY_JS)
                if mode == 'full':
                    page.screenshot(path=str(out / f"{f['key']}.png"))
                    if f['key'] == 'real-sard-numbered':
                        page.pdf(path=str(out / 'real-sard.pdf'), format='A4', landscape=True, print_background=True, margin={'top': '0', 'right': '0', 'bottom': '0', 'left': '0'})
            row['valid'] = row['full']['valid']
            results.append(row)
        browser.close()
    problems = []
    for r in results:
        if r['full']['valid'] != r['scaled']['valid']:
            problems.append(f"{r['fixture']}: verdict differs between full size and scaled preview")
        if r['valid'] != r['expected_valid']:
            problems.append(f"{r['fixture']}: expected valid={r['expected_valid']} got {r['valid']} {r['full']['invalid_sections']}")
        for mode in ('full', 'scaled'):
            for e in r[mode].get('geometry', []):
                problems.append(f"{r['fixture']} [{mode}]: {e}")
    pdf = out / 'real-sard.pdf'
    try:
        from pypdf import PdfReader
        rd = PdfReader(str(pdf))
        if len(rd.pages) != 1:
            problems.append(f'real-sard.pdf has {len(rd.pages)} pages (expected 1)')
        else:
            w, h = float(rd.pages[0].mediabox.width), float(rd.pages[0].mediabox.height)
            if not (abs(w - 841.9) < 3 and abs(h - 595.3) < 3):
                problems.append(f'real-sard.pdf page is {w:.0f}x{h:.0f}pt, expected A4 landscape')
    except ImportError:
        print('note: pypdf not installed; PDF page-size assertion skipped')
    (out / 'results.json').write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding='utf-8')
    for r in results:
        print(f"{r['fixture']:24s} valid={r['valid']!s:5s} expected={r['expected_valid']!s:5s} scaled={r['scaled']['valid']!s:5s} {r['full']['invalid_sections']}")
    if problems:
        print('RC24.1 certificate fixture gate FAILED', file=sys.stderr)
        for p_ in problems:
            print(' -', p_, file=sys.stderr)
        return 1
    print('RC24.1 certificate fixture gate PASS')
    return 0


if __name__ == '__main__':
    sys.exit(main())
