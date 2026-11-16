#!/usr/bin/env python3
from pathlib import Path
import json,sys,re
root=Path(__file__).resolve().parents[1]
errors=[]
def read(rel):
    p=root/rel
    if not p.exists(): errors.append('missing '+rel); return ''
    return p.read_text(encoding='utf-8',errors='ignore')
core=read('app/src/main/java/com/mohammedsamir/halaqati/model/CertificateCore.kt')+read('app/src/main/java/com/mohammedsamir/halaqati/model/CertificateRules.kt')
renderer=read('app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt')
css=read('app/src/main/assets/documents/certificate.css')
fit_js=read('app/src/main/assets/documents/certificate-fit.js')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
ui=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
vector=read('app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt')
release_note=read('RELEASE_2_60_0_RC24_CERTIFICATE_ARCHIVE_RENDERING_CORE_AR.md')
migration=read('migration/20261009_v2600_rc24_certificate_archive_rendering_core.sql')
contract=json.loads(read('migration/source-contract.json') or '{}')
checks=[
 ('halaqati_certificate_render_v2',core),
 ('CertificateRevisionRow',core),('CertificateSeriesRow',core),
 ('cert-kicker',renderer),('cert-main-title',renderer),('cert-name-box',renderer),('cert-achievement',renderer),
 ('certificate-fit.js',renderer),
 ('__HALAQATI_CERTIFICATE_GATE__',fit_js),('render_revision',fit_js),
 ('margin:6px auto 0',css),
 ('rc24_issue_certificate',repo),('rc24_create_certificate_revision',repo),('rc24_void_certificate',repo),('rc24_import_legacy_certificate',repo),
 ('archive_v4',ui),('certificateId',ui),('revisionNo',ui),
 ('إنشاء نسخة مصححة',ui),('إلغاء رسمي',ui),('إضافة سجل أرشيف قديم',ui),('رموز الشهادات',ui),
 ('requireCertificateGateIfNeeded',vector),
 ('metadata_only_no_pdf_no_assets',migration),('legacy_partial',migration),
 ('لا تُرفع إلى Supabase',release_note),('v1/v2/v3',release_note),
]
for token,text in checks:
    if token not in text: errors.append('missing RC24 marker: '+token)
if 'byNumber' in ui[ui.find('object NativeCertificate'):ui.find('fun exportArchiveIndex')]: errors.append('local archive still dedupes by number only')
for t in ['certificate_series','certificate_revisions','certificate_number_counters']:
    if t not in contract.get('tables',[]): errors.append('source contract missing table '+t)
for r in ['rc24_issue_certificate','rc24_create_certificate_revision','rc24_void_certificate','rc24_import_legacy_certificate','rc24_next_certificate_number']:
    if r not in contract.get('rpc',[]): errors.append('source contract missing rpc '+r)
if contract.get('native_version') not in ('2.60.4','2.61.0','2.61.1','2.62.0','2.62.1','2.62.2','2.63.0','2.63.1','2.63.2','2.63.3','2.63.4','2.63.5','2.63.6','2.63.7','2.63.8','2.63.9','2.63.10','2.63.11','2.63.12','2.63.13','2.63.14','2.63.15','2.63.16','2.63.17','2.63.18','2.63.19', '2.63.20', '2.63.21', '2.63.22', '2.63.23', '2.63.24', '2.63.25', '2.63.26', '2.63.27', '2.63.28', '2.63.29', '2.63.30', '2.63.31', '2.63.32', '2.63.33', '2.63.34', '2.63.35', '2.63.36', '2.63.37', '2.63.38', '2.63.39', '2.63.40', '2.63.41', '2.63.42', '2.63.43', '2.63.44', '2.63.45', '2.63.46', '2.63.47', '2.63.48', '2.63.49', '2.63.50','2.64.0', '2.65.0', '2.66.0','2.67.0','2.68.0','2.69.0','2.70.0','2.71.0','2.72.0','2.73.0','2.74.0','2.75.0','2.76.0','2.77.0','2.78.0','2.79.0','2.80.0','2.81.0','2.82.0','2.83.0','2.84.0','2.85.0','2.86.0','2.87.0','2.88.0','2.89.0','2.89.1','2.89.2','2.89.3','2.89.4','2.89.5', '2.89.6', '2.89.7', '2.89.8', '2.89.9', '2.89.10', '2.89.11', '2.89.12', '2.89.13', '2.89.14', '2.89.15', '2.89.16', '2.89.18', '2.89.19', '2.89.20', '2.89.21', '2.89.22', '2.89.23', '2.89.24', '2.89.25', '2.89.26', '2.89.27', '2.89.28', '2.89.29', '2.89.30', '2.89.31', '2.89.32', '2.89.33', '2.90.0', '2.90.1', '2.90.2', '2.90.3', '2.90.4', '2.90.5', '2.90.6','2.90.7','2.90.8','2.90.9','2.90.10','2.90.11', '2.90.12','2.82.0'): errors.append('native version not 2.60.4/2.61.0')
if errors:
    print('RC24 certificate archive/rendering gate FAILED')
    [print(' -',e) for e in errors]; sys.exit(1)
print('RC24 certificate archive/rendering gate PASS')
