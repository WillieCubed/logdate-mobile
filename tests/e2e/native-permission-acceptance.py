#!/usr/bin/env python3
"""Exercise restored tracking grants and widget choices on synthetic Android emulators."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re

spec = importlib.util.spec_from_file_location("media_acceptance", Path(__file__).with_name("native-missing-media-acceptance.py"))
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)
TEST = "app.logdate.client.e2e.NativeRestoredSettingsE2ETest"
EXTRAS = ["-e", "restorePermissionAcceptance", "true"]
LOCATION = ["ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION"]
PERMISSIONS = h.PERMISSIONS + LOCATION + ["ACTIVITY_RECOGNITION"]


def run(args, serial):
    h.safe_target(serial)
    directory = args.output / serial
    directory.mkdir(mode=0o700)
    for apk in [args.app_apk, args.test_apk]:
        h.safe_target(serial)
        h.adb(serial, "install", "-r", "-t", apk)
    package = h.adb(serial, "shell", "dumpsys", "package", h.PACKAGE).stdout.decode()
    grants = {permission: bool(re.search(r"android\.permission\." + permission + r": granted=true", package)) for permission in PERMISSIONS}
    rows = h.adb(serial, "shell", "cmd", "netpolicy", "list", "wifi-networks").stdout.decode().splitlines()
    wifi_rows = [line for line in rows if line.rsplit(";", 1)[0] == "AndroidWifi"]
    original = wifi_rows[0].rsplit(";", 1)[1] if wifi_rows else None
    saver = b"enabled" in h.adb(serial, "shell", "cmd", "netpolicy", "get", "restrict-background").stdout
    airplane = h.adb(serial, "shell", "settings", "get", "global", "airplane_mode_on").stdout.strip() == b"1"
    wifi = h.adb(serial, "shell", "settings", "get", "global", "wifi_on").stdout.strip()
    if wifi not in [b"0", b"1", b"2"]:
        raise RuntimeError("Original Wi-Fi state is unavailable")
    (directory / "environment-before.json").write_text(json.dumps({"grants": grants, "metered": original,
        "dataSaver": saver, "airplane": airplane, "wifi": wifi.decode()}, indent=2))
    def probe(method, name, **kwargs):
        h.instrument(serial, TEST + "#" + method, directory / name, extras=EXTRAS, timeout=240, **kwargs)
    try:
        probe("restoreOriginalTrackingSettings", "initial-settings-cleanup.log")
        for permission in PERMISSIONS:
            result = h.adb(serial, "shell", "pm", "revoke", h.PACKAGE, "android.permission." + permission, check=False)
            if result.returncode and b"has not requested permission" not in result.stderr + result.stdout:
                raise RuntimeError("Fixture permission was not revoked")
        h.adb(serial, "shell", "cmd", "connectivity", "airplane-mode", "disable")
        h.adb(serial, "shell", "svc", "wifi", "enable")
        def grant_location():
            for permission in LOCATION:
                h.adb(serial, "shell", "pm", "grant", h.PACKAGE, "android.permission." + permission)
        def grant_motion():
            h.adb(serial, "shell", "pm", "grant", h.PACKAGE, "android.permission.ACTIVITY_RECOGNITION")
        probe("locationResumesAfterExternalGrants", "location-grant-recovery.log",
              status_actions={b"RESTORE_LOCATION_GRANT_REQUIRED": grant_location,
                              b"RESTORE_MOTION_GRANT_REQUIRED": grant_motion})
        for method, name in [("widget settings survive launcher ID remapping", "widget-remap.log"),
                             ("overlapping restored IDs preserve both widget choices", "widget-overlapping-remap.log")]:
            h.instrument(serial, "app.logdate.client.e2e.WidgetInstanceSettingsTest#" + method, directory / name)
    finally:
        try:
            probe("restoreOriginalTrackingSettings", "final-settings-cleanup.log")
        finally:
            h.restore_environment(serial, "AndroidWifi", original, saver, airplane, wifi, grants)
    return {"serial": serial, "locationAndMotionGrantRecovery": True, "widgetRemapping": True, "environmentRestored": True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emulator", action="append", required=True)
    parser.add_argument("--app-apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--synthetic-data", action="store_true", required=True)
    args = parser.parse_args()
    os.umask(0o077)
    args.output.mkdir(mode=0o700)
    results = [run(args, serial) for serial in args.emulator]
    (args.output / "acceptance.json").write_text(json.dumps(results, indent=2) + "\n")
    print(json.dumps(results))


if __name__ == "__main__":
    main()
