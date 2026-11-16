#!/usr/bin/env python3
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
errors=[]
def text(rel):
    p=root/rel
    if not p.exists():
        errors.append('missing '+rel); return ''
    return p.read_text(encoding='utf-8', errors='ignore')
def need(ok,msg):
    if not ok: errors.append(msg)

guard=text('app/src/main/java/com/mohammedsamir/halaqati/ui/guardian/GuardianUiState.kt')
home=text('app/src/main/java/com/mohammedsamir/halaqati/ui/guardian/GuardianHomeScreen.kt')
core=text('app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt')
comp=text('app/src/main/java/com/mohammedsamir/halaqati/ui/components/Components.kt')
theme=text('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/Theme.kt')
typo=text('app/src/main/java/com/mohammedsamir/halaqati/ui/theme/CairoTypography.kt')
graph=text('app/src/main/java/com/mohammedsamir/halaqati/data/AppGraph.kt')
dash=text('app/src/main/java/com/mohammedsamir/halaqati/ui/dashboard/DashboardViewModel.kt')
shell=text('app/src/main/java/com/mohammedsamir/halaqati/ui/components/GoldenShell.kt')
app=text('app/src/main/java/com/mohammedsamir/halaqati/ui/HalaqatiApp.kt')
mig=text('migration/20260929_v2521_rc17_1_consistency_performance.sql')

need('data class GuardianPlanActivity' in guard, 'typed plan activity missing')
need('planActivities: List<GuardianPlanActivity>' in guard, 'guardian plan not split into activities')
need('month.optDouble("hifz"' in guard and 'month.optDouble("sard"' in guard, 'old-RPC Hifz/Sard fallback missing')
need('combined value' in guard and 'ignores' in guard, 'combined plan fallback guard missing')
need('الحفظ والسرد محسوبان كلٌّ على حدة' in home, 'guardian plan split UX missing')
need('فوق الهدف' in home and 'coerceIn(0.0, 100.0)' in guard, 'surplus/100% cap missing')
need('session_exists' in mig and 'session_started' in mig, 'today session state missing in RPC')
need("'activities'" in mig and "'sard_target_pages'" in mig, 'RPC activity split missing')
need("activity_type::text = 'sard'" in mig and "activity_type::text = 'new_hifz'" in mig, 'Hifz/Sard facts not separated')
need('it.status == "active" && (it.halaqaId == null || it.halaqaId == session.halaqaId)' in core, 'suspended students can still enter session roster')
need('الطالب موقوف أو غير نشط' in core, 'direct recitation inactive guard missing')
need(('تعديل الخطة الذكية' in core or 'تعديل بيانات الخطة' in core) and 'showEditor = true' in core, 'direct Smart Plan edit action missing')
need('horizontalAlignment = Alignment.Start' in comp, 'RTL title hierarchy not semantic-start')
need('darkReadableAccent' in theme and 'onSurfaceVariant = Color(0xFFD0DED6)' in theme, 'dark contrast lift missing')
need('FontWeight.Bold' in typo and 'FontWeight.Light' in typo, 'typography weight hierarchy missing')
need('import androidx.compose.runtime.getValue' in shell, 'Compose State delegate getValue import missing in GoldenShell')
need('appScope.launch {' in graph and 'WorkManager.getInstance' in graph, 'WorkManager startup deferral missing')
need('if (hadCache) delay(320)' in dash, 'cache-first first-frame grace missing')
brand='Designed by : Dr. Mohammed Samir Abu Younis'
need(brand in shell and brand in app and 'صُنع بأيدٍ شرقية' in shell and 'صُنع بأيدٍ شرقية' in app, 'exact branding missing')
need('BrandLoadingScreen()' in app and 'scaleIn(' in app, 'branding animation missing')
need((root/'app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml').exists(), 'adaptive launcher icon missing')
need((root/'app/src/main/res/values-v31/styles.xml').exists(), 'Android 12 splash theme missing')
if errors:
    print('RC17.1 consistency/performance gate FAILED')
    for e in errors: print(' -', e)
    sys.exit(1)
print('RC17.1 consistency/performance gate PASS')
