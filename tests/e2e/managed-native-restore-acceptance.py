#!/usr/bin/env python3
"""Run native transport oracles while a separate GMD driver survives LogDate restarts."""
import argparse
import base64
import hashlib
import json
import math
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
EMULATOR = runpy.run_path(str(Path(__file__).with_name("managed-native-emulator.py")))
DRIVER = TARGET["DRIVER"]
DRIVER_CASE = "app.logdate.tools.restoredriver.NativeRestoreDriverTest"
FOLLOWUPS = {"account-job": "native-account-job-acceptance.py", "missing-media": "native-missing-media-acceptance.py",
             "storage": "native-storage-recovery-acceptance.py", "ui": "native-recovery-ui-acceptance.py",
             "permissions": "native-permission-acceptance.py", "contracts": "native-post-transfer-acceptance.py"}
add_failure_note = runpy.run_path(
    str(Path(__file__).with_name("native-failure-diagnostics.py"))
)["add_failure_note"]


def retain_terminal_results(process, results, output, started_ns):
    if process.poll() is None or not results.is_dir():
        return None
    retained = output / "managed-results"
    retained.mkdir(mode=0o700)
    for source in results.rglob("*"):
        if source.is_symlink() or not source.is_file() or source.stat().st_mtime_ns < started_ns:
            continue
        target = retained / source.relative_to(results)
        target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        shutil.copyfile(source, target)
        target.chmod(0o600)
    return retained


def acceptance_receipt(mode, native_arguments, followups):
    cloud_selection = mode == "transfer" and any(
        choice in native_arguments for choice in ("--exercise-cloud-backup", "--exercise-credentials-only"))
    return {"managedDeviceLifecycleAccepted": True, "outerDrivers": 2, "mode": mode,
            "managedNativeTransport": mode not in {"work-restart", "network-policy", "late-media", "manual-account"},
            "crossDeviceTransferAccepted": mode in {"transfer", "publication", "enrollment", "upgrade", "widget"} and not cloud_selection,
            "localCloudSelectionAccepted": cloud_selection and "--exercise-cloud-backup" in native_arguments,
            "nativeArguments": native_arguments, "followups": followups,
            "manualAccountRecoveryAccepted": mode == "manual-account",
            "googleRestoreProviderAccepted": False}


def acceptance_budget(followups, *, mode="transfer"):
    budget = {"boot": 600, "native": 1800 if mode == "enrollment" else 1200, "followup": 600, "teardown": 120}
    budget["driverMillis"] = 1000 * (budget["boot"] + budget["native"] + followups * budget["followup"] + budget["teardown"])
    if budget["driverMillis"] > 90 * 60_000:
        raise ValueError("The combined profile exceeds the bounded managed lifetime; split its follow-ups")
    return budget


def validate_followups(mode, followups):
    if len(followups) != len(set(followups)):
        raise ValueError("Each follow-up must run once per managed generation")
    if mode == "upgrade" and followups:
        raise ValueError("Released upgrades retain their candidate APK; run other profiles in a separate managed generation")


def followup_command(name, destination, app, probe, output):
    if name not in FOLLOWUPS:
        raise ValueError("Choose an existing native recovery acceptance harness")
    return [sys.executable, str(ROOT / "tests/e2e" / FOLLOWUPS[name]), "--emulator", destination,
            "--app-apk", str(app), "--test-apk", str(probe), "--output", str(output), "--synthetic-data"]


