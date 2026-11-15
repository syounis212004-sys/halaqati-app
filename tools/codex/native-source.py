#!/usr/bin/env python3
"""Safely extract Android source, and explicitly export reviewed source edits."""
import argparse
import hashlib
from pathlib import Path, PurePosixPath
import stat
import zipfile

root = Path(__file__).resolve().parents[2]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("command", choices=["extract", "status", "export"])
parser.add_argument("--output", type=Path, help="New ZIP outside the checkout; never overwrites the tracked archive")
args = parser.parse_args()
archive = root / "Halaqati_RC89.zip"
dest = root / ".codex-work/native"
stamp = dest / ".archive-sha256"
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
with zipfile.ZipFile(archive) as bundle:
    members = bundle.infolist()
    for info in members:
        path = PurePosixPath(info.filename)
        if path.is_absolute() or ".." in path.parts or "\\" in info.filename or stat.S_ISLNK(info.external_attr >> 16):
            raise SystemExit(f"Unsafe archive entry: {info.filename}")
    projects = [i.filename.removesuffix("settings.gradle.kts") for i in members if i.filename.endswith("/settings.gradle.kts")]
    if len(projects) != 1:
        raise SystemExit("Expected exactly one Android source project")
    project = dest / projects[0]
    if args.command == "extract":
        if dest.exists():
            if not stamp.exists() or stamp.read_text().strip() != digest:
                raise SystemExit("Archive changed or extraction incomplete. Preserve local edits and move .codex-work/native before extracting again.")
        else:
            dest.mkdir(parents=True)
            bundle.extractall(dest)
            stamp.write_text(digest + "\n")
        print(project)
    else:
        if not project.is_dir() or not stamp.exists() or stamp.read_text().strip() != digest:
            raise SystemExit("Run extract first; archive must match extraction stamp")
        changed = []
        known = set()
        for info in members:
            if info.is_dir():
                continue
            known.add(info.filename)
            local = dest / info.filename
            if not local.exists() or local.read_bytes() != bundle.read(info):
                changed.append(info.filename)
        ignored = {"build", ".gradle", ".kotlin", ".idea", "__pycache__"}
        private_names = {"local.properties", "google-services.json"}
        private_suffixes = {".jks", ".keystore", ".pem", ".key"}
        added = [p for p in project.rglob("*") if p.is_file() and not ignored.intersection(p.relative_to(project).parts) and p.name not in private_names and not p.name.startswith(".env") and p.suffix not in private_suffixes and p.relative_to(dest).as_posix() not in known]
        print("Changed/deleted archived files:", *changed, sep="\n")
        print("Added source files:", *(str(p.relative_to(dest)) for p in added), sep="\n")
        if args.command == "export":
            if args.output is None or args.output.resolve().is_relative_to(root):
                raise SystemExit("--output must name a new ZIP outside this checkout")
            with zipfile.ZipFile(args.output, "x", compression=zipfile.ZIP_DEFLATED) as output:
                for info in members:
                    local = dest / info.filename
                    if info.is_dir():
                        output.writestr(info, b"")
                    elif local.exists():
                        output.writestr(info, local.read_bytes())
                for local in added:
                    output.write(local, local.relative_to(dest))
            print("Review archive and matching release checksum before replacing tracked source:", args.output)
