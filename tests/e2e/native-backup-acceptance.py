#!/usr/bin/env python3
"""Exercise Android's debug backup transport between two fresh, rootable emulators.

Android restores application files from the source transport's payload. Fixtures
cover phrase-derived and account-unlocked encryption identities.
"""

import argparse
import base64
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
    "./shared_prefs/logdate_secure_storage.xml",
    "./databases/logdate",
    "./databases/logdate-wal",
    "./databases/logdate-shm",
    "./files/media/",
    "./files/user_media/",
    "./files/audio_notes/",
    "./files/camera-captures/",
    "./files/db_recovery/",
    "./files/datastore/",
    "./files/archive_recovery/records/",
    "./files/archive_recovery/sources/",
    "./files/identity_recovery/",
    "./files/logdate.preferences_pb",
    "./shared_prefs/logdate_widget_instances.xml",
)
RUNTIME_ONLY_PREFERENCES = {"android_platform_search_index_generation"}


def read_varint(data, offset):
    value = 0
    shift = 0
    while offset < len(data) and shift < 70:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7F) << shift
        if byte < 0x80:
            return value, offset
        shift += 7
    raise ValueError("invalid DataStore preferences: malformed varint")


def write_varint(value):
    encoded = bytearray()
    while value > 0x7F:
        encoded.append((value & 0x7F) | 0x80)
        value >>= 7
    encoded.append(value)
    return bytes(encoded)


def protobuf_fields(data):
    offset = 0
    while offset < len(data):
        start = offset
        tag, offset = read_varint(data, offset)
        number, wire_type = tag >> 3, tag & 7
        if number == 0:
            raise ValueError("invalid DataStore preferences: field number is zero")
        if wire_type == 0:
            payload, offset = read_varint(data, offset)
        elif wire_type == 1:
            payload = data[offset:offset + 8]
            offset += 8
        elif wire_type == 2:
            size, offset = read_varint(data, offset)
            payload = data[offset:offset + size]
            offset += size
        elif wire_type == 5:
            payload = data[offset:offset + 4]
            offset += 4
        else:
            raise ValueError("invalid DataStore preferences: unsupported wire type")
        if offset > len(data):
            raise ValueError("invalid DataStore preferences: field exceeds file size")
        yield number, wire_type, data[start:offset], payload


def length_delimited_field(number, value):
    return write_varint((number << 3) | 2) + write_varint(len(value)) + value


def validate_preference_value(data):
    fields = list(protobuf_fields(data))
    expected_wire_types = {1: 0, 2: 5, 3: 0, 4: 0, 5: 2, 6: 2, 7: 1, 8: 2}
    if len(fields) != 1:
        raise ValueError("invalid DataStore preferences: value must have exactly one value field")
    number, wire_type, _, payload = fields[0]
    if expected_wire_types.get(number) != wire_type:
        raise ValueError("invalid DataStore preferences: invalid preference value type")
    if number == 5:
        payload.decode("utf-8")
    if number == 8:
        for set_number, set_wire_type, _, set_payload in protobuf_fields(payload):
            if set_number != 1 or set_wire_type != 2:
                raise ValueError("invalid DataStore preferences: invalid string-set value")
            set_payload.decode("utf-8")
    return number


