#!/usr/bin/env python3
from pathlib import Path
import json, re, sys
ROOT=Path(__file__).resolve().parents[1]
errors=[]

def text(path):
    p=ROOT/path
    if not p.exists():
        errors.append(f'missing {path}')
        return ''
    return p.read_text(errors='replace')

def need(hay, needle, msg):
    if needle not in hay: errors.append(msg)

def nope(hay, needle, msg):
    if needle in hay: errors.append(msg)

build=text('app/build.gradle.kts')
need(build,'versionCode = 25402','RC14 versionCode missing')
need(build,'versionName = "2.54.2"','RC14 versionName missing')
contract=json.loads(text('migration/source-contract.json') or '{}')
if contract.get('native_version')!='2.54.2': errors.append('source-contract native_version mismatch')

# Document reliability: restore the complete v2.44 two-engine behavior.
vr=text('app/src/main/java/com/mohammedsamir/halaqati/documents/VectorDocumentRenderer.kt')
for tok in [
    'DIRECT_WRITE_TIMEOUT_MS = 120_000L', 'READY_MAX_POLLS = 120', 'DIRECT_FAILURE_COOLDOWN_MS = 0L', 'COMPATIBILITY_TIMEOUT_MS', 'exportMutex = Mutex()',
    'HalaqatiWebViewPdfWriter', 'renderCompatibilityToFile', 'captureWebViewSegment',
    'android-webview-raster-fallback-v244', 'shouldSkipDirectChromium', 'DIRECT_FAILURE_COOLDOWN_MS',
    'DocumentExportPhase.CHROMIUM', 'DocumentExportPhase.COMPATIBILITY',
    'MediaStore.MediaColumns.IS_PENDING', 'validatePdfFile', 'PDF_MAGIC', 'PdfDocument',
]: need(vr,tok,'document reliability missing '+tok)
writer=text('app/src/main/java/android/print/HalaqatiWebViewPdfWriter.java')
for tok in ['class Handle','layoutSignal.cancel()','writeSignal.cancel()','EXTRA_PRINT_PREVIEW','adapter.onWrite']:
    need(writer,tok,'cancellable Chromium bridge missing '+tok)

studio=text('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
for tok in ['استوديو التقارير RC14','onProgress = { phase -> message = phase.arabicLabel }','withTimeout(30_000L)','Downloads/Halaqati/Reports','Downloads/Halaqati/Certificates']:
    need(studio,tok,'RC14 studio integration missing '+tok)
css=text('app/src/main/assets/documents/certificate.css')
need(css,'transform:translateY(-10px)','certificate award badge was not lifted')

# Approval correctness: explicit user refresh must bypass stale cached pending state.
repo=text('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
for tok in ['fun profileStrictRefresh()','require(online())','CACHE_PROFILE','fun approveAccount','fun rejectAccount','fun setAccountActive','fun changeAccountRole','fun deleteAccountPermanently','fun guardianAccounts','fun linkPrimaryGuardian']:
    need(repo,tok,'guardian/admin repository parity missing '+tok)
vm=text('app/src/main/java/com/mohammedsamir/halaqati/ui/AppViewModel.kt')
need(vm,'fun refreshApproval()','pending approval strict refresh action missing')
need(vm,'repo.profileStrictRefresh()','refreshApproval must bypass stale cache')
app=text('app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt')
need(app,'PendingApprovalScreen(state.profile, vm::refreshApproval, vm::logout)','pending screen not wired to strict refresh')

# Guardian + Management surface planned for RC14.
nav=text('app/src/main/java/com/mohammedsamir/halaqati/ui/navigation/AppDestination.kt')
for tok in ['GuardianProfile','GuardianCompetition','GuardianAchievements','Users','Halaqas','Community','Notifications','ImportExport','SyncCenter']:
    need(nav,tok,'RC14 navigation missing '+tok)
mg=text('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt')
for tok in ['HalaqasManagementScreen','CommunityNativeScreen','NotificationsNativeScreen','GuardianProfileNativeScreen']:
    need(mg,tok,'management screen missing '+tok)
gp=text('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/GuardianParityScreens.kt')
for tok in ['GuardianCompetitionNativeScreen','GuardianAchievementsNativeScreen']:
    need(gp,tok,'guardian parity screen missing '+tok)
core=text('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
for tok in ['fun UsersScreen(','ربط ولي الأمر بالطالب','تغيير الصلاحية','حذف نهائي','اعتماد من جديد']:
    need(core,tok,'account management parity missing '+tok)

# Project invariants.
models=text('app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt')
nope(models,'third_track','third student track must not appear')
legacy_paths=[
 'migration/legacy-web-v2.44.0/src/features/reports/vector-pdf-runtime.js',
 'migration/legacy-web-v2.44.0/src/features/reports/runtime.js',
 'migration/legacy-web-v2.44.0/src/features/certificates/runtime.js',
]
for p in legacy_paths:
    if not (ROOT/p).exists(): errors.append('missing v2.44 golden master '+p)

if errors:
    print('RC14 Guardian + Management gate FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print('RC14 Guardian + Management + Document Reliability gate PASS')
