#!/usr/bin/env python3
"""Verify accepted archive recovery across Android low storage and app process death."""

import argparse
import json
import os
from pathlib import Path
import re
import runpy
import time
import uuid

shared = runpy.run_path(str(Path(__file__).with_name("native-missing-media-acceptance.py")))
adb = shared["adb"]
safe_target = shared["safe_target"]
instrument = shared["instrument"]
PACKAGE = shared["PACKAGE"]


def storage_state(serial):
    value = adb(serial, "shell", "dumpsys", "devicestoragemonitor").stdout.decode()
    forced = re.search(r"\bmForceState=(\w+)", value)
    level = re.search(r"\blevel=(\w+)", value)
    if forced is None or level is None:
        raise RuntimeError("Android did not expose its storage state")
    return {"forced": forced[1], "level": level[1]}


def set_storage(serial, command, expected):
    safe_target(serial)
    adb(serial, "shell", "cmd", "devicestoragemonitor", command, "-f")
    deadline = time.monotonic() + 10
    observed = storage_state(serial)
    while observed != expected:
        if time.monotonic() >= deadline:
            raise RuntimeError(f"Android storage command {command} did not settle: expected {expected}; observed {observed}")
        time.sleep(.1)
        observed = storage_state(serial)


def run(args):
    serial = args.emulator
    avd = safe_target(serial)
    for apk in [args.app_apk, args.test_apk]:
        safe_target(serial)
        adb(serial, "install", "-r", "-t", apk)
    original = storage_state(serial)
    if original != {"forced": "UNKNOWN", "level": "NORMAL"}:
        raise RuntimeError("Use an emulator with normal storage and no existing forced storage state")
    (args.output / "environment-before.json").write_text(json.dumps(original, indent=2) + "\n")
    label = "storage-" + uuid.uuid4().hex
    try:
        for mode in ["seed", "enqueue", "waiting", "resume"]:
            if mode == "enqueue":
                set_storage(serial, "force-low", {"forced": "LOW", "level": "LOW"})
            instrument(
                serial,
                "app.logdate.client.e2e.ArchiveLowStorageRecoveryTest#acceptedArchiveWaitsForStorageAcrossProcessDeath",
                args.output / (mode + ".log"),
                extras=["-e", "storageRecoveryMode", mode, "-e", "storageRecoveryLabel", label],
                status_actions={b"RESTORE_STORAGE_WAITING": lambda: set_storage(
                    serial, "force-not-low", {"forced": "NORMAL", "level": "NORMAL"}
                )} if mode == "resume" else None,
            )
            safe_target(serial)
            adb(serial, "shell", "am", "force-stop", PACKAGE)
    finally:
        set_storage(serial, "reset", original)
    receipt = {"serial": serial, "avd": avd, "acceptedSourceRetained": True,
               "waitedForPlatformStorage": True, "sameRequestSurvivedProcessDeath": True,
               "restoredAutomaticallyWhenStorageRecovered": True, "storageStateRestored": True}
    (args.output / "acceptance.json").write_text(json.dumps(receipt, indent=2) + "\n")
    print(json.dumps(receipt))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emulator", required=True)
    parser.add_argument("--app-apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--synthetic-data", action="store_true", required=True)
    args = parser.parse_args()
    os.umask(0o077)
    args.output.mkdir(mode=0o700, parents=True)
    run(args)


if __name__ == "__main__":
    main()
