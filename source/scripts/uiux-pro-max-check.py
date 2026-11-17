#!/usr/bin/env python3
from pathlib import Path
import re, sys
root=Path(__file__).resolve().parents[1]
errors=[]

def read(rel):
    p=root/rel
    if not p.exists():
        errors.append(f'missing {rel}')
        return ''
    return p.read_text(encoding='utf-8', errors='ignore')

theme=read('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/Theme.kt')
typography=read('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/CairoTypography.kt')
components=read('app/src/main/java/com/mohammedsamir/halaqati/ui/components/Components.kt')
reports=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ReportsAndTools.kt')
sync=read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SyncCenterScreen.kt')
repo=read('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')

for token in ('TouchTarget = 48.dp','RadiusSmall = 12.dp','RadiusMedium = 16.dp','RadiusLarge = 20.dp','isSystemInDarkTheme()'):
    if token not in theme: errors.append(f'UIUX token missing: {token}')
# RC29: Arabic body copy is one step larger (Cairo has a small x-height); styles are built by style(weight, size, line).
for style,size in (('bodySmall','13'),('bodyMedium','15')):
    if not re.search(rf'{style}\s*=\s*style\([^,]+,\s*{size},', typography):
        errors.append(f'UIUX Cairo typography missing: {style} {size}sp')
for helper in ('fun ProFilterChip(', 'fun StatusPill(', 'fun LoadingSkeleton(', 'fun StateNotice('):
    if helper not in components: errors.append(f'component missing: {helper}')
for feature in ('var halaqaId by remember', 'var sessionType by remember', 'NativeReportCache.save', 'NativeReportCache.load', 'suspend fun exportToDownloads(', 'fun archive(', 'selectedStudentIds', 'الإصدار الجماعي', 'أرشيف الشهادات', 'data class NativeCertificateStyle(', 'exportArchiveIndex', 'importArchiveIndex', 'تصميم الشهادة', 'إعادة إنشاء كنسخة جديدة'):
    if feature not in reports: errors.append(f'report/certificate UX parity missing: {feature}')
for feature in ('sessions.status=neq.cancelled','halaqaId: String = "all"','sessionType: String = "all"'):
    if feature not in repo: errors.append(f'report query parity missing: {feature}')

for feature in ('val template: String = "emerald"', 'fontFamily', 'titleSize', 'studentSize', 'bodySize', 'logoSize', 'borderWidth', 'ornamentStrength', 'showVerse', 'showSignatures', 'showDate', 'showNumber', 'numberPosition', 'styleToJson', 'styleFromJson', 'certificatePalette(style.template)'):
    if feature not in reports: errors.append(f'certificate style parity missing: {feature}')
if 'AssistChip(' in sync:
    errors.append('SyncCenter still uses AssistChip for non-action status; use a non-clickable status surface')
for feature in ('LoadingSkeleton(rows = 3','Modifier.heightIn(min = 48.dp)'):
    if feature not in sync: errors.append(f'SyncCenter UIUX state missing: {feature}')
if not any(token in sync for token in ('تم حفظ ${sync.pending} تغييرات محليًا', 'تم حفظ ${pendingRows} تغييرات محليًا')):
    errors.append('SyncCenter UIUX state missing: locally-saved pending changes notice')
if 'LinearProgressIndicator(Modifier.fillMaxWidth())' in sync:
    errors.append('SyncCenter returned to a blocking raw progress bar; keep skeleton/non-blocking refresh state')
# no explicitly undersized typography in native sources
for p in (root/'app/src/main/java').rglob('*.kt'):
    txt=p.read_text(encoding='utf-8', errors='ignore')
    for m in re.finditer(r'fontSize\s*=\s*(\d+)\.sp', txt):
        if int(m.group(1)) < 12:
            errors.append(f'{p.relative_to(root)} has fontSize {m.group(1)}sp (<12sp)')

