#!/usr/bin/env python3
"""Generate the compact offline QCF4 verse-line layout used by RC8.

Source is pinned to a concrete GitHub commit and verified against the Git blob SHA
that was reviewed for RC8. The runtime asset contains three bytes per ayah: page uint16 (big-endian),
then upper nibble = first QCF4 line
and lower nibble = last QCF4 line (1..15). Page and line metadata both come
from the pinned QCF4 source so partial-page accounting cannot drift.
"""
from __future__ import annotations

import base64
import hashlib
import json
import os
import time
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "app" / "src" / "main" / "assets" / "quran"
OUT_BIN = OUT_DIR / "qcf4-layout-v2.bin"
OUT_MANIFEST = OUT_DIR / "qcf4-layout-source.json"

QCF4_COMMIT = "5130511027e769f0a8f4eeb7f00f46bde3788d60"
EXPECTED_BLOB_SHA1 = "568f4820cefe142ccd3f75b89fa99b1a2ad01fe9"
SOURCE_URL = (
    "https://raw.githubusercontent.com/MohamadHajjRabee/quran-qcf4/"
    f"{QCF4_COMMIT}/verses.json"
)
AYAH_COUNTS = [
    7,286,200,176,120,165,206,75,129,109,123,111,43,52,99,128,111,110,
    98,135,112,78,118,64,77,227,93,88,69,60,34,30,73,54,45,83,
    182,88,75,85,54,53,89,59,37,35,38,29,18,45,60,49,62,55,
    78,96,29,22,24,13,14,11,11,18,12,12,30,52,52,44,28,28,
    20,56,40,31,50,40,46,42,29,19,36,25,22,17,19,26,30,20,
    15,21,11,8,8,19,5,8,8,11,11,8,3,9,5,4,7,3,6,3,5,4,5,6,
]
AYAH_COUNT = 6236
LINE_MODEL = 15


def git_blob_sha1(raw: bytes) -> str:
    return hashlib.sha1(b"blob " + str(len(raw)).encode() + b"\0" + raw).hexdigest()


def read_source() -> bytes:
    local = os.environ.get("QCF4_VERSES_JSON", "").strip()
    if local:
        return Path(local).read_bytes()

    headers = {"User-Agent": "Halaqati-RC8-QCF4-Builder/1", "Accept": "application/json"}
    urls = [
        (SOURCE_URL, False),
        (
            "https://api.github.com/repos/MohamadHajjRabee/quran-qcf4/git/blobs/" + EXPECTED_BLOB_SHA1,
            True,
        ),
    ]
    failures: list[str] = []
    for attempt in range(1, 4):
        for url, github_blob in urls:
            try:
                req = Request(url, headers=headers)
                with urlopen(req, timeout=25) as response:
                    payload = response.read()
                if github_blob:
                    decoded = json.loads(payload)
                    if decoded.get("encoding") != "base64":
                        raise ValueError("unexpected GitHub blob encoding")
                    payload = base64.b64decode(decoded["content"])
                if git_blob_sha1(payload) != EXPECTED_BLOB_SHA1:
                    raise ValueError("downloaded content failed pinned blob verification")
                return payload
            except Exception as exc:
                failures.append(f"attempt {attempt} {url}: {exc}")
        if attempt < 3:
            time.sleep(attempt * 2)
    raise SystemExit("Unable to fetch pinned QCF4 verses.json\n" + "\n".join(failures[-6:]))


