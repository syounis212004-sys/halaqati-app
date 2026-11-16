#!/usr/bin/env python3
from pathlib import Path
import re, sys

root = Path(__file__).resolve().parents[1]
kt_files = sorted((root / 'app/src/main/java').rglob('*.kt'))
errors=[]

# lightweight Kotlin lexical delimiter checker; strips strings/comments while preserving newlines.
def strip_kotlin(text: str) -> str:
    out=[]; i=0; n=len(text); state='code'; quote=''; depth=0
    while i<n:
        c=text[i]; d=text[i+1] if i+1<n else ''
        if state=='code':
            if c=='/' and d=='/': state='line'; out.extend('  '); i+=2; continue
            if c=='/' and d=='*': state='block'; depth=1; out.extend('  '); i+=2; continue
            if text.startswith('"""',i): state='triple'; out.extend('   '); i+=3; continue
            if c=='"': state='string'; out.append(' '); i+=1; continue
            if c=="'": state='char'; out.append(' '); i+=1; continue
            out.append(c); i+=1; continue
        if state=='line':
            if c=='\n': state='code'; out.append('\n')
            else: out.append(' ')
            i+=1; continue
        if state=='block':
            if c=='/' and d=='*': depth+=1; out.extend('  '); i+=2; continue
            if c=='*' and d=='/': depth-=1; out.extend('  '); i+=2; state='code' if depth==0 else 'block'; continue
            out.append('\n' if c=='\n' else ' '); i+=1; continue
        if state=='triple':
            if text.startswith('"""',i): state='code'; out.extend('   '); i+=3; continue
            out.append('\n' if c=='\n' else ' '); i+=1; continue
        if state in ('string','char'):
            if c=='\\': out.extend('  ' if i+1<n else ' '); i+=2; continue
            end='"' if state=='string' else "'"
            if c==end: state='code'
            out.append('\n' if c=='\n' else ' '); i+=1; continue
    if state in ('block','triple','string','char'):
        errors.append(f'{current}: unterminated lexical state {state}')
    return ''.join(out)

for f in kt_files:
    current=f.relative_to(root)
    text=f.read_text(encoding='utf-8')
    clean=strip_kotlin(text)
    stack=[]
    pairs={')':'(',']':'[','}':'{'}
    opens=set(pairs.values())
    line=1
    for ch in clean:
        if ch=='\n': line+=1; continue
        if ch in opens: stack.append((ch,line))
        elif ch in pairs:
            if not stack or stack[-1][0]!=pairs[ch]:
                errors.append(f'{current}:{line}: unmatched {ch}')
                break
            stack.pop()
    if stack:
        ch,ln=stack[-1]
        errors.append(f'{current}:{ln}: unclosed {ch}')

# Regression guards for mistakes that occurred in previous candidates.
repo=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text()
create_session=repo[repo.find('fun createSession'):repo.find('fun saveAttendance')]
if 'draft.' in create_session:
    errors.append('HalaqatiRepository.kt: createSession references recitation draft')
if '.put("selection_data", selectionData)' in repo and 'val selectionData' not in repo[repo.find('fun saveRecitation'):repo.find('fun flushQueue')]:
    errors.append('HalaqatiRepository.kt: selectionData missing inside saveRecitation')

models=(root/'app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt').read_text()
for field in ('parentPlanId','smartPlanId','qcfLineKeys'):
    if field not in models: errors.append(f'Models.kt missing {field}')

engine=(root/'app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt').read_text()
if 'val lineKeys: List<String>' not in engine: errors.append('SmartPlanEngine.Assignment missing lineKeys')

core=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt').read_text()
students_section=core.split('fun StudentsScreen(',1)[1].split('private fun StudentDetailSheet(',1)[0]
if 'openTodayOnLaunch' in students_section:
    errors.append('CoreScreens.kt: session auto-open workflow leaked into StudentsScreen')
if 'suspend fun reload(force: Boolean = false)' not in students_section:
    errors.append('CoreScreens.kt: StudentsScreen reload must accept force refresh')
plans_section=core.split('fun PlansScreen(',1)[1].split('private fun PlanDetailDialog(',1)[0]
if 'suspend fun reload(force: Boolean = false)' not in plans_section:
    errors.append('CoreScreens.kt: PlansScreen reload must accept force refresh')
repo_plan_snapshots=repo[repo.find('fun planSnapshots'):repo.find('private fun planRecitations')]
if 'forceRefresh: Boolean = false' not in repo_plan_snapshots:
    errors.append('HalaqatiRepository.kt: planSnapshots must expose forceRefresh for pull-to-refresh')

guardian_parity=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/GuardianParityScreens.kt').read_text()
if re.search(r'GoldenFeatureHero\s*\([^)]*\bicon\s*=', guardian_parity, re.S):
    errors.append('GuardianParityScreens.kt: GoldenFeatureHero does not accept an icon parameter')
if re.search(r'GoldenFeatureHero\s*\(\s*[^,]+,\s*[^,]+,\s*Icons\.Default\.', guardian_parity, re.S):
    errors.append('GuardianParityScreens.kt: GoldenFeatureHero third positional argument must be badge String, not ImageVector')

management=(root/'app/src/main/java/com/mohammedsamir/halaqati/ui/screens/ManagementScreens.kt').read_text()
if 'online: Boolean' not in management[management.find('fun GuardianProfileNativeScreen'):management.find('private fun GuardianAnalyticsCard')]:
    errors.append('ManagementScreens.kt: Guardian profile must receive online state explicitly')
if 'if (loading && students.isEmpty())' in management[management.find('fun GuardianProfileNativeScreen'):]:
    errors.append('ManagementScreens.kt: Guardian profile references an out-of-scope students list')
if 'GuardianAnalyticsRow(student, month, trend, today, plan' not in management:
    errors.append('ManagementScreens.kt: Guardian daily snapshot is not wired')

# RC13 FIX5: prevent public API from exposing the internal report-style model.
# Kotlin 2.2 treats this as a compile error. The renderer is module-internal in practice,
# so keep its build entry point internal rather than widening UI implementation types.
report_renderer=(root/'app/src/main/java/com/mohammedsamir/halaqati/documents/ReportHtmlRenderer.kt').read_text()
if 'object ReportHtmlRenderer {\n    internal fun build(' not in report_renderer:
    errors.append('ReportHtmlRenderer.kt: build must stay internal because NativeReportStyle is internal')

if errors:
    print('Compile sanity check FAILED')
    for e in errors: print(' -',e)
    sys.exit(1)
print(f'Compile sanity check passed ({len(kt_files)} Kotlin files)')
