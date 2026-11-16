#!/usr/bin/env python3
from pathlib import Path
import re, sys
ROOT=Path(__file__).resolve().parents[1]

def read(p):
    q=ROOT/p
    assert q.exists(), f"missing {p}"
    return q.read_text(errors="replace")

def need(text,tok,msg): assert tok in text,msg

def nope(text,tok,msg): assert tok not in text,msg

build=read("app/build.gradle.kts")
need(build,'versionCode = 25402','RC13 versionCode missing')
need(build,'versionName = "2.54.2"','RC13 versionName missing')
need(build,'prepareCairoFont','Cairo asset build hook missing')

vr=read("app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt")
for t in ['createPrintDocumentAdapter','PrintAttributes.MediaSize.ISO_A4','HalaqatiWebViewPdfWriter','__HALAQATI_LAYOUT_OK__','settings.blockNetworkLoads = true','renderToSeekableFile','saveHtmlPdfToDownloads','persistCompletedPdfToDownloads','MediaStore.Downloads.EXTERNAL_CONTENT_URI','MediaStore.MediaColumns.IS_PENDING','openOutputStream(uri, "w")','verifyStoredPdf','android-webview-raster-fallback-v244','verified-mediastore','MIN_VALID_PDF_BYTES','PDF_MAGIC']: need(vr,t,'vector renderer missing '+t)
need(read("app/src/main/java/android/print/HalaqatiWebViewPdfWriter.java"),'adapter.onWrite','direct vector PDF writer missing')
for t in ['DIRECT_WRITE_TIMEOUT_MS','COMPATIBILITY_TIMEOUT_MS','exportMutex','shouldSkipDirectChromium','renderCompatibilityToFile','captureWebViewSegment','PdfDocument','DocumentExportPhase.COMPATIBILITY']: need(vr,t,'RC14 v2.44 compatibility fallback missing '+t)
need(read("app/src/main/java/android/print/HalaqatiWebViewPdfWriter.java"),'class Handle','cancellable direct PDF writer handle missing')

preview=read("app/src/main/java/com/mohammedsamir/halaqati/documents/HtmlDocumentPreview.kt")
for t in ['PreviewRenderState','width=device-width','__HALAQATI_APPLY_PREVIEW_FIT__','transform=\'scale(\'+scale+\')\'','baseW=cert?1122','overflow-x:auto']: need(preview,t,'v2.44-style preview fit contract missing '+t)
nope(preview,'view.zoomBy','preview must scale the A4 document itself, not WebView zoom')
nope(preview,'setInitialScale(','preview must not depend on OEM initial-scale heuristics')

cert=read("app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt")
for t in ['certGirihPattern','cert-number-logo','cert-date-hijri','cert-date-gregorian','cert-signatures','cert-verse','autoFitScript','flowNodes','logoAutoFit','protectedNodes[j]']: need(cert,t,'certificate v2.44/layout-safety parity missing '+t)
nope(cert,'class="cert-content"','certificate DOM must stay aligned with v2.44 flow')
css=read("app/src/main/assets/documents/certificate.css")
for t in ['@page{size:A4 landscape','297mm!important','210mm!important','.cert-name-box','.cert-dates','.cert-signatures','bottom:190px;display:flex','bottom:78px;display:grid']: need(css,t,'certificate CSS/layout-zone contract missing '+t)
need(css,'transform:translateY(-10px)','certificate award badge must keep the RC14 visual lift')

reports=read("app/src/main/java/com/mohammedsamir/halaqati/documents/ReportHtmlRenderer.kt")
for t in ['HALAQA','LEADERS','POINTS','ATTENDANCE','RECITATIONS','STUDENTS','general_hifz','tathbit','reportFitScript']: need(reports,t,'reports parity missing '+t)
rcss=read("app/src/main/assets/documents/report.css")
for t in ['display:table-header-group','break-inside:avoid','.podium','.student-report-card']: need(rcss,t,'report CSS contract missing '+t)

screens=read("app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt")
for t in ['ReportStudioScrollableSurface','ReportStudioLastOption','HtmlDocumentPreview','CertificateHtmlRenderer.build','VectorDocumentRenderer.saveHtmlPdfToDownloads','subdirectory = "Reports"','subdirectory = "Certificates"','ReportHtmlRenderer.build']: need(screens,t,'studio integration missing '+t)
nope(screens,'fun previewBitmap(','old certificate bitmap preview still active')
nope(screens,'fun writePdf(','old certificate Canvas PDF writer still active')
nope(screens,'object NativePdf {','legacy report Canvas PDF path still present')
nope(screens,'android.graphics.pdf.PdfDocument','legacy PdfDocument import still present in ReportsAndTools')
nope(screens,'val createPdf = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"))','report PDF must not pre-create a SAF placeholder')
nope(screens,'val createSingle = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf"))','certificate PDF must not pre-create a SAF placeholder')
assert not (ROOT/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportStudioPdf.kt').exists(),'legacy ReportStudioPdf.kt still present'

repo=read("app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt")
for t in ['reportNotes','start_surah','end_ayah','selection_data']: need(repo,t,'report source contract missing '+t)
img=read("app/src/main/java/com/mohammedsamir/halaqati/documents/DocumentImageEncoder.kt")
for t in ['createScaledBitmap','Bitmap.CompressFormat.JPEG','Bitmap.CompressFormat.PNG']: need(img,t,'image preprocessing missing '+t)

# The preserved v2.44 implementation remains the golden master/reference.
for p in ['migration/legacy-web-v2.44.0/src/features/certificates/runtime.js','migration/legacy-web-v2.44.0/src/styles/certificates.css','migration/legacy-web-v2.44.0/src/features/reports/runtime.js','migration/legacy-web-v2.44.0/src/features/reports/vector-pdf-runtime.js']:
    assert (ROOT/p).exists(), 'missing v2.44 golden master '+p

# No Supabase storage upload path is allowed in certificate/report document code.
for p in ['app/src/main/java/com/mohammedsamir/halaqati/documents/CertificateHtmlRenderer.kt','app/src/main/java/com/mohammedsamir/halaqati/documents/ReportHtmlRenderer.kt','app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt']:
    t=read(p).lower(); assert 'storage/v1/object' not in t and 'supabase.storage' not in t, 'document renderer must not upload binary files'

fetch=read('scripts/fetch-cairo-font.py')
need(fetch,'app/src/main/assets/documents/cairo.ttf','Cairo document asset not fetched')
need(fetch,'app/src/main/res/font/cairo.ttf','Cairo Compose font not fetched')
fixture=read('scripts/rc13-render-fixtures.py')
need(fixture,'DOM layout preflight failed','browser fixture layout gate missing')
need(fixture,'from pypdf import PdfReader','self-contained PDF inspection missing')
need(fixture,'def assert_selectable_text','platform-robust selectable text gate missing')
need(fixture,'_arabic_fold','Arabic PDF extraction normalization missing')
need(fixture,'selectable Arabic extraction is unexpectedly sparse','raster-only PDF guard missing')
need(fixture,'_selftest_selectable_normalization','cross-runner PDF text normalization self-test missing')
nope(fixture,'["pdfinfo"','external pdfinfo dependency must not be required')
nope(fixture,'["pdftotext"','external pdftotext dependency must not be required')
print('RC13 Document & Reports Studio source gate PASS')
