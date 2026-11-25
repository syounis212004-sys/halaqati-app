#!/usr/bin/env python3
"""Validate a docs/discovery/<slug> folder: required files, evidence log, tags, no empty stages.
Usage: check_discovery.py <folder>   (stdlib only; exit 1 on problems)"""
import re, sys
from pathlib import Path

REQUIRED = ["01-context.md", "02-first-principles.md", "03-research.md", "04-options.md",
            "05-systems.md", "06-comparison.md", "08-critique.md", "09-experiments.md",
            "10-reevaluation.md", "11-design.md", "REPORT.md", "evidence-log.md"]
LIGHT = {"01-context.md", "03-research.md", "04-options.md", "06-comparison.md", "11-design.md", "REPORT.md", "evidence-log.md"}

def main(argv):
    if len(argv) != 2:
        print(__doc__); return 2
    d = Path(argv[1]); problems = []
    if not d.is_dir():
        print(f"not a directory: {d}"); return 2
    rep_text = (d / "REPORT.md").read_text(encoding="utf-8") if (d / "REPORT.md").exists() else ""
    tier_light = re.search(r"tier[^\n]*\blight\b", rep_text, re.I) is not None
    need = LIGHT if tier_light else set(REQUIRED)
    for f in sorted(need):
        p = d / f
        if not p.exists(): problems.append(f"missing {f}")
        elif len(p.read_text(encoding="utf-8").strip()) < 80: problems.append(f"{f} is nearly empty")
    log = d / "evidence-log.md"
    if log.exists():
        lines = [l for l in log.read_text(encoding="utf-8").splitlines() if re.match(r"^S\d+\s*\|", l)]
        stages = {int(re.match(r"S(\d+)", l).group(1)) for l in lines}
        if len(lines) < 5: problems.append(f"evidence log has only {len(lines)} entries")
        for l in lines:
            if len([c for c in l.split("|") if c.strip()]) < 3: problems.append(f"log line lacks evidence: {l[:60]}")
        if not tier_light and not {1, 3, 12} <= stages: problems.append("log lacks stages 1, 3 or 12")
    opts = d / "04-options.md"
    if opts.exists():
        n = len(re.findall(r"^#{2,3}\s*(option|alt|خيار|بديل)", opts.read_text(encoding="utf-8"), re.I | re.M))
        if n < 5: problems.append(f"04-options.md has {n} option headings (need >= 5, headings starting 'Option N')")
    rep = d / "REPORT.md"
    if rep.exists():
        t = rep.read_text(encoding="utf-8")
        if not re.search(r"\[(FACT|MEASURED|ASSUMPTION)\]", t): problems.append("REPORT.md has no [FACT]/[MEASURED]/[ASSUMPTION] tags")
        if not re.search(r"not verified|لم أتحقق|غير متحقق", t, re.I): problems.append("REPORT.md lacks a 'Not verified' section")
    print("OK" if not problems else "PROBLEMS:\n- " + "\n- ".join(problems))
    return 1 if problems else 0

if __name__ == "__main__":
    sys.exit(main(sys.argv))
