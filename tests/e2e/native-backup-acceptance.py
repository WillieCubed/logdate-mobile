#!/usr/bin/env python3
"""Exercise Android's debug backup transport between two fresh, rootable emulators.

Android restores application files from the source transport's payload. Fixtures
cover phrase-derived and account-unlocked encryption identities.
"""

import argparse
import copy
from contextlib import contextmanager
import base64
import hashlib
import io
import json
import os
from pathlib import Path
import re
import runpy
import shlex
import subprocess
import tarfile
import time
import uuid
import xml.etree.ElementTree as ET


# Use the adjacent helper for both standalone and importlib-loaded entry points.
instrumentation_passed = runpy.run_path(
    str(Path(__file__).with_name("native-instrumentation-results.py"))
)["instrumentation_passed"]


managed_adb_command = runpy.run_path(
    str(Path(__file__).with_name("managed-native-target.py"))
)["managed_adb_command"]


PACKAGE = os.environ.get("LOGDATE_NATIVE_ACCEPTANCE_PACKAGE", "studio.hypertext.logdate.debug")
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
RUNTIME_ONLY_SNAPSHOT_PREFIXES = ("files/datastore/FirebaseHeartBeat",)


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


def canonical_preferences(data, allowed_names=None, excluded_names=()):
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
            if name not in RUNTIME_ONLY_PREFERENCES and name not in excluded_names and (allowed_names is None or name in allowed_names):
                entries[name] = entry
        return b"".join(length_delimited_field(1, entry) for _, entry in sorted(entries.items()))
    except (IndexError, UnicodeDecodeError, ValueError) as error:
        if "invalid DataStore preferences" in str(error):
            raise
        raise ValueError("invalid DataStore preferences: malformed preference map") from error


def preference_changes(before, after):
    """Identify unexpected preference writes without logging values or credentials."""
    def entries(data):
        result = {}
        for _, _, _, entry in protobuf_fields(canonical_preferences(data)):
            fields = list(protobuf_fields(entry))
            name = next(payload.decode() for number, wire, _, payload in fields if number == 1 and wire == 2)
            result[name] = entry
        return result
    original, prepared = entries(before), entries(after)
    return {"added": sorted(prepared.keys() - original.keys()),
            "removed": sorted(original.keys() - prepared.keys()),
            "changed": sorted(name for name in original.keys() & prepared.keys() if original[name] != prepared[name])}


def is_user_content_path(path):
    path = path.decode() if isinstance(path, bytes) else path
    return any(path == root or path.startswith(root) for root in USER_CONTENT_PATHS)


def explicit_backup_privacy_defaults(data):
    """Expected first-backup normalization; existing values and other preferences stay intact."""
    normalized = canonical_preferences(data)
    names = {
        payload.decode()
        for _, _, _, entry in protobuf_fields(normalized)
        for number, wire, _, payload in protobuf_fields(entry)
        if number == 1 and wire == 2
    }
    defaults = {"is_onboarded": b"\x08\x00", "security_level": length_delimited_field(5, b"NONE")}
    for name, value in defaults.items():
        if name not in names:
            entry = length_delimited_field(1, name.encode()) + length_delimited_field(2, value)
            normalized += length_delimited_field(1, entry)
    return canonical_preferences(normalized)


def contains_user_content(files):
    return any(is_user_content_path(path) for path in files)


def contains_media_files(files):
    media_roots = ("files/media/", "files/user_media/", "files/audio_notes/", "files/camera-captures/")
    return any(
        (path.decode() if isinstance(path, bytes) else path).removeprefix("./").startswith(media_roots)
        for path in files
    )


def verify_restored_snapshot(restored_files, source_files, *, credentials_only):
    """Validate durable restore contents and report the intended acceptance failure."""
    database_path = "databases/logdate"
    database = restored_files.get(database_path)
    if credentials_only:
        # Application startup can create an empty SQLite database on a fresh installation. The
        # instrumentation probe verifies it contains no source journals or notes; the tar filter
        # separately proves no database entry was transported.
        if restored_files.get("files/db_recovery/passphrase_backup") is None:
            raise RuntimeError("credentials-only restore omitted the journal recovery key")
        identity_files = (
            "files/identity_recovery/phrase_backup",
            "files/identity_recovery/account_key_backup",
        )
        if not any(restored_files.get(path) is not None for path in identity_files):
            raise RuntimeError("credentials-only restore omitted portable identity material")
        if restored_files.get("files/identity_recovery/media_key_backup") is None:
            raise RuntimeError("credentials-only restore omitted the media recovery key")
        if contains_media_files(restored_files):
            raise RuntimeError("credentials-only restore unexpectedly delivered media data")
        return

    if database is None:
        raise RuntimeError("Android completed restore without delivering the journal database")
    source_database = source_files.get(database_path)
    if source_database is None:
        raise RuntimeError("synthetic source snapshot is missing the journal database")
    if database["sha256"] != source_database["sha256"]:
        raise RuntimeError("Android completed restore, but the destination database differs before app startup")


def verify_missing_keys_snapshot(restored_files):
    """An invalid selected bundle must leave no source journal or portable recovery material."""
    if "files/logdate.preferences_pb" not in restored_files:
        raise RuntimeError("missing-key restore did not deliver the source preferences")
    rejected_roots = (
        "databases/logdate",
        "files/db_recovery/",
        "files/identity_recovery/",
        "files/native_journal_transfer/",
    )
    if any(path.startswith(rejected_roots) for path in restored_files):
        raise RuntimeError("missing-key restore exposed an incomplete journal or portable recovery key")
    if contains_media_files(restored_files):
        raise RuntimeError("missing-key restore exposed original media without its recovery keys")


def emulator_serial(value):
    if not re.fullmatch(r"emulator-[0-9]+", value):
        raise argparse.ArgumentTypeError("an explicit emulator-NNNN serial is required")
    return value


def adb(serial, *arguments, timeout=180, input_bytes=None):
    emulator_serial(serial)
    started_ns = time.monotonic_ns()
    try:
        return subprocess.run(
            managed_adb_command(serial, arguments), input=input_bytes,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout, check=True,
        ).stdout
    except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
        failed_ns = time.monotonic_ns()
        stderr = error.stderr or b""
        if isinstance(stderr, bytes):
            stderr = stderr.decode(errors="replace")
        error.add_note(
            f"Managed ADB failure: start_monotonic_ns={started_ns}, failure_monotonic_ns={failed_ns}, "
            f"failure_unix_ns={time.time_ns()}, stderr={stderr[:4096]}"
        )
        raise


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