# Screen-level guardrails from UX Pilot + UI/UX Pro Max.
screens_root = root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens'
for p in screens_root.rglob('*.kt'):
    txt = p.read_text(encoding='utf-8', errors='ignore')
    rel = p.relative_to(root)
    if re.search(r'\bFilterChip\s*\(', txt):
        errors.append(f'{rel} uses raw FilterChip; use ProFilterChip')
    if re.search(r'AssistChip\s*\(\s*onClick\s*=\s*\{\s*\}', txt, flags=re.S):
        errors.append(f'{rel} has a no-op AssistChip; use a non-clickable status surface')
    if re.search(r'SuggestionChip\s*\(\s*onClick\s*=\s*\{\s*\}', txt, flags=re.S):
        errors.append(f'{rel} has a no-op SuggestionChip; use StatusPill or a non-clickable status surface')

    lines = txt.splitlines()
    for i, line in enumerate(lines):
        if re.search(r'\bIconButton\s*\(', line):
            window = '\n'.join(lines[i:i+22])
            if not re.search(r'modifier\s*=\s*Modifier\.size\((?:48\.dp|HalaqatiUiTokens\.TouchTarget)\)', window):
                errors.append(f'{rel}:{i+1} IconButton lacks explicit 48dp touch target')
        if re.search(r'\bFilledIconButton\s*\(', line):
            window = '\n'.join(lines[i:i+12])
            if not re.search(r'modifier\s*=\s*Modifier\.size\((?:48\.dp|HalaqatiUiTokens\.TouchTarget)\)', window):
                errors.append(f'{rel}:{i+1} FilledIconButton lacks explicit 48dp touch target')

students = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
for token in ('bottom = 120.dp', 'إعادة تعيين الفلاتر', 'studentStatusAr(student.status)', 'StudentDetailSheet(', 'بدء تسميع ${student.fullName}', 'كل المسارات', 'كل الفئات'):
    if token not in students: errors.append(f'Students UX regression: missing {token}')
for token in ('Icons.Default.Check', 'heightIn(min = HalaqatiUiTokens.TouchTarget)'):
    if token not in components: errors.append(f'ProFilterChip UX regression: missing {token}')
for token in ('CompactSearchField(', 'تسميع المقرر', 'graphicsLayer(scaleX = -1f)'):
    if token not in students: errors.append(f'Plans UX regression: missing {token}')

# Known regression from a previous edit: Student exposes track, not trackCode.
if 'trackCode' in '\n'.join(p.read_text(encoding='utf-8', errors='ignore') for p in (root/'app/src/main/java').rglob('*.kt')):
    errors.append('invalid Student.trackCode reference found; model field is track')

# Guardian + accounts regression guards from UX Pilot persona reviews.
management = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt')
app_shell = read('app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt')
golden_shell = read('app/src/main/java/com/mohammedsamir/halaqati/ui/components/GoldenShell.kt')
core = read('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
for token in ('fun GuardianTodayAlert(', 'حالة اليوم', 'وضع عدم الاتصال', 'bottom = FabClearance', 'StatusPill(reason, kind = "warning")', 'var selectedHalaqa by remember', 'كل الحلقات', 'scopedVisits'):
    if token not in management: errors.append(f'Guardian/Supervision UX regression: missing {token}')
for token in ('CapsuleTab(', 'role = Role.Tab', 'selectableGroup()', 'MaterialTheme.typography.labelMedium', 'heightIn(min = 48.dp)'):
    if token not in golden_shell: errors.append(f'Bottom navigation UX regression: missing {token}')
for token in ('LazyColumn(', 'weight(1f)', 'تسجيل الخروج', 'صُنع بأيدٍ شرقية', 'Designed by : Dr. Mohammed Samir Abu Younis'):
    if token not in golden_shell: errors.append(f'Drawer UX regression: missing {token}')
for token in ('LoadingSkeleton(rows = 4', 'bottom = 96.dp', 'حسابات أولياء أمور معتمدة'):
    if token not in core: errors.append(f'Accounts UX regression: missing {token}')

for token in ('هناك بيانات لم تُرفع بعد', 'الخروج مع الاحتفاظ محليًا', 'معزول حسب الحساب', 'pendingQueueSize()', 'blockedQueueSize()'):
    if token not in reports: errors.append(f'Settings/offline-account UX regression: missing {token}')


# Import Center release-freeze guardrails.
for token in ('mappingEpoch', 'تعديل ربط الأعمدة', 'فحص ما قبل الاعتماد', 'ImportColumnField(', 'ImportRules.RowDisposition', 'يحتاج تصحيح'):
    if token not in reports: errors.append(f'Import Center UX/validation regression: missing {token}')
if 'GenericDataScreen' in '\n'.join(p.read_text(encoding='utf-8', errors='ignore') for p in (root/'app/src/main/java').rglob('*.kt')):
    errors.append('GenericDataScreen fallback returned to Native runtime')

if errors:
    print('UI/UX Pro Max check FAILED')
    for e in errors: print(' -', e)
    sys.exit(1)
print('UI/UX Pro Max check passed')
