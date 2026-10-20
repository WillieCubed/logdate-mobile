#!/usr/bin/env python3
"""Run native transport oracles while a separate GMD driver survives LogDate restarts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import runpy
import shlex
import shutil
import subprocess
import sys
import time
import uuid
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
TARGET = runpy.run_path(str(Path(__file__).with_name("managed-native-target.py")))
DRIVER = TARGET["DRIVER"]
DRIVER_CASE = "app.logdate.tools.restoredriver.NativeRestoreDriverTest"
FOLLOWUPS = {"missing-media": "native-missing-media-acceptance.py",
             "storage": "native-storage-recovery-acceptance.py", "ui": "native-recovery-ui-acceptance.py",
             "permissions": "native-permission-acceptance.py", "contracts": "native-post-transfer-acceptance.py"}


def acceptance_receipt(mode, native_arguments, followups):
    return {"managedDeviceLifecycleAccepted": True, "outerDrivers": 2, "mode": mode,
            "managedNativeTransport": mode != "work-restart",
            "crossDeviceTransferAccepted": mode in {"transfer", "publication", "enrollment"},
            "nativeArguments": native_arguments, "followups": followups,
            "googleRestoreProviderAccepted": False}


def acceptance_budget(followups):
    budget = {"boot": 600, "native": 1200, "followup": 600, "teardown": 120}
    budget["driverMillis"] = 1000 * (budget["boot"] + budget["native"] + followups * budget["followup"] + budget["teardown"])
    return budget


def followup_command(name, destination, app, probe, output):
    if name not in FOLLOWUPS:
        raise ValueError("Choose an existing native recovery acceptance harness")
    return [sys.executable, str(ROOT / "tests/e2e" / FOLLOWUPS[name]), "--emulator", destination,
            "--app-apk", str(app), "--test-apk", str(probe), "--output", str(output), "--synthetic-data"]


def scenario_command(mode, source, destination, app, probe, output, arguments, *, classpath=None, fixture_environment=None):
    validate_native_arguments(arguments)
    if mode not in {"transfer", "publication", "work-restart", "d2d", "enrollment"} or (mode != "transfer" and arguments):
        raise ValueError("Only transfer mode accepts native scenario arguments")
    filenames = {"transfer": "native-backup-acceptance.py", "publication": "native-publication-acceptance.py",
                 "work-restart": "native-restore-work-restart-acceptance.py", "d2d": "native-d2d-acceptance.py",
                 "enrollment": "native-device-enrollment-acceptance.py"}
    command = [sys.executable, str(ROOT / "tests/e2e" / filenames[mode])]
    if mode in {"work-restart", "d2d"}:
        command += ["--emulator", source]
    else:
        command += ["--source", source, "--destination", destination]
    command += ["--app-apk", str(app), "--test-apk", str(probe), "--output", str(output)]
    if mode != "d2d":
        command += ["--seed-empty-source"]
    if mode == "enrollment":
        if classpath is None or fixture_environment is None:
            raise ValueError("Enrollment requires an existing private database fixture and server classpath")
        command += ["--classpath", str(classpath), "--fixture-environment", str(fixture_environment)]
    return command + arguments


def managed_environment(sdk, inherited):
    return dict(inherited, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
                GRADLE_USER_HOME=str(Path.home() / ".gradle"), JAVA_TOOL_OPTIONS="-Djava.awt.headless=true",
                LOGDATE_NATIVE_ACCEPTANCE_PACKAGE="studio.hypertext.logdate.debug")


def pinned_install_arguments(app, probe):
    if any(not path.is_absolute() or not path.is_file() for path in (app, probe)):
        raise ValueError("Managed installation requires both existing absolute pinned APK paths")
    return [f"-Plogdate.nativeAcceptanceAppApk={app}", f"-Plogdate.nativeAcceptanceProbeApk={probe}"]


def validate_native_arguments(arguments):
    allowed = {"--exercise-quota", "--exercise-archive-recovery", "--exercise-missing-keys",
               "--exercise-missing-privacy", "--exercise-credentials-only", "--exercise-data-only",
               "--exercise-duplicate-restore", "--exercise-partial-media"}
    remaining = iter(arguments)
    for argument in remaining:
        if argument == "--identity":
            if next(remaining, None) not in {"phrase", "account", "legacy-account"}:
                raise ValueError("Select a supported native identity fixture")
        elif argument not in allowed:
            raise ValueError("Only native scenario choices can be passed through the managed controller")


def command(*args, check=True, timeout=15):
    return subprocess.run(args, cwd=ROOT, check=check, capture_output=True, timeout=timeout)


def discovery_identity(pid, *, process_identity=TARGET["process_identity"]):
    try:
        return process_identity(pid)
    except (subprocess.SubprocessError, RuntimeError):
        return None


def observe_driver_receipt(adb, serial, nonce, candidate, *, command=command,
                           process_identity=TARGET["process_identity"]):
    """An unresponsive boot observation is not permission to replace a live emulator."""
    def same_process():
        return discovery_identity(candidate["pid"], process_identity=process_identity) == candidate["identity"]

    if not same_process():
        return None
    receipt = None
    try:
        raw = command(adb, "-s", serial, "exec-out", "run-as", DRIVER, "cat",
                      f"no_backup/managed-restore-{nonce}.json", check=False)
        receipt = json.loads(raw.stdout)
    except (subprocess.TimeoutExpired, ValueError, UnicodeError):
        pass
    # AGP's setup emulator can finish before the waiting test drivers start. It has
    # never entered custody, so discard its observation and keep the original Gradle
    # handle. Once bound, require_binding still treats any identity change as fatal.
    return receipt if same_process() else None


def save(path, contents):
    temporary = path.with_suffix(path.suffix + ".part")
    with open(temporary, "w", opener=lambda name, flags: os.open(name, flags, 0o600)) as output:
        json.dump(contents, output, indent=2)
        output.write("\n")
        output.flush()
        os.fsync(output.fileno())
    temporary.replace(path)


def console_port(listeners):
    ports = {int(p) for p in re.findall(r"^n(?:127\.0\.0\.1|\[::1\]):(\d+)$", listeners, re.M)
             if 5554 <= int(p) <= 5682 and int(p) % 2 == 0}
    if len(ports) != 1:
        return None
    return ports.pop()


def require_outer_results(directory):
    reports = list(directory.glob("**/TEST-*.xml"))
    cases = []
    for report in reports:
        root = ET.parse(report).getroot()
        for suite in root.iter("testsuite"):
            if any(int(suite.get(attribute, "0")) for attribute in ("failures", "errors", "skipped")):
                raise RuntimeError("The managed driver reported a failed or skipped suite")
        for case in root.iter("testcase"):
            if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                raise RuntimeError("The managed driver failed or skipped")
            cases.append((case.get("classname"), case.get("name")))
    if sorted(cases) != [(DRIVER_CASE, "retainsManagedLifecycle1"), (DRIVER_CASE, "retainsManagedLifecycle2")]:
        raise RuntimeError("Expected exactly one completed waiting driver on each managed device")


def run(options):
    validate_native_arguments(options.native_arguments)
    if options.mode != "transfer" and options.native_arguments:
        raise ValueError("Only transfer mode accepts native scenario arguments")
    if options.mode == "enrollment" and any(path is None or not path.is_file() for path in (options.classpath, options.fixture_environment)):
        raise ValueError("Enrollment requires an existing private database fixture and server classpath")
    if len(options.after_transfer) != len(set(options.after_transfer)):
        raise ValueError("Each follow-up must run once per managed generation")
    budget = acceptance_budget(len(options.after_transfer))
    output = options.output.resolve()
    output.mkdir(mode=0o700)
    nonce = str(uuid.uuid4())
    sdk = options.sdk.resolve()
    adb = str(sdk / "platform-tools/adb")
    app = ROOT / "app/android-main/build/outputs/apk/debug/android-main-debug.apk"
    probe = ROOT / "app/android-main/build/outputs/apk/androidTest/debug/android-main-debug-androidTest.apk"
    for apk in (app, probe):
        if not apk.is_file():
            raise RuntimeError("Assemble LogDate and its probe APK before starting the managed pair")
    # Both Gradle's initial installation and later native reinstallation use these
    # immutable copies; another build cannot replace the attested candidate.
    artifacts = output / "apks"
    artifacts.mkdir(mode=0o700)
    pinned = []
    for apk in (app, probe):
        destination = artifacts / apk.name
        shutil.copyfile(apk, destination)
        destination.chmod(0o400)
        pinned.append(destination)
    app, probe = pinned
    hashes = [hashlib.sha256(apk.read_bytes()).hexdigest() for apk in pinned]
    architecture = "arm64-v8a" if platform.machine() == "arm64" else "x86_64"
    image = "google_apis_playstore" if options.mode == "d2d" else "google_apis"
    group = "nativeD2dPair" if options.mode == "d2d" else "nativeRestorePair"
    device_name = "d2dPairApi34" if options.mode == "d2d" else "restorePairApi34"
    avd = f"dev34_{image}_{architecture}_Pixel_9_Pro"
    env = managed_environment(sdk, os.environ)
    args = ["./gradlew", f":tools:native-restore-driver:{group}GroupDebugAndroidTest",
            *pinned_install_arguments(app, probe),
            f"-Pandroid.testInstrumentationRunnerArguments.class={DRIVER_CASE}",
            "-Pandroid.testInstrumentationRunnerArguments.nativeManagedTransport=true",
            f"-Pandroid.testInstrumentationRunnerArguments.nativeManagedNonce={nonce}",
            f"-Pandroid.testInstrumentationRunnerArguments.nativeAppSha256={hashes[0]}",
            f"-Pandroid.testInstrumentationRunnerArguments.nativeProbeSha256={hashes[1]}",
            f"-Pandroid.testInstrumentationRunnerArguments.nativeManagedLifetimeMillis={budget['driverMillis']}",
            "-Pandroid.experimental.testOptions.managedDevices.maxConcurrentDevices=2",
            "-Pandroid.experimental.androidTest.numManagedDeviceShards=2",
            "-Pandroid.experimental.androidTest.builtin_test_platform=false",
            "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader",
            "--max-workers=2", "--console=plain"]
    existing = {int(line.split(None, 1)[0]) for line in command("ps", "-axo", "pid,args").stdout.decode().splitlines()
                if line.strip().split(None, 1)[0].isdigit() and "qemu-system" in line}
    custody_path = output / "custody.json"
    with (output / "gradle.log").open("wb") as log:
        process = subprocess.Popen(args, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        # Retain this handle even if boot observation times out; never launch a replacement
        # while Gradle can still own the same test generation.
        custody = {"nonce": nonce, "adb": adb, "phase": "discovering", "targets": {},
                   "gradle": {"pid": process.pid}}
        save(output / "active-process.json", custody)
        accepted = False
        try:
            deadline = time.monotonic() + budget["boot"]
            while len(custody["targets"]) < 2:
                if process.poll() is not None:
                    raise RuntimeError("The managed pair stopped before both drivers were ready; inspect gradle.log")
                if time.monotonic() > deadline:
                    raise RuntimeError("The managed pair is still owned by the recorded Gradle handle; boot observation expired")
                identity = TARGET["process_identity"](process.pid)
                if nonce not in identity["command"]:
                    time.sleep(.2)
                    continue
                custody["gradle"]["identity"] = identity
                processes = command("ps", "-axo", "pid,args").stdout.decode().splitlines()
                candidates = TARGET["managed_candidates"](processes, existing, avd)
                if len(candidates) > 2:
                    raise RuntimeError("More than two new managed devices; custody is ambiguous")
                for pid, candidate in candidates.items():
                    identity = discovery_identity(pid)
                    if identity is None:
                        continue
                    candidate = {**candidate, "pid": pid, "identity": identity}
                    listeners = command("lsof", "-nP", "-a", "-p", str(pid), "-iTCP", "-sTCP:LISTEN", "-Fn", check=False).stdout.decode()
                    port = console_port(listeners)
                    if port is None:
                        continue
                    serial = f"emulator-{port}"
                    if serial in custody["targets"]:
                        continue
                    receipt = observe_driver_receipt(adb, serial, nonce, candidate)
                    if not isinstance(receipt, dict):
                        continue
                    if not TARGET["validate_driver_receipt"](receipt, nonce, receipt.get("pid")):
                        continue
                    reported = command(adb, "-s", serial, "emu", "avd", "name").stdout.decode().splitlines()[0]
                    if reported != avd or command(adb, "-s", serial, "shell", "getprop", "ro.kernel.qemu").stdout.strip() != b"1":
                        raise RuntimeError("The managed endpoint does not match its QEMU process")
                    custody["targets"][serial] = {**candidate,
                                                   "driverPid": receipt["pid"], "driverStartTicks": receipt["startTicks"],
                                                   "role": receipt["role"], "shardIndex": receipt["shardIndex"]}
                    TARGET["require_binding"](custody, serial)
                save(output / "active-process.json", custody)
                time.sleep(.5)
            custody["phase"] = "active"
            save(custody_path, custody)
            save(output / "active-process.json", custody)
            proxy = output / "bin"
            proxy.mkdir()
            executable = proxy / "adb"
            executable.write_text("#!/bin/sh\nexec " + shlex.join([sys.executable, str(Path(__file__).with_name("managed-native-target.py"))]) + ' "$@"\n')
            executable.chmod(0o700)
            env.update(PATH=str(proxy) + os.pathsep + env["PATH"], LOGDATE_MANAGED_CUSTODY=str(custody_path))
            targets = custody["targets"]
            if {target["role"] for target in targets.values()} != {"source", "destination"} or {target["shardIndex"] for target in targets.values()} != {0, 1}:
                raise RuntimeError("Expected distinct source/destination roles on both managed shards")
            source = next(serial for serial, target in targets.items() if target["role"] == "source")
            destination = next(serial for serial, target in targets.items() if target["role"] == "destination")
            scenario_args = scenario_command(options.mode, source, destination, app, probe, output / "native-transfer", options.native_arguments,
                                             classpath=options.classpath, fixture_environment=options.fixture_environment)
            with (output / "native-harness.log").open("wb") as harness_log:
                subprocess.run(scenario_args, cwd=ROOT, env=env, stdout=harness_log, stderr=subprocess.STDOUT, check=True, timeout=budget["native"])
            for followup in options.after_transfer:
                with (output / f"{followup}-harness.log").open("wb") as harness_log:
                    subprocess.run(followup_command(followup, destination, app, probe, output / followup),
                                   cwd=ROOT, env=env, stdout=harness_log, stderr=subprocess.STDOUT, check=True, timeout=budget["followup"])
            accepted = True
        finally:
            custody["phase"] = "completing"
            save(custody_path, custody)
            # Each endpoint can disappear as soon as it acknowledges completion.
            # Terminal writes validate only their own driver, never the departed peer.
            completion_errors = []
            for serial, binding in custody["targets"].items():
                try:
                    TARGET["require_binding"](custody, serial)
                    result = json.dumps({"nonce": nonce, "startTicks": binding["driverStartTicks"], "accepted": accepted}).encode()
                    remote = f"no_backup/managed-restore-{nonce}-complete.json"
                    script = f"cat > {shlex.quote(remote + '.part')} && mv {shlex.quote(remote + '.part')} {shlex.quote(remote)}"
                    subprocess.run([adb, "-s", serial, "shell", shlex.join(["run-as", DRIVER, "sh", "-c", script])],
                                   input=result, check=True, capture_output=True, timeout=15)
                except (RuntimeError, ValueError, KeyError, subprocess.SubprocessError) as failure:
                    completion_errors.append(type(failure).__name__)
            save(output / "active-process.json", {**custody, "completionErrors": completion_errors})
            if process.poll() is None and len(custody["targets"]) == 2:
                try:
                    process.wait(timeout=budget["teardown"])
                except subprocess.TimeoutExpired:
                    pass
        if process.poll() is None:
            raise RuntimeError("Gradle is still live; inspect the retained process handle")
        if process.returncode != 0 or completion_errors:
            raise RuntimeError("The managed pair did not finish successfully")
        results = ROOT / "tools/native-restore-driver/build/outputs/androidTest-results/managedDevice/debug" / device_name
        require_outer_results(results)
        shutil.copytree(results, output / "managed-results")
        save(output / "acceptance.json", acceptance_receipt(options.mode, options.native_arguments, options.after_transfer))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk"))))
    parser.add_argument("--after-transfer", action="append", choices=sorted(FOLLOWUPS), default=[])
    parser.add_argument("--mode", choices=("transfer", "publication", "work-restart", "d2d", "enrollment"), default="transfer")
    parser.add_argument("--classpath", type=Path)
    parser.add_argument("--fixture-environment", type=Path)
    options, native_arguments = parser.parse_known_args()
    if native_arguments[:1] == ["--"]:
        native_arguments = native_arguments[1:]
    options.native_arguments = native_arguments
    run(options)
