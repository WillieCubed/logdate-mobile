#!/usr/bin/env python3
"""Exercise registered widget remapping and taps after duplicate native journal delivery."""

import argparse
import json
import os
from pathlib import Path
import runpy
import shlex

NATIVE = runpy.run_path(str(Path(__file__).with_name("native-backup-acceptance.py")))
add_failure_note = NATIVE["add_failure_note"]


def probe(serial, stage, output, old_id=None):
    command = ["am", "instrument", "-w", "-r", "-e", "class", "app.logdate.client.e2e.NativeRestoredWidgetProbeTest",
               "-e", "nativeWidgetStage", stage]
    if old_id is not None:
        command += ["-e", "nativeOldWidgetId", str(old_id)]
    raw = NATIVE["adb"](serial, "shell", shlex.join(command + [NATIVE["RUNNER"]]))
    NATIVE["private_file"](output, raw)
    if not NATIVE["instrumentation_passed"](raw) or stage == "restore" and b"NATIVE_WIDGET_SYSTEM_RESTORED_AND_TAPPED" not in raw:
        failure = RuntimeError(f"Registered widget {stage} did not pass; inspect {output}")
        try:
            diagnostic = NATIVE["adb"](serial, "logcat", "-b", "all", "-d", "-v", "threadtime", "-s",
                                      "GlanceAppWidgetReceiver:V", "AppWidgetServiceImpl:V", "BackupRestoreController:V",
                                      "BackupManagerService:V", "ActivityManager:V", "BroadcastQueue:V", "AndroidRuntime:E", "Napier:V")
            NATIVE["private_file"](output.with_suffix(".platform.log"), diagnostic)
            # Cleanup runs the same instrumentation method and AGP can replace its
            # per-method logcat. Retain the failed attempt before cleanup starts.
            diagnostic = NATIVE["adb"](serial, "logcat", "-b", "all", "-d", "-v", "threadtime")
            NATIVE["private_file"](output.with_suffix(".full-platform.log"), diagnostic)
            for service in ("appwidget", "activity broadcasts", "jobscheduler", "package " + NATIVE["PACKAGE"]):
                diagnostic = NATIVE["adb"](serial, "shell", "dumpsys", *service.split())
                NATIVE["private_file"](output.with_suffix("." + service.split()[0] + ".txt"), diagnostic)
        except Exception as error:
            add_failure_note(failure, f"Could not retain widget platform diagnostics: {type(error).__name__}")
        for name in (f"native-widget-{stage}.png", "native-widget-failure.png", "native-widget-failure.xml"):
            try:
                saved = NATIVE["adb"](serial, "exec-out", "run-as", NATIVE["PACKAGE"], "cat", "no_backup/" + name)
                if name.endswith(".png") and not saved.startswith(b"\x89PNG\r\n\x1a\n"):
                    raise ValueError("Widget failure screenshot is unavailable")
                if name.endswith(".xml") and not saved.lstrip().startswith(b"<?xml"):
                    raise ValueError("Widget failure hierarchy is unavailable")
                NATIVE["private_file"](output.parent / f"{serial}-{name}", saved)
            except Exception as error:
                add_failure_note(failure, f"Could not retain {name}: {type(error).__name__}")
        raise failure


def collect_screenshots(serial, stage, output):
    for suffix in ("", "-detail"):
        name = f"native-widget-{stage}{suffix}.png"
        raw = NATIVE["adb"](serial, "exec-out", "run-as", NATIVE["PACKAGE"], "cat", "no_backup/" + name)
        if not raw.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("Widget acceptance must retain an actual rendered screenshot")
        NATIVE["private_file"](output / f"{serial}-{name}", raw)


def run(options):
    NATIVE["prepare"](options.source, allow_preinstalled_empty=options.seed_empty_source)
    NATIVE["prepare"](options.destination, allow_preinstalled_empty=True)
    NATIVE["prepare_source"](options.source, options.app_apk, options.test_apk, options.output, "phrase")
    primary = None
    try:
        probe(options.source, "seed", options.output / "source-widget-seed.log")
        collect_screenshots(options.source, "seed", options.output)
        raw = NATIVE["adb"](options.source, "exec-out", "run-as", NATIVE["PACKAGE"], "cat", "no_backup/native-widget-restore.json")
        old_id = json.loads(raw)["widgetId"]
        if type(old_id) is not int or old_id <= 0:
            raise RuntimeError("The source must have an actual registered widget ID")
        NATIVE["private_file"](options.output / "source-widget.json", raw)
        NATIVE["transfer"](options.source, options.destination, options.app_apk, options.test_apk,
                           options.output, "phrase", seed_source=False, exercise_data_only=True,
                           exercise_duplicate_restore=True, exercise_system_widgets=True)
        probe(options.destination, "restore", options.output / "destination-widget-restore.log", old_id)
        collect_screenshots(options.destination, "platform", options.output)
        NATIVE["adb"](options.destination, "shell", "am", "force-stop", NATIVE["PACKAGE"])
        probe(options.destination, "restart", options.output / "destination-widget-restart.log")
        collect_screenshots(options.destination, "restart", options.output)
    except BaseException as error:
        primary = error
        raise
    finally:
        failures = []
        for serial, label in ((options.source, "source"), (options.destination, "destination")):
            try:
                probe(serial, "cleanup", options.output / f"{label}-widget-cleanup.log")
            except Exception as error:
                failures.append(type(error).__name__)
        if failures:
            message = "Registered widget fixture cleanup failed: " + ", ".join(failures)
            if primary is None:
                raise RuntimeError(message)
            add_failure_note(primary, message)
    NATIVE["private_file"](options.output / "widget-acceptance.json", json.dumps({
        "nativeTransfer": True, "registeredSourceWidget": True, "registeredReplacementWidget": True,
        "systemRestoresRegisteredWidgetWithoutInjectedCallbacks": True,
        "realSystemRestoredWidgetTapOpensChosenEntry": True,
        "distinctRemappedWidgetId": True, "duplicateNativeRestoreDeliveryRetainsWidget": True,
        "productionRemoteViewsRetainChosenEntry": True, "realWidgetTapOpensChosenEntry": True,
        "coldRestartRetainsWidgetAndTap": True, "launcherSetupTransferAccepted": False,
        "googleProviderAccepted": False,
    }, indent=2).encode())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=NATIVE["emulator_serial"])
    parser.add_argument("--destination", required=True, type=NATIVE["emulator_serial"])
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--seed-empty-source", action="store_true")
    options = parser.parse_args()
    if options.source == options.destination or any(not path.is_file() for path in (options.app_apk, options.test_apk)):
        parser.error("Distinct managed targets and both existing APKs are required")
    os.umask(0o077)
    options.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    run(options)


if __name__ == "__main__":
    main()
