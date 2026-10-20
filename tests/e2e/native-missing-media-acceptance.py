#!/usr/bin/env python3
"""Verify missing-original recovery on explicitly selected synthetic Android emulators."""

import argparse
import json
import os
from pathlib import Path
import re
import runpy
import shlex
import subprocess
import threading
import time
import uuid

# Use the adjacent helper for both standalone and importlib-loaded entry points.
instrumentation_passed = runpy.run_path(
    str(Path(__file__).with_name("native-instrumentation-results.py"))
)["instrumentation_passed"]


PACKAGE = os.environ.get("LOGDATE_NATIVE_ACCEPTANCE_PACKAGE", "studio.hypertext.logdate.debug")
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
TEST = "app.logdate.client.e2e.NativeMissingMediaE2ETest"
PERMISSIONS = ["READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO", "POST_NOTIFICATIONS"]


def adb(serial, *args, timeout=180, check=True):
    if not re.fullmatch(r"emulator-[0-9]+", serial):
        raise ValueError("An explicit emulator-NNNN serial is required")
    return subprocess.run(["adb", "-s", serial, *map(str, args)], capture_output=True, timeout=timeout, check=check)


def safe_target(serial):
    if adb(serial, "shell", "getprop", "ro.kernel.qemu").stdout.strip() != b"1":
        raise RuntimeError("Target did not identify itself as an emulator")
    return adb(serial, "emu", "avd", "name").stdout.decode().splitlines()[0]


def set_metered(serial, network, mode):
    # AOSP applies this command but returns -1. Verify the actual configuration.
    result = adb(serial, "shell", "cmd", "netpolicy", "set", "metered-network", network, mode, check=False)
    rows = adb(serial, "shell", "cmd", "netpolicy", "list", "wifi-networks").stdout.decode().splitlines()
    actual = [line.rsplit(";", 1)[1] for line in rows if line.rsplit(";", 1)[0] == network]
    expected = "none" if mode == "undefined" else mode
    if result.returncode not in [0, 255] or not actual or any(value != expected for value in actual):
        raise RuntimeError("Metered-network configuration did not match the requested state")