def canonical_preferences(data):
    """Normalize DataStore preference ordering and omit its runtime search-index counter."""
    try:
        entries = {}
        for number, wire_type, encoded, entry in protobuf_fields(data):
            if number != 1 or wire_type != 2:
                raise ValueError("invalid DataStore preferences: unexpected map field")
            entry_fields = list(protobuf_fields(entry))
            name_fields = [payload for field_number, field_wire_type, _, payload in entry_fields if field_number == 1 and field_wire_type == 2]
            values = [payload for field_number, field_wire_type, _, payload in entry_fields if field_number == 2 and field_wire_type == 2]
            if len(entry_fields) != 2 or len(name_fields) != 1 or len(values) != 1:
                raise ValueError("invalid DataStore preferences: missing preference name")
            name = name_fields[0].decode("utf-8")
            value_type = validate_preference_value(values[0])
            if name in entries:
                raise ValueError("invalid DataStore preferences: duplicate preference name")
            if name == "android_platform_search_index_generation" and value_type != 4:
                raise ValueError("invalid DataStore preferences: runtime generation has invalid type")
            if name not in RUNTIME_ONLY_PREFERENCES:
                entries[name] = entry
        return b"".join(length_delimited_field(1, entry) for _, entry in sorted(entries.items()))
    except (IndexError, UnicodeDecodeError, ValueError) as error:
        if "invalid DataStore preferences" in str(error):
            raise
        raise ValueError("invalid DataStore preferences: malformed preference map") from error


def is_user_content_path(path):
    path = path.decode() if isinstance(path, bytes) else path
    return any(path == root or path.startswith(root) for root in USER_CONTENT_PATHS)


def contains_user_content(files):
    return any(is_user_content_path(path) for path in files)