def scenario_command(mode, source, destination, app, probe, output, arguments, *, classpath=None, fixture_environment=None, fixture_control=None):
    validate_native_arguments(arguments)
    if mode not in {"transfer", "publication", "work-restart", "d2d", "enrollment", "upgrade", "network-policy", "widget", "late-media", "manual-account"} or (mode != "transfer" and arguments):
        raise ValueError("Only transfer mode accepts native scenario arguments")
    if mode == "manual-account":
        if fixture_control is None:
            raise ValueError("Manual account recovery requires its owned local fixture")
        return [sys.executable, str(ROOT / "tests/e2e/manual-account-recovery-acceptance.py"),
                "--source", source, "--destination", destination, "--output", str(output),
                "--fixture-control", str(fixture_control), "--synthetic-data", "--focused"]
    filenames = {"transfer": "native-backup-acceptance.py", "publication": "native-publication-acceptance.py",
                 "work-restart": "native-restore-work-restart-acceptance.py", "d2d": "native-d2d-acceptance.py",
                 "enrollment": "native-device-enrollment-acceptance.py", "upgrade": "native-released-upgrade-acceptance.py",
                 "network-policy": "native-missing-media-acceptance.py", "widget": "native-widget-transfer-acceptance.py",
                 "late-media": "native-late-media-acceptance.py"}
    command = [sys.executable, str(ROOT / "tests/e2e" / filenames[mode])]
    if mode in {"work-restart", "d2d", "network-policy"}:
        command += ["--emulator", source]
    else:
        command += ["--source", source, "--destination", destination]
    command += ["--app-apk", str(app), "--test-apk", str(probe), "--output", str(output)]
    if mode not in {"d2d", "network-policy"}:
        command += ["--seed-empty-source"]
    if mode == "network-policy":
        command += ["--emulator", destination, "--network-only", "--synthetic-data"]
    if mode == "enrollment":
        if classpath is None or fixture_environment is None:
            raise ValueError("Enrollment requires an existing private database fixture and server classpath")
        command += ["--classpath", str(classpath), "--fixture-environment", str(fixture_environment)]
    if mode == "upgrade":
        if fixture_environment is None:
            raise ValueError("Released upgrade requires a private artifact fixture")
        command += ["--fixture-environment", str(fixture_environment)]
    return command + arguments


def managed_environment(sdk, inherited, package):
    return dict(inherited, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk),
                GRADLE_USER_HOME=str(Path.home() / ".gradle"), JAVA_TOOL_OPTIONS="-Djava.awt.headless=true",
                LOGDATE_NATIVE_ACCEPTANCE_PACKAGE=package)


def managed_boot_deadline(current_deadline, qemu_observed_at, boot_budget):
    return current_deadline if current_deadline is not None else qemu_observed_at + boot_budget


def pinned_install_arguments(app, probe):
    if any(not path.is_absolute() or not path.is_file() for path in (app, probe)):
        raise ValueError("Managed installation requires both existing absolute pinned APK paths")
    return [f"-Plogdate.nativeAcceptanceAppApk={app}", f"-Plogdate.nativeAcceptanceProbeApk={probe}"]


def candidate_apk_paths(app_override, probe_override, root):
    if (app_override is None) != (probe_override is None):
        raise ValueError("Provide both pinned candidate APK paths or neither")
    if app_override is None:
        return (
            root / "app/android-main/build/outputs/apk/debug/android-main-debug.apk",
            root / "app/android-main/build/outputs/apk/androidTest/debug/android-main-debug-androidTest.apk",
        )
    if any(not path.is_absolute() or not path.is_file() for path in (app_override, probe_override)):
        raise ValueError("Pinned candidate APKs must be existing absolute files")
    return app_override.resolve(), probe_override.resolve()


