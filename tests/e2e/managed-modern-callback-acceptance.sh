#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 1 ]]; then
  echo 'Usage: bash tests/e2e/managed-modern-callback-acceptance.sh PRIVATE_OUTPUT_DIRECTORY' >&2
  exit 2
fi
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
umask 077
mkdir "$1"
output="$(cd "$1" && pwd)"

# Compile before GMD owns an emulator so compiler and emulator memory peaks do not overlap.
./gradlew :app:android-main:assembleDebug :app:android-main:assembleDebugAndroidTest \
  :tools:native-restore-driver:assembleDebug :tools:native-restore-driver:assembleDebugAndroidTest \
  --max-workers=2 --no-daemon --console=plain > "$output/build.log" 2>&1
read -r app_sha probe_sha contract <<EOF
$(python3 - <<'PYARGS'
import base64
import hashlib
from pathlib import Path
app = Path("app/android-main/build/outputs/apk/debug/android-main-debug.apk")
probe = Path("app/android-main/build/outputs/apk/androidTest/debug/android-main-debug-androidTest.apk")
contract = base64.b64encode(Path("tests/e2e/native-modern-callback-cases.json").read_bytes()).decode()
print(hashlib.sha256(app.read_bytes()).hexdigest(), hashlib.sha256(probe.read_bytes()).hexdigest(), contract)
PYARGS
)
EOF
managed_status=0
./gradlew :tools:native-restore-driver:restoreCallbackApi36_1DebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.tools.restoredriver.NativeModernRestoreCallbackTest \
  "-Pandroid.testInstrumentationRunnerArguments.nativeAppSha256=$app_sha" \
  "-Pandroid.testInstrumentationRunnerArguments.nativeProbeSha256=$probe_sha" \
  "-Pandroid.testInstrumentationRunnerArguments.nativeCallbackContract=$contract" \
  -Pandroid.experimental.androidTest.builtin_test_platform=false \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader \
  --max-workers=2 --no-daemon --console=plain > "$output/managed.log" 2>&1 || managed_status=$?

results=tools/native-restore-driver/build/outputs/androidTest-results/managedDevice/debug/restoreCallbackApi36_1
if [[ -d "$results" ]]; then
  cp -R "$results" "$output/results"
fi
additional=tools/native-restore-driver/build/intermediates/managed_device_android_test_additional_output/debugAndroidTest/restoreCallbackApi36_1DebugAndroidTest
if [[ -d "$additional" ]]; then
  cp -R "$additional" "$output/additional-output"
fi
if (( managed_status != 0 )); then
  exit "$managed_status"
fi
python3 tests/e2e/native-instrumentation-results.py \
  --managed-results "$output/results" --expected-cases tests/e2e/native-modern-callback-driver-cases.json
python3 - "$output" "$app_sha" "$probe_sha" <<'PY'
import json
from pathlib import Path
import sys

output = Path(sys.argv[1])
artifacts = {"android-main-debug.apk": sys.argv[2], "android-main-debug-androidTest.apk": sys.argv[3]}
with (output / "acceptance.json").open("w") as receipt:
    json.dump({"managedModernRestoreCallback": True, "requiredSdkFull": 3600001,
               "googleRestoreProviderAccepted": False,
               "apks": artifacts}, receipt, indent=2)
    receipt.write("\n")
PY
