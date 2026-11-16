#!/usr/bin/env python3
from pathlib import Path
root=Path(__file__).resolve().parents[1]

def need(cond,msg):
    if not cond: raise SystemExit('RC12.1 FAIL: '+msg)

build=(root/'app/build.gradle.kts').read_text()
repo=(root/'app/src/main/java/com/mohammedsamir/halaqati/data/HalaqatiRepository.kt').read_text()
engine=(root/'app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanEngine.kt').read_text()
tag=(root/'app/src/main/java/com/mohammedsamir/halaqati/plans/SmartPlanTagRules.kt').read_text()
models=(root/'app/src/main/java/com/mohammedsamir/halaqati/model/Models.kt').read_text()
cert=(root/'app/src/main/java/com/mohammedsamir/halaqati/model/CertificateCore.kt').read_text()
mig=(root/'migration/20260927_v2471_rc12_1_stability_certificates_core.sql').read_text()
contract=(root/'migration/source-contract.json').read_text()
need('versionCode = 25402' in build and 'versionName = "2.54.2"' in build,'version')
need('normalizeId' in tag and 'equals("null"' in tag,'legacy null tag normalizer')
need('optNullableString("smart_plan_id")' in models,'model null tag normalization')
need('rec.copy(taggedPlanId = phaseAwareEnginePlan.id)' in repo,'phase_id authoritative progress')
need('resolvedSmartPlanId' in repo and 'resolvedSmartPlanPhaseId' in repo,'new recitations receive plan/phase tags')
need('invalidatePlanSnapshotMemory()' in repo and 'if (pushed > 0) invalidatePlanSnapshotMemory()' in repo,'sync cache invalidation')
need('اليوم السبت إجازة' in repo,'off-day phantom session guard')
need('sardRouteDirection = if (phase.type == "sard") phaseRoute' in repo,'Sard route propagation')
need('SmartPlanTagRules.normalizeId(recitation.taggedPlanId)' in engine,'engine defensive tag normalization')
need('data class CertificateRecord' in cert and 'data class CertificateCoreDraft' in cert,'certificate core models')
need('fun saveCertificateDraft' in repo and 'fun issueCertificate' in repo,'certificate repository API')
for token in ('create table if not exists public.certificates','create table if not exists public.certificate_templates','rc121_issue_certificate','rc121_next_certificate_number','rc121_capabilities','smart_plan_phase_id'):
    need(token in mig,'migration missing '+token)
need('issued certificate content is immutable' in mig,'issued snapshot immutability')
need('student_guardians' in mig and 'certificates_select_access' in mig,'guardian/staff RLS')
need('"certificates"' in contract and '"certificate_templates"' in contract and '"rc121_issue_certificate"' in contract,'source contract')
print('RC12.1 Stability + Certificates Core gate PASS')
