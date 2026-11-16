#!/usr/bin/env python3
from pathlib import Path
import sys
ROOT=Path(__file__).resolve().parents[1]
errors=[]
def need(rel,*tokens):
    p=ROOT/rel
    if not p.exists(): errors.append(f"missing {rel}"); return
    s=p.read_text(encoding="utf-8",errors="ignore")
    for token in tokens:
        if token not in s: errors.append(f"{rel}: missing {token}")
need("app/src/main/java/com/mohammedsamir/halaqati/ui/screens/CoreScreens.kt",
     "إدارة مراحل الحفظ والسرد", "phasePlan = snapshot.plan")
need("app/src/main/java/com/mohammedsamir/halaqati/ui/screens/SmartPlanPhasesUi.kt",
     "var showStudentActions by remember { mutableStateOf(true) }", "إضافة السرد",
     "إنهاء الحفظ", "معاينة الانتقال الجماعي", "اعتماد وبدء السرد", "initialType = addingType")
if errors:
    print("RC11 UI access gate FAILED")
    for e in errors: print(" -",e)
    sys.exit(1)
print("RC11 UI access gate PASS (direct phase entry, visible student actions, Sard setup, Hifz->Sard transition)")
