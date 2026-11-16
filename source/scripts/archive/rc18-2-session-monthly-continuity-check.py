#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]
errors=[]
def text(rel):
    p=root/rel
    if not p.exists(): errors.append(f'missing {rel}'); return ''
    return p.read_text(encoding='utf-8',errors='ignore')
def need(ok,msg):
    if not ok: errors.append(msg)

models=text('app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt')
repo=text('app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt')
core=text('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
mig=text('migration/20261002_v2542_rc18_2_session_monthly_continuity.sql')
build=text('app/build.gradle.kts')
contract=text('migration/source-contract.json')

need('versionCode = 25402' in build and 'versionName = "2.54.2"' in build,'v2.54.2 version contract')
need('val notes: String = ""' in models and 'activityDataJson' in models,'SessionRow preserves notes/activity_data')
need('includeArchived: Boolean = false' in repo and 'filterNot { it.archived }' in repo,'archived session list support')
need('fun updateSessionDetails(' in repo and 'rc182_update_session_details' in repo,'session metadata edit RPC wiring')
need('fun setSessionArchived(' in repo and 'fun restoreSession(' in repo,'archive/restore repository flow')
need('تعديل بيانات الجلسة' in core and 'EditSessionDialog' in core and 'ملاحظات الجلسة' in core,'session edit UI')
need('الجلسات الحالية' in core and 'الأرشيف' in core and 'استعادة الجلسة' in core,'session archive/restore UI')
need('rc182_start_month' in repo and 'إدارة دورة الشهر' in core,'monthly engine wiring')
need('create or replace function public.rc182_start_month' in mig,'individual monthly RPC')
need("sp.end_date desc" in mig and "case when sp.student_id=st.id then 3" in mig,'previous effective plan ordered by recency then specificity')
need("v_hifz_end := least(v_month_end, v_month_start + 21)" in mig,'Hifz ends on day 22')
need("v_sard_start := least(v_month_end, v_month_start + 22)" in mig,'Sard starts on day 23')
need("'sard','مرحلة السرد'" in mig and "25,25,'flexible','last_activity'" in mig,'fixed 25-page Sard phase')
need("'hifz','مرحلة الحفظ'" in mig and "'last_activity'" in mig,'Hifz last-activity continuation')
need('rc182_rebind_recitation' in mig and 'recitation access denied' in mig,'server-side recitation rebind + authorization')
need('rc182_clear_computed_badges' in mig and 'revoke all on function public.rc182_clear_computed_badges(uuid,date) from public, anon, authenticated' in mig,'exact badge clear is internal-only')
need('rc182_refresh_student_badges_exact' in repo and 'rc182_refresh_student_badges_exact' in mig,'exact badge refresh wrapper')
need('pre_archive_status' in mig and "v_activity - 'pre_archive_status'" in mig,'archive metadata is preserved/restorable')
need("p.scope_type='student' and st.id=p.student_id" in mig and 'st.status=' not in mig[mig.index('create or replace function public.rc18_archive_finished_plans'):mig.index('-- ---------------------------------------------------------------------------\n-- 6)')],'historical archive does not drop inactive student-scoped history')
need('"rc182_update_session_details"' in contract and '"rc182_start_month"' in contract,'source contract declares new RPCs')

if errors:
    print('RC18.2 session/monthly continuity gate FAILED')
    for e in errors: print(' -',e)
    raise SystemExit(1)
print('RC18.2 session/monthly continuity gate passed')
