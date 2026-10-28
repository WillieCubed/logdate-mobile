#!/usr/bin/env bash
set -euo pipefail

if [[ $# != 1 ]]; then
  echo 'Usage: bash tests/e2e/managed-editor-interruption-acceptance.sh NEW_OUTPUT_DIRECTORY' >&2
  exit 2
fi
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$root"
umask 077
mkdir "$1"
output="$(cd "$1" && pwd)"
git rev-parse HEAD > "$output/base-commit.txt"
git diff --binary HEAD > "$output/source.patch"
python3 - "$output" << 'PY'
import hashlib
import json
import shutil
import sys
from pathlib import Path
output = Path(sys.argv[1])
roots = ["app/compose-main/src", "app/android-main/src", "client/feature/editor/src",
         "client/media/src", "client/ui/src", "tools/native-restore-driver/src"]
files = sorted(p for root in roots for p in Path(root).rglob("*") if p.is_file())
(output / "source-sha256.json").write_text(json.dumps({str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in files}, indent=2) + "\n")
for name in ["tools/native-restore-driver/src/androidTest/kotlin/app/logdate/tools/restoredriver/NativeLiveRecordingDeathTest.kt",
             "app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/EditorRecordingLifecycleTest.kt",
             "tests/e2e/managed-editor-interruption-acceptance.sh"]:
    destination = output / "test-source" / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(name, destination)
PY

./gradlew :app:android-main:assembleDebug :app:android-main:assembleDebugAndroidTest \
  :tools:native-restore-driver:assembleDebug :tools:native-restore-driver:assembleDebugAndroidTest \
  --max-workers=2 --no-daemon --console=plain > "$output/build.log" 2>&1
read -r app_sha probe_sha << EOF
$(
  python3 - << 'PY'
import hashlib
from pathlib import Path
paths = ["app/android-main/build/outputs/apk/debug/android-main-debug.apk",
         "app/android-main/build/outputs/apk/androidTest/debug/android-main-debug-androidTest.apk"]
print(*(hashlib.sha256(Path(p).read_bytes()).hexdigest() for p in paths))
PY
)
EOF
printf '%s %s\n' "$app_sha" "$probe_sha" > "$output/apk-sha256.txt"
death_status=0
./gradlew :tools:native-restore-driver:restorePairApi34DebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.logdate.tools.restoredriver.NativeLiveRecordingDeathTest \
  "-Pandroid.testInstrumentationRunnerArguments.nativeAppSha256=$app_sha" \
  "-Pandroid.testInstrumentationRunnerArguments.nativeProbeSha256=$probe_sha" \
  -Pandroid.experimental.androidTest.builtin_test_platform=false \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader \
  --max-workers=2 --no-daemon --console=plain > "$output/live-death.log" 2>&1 || death_status=$?
for pair in \
  "tools/native-restore-driver/build/outputs/androidTest-results/managedDevice/debug/restorePairApi34:live-death-results" \
  "tools/native-restore-driver/build/intermediates/managed_device_android_test_additional_output/debugAndroidTest/restorePairApi34DebugAndroidTest:live-death-output"; do
  [[ ! -d "${pair%:*}" ]] || cp -R "${pair%:*}" "$output/${pair##*:}"
done
dismissal_status=0
./gradlew :app:android-main:phoneApi34DebugAndroidTest \
  -Plogdate.androidTestClass=app.logdate.client.e2e.EditorRecordingLifecycleTest \
  -Plogdate.androidTestOrchestrator=false -Plogdate.androidTestCoverage=false \
  -Pandroid.experimental.androidTest.builtin_test_platform=false \
  -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader \
  --max-workers=2 --no-daemon --console=plain > "$output/dismissal.log" 2>&1 || dismissal_status=$?
for pair in \
  "app/android-main/build/outputs/androidTest-results/managedDevice/debug/phoneApi34:dismissal-results" \
  "app/android-main/build/intermediates/managed_device_android_test_additional_output/debugAndroidTest/phoneApi34DebugAndroidTest:dismissal-output"; do
  [[ ! -d "${pair%:*}" ]] || cp -R "${pair%:*}" "$output/${pair##*:}"
done
((death_status == 0 && dismissal_status == 0))
python3 - "$output" << 'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
output = Path(sys.argv[1])
for directory, expected in [("live-death-results", 1), ("dismissal-results", 4)]:
    cases = [case for path in (output / directory).glob("TEST-*.xml") for case in ET.parse(path).getroot().iter("testcase")]
    assert len(cases) == expected, (directory, len(cases))
    assert all(not list(case) for case in cases), "Acceptance requires passed cases without failures or skips"
PY
