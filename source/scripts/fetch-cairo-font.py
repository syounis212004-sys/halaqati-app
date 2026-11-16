#!/usr/bin/env python3
"""Fetch the official Cairo variable font at build time and verify the pinned Git blob.
RC13 writes the same verified bytes to Android resources and the local WebView document assets.
The source archive deliberately does not redistribute the raw font file.
"""
from pathlib import Path
import hashlib, urllib.request

URL = 'https://raw.githubusercontent.com/google/fonts/main/ofl/cairo/Cairo%5Bslnt%2Cwght%5D.ttf'
EXPECTED_GIT_BLOB_SHA1 = '25641b1c828857698457bcd9f5a102616b4e8033'
ROOT = Path(__file__).resolve().parents[1]
DESTS = [
    ROOT / 'app/src/main/res/font/cairo.ttf',
    ROOT / 'app/src/main/assets/documents/cairo.ttf',
]

def blob_sha1(data: bytes) -> str:
    return hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest()

def valid(path: Path) -> bool:
    return path.exists() and blob_sha1(path.read_bytes()) == EXPECTED_GIT_BLOB_SHA1

def write_all(data: bytes) -> None:
    for dest in DESTS:
        dest.parent.mkdir(parents=True, exist_ok=True)
        tmp = dest.with_suffix(dest.suffix + '.tmp')
        tmp.write_bytes(data)
        tmp.replace(dest)

AMIRI_URL = 'https://raw.githubusercontent.com/google/fonts/main/ofl/amiri/Amiri-Bold.ttf'
AMIRI_BLOB_SHA1 = '017afa9adb8ce417b08796d22d9204569ad211c7'
AMIRI_DEST = ROOT / 'app/src/main/res/font/amiri_bold.ttf'

def fetch_amiri() -> None:
    """RC41: Amiri Bold (OFL) is used for large headings only; pinned like Cairo."""
    if AMIRI_DEST.exists() and blob_sha1(AMIRI_DEST.read_bytes()) == AMIRI_BLOB_SHA1:
        print('Amiri Bold already present and verified.')
        return
    with urllib.request.urlopen(AMIRI_URL, timeout=60) as response:
        data = response.read()
    actual = blob_sha1(data)
    if actual != AMIRI_BLOB_SHA1:
        raise SystemExit(f'Amiri Git blob SHA-1 mismatch: {actual}')
    AMIRI_DEST.parent.mkdir(parents=True, exist_ok=True)
    AMIRI_DEST.write_bytes(data)
    print(f'Embedded verified Amiri Bold ({len(data)} bytes).')

def main() -> None:
    fetch_amiri()
    if all(valid(p) for p in DESTS):
        print('Cairo font already present in resource + document asset paths and verified.')
        return
    source = next((p for p in DESTS if valid(p)), None)
    if source:
        data = source.read_bytes()
    else:
        with urllib.request.urlopen(URL, timeout=60) as response:
            data = response.read()
    actual = blob_sha1(data)
    if actual != EXPECTED_GIT_BLOB_SHA1:
        raise SystemExit(f'Cairo Git blob SHA-1 mismatch: {actual}')
    write_all(data)
    print(f'Embedded verified Cairo font in {len(DESTS)} locations ({len(data)} bytes each).')

if __name__ == '__main__':
    main()
