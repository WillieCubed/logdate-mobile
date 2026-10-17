#!/usr/bin/env python3
"""Physical serials must be rejected before the native acceptance harness calls adb."""

import argparse
import importlib.util
import io
from pathlib import Path
import subprocess
import stat
import tarfile
import tempfile
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("native_acceptance", ROOT / "tests/e2e/native-backup-acceptance.py")
HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HARNESS)


class NativeAcceptanceSafetyTest(unittest.TestCase):
    def test_radio_cleanup_attempts_wifi_after_airplane_mode_failure(self):
        original = (False, b"1")
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[subprocess.CalledProcessError(1, "airplane"), b"wifi restored"],
        ) as adb, patch.object(HARNESS, "radio_state", return_value=original):
            HARNESS.restore_radio_state("emulator-5582", original)

        self.assertEqual(2, adb.call_count)
        self.assertEqual(("shell", "svc", "wifi", "enable"), adb.call_args_list[1].args[1:])

    def test_prepare_leaves_network_state_for_the_scenario_to_control(self):
        with patch.object(HARNESS, "adb", side_effect=[b"1", b"", b"", b"", b"0", b"", b""]) as adb, patch.object(
            HARNESS,
            "wait_for_package_manager",
        ), patch.object(HARNESS, "select_local_transport"):
            HARNESS.prepare("emulator-5582")

        self.assertFalse(
            any(call.args[2:4] in (("cmd", "connectivity"), ("svc", "wifi")) for call in adb.call_args_list),
            "Transport preparation must not force a cold app launch to happen offline",
        )

    def test_resume_preparation_requires_an_existing_synthetic_package(self):
        with patch.object(HARNESS, "adb", side_effect=[b"1", b""]) as adb, patch.object(
            HARNESS,
            "wait_for_package_manager",
        ):
            with self.assertRaisesRegex(RuntimeError, "resume requires the synthetic source"):
                HARNESS.prepare("emulator-5582", allow_existing=True)

        self.assertEqual(2, adb.call_count)

    def test_snapshot_source_is_installed_and_snapshotted_without_reseeding_or_compaction(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(HARNESS, "adb") as adb, patch.object(
            HARNESS,
            "probe",
        ) as probe, patch.object(
            HARNESS,
            "package_file_snapshot",
            return_value={"databases/logdate": {"sha256": "stable"}},
        ):
            HARNESS.prepare_source(
                "emulator-5576",
                Path(temporary) / "app.apk",
                Path(temporary) / "test.apk",
                Path(temporary),
                "phrase",
                seed_source=False,
                snapshot_source=True,
            )

        installs = [call.args[1:3] for call in adb.call_args_list if call.args[1:2] == ("install",)]
        self.assertEqual([("install", "-r"), ("install", "-r")], [item[:2] for item in installs])
        self.assertEqual(["snapshot"], [call.args[1] for call in probe.call_args_list])
        self.assertNotIn("seed", [call.args[1] for call in probe.call_args_list])
        self.assertNotIn("compact-transfer", [call.args[1] for call in probe.call_args_list])

    def test_probe_passes_fresh_media_nonce_with_transfer_metadata(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "adb",
            return_value=b"INSTRUMENTATION_STATUS: stream=OK (1 test)",
        ) as adb:
            HARNESS.probe(
                "emulator-5576",
                "verify",
                Path(temporary) / "verify.log",
                metadata={"mediaNonce": "nonce", "owner": "owner", "device": "device", "fingerprint": "fingerprint", "mediaCiphertext": "ciphertext"},
            )

        arguments = adb.call_args.args[1:]
        self.assertIn(("-e", "sourceMediaNonce", "nonce"), list(zip(arguments, arguments[1:], arguments[2:])))

    def test_snapshot_rejects_changes_to_the_existing_journal(self):
        original = {
            "databases/logdate": {"sha256": "before"},
            "files/db_recovery/passphrase_backup": {"sha256": "key"},
            "databases/logdate-wal": {"sha256": "wal-before"},
            "files/media/objects/original.jpg": {"sha256": "media-before"},
            "files/logdate.preferences_pb": {"sha256": "settings-before"},
        }
        unchanged = {**original, "databases/logdate-wal": {"sha256": "wal-after"}}
        changed = {**unchanged, "databases/logdate": {"sha256": "after"}}
        changed_key = {**unchanged, "files/db_recovery/passphrase_backup": {"sha256": "changed-key"}}

        HARNESS.assert_snapshot_preserved(original, unchanged)
        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable journal, media, preference, or recovery data"):
            HARNESS.assert_snapshot_preserved(original, changed)
        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable journal, media, preference, or recovery data"):
            HARNESS.assert_snapshot_preserved(original, changed_key)
        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable journal, media, preference, or recovery data"):
            HARNESS.assert_snapshot_preserved(original, {**unchanged, "files/media/objects/original.jpg": {"sha256": "media-after"}})
        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable journal, media, preference, or recovery data"):
            HARNESS.assert_snapshot_preserved(original, {**unchanged, "files/logdate.preferences_pb": {"sha256": "settings-after"}})

    def test_native_backup_checks_source_data_before_and_after_transport_backup(self):
        retained = {"databases/logdate": {"sha256": "journal"}, "files/media/original.jpg": {"sha256": "media"}}
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "package_file_snapshot",
            side_effect=[retained, retained],
        ) as snapshot, patch.object(
            HARNESS,
            "adb",
            return_value=f"Package {HARNESS.PACKAGE} with result: Success".encode(),
        ) as adb:
            result, before = HARNESS.backup_source("emulator-5576", Path(temporary) / "backup.log")

        self.assertIn(b"with result: Success", result)
        self.assertEqual(retained, before)
        self.assertEqual(2, snapshot.call_count)
        self.assertIn(("shell", "bmgr", "backupnow", HARNESS.PACKAGE), [call.args[1:] for call in adb.call_args_list])

    def test_native_backup_fails_if_source_journal_changes_during_backup(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "package_file_snapshot",
            side_effect=[{"databases/logdate": {"sha256": "before"}}, {"databases/logdate": {"sha256": "after"}}],
        ), patch.object(
            HARNESS,
            "adb",
            return_value=f"Package {HARNESS.PACKAGE} with result: Success".encode(),
        ):
            with self.assertRaisesRegex(RuntimeError, "snapshot changed durable journal"):
                HARNESS.backup_source("emulator-5576", Path(temporary) / "backup.log")

    def test_preference_snapshot_ignores_only_runtime_search_index_generation(self):
        def field(number, value):
            tag = bytes([(number << 3) | 2])
            return tag + bytes([len(value)]) + value

        def preference(name, value):
            value_message = b"\x20\x01" if name == "android_platform_search_index_generation" else field(5, value.encode())
            return field(1, name.encode()) + field(2, value_message)

        before = field(1, preference("theme", "dark")) + field(1, preference("android_platform_search_index_generation", "1"))
        after = field(1, preference("android_platform_search_index_generation", "2")) + field(1, preference("theme", "dark"))
        changed_setting = field(1, preference("android_platform_search_index_generation", "2")) + field(1, preference("theme", "light"))

        self.assertEqual(HARNESS.canonical_preferences(before), HARNESS.canonical_preferences(after))
        self.assertNotEqual(HARNESS.canonical_preferences(before), HARNESS.canonical_preferences(changed_setting))

    def test_preference_snapshot_rejects_malformed_datastore_file(self):
        with self.assertRaisesRegex(ValueError, "invalid DataStore preferences"):
            HARNESS.canonical_preferences(b"\x0a\x80")

    def test_preference_snapshot_rejects_duplicate_keys_and_malformed_values(self):
        def field(number, value):
            return bytes([(number << 3) | 2, len(value)]) + value

        duplicate_key = field(1, b"android_platform_search_index_generation") + field(1, b"theme") + field(2, b"\x20\x01")
        malformed_value = field(1, b"theme") + field(2, b"\x2a\x05x")
        duplicate_value = field(1, b"theme") + field(2, b"\x2a\x01x") + field(2, b"\x2a\x01y")

        for entry in (duplicate_key, malformed_value, duplicate_value):
            with self.subTest(entry=entry), self.assertRaisesRegex(ValueError, "invalid DataStore preferences"):
                HARNESS.canonical_preferences(field(1, entry))

    def test_preinstalled_empty_emulator_can_contain_sdk_state_without_journal_data(self):
        with patch.object(HARNESS, "adb", side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), b"./shared_prefs/com.google.firebase.crashlytics.xml", b"", b"", b"0", b"", b""]) as adb, patch.object(
            HARNESS,
            "wait_for_package_manager",
        ), patch.object(HARNESS, "select_local_transport"):
            HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

        self.assertIn(("shell", "run-as", HARNESS.PACKAGE, "find", ".", "-type", "f"), [call.args[1:] for call in adb.call_args_list])

    def test_preinstalled_emulator_with_journal_or_media_data_is_rejected(self):
        with patch.object(HARNESS, "adb", side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), b"./files/media/objects/retained.jpg"]) as adb, patch.object(
            HARNESS,
            "wait_for_package_manager",
        ):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

        self.assertEqual(3, adb.call_count)

    def test_preinstalled_emulator_with_retained_recovery_keys_is_rejected(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), b"./files/identity_recovery/phrase_backup"],
        ), patch.object(HARNESS, "wait_for_package_manager"):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_preinstalled_emulator_with_backup_managed_recovery_or_widget_data_is_rejected(self):
        retained_paths = (
            b"./files/archive_recovery/records/pending.json",
            b"./files/archive_recovery/sources/source.zip",
            b"./shared_prefs/logdate_widget_instances.xml",
        )
        for retained_path in retained_paths:
            with self.subTest(path=retained_path), patch.object(
                HARNESS,
                "adb",
                side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), retained_path],
            ), patch.object(HARNESS, "wait_for_package_manager"):
                with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                    HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_preinstalled_emulator_with_encrypted_identity_or_session_is_rejected(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"1",
                f"package:{HARNESS.PACKAGE}".encode(),
                b"./shared_prefs/logdate_secure_storage.xml",
            ],
        ), patch.object(HARNESS, "wait_for_package_manager"):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_credentials_only_fixture_keeps_owner_preferences_but_excludes_journal_and_media(self):
        entries = {
            f"apps/{HARNESS.PACKAGE}/f/db_recovery/passphrase_backup": b"database-key",
            f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup": b"identity-key",
            f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup": b"media-key",
            f"apps/{HARNESS.PACKAGE}/f/logdate.preferences_pb": b"owner-preferences",
            f"apps/{HARNESS.PACKAGE}/d/logdate": b"encrypted-journal",
            f"apps/{HARNESS.PACKAGE}/f/media/original.jpg": b"original-media",
        }
        payload = io.BytesIO()
        with HARNESS.tarfile.open(fileobj=payload, mode="w:") as archive:
            for name, contents in entries.items():
                item = HARNESS.tarfile.TarInfo(name)
                item.size = len(contents)
                archive.addfile(item, io.BytesIO(contents))

        filtered = HARNESS.credentials_only_from_full_backup(payload.getvalue())
        with HARNESS.tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as archive:
            retained = {item.name for item in archive.getmembers()}

        self.assertIn(f"apps/{HARNESS.PACKAGE}/f/logdate.preferences_pb", retained)
        self.assertIn(f"apps/{HARNESS.PACKAGE}/f/db_recovery/passphrase_backup", retained)
        self.assertNotIn(f"apps/{HARNESS.PACKAGE}/d/logdate", retained)
        self.assertNotIn(f"apps/{HARNESS.PACKAGE}/f/media/original.jpg", retained)

    def test_preinstalled_emulator_with_retained_datastore_state_is_rejected(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), b"./files/datastore/user_preferences.preferences_pb"],
        ), patch.object(HARNESS, "wait_for_package_manager"):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_preinstalled_emulator_with_root_datastore_preferences_is_rejected(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), b"./files/logdate.preferences_pb"],
        ), patch.object(HARNESS, "wait_for_package_manager"):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_probe_uses_legacy_zero_nonce_when_existing_fixture_has_no_nonce(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "adb",
            return_value=b"INSTRUMENTATION_STATUS: stream=OK (1 test)",
        ) as adb:
            HARNESS.probe(
                "emulator-5576",
                "verify",
                Path(temporary) / "verify.log",
                metadata={"owner": "owner", "device": "device", "fingerprint": "fingerprint", "mediaCiphertext": "ciphertext"},
            )

        arguments = adb.call_args.args[1:]
        self.assertIn(("-e", "sourceMediaNonce", "AAAAAAAAAAAAAAAA"), list(zip(arguments, arguments[1:], arguments[2:])))

    def test_snapshot_existing_source_cannot_seed_archive_recovery(self):
        options = argparse.Namespace(
            source="emulator-5576",
            destination="emulator-5584",
            exercise_missing_keys=False,
            exercise_credentials_only=False,
            exercise_archive_recovery=True,
            resume_seeded_source=False,
            seed_empty_source=False,
            snapshot_existing_source=True,
            identity="phrase",
        )
        with self.assertRaises(SystemExit):
            HARNESS.validate_options(options, argparse.ArgumentParser())

    def test_installed_package_without_user_data_is_an_empty_fixture(self):
        error = subprocess.CalledProcessError(
            1,
            ["adb", "shell", "run-as"],
            stderr=f"run-as: couldn't stat /data/user/0/{HARNESS.PACKAGE}: No such file or directory".encode(),
        )
        with patch.object(HARNESS, "adb", side_effect=error):
            self.assertEqual([], HARNESS.package_user_files("emulator-5582"))

    def test_package_manager_readiness_retries_until_the_android_service_is_available(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[subprocess.CalledProcessError(20, "pm"), b"package:android\n"],
        ) as adb, patch.object(HARNESS.time, "sleep") as sleep:
            HARNESS.wait_for_package_manager("emulator-5582", attempts=2)

        self.assertEqual(2, adb.call_count)
        sleep.assert_called_once_with(1)

    def test_package_manager_readiness_stops_after_its_bounded_attempts(self):
        with patch.object(HARNESS, "adb", side_effect=subprocess.CalledProcessError(20, "pm")) as adb, patch.object(
            HARNESS.time,
            "sleep",
        ):
            with self.assertRaisesRegex(RuntimeError, "Package Manager did not become ready"):
                HARNESS.wait_for_package_manager("emulator-5582", attempts=2)

        self.assertEqual(2, adb.call_count)

    def test_package_file_snapshot_hashes_all_retained_files_without_returning_contents(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[b"encrypted database", b"wal", b"image", b"\x0a\x0b\x0a\x05theme\x12\x02\x18\x01"],
        ) as adb, patch.object(
            HARNESS,
            "package_user_files",
            return_value=[
                b"./databases/logdate",
                b"./databases/logdate-wal",
                b"./files/media/objects/original.jpg",
                b"./files/logdate.preferences_pb",
                b"./files/cache/ignored.tmp",
            ],
        ):
            snapshot = HARNESS.package_file_snapshot("emulator-5582")

        self.assertEqual({"bytes": len(b"encrypted database"), "sha256": HARNESS.hashlib.sha256(b"encrypted database").hexdigest()}, snapshot["databases/logdate"])
        self.assertIn("files/media/objects/original.jpg", snapshot)
        self.assertIn("files/logdate.preferences_pb", snapshot)
        self.assertNotIn("files/cache/ignored.tmp", snapshot)
        self.assertEqual(4, adb.call_count)

    def test_timeline_acceptance_uses_stable_destination_controls(self):
        hierarchy = b'<hierarchy><node text="Search your log"/><node text="Timeline"/></hierarchy>'
        self.assertTrue(HARNESS.timeline_is_open(hierarchy))

    def test_ui_poll_evidence_can_be_updated_without_changing_private_permissions(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "destination-ui.xml"
            HARNESS.private_file(path, b"first poll")
            HARNESS.overwrite_private_file(path, b"latest poll")

            self.assertEqual(b"latest poll", path.read_bytes())
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))

    def test_timeline_wait_keeps_polling_until_restored_content_is_visible(self):
        loading = b'<hierarchy><node text="Loading"/></hierarchy>'
        timeline = b'<hierarchy><node text="Search your log"/><node text="Timeline"/></hierarchy>'
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                loading,
                b"loading image",
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                timeline,
                b"restored journal image",
            ],
        ) as adb, patch.object(HARNESS.time, "sleep"):
            HARNESS.wait_for_timeline("emulator-5582", Path(temporary), timeout_seconds=5)

            self.assertEqual(6, adb.call_count)
            self.assertEqual(timeline, (Path(temporary) / "destination-ui.xml").read_bytes())
            self.assertEqual(b"restored journal image", (Path(temporary) / "destination-journal.png").read_bytes())

    def test_archive_probe_uses_the_supplied_per_run_fixture_label(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "adb",
            return_value=b"INSTRUMENTATION_STATUS: stream=OK (1 test)",
        ) as adb:
            HARNESS.archive_probe(
                "emulator-5582",
                "seed",
                Path(temporary) / "archive-seed.log",
                fixture_label="transfer-unique-run",
            )

        command = adb.call_args.args[2]
        self.assertIn("-e archiveRecoveryFixtureLabel transfer-unique-run", command)

    def test_quota_probe_restores_transport_and_cleans_fixture_after_rejection_failure(self):
        calls = []

        def adb(serial, *arguments, **kwargs):
            calls.append(arguments)
            if arguments[:4] == ("shell", "settings", "get", "secure"):
                return b"is_encrypted=true,is_device_transfer=true"
            if arguments[:3] == ("shell", "bmgr", "backupnow"):
                return b"Backup finished with result: Success"
            return b""

        with tempfile.TemporaryDirectory() as temporary, patch.object(HARNESS, "adb", side_effect=adb), patch.object(
            HARNESS,
            "probe",
        ) as probe:
            with self.assertRaisesRegex(RuntimeError, "did not reject the oversized cloud backup"):
                HARNESS.verify_quota("emulator-5582", Path(temporary))

        self.assertIn(
            ("shell", "settings", "put", "secure", "backup_local_transport_parameters", "is_encrypted=true,is_device_transfer=true"),
            calls,
        )
        self.assertEqual(
            ["quota-cleanup", "quota", "quota-cleanup"],
            [call.args[1] for call in probe.call_args_list],
        )

    def test_missing_key_fixture_omits_only_portable_recovery_keys(self):
        full_backup = io.BytesIO()
        long_path = f"apps/{HARNESS.PACKAGE}/f/media/objects/sha256/" + "b" * 90 + "/item.jpg"
        with tarfile.open(fileobj=full_backup, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            for name, contents in [
                (f"apps/{HARNESS.PACKAGE}/f/db_recovery/passphrase_backup", b"database key"),
                (f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup", b"identity key"),
                (f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup", b"media key"),
                (f"apps/{HARNESS.PACKAGE}/db/logdate", b"encrypted journal"),
                (f"apps/{HARNESS.PACKAGE}/f/user_media/original", b"original media"),
                (f"apps/{HARNESS.PACKAGE}/f/media/objects/sha256/ab/" + "a" * 64 + ".jpg", b"long media path"),
                (long_path, b"long path"),
                ("apps/org.example.other/f/db_recovery/passphrase_backup", b"other app"),
            ]:
                item = tarfile.TarInfo(name)
                item.size = len(contents)
                archive.addfile(item, io.BytesIO(contents))

        payload = io.BytesIO()
        with tarfile.open(fileobj=payload, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            name = f"1/_full/{HARNESS.PACKAGE}"
            item = tarfile.TarInfo(name)
            item.size = len(full_backup.getvalue())
            archive.addfile(item, io.BytesIO(full_backup.getvalue()))
            transport_metadata = tarfile.TarInfo("1/_delta/@pm@/synthetic")
            transport_metadata.size = len(b"transport metadata")
            archive.addfile(transport_metadata, io.BytesIO(b"transport metadata"))

        filtered = HARNESS.without_portable_recovery_keys(payload.getvalue())
        self.assertNotIn(b"././@PaxHeader", filtered, "The native Android restore reader must receive legacy tar headers")
        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as transport:
            self.assertFalse(any(item.type in (tarfile.XHDTYPE, tarfile.XGLTYPE) for item in transport.getmembers()))
            self.assertEqual(b"transport metadata", transport.extractfile("1/_delta/@pm@/synthetic").read())
            nested = transport.extractfile(f"1/_full/{HARNESS.PACKAGE}").read()
        with tarfile.open(fileobj=io.BytesIO(nested), mode="r:") as archive:
            retained = {item.name: archive.extractfile(item).read() for item in archive.getmembers()}
            self.assertFalse(
                any(item.type in (tarfile.XHDTYPE, tarfile.XGLTYPE) for item in archive.getmembers()),
                "Android's full-restore parser cannot consume Python PAX extension headers",
            )

        self.assertNotIn(f"apps/{HARNESS.PACKAGE}/f/db_recovery/passphrase_backup", retained)
        self.assertNotIn(f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup", retained)
        self.assertNotIn(f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup", retained)
        self.assertEqual(b"encrypted journal", retained[f"apps/{HARNESS.PACKAGE}/db/logdate"])
        self.assertEqual(b"original media", retained[f"apps/{HARNESS.PACKAGE}/f/user_media/original"])
        self.assertEqual(
            b"long media path",
            retained[f"apps/{HARNESS.PACKAGE}/f/media/objects/sha256/ab/" + "a" * 64 + ".jpg"],
        )
        self.assertEqual(b"long path", retained[long_path])
        self.assertEqual(b"other app", retained["apps/org.example.other/f/db_recovery/passphrase_backup"])

    def test_credentials_only_fixture_keeps_all_recovery_keys_and_removes_journal_data(self):
        full_backup = io.BytesIO()
        names = [
            f"apps/{HARNESS.PACKAGE}/f/db_recovery/passphrase_backup",
            f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup",
            f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup",
            f"apps/{HARNESS.PACKAGE}/db/logdate",
            f"apps/{HARNESS.PACKAGE}/f/media/objects/original.jpg",
            f"apps/org.example.other/f/db_recovery/passphrase_backup",
        ]
        with tarfile.open(fileobj=full_backup, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            for name in names:
                contents = name.encode()
                item = tarfile.TarInfo(name)
                item.size = len(contents)
                archive.addfile(item, io.BytesIO(contents))

        retained = HARNESS.credentials_only_from_full_backup(full_backup.getvalue())

        with tarfile.open(fileobj=io.BytesIO(retained), mode="r:") as archive:
            names_after_filter = {item.name for item in archive.getmembers()}
            contents = {item.name: archive.extractfile(item).read() for item in archive.getmembers()}
        self.assertEqual(
            {
                f"apps/{HARNESS.PACKAGE}/f/db_recovery/passphrase_backup",
                f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup",
                f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup",
            },
            names_after_filter,
        )
        self.assertTrue(all(value == name.encode() for name, value in contents.items()))

    def test_credentials_only_acceptance_detects_nested_media_file_paths(self):
        self.assertTrue(HARNESS.contains_media_files({"files/media/objects/sha256/photo.jpg": {"bytes": 1}}))
        self.assertTrue(HARNESS.contains_media_files({b"./files/audio_notes/recording.m4a": {"bytes": 1}}))
        self.assertTrue(HARNESS.contains_media_files({"files/camera-captures/original.jpg": {"bytes": 1}}))
        self.assertFalse(HARNESS.contains_media_files({"files/datastore/preferences": {"bytes": 1}}))

    def test_credentials_only_fixture_rejects_missing_database_recovery_key(self):
        full_backup = io.BytesIO()
        with tarfile.open(fileobj=full_backup, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            item = tarfile.TarInfo(f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup")
            item.size = 4
            archive.addfile(item, io.BytesIO(b"key!"))

        with self.assertRaisesRegex(RuntimeError, "missing required portable recovery keys"):
            HARNESS.credentials_only_from_full_backup(full_backup.getvalue())

    def test_credentials_only_transport_preserves_android_headers_and_other_packages(self):
        full_backup = io.BytesIO()
        with tarfile.open(fileobj=full_backup, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            for name, contents in [
                (f"apps/{HARNESS.PACKAGE}/f/db_recovery/passphrase_backup", b"database key"),
                (f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup", b"identity key"),
                (f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup", b"media key"),
                (f"apps/{HARNESS.PACKAGE}/db/logdate", b"encrypted journal"),
                (f"apps/{HARNESS.PACKAGE}/f/media/original.jpg", b"original media"),
                ("apps/org.example.other/f/settings", b"other app data"),
            ]:
                item = tarfile.TarInfo(name)
                item.size = len(contents)
                archive.addfile(item, io.BytesIO(contents))

        payload = io.BytesIO()
        with tarfile.open(fileobj=payload, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            item = tarfile.TarInfo(f"1/_full/{HARNESS.PACKAGE}")
            item.size = len(full_backup.getvalue())
            archive.addfile(item, io.BytesIO(full_backup.getvalue()))
            metadata = tarfile.TarInfo("1/_delta/@pm@/synthetic")
            metadata.size = len(b"transport metadata")
            archive.addfile(metadata, io.BytesIO(b"transport metadata"))

        filtered = HARNESS.credentials_only(payload.getvalue())
        self.assertNotIn(b"././@PaxHeader", filtered)
        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as transport:
            self.assertEqual(b"transport metadata", transport.extractfile("1/_delta/@pm@/synthetic").read())
            nested = transport.extractfile(f"1/_full/{HARNESS.PACKAGE}").read()
        with tarfile.open(fileobj=io.BytesIO(nested), mode="r:") as archive:
            self.assertEqual(
                {
                    f"apps/{HARNESS.PACKAGE}/f/db_recovery/passphrase_backup",
                    f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup",
                    f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup",
                },
                {item.name for item in archive.getmembers()},
            )

    def test_transport_registration_and_selection_are_both_confirmed(self):
        transport = HARNESS.TRANSPORT.encode()
        responses = [b"* other", b"  " + transport, b"ignored selection", b"* other\n  " + transport,
                     b"  " + transport, b"selected", b"* " + transport]
        with patch.object(HARNESS, "adb", side_effect=responses) as adb, patch.object(HARNESS.time, "sleep"):
            HARNESS.select_local_transport("emulator-5582", attempts=3)
        self.assertEqual(2, sum(call.args[2:] == ("bmgr", "transport", HARNESS.TRANSPORT) for call in adb.call_args_list))

    def test_unavailable_transport_fails_before_installing_or_backing_up(self):
        with patch.object(HARNESS, "adb", return_value=b"* other") as adb, patch.object(HARNESS.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "local backup transport"):
                HARNESS.select_local_transport("emulator-5582", attempts=2)
        self.assertTrue(all(call.args[2:] == ("bmgr", "list", "transports") for call in adb.call_args_list))

    def test_transport_service_startup_timeout_is_retried(self):
        transport = HARNESS.TRANSPORT.encode()
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[subprocess.TimeoutExpired("bmgr", 10), b"  " + transport, b"selected", b"* " + transport],
        ) as adb, patch.object(HARNESS.time, "sleep"):
            HARNESS.select_local_transport("emulator-5582", attempts=3)

        self.assertEqual(4, adb.call_count)

    def test_physical_remote_and_ambiguous_targets_are_rejected(self):
        for value in ("38191FDJG00FNY", "192.0.2.1:5555", "adb-phone._tcp", "emulator-", "emulator-5580; id"):
            with self.subTest(serial=value):
                with self.assertRaises(argparse.ArgumentTypeError):
                    HARNESS.emulator_serial(value)

    def test_explicit_emulator_serial_is_accepted(self):
        self.assertEqual("emulator-5582", HARNESS.emulator_serial("emulator-5582"))


if __name__ == "__main__":
    unittest.main()