def assert_snapshot_preserved(before, after, *, require_database=True, ignored_paths=()):
    def is_volatile(path):
        if path in {"databases/logdate-wal", "databases/logdate-shm", "files/logdate.preferences_pb.tmp"} or path.startswith(
            RUNTIME_ONLY_SNAPSHOT_PREFIXES
        ):
            return True
        return path.startswith("files/datastore/firebaseSessions/") and path.endswith(".tmp")

    ignored_paths = set(ignored_paths)
    before_durable = {path: value for path, value in before.items() if not is_volatile(path) and path not in ignored_paths}
    after_durable = {path: value for path, value in after.items() if not is_volatile(path) and path not in ignored_paths}
    if require_database and before.get("databases/logdate") is None:
        raise RuntimeError("snapshot has no journal database to preserve")
    if before_durable != after_durable:
        changed_paths = sorted(
            path
            for path in before_durable.keys() | after_durable.keys()
            if before_durable.get(path) != after_durable.get(path)
        )
        raise RuntimeError("snapshot changed durable data: " + ", ".join(changed_paths))


def enable_backup(serial, attempts=3):
    state = b""
    for attempt in range(attempts):
        adb(serial, "shell", "bmgr", "enable", "true")
        state = adb(serial, "shell", "bmgr", "enabled")
        if b"Backup Manager currently enabled" in state:
            return
        if attempt + 1 < attempts:
            time.sleep(1)
    raise RuntimeError("Android backup service remained disabled after enable request: " + state.decode().strip())


def backup_source(serial, output, *, verify_after_backup=None):
    """Back up a retained fixture and prove Android's backup did not change it."""
    launch = adb(serial, "shell", "am", "start", "-W", "-n", PACKAGE + "/app.logdate.client.MainActivity")
    private_file(output.with_name("source-launch-before-native-backup.log"), launch)
    if b"Status: ok" not in launch:
        if b"Status: timeout" not in launch:
            raise RuntimeError("The source app did not start before native backup")
        wait_for_timeline(serial, output.parent, timeout_seconds=120, prefix="source")
    before = package_file_snapshot(serial)
    preference_path = "files/logdate.preferences_pb"
    original_preferences = adb(serial, "exec-out", "run-as", PACKAGE, "cat", preference_path) if preference_path in before else b""
    if before.get("databases/logdate") is None:
        raise RuntimeError("The synthetic source has no database to back up")
    enable_backup(serial)
    # A managed device can inherit a prior LocalTransport dataset even when its app fixture is
    # fresh. Force a complete package snapshot so the nested archive belongs to this source.
    backup = adb(serial, "shell", "bmgr", "backupnow", "@pm@", PACKAGE, "--non-incremental")
    private_file(output, backup)
    if f"Package {PACKAGE} with result: Success".encode() not in backup:
        try:
            diagnostics = adb(serial, "shell", "dumpsys", "backup", timeout=60)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            diagnostics = f"Backup service diagnostics unavailable: {type(error).__name__}".encode()
        diagnostic_path = output.with_name("backup-platform-diagnostics.log")
        private_file(diagnostic_path, diagnostics)
        raise RuntimeError(f"Android did not complete the native backup; inspect {diagnostic_path}")
    after = package_file_snapshot(serial)
    private_file(output.with_name("source-files-before-native-backup.json"), json.dumps(before, indent=2).encode())
    private_file(output.with_name("source-files-after-native-backup.json"), json.dumps(after, indent=2).encode())
    if before.get(preference_path) != after.get(preference_path):
        prepared_preferences = adb(serial, "exec-out", "run-as", PACKAGE, "cat", preference_path)
        if explicit_backup_privacy_defaults(original_preferences) != canonical_preferences(prepared_preferences):
            private_file(output.with_name("source-preference-changes.json"),
                         json.dumps(preference_changes(original_preferences, prepared_preferences), indent=2).encode())
            raise RuntimeError("native backup changed preferences beyond explicit missing privacy defaults")
        before = {**before, preference_path: after[preference_path]}
    if verify_after_backup is not None:
        adb(serial, "shell", "am", "force-stop", PACKAGE)
        verify_after_backup()
    assert_snapshot_preserved(
        before,
        after,
        # SQLCipher may checkpoint the live WAL while the agent selects an encrypted database
        # snapshot. The post-backup probe verifies the source journal and original media logically.
        ignored_paths={"shared_prefs/logdate_secure_storage.xml", "databases/logdate"}
        if verify_after_backup is not None else (),
    )
    return backup, before


def package_user_files(serial, package=PACKAGE):
    """List installed-app files, treating an absent package data directory as empty."""
    try:
        return adb(serial, "shell", "run-as", package, "find", ".", "-type", "f").splitlines()
    except subprocess.CalledProcessError as error:
        missing_directory = f"couldn't stat /data/user/0/{package}: No such file or directory".encode()
        if missing_directory in (error.stderr or b""):
            return []
        raise


def without_portable_recovery_keys(payload):
    """Remove portable keys from LogDate's nested full backup, retaining the transport set."""
    return rewrite_native_full_backup(payload, without_portable_recovery_keys_from_full_backup)


def credentials_only(payload):
    """Keep LogDate's portable recovery keys while removing journal and media data."""
    return rewrite_native_full_backup(payload, credentials_only_from_full_backup)


def without_privacy_metadata(payload):
    """Retain owner, drafts, keys, and content while omitting only the original privacy policy."""
    preference_path = f"apps/{PACKAGE}/f/logdate.preferences_pb"

    def remove_policy(full_backup):
        with tarfile.open(fileobj=io.BytesIO(full_backup), mode="r:") as archive:
            preferences = archive.extractfile(preference_path)
            if preferences is None:
                raise RuntimeError("native fixture is missing privacy metadata")
            original = canonical_preferences(preferences.read())
            filtered = canonical_preferences(original, excluded_names={"is_onboarded", "security_level"})
            if filtered == original:
                raise RuntimeError("native fixture never contained privacy metadata")
            paths = {member.name for member in archive.getmembers()}
        return rewrite_app_backup(full_backup, paths, {preference_path: filtered})

    return rewrite_native_full_backup(payload, remove_policy)


