#!/usr/bin/env python3
"""Verify the production unique Cloud Restore request survives an Android app process restart."""

import argparse
import json
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


NATIVE = runpy.run_path(str(Path(__file__).with_name("native-backup-acceptance.py")))
add_failure_note = NATIVE["add_failure_note"]


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
        NATIVE["managed_adb_command"](serial, arguments),
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        timeout=timeout,
        check=True,
    ).stdout


def probe(serial, mode, output):
    account_transitions = mode == "account-transitions"
    probe_class = "app.logdate.client.e2e.AndroidSyncAccountTransitionRuntimeTest" if account_transitions else PROBE
    argument = "syncAccountTransitions" if account_transitions else "cloudRestoreWorkMode"
    value = "true" if account_transitions else mode
    result = adb(
        serial,
        "shell",
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        probe_class,
        "-e",
        argument,
        value,
        RUNNER,
    )
    output.write_bytes(result)
    output.chmod(0o600)
    if not instrumentation_passed(result, 9 if account_transitions else 1):
        raise RuntimeError(f"Cloud restore work {mode} probe failed; inspect {output}")


def network_read_timeout(deadline):
    if deadline is None:
        return 180
    remaining = deadline - time.monotonic()
    if remaining <= 0:
        raise RuntimeError("Owned emulator offline admission deadline expired")
    return min(5, remaining)


def radio_state(serial, deadline=None):
    airplane = adb(serial, "shell", "settings", "get", "global", "airplane_mode_on",
                   timeout=network_read_timeout(deadline)).strip() == b"1"
    wifi = adb(serial, "shell", "settings", "get", "global", "wifi_on",
               timeout=network_read_timeout(deadline)).strip()
    if wifi not in (b"0", b"1", b"2"):
        raise RuntimeError("Android Wi-Fi state could not be read")
    return airplane, wifi


def set_offline(serial, output=None):
    deadline = time.monotonic() + 10
    connectivity, observed, default_network, failure = b"", None, None, None
    try:
        adb(serial, "shell", "cmd", "connectivity", "airplane-mode", "enable",
            timeout=network_read_timeout(deadline))
        adb(serial, "shell", "svc", "wifi", "disable", timeout=network_read_timeout(deadline))
        while True:
            connectivity = adb(serial, "shell", "dumpsys", "connectivity", timeout=network_read_timeout(deadline))
            defaults = [line.strip() for line in connectivity.splitlines()
                        if line.lstrip().startswith(b"Active default network:")]
            match = re.fullmatch(rb"Active default network:\s*(none|[0-9]+)", defaults[0]) if len(defaults) == 1 else None
            if match is None:
                raise RuntimeError("Android default network readback was missing or ambiguous")
            default_network = match.group(1).decode()
            observed = radio_state(serial, deadline=deadline)
            remaining = network_read_timeout(deadline)
            if observed == (True, b"0") and default_network == "none":
                return
            time.sleep(min(0.1, remaining))
    except BaseException as error:
        failure = error
        raise
    finally:
        if output is not None:
            try:
                NATIVE["private_file"](output / "offline-connectivity.log", connectivity[:131072])
                NATIVE["private_file"](output / "offline-radio.json", json.dumps({
                    "airplane": observed[0] if observed else None,
                    "wifi": observed[1].decode() if observed else None,
                    "defaultNetwork": default_network, "admitted": failure is None,
                    "failure": type(failure).__name__ if failure else None,
                }).encode())
            except Exception as diagnostic_error:
                if failure is None:
                    raise
                add_failure_note(failure, f"Offline diagnostics could not be retained: {type(diagnostic_error).__name__}")


def restore_radio_state(serial, original):
    airplane, wifi = original
    failures = []
    for label, arguments in (
        ("airplane", ("shell", "cmd", "connectivity", "airplane-mode", "enable" if airplane else "disable")),
        ("Wi-Fi", ("shell", "svc", "wifi", "disable" if wifi == b"0" else "enable")),
    ):
        try:
            adb(serial, *arguments, timeout=5)
        except Exception:
            failures.append(label)
    deadline = time.monotonic() + 5
    observed = None
    for _ in range(20):
        if time.monotonic() >= deadline:
            break
        try:
            observed = radio_state(serial, deadline=deadline)
            if observed == original:
                return
        except Exception:
            failures.append("readback")
        time.sleep(min(0.25, max(0, deadline - time.monotonic())))
    raise RuntimeError(f"Could not restore the emulator's original network state: expected {original!r}, observed {observed!r}; "
                       f"{', '.join(dict.fromkeys(failures)) or 'state mismatch'}")


def require_empty_install(serial, allow_preinstalled_empty=False):
    packages = adb(serial, "shell", "pm", "list", "packages", PACKAGE).decode().splitlines()
    if "package:" + PACKAGE not in packages:
        return
    if not allow_preinstalled_empty:
        raise RuntimeError("use a fresh isolated application install; the harness never clears app data")
    if NATIVE["contains_user_content"](NATIVE["package_user_files"](serial, package=PACKAGE)):
        raise RuntimeError("The installed managed source contains retained data")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emulator", required=True, type=emulator_serial)
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seed-empty-source", action="store_true")
    options = parser.parse_args()
    if not options.app_apk.is_file() or not options.test_apk.is_file():
        parser.error("both APKs must exist")
    options.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    if adb(options.emulator, "shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("target did not identify itself as an emulator")
    require_empty_install(options.emulator, options.seed_empty_source)

    adb(options.emulator, "install", "-r", "-t", str(options.app_apk.resolve()))
    adb(options.emulator, "install", "-r", "-t", str(options.test_apk.resolve()))
    original_radio = radio_state(options.emulator)
    try:
        set_offline(options.emulator, options.output)
        probe(options.emulator, "account-transitions", options.output / "account-transitions.log")
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
