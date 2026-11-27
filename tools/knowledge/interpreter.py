"""Resolve a Python console-script interpreter, including pipx's '-E' flag."""
import os
from pathlib import Path
import shlex
import shutil
import sys


def resolve(command):
    with Path(command).open(encoding="utf-8") as handle:
        line = handle.readline().strip()
    if not line.startswith("#!"):
        raise ValueError("Graphify launcher has no interpreter shebang")
    parts = shlex.split(line[2:])
    if Path(parts[0]).name == "env":
        parts = [part for part in parts[1:] if not part.startswith("-")]
    executable = shutil.which(parts[0])
    if not executable or "python" not in Path(executable).name.lower():
        raise ValueError("Expected a Python Graphify console-script launcher")
    # Do not resolve symlinks: a venv Python must retain its pyvenv.cfg context.
    return os.path.abspath(executable)


if __name__ == "__main__":
    print(resolve(sys.argv[1]))
