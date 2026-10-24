#!/usr/bin/env python3
"""Verify an installed upgrade and native restore from a released encrypted journal."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import runpy
import shlex
import shutil
import stat

ROOT = Path(__file__).resolve().parents[2]
H = runpy.run_path(str(ROOT / "tests/e2e/native-backup-acceptance.py"))
add_failure_note = H["add_failure_note"]
TARGET = runpy.run_path(str(ROOT / "tests/e2e/managed-native-target.py"))
PROBE = "app.logdate.client.e2e.NativeReleasedUpgradeProbeTest"
MARKER = "native-released-upgrade.json"


def require_owned_pair(source, destination):
    if source == destination:
        raise ValueError("Compatibility requires two different managed devices")
    custody = json.loads(Path(os.environ["LOGDATE_MANAGED_CUSTODY"]).read_text())
    if custody.get("phase") != "active":
        raise RuntimeError("The managed generation is not accepting native commands")
    for serial in (source, destination):
        H["emulator_serial"](serial)
        TARGET["require_binding"](custody, serial)


def fixture_artifacts(path, output):
    if stat.S_IMODE(path.stat().st_mode) != 0o600 or path.stat().st_size > 32_768:
        raise ValueError("Compatibility requires a private bounded fixture document")
    document = json.loads(path.read_text())
    if document.get("baselineVersion") != 205 or document.get("baselineSchema") != 55:
        raise ValueError("Select the reviewed released-version baseline")
    candidate_schema = document.get("candidateSchema")
    if document.get("candidateVersion", 0) <= 205 or type(candidate_schema) is not int or candidate_schema not in (57, 58):
        raise ValueError("The candidate must upgrade the released baseline to schema 57 or 58")
    folder = output / "compatibility-apks"
    folder.mkdir(mode=0o700)
    artifacts = {}
    for name in ("baselineApp", "baselineProbe", "candidateApp"):
        spec = document[name]
        original = Path(spec["path"])
        if not original.is_absolute() or not original.is_file():
            raise ValueError("Compatibility APK paths must be existing absolute files")
        digest = hashlib.sha256(original.read_bytes()).hexdigest()
        if digest != spec["sha256"]:
            raise RuntimeError("A selected compatibility APK changed after its build receipt")
        selected = folder / (name + ".apk")
        shutil.copyfile(original, selected)
        selected.chmod(0o400)
        if hashlib.sha256(selected.read_bytes()).hexdigest() != digest:
            raise RuntimeError("The compatibility artifact copy changed")
        artifacts[name] = selected
    return document, artifacts


def installed_hash(serial, package):
    rows = H["adb"](serial, "shell", "pm", "path", package).decode().splitlines()
    if len(rows) != 1 or not rows[0].startswith("package:/data/app/") or not rows[0].endswith(".apk"):
        raise RuntimeError("Compatibility requires a single installed debug APK")
    digest = H["adb"](serial, "shell", "run-as", H["PACKAGE"], "sha256sum", rows[0].removeprefix("package:")).split()[0]
    return digest.decode()


def install(serial, app, probe):
    for artifact, package in ((app, H["PACKAGE"]), (probe, H["PACKAGE"] + ".test")):
        H["adb"](serial, "install", "-r", "-t", str(artifact))
        if installed_hash(serial, package) != hashlib.sha256(artifact.read_bytes()).hexdigest():
            raise RuntimeError("The installed compatibility APK does not match the selected build")


def probe(serial, stage, version, schema, output):
    raw = H["adb"](
        serial, "shell", "am", "instrument", "-w", "-r", "-e", "class", PROBE,
        "-e", "releasedUpgradeStage", stage, "-e", "releasedUpgradeVersion", str(version),
        "-e", "releasedUpgradeSchema", str(schema), H["RUNNER"], timeout=180,
    )
    H["private_file"](output, raw)
    if not H["instrumentation_passed"](raw):
        raise RuntimeError(f"Released compatibility {stage} failed; inspect its private probe log")


def write_destination_marker(serial, payload):
    H["adb"](serial, "shell", "run-as", H["PACKAGE"], "mkdir", "-p", "no_backup")
    command = shlex.join(["run-as", H["PACKAGE"], "sh", "-c", f"umask 077; cat > no_backup/{MARKER}"])
    H["adb"](serial, "shell", command, input_bytes=payload)
    actual = H["adb"](serial, "exec-out", "run-as", H["PACKAGE"], "cat", "no_backup/" + MARKER)
    if actual != payload:
        raise RuntimeError("The private released-source expectation did not round-trip")


def restore_radios(radios, primary_error=None):
    failures = []
    for serial, original in radios.items():
        try:
            H["restore_radio_state"](serial, original)
        except Exception as error:
            failures.append(type(error).__name__)
    if failures:
        if primary_error is None:
            raise RuntimeError("Compatibility radio restoration failed: " + ", ".join(failures))
        add_failure_note(primary_error, "Radio restoration also failed: " + ", ".join(failures))


def run(options):
    require_owned_pair(options.source, options.destination)
    if not options.seed_empty_source:
        raise ValueError("Released compatibility only accepts fresh synthetic source data")
    output = options.output.resolve()
    output.mkdir(mode=0o700)
    fixture, artifacts = fixture_artifacts(options.fixture_environment, output)
    radios = {serial: H["radio_state"](serial) for serial in (options.source, options.destination)}
    primary_error = None
    try:
        for serial in radios:
            H["prepare"](serial, allow_preinstalled_empty=True)
            H["set_offline"](serial, True)
        source, destination = options.source, options.destination
        install(source, artifacts["baselineApp"], artifacts["baselineProbe"])
        H["probe"](source, "seed", output / "released-source-seed.log", identity="account")
        H["recording_policy_probe"](source, output / "released-recording-policy.log")
        probe(source, "baseline", 205, 55, output / "released-source-baseline.log")
        metadata = json.loads(H["adb"](source, "exec-out", "run-as", H["PACKAGE"], "cat", "no_backup/native-backup-fixture.json"))
        expectation = H["adb"](source, "exec-out", "run-as", H["PACKAGE"], "cat", "no_backup/" + MARKER)
        H["private_file"](output / MARKER, expectation)
        # The released app and its old backup agent must write the transport before updating.
        _, source_files = H["backup_source"](
            source, output / "released-native-backup.log",
            verify_after_backup=lambda: probe(source, "baseline-restart", 205, 55, output / "released-source-after-backup.log"),
        )
        payload = H["adb"](source, "exec-out", "tar", "-C", H["TRANSPORT_FILES"], "-cf", "-", "1")
        transport = output / "released-native-transport.tar"
        H["private_file"](transport, payload)
        candidate = fixture["candidateVersion"]
        candidate_schema = fixture["candidateSchema"]
        install(source, artifacts["candidateApp"], options.test_apk)
        probe(source, "upgrade", candidate, candidate_schema, output / "installed-upgrade.log")
        H["adb"](source, "shell", "am", "force-stop", H["PACKAGE"])
        probe(source, "upgrade-restart", candidate, candidate_schema, output / "installed-upgrade-restart.log")
        install(destination, artifacts["candidateApp"], options.test_apk)
        H["adb"](destination, "push", str(transport), "/data/local/tmp/logdate-released-native-transport.tar")
        H["adb"](destination, "shell", "tar", "-C", H["TRANSPORT_FILES"], "-xf", "/data/local/tmp/logdate-released-native-transport.tar")
        for command in (("chown", "-R", "system:system"), ("restorecon", "-R")):
            H["adb"](destination, "shell", *command, H["TRANSPORT_FILES"] + "/1")
        for stage in ("restore", "replay"):
            before = H["package_file_snapshot"](destination) if stage == "replay" else None
            raw = H["adb"](destination, "shell", "bmgr", "restore", "1", H["PACKAGE"])
            H["private_file"](output / f"released-native-{stage}.log", raw)
            if b"restoreFinished: 0" not in raw:
                raise RuntimeError("Android did not restore the released backup")
            if before is not None:
                H["assert_snapshot_preserved"](before, H["package_file_snapshot"](destination), require_database=True)
        H["verify_restored_snapshot"](H["package_file_snapshot"](destination), source_files, credentials_only=False)
        write_destination_marker(destination, expectation)
        probe(destination, "restore", candidate, candidate_schema, output / "released-backup-restore.log")
        H["adb"](destination, "shell", "am", "force-stop", H["PACKAGE"])
        probe(destination, "restore-restart", candidate, candidate_schema, output / "released-backup-restart.log")
        # These existing helpers create the offline edit; run them after preservation comparisons.
        H["probe"](source, "verify-in-place", output / "upgraded-source-originals-and-key.log", metadata, "account")
        H["probe"](destination, "verify", output / "restored-originals-and-key.log", metadata, "account")
        launch = H["adb"](destination, "shell", "am", "start", "-W", "-n", H["PACKAGE"] + "/app.logdate.client.MainActivity")
        H["private_file"](output / "released-destination-launch.log", launch)
        H["wait_for_timeline"](destination, output, timeout_seconds=120, prefix="released-destination")
        result = {"releasedInstalledUpgrade": True, "releasedBackupNativeRestore": True,
                  "sourceSchema": fixture["baselineSchema"], "targetSchema": candidate_schema,
                  "encryptedSchemaMigration": True, "encryptedSchema55To57": candidate_schema == 57,
                  "journalKeysAndOwnerRetained": True,
                  "timestampsDeletionAndOutboxRetained": True, "bothDraftStoresRetained": True,
                  "sourceDeviceRetained": True, "distinctStableReplacementDevice": True,
                  "pendingRecordingMetadataRetained": True, "temporaryRecordingBytesExcluded": True,
                  "duplicateOldBackupDelivery": True, "googleProviderAccepted": False, "playSignedArtifact": False}
        H["private_file"](output / "acceptance.json", json.dumps(result, indent=2).encode())
        return result
    except BaseException as error:
        primary_error = error
        raise
    finally:
        restore_radios(radios, primary_error)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True)
    parser.add_argument("--destination", required=True)
    parser.add_argument("--app-apk", type=Path, required=True)
    parser.add_argument("--test-apk", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--fixture-environment", type=Path, required=True)
    parser.add_argument("--seed-empty-source", action="store_true")
    run(parser.parse_args())
