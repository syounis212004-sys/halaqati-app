#!/usr/bin/env bash
set -euo pipefail
repo_root="$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)"
cd "$repo_root"
source tools/codex/env.sh
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  echo 'Set ANDROID_SERIAL to an authorized disposable test device from adb devices -l.' >&2
  exit 1
fi
if [[ "$(adb -s "$ANDROID_SERIAL" get-state)" != device ]]; then
  echo 'Selected test device is unavailable.' >&2
  exit 1
fi
python3 tools/codex/gradle.py :app:connectedDebugAndroidTest
mkdir -p .codex-work/screenshots
adb -s "$ANDROID_SERIAL" exec-out screencap -p > .codex-work/screenshots/android.png
echo 'Captured .codex-work/screenshots/android.png; review Arabic, RTL, font scale, and both themes.'
