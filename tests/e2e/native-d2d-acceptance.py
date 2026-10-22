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
import subprocess
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
        match = re.match(r"\s*((?:0x)?[0-9a-fA-F]+)\s*:\s*(.+)", line)
        if match and re.match(r"D2D\b", match.group(2), re.IGNORECASE):
            return match.group(1)
    raise RuntimeError("Google D2D test transport did not expose a D2D restore set")


def selected_transport(serial):
    transports = HARNESS.adb(serial, "shell", "bmgr", "list", "transports").decode().splitlines()
    return next((line.lstrip()[2:].strip() for line in transports if line.lstrip().startswith("* ")), None)


def source_fixture(serial):
    return HARNESS.adb(serial, "exec-out", "run-as", HARNESS.PACKAGE, "cat", "no_backup/native-backup-fixture.json")


def require_empty_source(serial):
    packages = HARNESS.adb(serial, "shell", "pm", "list", "packages", HARNESS.PACKAGE).decode().splitlines()
    if "package:" + HARNESS.PACKAGE in packages and HARNESS.contains_user_content(HARNESS.package_user_files(serial)):
        raise RuntimeError("Use a fresh emulator for D2D acceptance; the installed app contains retained user data")


def select_d2d_restore_transport(serial):
    HARNESS.adb(serial, "shell", "bmgr", "transport", GMS_BACKUP_TRANSPORT, timeout=60)
    if selected_transport(serial) != GMS_BACKUP_TRANSPORT:
        raise RuntimeError("Google backup transport was not selected for D2D test-mode restore")


def select_d2d_transport(serial, attempts=30, output=None):
    registered = None
    selection = None
    for attempt in range(attempts):
        try:
            registered = HARNESS.adb(serial, "shell", "bmgr", "list", "transports", timeout=60).decode().splitlines()
            if any(line.lstrip("* ").strip() == D2D_TRANSPORT for line in registered):
                selection = HARNESS.adb(serial, "shell", "bmgr", "transport", D2D_TRANSPORT, timeout=60)
                initialized = HARNESS.adb(serial, "shell", "bmgr", "init", D2D_TRANSPORT, timeout=60)
                if output is not None:
                    HARNESS.private_file(output / f"d2d-init-{attempt + 1}.log", initialized)
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


def capture_transport_failure(serial, output):
    errors = []
    commands = {
        "d2d-backup-state.log": ("shell", "dumpsys", "backup"),
        "d2d-logcat.log": ("logcat", "-d", "-b", "all", "-t", "4000"),
    }
    for name, command in commands.items():
        try:
            HARNESS.private_file(output / name, HARNESS.adb(serial, *command, timeout=30))
        except Exception as error:
            errors.append(f"{name}: {error}")
    if errors:
        try:
            HARNESS.private_file(output / "d2d-diagnostic-errors.log", "\n".join(errors).encode())
        except Exception:
            print("D2D diagnostic output could not be retained", file=sys.stderr)


def backup_d2d_source(serial, output):
    # Match Android Studio's backup tooling: a newly initialized transport needs
    # a full package-manager metadata snapshot, not an incremental baseline.
    backup = HARNESS.adb(
        serial, "shell", "bmgr", "backupnow", "@pm@", HARNESS.PACKAGE,
        "--non-incremental", "--monitor-verbose", timeout=180,
    )
    HARNESS.private_file(output / "d2d-backup.log", backup)
    required = [
        b"Package @pm@ with result: Success",
        f"Package {HARNESS.PACKAGE} with result: Success".encode(),
        b"Backup finished with result: Success",
    ]
    if not all(result in backup for result in required):
        retain_transport_failure(serial, output, "backup")
        raise RuntimeError("Google D2D test transport did not complete the native backup")


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


def retain_transport_failure(serial, output, phase):
    # The package-manager backup can fail before the application's agent is called.
    # Keep bounded transport evidence without replacing the failed acceptance result.
    try:
        evidence = HARNESS.adb(
            serial, "shell", "logcat", "-d", "-t", "500", "-v", "threadtime",
            "BackupManagerService:V", "Backup:V", "PFTBT:V", "*:S", timeout=30,
        )
    except (OSError, subprocess.SubprocessError) as error:
        evidence = f"Transport diagnostics unavailable: {type(error).__name__}\n".encode()
    HARNESS.private_file(output / f"d2d-{phase}-transport.log", evidence)


def exercise_d2d(serial, app_apk, test_apk, output):
    verify_emulator(serial)
    if not app_apk.is_file() or not test_apk.is_file():
        raise RuntimeError("Both debug and instrumentation APKs must exist")
    require_empty_source(serial)
    output.mkdir(mode=0o700, parents=True, exist_ok=False)
    HARNESS.ensure_emulator_setup_complete(serial)
    previous_transport = selected_transport(serial)
    previous_mode = HARNESS.adb(serial, "shell", "settings", "get", "secure", D2D_TEST_MODE).strip()
    backup_was_enabled = b"currently enabled" in HARNESS.adb(serial, "shell", "bmgr", "enabled").lower()
    try:
        HARNESS.adb(serial, "shell", "bmgr", "enable", "true")
        HARNESS.adb(serial, "shell", "settings", "put", "secure", D2D_TEST_MODE, "1")
        select_d2d_transport(serial, output=output)

        HARNESS.adb(serial, "install", "-r", "-t", str(app_apk.resolve()))
        HARNESS.adb(serial, "install", "-r", "-t", str(test_apk.resolve()))
        HARNESS.probe(serial, "seed", output / "source-seed.log")
        raw_fixture = source_fixture(serial)
        metadata = json.loads(raw_fixture)
        HARNESS.private_file(output / "source-fixture.json", raw_fixture)

        backup_d2d_source(serial, output)

        HARNESS.probe(serial, "post-backup-mutation", output / "post-backup-mutation.log")
        select_d2d_restore_transport(serial)
        sets = HARNESS.adb(serial, "shell", "bmgr", "list", "sets", timeout=60)
        HARNESS.private_file(output / "d2d-restore-sets.log", sets)
        token = restore_set_token(sets)
        HARNESS.adb(serial, "shell", "am", "force-stop", HARNESS.PACKAGE)
        restore = HARNESS.adb(serial, "shell", "bmgr", "restore", token, HARNESS.PACKAGE, timeout=180)
        HARNESS.private_file(output / "d2d-restore.log", restore)
        if b"restoreFinished: 0" not in restore:
            retain_transport_failure(serial, output, "restore")
            raise RuntimeError("Google D2D test transport did not complete native restore")

        HARNESS.adb(serial, "install", "-r", "-t", str(test_apk.resolve()))
        HARNESS.probe(serial, "native-restore-status", output / "d2d-callback-status.log")
        HARNESS.probe(serial, "verify-in-place", output / "d2d-restored-content.log", metadata)
        print("Google D2D test-mode backup, restore callback, and restored content passed", flush=True)
    except Exception:
        capture_transport_failure(serial, output)
        raise
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
