#!/usr/bin/env python3
"""Recompute the integrity hash of every vendored skill and compare with manifest.json.
Hash: sha256 over each file (sorted relative paths; tests/, evals/, __pycache__ excluded) as: path NUL bytes NUL.
Usage: verify.py [SKILLS_DIR] [MANIFEST]   (stdlib only; exit 1 on any mismatch)"""
import hashlib, json, os, sys
EXCL = {"tests", "evals", "__pycache__"}
def files(d):
    out = []
    for r, dirs, fs in os.walk(d):
        dirs[:] = [x for x in dirs if x not in EXCL]
        out += [os.path.relpath(os.path.join(r, f), d).replace(os.sep, "/") for f in fs]
    return sorted(out)
def digest(d):
    x = hashlib.sha256()
    for rel in files(d):
        x.update(rel.encode() + b"\0"); x.update(open(os.path.join(d, rel), "rb").read()); x.update(b"\0")
    return x.hexdigest()
here = os.path.dirname(os.path.abspath(__file__))
skills = sys.argv[1] if len(sys.argv) > 1 else os.path.join(here, "..", "skills")
manifest = json.load(open(sys.argv[2] if len(sys.argv) > 2 else os.path.join(here, "manifest.json")))
bad = [n for n, e in manifest["skills"].items() if not os.path.isdir(os.path.join(skills, n)) or digest(os.path.join(skills, n)) != e["sha256"]]
print(f"{len(manifest['skills']) - len(bad)}/{len(manifest['skills'])} skills match the manifest" + (f"; MISMATCH: {bad}" if bad else ""))
sys.exit(1 if bad else 0)
