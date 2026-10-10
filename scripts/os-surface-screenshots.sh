#!/usr/bin/env bash
# Captures real Android system UI (launcher, shade, notifications, bubbles) for the OS integration
# screenshot lane, then copies the images into docs/screenshots/android-os-integration/.
#
# The captures come from OsSurfaceScreenshotsTest running on Gradle Managed Devices, never a
# physical device. By default it runs on the Android 17 (API 37) phone and tablet, which have App
# Bubbles, the Bubble Bar, and Live Update chips; pass managed device names to choose others, and set
# OS_SURFACE_TEST to run one test method.
#
# Usage:
#   scripts/os-surface-screenshots.sh                                  # phone + tablet, every test
#   scripts/os-surface-screenshots.sh flagshipPhoneApi36               # Android 16 phone only
#   OS_SURFACE_TEST=launcherShortcuts scripts/os-surface-screenshots.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

devices=("$@")
if [[ ${#devices[@]} -eq 0 ]]; then
    devices=(flagshipPhoneApi37 largeScreenTabletApi37)
fi

test_class="app.logdate.client.e2e.osintegration.OsSurfaceScreenshotsTest"
if [[ -n "${OS_SURFACE_TEST:-}" ]]; then
    test_class="${test_class}#${OS_SURFACE_TEST}"
fi

tasks=()
for device in "${devices[@]}"; do
    tasks+=(":app:android-main:${device}DebugAndroidTest")
done

# Keep going when a test fails: the captures that did succeed are still worth copying and reviewing.
gradle_status=0
./gradlew "${tasks[@]}" \
    -Plogdate.androidTestClass="$test_class" \
    -Plogdate.androidTestOrchestrator=false \
    -Pandroid.experimental.testOptions.managedDevices.maxConcurrentDevices=1 \
    --continue || gradle_status=$?

# A passing run publishes captures under outputs/; a failing one leaves them under intermediates/.
output_roots=(
    "app/android-main/build/outputs/managed_device_android_test_additional_output"
    "app/android-main/build/intermediates/managed_device_android_test_additional_output"
)
destination="docs/screenshots/android-os-integration"
copied=0
while IFS= read -r -d '' image; do
    relative="${image#*os-integration/}"
    mkdir -p "$destination/$(dirname "$relative")"
    cp "$image" "$destination/$relative"
    echo "  $destination/$relative"
    copied=$((copied + 1))
done < <(find "${output_roots[@]}" -path '*/os-integration/*' \( -name '*.png' -o -name '*.txt' \) -not -path '*/failures/*' -print0 2>/dev/null)

if [[ $copied -eq 0 ]]; then
    echo "No OS integration captures were produced. Check the managed device test output above." >&2
    exit 1
fi
echo "Copied $copied capture(s). Review them, register them in config/screenshots/os-integration-coverage.txt, and run ./run screenshots:check."
if [[ $gradle_status -ne 0 ]]; then
    echo "Some captures failed; see failures/ under the managed device output for what the screen showed." >&2
    exit "$gradle_status"
fi
