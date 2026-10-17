#!/usr/bin/env python3
"""Exercise Android's debug backup transport between two fresh, rootable emulators.

Android restores application files from the source transport's payload. Fixtures
cover phrase-derived and account-unlocked encryption identities.
"""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import tarfile
import time
import uuid
import xml.etree.ElementTree as ET


PACKAGE = "studio.hypertext.logdate.debug"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
PROBE = "app.logdate.client.e2e.NativeBackupTransferProbeTest"
TRANSPORT = "com.android.localtransport/.LocalTransport"
TRANSPORT_FILES = "/data/user/0/com.android.localtransport/files"
USER_CONTENT_PATHS = (
    "./databases/logdate",
    "./databases/logdate-wal",
    "./databases/logdate-shm",
    "./files/media/",
    "./files/user_media/",
    "./files/audio_notes/",
    "./files/camera-captures/",
)


def emulator_serial(value):
    if not re.fullmatch(r"emulator-[0-9]+", value):
        raise argparse.ArgumentTypeError("an explicit emulator-NNNN serial is required")
    return value


def adb(serial, *arguments, timeout=180, input_bytes=None):
    emulator_serial(serial)
    return subprocess.run(
        ["adb", "-s", serial, *arguments], input=input_bytes,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout, check=True,
    ).stdout


def wait_for_package_manager(serial, attempts=60):
    for attempt in range(attempts):
        try:
            adb(serial, "shell", "pm", "list", "packages", timeout=10)
            return
        except subprocess.CalledProcessError as error:
            if attempt + 1 == attempts:
                raise RuntimeError("Android Package Manager did not become ready") from error
            time.sleep(1)


def private_file(path, contents):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(contents)


def overwrite_private_file(path, contents):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(contents)
    os.chmod(path, 0o600)


def package_file_snapshot(serial):
    """Hash restore-critical files without launching the app or exposing their contents."""
    files = (
        "databases/logdate",
        "databases/logdate-wal",
        "files/db_recovery/passphrase_backup",
        "files/identity_recovery/phrase_backup",
        "files/identity_recovery/account_key_backup",
        "files/identity_recovery/media_key_backup",
    )
    snapshot = {}
    for path in files:
        try:
            adb(serial, "shell", "run-as", PACKAGE, "test", "-f", path)
        except subprocess.CalledProcessError:
            snapshot[path] = None
            continue
        contents = adb(serial, "exec-out", "run-as", PACKAGE, "cat", path)
        snapshot[path] = {"bytes": len(contents), "sha256": hashlib.sha256(contents).hexdigest()}
    return snapshot


def package_user_files(serial):
    """List installed-app files, treating an absent package data directory as empty."""
    try:
        return adb(serial, "shell", "run-as", PACKAGE, "find", ".", "-type", "f").splitlines()
    except subprocess.CalledProcessError as error:
        missing_directory = f"couldn't stat /data/user/0/{PACKAGE}: No such file or directory".encode()
        if missing_directory in (error.stderr or b""):
            return []
        raise


