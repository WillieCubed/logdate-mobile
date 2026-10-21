import argparse
import importlib.util
import io
from pathlib import Path
import tarfile
import tempfile
import time
import unittest
from unittest.mock import call, patch


SCRIPT = Path(__file__).with_name("native-backup-acceptance.py")
SPEC = importlib.util.spec_from_file_location("native_backup_acceptance", SCRIPT)
NATIVE_BACKUP_ACCEPTANCE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE_BACKUP_ACCEPTANCE)
WORK_SCRIPT = Path(__file__).with_name("native-restore-work-restart-acceptance.py")
WORK_SPEC = importlib.util.spec_from_file_location("native_restore_work_acceptance", WORK_SCRIPT)
NATIVE_RESTORE_WORK_ACCEPTANCE = importlib.util.module_from_spec(WORK_SPEC)
WORK_SPEC.loader.exec_module(NATIVE_RESTORE_WORK_ACCEPTANCE)
D2D_SCRIPT = Path(__file__).with_name("native-d2d-acceptance.py")


class RestoreSnapshotValidationTest(unittest.TestCase):
    def test_first_backup_may_make_only_missing_privacy_defaults_explicit(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        original = harness.length_delimited_field(1, CredentialsOnlyBackupFilterTest.preference_entry("security_level", "BIOMETRIC"))
        explicit = harness.explicit_backup_privacy_defaults(original)
        self.assertIn(b"BIOMETRIC", explicit)
        self.assertNotIn(b"NONE", explicit)
        self.assertIn(b"is_onboarded", explicit)
        self.assertEqual(explicit, harness.explicit_backup_privacy_defaults(explicit))
        extra = harness.length_delimited_field(1, CredentialsOnlyBackupFilterTest.preference_entry("draft", "changed"))
        self.assertNotEqual(explicit, harness.canonical_preferences(explicit + extra))

    def test_snapshot_ignores_firebase_backup_heartbeat_file(self):
        before = {
            "databases/logdate": {"bytes": 1, "sha256": "journal"},
            "files/datastore/FirebaseHeartBeatW0RFRkFVTFRd.preferences_pb": {
                "bytes": 1,
                "sha256": "before",
            },
        }
        after = {
            "databases/logdate": {"bytes": 1, "sha256": "journal"},
            "files/datastore/FirebaseHeartBeatW0RFRkFVTFRd.preferences_pb": {
                "bytes": 1,
                "sha256": "after",
            },
        }

        NATIVE_BACKUP_ACCEPTANCE.assert_snapshot_preserved(before, after)

    def test_snapshot_still_rejects_changes_to_logdate_datastore(self):
        before = {
            "databases/logdate": {"bytes": 1, "sha256": "journal"},
            "files/datastore/logdate.preferences_pb": {"bytes": 1, "sha256": "before"},
        }
        after = {
            "databases/logdate": {"bytes": 1, "sha256": "journal"},
            "files/datastore/logdate.preferences_pb": {"bytes": 1, "sha256": "after"},
        }

        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable data: files/datastore/logdate.preferences_pb"):
            NATIVE_BACKUP_ACCEPTANCE.assert_snapshot_preserved(before, after)

    def test_credentials_only_snapshot_accepts_absent_database_and_media(self):
        restored = {
            "files/db_recovery/passphrase_backup": {"bytes": 32, "sha256": "passphrase"},
            "files/identity_recovery/phrase_backup": {"bytes": 24, "sha256": "phrase"},
            "files/identity_recovery/media_key_backup": {"bytes": 32, "sha256": "media-key"},
        }

        NATIVE_BACKUP_ACCEPTANCE.verify_restored_snapshot(restored, {}, credentials_only=True)

    def test_credentials_only_snapshot_reports_missing_keys_without_key_error(self):
        with self.assertRaisesRegex(RuntimeError, "journal recovery key"):
            NATIVE_BACKUP_ACCEPTANCE.verify_restored_snapshot({}, {}, credentials_only=True)

    def test_data_only_snapshot_reports_missing_database_without_key_error(self):
        with self.assertRaisesRegex(RuntimeError, "without delivering the journal database"):
            NATIVE_BACKUP_ACCEPTANCE.verify_restored_snapshot({}, {}, credentials_only=False)

    def test_data_only_snapshot_rejects_a_database_that_differs_from_source(self):
        with self.assertRaisesRegex(RuntimeError, "destination database differs"):
            NATIVE_BACKUP_ACCEPTANCE.verify_restored_snapshot(
                {"databases/logdate": {"sha256": "different"}},
                {"databases/logdate": {"sha256": "source"}},
                credentials_only=False,
            )

    def test_credentials_only_snapshot_allows_startup_created_empty_database(self):
        NATIVE_BACKUP_ACCEPTANCE.verify_restored_snapshot(
            {
                "databases/logdate": {"bytes": 4096, "sha256": "empty-sqlite"},
                "files/db_recovery/passphrase_backup": {"bytes": 32, "sha256": "passphrase"},
                "files/identity_recovery/phrase_backup": {"bytes": 24, "sha256": "phrase"},
                "files/identity_recovery/media_key_backup": {"bytes": 32, "sha256": "media-key"},
            },
            {},
            credentials_only=True,
        )


class NativeD2DRestoreEvidenceTest(unittest.TestCase):
    def test_single_device_simulation_changes_fixture_after_backup_before_restore(self):
        harness = D2D_SCRIPT.read_text()
        probe_path = (
            SCRIPT.parents[2]
            / "app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/NativeBackupTransferProbeTest.kt"
        )
        probe = probe_path.read_text()

        backup_call = harness.index('HARNESS.adb(serial, "shell", "bmgr", "backupnow"')
        mutation_probe = harness.index('HARNESS.probe(serial, "post-backup-mutation"')
        restore_call = harness.index('HARNESS.adb(serial, "shell", "bmgr", "restore"')

        self.assertLess(backup_call, mutation_probe)
        self.assertLess(mutation_probe, restore_call)
        verification = harness.index('HARNESS.probe(serial, "verify-in-place"')
        self.assertLess(restore_call, verification)
        self.assertIn('"post-backup-mutation"', probe)
        self.assertIn("Mutated after native D2D backup", probe)
        self.assertIn('"Native transfer journal"', probe)
        marker_check = probe.index('File(context.noBackupFilesDir, "native-backup-fixture.json")')
        title_check = probe.index('assertEquals("Native transfer journal"')
        mutation_write = probe.index("journals.update(journal.copy(title = \"Mutated after native D2D backup\"))")
        self.assertLess(marker_check, mutation_write)
        self.assertLess(title_check, mutation_write)


class RuntimePermissionGrantParsingTest(unittest.TestCase):
    def test_parses_android_34_runtime_permission_without_flags(self):
        package_state = (
            "      requested permissions:\n        android.permission.RECORD_AUDIO\n"
            "      runtime permissions:\n"
        )

        self.assertFalse(
            NATIVE_BACKUP_ACCEPTANCE.runtime_permission_grant(
                package_state,
                "android.permission.RECORD_AUDIO",
            )
        )

    def test_parses_runtime_permission_with_flags(self):
        package_state = (
            "      requested permissions:\n        android.permission.POST_NOTIFICATIONS\n"
            "      runtime permissions:\n"
            "        android.permission.POST_NOTIFICATIONS: granted=true, flags=[ USER_SET ]\n"
        )

        self.assertTrue(
            NATIVE_BACKUP_ACCEPTANCE.runtime_permission_grant(
                package_state,
                "android.permission.POST_NOTIFICATIONS",
            )
        )

    def test_rejects_permission_missing_from_package_state(self):
        with self.assertRaisesRegex(RuntimeError, "Android did not report the runtime grant"):
            NATIVE_BACKUP_ACCEPTANCE.runtime_permission_grant(
                "      requested permissions:\n        android.permission.CAMERA\n      runtime permissions:\n",
                "android.permission.RECORD_AUDIO",
            )


class EmulatorSetupStateTest(unittest.TestCase):
    @patch.object(NATIVE_BACKUP_ACCEPTANCE, "adb", side_effect=[b"0", b"", b"1"])
    def test_marks_emulator_setup_complete_before_backup(self, adb):
        NATIVE_BACKUP_ACCEPTANCE.ensure_emulator_setup_complete("emulator-5554")

        self.assertEqual(
            [
                call("emulator-5554", "shell", "settings", "get", "secure", "user_setup_complete"),
                call("emulator-5554", "shell", "settings", "put", "secure", "user_setup_complete", "1"),
                call("emulator-5554", "shell", "settings", "get", "secure", "user_setup_complete"),
            ],
            adb.call_args_list,
        )

    @patch.object(NATIVE_BACKUP_ACCEPTANCE, "adb", side_effect=[b"1"])
    def test_preserves_already_completed_setup(self, adb):
        NATIVE_BACKUP_ACCEPTANCE.ensure_emulator_setup_complete("emulator-5554")

        adb.assert_called_once_with(
            "emulator-5554",
            "shell",
            "settings",
            "get",
            "secure",
            "user_setup_complete",
        )


class EmulatorWakeStateTest(unittest.TestCase):
    @patch.object(NATIVE_BACKUP_ACCEPTANCE, "adb")
    def test_wakes_destination_and_dismisses_keyguard_before_timeline_assertion(self, adb):
        NATIVE_BACKUP_ACCEPTANCE.wake_emulator("emulator-5554")

        self.assertEqual(
            [
                call("emulator-5554", "shell", "input", "keyevent", "KEYCODE_WAKEUP"),
                call("emulator-5554", "shell", "wm", "dismiss-keyguard"),
            ],
            adb.call_args_list,
        )


class NativeTimelineSystemDialogTest(unittest.TestCase):
    def test_finds_only_the_close_button_for_the_known_bluetooth_system_crash_dialog(self):
        hierarchy = (
            '<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            'text="Bluetooth keeps stopping"/><node package="android" '
            'resource-id="android:id/aerr_close" bounds="[70,1028][1010,1154]"/></hierarchy>'
        )

        target = NATIVE_BACKUP_ACCEPTANCE.bluetooth_crash_dialog_close_target(hierarchy)

        self.assertEqual((540, 1091), target)

    def test_finds_wait_button_only_for_the_system_ui_not_responding_dialog(self):
        hierarchy = (
            '<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            'text="System UI isn&#x27;t responding"/><node package="android" '
            'resource-id="android:id/aerr_wait" bounds="[70,1028][1010,1154]"/></hierarchy>'
        )

        target = NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(hierarchy)

        self.assertEqual((540, 1091), target)

    def test_detects_system_ui_dialog_when_wait_control_is_missing(self):
        hierarchy = (
            '<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            'text="System UI isn&#x27;t responding"/><node text="Search your log"/>'
            '<node text="Timeline"/></hierarchy>'
        )

        self.assertTrue(NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_dialog_present(hierarchy))
        self.assertIsNone(NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(hierarchy))

    def test_ignores_other_anr_dialogs(self):
        hierarchy = (
            '<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            'text="LogDate isn&#x27;t responding"/><node package="android" '
            'resource-id="android:id/aerr_wait" bounds="[70,1028][1010,1154]"/></hierarchy>'
        )

        target = NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(hierarchy)

        self.assertIsNone(target)

    def test_process_system_anr_has_an_android_wait_control(self):
        hierarchy = (
            '<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            'text="Process system isn&#x27;t responding"/><node package="android" '
            'resource-id="android:id/aerr_wait" bounds="[70,1028][1010,1154]"/></hierarchy>'
        )
        self.assertTrue(NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_dialog_present(hierarchy))
        self.assertEqual((540, 1091), NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(hierarchy))
        no_wait = hierarchy.replace("android:id/aerr_wait", "android:id/other")
        self.assertTrue(NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_dialog_present(no_wait))
        self.assertIsNone(NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(no_wait))
        app_owned = hierarchy.replace('package="android"', 'package="studio.hypertext.logdate.debug"')
        self.assertFalse(NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_dialog_present(app_owned))
        self.assertIsNone(NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(app_owned))

    def test_timeline_poll_rejects_an_app_anr_even_with_timeline_content_behind_it(self):
        hierarchy = (
            b'<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            b'text="LogDate isn&#x27;t responding"/><node package="android" '
            b'resource-id="android:id/aerr_wait" bounds="[70,1028][1010,1154]"/>'
            b'<node text="Search your log"/><node text="Timeline"/></hierarchy>'
        )
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE_BACKUP_ACCEPTANCE,
            "adb",
            side_effect=[b"", b"", b"UI hierarchy dumped to: /data/local/tmp/ui.xml", hierarchy, b"app ANR"],
        ) as adb:
            with self.assertRaisesRegex(RuntimeError, "replacement app did not open its Timeline"):
                NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline("emulator-5582", Path(temporary), timeout_seconds=0)
            self.assertEqual(5, adb.call_count)

    def test_timeline_poll_waits_for_process_system_recovery_then_rechecks_content(self):
        self.assert_system_anr_recovery("Process system")

    def test_timeline_poll_waits_for_system_ui_recovery_then_rechecks_content(self):
        self.assert_system_anr_recovery("System UI")

    def assert_system_anr_recovery(self, title):
        system_ui_dialog = (
            b'<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            b'text="System UI isn&#x27;t responding"/><node package="android" '
            b'resource-id="android:id/aerr_wait" bounds="[70,1028][1010,1154]"/>'
            b'<node text="Search your log"/><node text="Timeline"/></hierarchy>'
        )
        system_ui_dialog = system_ui_dialog.replace(b"System UI", title.encode())
        timeline = b'<hierarchy><node text="Search your log"/><node text="Timeline"/></hierarchy>'
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE_BACKUP_ACCEPTANCE,
            "adb",
            side_effect=[
                b"",
                b"",
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                system_ui_dialog,
                b"dialog screenshot",
                b"",
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                timeline,
                b"restored journal screenshot",
            ],
        ) as adb, patch.object(NATIVE_BACKUP_ACCEPTANCE.time, "sleep"):
            NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline("emulator-5582", Path(temporary), timeout_seconds=5)

            adb.assert_any_call("emulator-5582", "shell", "input", "tap", "540", "1091")
            self.assertEqual(timeline, (Path(temporary) / "destination-ui.xml").read_bytes())
            self.assertEqual(b"restored journal screenshot", (Path(temporary) / "destination-journal.png").read_bytes())

    def test_timeline_poll_does_not_accept_anr_overlay_without_a_usable_wait_control(self):
        blocked_timeline = (
            b'<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            b'text="System UI isn&#x27;t responding"/><node text="Search your log"/>'
            b'<node text="Timeline"/></hierarchy>'
        )
        timeline = b'<hierarchy><node text="Search your log"/><node text="Timeline"/></hierarchy>'
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE_BACKUP_ACCEPTANCE,
            "adb",
            side_effect=[
                b"",
                b"",
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                blocked_timeline,
                b"blocked screenshot",
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                timeline,
                b"restored journal screenshot",
            ],
        ) as adb, patch.object(NATIVE_BACKUP_ACCEPTANCE.time, "sleep"):
            NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline("emulator-5582", Path(temporary), timeout_seconds=5)

            self.assertEqual(8, adb.call_count)
            self.assertEqual(timeline, (Path(temporary) / "destination-ui.xml").read_bytes())

    def test_timeline_poll_does_not_accept_bluetooth_overlay_without_a_usable_close_control(self):
        blocked_timeline = (
            b'<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            b'text="Bluetooth keeps stopping"/><node text="Search your log"/>'
            b'<node text="Timeline"/></hierarchy>'
        )
        timeline = b'<hierarchy><node text="Search your log"/><node text="Timeline"/></hierarchy>'
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE_BACKUP_ACCEPTANCE,
            "adb",
            side_effect=[
                b"",
                b"",
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                blocked_timeline,
                b"blocked screenshot",
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                timeline,
                b"restored journal screenshot",
            ],
        ) as adb, patch.object(NATIVE_BACKUP_ACCEPTANCE.time, "sleep"):
            NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline("emulator-5582", Path(temporary), timeout_seconds=5)

            self.assertEqual(8, adb.call_count)
            self.assertEqual(timeline, (Path(temporary) / "destination-ui.xml").read_bytes())

    def test_ignores_other_system_crash_dialogs(self):
        hierarchy = (
            '<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            'text="LogDate keeps stopping"/><node package="android" '
            'resource-id="android:id/aerr_close" bounds="[70,1028][1010,1154]"/></hierarchy>'
        )

        target = NATIVE_BACKUP_ACCEPTANCE.bluetooth_crash_dialog_close_target(hierarchy)

        self.assertIsNone(target)


