#!/usr/bin/env python3
"""Install pinned Linux x64 build tools in a user-owned directory."""
import hashlib
import os
from pathlib import Path
import subprocess
import tarfile
import urllib.request
import zipfile

base = Path(os.environ.get("HALAQATI_TOOLS", Path.home() / ".local/share/halaqati"))
base.mkdir(parents=True, exist_ok=True)
downloads = base / "downloads"
downloads.mkdir(exist_ok=True)


def download(name, url, expected, algorithm="sha256"):
    dest = downloads / name
    if not dest.exists():
        print(f"Downloading {name}", flush=True)
        urllib.request.urlretrieve(url, dest.with_suffix(".part"))
        dest.with_suffix(".part").rename(dest)
    actual = hashlib.new(algorithm, dest.read_bytes()).hexdigest()
    if actual != expected:
        raise SystemExit(f"Checksum mismatch: {dest}; remove it and retry")
    return dest


if not (base / "jdk-17/bin/java").exists():
    archive = download(
        "jdk17.tar.gz",
        "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.21%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.21_9.tar.gz",
        "8b9b611d917e3dfd8419d3566b5949226f69b052fed7e109c1368add1dfe8711",
    )
    with tarfile.open(archive) as bundle:
        top = bundle.getnames()[0].split("/")[0]
        bundle.extractall(base, filter="data")
    (base / top).rename(base / "jdk-17")

if not (base / "gradle-8.13/bin/gradle").exists():
    archive = download(
        "gradle-8.13-bin.zip",
        "https://services.gradle.org/distributions/gradle-8.13-bin.zip",
        "20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78",
    )
    with zipfile.ZipFile(archive) as bundle:
        bundle.extractall(base)
    (base / "gradle-8.13/bin/gradle").chmod(0o755)

sdk = base / "android-sdk"
manager = sdk / "cmdline-tools/23.0/bin/sdkmanager"
if not manager.exists():
    archive = download(
        "commandlinetools-linux-16111833_latest.zip",
        "https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip",
        "e025545c62a8e64c7559119566a569fb1dec5f60",
        "sha1",  # Published Android repository manifest checksum.
    )
    target = sdk / "cmdline-tools"
    target.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as bundle:
        bundle.extractall(target)
    (target / "cmdline-tools").rename(target / "23.0")
    for executable in (target / "23.0/bin").iterdir():
        executable.chmod(0o755)

env = {**os.environ, "JAVA_HOME": str(base / "jdk-17"), "ANDROID_HOME": str(sdk)}
command = [str(manager), f"--sdk_root={sdk}"]
subprocess.run(command + ["--licenses"], input="y\n" * 100, text=True, env=env, check=True)
subprocess.run(command + ["platform-tools", "platforms;android-36", "build-tools;35.0.0", "build-tools;36.0.0"], env=env, check=True)
print(f"Installed Android tools: {base}")
