import argparse
from contextlib import redirect_stderr
import hashlib
import importlib.util
import io
from pathlib import Path
import subprocess
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


class ManagedWorkSourceGuardTest(unittest.TestCase):
    def test_preinstalled_guard_checks_the_selected_package_even_when_import_defaults_differ(self):
        harness = NATIVE_RESTORE_WORK_ACCEPTANCE
        with patch.object(harness, "adb", return_value=("package:" + harness.PACKAGE).encode()), patch.dict(
            harness.NATIVE, {"package_user_files": lambda serial, package: self.assertEqual(harness.PACKAGE, package) or []}
        ):
            harness.require_empty_install("emulator-5556", True)

    def test_preinstalled_source_requires_explicit_opt_in_and_refuses_retained_content(self):
        harness = NATIVE_RESTORE_WORK_ACCEPTANCE
        installed = ("package:" + harness.PACKAGE).encode()
        with patch.object(harness, "adb", return_value=installed), patch.dict(harness.NATIVE, {"package_user_files": lambda serial, package: []}):
            with self.assertRaisesRegex(RuntimeError, "fresh isolated"):
                harness.require_empty_install("emulator-5556", False)
            harness.require_empty_install("emulator-5556", True)
        for path in ("./databases/logdate", "./files/logdate.preferences_pb", "./files/audio_notes/draft.m4a", "./files/db_recovery/passphrase_backup"):
            with self.subTest(path=path), patch.object(harness, "adb", return_value=installed), patch.dict(
                harness.NATIVE, {"package_user_files": lambda serial, package: [path]}
            ):
                with self.assertRaisesRegex(RuntimeError, "retained data"):
                    harness.require_empty_install("emulator-5556", True)


