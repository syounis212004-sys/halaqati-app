#!/usr/bin/env bash
# Runs inside reactivecircus/android-emulator-runner (emulator already booted). Measures install sizes (release, optional) then runs the
# instrumented DkPerfTest from the debug build and pulls its JSON. Never publishes anything. Usage: perf-on-emulator.sh <out_dir>
set -uo pipefail
OUT="${1:?out dir}"
PKG=com.mohammedsamir.halaqati
mkdir -p "$OUT"
APPDIR="app/build/outputs/apk"
RELEASE_APK="$APPDIR/release/app-release.apk"
DEBUG_APK="$APPDIR/debug/app-debug.apk"
TEST_APK="$APPDIR/androidTest/debug/app-debug-androidTest.apk"

adb wait-for-device
until [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" = 1 ]; do sleep 2; done
adb shell getprop > "$OUT/getprop.txt" || true
adb shell df -k /data > "$OUT/df-data.txt" || true

# installed size of one APK as the package manager sees it: base.apk, oat/odex dirs, and the data dir after install
measure_install() { # <label> <apk>
  local label="$1" apk="$2" dir
  adb install -r -t "$apk" > "$OUT/install-$label.txt" 2>&1 || { echo "install $label failed" >> "$OUT/errors.txt"; return 1; }
  dir="$(adb shell pm path "$PKG" | head -1 | sed 's/^package://; s#/base.apk##' | tr -d '\r')"
  {
    echo "label=$label"
    echo "apk_file_bytes=$(stat -c %s "$apk")"
    echo "code_dir=$dir"
    echo "--- du -sk code dir"; adb shell du -sk "$dir" 2>&1
    echo "--- du -ak code dir"; adb shell du -ak "$dir" 2>&1
    echo "--- du -sk data dir (just after install)"; adb shell "run-as $PKG du -sk /data/data/$PKG" 2>&1 || adb shell "du -sk /data/data/$PKG" 2>&1
    echo "--- dumpsys codePath/size lines"; adb shell dumpsys package "$PKG" | grep -iE 'codePath|resourcePath|dataDir|versionName|versionCode' | head -10
  } > "$OUT/sizes-$label.txt" 2>&1
}

if [ -s "$RELEASE_APK" ]; then
  measure_install release "$RELEASE_APK"
  adb uninstall "$PKG" > /dev/null 2>&1 || true   # the debug build has a different signature
else
  echo "no release APK built (measure_release=false): installed-size figures are for the debug build only" >> "$OUT/notes.txt"
fi

measure_install debug "$DEBUG_APK" || exit 1
adb install -r -t "$TEST_APK" > "$OUT/install-test.txt" 2>&1 || { echo "test APK install failed" >> "$OUT/errors.txt"; exit 1; }

adb logcat -c || true
set +e
adb shell am instrument -w -r -e class "$PKG.quran.dk.DkPerfTest" "$PKG.test/androidx.test.runner.AndroidJUnitRunner" > "$OUT/instrument.txt" 2>&1
RC=$?
adb logcat -d -s DkPerf:I AndroidRuntime:E > "$OUT/logcat-dkperf.txt" 2>&1 || true
adb exec-out run-as "$PKG" cat files/dk-perf-results.json > "$OUT/dk-perf-results.json" 2>> "$OUT/errors.txt" || true
{ echo "--- du -sk data dir after the run"; adb shell "run-as $PKG du -sk /data/data/$PKG"; adb shell "run-as $PKG du -ak /data/data/$PKG" | sort -rn | head -15; } > "$OUT/sizes-debug-after-run.txt" 2>&1 || true

# `am instrument` exits 0 even when the test fails: read the per-test status codes (0 ok, -1 error, -2 failure, -4 assumption failed = pack missing)
if [ ! -s "$OUT/dk-perf-results.json" ]; then echo "no results JSON produced" >> "$OUT/errors.txt"; exit 1; fi
if grep -qE '^INSTRUMENTATION_STATUS_CODE: (-1|-2|-4)' "$OUT/instrument.txt" || ! grep -qE '^INSTRUMENTATION_STATUS_CODE: 0' "$OUT/instrument.txt"; then
  echo "instrumented test reported a failure (rc=$RC); see instrument.txt" >> "$OUT/errors.txt"; exit 1
fi
exit 0
