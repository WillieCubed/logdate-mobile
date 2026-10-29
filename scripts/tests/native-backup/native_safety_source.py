"""Native acceptance safety contracts for source."""

import argparse
from pathlib import Path
import tempfile
from unittest.mock import patch

from native_safety_support import HARNESS


class NativeSourceSafety:
    def test_snapshot_source_reads_the_existing_fixture_without_launching_the_app(self):
        marker = f"/data/user/0/{HARNESS.PACKAGE}/no_backup/native-backup-fixture.json"

        def respond_to_adb(_serial, *args, **_kwargs):
            if args == ("shell", "run-as", HARNESS.PACKAGE, "find", ".", "-type", "f"):
                return b"\n".join(
                    (
                        b"./files/db_recovery/passphrase_backup",
                        b"./files/identity_recovery/phrase_backup",
                        b"./files/identity_recovery/media_key_backup",
                    )
                )
            if args == ("exec-out", "cat", marker):
                fixture = {
                    "owner": "owner",
                    "device": "device",
                    "fingerprint": "fingerprint",
                    "mediaNonce": "nonce",
                    "mediaCiphertext": "ciphertext",
                }
                return HARNESS.json.dumps(fixture).encode()
            return b""

        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                HARNESS,
                "adb",
                side_effect=respond_to_adb,
            ) as adb,
            patch.object(
                HARNESS,
                "probe",
            ) as probe,
        ):
            output = Path(temporary) / "output"
            output.mkdir()
            HARNESS.prepare_source(
                "emulator-5576",
                Path(temporary) / "app.apk",
                Path(temporary) / "test.apk",
                output,
                "phrase",
                seed_source=False,
                snapshot_source=True,
            )
            self.assertEqual(
                "owner",
                HARNESS.json.loads(
                    (output / "source-existing-fixture.json").read_text()
                )["owner"],
            )

        installs = [
            call.args[1:3]
            for call in adb.call_args_list
            if call.args[1:2] == ("install",)
        ]
        self.assertEqual(
            [("install", "-r"), ("install", "-r")], [item[:2] for item in installs]
        )
        self.assertIn(
            ("shell", "run-as", HARNESS.PACKAGE, "test", "-s", marker),
            [call.args[1:] for call in adb.call_args_list],
        )
        self.assertIn(
            ("exec-out", "cat", marker), [call.args[1:] for call in adb.call_args_list]
        )
        self.assertIn(
            ("shell", "run-as", HARNESS.PACKAGE, "find", ".", "-type", "f"),
            [call.args[1:] for call in adb.call_args_list],
        )
        probe.assert_not_called()

    def test_snapshot_source_requires_portable_recovery_keys(self):
        marker = f"/data/user/0/{HARNESS.PACKAGE}/no_backup/native-backup-fixture.json"

        def respond_to_adb(_serial, *args, **_kwargs):
            if args == ("shell", "run-as", HARNESS.PACKAGE, "find", ".", "-type", "f"):
                return b"./files/db_recovery/passphrase_backup"
            if args == ("exec-out", "cat", marker):
                return HARNESS.json.dumps(
                    {
                        "owner": "owner",
                        "device": "device",
                        "fingerprint": "fingerprint",
                        "mediaCiphertext": "ciphertext",
                    },
                ).encode()
            return b""

        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                HARNESS,
                "adb",
                side_effect=respond_to_adb,
            ),
        ):
            output = Path(temporary) / "output"
            output.mkdir()
            with self.assertRaisesRegex(RuntimeError, "missing portable recovery keys"):
                HARNESS.prepare_source(
                    "emulator-5576",
                    Path(temporary) / "app.apk",
                    Path(temporary) / "test.apk",
                    output,
                    "phrase",
                    seed_source=False,
                    snapshot_source=True,
                )

    def test_probe_passes_fresh_media_nonce_with_transfer_metadata(self):
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                HARNESS,
                "adb",
                return_value=b"INSTRUMENTATION_STATUS_CODE: 0\n\nOK (1 test)\n",
            ) as adb,
        ):
            HARNESS.probe(
                "emulator-5576",
                "verify",
                Path(temporary) / "verify.log",
                metadata={
                    "mediaNonce": "nonce",
                    "owner": "owner",
                    "device": "device",
                    "fingerprint": "fingerprint",
                    "mediaCiphertext": "ciphertext",
                },
            )

        arguments = adb.call_args.args[1:]
        self.assertIn(
            ("-e", "sourceMediaNonce", "nonce"),
            list(zip(arguments, arguments[1:], arguments[2:])),
        )

    def test_probe_uses_legacy_zero_nonce_when_existing_fixture_has_no_nonce(self):
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                HARNESS,
                "adb",
                return_value=b"INSTRUMENTATION_STATUS_CODE: 0\n\nOK (1 test)\n",
            ) as adb,
        ):
            HARNESS.probe(
                "emulator-5576",
                "verify",
                Path(temporary) / "verify.log",
                metadata={
                    "owner": "owner",
                    "device": "device",
                    "fingerprint": "fingerprint",
                    "mediaCiphertext": "ciphertext",
                },
            )

        arguments = adb.call_args.args[1:]
        self.assertIn(
            ("-e", "sourceMediaNonce", "AAAAAAAAAAAAAAAA"),
            list(zip(arguments, arguments[1:], arguments[2:])),
        )

    def test_snapshot_existing_source_cannot_seed_archive_recovery(self):
        options = argparse.Namespace(
            exercise_cloud_backup=False,
            source="emulator-5576",
            destination="emulator-5584",
            exercise_missing_keys=False,
            exercise_missing_privacy=False,
            exercise_credentials_only=False,
            exercise_archive_recovery=True,
            exercise_partial_media=False,
            exercise_pending_camera_draft=False,
            exercise_data_only=False,
            exercise_duplicate_restore=False,
            resume_seeded_source=False,
            seed_empty_source=False,
            snapshot_existing_source=True,
            identity="phrase",
        )
        with self.assertRaises(SystemExit):
            HARNESS.validate_options(options, argparse.ArgumentParser())

    def test_package_file_snapshot_hashes_all_retained_files_without_returning_contents(
        self,
    ):
        with (
            patch.object(
                HARNESS,
                "adb",
                side_effect=[
                    b"encrypted database",
                    b"wal",
                    b"image",
                    b"\x0a\x0b\x0a\x05theme\x12\x02\x18\x01",
                ],
            ) as adb,
            patch.object(
                HARNESS,
                "package_user_files",
                return_value=[
                    b"./databases/logdate",
                    b"./databases/logdate-wal",
                    b"./files/media/objects/original.jpg",
                    b"./files/logdate.preferences_pb",
                    b"./files/cache/ignored.tmp",
                ],
            ),
        ):
            snapshot = HARNESS.package_file_snapshot("emulator-5582")

        self.assertEqual(
            {
                "bytes": len(b"encrypted database"),
                "sha256": HARNESS.hashlib.sha256(b"encrypted database").hexdigest(),
            },
            snapshot["databases/logdate"],
        )
        self.assertIn("files/media/objects/original.jpg", snapshot)
        self.assertIn("files/logdate.preferences_pb", snapshot)
        self.assertNotIn("files/cache/ignored.tmp", snapshot)
        self.assertEqual(4, adb.call_count)
