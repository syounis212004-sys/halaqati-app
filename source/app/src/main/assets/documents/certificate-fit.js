/* Halaqati RC24.1 — certificate auto-fit + render gate.
 *
 * Design rules
 *  - Everything is measured in certificate pixels (the 1122x794 page), never in screen pixels, so the
 *    result is identical in the scaled preview, the full-size export and the printed PDF.
 *  - layout() is idempotent: it resets every inline size it touched and fits again. The gate calls it
 *    itself, so the answer never depends on when the page finished loading.
 *  - Text is shrunk (never clipped or hidden) until it fits its zone. Only content that cannot fit even
 *    at the readable floor is reported, with the exact section name.
 */
(function () {
  'use strict';
  var REVISION = 'rc24.1';
  var PAGE_W = 1122, PAGE_H = 794;
  var state = { ready: false, layoutOk: true, problems: [] };
  window.__HALAQATI_READY__ = false;
  window.__HALAQATI_LAYOUT_OK__ = true;

  function q(sel) { return document.querySelector(sel); }
  function qa(sel) { return Array.prototype.slice.call(document.querySelectorAll(sel)); }
  function page() { return q('.halaqati-certificate'); }
  function scale() {
    var p = page();
    if (!p) return 1;
    var w = p.offsetWidth || PAGE_W;
    var r = p.getBoundingClientRect();
    return r.width > 0 ? r.width / w : 1;
  }
  function box(el) {
    var P = page().getBoundingClientRect(), S = scale(), r = el.getBoundingClientRect();
    return { l: (r.left - P.left) / S, t: (r.top - P.top) / S, r: (r.right - P.left) / S, b: (r.bottom - P.top) / S, w: r.width / S, h: r.height / S };
  }
  function hasText(el) { return !!el && String(el.textContent || '').replace(/\s+/g, '').length > 0; }
  function visible(el) {
    if (!el) return false;
    var s = getComputedStyle(el), r = el.getBoundingClientRect();
    return s.display !== 'none' && s.visibility !== 'hidden' && Number(s.opacity || 1) > 0 && r.width > 1 && r.height > 1;
  }
  function lineCount(el) {
    if (!hasText(el)) return 0;
    var cs = getComputedStyle(el);
    var lh = parseFloat(cs.lineHeight);
    if (!isFinite(lh) || lh <= 0) lh = (parseFloat(cs.fontSize) || 16) * 1.4;
    var pv = (parseFloat(cs.paddingTop) || 0) + (parseFloat(cs.paddingBottom) || 0);
    return Math.max(1, Math.round((el.clientHeight - pv) / lh));
  }
  function wideOverflow(el) { return !!el && el.scrollWidth > el.clientWidth + 1; }
  function fontOf(el) { return parseFloat(getComputedStyle(el).fontSize) || 16; }

  /* shrink font by 0.5px until lines <= maxLines and no horizontal overflow, never below min */
  function fit(el, min, maxLines) {
    if (!el || !hasText(el)) return true;
    var s = fontOf(el), guard = 0;
    function bad() { return (maxLines && lineCount(el) > maxLines) || wideOverflow(el); }
    while (guard++ < 120 && s > min && bad()) { s -= 0.5; el.style.fontSize = s + 'px'; }
    return !bad();
  }

  var FAMILIES = [
    ['.cert-main-title', 28], ['.cert-intro', 14], ['.cert-name-box strong', 24],
    ['.cert-achievement', 14], ['.cert-dua', 11]
  ];
  var FITTED = ['.cert-org h3', '.cert-org h4', '.cert-kicker', '.cert-main-title', '.cert-intro',
    '.cert-name-box strong', '.cert-achievement', '.cert-dua', '.cert-sign b', '.cert-sign span'];

  function reset() {
    FITTED.forEach(function (sel) { qa(sel).forEach(function (e) { e.style.fontSize = ''; }); });
    var p = page();
    if (p) {
      if (!p.dataset.logoReq) p.dataset.logoReq = String(parseFloat(getComputedStyle(p).getPropertyValue('--cert-logo-size')) || 168);
      p.style.setProperty('--cert-logo-size', p.dataset.logoReq + 'px');
    }
  }
  function overflow() {
    var b = q('.cert-body');
    return b ? (b.scrollHeight - b.clientHeight) : 0;
  }
  function problem(section, why) { state.problems.push(section + ':' + why); state.layoutOk = false; }

  function layout() {
    state.problems = [];
    state.layoutOk = true;
    var p = page();
    if (!p) { state.layoutOk = false; state.problems.push('page:missing'); return state; }
    reset();

    /* 1) every text block fits its own line budget */
    fit(q('.cert-org h3'), 18, 1);
    fit(q('.cert-org h4'), 16, 1);
    fit(q('.cert-kicker'), 13, 1);
    fit(q('.cert-main-title'), 28, 2);
    fit(q('.cert-intro'), 14, 2);
    var strong = q('.cert-name-box strong');
    if (strong && !fit(strong, 30, 1)) { strong.style.fontSize = ''; fit(strong, 24, 2); }
    fit(q('.cert-achievement'), 14, 3);
    fit(q('.cert-dua'), 11, 2);
    qa('.cert-sign b').forEach(function (e) { fit(e, 11, 1); });
    qa('.cert-sign span').forEach(function (e) { fit(e, 10, 1); });

    /* 2) the body zone must hold all of it: logos give way first, then the text families */
    var logoReq = parseFloat(p.dataset.logoReq) || 168;
    var logo = logoReq, logoFloor = Math.min(logoReq, 140), g = 0;
    while (g++ < 80 && overflow() > 0.5 && logo > logoFloor) {
      logo = Math.max(logoFloor, logo - 4);
      p.style.setProperty('--cert-logo-size', logo + 'px');
    }
    g = 0;
    while (g++ < 120 && overflow() > 0.5) {
      var shrunk = false;
      FAMILIES.forEach(function (f) {
        var e = q(f[0]);
        if (!e || !hasText(e)) return;
        var s = fontOf(e);
        if (s > f[1]) { e.style.fontSize = Math.max(f[1], s - 0.5) + 'px'; shrunk = true; }
      });
      if (!shrunk) break;
    }
    if (logo < logoReq) document.documentElement.dataset.logoAutoFit = String(Math.round(logo)); else delete document.documentElement.dataset.logoAutoFit;

    /* 3) what is still wrong now cannot be repaired automatically */
    if (overflow() > 0.5) {
      var ach = q('.cert-achievement');
      problem(ach && lineCount(ach) >= 3 ? 'achievement' : 'body', 'overflow');
    }
    /* readability limits: more lines than this means the text is too long for a certificate */
    [['.cert-main-title', 'title', 2], ['.cert-name-box strong', 'student', 2], ['.cert-achievement', 'achievement', 4],
     ['.cert-intro', 'intro', 3], ['.cert-dua', 'dua', 3], ['.cert-org h3', 'center', 1], ['.cert-org h4', 'halaqa', 1]].forEach(function (row) {
      var e = q(row[0]);
      if (!e || !hasText(e)) return;
      if (wideOverflow(e) || lineCount(e) > row[2]) problem(row[1], 'overflow');
    });
    return state;
  }

  function fontsSettled() { return !document.fonts || document.fonts.status !== 'loading'; }

  /* true only when the boxes overlap by more than `tol` certificate pixels (touching edges are fine) */
  function intersects(a, b, tol) {
    tol = tol || 0;
    return !(a.b <= b.t + tol || b.b <= a.t + tol || a.r <= b.l + tol || b.r <= a.l + tol);
  }

  window.__HALAQATI_CERTIFICATE_GATE__ = function () {
    var st = layout();
    var p = page();
    var invalid = st.problems.slice();
    var present = true, shown = true, inside = true, noOverlap = true;
    if (!p) {
      return { ready: false, layout_ok: false, required_nodes_present: false, required_nodes_visible: false,
        required_nodes_inside_page: false, no_protected_overlap: false, invalid_sections: ['page:missing'], render_revision: REVISION };
    }
    var protectedBoxes = qa('.cert-dates,.cert-signatures,.cert-footer,.cert-logo-slot,.cert-number-top').filter(visible).map(box);
    var required = [['kicker', '.cert-kicker'], ['title', '.cert-main-title'], ['student', '.cert-name-box strong'], ['achievement', '.cert-achievement']];
    var optional = [['intro', '.cert-intro'], ['dua', '.cert-dua']];
    function checkNode(name, sel, mustExist) {
      var e = q(sel);
      if (!e) { if (mustExist) { present = false; invalid.push(name + ':missing'); } return; }
      if (!hasText(e)) { if (mustExist) { present = false; invalid.push(name + ':missing'); } return; }
      if (!visible(e)) { shown = false; invalid.push(name + ':hidden'); return; }
      var b = box(e);
      if (b.l < -1 || b.t < -1 || b.r > PAGE_W + 1 || b.b > PAGE_H + 1) { inside = false; invalid.push(name + ':outside'); }
      for (var i = 0; i < protectedBoxes.length; i++) {
        if (intersects(b, protectedBoxes[i], 1.5)) { noOverlap = false; invalid.push(name + ':overlap'); break; }
      }
    }
    required.forEach(function (r) { checkNode(r[0], r[1], true); });
    optional.forEach(function (r) { checkNode(r[0], r[1], false); });

    var settled = fontsSettled();
    var layoutOk = st.layoutOk;
    document.documentElement.dataset.layoutOk = layoutOk ? '1' : '0';
    window.__HALAQATI_LAYOUT_OK__ = layoutOk;
    return {
      ready: settled,
      layout_ok: layoutOk,
      required_nodes_present: present,
      required_nodes_visible: shown,
      required_nodes_inside_page: inside,
      no_protected_overlap: noOverlap,
      invalid_sections: invalid.filter(function (v, i, a) { return a.indexOf(v) === i; }),
      render_revision: REVISION
    };
  };

  function run() {
    layout();
    document.documentElement.dataset.layoutOk = state.layoutOk ? '1' : '0';
    window.__HALAQATI_LAYOUT_OK__ = state.layoutOk;
    state.ready = true;
    window.__HALAQATI_READY__ = true;
  }
  function finish() { requestAnimationFrame(function () { requestAnimationFrame(run); }); }
  if (document.fonts && document.fonts.ready) {
    document.fonts.ready.then(finish, finish);
    /* a late font swap must re-fit once more */
    document.fonts.addEventListener && document.fonts.addEventListener('loadingdone', function () { if (state.ready) layout(); });
  } else finish();
})();