def build(raw: bytes) -> bytearray:
    sha = git_blob_sha1(raw)
    if sha != EXPECTED_BLOB_SHA1:
        raise SystemExit(f"QCF4 verses.json blob SHA mismatch: {sha}")
    verses = json.loads(raw)
    if len(verses) != AYAH_COUNT:
        raise SystemExit(f"Expected {AYAH_COUNT} ayahs, got {len(verses)}")

    prefix = [0]
    for count in AYAH_COUNTS:
        prefix.append(prefix[-1] + count)
    if prefix[-1] != AYAH_COUNT:
        raise SystemExit("Internal ayah count mismatch")

    packed = bytearray(AYAH_COUNT * 3)
    pages_seen: set[int] = set()
    for key, value in verses.items():
        try:
            surah_s, ayah_s = key.split(":", 1)
            surah, ayah = int(surah_s), int(ayah_s)
        except Exception as exc:
            raise SystemExit(f"Bad verse key: {key}") from exc
        if not (1 <= surah <= 114 and 1 <= ayah <= AYAH_COUNTS[surah - 1]):
            raise SystemExit(f"Out-of-range verse key: {key}")
        g = prefix[surah - 1] + ayah
        lines = [int(x.get("line", 0)) for x in value.get("lines", []) if x.get("line")]
        if not lines:
            raise SystemExit(f"No QCF4 line metadata for {key}")
        first, last = min(lines), max(lines)
        if not (1 <= first <= last <= LINE_MODEL):
            raise SystemExit(f"Invalid QCF4 lines for {key}: {first}..{last}")
        page = int(value.get("page", 0))
        if not (1 <= page <= 604):
            raise SystemExit(f"Invalid page for {key}: {page}")
        pages_seen.add(page)
        offset = (g - 1) * 3
        packed[offset] = (page >> 8) & 0xFF
        packed[offset + 1] = page & 0xFF
        packed[offset + 2] = (first << 4) | last

    missing = [g for g in range(1, AYAH_COUNT + 1) if packed[(g - 1) * 3 + 2] == 0]
    if missing:
        raise SystemExit(f"Missing packed ayahs: {missing[:10]}")
    if pages_seen != set(range(1, 605)):
        missing_pages = sorted(set(range(1, 605)) - pages_seen)
        raise SystemExit(f"QCF4 source does not cover all 604 pages: {missing_pages[:10]}")
    return packed


def valid_existing() -> bool:
    if not OUT_BIN.exists() or not OUT_MANIFEST.exists() or OUT_BIN.stat().st_size != AYAH_COUNT * 3:
        return False
    try:
        manifest = json.loads(OUT_MANIFEST.read_text(encoding="utf-8"))
    except Exception:
        return False
    return (
        manifest.get("source_commit") == QCF4_COMMIT
        and manifest.get("source_blob_sha1") == EXPECTED_BLOB_SHA1
        and manifest.get("ayah_count") == AYAH_COUNT
        and manifest.get("line_model") == LINE_MODEL
        and manifest.get("asset_sha256") == hashlib.sha256(OUT_BIN.read_bytes()).hexdigest()
    )


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    if valid_existing():
        print(f"QCF4 layout already verified: {OUT_BIN}")
        return
    # Normal RC8 builds are intentionally network-free for Quran metadata. The
    # verified compact asset is part of the source archive. If it is missing or
    # corrupt, fail immediately instead of spending minutes retrying a remote URL.
    # Maintainers may opt in explicitly when regenerating the pinned asset.
    if os.environ.get("HALAQATI_ALLOW_QCF4_FETCH", "").strip() != "1":
        raise SystemExit(
            "Bundled QCF4 layout is missing or invalid; network regeneration disabled "
            "for standard builds. Restore the verified asset or set "
            "HALAQATI_ALLOW_QCF4_FETCH=1 for an explicit maintenance regeneration."
        )
    raw = read_source()
    packed = build(raw)
    OUT_BIN.write_bytes(packed)
    manifest = {
        "format": "halaqati-qcf4-page-line-v2",
        "source": "MohamadHajjRabee/quran-qcf4 verses.json",
        "source_url": SOURCE_URL,
        "source_commit": QCF4_COMMIT,
        "source_blob_sha1": EXPECTED_BLOB_SHA1,
        "ayah_count": AYAH_COUNT,
        "page_count": 604,
        "line_model": LINE_MODEL,
        "asset_sha256": hashlib.sha256(packed).hexdigest(),
    }
    OUT_MANIFEST.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Generated verified QCF4 asset: {OUT_BIN} ({len(packed)} bytes)")
    print("SHA-256:", manifest["asset_sha256"])


if __name__ == "__main__":
    main()