def initial_installation(mode, app, probe, fixture_environment, output):
    """Start upgrades on the released APK and freeze their inputs before managed boot."""
    if mode != "upgrade":
        return app, probe, fixture_environment
    compatibility = runpy.run_path(str(Path(__file__).with_name("native-released-upgrade-acceptance.py")))
    document, artifacts = compatibility["fixture_artifacts"](fixture_environment, output)
    if hashlib.sha256(app.read_bytes()).hexdigest() != document["candidateApp"]["sha256"]:
        raise RuntimeError("The selected candidate differs from the released-upgrade fixture")
    frozen_document = dict(document)
    for role, artifact in artifacts.items():
        frozen_document[role] = dict(path=str(artifact), sha256=document[role]["sha256"])
    frozen = output / "released-upgrade-fixture.json"
    descriptor = os.open(frozen, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as stream:
        json.dump(frozen_document, stream)
    return artifacts["baselineApp"], artifacts["baselineProbe"], frozen


def validate_native_arguments(arguments):
    allowed = {"--exercise-quota", "--exercise-archive-recovery", "--exercise-missing-keys",
               "--exercise-missing-privacy", "--exercise-credentials-only", "--exercise-cloud-backup", "--exercise-data-only",
               "--exercise-duplicate-restore", "--exercise-partial-media", "--exercise-pending-camera-draft"}
    remaining = iter(arguments)
    for argument in remaining:
        if argument == "--identity":
            if next(remaining, None) not in {"phrase", "account", "legacy-account"}:
                raise ValueError("Select a supported native identity fixture")
        elif argument not in allowed:
            raise ValueError("Only native scenario choices can be passed through the managed controller")


def command(*args, check=True, timeout=15):
    return subprocess.run(args, cwd=ROOT, check=check, capture_output=True, timeout=timeout)


def process_snapshot(*, execute=command, retries=2, sleep=time.sleep):
    """Retry transient host process-list stalls without weakening emulator ownership checks."""
    for attempt in range(retries):
        try:
            return execute("ps", "-axo", "pid,args").stdout.decode().splitlines()
        except subprocess.TimeoutExpired:
            if attempt + 1 == retries:
                return None
            sleep(0.2)


def verify_managed_emulator(sdk, output, *, command=command):
    result = command(str(sdk / "emulator/emulator"), "-no-window", "-version", check=False)
    log = output / "emulator-preflight.log"
    log.write_bytes(b"stdout:\n" + result.stdout[:16_384] + b"\nstderr:\n" + result.stderr[:16_384])
    log.chmod(0o600)
    result.check_returncode()


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
        if any(re.search(r"\bcancel(?:ed|led|lation)\b", error.text or "", re.IGNORECASE)
               for error in root.iter("system-err")):
            raise RuntimeError("The managed driver was canceled")
        for suite in root.iter("testsuite"):
            if any(int(suite.get(attribute, "0")) for attribute in ("failures", "errors", "skipped")):
                raise RuntimeError("The managed driver reported a failed or skipped suite")
        for case in root.iter("testcase"):
            try:
                duration = float(case.get("time", "0"))
            except ValueError as error:
                raise RuntimeError("The managed case has an invalid duration") from error
            if not math.isfinite(duration) or duration < 0:
                raise RuntimeError("The managed case did not complete with a valid duration")
            if any(case.find(tag) is not None for tag in ("failure", "error", "skipped")):
                raise RuntimeError("The managed driver failed or skipped")
            cases.append((case.get("classname"), case.get("name")))
    if sorted(cases) != [(DRIVER_CASE, "retainsManagedLifecycle1"), (DRIVER_CASE, "retainsManagedLifecycle2")]:
        raise RuntimeError("Expected exactly one completed waiting driver on each managed device")


def assemble_candidate_apks(output, package, *, root=ROOT, run=subprocess.run, recovery_assets=None, app_only=False, sdk=None):
    if not re.fullmatch(r"studio\.hypertext\.logdate(?:\.[a-z][a-z0-9_]*)*\.debug", package):
        raise ValueError("Native acceptance requires the matching LogDate debug application ID")
    application_id = package.removesuffix(".debug")
    arguments = ["./gradlew", ":app:android-main:assembleDebug"]
    if not app_only:
        arguments.append(":app:android-main:assembleDebugAndroidTest")
    if recovery_assets is not None:
        if app_only or not recovery_assets.is_dir():
            raise ValueError("Manual recovery requires an existing private acceptance asset directory")
        arguments.append(f"-Plogdate.recoveryAcceptanceAssetsDir={recovery_assets}")
    if application_id != "studio.hypertext.logdate":
        arguments.append(f"-Plogdate.applicationId={application_id}")
    arguments.extend(("--no-daemon", "--max-workers=2", "--console=plain"))
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    with (output / "candidate-build.log").open("wb") as log:
        environment = dict(os.environ, ANDROID_HOME=str(sdk), ANDROID_SDK_ROOT=str(sdk)) if sdk is not None else None
        run(arguments, cwd=root, env=environment, stdout=log, stderr=subprocess.STDOUT, check=True, timeout=1800)


def android_signing_origin(sdk, apk, *, execute=None):
    execute = execute or command
    versions = sorted((sdk / "build-tools").glob("*/apksigner"), reverse=True)
    if not versions:
        raise RuntimeError("Android SDK has no apksigner for the selected APK")
    result = execute(str(versions[0]), "verify", "--print-certs", str(apk))
    digests = re.findall(rb"(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})$", result.stdout)
    if len(digests) != 1:
        raise RuntimeError("The selected APK must have exactly one signer")
    return "android:apk-key-hash:" + base64.urlsafe_b64encode(bytes.fromhex(digests[0].decode())).decode().rstrip("=")


def start_manual_fixture(options, output, sdk, package):
    if any(path is None or not path.is_file() for path in (options.classpath, options.fixture_environment)):
        raise ValueError("Manual account recovery requires a private server classpath and fixture environment")
    assemble_candidate_apks(output, package, app_only=True, sdk=sdk)
    candidate = ROOT / "app/android-main/build/outputs/apk/debug/android-main-debug.apk"
    if not candidate.is_file():
        raise RuntimeError("The candidate application APK was not built")
    origin = android_signing_origin(sdk, candidate)
    fixture = output / "manual-account-fixture"
    if fixture.exists():
        raise RuntimeError("Manual account recovery requires a fresh private fixture directory")
    arguments = [sys.executable, str(ROOT / "tests/e2e/manual-account-recovery-fixture.py"),
                 "--classpath", str(options.classpath), "--fixture-environment", str(options.fixture_environment),
                 "--android-origin", origin, "--output", str(fixture), "--lifetime", "3600"]
    with (output / "manual-account-fixture.log").open("wb") as log:
        process = subprocess.Popen(arguments, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + 150
    while not (fixture / "fixture-ready").is_file():
        if process.poll() is not None or time.monotonic() >= deadline:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=15)
            raise RuntimeError("The private manual recovery fixture did not become ready; inspect manual-account-fixture.log")
        time.sleep(.1)
    return process, fixture, origin


def verify_pinned_packages(sdk, app, probe, package, *, execute=None):
    execute = execute or command
    versions = sorted((sdk / "build-tools").glob("*/aapt2"), reverse=True)
    if not versions:
        raise RuntimeError("Android SDK has no aapt2 to verify pinned APK identities")
    for apk, expected in ((app, package), (probe, f"{package}.test")):
        actual = execute(str(versions[0]), "dump", "packagename", str(apk)).stdout.decode().strip()
        if actual != expected:
            raise RuntimeError(f"Pinned {apk.name} has application ID {actual!r}; expected {expected!r}")


def run_managed_pair(options, *, recovery_assets=None, fixture_control=None, expected_android_origin=None):
    validate_native_arguments(options.native_arguments)
    if options.mode != "transfer" and options.native_arguments:
        raise ValueError("Only transfer mode accepts native scenario arguments")
    if options.mode == "enrollment" and any(path is None or not path.is_file() for path in (options.classpath, options.fixture_environment)):
        raise ValueError("Enrollment requires an existing private database fixture and server classpath")
    if options.mode == "upgrade" and (options.fixture_environment is None or not options.fixture_environment.is_file()):
        raise ValueError("Released upgrade requires an existing private artifact fixture")
    validate_followups(options.mode, options.after_transfer)
    budget = acceptance_budget(len(options.after_transfer), mode=options.mode)
    output = options.output.resolve()
    output.mkdir(mode=0o700, exist_ok=options.mode == "manual-account")
    nonce = str(uuid.uuid4())
    sdk = options.sdk.resolve()
    adb = str(sdk / "platform-tools/adb")
    package = os.environ.get("LOGDATE_NATIVE_ACCEPTANCE_PACKAGE", "studio.hypertext.logdate.debug")
    app, probe = candidate_apk_paths(options.candidate_app_apk, options.candidate_test_apk, ROOT)
    if options.candidate_app_apk is None:
        assemble_candidate_apks(output, package, recovery_assets=recovery_assets, sdk=sdk)
        app, probe = candidate_apk_paths(options.candidate_app_apk, options.candidate_test_apk, ROOT)
    for apk in (app, probe):
        if not apk.is_file():
            raise RuntimeError("Candidate build did not produce LogDate and probe APKs")
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
    verify_pinned_packages(sdk, app, probe, package)
    if expected_android_origin is not None and android_signing_origin(sdk, app) != expected_android_origin:
        raise RuntimeError("The pinned APK signer changed after the local recovery fixture was created")
    initial_app, initial_probe, frozen_fixture = initial_installation(
        options.mode, app, probe, options.fixture_environment, output)
    hashes = [hashlib.sha256(apk.read_bytes()).hexdigest() for apk in (initial_app, initial_probe)]
    architecture = "arm64-v8a" if platform.machine() == "arm64" else "x86_64"
    image = "google_apis_playstore" if options.mode == "d2d" else "google_apis"
    group = "nativeD2dPair" if options.mode == "d2d" else "nativeRestorePair"
    device_name = "d2dPairApi34" if options.mode == "d2d" else "restorePairApi34"
    avd = f"dev34_{image}_{architecture}_Pixel_9_Pro"
    properties = ROOT / "local.properties"
    if properties.is_file() and re.search(r"(?m)^\s*sdk(?:\\\.|\.)dir(?:\s|:|=)", properties.read_text()):
        raise RuntimeError("The managed SDK facade cannot override sdk.dir in local.properties")
    capability = command(str(sdk / "emulator/emulator"), "-help-no-snapshot-load")
    if b"snapshot" not in capability.stdout + capability.stderr:
        raise RuntimeError("The installed emulator must support cold boots of managed instances")
    managed_sdk = EMULATOR["create_managed_sdk"](sdk, output / "managed-sdk", avd, nonce)
    verify_managed_emulator(managed_sdk, output)
    env = managed_environment(managed_sdk, os.environ, package)
    env["LOGDATE_NATIVE_ACCEPTANCE_TIMEOUT_SECONDS"] = str(budget["native"])
    initial_processes = command("ps", "-axo", "pid,args").stdout.decode().splitlines()
    capacity = TARGET["managed_device_capacity"](initial_processes)
    args = ["./gradlew", f":tools:native-restore-driver:{group}GroupDebugAndroidTest",
            *pinned_install_arguments(initial_app, initial_probe),
            f"-Pandroid.testInstrumentationRunnerArguments.class={DRIVER_CASE}",
            "-Pandroid.testInstrumentationRunnerArguments.nativeManagedTransport=true",
            f"-Pandroid.testInstrumentationRunnerArguments.nativeManagedNonce={nonce}",
            f"-Pandroid.testInstrumentationRunnerArguments.nativeAppSha256={hashes[0]}",
            f"-Pandroid.testInstrumentationRunnerArguments.nativeProbeSha256={hashes[1]}",
            f"-Pandroid.testInstrumentationRunnerArguments.nativeManagedLifetimeMillis={budget['driverMillis']}",
            f"-Pandroid.experimental.testOptions.managedDevices.maxConcurrentDevices={capacity}",
            "-Pandroid.experimental.androidTest.numManagedDeviceShards=2",
            "-Pandroid.experimental.androidTest.builtin_test_platform=false",
            "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader",
            # AGP retains SDK environment properties in daemon-static state.
            # A fresh single-use daemon cannot reuse a deleted generation's SDK.
            "--no-daemon", "--no-configuration-cache", "--max-workers=2", "--console=plain"]
    existing = {int(line.split(None, 1)[0]) for line in initial_processes
                if line.strip().split(None, 1)[0].isdigit() and "qemu-system" in line}
    custody_path = output / "custody.json"
    results = ROOT / "tools/native-restore-driver/build/outputs/androidTest-results/managedDevice/debug" / device_name
    results_started_ns = time.time_ns()
    with (output / "gradle.log").open("wb") as log:
        process = subprocess.Popen(args, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
        # Retain this handle even if boot observation times out; never launch a replacement
        # while Gradle can still own the same test generation.
        custody = {"nonce": nonce, "adb": adb, "phase": "discovering", "targets": {},
                   "gradle": {"pid": process.pid}}
        save(output / "active-process.json", custody)
        accepted = False
        primary = None
        observed_managed_pids = set()
        try:
            deadline = None
            launch_deadline = time.monotonic() + max(1200, budget["boot"] * 2)
            while len(custody["targets"]) < 2:
                if process.poll() is not None:
                    raise RuntimeError("The managed pair stopped before both drivers were ready; inspect gradle.log")
                identity = TARGET["process_identity"](process.pid)
                if nonce not in identity["command"]:
                    time.sleep(.2)
                    continue
                custody["gradle"]["identity"] = identity
                processes = process_snapshot()
                if processes is None:
                    if time.monotonic() > (deadline or launch_deadline):
                        raise RuntimeError("Managed emulator process discovery expired after repeated host process-list timeouts")
                    time.sleep(0.2)
                    continue
                candidates = TARGET["managed_candidates"](processes, existing, avd)
                candidates = EMULATOR["owned_managed_candidates"](
                    output / "managed-emulator-launches", candidates, nonce)
                if candidates:
                    deadline = managed_boot_deadline(deadline, time.monotonic(), budget["boot"])
                if deadline is not None and time.monotonic() > deadline:
                    raise RuntimeError("The managed pair is still owned by the recorded Gradle handle; boot observation expired")
                if deadline is None and not candidates and time.monotonic() > launch_deadline:
                    raise RuntimeError("Gradle did not launch an owned managed emulator before the bounded discovery deadline")
                observed_managed_pids.update(candidates)
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
            network_identity = {}
            stations = {}
            for serial, binding in targets.items():
                for peer in targets:
                    TARGET["require_binding"](custody, peer)
                observed = command(*TARGET["device_command"](adb, serial, ["shell", "ip", "-o", "link", "show", "wlan0"]), check=False)
                network_identity[binding["role"]] = {"returnCode": observed.returncode, "interface": observed.stdout.decode(), "error": observed.stderr.decode()}
                stations[serial] = TARGET["wifi_station_address"](observed.stdout)
            save(output / "managed-network-identities.json", network_identity)
            # The Play Store image forbids root reads; its provider-only profile
            # remains separate from the rooted, paired transport/network oracles.
            if options.mode != "d2d":
                station_identity = EMULATOR["require_managed_station_launches"](
                    output / "managed-emulator-launches", targets, nonce, stations)
                save(output / "managed-network-stations.json", station_identity)
            scenario_args = scenario_command(options.mode, source, destination, app, probe, output / "native-transfer", options.native_arguments,
                                             classpath=options.classpath, fixture_environment=frozen_fixture,
                                             fixture_control=fixture_control)
            with (output / "native-harness.log").open("wb") as harness_log:
                subprocess.run(scenario_args, cwd=ROOT, env=env, stdout=harness_log, stderr=subprocess.STDOUT, check=True, timeout=budget["native"])
            for followup in options.after_transfer:
                with (output / f"{followup}-harness.log").open("wb") as harness_log:
                    subprocess.run(followup_command(followup, destination, app, probe, output / followup),
                                   cwd=ROOT, env=env, stdout=harness_log, stderr=subprocess.STDOUT, check=True, timeout=budget["followup"])
            accepted = True
        except BaseException as failure:
            primary = failure
            raise
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
            try:
                listing = command("ps", "-axo", "pid,args", check=False)
                process_lines = listing.stdout.decode().splitlines() if listing.returncode == 0 else None
                invocations = [json.loads(path.read_text()) for path in (output / "managed-emulator-invocations").glob("*.json")]
                if EMULATOR["managed_processes_stopped"](process, invocations, observed_managed_pids, process_lines, managed_sdk):
                    shutil.rmtree(managed_sdk)
            except Exception as failure:
                if primary is None:
                    primary = failure
                else:
                    add_failure_note(primary, "Could not remove private managed SDK facade: " + type(failure).__name__)
            if len(custody["targets"]) == 2:
                try:
                    retain_terminal_results(process, results, output, results_started_ns)
                except Exception as failure:
                    if primary is None:
                        raise
                    add_failure_note(primary, "Could not retain managed results: " + type(failure).__name__)
        if primary is not None:
            raise primary
        if process.poll() is None:
            raise RuntimeError("Gradle is still live; inspect the retained process handle")
        if process.returncode != 0 or completion_errors:
            raise RuntimeError("The managed pair did not finish successfully")
        require_outer_results(output / "managed-results")
        save(output / "acceptance.json", acceptance_receipt(options.mode, options.native_arguments, options.after_transfer))


def run(options):
    if options.mode != "manual-account":
        return run_managed_pair(options)
    if options.after_transfer or options.native_arguments:
        raise ValueError("Focused manual recovery uses its own untouched managed pair")
    output = options.output.resolve()
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    sdk = options.sdk.resolve()
    package = os.environ.get("LOGDATE_NATIVE_ACCEPTANCE_PACKAGE", "studio.hypertext.logdate.debug")
    process, fixture, origin = start_manual_fixture(options, output, sdk, package)
    try:
        run_managed_pair(options, recovery_assets=fixture / "assets", fixture_control=fixture / "server-control",
                         expected_android_origin=origin)
        process.wait(timeout=30)
        if process.returncode != 0:
            raise RuntimeError("The local recovery fixture did not finish successfully; inspect its private log")
    except BaseException:
        (output / "acceptance.json").unlink(missing_ok=True)
        raise
    finally:
        if process.poll() is None:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=5)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, default=Path(os.environ.get("ANDROID_HOME", str(Path.home() / "Library/Android/sdk"))))
    parser.add_argument("--after-transfer", action="append", choices=sorted(FOLLOWUPS), default=[])
    parser.add_argument("--mode", choices=("transfer", "publication", "work-restart", "d2d", "enrollment", "upgrade", "network-policy", "widget", "late-media", "manual-account"), default="transfer")
    parser.add_argument("--classpath", type=Path)
    parser.add_argument("--fixture-environment", type=Path)
    parser.add_argument("--candidate-app-apk", type=Path)
    parser.add_argument("--candidate-test-apk", type=Path)
    options, native_arguments = parser.parse_known_args()
    if native_arguments[:1] == ["--"]:
        native_arguments = native_arguments[1:]
    options.native_arguments = native_arguments
    run(options)
