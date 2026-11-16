#!/usr/bin/env python3
"""Run only the gates that belong to RC8 and its inherited RC7/parity contract.

Future-phase gates (RC9+ / Stable) are intentionally not auto-discovered here.
This prevents a later check file from making an earlier release candidate fail
before Gradle compilation is even reached.
"""
from __future__ import annotations

from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = ROOT / "scripts"

# Ordered, explicit RC8 contract. Do not replace with scripts/*check.py globbing.
GATES = [
    "generate-qcf4-layout.py",
    "rc8-integration-check.py",
    "rc82-recitation-parity-check.py",
    "rc82-qcf-regression.py",
    "rc83-smart-plan-quran-ux-check.py",
    "rc84-direction-offline-ux-check.py",
    "rc85-smart-plan-activity-performance-check.py",
    "rc86-cache-sync-smartplan-ui-check.py",
    "rc86-v3-performance-session-ux-check.py",
    "rc7-offline-contract-check.py",
    "rc7-durability-check.py",
    "behavioral-parity-check.py",
    "auth-push-privacy-check.py",
    "cairo-contract-check.py",
    "compile-sanity-check.py",
    "compiler-regression-check.py",
    "compose-risk-check.py",
    "full-release-parity-check.py",
    "google-auth-flow-check.py",
    "local-sensitive-account-scope-check.py",
    "manifest-contract-check.py",
    "navigation-contract-check.py",
    "offline-account-scope-check.py",
    "parity-matrix-check.py",
    "release-contract-check.py",
    "release-parity-contract-check.py",
    "smart-plan-alerts-check.py",
    "static-parity-check.py",
    "ui244-restoration-check.py",
    "uiux-pro-max-check.py",
]

# These are valid project checks, but they belong to future phases and MUST NOT
# gate RC8. Keep this assertion so a mistaken edit is caught immediately.
FUTURE_PHASE_GATES = {"stable-release-check.py"}


def main() -> int:
    overlap = FUTURE_PHASE_GATES.intersection(GATES)
    if overlap:
        print(f"RC8 gate manifest illegally includes future gates: {sorted(overlap)}", file=sys.stderr)
        return 2

    missing = [name for name in GATES if not (SCRIPTS / name).is_file()]
    if missing:
        print(f"RC8 gate manifest references missing scripts: {missing}", file=sys.stderr)
        return 2

    print("=== RC8 phase-scoped static gates ===")
    print("Future Stable/RC13/RC15 gates are intentionally excluded from RC8.")
    for name in GATES:
        print(f"\n=== {name} ===", flush=True)
        completed = subprocess.run([sys.executable, str(SCRIPTS / name)], cwd=ROOT)
        if completed.returncode != 0:
            print(f"RC8 gate FAILED: {name} (exit {completed.returncode})", file=sys.stderr)
            return completed.returncode

    print(f"\nRC8 phase-scoped static gates: {len(GATES)}/{len(GATES)} PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