class CloudRestoreProcessRestartProbeTest(unittest.TestCase):
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb", return_value=b"OK (1 test)")
    def test_targets_only_the_explicit_emulator_and_records_probe_output(self, adb):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "after-process-restart.log"

            NATIVE_RESTORE_WORK_ACCEPTANCE.probe("emulator-5554", "verify", output)

            self.assertEqual(b"OK (1 test)", output.read_bytes())
            adb.assert_called_once_with(
                "emulator-5554",
                "shell",
                "am",
                "instrument",
                "-w",
                "-r",
                "-e",
                "class",
                NATIVE_RESTORE_WORK_ACCEPTANCE.PROBE,
                "-e",
                "cloudRestoreWorkMode",
                "verify",
                NATIVE_RESTORE_WORK_ACCEPTANCE.RUNNER,
            )

    def test_refuses_physical_device_serials(self):
        with self.assertRaisesRegex(argparse.ArgumentTypeError, "explicit emulator-NNNN serial"):
            NATIVE_RESTORE_WORK_ACCEPTANCE.emulator_serial("R5CT123456")

    @patch.object(time, "sleep")
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "radio_state", side_effect=[(True, b"0"), (False, b"1")])
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb")
    def test_waits_for_android_to_apply_restored_radio_state(self, adb, radio_state, sleep):
        original = (False, b"1")

        NATIVE_RESTORE_WORK_ACCEPTANCE.restore_radio_state("emulator-5554", original)

        self.assertEqual(2, radio_state.call_count)
        sleep.assert_called_once()
        self.assertEqual(
            [
                call("emulator-5554", "shell", "cmd", "connectivity", "airplane-mode", "disable"),
                call("emulator-5554", "shell", "svc", "wifi", "enable"),
            ],
            adb.call_args_list,
        )

    @patch.object(time, "sleep")
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "radio_state", return_value=(True, b"0"))
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb")
    def test_fails_if_radio_state_does_not_return_to_original(self, adb, radio_state, sleep):
        with self.assertRaisesRegex(RuntimeError, "Could not restore the emulator's original network state"):
            NATIVE_RESTORE_WORK_ACCEPTANCE.restore_radio_state("emulator-5554", (False, b"1"))

        self.assertGreater(radio_state.call_count, 1)