def contains_media_files(files):
    media_roots = ("files/media/", "files/user_media/", "files/audio_notes/", "files/camera-captures/")
    return any(
        (path.decode() if isinstance(path, bytes) else path).removeprefix("./").startswith(media_roots)
        for path in files
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
    """Hash all retained journal, media, preference, and recovery files without exposing contents."""
    snapshot = {}
    for path in package_user_files(serial):
        if not is_user_content_path(path):
            continue
        path = path.decode().removeprefix("./")
        contents = adb(serial, "exec-out", "run-as", PACKAGE, "cat", path)
        if path == "files/logdate.preferences_pb":
            contents = canonical_preferences(contents)
        snapshot[path] = {"bytes": len(contents), "sha256": hashlib.sha256(contents).hexdigest()}
    return snapshot


def assert_snapshot_preserved(before, after):
    volatile_files = {"databases/logdate-wal", "databases/logdate-shm"}
    before_durable = {path: value for path, value in before.items() if path not in volatile_files}
    after_durable = {path: value for path, value in after.items() if path not in volatile_files}
    if before.get("databases/logdate") is None or before_durable != after_durable:
        raise RuntimeError("snapshot changed durable journal, media, preference, or recovery data")


def backup_source(serial, output):
    """Back up a retained fixture and prove Android's backup did not change it."""
    before = package_file_snapshot(serial)
    if before.get("databases/logdate") is None:
        raise RuntimeError("The synthetic source has no database to back up")
    backup = adb(serial, "shell", "bmgr", "backupnow", PACKAGE)
    private_file(output, backup)
    if f"Package {PACKAGE} with result: Success".encode() not in backup:
        raise RuntimeError("Android did not complete the native backup")
    assert_snapshot_preserved(before, package_file_snapshot(serial))
    return backup, before


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


def credentials_only(payload):
    """Keep LogDate's portable recovery keys while removing journal and media data."""
    full_backup_name = f"1/_full/{PACKAGE}"
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as source:
        item = next((member for member in source.getmembers() if member.name == full_backup_name), None)
        if item is None:
            raise RuntimeError("native transport did not contain the LogDate full backup")
        full_backup = source.extractfile(item)
        if full_backup is None:
            raise RuntimeError("native transport did not contain the LogDate full backup")
        filtered_full_backup = credentials_only_from_full_backup(full_backup.read())

    # Keep the legacy outer transport headers intact; Android parses these as backup records.
    header = bytearray(payload[item.offset:item.offset + 512])
    header[124:136] = f"{len(filtered_full_backup):011o}\0".encode()
    header[148:156] = b"        "
    header[148:156] = f"{sum(header):06o}\0 ".encode()
    old_payload_end = item.offset_data + ((item.size + 511) // 512) * 512
    padding = b"\0" * ((-len(filtered_full_backup)) % 512)
    return payload[:item.offset] + bytes(header) + filtered_full_backup + padding + payload[old_payload_end:]


def credentials_only_from_full_backup(payload):
    """Keep recovery keys and owner preferences while excluding journal/media content."""
    prefix = f"apps/{PACKAGE}/f/"
    database_key = prefix + "db_recovery/passphrase_backup"
    owner_preferences = prefix + "logdate.preferences_pb"
    identity_keys = {
        prefix + "identity_recovery/phrase_backup",
        prefix + "identity_recovery/account_key_backup",
        prefix + "identity_recovery/media_key_backup",
    }
    allowed = {database_key, owner_preferences, *identity_keys}
    found = set()
    removed_ranges = []
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as source:
        for item in source.getmembers():
            if item.name in allowed:
                found.add(item.name)
            else:
                end = item.offset_data + ((item.size + 511) // 512) * 512
                removed_ranges.append((item.offset, end))
    has_identity_key = bool(found.intersection(identity_keys - {prefix + "identity_recovery/media_key_backup"}))
    required = {database_key, prefix + "identity_recovery/media_key_backup"}
    if not required.issubset(found) or not has_identity_key:
        raise RuntimeError("credentials-only fixture is missing required portable recovery keys")
    output = bytearray()
    cursor = 0
    for start, end in sorted(removed_ranges):
        output.extend(payload[cursor:start])
        cursor = end
    output.extend(payload[cursor:])
    return bytes(output)


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
        if contains_user_content(files):
            raise RuntimeError("preinstalled emulator contains retained data; use an empty emulator")
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
    failures = []
    for label, command in (
        (
            "airplane mode",
            ("shell", "cmd", "connectivity", "airplane-mode", "enable" if airplane else "disable"),
        ),
        ("Wi-Fi", ("shell", "svc", "wifi", "disable" if wifi == b"0" else "enable")),
    ):
        try:
            adb(serial, *command)
        except Exception:
            failures.append(label)

    for _ in range(20):
        try:
            if radio_state(serial) == original:
                return
        except Exception:
            failures.append("radio state readback")
        time.sleep(0.25)
    detail = ", ".join(dict.fromkeys(failures)) or "radio state mismatch"
    raise RuntimeError(f"Could not restore emulator radio state: {detail}")


def probe(serial, mode, output, metadata=None, identity="phrase"):
    arguments = ["shell", "am", "instrument", "-w", "-r", "-e", "class", PROBE,
                 "-e", "nativeBackupMode", mode, "-e", "nativeIdentityMode", identity]
    if metadata:
        for name, key in (("sourceOwner", "owner"), ("sourceDevice", "device"), ("sourceFingerprint", "fingerprint"), ("sourceMediaNonce", "mediaNonce"), ("sourceMediaCiphertext", "mediaCiphertext")):
            value = metadata.get(key)
            if key == "mediaNonce" and not value:
                value = base64.b64encode(bytes(12)).decode()
            if value is None:
                raise ValueError(f"existing source fixture is missing {key}")
            arguments.extend(("-e", name, value))
        if metadata.get("recordingTransferFixture"):
            arguments.extend(("-e", "sourceRecordingTransferFixture", "true"))
    arguments.append(RUNNER)
    result = adb(serial, *arguments)
    private_file(output, result)
    if b"OK (1 test)" not in result or b"Process crashed" in result:
        raise RuntimeError(f"native {mode} probe failed; inspect {output}")


def recording_policy_probe(serial, output):
    permissions = ["android.permission.RECORD_AUDIO"]
    sdk = int(adb(serial, "shell", "getprop", "ro.build.version.sdk").strip())
    if sdk >= 33:
        permissions.append("android.permission.POST_NOTIFICATIONS")
    original = {}
    package_state = adb(serial, "shell", "dumpsys", "package", PACKAGE).decode()
    for permission in permissions:
        state = re.search(rf"^\s*{re.escape(permission)}: granted=(true|false), flags=", package_state, re.MULTILINE)
        if state is None:
            raise RuntimeError(f"Android did not report the runtime grant for {permission}")
        original[permission] = state.group(1) == "true"
    probe_error = None
    try:
        probe(serial, "recording-policy", output)
    except BaseException as error:
        probe_error = error
    finally:
        cleanup_errors = []
        for permission, was_granted in original.items():
            try:
                current_state = adb(serial, "shell", "dumpsys", "package", PACKAGE).decode()
                state = re.search(rf"^\s*{re.escape(permission)}: granted=(true|false), flags=", current_state, re.MULTILINE)
                if state is None:
                    raise RuntimeError(f"Android did not report the runtime grant for {permission} during cleanup")
                if (state.group(1) == "true") != was_granted:
                    action = "grant" if was_granted else "revoke"
                    adb(serial, "shell", "pm", action, "--user", "0", PACKAGE, permission)
                restored_state = adb(serial, "shell", "dumpsys", "package", PACKAGE).decode()
                restored = re.search(rf"^\s*{re.escape(permission)}: granted=(true|false), flags=", restored_state, re.MULTILINE)
                if restored is None or (restored.group(1) == "true") != was_granted:
                    raise RuntimeError(f"Android did not restore the original runtime grant for {permission}")
            except Exception as error:
                cleanup_errors.append(f"{permission}: {error}")
        if probe_error is not None:
            for error in cleanup_errors:
                probe_error.add_note(f"Runtime permission cleanup also failed: {error}")
            raise probe_error
        if cleanup_errors:
            raise RuntimeError("Could not restore recording probe permissions: " + "; ".join(cleanup_errors))


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


def prepare_source(serial, app_apk, test_apk, output, identity, seed_source=True, snapshot_source=False):
    if seed_source:
        print("Seeding a synthetic source journal", flush=True)
        adb(serial, "install", "-r", "-t", str(app_apk))
        adb(serial, "install", "-r", "-t", str(test_apk))
        probe(serial, "seed", output / "source-seed.log", identity=identity)
        recording_policy_probe(serial, output / "source-recording-policy.log")
    else:
        if snapshot_source:
            print("Recording a synthetic source's existing journal fixture", flush=True)
        else:
            marker = f"/data/user/0/{PACKAGE}/no_backup/native-backup-fixture.json"
            adb(serial, "shell", "run-as", PACKAGE, "test", "-s", marker)
            print("Resuming the existing synthetic source journal", flush=True)
    # The backup agent's file-based restore mode is a manifest contract. Update a resumed fixture
    # without clearing its synthetic content so the source actually writes that format.
    if not seed_source:
        adb(serial, "install", "-r", "-t", str(app_apk))
        adb(serial, "install", "-r", "-t", str(test_apk))
        mode = "snapshot" if snapshot_source else "compact-transfer"
        if snapshot_source:
            before = package_file_snapshot(serial)
            probe(serial, mode, output / f"source-{mode}.log", identity=identity)
            assert_snapshot_preserved(before, package_file_snapshot(serial))
        else:
            probe(serial, mode, output / f"source-{mode}.log")


def transfer(source, destination, app_apk, test_apk, output, identity, exercise_archive_recovery=False,
             exercise_missing_keys=False, exercise_credentials_only=False, seed_source=True, snapshot_source=False):
    prepare_source(source, app_apk, test_apk, output, identity, seed_source, snapshot_source)
    # The backup agent's file-based restore mode is a manifest contract. Update a resumed fixture
    # without clearing its synthetic content so the source actually writes that format.
    adb(source, "install", "-r", "-t", str(app_apk))
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
    backup, source_files = backup_source(source, output / "native-backup.log")
    payload = adb(source, "exec-out", "tar", "-C", TRANSPORT_FILES, "-cf", "-", "1")
    if exercise_missing_keys:
        payload = without_portable_recovery_keys(payload)
    elif exercise_credentials_only:
        payload = credentials_only(payload)
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
    if exercise_credentials_only:
        if restored_files["databases/logdate"] is not None:
            raise RuntimeError("credentials-only restore unexpectedly delivered the journal database")
        if restored_files["files/db_recovery/passphrase_backup"] is None:
            raise RuntimeError("credentials-only restore omitted the journal recovery key")
        identity_files = [
            "files/identity_recovery/phrase_backup",
            "files/identity_recovery/account_key_backup",
        ]
        if not any(restored_files[path] is not None for path in identity_files):
            raise RuntimeError("credentials-only restore omitted portable identity material")
        if restored_files["files/identity_recovery/media_key_backup"] is None:
            raise RuntimeError("credentials-only restore omitted the media recovery key")
        if contains_media_files(restored_files):
            raise RuntimeError("credentials-only restore unexpectedly delivered media data")
    else:
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
    if exercise_credentials_only:
        print("Verifying restored credentials do not synthesize or replace journal content", flush=True)
        probe(destination, "credentials-only", output / "destination-credentials-only.log", metadata, identity)
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
    parser.add_argument("--exercise-credentials-only", action="store_true", help="restore portable recovery keys without journal or media data")
    parser.add_argument("--resume-seeded-source", action="store_true", help="resume from a retained synthetic source fixture without clearing or reseeding app data")
    parser.add_argument("--snapshot-existing-source", action="store_true", help="record a synthetic source's existing restore fixture without replacing journal data")
    parser.add_argument("--seed-empty-source", action="store_true", help="seed an installed emulator only when it contains no journal database or media files")
    options = parser.parse_args()
    validate_options(options, parser)
    if not options.app_apk.is_file() or not options.test_apk.is_file():
        parser.error("both APKs must exist")
    options.output.mkdir(mode=0o700, parents=True, exist_ok=False)
    prepare(
        options.source,
        allow_existing=options.resume_seeded_source or options.snapshot_existing_source,
        allow_preinstalled_empty=options.seed_empty_source,
    )
    prepare(options.destination, allow_preinstalled_empty=True)
    transfer(
        options.source,
        options.destination,
        options.app_apk.resolve(),
        options.test_apk.resolve(),
        options.output.resolve(),
        options.identity,
        options.exercise_archive_recovery,
        options.exercise_missing_keys,
        options.exercise_credentials_only,
        seed_source=not (options.resume_seeded_source or options.snapshot_existing_source),
        snapshot_source=options.snapshot_existing_source,
    )
    if options.exercise_quota:
        verify_quota(options.source, options.output.resolve())


def validate_options(options, parser):
    if options.source == options.destination:
        parser.error("source and destination must be different emulators")
    recovery_scenarios = [options.exercise_missing_keys, options.exercise_credentials_only, options.exercise_archive_recovery]
    if sum(recovery_scenarios) > 1:
        parser.error("choose one restore recovery scenario per destination fixture")
    if options.resume_seeded_source and not any(recovery_scenarios):
        parser.error("resuming a seeded source requires a recovery acceptance scenario")
    if options.resume_seeded_source and options.seed_empty_source:
        parser.error("choose either a retained synthetic source or an empty installed source")
    if options.snapshot_existing_source and (options.resume_seeded_source or options.seed_empty_source):
        parser.error("snapshot an existing source separately from resuming a fixture or seeding an empty source")
    if options.snapshot_existing_source and not any(recovery_scenarios):
        parser.error("snapshotting an existing source requires a recovery acceptance scenario")
    if options.snapshot_existing_source and options.exercise_archive_recovery:
        parser.error("snapshotting an existing source cannot run archive seeding because it mutates the source journal")
    if options.snapshot_existing_source and options.identity == "legacy-account":
        parser.error("legacy-account is a source seeding mode and cannot snapshot an existing source")


if __name__ == "__main__":
    main()
