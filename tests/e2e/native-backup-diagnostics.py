"""Bounded, private failure evidence; never changes an acceptance result."""

import hashlib
import json
import re

MAX_RECORD = 262144
RECORD = re.compile(r"files/archive_recovery/records/[a-f0-9-]{36}\.json")


def capture_archive_records(h, serial, snapshot):
    result = {}
    paths = sorted(path for path in snapshot if RECORD.fullmatch(path))[:8]
    for path in paths:
        try:
            if snapshot[path].get("bytes", MAX_RECORD + 1) > MAX_RECORD:
                raise ValueError("Recovery diagnostic record exceeds its bound")
            raw = h.adb(
                serial,
                "exec-out",
                "run-as",
                h.PACKAGE,
                "head",
                "-c",
                str(MAX_RECORD + 1),
                path,
                timeout=20,
            )
            if len(raw) > MAX_RECORD:
                raise ValueError("Recovery diagnostic record grew beyond its bound")
            result[path] = {
                "data": json.loads(raw),
                "snapshotSha256": snapshot[path].get("sha256"),
                "readSha256": hashlib.sha256(raw).hexdigest(),
            }
        except Exception as error:
            result[path] = {"diagnosticError": type(error).__name__}
    return result


def _description(value):
    if isinstance(value, bool) or value is None:
        return {"type": type(value).__name__, "value": value}
    raw = json.dumps(value, sort_keys=True).encode()
    return {"type": type(value).__name__, "sha256": hashlib.sha256(raw).hexdigest()}


def _changes(before, after, field=""):
    if isinstance(before, dict) and isinstance(after, dict):
        result = []
        for key in sorted(before.keys() | after.keys()):
            name = (
                key
                if re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,63}", key)
                else hashlib.sha256(key.encode()).hexdigest()
            )
            result += _changes(before.get(key), after.get(key), field + "/" + name)
        return result
    if before == after:
        return []
    return [
        {"field": field, "before": _description(before), "after": _description(after)}
    ]


def record_archive_changes(h, output, before, after):
    if not before and not after:
        return
    records = []
    for path in sorted(before.keys() | after.keys()):
        old, new = before.get(path, {}), after.get(path, {})
        records.append(
            {
                "path": path,
                "beforeAligned": bool(old.get("readSha256"))
                and old.get("snapshotSha256") == old["readSha256"],
                "afterAligned": bool(new.get("readSha256"))
                and new.get("snapshotSha256") == new["readSha256"],
                "changes": _changes(old.get("data"), new.get("data")),
                "readErrors": [
                    item["diagnosticError"]
                    for item in (old, new)
                    if "diagnosticError" in item
                ],
            }
        )
    h.private_file(
        output.with_suffix(".archive-changes.json"),
        json.dumps(records, indent=2).encode(),
    )


def capture_backup_failure(h, serial, output, failure):
    try:
        data = h.adb(
            serial,
            "logcat",
            "-d",
            "-v",
            "threadtime",
            "-s",
            "BackupServiceBinder:D",
            "BackupAgent:D",
            "BackupManagerService:D",
            "AndroidRuntime:E",
            timeout=20,
        )
        h.private_file(output.with_suffix(".agent.log"), data)
    except Exception as error:
        h.add_failure_note(
            failure, "Backup failure diagnostics unavailable: " + type(error).__name__
        )


FUNCTIONS = {
    "capture_archive_records": capture_archive_records,
    "record_archive_changes": record_archive_changes,
    "capture_backup_failure": capture_backup_failure,
}
