#!/usr/bin/env python3
"""Retain key, editing, privacy and callback oracles inside an owned managed transfer."""

import argparse
import json
from pathlib import Path
import runpy
import shlex
import uuid

NATIVE = runpy.run_path(str(Path(__file__).with_name("native-backup-acceptance.py")))
add_failure_note = NATIVE["add_failure_note"]
PREFIX = "app.logdate.client.e2e."
LOCK_EVIDENCE_STAGES = (
    "native-enrollment",
    "enrollment-canceled",
    "initial-pin-challenge",
    "foreground-pin-challenge",
    "recreated-pin-challenge",
    "initial-unlocked",
    "foreground-unlocked",
    "recreated-unlocked",
)


def instrument(serial, classname, count, output, extras=()):
    arguments = [
        "am",
        "instrument",
        "-w",
        "-r",
        "-e",
        "class",
        classname,
        *extras,
        NATIVE["RUNNER"],
    ]
    result = NATIVE["adb"](serial, "shell", shlex.join(arguments))
    NATIVE["private_file"](output, result)
    if not NATIVE["instrumentation_passed"](result, count):
        raise RuntimeError(
            f"Post-transfer {classname.rsplit('.', 1)[-1]} did not pass every required case; inspect {output}"
        )


def retain_lock_evidence(serial, output):
    remote = f"/sdcard/Android/data/{NATIVE['PACKAGE']}/cache/restored-app-lock"
    NATIVE["adb"](serial, "pull", remote, str(output))
    if not output.is_dir():
        raise RuntimeError("Native lock evidence was not retained")
    for path in (output, *output.rglob("*")):
        if path.is_symlink():
            raise RuntimeError("Native lock evidence contains an unexpected link")
        path.chmod(0o700 if path.is_dir() else 0o600)
    for stage in LOCK_EVIDENCE_STAGES:
        for name in (f"{stage}.png", f"{stage}.xml", f"{stage}-state.txt"):
            path = output / name
            if not path.is_file() or path.stat().st_size == 0:
                raise RuntimeError(f"Missing native lock evidence: {name}")


def instrument_journal_lock(serial, output):
    failure = None
    try:
        instrument(
            serial,
            PREFIX
            + "RestoredJournalLockRuntimeTest,"
            + PREFIX
            + "RestoredAppLockEnrollmentRuntimeTest",
            8,
            output / "journal-lock.log",
        )
    except Exception as error:
        failure = error
        raise
    finally:
        try:
            retain_lock_evidence(serial, output / "restored-app-lock")
        except Exception as capture_error:
            if failure is None:
                raise
            add_failure_note(
                failure, f"Native lock evidence capture failed: {capture_error}"
            )


def run(serial, output):
    if NATIVE["adb"](serial, "shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("Post-transfer acceptance requires an emulator")
    output.mkdir(mode=0o700, parents=True, exist_ok=False)
    instrument(
        serial,
        PREFIX + "NativeSourceSessionOwnershipTest",
        3,
        output / "source-session-ownership.log",
    )
    instrument(
        serial,
        PREFIX + "NativeRetainedKeyFailureTest",
        8,
        output / "retained-keys.log",
        ("-e", "retainedKeyFailures", "true"),
    )
    namespace = str(uuid.uuid4())
    for stage in ("seed", "verify"):
        instrument(
            serial,
            PREFIX + "NativeKeyPersistenceRestartProbeTest",
            1,
            output / f"key-process-{stage}.log",
            (
                "-e",
                "retainedKeyFailures",
                "true",
                "-e",
                "keyRestartStage",
                stage,
                "-e",
                "keyRestartId",
                namespace,
            ),
        )
        if stage == "seed":
            NATIVE["adb"](serial, "shell", "am", "force-stop", NATIVE["PACKAGE"])
    instrument(
        serial,
        PREFIX + "NativeRestoredEntryEditingTest",
        8,
        output / "entry-editing.log",
        ("-e", "restoredEntryEditing", "true"),
    )
    instrument_journal_lock(serial, output)
    callback_contract = json.loads(
        Path(__file__).with_name("native-modern-callback-cases.json").read_text()
    )
    callback_count = sum(len(methods) for methods in callback_contract.values())
    instrument(
        serial, ",".join(callback_contract), callback_count, output / "callbacks.log"
    )
    NATIVE["private_file"](
        output / "acceptance.json",
        json.dumps(
            {
                "retainedKeyCases": 8,
                "pendingKeyAcrossProcesses": True,
                "editingCases": 8,
                "privacyCases": 8,
                "callbackCases": callback_count,
                "modernCallbackAccepted": False,
                "googleProviderAccepted": False,
            },
            indent=2,
        ).encode(),
    )


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--emulator", required=True, type=NATIVE["emulator_serial"])
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--synthetic-data", action="store_true", required=True)
    options = parser.parse_args()
    run(options.emulator, options.output)
