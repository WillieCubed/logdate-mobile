#!/usr/bin/env python3
"""Exercise Android's repeatable, single-device D2D test-mode simulation.

Android recommends a source and target device for comprehensive D2D acceptance, and documents
this single-device test-mode loop for repeatable development. This harness follows its transport
setup, then requests restore explicitly with `bmgr restore`: the guide's uninstall/reinstall step
would destroy LogDate data, which this repository forbids agents from clearing. Passing this test
checks the GMS D2D transport, callback, and restored content; it does not prove setup-wizard transfer.

Guide: https://developer.android.com/identity/data/testingbackup
"""

import argparse
import importlib.util
import json
from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location(
    "native_acceptance",
    ROOT / "tests/e2e/native-backup-acceptance.py",
)
HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HARNESS)
D2D_TRANSPORT = "com.google.android.gms/.backup.migrate.service.D2dTransport"
D2D_TEST_MODE = "backup_enable_d2d_test_mode"
GMS_BACKUP_TRANSPORT = "com.google.android.gms/.backup.BackupTransportService"


def verify_emulator(serial):
    if not serial.startswith("emulator-"):
        raise RuntimeError("D2D acceptance requires an explicit emulator serial")
    if HARNESS.adb(serial, "shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("D2D acceptance target did not identify itself as an emulator")


def restore_set_token(output):
    text = output.decode(errors="replace") if isinstance(output, bytes) else output
    for line in text.splitlines():
        match = re.match(r"\s*(0x[0-9a-fA-F]+|[0-9]+)\s*:", line)
        if match:
            return match.group(1)
    raise RuntimeError("Google D2D test transport did not expose a restore set")


def selected_transport(serial):
    transports = HARNESS.adb(serial, "shell", "bmgr", "list", "transports").decode().splitlines()
    return next((line[2:].strip() for line in transports if line.startswith("* ")), None)


def select_d2d_transport(serial, attempts=30):
    registered = None
    selection = None
    for attempt in range(attempts):
        try:
            registered = HARNESS.adb(serial, "shell", "bmgr", "list", "transports", timeout=60).decode().splitlines()
            if any(line.lstrip("* ").strip() == D2D_TRANSPORT for line in registered):
                selection = HARNESS.adb(serial, "shell", "bmgr", "transport", D2D_TRANSPORT, timeout=60)
                HARNESS.adb(serial, "shell", "bmgr", "init", D2D_TRANSPORT, timeout=60)
                if selected_transport(serial) == D2D_TRANSPORT:
                    return
        except Exception as error:
            if attempt + 1 == attempts:
                raise RuntimeError("Google D2D transport could not be selected and initialized") from error
        if attempt + 1 < attempts:
            HARNESS.time.sleep(1)
    raise RuntimeError(
        "Google D2D transport is not available or was not selected; "
        f"registered={registered!r}, selection={selection!r}"
    )


def restore_device_settings(serial, previous_transport, previous_mode, backup_was_enabled):
    errors = []
    try:
        if previous_mode in (b"", b"null"):
            HARNESS.adb(serial, "shell", "settings", "delete", "secure", D2D_TEST_MODE)
        else:
            HARNESS.adb(serial, "shell", "settings", "put", "secure", D2D_TEST_MODE, previous_mode.decode())
    except Exception as error:
        errors.append(f"D2D test-mode setting: {error}")
    try:
        HARNESS.adb(
            serial,
            "shell",
            "bmgr",
            "transport",
            previous_transport or GMS_BACKUP_TRANSPORT,
            timeout=60,
        )
    except Exception as error:
        errors.append(f"backup transport: {error}")
    try:
        if not backup_was_enabled:
            HARNESS.adb(serial, "shell", "bmgr", "enable", "false")
    except Exception as error:
        errors.append(f"backup enablement: {error}")
    if errors:
        raise RuntimeError("Could not restore the emulator's original backup settings: " + "; ".join(errors))


def exercise_d2d(serial, app_apk, test_apk, output):
    verify_emulator(serial)
    if not app_apk.is_file() or not test_apk.is_file():
        raise RuntimeError("Both debug and instrumentation APKs must exist")
    output.mkdir(mode=0o700, parents=True, exist_ok=False)
    HARNESS.ensure_emulator_setup_complete(serial)
    previous_transport = selected_transport(serial)
    previous_mode = HARNESS.adb(serial, "shell", "settings", "get", "secure", D2D_TEST_MODE).strip()
    backup_was_enabled = b"currently enabled" in HARNESS.adb(serial, "shell", "bmgr", "enabled").lower()
    try:
        HARNESS.adb(serial, "shell", "bmgr", "enable", "true")
        HARNESS.adb(serial, "shell", "settings", "put", "secure", D2D_TEST_MODE, "1")
        select_d2d_transport(serial)

        HARNESS.adb(serial, "install", "-r", "-t", str(app_apk.resolve()))
        HARNESS.adb(serial, "install", "-r", "-t", str(test_apk.resolve()))
        HARNESS.probe(serial, "seed", output / "source-seed.log")
        fixture_path = f"/data/user/0/{HARNESS.PACKAGE}/no_backup/native-backup-fixture.json"
        raw_fixture = HARNESS.adb(serial, "exec-out", "cat", fixture_path)
        metadata = json.loads(raw_fixture)
        HARNESS.private_file(output / "source-fixture.json", raw_fixture)

        backup = HARNESS.adb(serial, "shell", "bmgr", "backupnow", HARNESS.PACKAGE, timeout=180)
        HARNESS.private_file(output / "d2d-backup.log", backup)
        if f"Package {HARNESS.PACKAGE} with result: Success".encode() not in backup:
            raise RuntimeError("Google D2D test transport did not complete the native backup")

        HARNESS.probe(serial, "post-backup-mutation", output / "post-backup-mutation.log")
        sets = HARNESS.adb(serial, "shell", "bmgr", "list", "sets", timeout=60)
        HARNESS.private_file(output / "d2d-restore-sets.log", sets)
        token = restore_set_token(sets)
        HARNESS.adb(serial, "shell", "am", "force-stop", HARNESS.PACKAGE)
        restore = HARNESS.adb(serial, "shell", "bmgr", "restore", token, HARNESS.PACKAGE, timeout=180)
        HARNESS.private_file(output / "d2d-restore.log", restore)
        if b"restoreFinished: 0" not in restore:
            raise RuntimeError("Google D2D test transport did not complete native restore")

        HARNESS.adb(serial, "install", "-r", "-t", str(test_apk.resolve()))
        HARNESS.probe(serial, "native-restore-status", output / "d2d-callback-status.log")
        HARNESS.probe(serial, "verify", output / "d2d-restored-content.log", metadata)
        print("Google D2D test-mode backup, restore callback, and restored content passed", flush=True)
    finally:
        restore_device_settings(serial, previous_transport, previous_mode, backup_was_enabled)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emulator", required=True, type=HARNESS.emulator_serial)
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    options = parser.parse_args()
    exercise_d2d(options.emulator, options.app_apk, options.test_apk, options.output)


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"D2D acceptance failed: {error}", file=sys.stderr)
        raise SystemExit(1) from error