class DataOnlySourceSessionTest(unittest.TestCase):
    def test_synthetic_session_stays_offline_through_backup_and_restores_radio_on_failure(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        events = []
        failure = RuntimeError("backup rejected")
        with tempfile.TemporaryDirectory() as directory, patch.object(harness, "radio_state", return_value=(False, b"1")), patch.object(
            harness, "set_offline", side_effect=lambda *args: events.append(("offline", args))
        ), patch.object(harness, "probe", side_effect=lambda serial, mode, output, **kwargs: events.append(mode)), patch.object(
            harness, "adb", side_effect=lambda *args: events.append(("adb", args))
        ), patch.object(harness, "restore_radio_state", side_effect=lambda *args: events.append(("restore", args))):
            with self.assertRaises(RuntimeError) as raised:
                with harness.data_only_source_session("emulator-5556", Path(directory), True):
                    events.append("backup")
                    raise failure
        self.assertIs(failure, raised.exception)
        self.assertEqual([("offline", ("emulator-5556", True)), "source-account-session", "backup",
                          "source-account-session-cleanup", ("adb", ("emulator-5556", "shell", "am", "force-stop", harness.PACKAGE)),
                          ("restore", ("emulator-5556", (False, b"1")))], events)

    def test_failed_setup_cleanup_retains_the_same_unique_session_identity(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        failure = RuntimeError("existing session refused")
        with tempfile.TemporaryDirectory() as directory, patch.object(harness, "radio_state", return_value=(False, b"1")), patch.object(
            harness, "set_offline"
        ), patch.object(harness, "probe", side_effect=[failure, None]) as probe, patch.object(harness, "adb"), patch.object(
            harness, "restore_radio_state"
        ):
            with self.assertRaises(RuntimeError) as raised:
                with harness.data_only_source_session("emulator-5556", Path(directory), True):
                    self.fail("A rejected source session cannot reach backup")
        self.assertIs(failure, raised.exception)
        identifiers = [call.kwargs["source_session_id"] for call in probe.call_args_list]
        self.assertEqual(2, len(identifiers))
        self.assertEqual(identifiers[0], identifiers[1])
        self.assertRegex(identifiers[0], r"^[a-f0-9-]{36}$")

    def test_normal_transfer_does_not_change_source_connectivity(self):
        with patch.object(NATIVE_BACKUP_ACCEPTANCE, "radio_state") as radio:
            with NATIVE_BACKUP_ACCEPTANCE.data_only_source_session("emulator-5556", Path("unused"), False):
                pass
        radio.assert_not_called()


class RestoreSnapshotValidationTest(unittest.TestCase):
    def test_backup_accepts_checkpointed_database_only_after_logical_probe(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        before = {"databases/logdate": {"bytes": 4096, "sha256": "before"}}
        after = {"databases/logdate": {"bytes": 741376, "sha256": "after"}}
        calls = []

        def adb(serial, *arguments, **kwargs):
            if arguments[:3] == ("shell", "am", "start"):
                return b"Status: ok"
            if arguments[:3] == ("shell", "bmgr", "backupnow"):
                calls.append(arguments)
                return f"Package {harness.PACKAGE} with result: Success".encode()
            return b""

        with tempfile.TemporaryDirectory() as directory, patch.object(harness, "adb", side_effect=adb), patch.object(
            harness, "enable_backup"
        ), patch.object(harness, "package_file_snapshot", side_effect=[before, after]), patch.object(
            harness, "private_file"
        ):
            harness.backup_source("emulator-5554", Path(directory) / "backup.log", verify_after_backup=lambda: calls.append("logical"))
        self.assertEqual(
            [("shell", "bmgr", "backupnow", "@pm@", harness.PACKAGE, "--non-incremental"), "logical"],
            calls,
        )

    def test_datastore_atomic_write_staging_is_not_an_accepted_preferences_change(self):
        retained = {
            "databases/logdate": {"sha256": "journal"},
            "files/logdate.preferences_pb": {"sha256": "preferences"},
        }
        during_write = {**retained, "files/logdate.preferences_pb.tmp": {"sha256": "staging"}}
        NATIVE_BACKUP_ACCEPTANCE.assert_snapshot_preserved(during_write, retained)

    def test_atomic_write_exclusion_does_not_hide_accepted_preferences_or_recovery_keys(self):
        for path in ("files/logdate.preferences_pb", "files/db_recovery/passphrase_backup.part", "files/media/original.tmp"):
            with self.subTest(path=path):
                before = {"databases/logdate": {"sha256": "journal"}, path: {"sha256": "before"}}
                after = {"databases/logdate": {"sha256": "journal"}, path: {"sha256": "after"}}
                with self.assertRaisesRegex(RuntimeError, "snapshot changed durable data"):
                    NATIVE_BACKUP_ACCEPTANCE.assert_snapshot_preserved(before, after)

    def test_preference_change_diagnostics_identify_keys_without_values(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        def preferences(items):
            return b"".join(harness.length_delimited_field(1, CredentialsOnlyBackupFilterTest.preference_entry(k, v))
                            for k, v in items.items())
        before = preferences({"same": "retained", "changed": "old-secret", "removed": "removed-secret"})
        after = preferences({"same": "retained", "changed": "new-secret", "added": "added-secret"})
        changes = harness.preference_changes(before, after)
        self.assertEqual({"added": ["added"], "removed": ["removed"], "changed": ["changed"]}, changes)
        self.assertNotIn("secret", str(changes))

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

    def test_verified_checkpoint_allows_only_the_live_database_bytes_to_change(self):
        before = {
            "databases/logdate": {"bytes": 4096, "sha256": "live-before-checkpoint"},
            "files/db_recovery/passphrase_backup": {"bytes": 32, "sha256": "key"},
        }
        checkpointed = {**before, "databases/logdate": {"bytes": 741376, "sha256": "live-after-checkpoint"}}
        with self.assertRaisesRegex(RuntimeError, "databases/logdate"):
            NATIVE_BACKUP_ACCEPTANCE.assert_snapshot_preserved(before, checkpointed)
        NATIVE_BACKUP_ACCEPTANCE.assert_snapshot_preserved(
            before, checkpointed, ignored_paths={"databases/logdate"}
        )
        changed_key = {**checkpointed, "files/db_recovery/passphrase_backup": {"bytes": 32, "sha256": "different"}}
        with self.assertRaisesRegex(RuntimeError, "files/db_recovery/passphrase_backup"):
            NATIVE_BACKUP_ACCEPTANCE.assert_snapshot_preserved(
                before, changed_key, ignored_paths={"databases/logdate"}
            )

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

    def test_missing_key_snapshot_accepts_rejected_journal_and_retained_preferences(self):
        NATIVE_BACKUP_ACCEPTANCE.verify_missing_keys_snapshot(
            {"files/logdate.preferences_pb": {"sha256": "preferences"}},
        )

    def test_missing_key_snapshot_rejects_a_delivered_database_or_portable_key(self):
        for path in ("databases/logdate", "databases/logdate-wal", "files/db_recovery/passphrase_backup",
                     "files/identity_recovery/phrase_backup", "files/media/objects/original.png"):
            with self.subTest(path=path), self.assertRaisesRegex(RuntimeError, "missing-key restore"):
                NATIVE_BACKUP_ACCEPTANCE.verify_missing_keys_snapshot(
                    {"files/logdate.preferences_pb": {"sha256": "preferences"}, path: {"sha256": "unexpected"}},
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

        backup_call = harness.index("backup_d2d_source(serial, output)", harness.index("def exercise_d2d("))
        mutation_probe = harness.index('HARNESS.probe(serial, "post-backup-mutation"')
        restore_call = harness.index('HARNESS.adb(serial, "shell", "bmgr", "restore"')

        self.assertLess(backup_call, mutation_probe)
        self.assertLess(mutation_probe, restore_call)
        verification = harness.index('HARNESS.probe(serial, "verify-in-place"')
        self.assertLess(restore_call, verification)
        self.assertIn('recording_transport="d2d"', harness[verification:verification + 180])
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
    def test_navigation_over_loading_placeholders_is_not_a_loaded_restored_timeline(self):
        hierarchy = b'<hierarchy><node text="Search your log"/><node text="Timeline"/></hierarchy>'
        self.assertFalse(NATIVE_BACKUP_ACCEPTANCE.timeline_is_open(hierarchy))

    def test_restored_content_must_be_visible_beside_timeline_navigation(self):
        hierarchy = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
        self.assertTrue(NATIVE_BACKUP_ACCEPTANCE.timeline_is_open(hierarchy))

    def test_killed_ui_inspector_is_retried_before_accepting_the_visible_timeline(self):
        timeline = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE_BACKUP_ACCEPTANCE, "adb", side_effect=[
                b"", b"", subprocess.CalledProcessError(137, "uiautomator"),
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml", timeline, b"journal screenshot",
            ],
        ), patch.object(NATIVE_BACKUP_ACCEPTANCE.time, "sleep"):
            NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline("emulator-5582", Path(temporary), timeout_seconds=5)
            self.assertEqual(timeline, (Path(temporary) / "destination-ui.xml").read_bytes())

    def test_killed_ui_inspector_cannot_extend_the_acceptance_deadline(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE_BACKUP_ACCEPTANCE, "adb", side_effect=[
                b"", b"", subprocess.CalledProcessError(137, "uiautomator"),
            ],
        ), patch.object(NATIVE_BACKUP_ACCEPTANCE.time, "monotonic", side_effect=[0, 6]):
            with self.assertRaisesRegex(RuntimeError, "did not open its Timeline"):
                NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline("emulator-5582", Path(temporary), timeout_seconds=5)
            self.assertFalse((Path(temporary) / "destination-ui.xml").exists())

    def test_other_ui_inspector_errors_remain_failures(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE_BACKUP_ACCEPTANCE, "adb", side_effect=[
                b"", b"", subprocess.CalledProcessError(1, "uiautomator"),
            ],
        ):
            with self.assertRaises(subprocess.CalledProcessError):
                NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline("emulator-5582", Path(temporary), timeout_seconds=5)

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

    def test_digital_wellbeing_anr_waits_without_hiding_logdate_anr(self):
        template = ('<hierarchy><node package="android" resource-id="android:id/alertTitle" '
                    'text="{} isn&#x27;t responding"/><node package="android" '
                    'resource-id="android:id/aerr_wait" bounds="[80,1502][1200,1646]"/></hierarchy>')
        hierarchy = template.format("Digital Wellbeing").encode()
        self.assertEqual((640, 1574), NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(hierarchy))
        app = template.format("LogDate").encode()
        self.assertIsNone(NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(app))
        self.assertFalse(NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_dialog_present(app))

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
        timeline = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
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
        timeline = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
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
        timeline = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
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


class NativeProbeDiagnosticsTest(unittest.TestCase):
    def test_cold_instrumentation_uses_a_bounded_timeout(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        with tempfile.TemporaryDirectory() as directory, patch.object(
            harness, "adb", return_value=b"OK (1 test)\n"
        ) as adb:
            output = Path(directory)
            harness.probe("emulator-5554", "seed", output / "source-seed.log")
            harness.probe("emulator-5554", "verify", output / "destination-verify.log")
            harness.archive_probe("emulator-5554", "seed", output / "archive-seed.log")
            harness.privacy_probe("emulator-5554", "blocked", output / "privacy.log")
            harness.verify_restarted_content("emulator-5554", output, {}, "phrase")
        instrument_calls = [item for item in adb.call_args_list if "instrument" in " ".join(map(str, item.args))]
        self.assertEqual(6, len(instrument_calls))
        self.assertTrue(all(item.kwargs.get("timeout") == 600 for item in instrument_calls))

    def test_adb_timeout_preserves_original_failure_on_python_without_exception_notes(self):
        class LegacyTimeout(subprocess.TimeoutExpired):
            def __getattribute__(self, name):
                if name == "add_note":
                    raise AttributeError(name)
                return super().__getattribute__(name)

        failure = LegacyTimeout("owned-adb", 180, stderr=b"ART verification still running")
        diagnostics = io.StringIO()
        with patch.object(NATIVE_BACKUP_ACCEPTANCE, "managed_adb_command", return_value=["owned-adb"]), patch.object(
            NATIVE_BACKUP_ACCEPTANCE.subprocess, "run", side_effect=failure
        ), redirect_stderr(diagnostics):
            with self.assertRaises(LegacyTimeout) as raised:
                NATIVE_BACKUP_ACCEPTANCE.adb("emulator-5554", "shell", "am", "instrument", "-w")
        self.assertIs(failure, raised.exception)
        self.assertTrue(any("Managed ADB failure" in note for note in failure.__notes__))
        self.assertIn("Managed ADB failure", diagnostics.getvalue())
        self.assertIn("ART verification still running", diagnostics.getvalue())

    @patch.object(NATIVE_BACKUP_ACCEPTANCE, "adb", side_effect=[
        b"INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n",
        b"synthetic fatal exception\n",
    ])
    def test_failed_probe_retains_crash_diagnostics_and_still_fails(self, adb):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "source-recording-policy.log"
            with self.assertRaisesRegex(RuntimeError, "native recording-policy probe failed"):
                NATIVE_BACKUP_ACCEPTANCE.probe("emulator-5554", "recording-policy", output)
            self.assertEqual(b"synthetic fatal exception\n", output.with_suffix(".logcat.log").read_bytes())
            self.assertEqual(0o600, output.with_suffix(".logcat.log").stat().st_mode & 0o777)
            self.assertEqual(call("emulator-5554", "logcat", "-d", "-b", "crash", "-v", "threadtime"), adb.call_args_list[-1])


class RecordingTransferProbeContractTest(unittest.TestCase):
    def test_cloud_and_d2d_probes_pass_distinct_recording_expectations(self):
        metadata = {
            "owner": "synthetic-owner", "device": "synthetic-device", "fingerprint": "synthetic-fingerprint",
            "mediaNonce": "AAAAAAAAAAAAAAAA", "mediaCiphertext": "synthetic-ciphertext",
            "recordingTransferFixture": True,
        }
        with tempfile.TemporaryDirectory() as directory, patch.object(
            NATIVE_BACKUP_ACCEPTANCE, "adb", return_value=b"OK (1 test)\n"
        ) as adb:
            output = Path(directory)
            NATIVE_BACKUP_ACCEPTANCE.probe("emulator-5554", "verify-restored", output / "cloud.log", metadata)
            cloud = adb.call_args.args
            NATIVE_BACKUP_ACCEPTANCE.probe(
                "emulator-5554", "verify-in-place", output / "d2d.log", metadata,
                recording_transport="d2d",
            )
            d2d = adb.call_args.args
        self.assertEqual("cloud", cloud[cloud.index("sourceRecordingTransport") + 1])
        self.assertEqual("d2d", d2d[d2d.index("sourceRecordingTransport") + 1])


class CloudRestoreProcessRestartProbeTest(unittest.TestCase):
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb", return_value=b"OK (9 tests)")
    def test_account_transition_probe_runs_all_nine_runtime_cases(self, adb):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "account-transitions.log"
            NATIVE_RESTORE_WORK_ACCEPTANCE.probe("emulator-5554", "account-transitions", output)
            self.assertEqual(b"OK (9 tests)", output.read_bytes())
            arguments = adb.call_args.args
            self.assertIn("app.logdate.client.e2e.AndroidSyncAccountTransitionRuntimeTest", arguments)
            self.assertIn("syncAccountTransitions", arguments)
            self.assertNotIn("cloudRestoreWorkMode", arguments)

    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb", return_value=b"OK (6 tests)")
    def test_account_transition_probe_rejects_missing_runtime_cases(self, adb):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(RuntimeError):
                NATIVE_RESTORE_WORK_ACCEPTANCE.probe("emulator-5554", "account-transitions", Path(directory) / "result.log")

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
                call("emulator-5554", "shell", "cmd", "connectivity", "airplane-mode", "disable", timeout=5),
                call("emulator-5554", "shell", "svc", "wifi", "enable", timeout=5),
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

    @patch.object(time, "sleep")
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "radio_state", return_value=(True, b"0"))
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb")
    def test_radio_failure_reports_expected_and_observed_state(self, adb, radio_state, sleep):
        with self.assertRaisesRegex(RuntimeError, "expected.*False.*1.*observed.*True.*0"):
            NATIVE_RESTORE_WORK_ACCEPTANCE.restore_radio_state("emulator-5554", (False, b"1"))


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


class PinnedNativeTransferArchiveTest(unittest.TestCase):
    def test_selected_database_hash_is_taken_from_transport_snapshot(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        prefix = f"apps/{harness.PACKAGE}/f/"
        nested = PartialNativeTransportTest.make_tar(
            [(prefix + "native_journal_transfer/current/database/logdate", b"selected encrypted journal"),
             (prefix + "db_recovery/unrelated", b"unrelated")]
        )
        outer = PartialNativeTransportTest.make_tar([(f"1/_full/{harness.PACKAGE}", nested)])

        selected = harness.selected_database_snapshot(outer)

        self.assertEqual(len(b"selected encrypted journal"), selected["bytes"])
        self.assertEqual(
            hashlib.sha256(b"selected encrypted journal").hexdigest(), selected["sha256"]
        )

    def test_selected_database_hash_rejects_missing_snapshot(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        nested = PartialNativeTransportTest.make_tar(
            [(f"apps/{harness.PACKAGE}/f/db_recovery/unrelated", b"unrelated")]
        )
        outer = PartialNativeTransportTest.make_tar([(f"1/_full/{harness.PACKAGE}", nested)])

        with self.assertRaisesRegex(RuntimeError, "selected journal database"):
            harness.selected_database_snapshot(outer)

    def test_missing_key_fixture_removes_selected_database_key_and_identity_keys_only(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        prefix = f"apps/{harness.PACKAGE}/f/"
        selected_key = prefix + "native_journal_transfer/current/files/db_recovery/passphrase_backup"
        identity_key = prefix + "identity_recovery/phrase_backup"
        media_key = prefix + "identity_recovery/media_key_backup"
        retained = {
            prefix + "native_journal_transfer/current/database/logdate": b"selected journal",
            prefix + "native_journal_transfer/current/files/logdate.preferences_pb": b"selected preferences",
            prefix + "media/objects/photo.png": b"original",
            prefix + "db_recovery/unrelated": b"unrelated",
        }
        nested = PartialNativeTransportTest.make_tar(
            [(selected_key, b"database key"), (identity_key, b"identity key"), (media_key, b"media key"), *retained.items()]
        )
        outer = PartialNativeTransportTest.make_tar([(f"1/_full/{harness.PACKAGE}", nested)])

        filtered = harness.without_portable_recovery_keys(outer)

        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as transport:
            full = transport.extractfile(f"1/_full/{harness.PACKAGE}")
            self.assertIsNotNone(full)
            with tarfile.open(fileobj=full, mode="r:") as app_backup:
                names = {member.name for member in app_backup.getmembers()}
                self.assertTrue({selected_key, identity_key, media_key}.isdisjoint(names))
                self.assertEqual(retained, {name: app_backup.extractfile(name).read() for name in retained})

    def test_missing_key_fixture_rejects_an_archive_without_the_selected_key(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        prefix = f"apps/{harness.PACKAGE}/f/"
        nested = PartialNativeTransportTest.make_tar(
            [(prefix + "db_recovery/passphrase_backup", b"stale live key"),
             (prefix + "identity_recovery/phrase_backup", b"identity key")]
        )
        outer = PartialNativeTransportTest.make_tar([(f"1/_full/{harness.PACKAGE}", nested)])

        with self.assertRaisesRegex(RuntimeError, "expected portable key"):
            harness.without_portable_recovery_keys(outer)


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
    def test_credentials_only_prepares_an_encrypted_cloud_transport(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        calls = []

        def adb(serial, *arguments, **kwargs):
            calls.append(arguments)
            if arguments == ("shell", "getprop", "ro.kernel.qemu"):
                return b"1"
            if arguments == ("shell", "id", "-u"):
                return b"0"
            return b""

        with patch.object(harness, "adb", side_effect=adb), patch.object(harness, "wait_for_package_manager"), patch.object(
            harness, "ensure_emulator_setup_complete"
        ), patch.object(harness, "select_local_transport"):
            harness.prepare("emulator-5554", cloud_backup=True)

        self.assertIn(
            ("shell", "settings", "put", "secure", "backup_local_transport_parameters",
             "is_encrypted=true,is_device_transfer=false"),
            calls,
        )

    def test_cloud_source_archive_requires_journal_and_live_recovery_key(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        prefix = f"apps/{harness.PACKAGE}"
        entries = [
            (prefix + "/_manifest", b"manifest"),
            (prefix + "/db/logdate", b"encrypted journal"),
            (prefix + "/f/db_recovery/passphrase_backup", b"cloud key"),
        ]
        full_backup_name = f"1/_full/{harness.PACKAGE}"

        def outer(items):
            return self.make_tar([(full_backup_name, self.make_tar(items)), ("1/@pm@", b"metadata")])

        harness.assert_cloud_backup_contents(outer(entries))
        for missing in (entries[1][0], entries[2][0]):
            with self.subTest(missing=missing), self.assertRaisesRegex(RuntimeError, "cloud backup"):
                harness.assert_cloud_backup_contents(outer([item for item in entries if item[0] != missing]))
        for forbidden in (
            prefix + "/f/native_journal_transfer/current/database/logdate",
            prefix + "/f/media/objects/original.jpg",
        ):
            with self.subTest(forbidden=forbidden), self.assertRaisesRegex(RuntimeError, "cloud backup included"):
                harness.assert_cloud_backup_contents(outer([*entries, (forbidden, b"must not be cloud backed up")]))

    def test_credentials_only_preserves_one_recovery_directory_when_source_orders_it_after_key(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        prefix = f"apps/{harness.PACKAGE}"
        full_backup = self.make_tar([
            (prefix + "/_manifest", b"manifest"),
            (prefix + "/f/db_recovery/passphrase_backup", b"db-key"),
            (prefix + "/f/db_recovery/", b""),
            (prefix + "/f/identity_recovery/phrase_backup", b"identity-key"),
            (prefix + "/f/identity_recovery/media_key_backup", b"media-key"),
            (prefix + "/f/logdate.preferences_pb", harness.length_delimited_field(
                1, self.preference_entry("identity.canonical_owner_id", "native-test-owner")
            )),
        ])

        filtered = harness.credentials_only_from_full_backup(full_backup)

        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as archive:
            directories = [member for member in archive.getmembers() if member.isdir() and member.name.rstrip("/") == prefix + "/f/db_recovery"]
        self.assertEqual(1, len(directories))

    def test_credentials_only_outer_transport_retains_android_metadata(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        prefix = f"apps/{harness.PACKAGE}"
        full_backup = self.make_tar([
            (prefix + "/_manifest", b"manifest"),
            (prefix + "/f/db_recovery/passphrase_backup", b"db-key"),
            (prefix + "/f/identity_recovery/phrase_backup", b"identity-key"),
            (prefix + "/f/identity_recovery/media_key_backup", b"media-key"),
            (prefix + "/f/logdate.preferences_pb", harness.length_delimited_field(
                1, self.preference_entry("identity.canonical_owner_id", "native-test-owner")
            )),
            (prefix + "/db/logdate", b"encrypted journal"),
        ])
        outer = self.make_tar([(f"1/_full/{harness.PACKAGE}", full_backup), ("1/@pm@", b"metadata")])

        filtered = harness.credentials_only(outer)

        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as transport:
            self.assertEqual(b"metadata", transport.extractfile("1/@pm@").read())
            with tarfile.open(fileobj=io.BytesIO(transport.extractfile(f"1/_full/{harness.PACKAGE}").read()), mode="r:") as app_backup:
                names = {member.name for member in app_backup.getmembers()}
                self.assertIn(prefix + "/f/db_recovery/passphrase_backup", names)
                self.assertNotIn(prefix + "/db/logdate", names)

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
            (prefix + "/db/logdate", b"encrypted journal"),
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
        ]
        backup = self.make_tar(entries)

        filtered = NATIVE_BACKUP_ACCEPTANCE.credentials_only_from_full_backup(backup)

        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as archive:
            members = {member.name: member for member in archive.getmembers()}
            preferences_data = archive.extractfile(prefix + "/f/logdate.preferences_pb").read()
            database_key = archive.extractfile(prefix + "/f/db_recovery/passphrase_backup").read()
        self.assertIn(prefix + "/_manifest", members)
        self.assertTrue(members[prefix + "/f/db_recovery"].isdir())
        self.assertTrue(members[prefix + "/f/identity_recovery"].isdir())
        self.assertIn(prefix + "/f/db_recovery/passphrase_backup", members)
        self.assertEqual(b"db-key", database_key)
        self.assertNotIn(prefix + "/f/native_journal_transfer/current/files/db_recovery/passphrase_backup", members)
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

    def test_rejects_credentials_only_archive_without_cloud_database_key(self):
        package = NATIVE_BACKUP_ACCEPTANCE.PACKAGE
        prefix = f"apps/{package}"
        backup = self.make_tar([
            (prefix + "/_manifest", b"manifest"),
            (prefix + "/f/native_journal_transfer/current/files/db_recovery/passphrase_backup", b"selected D2D key"),
            (prefix + "/f/identity_recovery/phrase_backup", b"identity-key"),
            (prefix + "/f/identity_recovery/media_key_backup", b"media-key"),
            (prefix + "/f/logdate.preferences_pb", NATIVE_BACKUP_ACCEPTANCE.length_delimited_field(
                1, self.preference_entry("identity.canonical_owner_id", "native-test-owner")
            )),
        ])

        with self.assertRaisesRegex(RuntimeError, "portable recovery keys"):
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
