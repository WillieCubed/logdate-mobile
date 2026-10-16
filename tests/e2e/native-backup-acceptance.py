#!/usr/bin/env python3
"""Exercise Android's debug backup transport between two fresh, rootable emulators.

Android restores application files from the source transport's payload. Fixtures
cover phrase-derived and account-unlocked encryption identities.
"""

import argparse
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import xml.etree.ElementTree as ET


PACKAGE = "studio.hypertext.logdate.debug"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
PROBE = "app.logdate.client.e2e.NativeBackupTransferProbeTest"
TRANSPORT = "com.android.localtransport/.LocalTransport"
TRANSPORT_FILES = "/data/user/0/com.android.localtransport/files"


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


def private_file(path, contents):
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "wb") as output:
        output.write(contents)


def prepare(serial):
    if adb(serial, "shell", "getprop", "ro.kernel.qemu").strip() != b"1":
        raise RuntimeError("target did not identify itself as an emulator")
    existing = adb(serial, "shell", "pm", "list", "packages", PACKAGE).decode().splitlines()
    if "package:" + PACKAGE in existing:
        raise RuntimeError("use a fresh emulator; this harness never clears or uninstalls LogDate")
    adb(serial, "root")
    adb(serial, "wait-for-device")
    if adb(serial, "shell", "id", "-u").strip() != b"0":
        raise RuntimeError("a rootable Google APIs emulator is required for the local transport")
    adb(serial, "shell", "bmgr", "enable", "true")
    adb(serial, "shell", "bmgr", "transport", TRANSPORT)
    adb(serial, "shell", "settings", "put", "secure", "backup_local_transport_parameters",
        "is_encrypted=true,is_device_transfer=true")
    adb(serial, "shell", "cmd", "connectivity", "airplane-mode", "enable")
    adb(serial, "shell", "svc", "wifi", "disable")
    adb(serial, "shell", "svc", "data", "disable")


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


def archive_probe(serial, mode, output, fixture=None):
    arguments = ["am", "instrument", "-w", "-r", "-e", "class",
                 "app.logdate.client.e2e.ArchiveMediaRecoveryE2ETest",
                 "-e", "archiveRecoveryMode", mode,
                 "-e", "archiveRecoveryFixtureLabel", "transfer"]
    if fixture:
        arguments.extend(("-e", "archiveRecoveryFixture", fixture))
    arguments.append(RUNNER)
    result = adb(serial, "shell", shlex.join(arguments))
    private_file(output, result)
    if b"OK (1 test)" not in result or b"Process crashed" in result:
        raise RuntimeError(f"archive {mode} probe failed; inspect {output}")


def transfer(source, destination, app_apk, test_apk, output, identity, exercise_archive_recovery=False):
    print("Seeding a synthetic source journal", flush=True)
    adb(source, "install", "-t", str(app_apk))
    adb(source, "install", "-t", str(test_apk))
    probe(source, "seed", output / "source-seed.log", identity=identity)
    raw_metadata = adb(source, "exec-out", "cat", f"/data/user/0/{PACKAGE}/no_backup/native-backup-fixture.json")
    private_file(output / "source-fixture.json", raw_metadata)
    metadata = json.loads(raw_metadata)
    archive_fixture = None
    if exercise_archive_recovery:
        archive_probe(source, "seed", output / "archive-source-seed.log")
        archive_fixture = adb(source, "exec-out", "cat", f"/data/user/0/{PACKAGE}/no_backup/archive-media-recovery-transfer.json")
        private_file(output / "archive-source-fixture.json", archive_fixture)
    backup = adb(source, "shell", "bmgr", "backupnow", PACKAGE)
    private_file(output / "native-backup.log", backup)
    if f"Package {PACKAGE} with result: Success".encode() not in backup:
        raise RuntimeError("Android did not complete the native backup")
    payload = adb(source, "exec-out", "tar", "-C", TRANSPORT_FILES, "-cf", "-", "1")
    private_file(output / "native-transport.tar", payload)
    print("Restoring through Android's destination backup service", flush=True)
    adb(destination, "push", str(output / "native-transport.tar"), "/data/local/tmp/logdate-native-transport.tar")
    adb(destination, "shell", "tar", "-C", TRANSPORT_FILES, "-xf", "/data/local/tmp/logdate-native-transport.tar")
    adb(destination, "shell", "chown", "-R", "system:system", TRANSPORT_FILES + "/1")
    adb(destination, "shell", "restorecon", "-R", TRANSPORT_FILES + "/1")
    adb(destination, "install", "-t", str(app_apk))
    restored = adb(destination, "shell", "bmgr", "restore", "1", PACKAGE)
    private_file(output / "native-restore.log", restored)
    if b"restoreFinished: 0" not in restored:
        raise RuntimeError("Android did not complete the native restore")
    adb(destination, "install", "-t", str(test_apk))
    print("Verifying original media, identity, drafts, and offline editing", flush=True)
    probe(destination, "verify", output / "destination-verify.log", metadata, identity)
    adb(destination, "shell", "am", "force-stop", PACKAGE)
    probe(destination, "verify", output / "destination-restart.log", metadata, identity)
    if archive_fixture:
        archive_probe(destination, "repair", output / "archive-destination-repair.log", archive_fixture.decode())
        adb(destination, "shell", "am", "force-stop", PACKAGE)
        archive_probe(destination, "verify", output / "archive-destination-restart.log")
    launch = adb(destination, "shell", "am", "start", "-W", "-n", PACKAGE + "/app.logdate.client.MainActivity")
    private_file(output / "destination-launch.log", launch)
    for attempt in range(3):
        dumped = adb(destination, "shell", "uiautomator", "dump", "/data/local/tmp/logdate-native-acceptance-ui.xml")
        if b"dumped to:" not in dumped:
            continue
        hierarchy = adb(destination, "exec-out", "cat", "/data/local/tmp/logdate-native-acceptance-ui.xml")
        texts = {node.get("text") for node in ET.fromstring(hierarchy).iter("node")}
        if "Text retained through native backup" in texts:
            break
    else:
        raise RuntimeError("replacement timeline did not display the transferred journal")
    private_file(output / "destination-ui.xml", hierarchy)
    private_file(output / "destination-journal.png", adb(destination, "exec-out", "screencap", "-p"))
    print("Native transport and offline restart checks passed", flush=True)


def verify_quota(source, output):
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
    options = parser.parse_args()
    if options.source == options.destination:
        parser.error("source and destination must be different emulators")
    if not options.app_apk.is_file() or not options.test_apk.is_file():
        parser.error("both APKs must exist")
    options.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    prepare(options.source)
    prepare(options.destination)
    transfer(options.source, options.destination, options.app_apk.resolve(), options.test_apk.resolve(), options.output.resolve(), options.identity, options.exercise_archive_recovery)
    if options.exercise_quota:
        verify_quota(options.source, options.output.resolve())


if __name__ == "__main__":
    main()
