#!/usr/bin/env python3
"""RC24.1 — Certificate Layout & Archive UX Hotfix: static contract."""
from pathlib import Path
import json, sys
root = Path(__file__).resolve().parents[1]
errors = []


def read(rel):
    p = root / rel
    if not p.exists():
        errors.append('missing ' + rel)
        return ''
    return p.read_text(encoding='utf-8', errors='ignore')


css = read('app/src/main/assets/documents/certificate.css')
fit = read('app/src/main/assets/documents/certificate-fit.js')
renderer = read('app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt')
gate = read('app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateRenderGate.kt')
vector = read('app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt')
preview = read('app/src/main/java/com/mohammedsamir/halaqati/documents/HtmlDocumentPreview.kt')
ui = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
repo = read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
build = read('app/build.gradle.kts')
note = read('RELEASE_2_60_1_RC24_1_CERTIFICATE_LAYOUT_ARCHIVE_UX_AR.md')
contract = json.loads(read('migration/source-contract.json') or '{}')

checks = [
    # fixed zones, no flow/absolute mix for the body
    ('.cert-main{', css), ('.cert-body{', css), ('--cert-main-bottom', css), ('.cert-head{', css),
    ('.cert-signatures{', css), ('.cert-dates{', css), ('.cert-footer{', css),
    # fit script: idempotent, scale independent, self-running gate
    ('function layout()', fit), ('function scale()', fit), ('var st = layout();', fit),
    ('__HALAQATI_CERTIFICATE_GATE__', fit), ("'rc24.1'", fit), ('function reset()', fit),
    # renderer
    ('documents/certificate-fit.js', renderer), ('class="cert-main"', renderer), ('class="cert-body"', renderer),
    ('draft.intro.isBlank()', renderer), ('draft.dua.isBlank()', renderer),
    # gate error quality + race-free evaluation
    ('suspend fun awaitCertificateGate', gate), ('"overflow" ->', gate),
    ('awaitCertificateGate(view)', vector), ('awaitCertificateGate(view)', preview),
    # archive UX
    ('CertificateArchiveRow(', ui), ('CertificateActionButton(', ui), ('fun deleteLocalArchive', ui), ('fun setHidden', ui),
    ('حذف نهائي', ui), ('إخفاء من القائمة', ui), ('حذف من هذا الجهاز', ui), ('إصلاح التنسيق وفحص A4', ui),
    ('"voided" to "ملغاة', ui), ('"legacy" to "قديمة', ui), ('"hidden" to "مخفية', ui),
    ('fun deleteCertificate', repo), ('removeJsonArrayCacheRow', repo),
    ('2.60.1', note), ('digest_search_path', note),
]
for token, text in checks:
    if token not in text:
        errors.append('missing RC24.1 marker: ' + token)
# regressions we explicitly fixed
if 'protectedTop-8' in renderer or 'safeLogo>174' in renderer:
    errors.append('old measuring script still embedded in the renderer')
if 'position:absolute;top:0;width:220px' in css:
    errors.append('old absolute logo slots are back')
if 'CertificateStudioHeader(\n            version = BuildConfig.VERSION_NAME,\n            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),' in ui:
    errors.append('pinned (non-scrolling) certificate header is back')
if contract.get('native_version') not in ('2.60.1', '2.60.2', '2.60.3', '2.60.4', '2.61.0', '2.61.1', '2.62.0', '2.62.1', '2.62.2', '2.63.0', '2.63.1', '2.63.2', '2.63.3', '2.63.4', '2.63.5', '2.63.6', '2.63.7','2.63.8','2.63.9', '2.63.10', '2.63.11', '2.63.12','2.63.13','2.63.14', '2.63.15', '2.63.16', '2.63.17', '2.63.18', '2.63.19', '2.63.20', '2.63.21', '2.63.22', '2.63.23', '2.63.24', '2.63.25', '2.63.26', '2.63.27', '2.63.28', '2.63.29', '2.63.30', '2.63.31', '2.63.32', '2.63.33', '2.63.34', '2.63.35', '2.63.36', '2.63.37', '2.63.38', '2.63.39', '2.63.40', '2.63.41', '2.63.42', '2.63.43', '2.63.44', '2.63.45', '2.63.46', '2.63.47', '2.63.48', '2.63.49', '2.63.50','2.64.0', '2.65.0', '2.66.0','2.67.0','2.68.0','2.69.0','2.70.0','2.71.0','2.72.0','2.73.0','2.74.0','2.75.0','2.76.0','2.77.0','2.78.0','2.79.0','2.80.0','2.81.0','2.82.0','2.83.0','2.84.0','2.85.0','2.86.0','2.87.0','2.88.0','2.89.0','2.89.1','2.89.2','2.89.3','2.89.4','2.89.5', '2.89.6', '2.89.7', '2.89.8', '2.89.9', '2.89.10', '2.89.11', '2.89.12', '2.89.13', '2.89.14', '2.89.15', '2.89.16', '2.89.18', '2.89.19', '2.89.20', '2.89.21', '2.89.22', '2.89.23', '2.89.24', '2.89.25', '2.89.26', '2.89.27', '2.89.28', '2.89.29', '2.89.30', '2.89.31', '2.89.32', '2.89.33', '2.90.0', '2.90.1', '2.90.2', '2.90.3', '2.90.4', '2.90.5', '2.90.6','2.90.7','2.90.8','2.90.9','2.90.10','2.90.11', '2.90.12', '2.91.0','2.91.1','2.91.2','2.91.3','2.91.5','2.82.0'):
    errors.append('source contract native_version unexpected')
if errors:
    print('RC24.1 certificate layout/archive gate FAILED')
    for e in errors:
        print(' -', e)
    sys.exit(1)
print('RC24.1 certificate layout/archive gate PASS')
