#!/usr/bin/env python3
"""Verify external location/motion grants on the current task's ARM64 Gradle Managed Device."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import shutil
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("managed_target", Path(__file__).with_name("managed-talkback-acceptance.py"))
managed = importlib.util.module_from_spec(spec)
spec.loader.exec_module(managed)
CLASS = "app.logdate.client.e2e.NativeRestoredSettingsE2ETest"
METHOD = "locationResumesAfterExternalGrants"


def permissions_for_receipt(state, nonce):
    if state.get("nonce") != nonce:
        raise RuntimeError("Permission receipt belongs to another managed run")
    return {
        "RESTORE_LOCATION_GRANT_REQUIRED": ["ACCESS_COARSE_LOCATION", "ACCESS_FINE_LOCATION"],
        "RESTORE_MOTION_GRANT_REQUIRED": ["ACTIVITY_RECOGNITION"],
    }.get(state.get("phase"), [])


def run(output, sdk):
    output.mkdir()
    nonce = str(uuid.uuid4())
    adb = str(sdk / "platform-tools/adb")
    receipt = f"no_backup/permission-acceptance-{nonce}.json"
    env = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
               GRADLE_USER_HOME=str(Path.home() / ".gradle"), JAVA_TOOL_OPTIONS="-Djava.awt.headless=true")
    existing = {int(line.split(None, 1)[0]) for line in managed.command("ps", "-axo", "pid,args").stdout.decode().splitlines()
                if "qemu-system" in line and line.strip().split(None, 1)[0].isdigit()}
    args = ["./gradlew", ":app:android-main:phoneApi34DebugAndroidTest",
            f"-Plogdate.androidTestClass={CLASS}#{METHOD}", "-Plogdate.androidTestOrchestrator=false",
            "-Pandroid.testInstrumentationRunnerArguments.restorePermissionAcceptance=true",
            f"-Pandroid.testInstrumentationRunnerArguments.managedPermissionNonce={nonce}", "--console=plain"]
    serial = None
    seen = []
    last_capture = 0
    with (output / "gradle.log").open("wb") as log:
        process = subprocess.Popen(args, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        handle = {"pid": process.pid, "nonce": nonce, "log": str(output / "gradle.log")}
        (output / "process.json").write_text(json.dumps(handle, indent=2))
        deadline = time.monotonic() + 480
        try:
            while process.poll() is None:
                if time.monotonic() > deadline:
                    raise RuntimeError("Observation deadline reached; inspect the retained process handle before continuing")
                candidates = managed.managed_processes(managed.command("ps", "-axo", "pid,args").stdout.decode().splitlines(), existing)
                if candidates:
                    listeners = managed.command("lsof", "-nP", "-a", "-p", str(candidates[0][0]),
                                                "-iTCP", "-sTCP:LISTEN", "-Fn", check=False).stdout.decode()
                    for port in managed.console_ports(listeners):
                        candidate = f"emulator-{port}"
                        raw = managed.command(adb, "-s", candidate, "exec-out", "run-as", managed.PACKAGE, "cat", receipt, check=False)
                        try:
                            state = json.loads(raw.stdout)
                        except ValueError:
                            continue
                        permissions_for_receipt(state, nonce)
                        if serial is None:
                            avd = managed.command(adb, "-s", candidate, "emu", "avd", "name").stdout.decode().splitlines()[0]
                            if avd != "dev34_google_apis_arm64-v8a_Pixel_9_Pro":
                                raise RuntimeError("Unexpected managed image")
                            if managed.command(adb, "-s", candidate, "shell", "getprop", "ro.kernel.qemu").stdout.strip() != b"1":
                                raise RuntimeError("Managed target is not an emulator")
                            serial = candidate
                        if candidate != serial:
                            raise RuntimeError("Acceptance nonce appeared on another device")
                        if state["phase"] not in seen:
                            seen.append(state["phase"])
                            (output / "phases.json").write_text(json.dumps(seen, indent=2))
                            for permission in permissions_for_receipt(state, nonce):
                                current = json.loads(managed.command(adb, "-s", serial, "exec-out", "run-as",
                                                                    managed.PACKAGE, "cat", receipt).stdout)
                                if permission not in permissions_for_receipt(current, nonce):
                                    raise RuntimeError("The fixture no longer requests this permission")
                                managed.command(adb, "-s", serial, "shell", "pm", "grant", managed.PACKAGE,
                                                "android.permission." + permission)
                        if time.monotonic() - last_capture >= 2:
                            (output / "logcat.txt").write_bytes(managed.command(adb, "-s", serial, "logcat", "-d", "-t", "5000").stdout)
                            last_capture = time.monotonic()
                time.sleep(.25)
            if process.returncode != 0:
                raise RuntimeError(f"Managed permission acceptance failed; see {output / 'gradle.log'}")
            results = ROOT / "app/android-main/build/outputs/androidTest-results/managedDevice/debug/phoneApi34"
            cases = []
            for report in results.glob("TEST-*.xml"):
                shutil.copy(report, output / report.name)
                cases.extend(ET.parse(report).getroot().iter("testcase"))
            if len(cases) != 1 or cases[0].get("classname") != CLASS or cases[0].get("name") != METHOD:
                raise RuntimeError("The expected permission recovery case did not execute exactly once")
            if any(cases[0].find(tag) is not None for tag in ("skipped", "failure", "error")):
                raise RuntimeError("Permission recovery did not pass")
            if not {"RESTORE_LOCATION_GRANT_REQUIRED", "RESTORE_MOTION_GRANT_REQUIRED"}.issubset(seen):
                raise RuntimeError("Both external grants must be exercised")
            (output / "acceptance.json").write_text(json.dumps({"locationAndMotionGrantRecovery": True,
                "normalStartup": True, "googleRestoreProviderAccepted": False, "phases": seen}, indent=2) + "\n")
        finally:
            if process.poll() is None:
                (output / "active-process.json").write_text(json.dumps(handle, indent=2))
                try:
                    process.wait(timeout=60)
                except subprocess.TimeoutExpired:
                    pass


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=Path.home() / "Library/Android/sdk")
    args = parser.parse_args()
    args.output = args.output.resolve()
    if args.output.exists():
        raise SystemExit("Use a fresh evidence directory")
    run(args.output, args.sdk)