def without_one_native_media_file(payload):
    """Remove one original from the nested Android full backup and return its app path."""
    removed = []
    prefix = f"apps/{PACKAGE}/f/"

    def remove_one(full_backup):
        with tarfile.open(fileobj=io.BytesIO(full_backup), mode="r:") as archive:
            media = next(
                (
                    member
                    for member in archive.getmembers()
                    if member.isfile()
                    and member.name.startswith(prefix + "media/objects/")
                    and member.name.lower().endswith(".png")
                ),
                None,
            )
        if media is None:
            raise RuntimeError("native source backup did not contain the PNG original used by the image note")
        start = media.offset
        end = media.offset_data + ((media.size + 511) // 512) * 512
        removed.append("files/" + media.name.removeprefix(prefix))
        return full_backup[:start] + full_backup[end:]

    rewritten = rewrite_native_full_backup(payload, remove_one)
    if len(removed) != 1:
        raise RuntimeError("native partial-media fixture did not remove exactly one original")
    return rewritten, removed[0]


def rewrite_native_full_backup(payload, transform):
    """Replace the nested app archive while preserving Android's legacy outer tar headers."""
    full_backup_name = f"1/_full/{PACKAGE}"
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as source:
        item = next((member for member in source.getmembers() if member.name == full_backup_name), None)
        if item is None:
            raise RuntimeError("native transport did not contain the LogDate full backup")
        full_backup = source.extractfile(item)
        if full_backup is None:
            raise RuntimeError("native transport did not contain the LogDate full backup")
        filtered_full_backup = transform(full_backup.read())

    # Keep the legacy outer transport headers intact; Android parses these as backup records.
    header = bytearray(payload[item.offset:item.offset + 512])
    header[124:136] = f"{len(filtered_full_backup):011o}\0".encode()
    header[148:156] = b"        "
    header[148:156] = f"{sum(header):06o}\0 ".encode()
    old_payload_end = item.offset_data + ((item.size + 511) // 512) * 512
    padding = b"\0" * ((-len(filtered_full_backup)) % 512)
    return payload[:item.offset] + bytes(header) + filtered_full_backup + padding + payload[old_payload_end:]


def selected_database_snapshot(payload):
    """Hash the journal file selected by the backup agent, not the live WAL database."""
    full_backup_name = f"1/_full/{PACKAGE}"
    selected_database = f"apps/{PACKAGE}/f/native_journal_transfer/current/database/logdate"
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as transport:
        full_backup_item = next((item for item in transport.getmembers() if item.name == full_backup_name and item.isfile()), None)
        full_backup = transport.extractfile(full_backup_item) if full_backup_item else None
        if full_backup is None:
            raise RuntimeError("native transport did not contain the LogDate full backup")
        with tarfile.open(fileobj=io.BytesIO(full_backup.read()), mode="r:") as app_backup:
            database_item = next((item for item in app_backup.getmembers() if item.name == selected_database and item.isfile()), None)
            database = app_backup.extractfile(database_item) if database_item else None
            if database is None:
                raise RuntimeError("native transport did not contain the selected journal database")
            contents = database.read()
    return {"bytes": len(contents), "sha256": hashlib.sha256(contents).hexdigest()}


def credentials_only_from_full_backup(payload):
    """Model a partial delivery of portable credentials without journal content."""
    app_prefix = f"apps/{PACKAGE}"
    prefix = app_prefix + "/f/"
    database_key = prefix + "db_recovery/passphrase_backup"
    selected_key = prefix + "native_journal_transfer/current/files/db_recovery/passphrase_backup"
    owner_preferences = prefix + "logdate.preferences_pb"
    manifest = app_prefix + "/_manifest"
    identity_keys = {
        prefix + "identity_recovery/phrase_backup",
        prefix + "identity_recovery/account_key_backup",
        prefix + "identity_recovery/media_key_backup",
    }
    retained = {manifest, owner_preferences, *identity_keys}
    retained_directories = {
        app_prefix + "/f",
        prefix + "db_recovery",
        prefix + "identity_recovery",
    }
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as source:
        members = source.getmembers()
        files = {item.name: item for item in members if item.isfile()}
        if manifest not in files:
            raise RuntimeError("credentials-only fixture is missing Android's app backup manifest")
        has_identity_key = any(path in files for path in identity_keys - {prefix + "identity_recovery/media_key_backup"})
        if selected_key not in files or prefix + "identity_recovery/media_key_backup" not in files or not has_identity_key:
            raise RuntimeError("credentials-only fixture is missing required portable recovery keys")
        preferences_item = files.get(owner_preferences)
        preferences = source.extractfile(preferences_item) if preferences_item else None
        if preferences is None:
            raise RuntimeError("credentials-only fixture is missing required portable recovery keys")
        owner = canonical_preferences(
            preferences.read(),
            allowed_names={"identity.canonical_owner_id", "identity.canonical_owner_bound"},
        )
        if not owner:
            raise RuntimeError("credentials-only fixture is missing the canonical journal owner")
        output = io.BytesIO()
        with tarfile.open(fileobj=output, mode="w:", format=tarfile.USTAR_FORMAT) as filtered:
            emitted_directories = set()
            for item in members:
                if item.isdir() and item.name.rstrip("/") in retained_directories and item.name.rstrip("/") not in emitted_directories:
                    filtered.addfile(copy.copy(item))
                    emitted_directories.add(item.name.rstrip("/"))
                elif item.name == selected_key:
                    if prefix + "db_recovery" not in emitted_directories:
                        directory = tarfile.TarInfo(prefix + "db_recovery/")
                        directory.type = tarfile.DIRTYPE
                        directory.mode = 0o700
                        filtered.addfile(directory)
                        emitted_directories.add(prefix + "db_recovery")
                    renamed = copy.copy(item)
                    renamed.name = database_key
                    renamed.pax_headers = {}
                    filtered.addfile(renamed, source.extractfile(item))
                elif item.name in retained:
                    retained_item = copy.copy(item)
                    retained_item.pax_headers = {}
                    if item.name == owner_preferences:
                        retained_item.size = len(owner)
                        filtered.addfile(retained_item, io.BytesIO(owner))
                    else:
                        filtered.addfile(retained_item, source.extractfile(item))
    return output.getvalue()


def rewrite_app_backup(payload, allowed_paths, replacements=None):
    """Retain selected Android app-backup entries, preserving tar metadata and terminators."""
    replacements = replacements or {}
    app_prefix = f"apps/{PACKAGE}"
    output = bytearray()
    cursor = 0
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as source:
        members = source.getmembers()
    for item in members:
        end = item.offset_data + ((item.size + 511) // 512) * 512
        if item.name not in allowed_paths and not (item.isdir() and item.name.startswith(app_prefix + "/")):
            cursor = end
            continue
        output.extend(payload[cursor:item.offset])
        replacement = replacements.get(item.name)
        if replacement is None:
            output.extend(payload[item.offset:end])
        else:
            header = bytearray(payload[item.offset:item.offset_data])
            header[124:136] = f"{len(replacement):011o}\0".encode()
            header[148:156] = b"        "
            header[148:156] = f"{sum(header):06o}\0 ".encode()
            output.extend(header)
            output.extend(replacement)
            output.extend(b"\0" * ((-len(replacement)) % 512))
        cursor = end
    output.extend(payload[cursor:])
    return bytes(output)


def without_portable_recovery_keys_from_full_backup(payload):
    """Remove only this app's portable keys from Android's full-backup tar."""
    found_database_key = False
    found_identity_key = False
    database_key = f"apps/{PACKAGE}/f/native_journal_transfer/current/files/db_recovery/passphrase_backup"
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
    registered = None
    selection = None
    selected = None
    for attempt in range(attempts):
        try:
            registered = adb(serial, "shell", "bmgr", "list", "transports", timeout=60).decode().splitlines()
            if any(line.lstrip("* ").strip() == TRANSPORT for line in registered):
                selection = adb(serial, "shell", "bmgr", "transport", TRANSPORT, timeout=60).decode().strip()
                selected = adb(serial, "shell", "bmgr", "list", "transports", timeout=60).decode().splitlines()
                if any(line.strip() == "* " + TRANSPORT for line in selected):
                    return
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            if attempt + 1 == attempts:
                raise RuntimeError("Android local backup transport could not be selected") from error
        if attempt + 1 < attempts:
            time.sleep(1)
    raise RuntimeError(
        "Android's local backup transport did not become selected; "
        f"registered={registered!r}, selection={selection!r}, selected={selected!r}"
    )


def ensure_emulator_setup_complete(serial):
    setting = ("shell", "settings", "get", "secure", "user_setup_complete")
    if adb(serial, *setting).strip() == b"1":
        return
    adb(serial, "shell", "settings", "put", "secure", "user_setup_complete", "1")
    if adb(serial, *setting).strip() != b"1":
        raise RuntimeError("Android setup is incomplete; native backup is unavailable")


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
    ensure_emulator_setup_complete(serial)
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
    nodes = list(ET.fromstring(hierarchy).iter("node"))
    if any(
        node.get("package") == "android"
        and node.get("resource-id") == "android:id/alertTitle"
        and (node.get("text") or "").endswith("isn't responding")
        for node in nodes
    ):
        return False
    texts = {node.get("text") for node in nodes}
    return {"Search your log", "Timeline", "Text retained through native backup"}.issubset(texts)


def system_ui_unresponsive_dialog_present(hierarchy):
    """Detect known platform ANRs even when their Wait control is absent."""
    try:
        nodes = ET.fromstring(hierarchy).iter("node")
    except ET.ParseError:
        return False
    return any(
        node.get("package") == "android"
        and node.get("resource-id") == "android:id/alertTitle"
        and node.get("text") in {"System UI isn't responding", "Process system isn't responding", "Digital Wellbeing isn't responding"}
        for node in nodes
    )


def system_ui_unresponsive_wait_target(hierarchy):
    """Return Wait only for known platform ANRs; never dismiss a LogDate failure."""
    try:
        nodes = list(ET.fromstring(hierarchy).iter("node"))
    except ET.ParseError:
        return None
    has_system_anr = any(
        node.get("package") == "android"
        and node.get("resource-id") == "android:id/alertTitle"
        and node.get("text") in {"System UI isn't responding", "Process system isn't responding", "Digital Wellbeing isn't responding"}
        for node in nodes
    )
    if not has_system_anr:
        return None
    for node in nodes:
        if node.get("package") != "android" or node.get("resource-id") != "android:id/aerr_wait":
            continue
        bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if bounds is None:
            return None
        left, top, right, bottom = map(int, bounds.groups())
        return ((left + right) // 2, (top + bottom) // 2)
    return None


def bluetooth_crash_dialog_present(hierarchy):
    """Detect Android's unrelated Bluetooth crash dialog even without a close control."""
    try:
        nodes = ET.fromstring(hierarchy).iter("node")
    except ET.ParseError:
        return False
    return any(
        node.get("package") == "android"
        and node.get("resource-id") == "android:id/alertTitle"
        and node.get("text") == "Bluetooth keeps stopping"
        for node in nodes
    )


def bluetooth_crash_dialog_close_target(hierarchy):
    """Return the close-button center only for Android's unrelated Bluetooth crash dialog."""
    try:
        nodes = list(ET.fromstring(hierarchy).iter("node"))
    except ET.ParseError:
        return None
    has_bluetooth_crash = any(
        node.get("package") == "android"
        and node.get("resource-id") == "android:id/alertTitle"
        and node.get("text") == "Bluetooth keeps stopping"
        for node in nodes
    )
    if not has_bluetooth_crash:
        return None
    for node in nodes:
        if node.get("package") != "android" or node.get("resource-id") != "android:id/aerr_close":
            continue
        bounds = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
        if bounds is None:
            return None
        left, top, right, bottom = map(int, bounds.groups())
        return ((left + right) // 2, (top + bottom) // 2)
    return None


def wake_emulator(serial):
    """Make the replacement app visible before asserting its post-restore destination."""
    adb(serial, "shell", "input", "keyevent", "KEYCODE_WAKEUP")
    adb(serial, "shell", "wm", "dismiss-keyguard")


def wait_for_timeline(serial, output, timeout_seconds=60, prefix="destination"):
    wake_emulator(serial)
    deadline = time.monotonic() + timeout_seconds
    ui_path = output / f"{prefix}-ui.xml"
    screenshot_path = output / f"{prefix}-journal.png"
    while True:
        try:
            dumped = adb(serial, "shell", "uiautomator", "dump", "/data/local/tmp/logdate-native-acceptance-ui.xml")
        except subprocess.CalledProcessError as error:
            if error.returncode != 137:
                raise
            print("Android killed the UI inspector; rechecking within the existing Timeline deadline", flush=True)
            dumped = b""
        if b"dumped to:" in dumped:
            hierarchy = adb(serial, "exec-out", "cat", "/data/local/tmp/logdate-native-acceptance-ui.xml")
            screenshot = adb(serial, "exec-out", "screencap", "-p")
            overwrite_private_file(ui_path, hierarchy)
            overwrite_private_file(screenshot_path, screenshot)
            wait_target = system_ui_unresponsive_wait_target(hierarchy)
            if wait_target is not None:
                print("Waiting for an Android platform app to recover over the restored Timeline", flush=True)
                adb(serial, "shell", "input", "tap", str(wait_target[0]), str(wait_target[1]))
                time.sleep(0.25)
            elif system_ui_unresponsive_dialog_present(hierarchy):
                pass
            elif bluetooth_crash_dialog_present(hierarchy):
                close_target = bluetooth_crash_dialog_close_target(hierarchy)
                if close_target is not None:
                    print("Dismissing Android's unrelated Bluetooth crash dialog over the restored Timeline", flush=True)
                    adb(serial, "shell", "input", "tap", str(close_target[0]), str(close_target[1]))
                    time.sleep(0.25)
            elif timeline_is_open(hierarchy):
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


def probe(serial, mode, output, metadata=None, identity="phrase", missing_media_path=None, source_has_session=False, source_session_id=None, installation_trust=False, recording_transport="cloud"):
    arguments = ["shell", "am", "instrument", "-w", "-r", "-e", "class", PROBE,
                 "-e", "nativeBackupMode", mode, "-e", "nativeIdentityMode", identity]
    if mode == "seed" and installation_trust is True:
        arguments.extend(("-e", "nativeInstallationTrustFixture", "true"))
    if metadata:
        for name, key in (("sourceOwner", "owner"), ("sourceDevice", "device"), ("sourceFingerprint", "fingerprint"), ("sourceMediaNonce", "mediaNonce"), ("sourceMediaCiphertext", "mediaCiphertext")):
            value = metadata.get(key)
            if key == "mediaNonce" and not value:
                value = base64.b64encode(bytes(12)).decode()
            if value is None:
                raise ValueError(f"existing source fixture is missing {key}")
            arguments.extend(("-e", name, value))
        if metadata.get("recordingTransferFixture"):
            if recording_transport not in ("cloud", "d2d"):
                raise ValueError("recording probe requires a known Android backup transport")
            arguments.extend(("-e", "sourceRecordingTransferFixture", "true"))
            arguments.extend(("-e", "sourceRecordingTransport", recording_transport))
        if metadata.get("installationTrustFixture") is True:
            arguments.extend(("-e", "sourceInstallationTrustFixture", "true"))
    if mode == "source-backup-integrity":
        arguments.extend(("-e", "sourceHasSession", "true" if source_has_session else "false"))
    if source_session_id is not None:
        arguments.extend(("-e", "sourceSessionId", source_session_id))
    if missing_media_path:
        arguments.extend(("-e", "expectedMissingMediaPath", missing_media_path))
    arguments.append(RUNNER)
    result = adb(serial, *arguments)
    private_file(output, result)
    if not instrumentation_passed(result):
        failure = RuntimeError(f"native {mode} probe failed; inspect {output}")
        try:
            private_file(output.with_suffix(".logcat.log"), adb(serial, "logcat", "-d", "-b", "crash", "-v", "threadtime"))
        except Exception as error:
            failure.add_note(f"Crash diagnostics could not be collected: {error}")
        raise failure


def verify_restarted_content(serial, output, metadata, identity):
    arguments = ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                 "app.logdate.client.e2e.NativeRetainedOfflineEditProbeTest",
                 "-e", "nativeRetainedEdit", "true"]
    if "archiveJournalIds" in metadata:
        journals = metadata["archiveJournalIds"]
        if not isinstance(journals, list) or len(journals) != 2 or not all(isinstance(value, str) for value in journals):
            raise ValueError("Archive restart requires exactly two seeded journal IDs")
        journals = [str(uuid.UUID(value)) for value in journals]
        base = {"00000000-0000-0000-0000-000000000001", "00000000-0000-0000-0000-000000000002"}
        if len(set(journals)) != 2 or base.intersection(journals):
            raise ValueError("Archive restart journal IDs must be distinct from the transfer fixture")
        arguments.extend(("-e", "nativeRetainedArchiveJournals", ",".join(journals)))
    result = adb(serial, *arguments, RUNNER)
    log = output / "destination-retained-offline-edit.log"
    private_file(log, result)
    if not instrumentation_passed(result):
        raise RuntimeError(f"The retained offline edit did not pass read-only verification; inspect {log}")
    probe(serial, "verify", output / "destination-restart.log", metadata, identity)


def privacy_probe(serial, mode, output):
    result = adb(serial, "shell", "am", "instrument", "-w", "-r", "-e", "class",
                 "app.logdate.client.e2e.NativeRestorePrivacyRuntimeTest",
                 "-e", "restorePrivacyMode", mode, RUNNER)
    private_file(output, result)
    if not instrumentation_passed(result):
        raise RuntimeError(f"native privacy {mode} probe failed; inspect {output}")


def verify_privacy_backup_refusal(result, diagnostics, started_at):
    expected = f"Package {PACKAGE} with result: Transport rejected package because it wasn't able to process it at the time"
    if expected not in result.decode().splitlines():
        raise RuntimeError("Android did not report the expected package backup rejection")
    agents = {}
    for line in diagnostics.decode(errors="replace").splitlines():
        match = re.match(r"\s*(\d+\.\d+)\s+(\d+)\s+\d+\s+[VDIWEF]\s+BackupServiceBinder\s*:\s*(.*)", line)
        if match and float(match[1]) >= started_at:
            agents.setdefault(match[2], []).append(match[3])
    if not any(
        "onFullBackup" in "\n".join(lines)
        and "app.logdate.client.data.restore.NativeAccountBackupAgent" in "\n".join(lines)
        and "NativeRestorePrivacyMetadataRequiredException" in "\n".join(lines)
        and "NativeRestorePrivacyPolicy$prepareBackup" in "\n".join(lines)
        for lines in agents.values()
    ):
        raise RuntimeError("The current backup did not reach the agent's missing-privacy refusal")


def runtime_permission_grant(package_state, permission):
    requested = re.search(
        rf"^\s*{re.escape(permission)}\s*$",
        package_state,
        re.MULTILINE,
    )
    if requested is None:
        raise RuntimeError(f"Android did not report the runtime grant for {permission}")
    state = re.search(
        rf"^\s*{re.escape(permission)}: granted=(true|false)(?:, flags=\[[^\]]*\])?\s*$",
        package_state,
        re.MULTILINE,
    )
    if state is None:
        if re.search(rf"^\s*{re.escape(permission)}:", package_state, re.MULTILINE):
            raise RuntimeError(f"Android reported an invalid runtime grant for {permission}")
        return False
    return state.group(1) == "true"


def recording_policy_probe(serial, output):
    permissions = ["android.permission.RECORD_AUDIO"]
    sdk = int(adb(serial, "shell", "getprop", "ro.build.version.sdk").strip())
    if sdk >= 33:
        permissions.append("android.permission.POST_NOTIFICATIONS")
    original = {}
    package_state = adb(serial, "shell", "dumpsys", "package", PACKAGE).decode()
    for permission in permissions:
        original[permission] = runtime_permission_grant(package_state, permission)
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
                current_grant = runtime_permission_grant(current_state, permission)
                if current_grant != was_granted:
                    action = "grant" if was_granted else "revoke"
                    adb(serial, "shell", "pm", action, "--user", "0", PACKAGE, permission)
                restored_state = adb(serial, "shell", "dumpsys", "package", PACKAGE).decode()
                if runtime_permission_grant(restored_state, permission) != was_granted:
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
    if not instrumentation_passed(result):
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
            print("Reading the retained synthetic source fixture without launching the app", flush=True)
        else:
            print("Resuming the existing synthetic source journal", flush=True)
        marker = f"/data/user/0/{PACKAGE}/no_backup/native-backup-fixture.json"
        adb(serial, "shell", "run-as", PACKAGE, "test", "-s", marker)
    # The backup agent's file-based restore mode is a manifest contract. Update a resumed fixture
    # without clearing its synthetic content so the source actually writes that format.
    if not seed_source:
        adb(serial, "install", "-r", "-t", str(app_apk))
        adb(serial, "install", "-r", "-t", str(test_apk))
        if snapshot_source:
            source_files = set(package_user_files(serial))
            recovery_prefix = b"./files/"
            identity_backup = {
                "phrase": b"identity_recovery/phrase_backup",
                "account": b"identity_recovery/account_key_backup",
                "legacy-account": b"identity_recovery/account_key_backup",
            }[identity]
            required_source_files = {
                recovery_prefix + b"db_recovery/passphrase_backup",
                recovery_prefix + identity_backup,
                recovery_prefix + b"identity_recovery/media_key_backup",
            }
            missing_source_files = required_source_files - source_files
            if missing_source_files:
                raise RuntimeError(
                    "The retained synthetic source is missing portable recovery keys: "
                    + ", ".join(path.decode() for path in sorted(missing_source_files))
                )
            raw_fixture = adb(serial, "exec-out", "cat", marker)
            try:
                metadata = json.loads(raw_fixture)
            except (UnicodeDecodeError, json.JSONDecodeError) as error:
                raise RuntimeError("The retained synthetic source fixture is invalid") from error
            required = {"owner", "device", "fingerprint", "mediaCiphertext"}
            if not isinstance(metadata, dict) or not required.issubset(metadata):
                raise RuntimeError("The retained synthetic source fixture is incomplete")
            if not all(metadata[key] for key in required):
                raise RuntimeError("The retained synthetic source fixture is incomplete")
            private_file(output / "source-existing-fixture.json", raw_fixture)
        else:
            probe(serial, "compact-transfer", output / "source-compact-transfer.log")


@contextmanager
def data_only_source_session(source, output, enabled):
    if not enabled:
        yield None
        return
    session_id = str(uuid.uuid4())
    original_radio = radio_state(source)
    primary_error = None
    try:
        # The synthetic session has no hosted account. Its draft adoption must finish
        # before the source snapshot, while network work stays queued for real sign-in.
        set_offline(source, True)
        probe(source, "source-account-session", output / "source-account-session.log", source_session_id=session_id)
        yield session_id
    except BaseException as error:
        primary_error = error
        raise
    finally:
        cleanup_errors = []
        actions = (
            lambda: probe(source, "source-account-session-cleanup", output / "source-account-session-cleanup.log", source_session_id=session_id),
            lambda: adb(source, "shell", "am", "force-stop", PACKAGE),
            lambda: restore_radio_state(source, original_radio),
        )
        for action in actions:
            try:
                action()
            except Exception as error:
                cleanup_errors.append(error)
        if cleanup_errors:
            if primary_error is None:
                raise RuntimeError("Synthetic source session or network cleanup failed") from cleanup_errors[0]
            primary_error.add_note("Synthetic source cleanup also failed: " + ", ".join(type(error).__name__ for error in cleanup_errors))


def restore_transport(serial, *, system_widgets=False):
    if system_widgets:
        # A filtered single-package restore never finishes Android's system widget restore.
        # Restore-all is safe here only when the *actual* dataset holds our synthetic app.
        payload = adb(serial, "exec-out", "tar", "-C", TRANSPORT_FILES, "-cf", "-", "1")
        validate_widget_restore_dataset(payload)
    arguments = () if system_widgets else (PACKAGE,)
    return adb(serial, "shell", "bmgr", "restore", "1", *arguments)


def validate_widget_restore_dataset(payload):
    found_app = False
    found_metadata = False
    with tarfile.open(fileobj=io.BytesIO(payload), mode="r:") as archive:
        for item in archive:
            name = item.name.removeprefix("./").rstrip("/")
            if item.issym() or item.islnk() or any(part in {"", ".", ".."} for part in name.split("/")):
                raise RuntimeError("System widget restore requires an isolated LogDate dataset")
            if item.isdir() and name in {"1", "1/_full", "1/_delta", "1/_delta/@pm@"}:
                continue
            if item.isfile() and name == f"1/_full/{PACKAGE}":
                if found_app:
                    raise RuntimeError("System widget restore contains duplicate app payloads")
                found_app = True
            elif item.isfile() and name.startswith("1/_delta/@pm@/") and len(name.split("/")) == 4:
                found_metadata = True
                continue
            else:
                raise RuntimeError("System widget restore contains an unrelated payload")
    if not found_app:
        raise RuntimeError("System widget restore is missing the LogDate payload")
    if not found_metadata:
        raise RuntimeError("System widget restore is missing package metadata")


def capture_widget_restore_delivery(serial, output):
    private_file(output.with_suffix(".system.log"), adb(serial, "logcat", "-b", "all", "-d", "-v", "threadtime"))
    for service in ("appwidget", "activity broadcasts", "package " + PACKAGE):
        private_file(output.with_suffix("." + service.split()[0] + ".txt"), adb(serial, "shell", "dumpsys", *service.split()))


def transfer(source, destination, app_apk, test_apk, output, identity, exercise_archive_recovery=False,
             exercise_missing_keys=False, exercise_credentials_only=False, seed_source=True, snapshot_source=False,
             exercise_duplicate_restore=False, exercise_partial_media=False, exercise_data_only=False,
             exercise_missing_privacy=False, exercise_system_widgets=False):
    prepare_source(
        source,
        app_apk,
        test_apk,
        output,
        identity,
        seed_source,
        snapshot_source,
    )
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
        archive_state = json.loads(archive_fixture)
        metadata["archiveJournalIds"] = [archive_state["journal"], archive_state["extraJournal"]]
    with data_only_source_session(source, output, exercise_data_only) as source_session_id:
        validate_source_integrity = lambda: probe(
            source,
            "source-backup-integrity",
            output / "source-backup-integrity.log",
            metadata,
            identity,
            source_has_session=exercise_data_only,
            source_session_id=source_session_id,
        )
        backup, source_files = backup_source(
            source,
            output / "native-backup.log",
            verify_after_backup=validate_source_integrity,
        )
    payload = adb(source, "exec-out", "tar", "-C", TRANSPORT_FILES, "-cf", "-", "1")
    original_payload = payload
    private_file(output / "native-transport-original.tar", original_payload)
    selected_source_files = {**source_files, "databases/logdate": selected_database_snapshot(original_payload)}
    missing_media_path = None
    if exercise_missing_keys:
        payload = without_portable_recovery_keys(payload)
    elif exercise_credentials_only:
        payload = credentials_only(payload)
    elif exercise_partial_media:
        payload, missing_media_path = without_one_native_media_file(payload)
        private_file(output / "omitted-native-media-path.txt", missing_media_path.encode())
    elif exercise_missing_privacy:
        payload = without_privacy_metadata(payload)
    private_file(output / "native-transport.tar", payload)
    print("Restoring through Android's destination backup service", flush=True)
    adb(destination, "push", str(output / "native-transport.tar"), "/data/local/tmp/logdate-native-transport.tar")
    adb(destination, "shell", "tar", "-C", TRANSPORT_FILES, "-xf", "/data/local/tmp/logdate-native-transport.tar")
    adb(destination, "shell", "chown", "-R", "system:system", TRANSPORT_FILES + "/1")
    adb(destination, "shell", "restorecon", "-R", TRANSPORT_FILES + "/1")
    # Some clean destination AVDs already have the debug package installed but
    # have never created app data. Updating keeps that safe test slot reusable.
    adb(destination, "install", "-r", "-t", str(app_apk))
    restored = restore_transport(destination, system_widgets=exercise_system_widgets)
    private_file(output / "native-restore.log", restored)
    if b"restoreFinished: 0" not in restored:
        raise RuntimeError("Android did not complete the native restore")
    if exercise_system_widgets:
        capture_widget_restore_delivery(destination, output / "widget-delivery-initial")
    if exercise_duplicate_restore:
        before_replay = package_file_snapshot(destination)
        replay = restore_transport(destination, system_widgets=exercise_system_widgets)
        private_file(output / "native-restore-replay.log", replay)
        if b"restoreFinished: 0" not in replay:
            raise RuntimeError("Android did not complete the replayed native restore")
        if exercise_system_widgets:
            capture_widget_restore_delivery(destination, output / "widget-delivery-replay")
        assert_snapshot_preserved(
            before_replay,
            package_file_snapshot(destination),
            require_database=not exercise_credentials_only,
            # Widget IDs are newly allocated by each full system restore; the widget
            # acceptance probe checks every retained choice against the real binding.
            ignored_paths={"shared_prefs/logdate_widget_instances.xml"} if exercise_system_widgets else (),
        )
    restored_files = package_file_snapshot(destination)
    private_file(output / "source-files-before-backup.json", json.dumps(source_files, indent=2).encode())
    private_file(output / "destination-files-after-restore.json", json.dumps(restored_files, indent=2).encode())
    if exercise_missing_keys:
        verify_missing_keys_snapshot(restored_files)
    else:
        verify_restored_snapshot(restored_files, selected_source_files, credentials_only=exercise_credentials_only)
    print("Launching the restored app through its normal startup path", flush=True)
    launch = adb(destination, "shell", "am", "start", "-W", "-n", PACKAGE + "/app.logdate.client.MainActivity")
    private_file(output / "destination-first-launch.log", launch)
    adb(destination, "install", "-r", "-t", str(test_apk))
    if exercise_missing_privacy:
        print("Verifying missing privacy metadata blocks every database consumer", flush=True)
        privacy_probe(destination, "blocked", output / "destination-privacy-blocked.log")
        started_at = float(adb(destination, "shell", "date", "+%s.%N").strip())
        refused_backup = adb(destination, "shell", "bmgr", "backupnow", PACKAGE)
        private_file(output / "destination-privacy-backup-refused.log", refused_backup)
        diagnostics = adb(destination, "logcat", "-d", "-v", "epoch", "-s", "BackupServiceBinder:D")
        verify_privacy_backup_refusal(refused_backup, diagnostics, started_at)
        privacy_probe(destination, "blocked", output / "destination-privacy-after-backup.log")
        # Deliver the original source policy through Android, without clearing destination data.
        adb(destination, "shell", "am", "force-stop", PACKAGE)
        private_file(output / "native-transport-complete.tar", original_payload)
        adb(destination, "push", str(output / "native-transport-complete.tar"), "/data/local/tmp/logdate-native-transport.tar")
        adb(destination, "shell", "tar", "-C", TRANSPORT_FILES, "-xf", "/data/local/tmp/logdate-native-transport.tar")
        adb(destination, "shell", "chown", "-R", "system:system", TRANSPORT_FILES + "/1")
        adb(destination, "shell", "restorecon", "-R", TRANSPORT_FILES + "/1")
        repaired = adb(destination, "shell", "bmgr", "restore", "1", PACKAGE)
        private_file(output / "native-restore-complete.log", repaired)
        if b"restoreFinished: 0" not in repaired:
            raise RuntimeError("Android did not finish delivering the original privacy policy")
        verify_restored_snapshot(package_file_snapshot(destination), selected_source_files, credentials_only=False)
        privacy_probe(destination, "resolved", output / "destination-privacy-resolved.log")
        print("Original native preferences restored journal access without replacing its key", flush=True)
        return
    if exercise_missing_keys:
        print("Verifying an incomplete native journal stays unavailable for recovery", flush=True)
        probe(destination, "missing-keys-rejected", output / "destination-missing-keys.log")
        print("Incomplete journal remained unavailable and recovery state persisted", flush=True)
        return
    if exercise_credentials_only:
        print("Verifying restored credentials do not synthesize or replace journal content", flush=True)
        probe(destination, "credentials-only", output / "destination-credentials-only.log", metadata, identity)
        return
    if exercise_partial_media:
        print("Verifying a partial native media delivery remains recoverable", flush=True)
        probe(
            destination,
            "partial-media",
            output / "destination-partial-media.log",
            metadata,
            identity,
            missing_media_path,
        )
        return
    print("Verifying original media, identity, drafts, and offline editing", flush=True)
    verify_mode = "data-only" if exercise_data_only else "verify"
    if exercise_duplicate_restore and metadata.get("installationTrustFixture") is True:
        # Establish only installation trust before the final full database delivery.
        # The ordinary editing probe must run afterward, before the read-only restart oracle.
        probe(destination, "verify-restored", output / "destination-before-trust-replay.log", metadata, identity)
        replay_destination_trust(destination, output, metadata, identity, system_widgets=exercise_system_widgets)
    probe(destination, verify_mode, output / f"destination-{verify_mode}.log", metadata, identity)
    original_radio = radio_state(destination)
    try:
        adb(destination, "shell", "am", "force-stop", PACKAGE)
        set_offline(destination, True)
        verify_restarted_content(destination, output, metadata, identity)
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


def replay_destination_trust(serial, output, metadata, identity, *, system_widgets=False):
    adb(serial, "shell", "am", "force-stop", PACKAGE)
    replay = restore_transport(serial, system_widgets=system_widgets)
    private_file(output / "native-restore-after-negotiation.log", replay)
    if b"restoreFinished: 0" not in replay:
        raise RuntimeError("Android did not complete the post-negotiation native restore")
    if system_widgets:
        capture_widget_restore_delivery(serial, output / "widget-delivery-after-negotiation")
    probe(serial, "verify-restored", output / "destination-trust-after-replay.log", metadata, identity)


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
    parser.add_argument("--exercise-missing-privacy", action="store_true", help="omit privacy metadata, verify protected recovery, then deliver original preferences")
    parser.add_argument("--exercise-credentials-only", action="store_true", help="restore portable recovery keys without journal or media data")
    parser.add_argument("--exercise-data-only", action="store_true", help="verify journal and media restore without an account session")
    parser.add_argument("--exercise-duplicate-restore", action="store_true", help="replay native restore before app launch and verify idempotent durable state")
    parser.add_argument("--exercise-partial-media", action="store_true", help="omit one original from native transport and verify recovery state")
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
        exercise_duplicate_restore=options.exercise_duplicate_restore,
        exercise_partial_media=options.exercise_partial_media,
        exercise_data_only=options.exercise_data_only,
        exercise_missing_privacy=options.exercise_missing_privacy,
    )
    if options.exercise_quota:
        verify_quota(options.source, options.output.resolve())


def validate_options(options, parser):
    if options.source == options.destination:
        parser.error("source and destination must be different emulators")
    filtered_scenarios = [
        options.exercise_missing_keys,
        options.exercise_credentials_only,
        options.exercise_archive_recovery,
        options.exercise_partial_media,
        options.exercise_missing_privacy,
    ]
    if sum(filtered_scenarios) > 1:
        parser.error("choose one restore recovery scenario per destination fixture")
    if options.exercise_data_only and any(filtered_scenarios):
        parser.error("data-only acceptance requires a complete native app-data restore")
    acceptance_scenarios = [*filtered_scenarios, options.exercise_duplicate_restore, options.exercise_data_only]
    if options.resume_seeded_source and not any(acceptance_scenarios):
        parser.error("resuming a seeded source requires a recovery acceptance scenario")
    if options.resume_seeded_source and options.seed_empty_source:
        parser.error("choose either a retained synthetic source or an empty installed source")
    if options.snapshot_existing_source and (options.resume_seeded_source or options.seed_empty_source):
        parser.error("snapshot an existing source separately from resuming a fixture or seeding an empty source")
    if options.snapshot_existing_source and not any(acceptance_scenarios):
        parser.error("snapshotting an existing source requires a recovery acceptance scenario")
    if options.snapshot_existing_source and options.exercise_archive_recovery:
        parser.error("snapshotting an existing source cannot run archive seeding because it mutates the source journal")
    if options.snapshot_existing_source and options.identity == "legacy-account":
        parser.error("legacy-account is a source seeding mode and cannot snapshot an existing source")
    if options.exercise_data_only and options.exercise_credentials_only:
        parser.error("data-only and credentials-only acceptance are distinct restore scenarios")


if __name__ == "__main__":
    main()
