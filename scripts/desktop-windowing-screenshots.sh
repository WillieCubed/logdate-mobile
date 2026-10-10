#!/usr/bin/env bash
# Captures LogDate in Android desktop windowing on an Android 17 tablet emulator, then copies the
# images into docs/screenshots/android-os-integration/.
#
# The stock tablet image keeps desktop windowing off. Turning it on takes a developer setting and a
# restart, and a Gradle Managed Device cannot restart in the middle of a run, so this script runs its
# own emulator instead. It creates the emulator the first time, boots it without a window, turns
# desktop windowing on and restarts it if needed, installs the debug app and its tests, and runs
# OsSurfaceScreenshotsTest#desktopWindows and #crossAppDrag. It only ever talks to that emulator's
# serial, never to a physical device, and shuts the emulator down again if it started it.
#
# Usage:
#   scripts/desktop-windowing-screenshots.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_HOME="$SDK"
AVD="logdate_desktop_windowing_api37"
IMAGE="system-images;android-37.0;google_apis_ps16k;arm64-v8a"
PORT=5580
SERIAL="emulator-$PORT"
APP_ID="studio.hypertext.logdate.debug"
TESTS=(desktopWindows crossAppDrag)
TEST_CLASS="$(printf 'app.logdate.client.e2e.osintegration.OsSurfaceScreenshotsTest#%s,' "${TESTS[@]}")"
TEST_CLASS="${TEST_CLASS%,}"
DEVICE_OUTPUT="/sdcard/Android/media/$APP_ID/additional_test_output"

adb() { "$SDK/platform-tools/adb" -s "$SERIAL" "$@"; }

wait_for_boot() {
    adb wait-for-device
    until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
        sleep 5
    done
}

if ! "$SDK/emulator/emulator" -list-avds | grep -qx "$AVD"; then
    echo "Creating the $AVD emulator"
    echo no | "$SDK/cmdline-tools/latest/bin/avdmanager" create avd -n "$AVD" -k "$IMAGE" -d pixel_tablet
    # A hardware keyboard, like a tablet docked for desktop use, and room for several windows.
    perl -pi -e 's/^hw\.keyboard\s*=.*/hw.keyboard = yes/; s/^hw\.ramSize\s*=.*/hw.ramSize = 4096/' \
        "$HOME/.android/avd/$AVD.avd/config.ini"
fi

started_here=false
if ! adb get-state >/dev/null 2>&1; then
    echo "Booting $AVD as $SERIAL"
    "$SDK/emulator/emulator" -avd "$AVD" -port "$PORT" -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect >/dev/null 2>&1 &
    started_here=true
fi
trap 'if $started_here; then adb emu kill >/dev/null 2>&1 || true; fi' EXIT
wait_for_boot

# The developer option "Enable desktop windowing" is override_desktop_mode_features. The Pixel Tablet
# emulator is not on Android's list of desktop devices, so the device check is lifted too, which
# needs root (available on google_apis images).
if [[ "$(adb shell settings get global override_desktop_mode_features | tr -d '\r')" != "1" ||
    "$(adb shell getprop persist.wm.debug.desktop_mode_enforce_device_restrictions | tr -d '\r')" != "false" ]]; then
    echo "Turning desktop windowing on and restarting $SERIAL"
    adb root >/dev/null
    adb wait-for-device
    adb shell settings put global override_desktop_mode_features 1
    adb shell settings put global enable_freeform_support 1
    adb shell setprop persist.wm.debug.desktop_mode true
    adb shell setprop persist.wm.debug.desktop_mode_enforce_device_restrictions false
    # Android writes settings to disk a few seconds after they change; restarting sooner loses them.
    sleep 8
    adb reboot
    sleep 15
    wait_for_boot
fi

./gradlew :app:android-main:assembleDebug :app:android-main:assembleDebugAndroidTest
adb install -r -g app/android-main/build/outputs/apk/debug/android-main-debug.apk
adb install -r -g app/android-main/build/outputs/apk/androidTest/debug/android-main-debug-androidTest.apk

adb shell rm -r "$DEVICE_OUTPUT/os-integration" >/dev/null 2>&1 || true
instrument_output="$(adb shell am instrument -w \
    -e class "$TEST_CLASS" \
    -e logdate.desktopWindowing true \
    -e additionalTestOutputDir "$DEVICE_OUTPUT" \
    "$APP_ID.test/androidx.test.runner.AndroidJUnitRunner")"
echo "$instrument_output"

staging="$(mktemp -d)"
adb pull "$DEVICE_OUTPUT/os-integration" "$staging" >/dev/null
destination="docs/screenshots/android-os-integration"
copied=0
while IFS= read -r -d '' image; do
    relative="${image#"$staging"/os-integration/}"
    mkdir -p "$destination/$(dirname "$relative")"
    cp "$image" "$destination/$relative"
    echo "  $destination/$relative"
    copied=$((copied + 1))
done < <(find "$staging/os-integration" \( -name '*.png' -o -name '*.txt' \) -not -path '*/failures/*' -print0 2>/dev/null)

if ! grep -q "OK (${#TESTS[@]} tests)" <<<"$instrument_output" || [[ $copied -eq 0 ]]; then
    echo "The desktop windowing capture did not complete. Failure captures, if any, are in $staging." >&2
    exit 1
fi
echo "Copied $copied capture(s). Review them, register them in config/screenshots/os-integration-coverage.txt, and run ./run screenshots:check."
