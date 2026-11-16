#!/usr/bin/env python3
"""RC35 static gate: Sard is counted in recited pages and placeholder recitations never lower a quality average."""
import sys, re
from pathlib import Path
rd = lambda p: Path(p).read_text(encoding='utf-8')
b = 'app/src/main/java/com/mohammedsamir/halaqati/'
errors = []
eng = rd(b + 'plans/SmartPlanEngine.kt')
if len(re.findall(r'val exactPagePlan = !linePlan && plan\.planType != "sard"', eng)) != 2:
    errors.append('both exactPagePlan definitions must exclude Sard')
if not Path(b + 'plans/RecitationQualityRules.kt').exists():
    errors.append('RecitationQualityRules missing')
uses = {
    'plans/OrgInsightRules.kt': 'RecitationQualityRules.isPlaceholder',
    'ui/screens/StudentProfileScreen.kt': 'RecitationQualityRules.isPlaceholder',
    'data/HalaqatiRepository.kt': 'RecitationQualityRules.isPlaceholder',
    'ui/screens/ManagementScreens.kt': 'RecitationQualityRules.isPlaceholder',
    'ui/dashboard/DashboardViewModel.kt': 'RecitationQualityRules.isPlaceholder',
}
for f, tok in uses.items():
    if tok not in rd(b + f):
        errors.append(f + ' must ignore placeholder recitations in quality averages')
if 'evaluation,safety_percentage' not in rd(b + 'data/HalaqatiRepository.kt'):
    errors.append('report recitation query must select evaluation')
if errors:
    print('RC35 calculation-accuracy gate FAILED'); [print(' -', e) for e in errors]; sys.exit(1)
print('RC35 calculation-accuracy gate passed')