class PartialNativeTransportTest(unittest.TestCase):
    def test_removes_one_original_media_file_and_preserves_the_transport_tar(self):
        package = NATIVE_BACKUP_ACCEPTANCE.PACKAGE
        inner = self.make_tar(
            [
                (f"apps/{package}/f/media/objects/audio.wav", b"audio"),
                (f"apps/{package}/f/media/objects/photo.png", b"photo"),
                (f"apps/{package}/f/media/objects/video.mp4", b"video"),
                (f"apps/{package}/f/db_recovery/passphrase_backup", b"key"),
            ]
        )
        outer = self.make_tar([(f"1/_full/{package}", inner)])

        filtered, removed_path = NATIVE_BACKUP_ACCEPTANCE.without_one_native_media_file(outer)

        self.assertEqual("files/media/objects/photo.png", removed_path)
        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as transport:
            full = transport.extractfile(f"1/_full/{package}")
            self.assertIsNotNone(full)
            with tarfile.open(fileobj=full, mode="r:") as app_backup:
                names = {member.name for member in app_backup.getmembers()}
        self.assertNotIn(f"apps/{package}/f/media/objects/photo.png", names)
        self.assertIn(f"apps/{package}/f/media/objects/audio.wav", names)
        self.assertIn(f"apps/{package}/f/media/objects/video.mp4", names)
        self.assertIn(f"apps/{package}/f/db_recovery/passphrase_backup", names)

    @staticmethod
    def make_tar(entries):
        output = io.BytesIO()
        with tarfile.open(fileobj=output, mode="w:", format=tarfile.USTAR_FORMAT) as archive:
            for name, contents in entries:
                info = tarfile.TarInfo(name)
                info.size = len(contents)
                archive.addfile(info, io.BytesIO(contents))
        return output.getvalue()


