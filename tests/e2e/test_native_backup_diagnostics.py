import json
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from native_backup_test_support import NATIVE_BACKUP_ACCEPTANCE as H


class NativeBackupDiagnosticsTest(unittest.TestCase):
    def test_backup_integrity_failure_keeps_aligned_checkpoint_field_evidence(self):
        path = (
            "files/archive_recovery/records/9c95e470-f773-41f3-bcec-eb53afe37e25.json"
        )
        records = [
            json.dumps({"summary": {"recoveringMedia": value}}).encode()
            for value in (True, False)
        ]
        snapshots = [
            {
                "databases/logdate": {"bytes": 1, "sha256": "journal"},
                path: {"bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()},
            }
            for raw in records
        ]
        pending = iter(records)

        def adb(serial, *args, **kwargs):
            if "head" in args:
                return next(pending)
            if "backupnow" in args:
                return f"Package {H.PACKAGE} with result: Success".encode()
            return b"Status: ok"

        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(H, "adb", side_effect=adb),
            patch.object(H, "enable_backup"),
            patch.object(H, "package_file_snapshot", side_effect=snapshots),
        ):
            output = Path(directory) / "backup.log"
            with self.assertRaisesRegex(RuntimeError, "snapshot changed durable data"):
                H.backup_source("emulator-5554", output)
            evidence = json.loads(
                output.with_suffix(".archive-changes.json").read_text()
            )
            self.assertTrue(evidence[0]["beforeAligned"])
            self.assertTrue(evidence[0]["afterAligned"])
            self.assertEqual(
                "/summary/recoveringMedia", evidence[0]["changes"][0]["field"]
            )

    def test_native_agent_failure_collects_framework_error_before_teardown(self):
        def adb(serial, *args, **kwargs):
            if "backupnow" in args:
                return f"Package {H.PACKAGE} with result: Agent error".encode()
            if "logcat" in args:
                return b"framework agent error"
            return b"Status: ok"

        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(H, "adb", side_effect=adb),
            patch.object(H, "enable_backup"),
            patch.object(
                H,
                "package_file_snapshot",
                return_value={"databases/logdate": {"bytes": 1, "sha256": "journal"}},
            ),
        ):
            output = Path(directory) / "backup.log"
            with self.assertRaisesRegex(
                RuntimeError, "Android did not complete the native backup"
            ):
                H.backup_source("emulator-5554", output)
            self.assertEqual(
                b"framework agent error", output.with_suffix(".agent.log").read_bytes()
            )

    def test_record_reads_are_bounded_and_limited_to_known_recovery_records(self):
        path = (
            "files/archive_recovery/records/9c95e470-f773-41f3-bcec-eb53afe37e25.json"
        )
        value = {"summary": {"recoveringMedia": True}, "sourceLabel": "private label"}
        raw = json.dumps(value).encode()
        digest = hashlib.sha256(raw).hexdigest()
        with patch.object(H, "adb", return_value=raw) as adb:
            result = H.capture_archive_records(
                "emulator-5554",
                {
                    path: {"bytes": 100, "sha256": digest},
                    "files/logdate.preferences_pb": {"bytes": 100},
                    "files/archive_recovery/records/../../unrelated.json": {
                        "bytes": 100
                    },
                },
            )
        self.assertEqual(
            {path: {"data": value, "snapshotSha256": digest, "readSha256": digest}},
            result,
        )
        adb.assert_called_once_with(
            "emulator-5554",
            "exec-out",
            "run-as",
            H.PACKAGE,
            "head",
            "-c",
            "262145",
            path,
            timeout=20,
        )

    def test_changed_field_evidence_never_exposes_string_values_or_waives_integrity(
        self,
    ):
        path = "files/archive_recovery/records/recovery.json"
        before = {
            path: {
                "data": {
                    "summary": {"recoveringMedia": True},
                    "sourceLabel": "old private label",
                }
            }
        }
        after = {
            path: {
                "data": {
                    "summary": {"recoveringMedia": False},
                    "sourceLabel": "new private label",
                }
            }
        }
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "native-backup.log"
            H.record_archive_changes(output, before, after)
            evidence = output.with_suffix(".archive-changes.json")
            raw = evidence.read_text()
            self.assertNotIn("private label", raw)
            self.assertIn("recoveringMedia", raw)
            self.assertIn('"value": true', raw)
            self.assertIn('"value": false', raw)
            self.assertEqual(0o600, evidence.stat().st_mode & 0o777)
        journal = {"databases/logdate": {"bytes": 1, "sha256": "accepted"}}
        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable data"):
            H.assert_snapshot_preserved(
                {**journal, path: {"bytes": 1, "sha256": "old"}},
                {**journal, path: {"bytes": 1, "sha256": "new"}},
            )

    def test_backup_failure_evidence_is_private_and_cannot_replace_the_primary_failure(
        self,
    ):
        failure = RuntimeError("native backup failed")
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "native-backup.log"
            with patch.object(H, "adb", return_value=b"framework failure") as adb:
                H.capture_backup_failure("emulator-5554", output, failure)
            evidence = output.with_suffix(".agent.log")
            self.assertEqual(b"framework failure", evidence.read_bytes())
            self.assertEqual(0o600, evidence.stat().st_mode & 0o777)
            self.assertEqual(20, adb.call_args.kwargs["timeout"])
            self.assertNotIn("run-as", adb.call_args.args)
            with patch.object(H, "adb", side_effect=OSError("unavailable")):
                H.capture_backup_failure("emulator-5554", output, failure)
            self.assertIn("OSError", " ".join(failure.__notes__))


if __name__ == "__main__":
    unittest.main()
