#!/usr/bin/env bash
# Captures LogDate's Capture widget on the hub, the widget space an Android tablet shows over its lock
# screen while it charges, then copies the images into docs/screenshots/android-os-integration/.
#
# Android's emulator images ship with the hub turned off, through two device settings that only the
# system image can change: config_glanceableHubEnabled in the framework and
# config_communalServiceEnabled in SystemUI. This script turns them on with two small resource
# overlays (sources in scripts/emulator-hub-overlays/), which it builds and preinstalls in the
# emulator's /vendor/overlay. That needs an emulator started with -writable-system, so it uses the
# desktop-windowing emulator that scripts/desktop-windowing-screenshots.sh creates, booted that way.
# The overlays change only that emulator's writable copy of its system image; booting it without
# -writable-system uses the untouched image again.
#
# It then empties the hub, so every run adds the widget the same way, and runs
# OsSurfaceScreenshotsTest#captureWidgetOnHub through scripts/desktop-windowing-screenshots.sh. It
# only ever talks to that emulator's serial, never to a physical device.
#
# Usage:
#   scripts/hub-widget-screenshots.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export ANDROID_HOME="$SDK"
AVD="logdate_desktop_windowing_api37"
PORT=5580
SERIAL="emulator-$PORT"
BUILD_TOOLS="$(find "$SDK/build-tools" -maxdepth 1 -mindepth 1 -type d | sort -V | tail -1)"
PLATFORM_JAR="$(find "$SDK/platforms" -maxdepth 2 -name android.jar | sort -V | tail -1)"
OVERLAY_SOURCES="scripts/emulator-hub-overlays"
OVERLAYS=(framework systemui)

adb() { "$SDK/platform-tools/adb" -s "$SERIAL" "$@"; }

wait_for_boot() {
    adb wait-for-device
    until [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
        sleep 5
    done
}

reboot_and_wait() {
    adb reboot
    sleep 15
    wait_for_boot
    adb root >/dev/null
    adb wait-for-device
}

if ! "$SDK/emulator/emulator" -list-avds | grep -qx "$AVD"; then
    echo "The $AVD emulator does not exist yet. Run scripts/desktop-windowing-screenshots.sh once to create it." >&2
    exit 1
fi

if ! adb get-state >/dev/null 2>&1; then
    echo "Booting $AVD as $SERIAL with a writable system image"
    "$SDK/emulator/emulator" -avd "$AVD" -port "$PORT" -writable-system -no-window -no-audio -no-boot-anim \
        -gpu swiftshader_indirect >/dev/null 2>&1 &
fi
wait_for_boot
adb root >/dev/null
adb wait-for-device

# Build each overlay: compile its resources, link them against the platform, and sign the APK.
staging="$(mktemp -d)"
for overlay in "${OVERLAYS[@]}"; do
    source="$OVERLAY_SOURCES/$overlay"
    "$BUILD_TOOLS/aapt2" compile --dir "$source/res" -o "$staging/$overlay.zip"
    "$BUILD_TOOLS/aapt2" link -o "$staging/$overlay-unsigned.apk" --manifest "$source/AndroidManifest.xml" \
        -I "$PLATFORM_JAR" "$staging/$overlay.zip"
    "$BUILD_TOOLS/apksigner" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
        --out "$staging/LogDateHub-$overlay.apk" "$staging/$overlay-unsigned.apk"
done

installed=true
for overlay in "${OVERLAYS[@]}"; do
    adb shell cmd overlay list | grep -q "\[x\] studio.hypertext.logdate.emulator.hub$overlay" || installed=false
done

if ! $installed; then
    echo "Installing the hub overlays on $SERIAL"
    if ! adb remount 2>&1 | tee "$staging/remount.log" | grep -q "Remount succeeded"; then
        echo "$SERIAL cannot remount its system partitions. Shut it down so this script can boot it with -writable-system." >&2
        exit 1
    fi
    # The first remount on a fresh emulator sets up an overlay filesystem that needs a restart.
    if grep -q "reboot" "$staging/remount.log"; then
        reboot_and_wait
        adb remount >/dev/null
    fi
    for overlay in "${OVERLAYS[@]}"; do
        directory="/vendor/overlay/LogDateHub-$overlay"
        adb shell mkdir -p "$directory"
        adb push "$staging/LogDateHub-$overlay.apk" "$directory/LogDateHub-$overlay.apk" >/dev/null
        adb shell "chmod 755 $directory && chmod 644 $directory/*.apk && chcon -R u:object_r:vendor_overlay_file:s0 $directory"
    done
    reboot_and_wait
    for overlay in "${OVERLAYS[@]}"; do
        if ! adb shell cmd overlay list | grep -q "\[x\] studio.hypertext.logdate.emulator.hub$overlay"; then
            echo "The $overlay hub overlay did not apply on $SERIAL." >&2
            exit 1
        fi
    done
fi

# Start from an empty hub, so the test adds the Capture widget the way a person would.
adb shell "rm -f /data/user_de/0/com.android.systemui/databases/communal_db*"
adb shell "pkill -f com.android.systemui" || true
sleep 10

DESKTOP_TEST=captureWidgetOnHub scripts/desktop-windowing-screenshots.sh
