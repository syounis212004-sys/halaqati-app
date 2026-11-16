#!/usr/bin/env python3
"""RC15.2 gate: Tathbit Core (monthly, no phases) + live-preview PDF core."""
from pathlib import Path
import sys
root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/mohammedsamir/halaqati'
fails = []
def need(path, token, msg):
    if token not in path.read_text(encoding='utf-8'): fails.append(msg)
def forbid(path, token, msg):
    if token in path.read_text(encoding='utf-8'): fails.append(msg)
ui = src/'ui/screens/SmartPlanPhasesUi.kt'
need(ui, 'TathbitMonthlyPlanSheet(plan = plan, snapshots = snapshots, onDismiss = onDismiss)', 'tathbit plan must open the monthly sheet, not phases')
need(ui, 'if (snapshot.plan.type == "tathbit") return', 'tathbit timeline must not render phases')
need(ui, 'val tathbitPlan = snapshot.plan.type == "tathbit"', 'timeline tathbitPlan must be defined from snapshot')
need(ui, 'val tathbitPlan = plan.type == "tathbit"', 'manager tathbitPlan must be defined')
repo = src/'data/HalaqatiRepository.kt'
need(repo, 'rc152_roll_tathbit_months', 'monthly roll RPC missing')
need(repo, 'if (draft.activityType != TrackActivityPolicy.TATHBIT) resolvedSmartPlanPhaseId', 'tathbit recitation must never carry phase_id')
prev = src/'documents/HtmlDocumentPreview.kt'
for t in ['object LiveDocumentPreview', 'suspend fun acquireFor', '@media print', '@media screen', '__HALAQATI_EXPORT_FIT__', 'onRelease']:
    need(prev, t, 'live preview missing '+t)
vr = src/'documents/VectorDocumentRenderer.kt'
for t in ['renderFromLivePreview', 'live-preview-vector', 'live-preview-raster-fallback', 'MIN_VALID_PDF_BYTES = 1024L', 'NonCancellable']:
    need(vr, t, 'renderer missing '+t)
rt = src/'ui/screens/ReportsAndTools.kt'
if rt.read_text(encoding='utf-8').count('DocumentExportManager.launch {') < 3:
    fails.append('certificate/report exports must run in DocumentExportManager, not the composition scope')
need(src/'documents/DocumentExportManager.kt', 'SupervisorJob()', 'export manager scope missing')
mig = root/'migration/20260929_v2502_rc15_2_tathbit_core_monthly.sql'
for t in ['rc152_roll_tathbit_months', 'rc152_tathbit_consistency', 'add column if not exists plan_id', 'خطة التثبيت شهرية بلا مراحل', 'cron.schedule']:
    need(mig, t, 'migration missing '+t)
if fails:
    print('\n'.join('FAIL: '+f for f in fails)); sys.exit(1)
print('RC15.2 tathbit core + live PDF gate: OK')