def instrument(serial, selector, log, extras=(), release_network=None, status_actions=None, timeout=120):
    safe_target(serial)
    command = shlex.join(["am", "instrument", "-w", "-r", "-e", "class", selector, *extras, RUNNER])
    process = subprocess.Popen(["adb", "-s", serial, "shell", command], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    timer = threading.Timer(timeout, process.kill)
    timer.start()
    output = bytearray()
    released = False
    pending_actions = dict(status_actions or {})
    try:
        log.write_bytes(b"")
        log.chmod(0o600)
        for line in process.stdout:
            output.extend(line)
            with log.open("ab") as transcript:
                transcript.write(line)
            for marker in list(pending_actions):
                if marker in line:
                    pending_actions.pop(marker)()
            if release_network and b"RESTORE_NETWORK_DEFERRED" in line:
                release_network()
                released = True
        code = process.wait()
    except BaseException:
        safe_target(serial)
        adb(serial, "shell", "am", "force-stop", PACKAGE)
        adb(serial, "shell", "am", "force-stop", PACKAGE + ".test")
        raise
    finally:
        timer.cancel()
        if process.poll() is None:
            process.kill()
        log.write_bytes(output)
        log.chmod(0o600)
    if code != 0 or not instrumentation_passed(output) or release_network and not released or pending_actions:
        safe_target(serial)
        adb(serial, "shell", "am", "force-stop", PACKAGE)
        adb(serial, "shell", "am", "force-stop", PACKAGE + ".test")
        raise RuntimeError(f"Emulator acceptance failed; see {log}")


def restore_environment(serial, network, original, saver, airplane, wifi, grants):
    errors = []
    def attempt(label, operation):
        try:
            operation()
        except Exception as error:
            errors.append(f"{label}: {type(error).__name__}")
    safe_target(serial)
    steps = []
    if original is not None:
        steps.append(("metered override", lambda: set_metered(serial, network, "undefined" if original == "none" else original)))
    steps.extend([
        ("Data Saver", lambda: adb(serial, "shell", "cmd", "netpolicy", "set", "restrict-background", str(saver).lower())),
        ("airplane mode", lambda: adb(serial, "shell", "cmd", "connectivity", "airplane-mode", "enable" if airplane else "disable")),
        ("Wi-Fi", lambda: adb(serial, "shell", "svc", "wifi", "disable" if wifi == b"0" else "enable")),
    ])
    for label, operation in steps:
        attempt(label, operation)
    def restore_grant(permission, granted):
        result = adb(serial, "shell", "pm", "grant" if granted else "revoke", PACKAGE,
                     "android.permission." + permission, check=False)
        if result.returncode and (granted or b"has not requested permission" not in result.stderr + result.stdout):
            raise RuntimeError("Permission grant was not restored")
    for permission, granted in grants.items():
        attempt(permission, lambda permission=permission, granted=granted: restore_grant(permission, granted))
    def verify():
        for _ in range(20):
            actual_airplane = adb(serial, "shell", "settings", "get", "global", "airplane_mode_on").stdout.strip() == b"1"
            actual_wifi = adb(serial, "shell", "settings", "get", "global", "wifi_on").stdout.strip() != b"0"
            if actual_airplane == airplane and actual_wifi == (wifi != b"0"):
                break
            time.sleep(0.25)
        else:
            raise RuntimeError("Radio settings were not restored")
        actual_saver = b"enabled" in adb(serial, "shell", "cmd", "netpolicy", "get", "restrict-background").stdout
        package = adb(serial, "shell", "dumpsys", "package", PACKAGE).stdout.decode()
        actual_grants = {permission: bool(re.search(r"android\.permission\." + permission + r": granted=true", package)) for permission in grants}
        if actual_saver != saver or actual_grants != grants:
            raise RuntimeError("Data Saver or permission grants were not restored")
    attempt("final state readback", verify)
    if errors:
        raise RuntimeError("Emulator environment restoration failed: " + "; ".join(errors))


def run(args, serial):
    avd = safe_target(serial)
    directory = args.output / serial
    directory.mkdir(mode=0o700)
    for apk in [args.app_apk, args.test_apk]:
        safe_target(serial)
        adb(serial, "install", "-r", "-t", apk)
    package = adb(serial, "shell", "dumpsys", "package", PACKAGE).stdout.decode()
    grants = {permission: bool(re.search(r"android\.permission\." + permission + r": granted=true", package)) for permission in PERMISSIONS}
    networks = adb(serial, "shell", "cmd", "netpolicy", "list", "wifi-networks").stdout.decode().splitlines()
    original = next((line.rsplit(";", 1)[1] for line in networks if line.rsplit(";", 1)[0] == args.wifi_network), None)
    if original is None:
        raise RuntimeError("The selected Wi-Fi network is not configured on this emulator")
    original_saver = b"enabled" in adb(serial, "shell", "cmd", "netpolicy", "get", "restrict-background").stdout
    airplane = adb(serial, "shell", "settings", "get", "global", "airplane_mode_on").stdout.strip() == b"1"
    wifi = adb(serial, "shell", "settings", "get", "global", "wifi_on").stdout.strip()
    if wifi not in [b"0", b"1", b"2"]:
        raise RuntimeError("Could not determine the original Wi-Fi state")
    (directory / "environment-before.json").write_text(json.dumps({"grants": grants, "meteredOverride": original,
        "dataSaver": original_saver, "airplane": airplane, "wifi": wifi.decode()}, indent=2))
    try:
        adb(serial, "shell", "cmd", "connectivity", "airplane-mode", "disable")
        adb(serial, "shell", "svc", "wifi", "enable")
        for permission in PERMISSIONS:
            result = adb(serial, "shell", "pm", "revoke", PACKAGE, "android.permission." + permission, check=False)
            if result.returncode and b"has not requested permission" not in result.stderr + result.stdout:
                raise RuntimeError("Could not revoke a fixture permission")
        for method in ["aSeedProviderCollisionForColdRestore", "bQuarantinedOriginalSurvivesProcessRestart", "cPrivateOriginalWorksWithoutLibraryOrNotificationPermission"]:
            if method == "bQuarantinedOriginalSurvivesProcessRestart":
                safe_target(serial)
                adb(serial, "shell", "am", "force-stop", PACKAGE)
            instrument(serial, TEST + "#" + method, directory / (method + ".log"))
        for saver in [False, True]:
            adb(serial, "shell", "cmd", "netpolicy", "set", "restrict-background", str(saver).lower())
            set_metered(serial, args.wifi_network, "true")
            def release_network():
                adb(serial, "shell", "cmd", "netpolicy", "set", "restrict-background", "false")
                set_metered(serial, args.wifi_network, "false")
            instrument(serial, TEST + "#dMeteredRecoveryResumesWhenPlatformAllowsMedia",
                       directory / ("data-saver-recovery.log" if saver else "metered-recovery.log"),
                       extras=["-e", "restoreNetworkPolicyExpected", "restricted" if saver else "metered"],
                       release_network=release_network)
        label = "permissions-" + uuid.uuid4().hex
        for mode in ["seed", "automatic", "verify"]:
            instrument(serial, "app.logdate.client.e2e.ArchiveMediaRecoveryE2ETest#archiveRecoveryAcrossProcessRestart",
                       directory / ("archive-" + mode + ".log"),
                       extras=["-e", "archiveRecoveryFixtureLabel", label, "-e", "archiveRecoveryMode", mode])
            safe_target(serial)
            adb(serial, "shell", "am", "force-stop", PACKAGE)
    finally:
        restore_environment(serial, args.wifi_network, original, original_saver, airplane, wifi, grants)
    return {"serial": serial, "avd": avd, "tests": 8, "originalNetworkOverride": original}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emulator", action="append", required=True)
    parser.add_argument("--app-apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--wifi-network", default="AndroidWifi")
    parser.add_argument("--synthetic-data", action="store_true", required=True, help="Confirm these emulators contain only synthetic LogDate data")
    args = parser.parse_args()
    os.umask(0o077)
    args.output.mkdir(mode=0o700, parents=True)
    results = [run(args, serial) for serial in args.emulator]
    (args.output / "acceptance.json").write_text(json.dumps(results, indent=2) + "\n")
    print(json.dumps(results))


if __name__ == "__main__":
    main()