def without_portable_recovery_keys(payload):
    """Remove portable keys from LogDate's nested full backup, retaining the transport set."""
    full_backup_name = f"1/_full/{PACKAGE}"
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as source:
        item = next((member for member in source.getmembers() if member.name == full_backup_name), None)
        if item is None:
            raise RuntimeError("native transport did not contain the LogDate full backup")
        full_backup = source.extractfile(item)
        if full_backup is None:
            raise RuntimeError("native transport did not contain the LogDate full backup")
        filtered_full_backup = without_portable_recovery_keys_from_full_backup(full_backup.read())

    # Preserve Android's transport headers. Rebuilding this outer tar can add PAX records
    # that Android's restore reader then parses as ordinary backup entries.
    header = bytearray(payload[item.offset:item.offset + 512])
    header[124:136] = f"{len(filtered_full_backup):011o}\0".encode()
    header[148:156] = b"        "
    checksum = sum(header)
    header[148:156] = f"{checksum:06o}\0 ".encode()
    old_payload_end = item.offset_data + ((item.size + 511) // 512) * 512
    padding = b"\0" * ((-len(filtered_full_backup)) % 512)
    return payload[:item.offset] + bytes(header) + filtered_full_backup + padding + payload[old_payload_end:]


def without_portable_recovery_keys_from_full_backup(payload):
    """Remove only this app's portable keys from Android's full-backup tar."""
    found_database_key = False
    found_identity_key = False
    database_key = f"apps/{PACKAGE}/f/db_recovery/passphrase_backup"
    identity_keys = {
        f"apps/{PACKAGE}/f/identity_recovery/phrase_backup",
        f"apps/{PACKAGE}/f/identity_recovery/account_key_backup",
        f"apps/{PACKAGE}/f/identity_recovery/media_key_backup",
    }
    removed_ranges = []
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as source:
        for item in source.getmembers():
            if item.name == database_key:
                found_database_key = True
            elif item.name in identity_keys:
                found_identity_key = True
            else:
                continue
            end = item.offset_data + ((item.size + 511) // 512) * 512
            removed_ranges.append((item.offset, end))
    if not found_database_key or not found_identity_key:
        raise RuntimeError("LogDate full backup did not contain both expected portable key files")
    output = bytearray()
    cursor = 0
    for start, end in sorted(removed_ranges):
        output.extend(payload[cursor:start])
        cursor = end
    output.extend(payload[cursor:])
    return bytes(output)


def select_local_transport(serial, attempts=30):
    for attempt in range(attempts):
        try:
            transports = adb(serial, "shell", "bmgr", "list", "transports", timeout=60).decode().splitlines()
            if any(line.lstrip("* ").strip() == TRANSPORT for line in transports):
                adb(serial, "shell", "bmgr", "transport", TRANSPORT, timeout=60)
                selected = adb(serial, "shell", "bmgr", "list", "transports", timeout=60).decode().splitlines()
                if any(line.strip() == "* " + TRANSPORT for line in selected):
                    return
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            if attempt + 1 == attempts:
                raise RuntimeError("Android local backup transport could not be selected") from error
        if attempt + 1 < attempts:
            time.sleep(1)
    raise RuntimeError("Android's local backup transport did not become selected")


def prepare(serial, allow_existing=False, allow_preinstalled_empty=False):
    if adb(serial, "shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("target did not identify itself as an emulator")
    wait_for_package_manager(serial)
    existing = adb(serial, "shell", "pm", "list", "packages", PACKAGE).decode().splitlines()
    installed = "package:" + PACKAGE in existing
    if not allow_existing and not allow_preinstalled_empty and installed:
        raise RuntimeError("use a fresh emulator; this harness never clears or uninstalls LogDate")
    if allow_existing and "package:" + PACKAGE not in existing:
        raise RuntimeError("resume requires the synthetic source application to remain installed")
    if allow_preinstalled_empty and installed:
        files = package_user_files(serial)
        content = [
            path.decode()
            for path in files
            if any(path.decode() == root or path.decode().startswith(root) for root in USER_CONTENT_PATHS)
        ]
        if content:
            raise RuntimeError("preinstalled emulator contains journal or media data; use an empty emulator")
    adb(serial, "root")
    adb(serial, "wait-for-device")
    if adb(serial, "shell", "id", "-u").strip() != b"0":
        raise RuntimeError("a rootable Google APIs emulator is required for the local transport")
    adb(serial, "shell", "bmgr", "enable", "true")
    select_local_transport(serial)
    adb(serial, "shell", "settings", "put", "secure", "backup_local_transport_parameters",
        "is_encrypted=true,is_device_transfer=true")


def radio_state(serial):
    airplane = adb(serial, "shell", "settings", "get", "global", "airplane_mode_on").strip() == b"1"
    wifi = adb(serial, "shell", "settings", "get", "global", "wifi_on").strip()
    if wifi not in (b"0", b"1", b"2"):
        raise RuntimeError("Android Wi-Fi state could not be read")
    return airplane, wifi


def timeline_is_open(hierarchy):
    texts = {node.get("text") for node in ET.fromstring(hierarchy).iter("node")}
    return "Search your log" in texts and "Timeline" in texts


def wait_for_timeline(serial, output, timeout_seconds=60):
    deadline = time.monotonic() + timeout_seconds
    ui_path = output / "destination-ui.xml"
    screenshot_path = output / "destination-journal.png"
    while True:
        dumped = adb(serial, "shell", "uiautomator", "dump", "/data/local/tmp/logdate-native-acceptance-ui.xml")
        if b"dumped to:" in dumped:
            hierarchy = adb(serial, "exec-out", "cat", "/data/local/tmp/logdate-native-acceptance-ui.xml")
            screenshot = adb(serial, "exec-out", "screencap", "-p")
            overwrite_private_file(ui_path, hierarchy)
            overwrite_private_file(screenshot_path, screenshot)
            if timeline_is_open(hierarchy):
                return
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            raise RuntimeError("replacement app did not open its Timeline after verifying restored records and media")
        time.sleep(min(1.0, remaining))


def set_offline(serial, offline):
    adb(serial, "shell", "cmd", "connectivity", "airplane-mode", "enable" if offline else "disable")
    adb(serial, "shell", "svc", "wifi", "disable" if offline else "enable")


def restore_radio_state(serial, original):
    airplane, wifi = original
    adb(serial, "shell", "cmd", "connectivity", "airplane-mode", "enable" if airplane else "disable")
    adb(serial, "shell", "svc", "wifi", "disable" if wifi == b"0" else "enable")


def probe(serial, mode, output, metadata=None, identity="phrase"):
    arguments = ["shell", "am", "instrument", "-w", "-r", "-e", "class", PROBE,
                 "-e", "nativeBackupMode", mode, "-e", "nativeIdentityMode", identity]
    if metadata:
        for name, key in (("sourceOwner", "owner"), ("sourceDevice", "device"), ("sourceFingerprint", "fingerprint"), ("sourceMediaCiphertext", "mediaCiphertext")):
            arguments.extend(("-e", name, metadata[key]))
    arguments.append(RUNNER)
    result = adb(serial, *arguments)
    private_file(output, result)
    if b"OK (1 test)" not in result or b"Process crashed" in result:
        raise RuntimeError(f"native {mode} probe failed; inspect {output}")


def archive_probe(serial, mode, output, fixture=None, fixture_label="transfer"):
    arguments = ["am", "instrument", "-w", "-r", "-e", "class",
                 "app.logdate.client.e2e.ArchiveMediaRecoveryE2ETest",
                 "-e", "archiveRecoveryMode", mode,
                 "-e", "archiveRecoveryFixtureLabel", fixture_label]
    if fixture:
        arguments.extend(("-e", "archiveRecoveryFixture", fixture))
    arguments.append(RUNNER)
    result = adb(serial, "shell", shlex.join(arguments))
    private_file(output, result)
    if b"OK (1 test)" not in result or b"Process crashed" in result:
        raise RuntimeError(f"archive {mode} probe failed; inspect {output}")


def transfer(source, destination, app_apk, test_apk, output, identity, exercise_archive_recovery=False,
             exercise_missing_keys=False, seed_source=True):
    if seed_source:
        print("Seeding a synthetic source journal", flush=True)
        adb(source, "install", "-r", "-t", str(app_apk))
        adb(source, "install", "-r", "-t", str(test_apk))
        probe(source, "seed", output / "source-seed.log", identity=identity)
    else:
        marker = f"/data/user/0/{PACKAGE}/no_backup/native-backup-fixture.json"
        adb(source, "shell", "run-as", PACKAGE, "test", "-s", marker)
        print("Resuming the existing synthetic source journal", flush=True)
    # The backup agent's file-based restore mode is a manifest contract. Update a resumed fixture
    # without clearing its synthetic content so the source actually writes that format.
    adb(source, "install", "-r", "-t", str(app_apk))
    if not seed_source:
        adb(source, "install", "-r", "-t", str(test_apk))
        probe(source, "compact-transfer", output / "source-compact-transfer.log")
    raw_metadata = adb(source, "exec-out", "cat", f"/data/user/0/{PACKAGE}/no_backup/native-backup-fixture.json")
    private_file(output / "source-fixture.json", raw_metadata)
    metadata = json.loads(raw_metadata)
    archive_fixture = None
    archive_fixture_label = "transfer-" + uuid.uuid4().hex
    if exercise_archive_recovery:
        archive_probe(source, "seed", output / "archive-source-seed.log", fixture_label=archive_fixture_label)
        archive_fixture = adb(
            source,
            "exec-out",
            "cat",
            f"/data/user/0/{PACKAGE}/no_backup/archive-media-recovery-{archive_fixture_label}.json",
        )
        private_file(output / "archive-source-fixture.json", archive_fixture)
    source_files = package_file_snapshot(source)
    if source_files["databases/logdate"] is None:
        raise RuntimeError("The synthetic source has no database to back up")
    backup = adb(source, "shell", "bmgr", "backupnow", PACKAGE)
    private_file(output / "native-backup.log", backup)
    if f"Package {PACKAGE} with result: Success".encode() not in backup:
        raise RuntimeError("Android did not complete the native backup")
    payload = adb(source, "exec-out", "tar", "-C", TRANSPORT_FILES, "-cf", "-", "1")
    if exercise_missing_keys:
        payload = without_portable_recovery_keys(payload)
    private_file(output / "native-transport.tar", payload)
    print("Restoring through Android's destination backup service", flush=True)
    adb(destination, "push", str(output / "native-transport.tar"), "/data/local/tmp/logdate-native-transport.tar")
    adb(destination, "shell", "tar", "-C", TRANSPORT_FILES, "-xf", "/data/local/tmp/logdate-native-transport.tar")
    adb(destination, "shell", "chown", "-R", "system:system", TRANSPORT_FILES + "/1")
    adb(destination, "shell", "restorecon", "-R", TRANSPORT_FILES + "/1")
    # Some clean destination AVDs already have the debug package installed but
    # have never created app data. Updating keeps that safe test slot reusable.
    adb(destination, "install", "-r", "-t", str(app_apk))
    restored = adb(destination, "shell", "bmgr", "restore", "1", PACKAGE)
    private_file(output / "native-restore.log", restored)
    if b"restoreFinished: 0" not in restored:
        raise RuntimeError("Android did not complete the native restore")
    restored_files = package_file_snapshot(destination)
    private_file(output / "source-files-before-backup.json", json.dumps(source_files, indent=2).encode())
    private_file(output / "destination-files-after-restore.json", json.dumps(restored_files, indent=2).encode())
    if restored_files["databases/logdate"] is None:
        raise RuntimeError("Android completed restore without delivering the journal database")
    if restored_files["databases/logdate"]["sha256"] != source_files["databases/logdate"]["sha256"]:
        raise RuntimeError("Android completed restore, but the destination database differs before app startup")
    print("Launching the restored app through its normal startup path", flush=True)
    launch = adb(destination, "shell", "am", "start", "-W", "-n", PACKAGE + "/app.logdate.client.MainActivity")
    private_file(output / "destination-first-launch.log", launch)
    adb(destination, "install", "-t", str(test_apk))
    if exercise_missing_keys:
        print("Verifying a retained journal remains untouched when portable keys are missing", flush=True)
        probe(destination, "missing-keys", output / "destination-missing-keys.log")
        print("Missing-key recovery state and retained database checks passed", flush=True)
        return
    print("Verifying original media, identity, drafts, and offline editing", flush=True)
    probe(destination, "verify", output / "destination-verify.log", metadata, identity)
    original_radio = radio_state(destination)
    try:
        adb(destination, "shell", "am", "force-stop", PACKAGE)
        set_offline(destination, True)
        probe(destination, "verify", output / "destination-restart.log", metadata, identity)
        if archive_fixture:
            archive_probe(
                destination,
                "repair",
                output / "archive-destination-repair.log",
                archive_fixture.decode(),
                fixture_label=archive_fixture_label,
            )
            adb(destination, "shell", "am", "force-stop", PACKAGE)
            archive_probe(
                destination,
                "verify",
                output / "archive-destination-restart.log",
                fixture_label=archive_fixture_label,
            )
        launch = adb(destination, "shell", "am", "start", "-W", "-n", PACKAGE + "/app.logdate.client.MainActivity")
        private_file(output / "destination-launch.log", launch)
        wait_for_timeline(destination, output)
    finally:
        restore_radio_state(destination, original_radio)
    print("Native transport and offline restart checks passed", flush=True)


def verify_quota(source, output):
    parameters = adb(source, "shell", "settings", "get", "secure", "backup_local_transport_parameters").decode().strip()
    if not parameters or parameters == "null":
        raise RuntimeError("Could not read the original native backup transport settings")
    probe(source, "quota-cleanup", output / "source-quota-cleanup-before.log")
    try:
        probe(source, "quota", output / "source-quota-seed.log")
        adb(source, "shell", "settings", "put", "secure", "backup_local_transport_parameters",
            "is_encrypted=true,is_device_transfer=false")
        result = adb(source, "shell", "bmgr", "backupnow", PACKAGE)
        private_file(output / "source-quota-backup.log", result)
        if b"Size quota exceeded" not in result:
            raise RuntimeError("Android did not reject the oversized cloud backup")
        record = adb(source, "shell", "run-as", PACKAGE, "cat",
                     "no_backup/.logdate_native_backup_quota_exceeded").strip()
        if not re.fullmatch(rb"[0-9]+:[0-9]+", record):
            raise RuntimeError("native quota callback did not persist a valid failure record")
        size, quota = map(int, record.split(b":"))
        if size <= quota:
            raise RuntimeError("quota callback did not report an oversized payload")
        private_file(output / "source-quota-callback.txt", record)
        print("Native quota rejection and callback checks passed", flush=True)
    finally:
        try:
            adb(source, "shell", "settings", "put", "secure", "backup_local_transport_parameters", parameters)
        finally:
            probe(source, "quota-cleanup", output / "source-quota-cleanup-after.log")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", required=True, type=emulator_serial)
    parser.add_argument("--destination", required=True, type=emulator_serial)
    parser.add_argument("--app-apk", required=True, type=Path)
    parser.add_argument("--test-apk", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--identity", choices=("phrase", "account", "legacy-account"), default="phrase")
    parser.add_argument("--exercise-quota", action="store_true", help="also verify native quota rejection on the synthetic source")
    parser.add_argument("--exercise-archive-recovery", action="store_true", help="transfer an edited partial archive and recover its originals on the destination")
    parser.add_argument("--exercise-missing-keys", action="store_true", help="omit portable key files from the native payload and verify safe recovery")
    parser.add_argument("--resume-seeded-source", action="store_true", help="resume from a retained synthetic source fixture without clearing or reseeding app data")
    parser.add_argument("--seed-empty-source", action="store_true", help="seed an installed emulator only when it contains no journal database or media files")
    options = parser.parse_args()
    if options.source == options.destination:
        parser.error("source and destination must be different emulators")
    if options.exercise_missing_keys and options.exercise_archive_recovery:
        parser.error("missing-key transfer cannot run with archive recovery in the same destination fixture")
    if options.resume_seeded_source and not (options.exercise_missing_keys or options.exercise_archive_recovery):
        parser.error("resuming a seeded source requires a recovery acceptance scenario")
    if options.resume_seeded_source and options.seed_empty_source:
        parser.error("choose either a retained synthetic source or an empty installed source")
    if not options.app_apk.is_file() or not options.test_apk.is_file():
        parser.error("both APKs must exist")
    options.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    prepare(
        options.source,
        allow_existing=options.resume_seeded_source,
        allow_preinstalled_empty=options.seed_empty_source,
    )
    prepare(options.destination, allow_preinstalled_empty=True)
    transfer(options.source, options.destination, options.app_apk.resolve(), options.test_apk.resolve(), options.output.resolve(), options.identity, options.exercise_archive_recovery, options.exercise_missing_keys, seed_source=not options.resume_seeded_source)
    if options.exercise_quota:
        verify_quota(options.source, options.output.resolve())


if __name__ == "__main__":
    main()
