#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]

def read(rel):
    p=root/rel
    if not p.exists(): raise SystemExit('RC12.2 FIX1 FAIL: missing '+rel)
    return p.read_text(encoding='utf-8',errors='ignore')

def need(cond,msg):
    if not cond: raise SystemExit('RC12.2 FIX1 FAIL: '+msg)

build=read('app/build.gradle.kts')
ui=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
settings=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SettingsParitySections.kt')
updater=read('app/src/main/java/com/mohammedsamir/halaqati/ui/update/NativeAppUpdater.kt')
manifest=read('app/src/main/AndroidManifest.xml')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
core=read('app/src/main/java/com/mohammedsamir/halaqati/model/CertificateCore.kt')
contract=read('migration/source-contract.json')
legacy_css=read('migration/legacy-web-v2.44.0/src/styles/certificates.css')
legacy_runtime=read('migration/legacy-web-v2.44.0/src/features/certificates/runtime.js')

need('versionCode = 25402' in build and 'versionName = "2.54.2"' in build,'FIX1 version')
need('"native_version": "2.54.2"' in contract,'source contract version')
for value,label in [('emerald','الرسمي الفيروزي'),('royal','الملكي الأخضر'),('gold','الذهبي الفاخر'),('blue','الأزرق الرسمي'),('minimal','الكلاسيكي الهادئ'),('burgundy','العنابي')]:
    need(f'"{value}" to "{label}"' in ui, f'certificate template {value}')
for kind in ('"sard" to "إتمام سرد"','"khatam" to "ختم القرآن"','"monthly" to "التميز الشهري"','"ideal" to "الطالب المثالي"','"rank" to "المراكز الأولى"'):
    need(kind in ui,'legacy certificate types')

# Visual-parity guard: verify the structural elements lost in the first native renderer are back.
for token in (
    'استوديو تصميم الشهادة','CertificateTemplateCard','CertificateEditableTextField',
    'شهادة تقدير وإنجاز','draft.halaqaName','certificateGregorianDate',
    'الختم الرسمي','﴿ وَقُلْ رَبِّ زِدْنِي عِلْمًا ﴾','drawCircleImage',
    'drawCertificateOrnaments','Footer verse is a separate branded badge',
): need(token in ui,'visual parity token '+token)
need('alpha = (18 * strength)' in ui,'Girih background must stay faint')
need('96-unit rhythm' not in ui,'old wallpaper-like ornament implementation must be gone')
need('.coerceAtMost(142f)' not in ui,'logo size slider must not be visually capped below v2.44 range')
need('val logoTop = 42f' in ui and 'logoTop + logoSize + 7f' in ui,'certificate number must follow actual logo size')
need('.cert-name-box' in legacy_css and '.cert-dates' in legacy_css and '.cert-signatures' in legacy_css and '.cert-verse' in legacy_css,'v2.44 golden CSS anchors retained')
need('certificateInnerHtml' in legacy_runtime,'v2.44 golden renderer retained for parity reference')

need('studentCategoryFilter' in ui and 'categoryNameOf' in ui and 'ترتيب/فلترة حسب الفئة' in ui,'category aware selection')
need('الطالب · مرتب حسب الفئة ثم الاسم' in ui and 'items(studentCandidates' in ui and 'studentCandidates.map' in ui,'single-student category sorting/filtering')
need('issueOfficialCertificate' in ui and 'repo.issueCertificate' in ui,'official server numbering')
need('metadata_only_no_pdf_no_assets' in ui,'metadata-only Supabase policy')
need('exportToDownloads' in ui and 'Downloads/Halaqati/Certificates' in ui,'independent local PDF export')
need('for (student in chosen)' in ui and 'NativeCertificate.exportToDownloads' in ui,'independent batch PDFs')
need('serverArchive' in ui and 'student_name' in ui,'lightweight official archive UI')
need('teacherName' in ui and 'supervisorName' in ui and 'halaqaName' in ui and 'centerName' in ui,'non-hardcoded identity fields')
need('certificateHijriDate' in ui and 'certificateGregorianDate' in ui,'separate Hijri/Gregorian dates')
need('style = Paint.Style.STROKE' not in ui, 'Kotlin Paint.style shadowing regression must be absent')
need(ui.count('setStyle(Paint.Style.STROKE)') >= 10, 'explicit Paint.setStyle calls required in certificate renderer')
need('fun dateCard(left: Float, textValue: String, color: Int)' not in ui, 'dateCard color parameter must not shadow Paint.color')
need('fun dateCard(left: Float, textValue: String, textColor: Int)' in ui, 'dateCard must use non-shadowing textColor parameter')
need('this.color = mixColor(palette.background, Color.WHITE, .58f)' in ui, 'date card fill must qualify Paint.color explicitly')
need('customFontName' in ui and 'خط الجهاز' in ui,'device font option restored')
need('fun studentCategories' in repo,'category repository source')
need('data class CertificateRecord' in core and 'CertificateCoreDraft' in core,'server certificate core retained')

# Update UI parity with v2.44: real states + progress + install permission flow.
for token in ('يتوفر تحديث','ما الجديد','LinearProgressIndicator','اكتمل تنزيل التحديث','تثبيت الآن'):
    need(token in settings,'update UI '+token)
for token in ('queryDownload','canRequestPackageInstalls','openInstallPermission','installDownloaded'):
    need(token in updater,'updater '+token)
need('android.permission.REQUEST_INSTALL_PACKAGES' in manifest,'install packages permission')

# Guard against accidental binary/file upload code in certificate path.
for bad in ('storage.from(', 'uploadCertificatePdf', 'pdf_base64', 'logo_base64', 'signature_base64', 'stamp_base64'):
    need(bad not in ui, 'binary upload path detected: '+bad)

print('RC12.2 FIX1 Certificates + Update UI gate PASS')
