from contextlib import redirect_stderr
import hashlib
import importlib.util
import io
import json
import sys
from pathlib import Path
import runpy
import tarfile
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "native_cloud_recovery", Path(__file__).with_name("native-backup-acceptance.py")
)
NATIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE)
MANAGED = runpy.run_path(
    str(Path(__file__).with_name("managed-native-restore-acceptance.py"))
)


def tar_bytes(entries):
    output = io.BytesIO()
    with tarfile.open(fileobj=output, mode="w") as archive:
        for name, data in entries:
            member = tarfile.TarInfo(name)
            member.size = len(data)
            archive.addfile(member, io.BytesIO(data))
    return output.getvalue()


class NativeCloudRecoveryTest(unittest.TestCase):
    def cloud_entries(self):
        prefix = f"apps/{NATIVE.PACKAGE}"
        return [
            (prefix + "/db/logdate", b"encrypted journal"),
            (prefix + "/f/logdate.preferences_pb", b"owner and draft preferences"),
            (prefix + "/f/db_recovery/passphrase_backup", b"database key"),
            (prefix + "/f/identity_recovery/phrase_backup", b"identity key"),
            (prefix + "/f/identity_recovery/media_key_backup", b"media key"),
        ]

    def transport(self, entries):
        return tar_bytes([(f"1/_full/{NATIVE.PACKAGE}", tar_bytes(entries))])

    def test_cloud_payload_requires_owner_preferences_and_all_recovery_material(self):
        entries = self.cloud_entries()
        NATIVE.assert_cloud_backup_contents(self.transport(entries))
        for missing, _ in entries:
            with (
                self.subTest(missing=missing),
                self.assertRaisesRegex(RuntimeError, "cloud backup"),
            ):
                NATIVE.assert_cloud_backup_contents(
                    self.transport(
                        [(name, data) for name, data in entries if name != missing]
                    )
                )
        account = [
            (
                name.replace(
                    "identity_recovery/phrase_backup",
                    "identity_recovery/account_key_backup",
                ),
                data,
            )
            for name, data in entries
        ]
        NATIVE.assert_cloud_backup_contents(self.transport(account))
        with self.assertRaisesRegex(RuntimeError, "cloud backup"):
            NATIVE.assert_cloud_backup_contents(
                self.transport(
                    [
                        (name, data)
                        for name, data in account
                        if not name.endswith("/identity_recovery/media_key_backup")
                    ]
                )
            )

    def test_cloud_payload_rejects_transfer_only_retained_custody(self):
        entries = self.cloud_entries() + [
            (
                f"apps/{NATIVE.PACKAGE}/f/retained_journals/old/database/logdate",
                b"old journal",
            )
        ]
        with self.assertRaisesRegex(RuntimeError, "cloud backup included"):
            NATIVE.assert_cloud_backup_contents(self.transport(entries))

    def test_complete_cloud_delivery_keeps_database_replays_and_checks_cold_offline_edit(
        self,
    ):
        payload = self.transport(self.cloud_entries())
        snapshot = {
            "databases/logdate": {
                "bytes": len(b"encrypted journal"),
                "sha256": hashlib.sha256(b"encrypted journal").hexdigest(),
            }
        }
        metadata = {
            "owner": "owner",
            "device": "source",
            "fingerprint": "fingerprint",
            "recordingTransferFixture": True,
        }

        def adb(serial, *arguments, **kwargs):
            if arguments[:2] == ("exec-out", "cat"):
                return json.dumps(metadata).encode()
            if arguments[:2] == ("exec-out", "tar"):
                return payload
            return b"Status: ok"

        with (
            tempfile.TemporaryDirectory() as folder,
            patch.object(NATIVE, "adb", side_effect=adb),
            patch.object(NATIVE, "prepare_source"),
            patch.object(NATIVE, "backup_source", return_value=(b"backup", snapshot)),
            patch.object(
                NATIVE, "restore_transport", return_value=b"restoreFinished: 0"
            ) as restore,
            patch.object(NATIVE, "package_file_snapshot", return_value=snapshot),
            patch.object(
                NATIVE,
                "credentials_only",
                side_effect=AssertionError(
                    "Full cloud recovery must not strip the database"
                ),
            ),
            patch.object(NATIVE, "probe") as probe,
            patch.object(NATIVE, "radio_state", return_value=(False, b"1")),
            patch.object(NATIVE, "set_offline") as offline,
            patch.object(NATIVE, "verify_restarted_content") as restarted,
            patch.object(NATIVE, "restore_radio_state"),
            patch.object(NATIVE, "wait_for_timeline"),
        ):
            output = Path(folder)
            NATIVE.transfer(
                "emulator-5554",
                "emulator-5556",
                Path("/app.apk"),
                Path("/probe.apk"),
                output,
                "phrase",
                exercise_cloud_backup=True,
            )
            self.assertEqual(payload, (output / "native-transport.tar").read_bytes())
            self.assertEqual(2, restore.call_count)
            self.assertIn("verify", [call.args[1] for call in probe.call_args_list])
            offline.assert_called_once_with("emulator-5556", True)
            restarted.assert_called_once_with(
                "emulator-5556", output, metadata, "phrase", recording_transport="cloud"
            )

    def test_main_selects_cloud_rules_on_both_devices_without_stripping_journal(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            app, probe = root / "app.apk", root / "probe.apk"
            app.touch()
            probe.touch()
            arguments = [
                "native-backup-acceptance.py",
                "--source",
                "emulator-5554",
                "--destination",
                "emulator-5556",
                "--app-apk",
                str(app),
                "--test-apk",
                str(probe),
                "--output",
                str(root / "results"),
                "--exercise-cloud-backup",
            ]
            with (
                patch.object(sys, "argv", arguments),
                patch.object(NATIVE, "prepare") as prepare,
                patch.object(NATIVE, "transfer") as transfer,
            ):
                NATIVE.main()
            self.assertEqual(
                [True, True],
                [call.kwargs["cloud_backup"] for call in prepare.call_args_list],
            )
            self.assertTrue(transfer.call_args.kwargs["exercise_cloud_backup"])
            self.assertFalse(
                transfer.call_args.args[8],
                "Cloud journal recovery must not become credentials-only",
            )
            for incompatible in (
                "--exercise-credentials-only",
                "--exercise-data-only",
                "--exercise-missing-keys",
            ):
                with (
                    self.subTest(incompatible=incompatible),
                    patch.object(sys, "argv", arguments + [incompatible]),
                    patch.object(NATIVE, "prepare") as prepare,
                    redirect_stderr(io.StringIO()),
                    self.assertRaises(SystemExit),
                ):
                    NATIVE.main()
                prepare.assert_not_called()

    def test_managed_cloud_receipt_does_not_claim_device_transfer_or_google_provider(
        self,
    ):
        arguments = ["--exercise-cloud-backup", "--exercise-duplicate-restore"]
        MANAGED["validate_native_arguments"](arguments)
        receipt = MANAGED["acceptance_receipt"]("transfer", arguments, [])
        self.assertTrue(receipt["localCloudSelectionAccepted"])
        self.assertFalse(receipt["crossDeviceTransferAccepted"])
        self.assertFalse(receipt["googleRestoreProviderAccepted"])
        credentials = MANAGED["acceptance_receipt"](
            "transfer", ["--exercise-credentials-only"], []
        )
        self.assertFalse(credentials["localCloudSelectionAccepted"])
        self.assertFalse(credentials["crossDeviceTransferAccepted"])
