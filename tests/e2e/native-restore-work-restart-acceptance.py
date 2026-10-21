#!/usr/bin/env python3
"""Verify the production unique Cloud Restore request survives an Android app process restart."""

import argparse
import os
from pathlib import Path
import re
import runpy
import subprocess
import time


# Use the adjacent helper for both standalone and importlib-loaded entry points.
instrumentation_passed = runpy.run_path(
    str(Path(__file__).with_name("native-instrumentation-results.py"))
)["instrumentation_passed"]


PACKAGE = os.environ.get("LOGDATE_NATIVE_ACCEPTANCE_PACKAGE", "studio.hypertext.logdate.transfer.debug")
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
PROBE = "app.logdate.client.e2e.CloudRestoreWorkPersistenceProbeTest"


def emulator_serial(value):
    if not re.fullmatch(r"emulator-[0-9]+", value):
        raise argparse.ArgumentTypeError("an explicit emulator-NNNN serial is required")
    return value


def adb(serial, *arguments, timeout=180):
    emulator_serial(serial)
    return subprocess.run(
        ["adb", "-s", serial, *arguments],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=True,
    ).stdout


def probe(serial, mode, output):
    result = adb(
        serial,
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        PROBE,
        "-e",
        "cloudRestoreWorkMode",
        mode,
        RUNNER,
    )
    output.write_bytes(result)
    output.chmod(0o600)
    if not instrumentation_passed(result):
        raise RuntimeError(f"Cloud restore work {mode} probe failed; inspect {output}")


def radio_state(serial):
    airplane = adb(serial, "shell", "settings", "get", "global", "airplane_mode_on").strip() == b"1"
    wifi = adb(serial, "shell", "settings", "get", "global", "wifi_on").strip()
    if wifi not in (b"0", b"1", b"2"):
        raise RuntimeError("Android Wi-Fi state could not be read")
    return airplane, wifi


def set_offline(serial):
    adb(serial, "shell", "cmd", "connectivity", "airplane-mode", "enable")
    adb(serial, "shell", "svc", "wifi", "disable")


def restore_radio_state(serial, original):
    airplane, wifi = original
    adb(serial, "shell", "cmd", "connectivity", "airplane-mode", "enable" if airplane else "disable")
    adb(serial, "shell", "svc", "wifi", "disable" if wifi == b"0" else "enable")
    for _ in range(20):
        observed = radio_state(serial)
        if observed == original:
            return
        time.sleep(0.25)
    raise RuntimeError(f"Could not restore the emulator's original network state: expected {original!r}, observed {observed!r}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emulator", required=True, type=emulator_serial)
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    options = parser.parse_args()
    if not options.app_apk.is_file() or not options.test_apk.is_file():
        parser.error("both APKs must exist")
    options.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    if adb(options.emulator, "shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("target did not identify itself as an emulator")
    packages = adb(options.emulator, "shell", "pm", "list", "packages", PACKAGE).decode().splitlines()
    installed = "package:" + PACKAGE in packages
    if installed:
        raise RuntimeError("use a fresh isolated application install; the harness never clears app data")

    adb(options.emulator, "install", "-t", str(options.app_apk.resolve()))
    adb(options.emulator, "install", "-t", str(options.test_apk.resolve()))
    original_radio = radio_state(options.emulator)
    try:
        set_offline(options.emulator)
        probe(options.emulator, "enqueue", options.output / "enqueue.log")
        adb(options.emulator, "shell", "am", "force-stop", PACKAGE)
        probe(options.emulator, "verify", options.output / "after-process-restart.log")
    finally:
        try:
            probe(options.emulator, "cleanup", options.output / "cleanup.log")
        finally:
            restore_radio_state(options.emulator, original_radio)
    print("Unique Cloud Restore work survived process death and duplicate enqueue", flush=True)


if __name__ == "__main__":
    main()