class MissingPrivacyBackupFilterTest(unittest.TestCase):
    def test_requires_package_rejection_and_current_backup_agent_privacy_failure(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        result = f"Package {harness.PACKAGE} with result: Transport rejected package because it wasn't able to process it at the time\n".encode()
        diagnostic = (
            "100.100 42 43 D BackupServiceBinder: onFullBackup[M] (app.logdate.client.data.restore.NativeAccountBackupAgent) threw\n"
            "100.101 42 43 D BackupServiceBinder: NativeRestorePrivacyMetadataRequiredException\n"
            "100.102 42 43 D BackupServiceBinder: at NativeRestorePrivacyPolicy$prepareBackup$3.invokeSuspend\n"
        ).encode()
        harness.verify_privacy_backup_refusal(result, diagnostic, 100.0)
        for unrelated in (b"Backup is not enabled", b"Backup finished with result: Success", b"Package missing with result: Success"):
            with self.assertRaises(RuntimeError):
                harness.verify_privacy_backup_refusal(unrelated, diagnostic, 100.0)
        for unrelated in (b"", diagnostic.replace(b"prepareBackup", b"verifyBeforeContent")):
            with self.assertRaises(RuntimeError):
                harness.verify_privacy_backup_refusal(result, unrelated, 100.0)
        with self.assertRaises(RuntimeError):
            harness.verify_privacy_backup_refusal(result, diagnostic, 101.0)

    def test_removes_only_privacy_metadata_and_preserves_nested_transport_content(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        prefix = f"apps/{harness.PACKAGE}"
        preferences = b"".join(
            harness.length_delimited_field(1, CredentialsOnlyBackupFilterTest.preference_entry(name, value))
            for name, value in [
                ("is_onboarded", "true"),
                ("security_level", "BIOMETRIC"),
                ("identity.canonical_owner_id", "native-test-owner"),
                ("editor_drafts_anonymous", "unfinished"),
            ]
        )
        entries = [
            (prefix + "/_manifest", b"manifest"),
            (prefix + "/f/logdate.preferences_pb", preferences),
            (prefix + "/db/logdate", b"encrypted journal"),
            (prefix + "/f/db_recovery/passphrase_backup", b"key"),
            (prefix + "/f/media/photo.png", b"original"),
        ]
        full_backup = CredentialsOnlyBackupFilterTest.make_tar(entries)
        transport_name = f"1/_full/{harness.PACKAGE}"
        transport = CredentialsOnlyBackupFilterTest.make_tar([(transport_name, full_backup), ("1/@pm@", b"metadata")])

        filtered = harness.without_privacy_metadata(transport)

        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as outer:
            self.assertEqual(b"metadata", outer.extractfile("1/@pm@").read())
            with tarfile.open(fileobj=io.BytesIO(outer.extractfile(transport_name).read()), mode="r:") as inner:
                for name, data in entries:
                    actual = inner.extractfile(name).read()
                    if name.endswith("logdate.preferences_pb"):
                        self.assertNotIn(b"is_onboarded", actual)
                        self.assertNotIn(b"security_level", actual)
                        self.assertIn(b"native-test-owner", actual)
                        self.assertIn(b"unfinished", actual)
                    else:
                        self.assertEqual(data, actual)

    def test_rejects_fixture_that_never_had_privacy_metadata(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        full_backup = CredentialsOnlyBackupFilterTest.make_tar([
            (f"apps/{harness.PACKAGE}/f/logdate.preferences_pb", b""),
        ])
        transport = CredentialsOnlyBackupFilterTest.make_tar([(f"1/_full/{harness.PACKAGE}", full_backup)])
        with self.assertRaisesRegex(RuntimeError, "privacy metadata"):
            harness.without_privacy_metadata(transport)


class CredentialsOnlyBackupFilterTest(unittest.TestCase):
    def test_preserves_android_manifest_directories_and_only_portable_credentials(self):
        package = NATIVE_BACKUP_ACCEPTANCE.PACKAGE
        prefix = f"apps/{package}"
        entries = [
            (prefix + "/_manifest", b"manifest"),
            (prefix + "/f/", b""),
            (prefix + "/f/db_recovery/", b""),
            (prefix + "/f/identity_recovery/", b""),
            (prefix + "/f/media/", b""),
            (prefix + "/f/db_recovery/passphrase_backup", b"db-key"),
            (prefix + "/f/identity_recovery/phrase_backup", b"identity-key"),
            (prefix + "/f/identity_recovery/media_key_backup", b"media-key"),
            (
                prefix + "/f/logdate.preferences_pb",
                b"".join(
                    NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(1, self.preference_entry(name, value))
                    for name, value in [
                        ("identity.canonical_owner_id", "native-test-owner"),
                        ("identity.canonical_owner_bound", "true"),
                        ("editor_drafts_anonymous", '{"drafts":["unfinished"]}'),
                    ]
                ),
            ),
            (prefix + "/f/media/photo.png", b"original"),
            (prefix + "/db/logdate", b"journal"),
        ]
        backup = self.make_tar(entries)

        filtered = NATIVE_BACKUP_ACCEPTANCE.credentials_only_from_full_backup(backup)

        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as archive:
            members = {member.name: member for member in archive.getmembers()}
            preferences_data = archive.extractfile(prefix + "/f/logdate.preferences_pb").read()
        self.assertIn(prefix + "/_manifest", members)
        self.assertTrue(members[prefix + "/f/db_recovery"].isdir())
        self.assertTrue(members[prefix + "/f/identity_recovery"].isdir())
        self.assertIn(prefix + "/f/db_recovery/passphrase_backup", members)
        self.assertIn(prefix + "/f/identity_recovery/phrase_backup", members)
        self.assertIn(prefix + "/f/identity_recovery/media_key_backup", members)
        self.assertIn(prefix + "/f/logdate.preferences_pb", members)
        self.assertNotIn(prefix + "/f/media/photo.png", members)
        self.assertNotIn(prefix + "/db/logdate", members)
        preferences = NATIVE_BACKUP_ACCEPTANCE.canonical_preferences(preferences_data)
        names = set()
        for _, _, _, entry in NATIVE_BACKUP_ACCEPTANCE.protobuf_fields(preferences):
            fields = list(NATIVE_BACKUP_ACCEPTANCE.protobuf_fields(entry))
            names.add(next(payload for number, wire, _, payload in fields if number == 1 and wire == 2).decode())
        self.assertEqual({"identity.canonical_owner_id", "identity.canonical_owner_bound"}, names)

    def test_rejects_credentials_only_archive_without_android_manifest(self):
        package = NATIVE_BACKUP_ACCEPTANCE.PACKAGE
        prefix = f"apps/{package}"
        backup = self.make_tar(
            [
                (prefix + "/f/db_recovery/passphrase_backup", b"db-key"),
                (prefix + "/f/identity_recovery/phrase_backup", b"identity-key"),
                (prefix + "/f/identity_recovery/media_key_backup", b"media-key"),
                (
                    prefix + "/f/logdate.preferences_pb",
                    NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(
                        1,
                        self.preference_entry("identity.canonical_owner_id", "native-test-owner"),
                    ),
                ),
            ]
        )

        with self.assertRaisesRegex(RuntimeError, "missing Android's app backup manifest"):
            NATIVE_BACKUP_ACCEPTANCE.credentials_only_from_full_backup(backup)

    @staticmethod
    def make_tar(entries):
        output = io.BytesIO()
        with tarfile.open(fileobj=output, mode="w:", format=tarfile.USTAR_FORMAT) as archive:
            for name, contents in entries:
                info = tarfile.TarInfo(name)
                info.size = len(contents)
                if name.endswith("/"):
                    info.type = tarfile.DIRTYPE
                archive.addfile(info, io.BytesIO(contents) if contents else None)
        return output.getvalue()

    @staticmethod
    def preference_entry(name, value):
        preference_value = NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(5, value.encode())
        entry = (
            NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(1, name.encode())
            + NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(2, preference_value)
        )
        return entry


if __name__ == "__main__":
    unittest.main()
