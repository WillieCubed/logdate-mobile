import argparse
import ast
from contextlib import redirect_stderr
import hashlib
import io
from pathlib import Path
import subprocess
import tarfile
import tempfile
import time
import unittest
from unittest.mock import call, patch
from native_backup_test_support import (
    SCRIPT,
    NATIVE_BACKUP_ACCEPTANCE,
    NATIVE_RESTORE_WORK_ACCEPTANCE,
    D2D_SCRIPT,
    BackupPayloadFixture,
)


class NativeD2DRestoreEvidenceTest(unittest.TestCase):
    def test_single_device_simulation_changes_fixture_after_backup_before_restore(self):
        harness = D2D_SCRIPT.read_text()
        probe_path = (
            SCRIPT.parents[2]
            / "app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/NativeBackupTransferProbeTest.kt"
        )
        probe = probe_path.read_text()

        backup_call = harness.index(
            "backup_d2d_source(serial, output)", harness.index("def exercise_d2d(")
        )
        mutation_probe = harness.index('HARNESS.probe(serial, "post-backup-mutation"')
        restore_call = harness.index('HARNESS.adb(serial, "shell", "bmgr", "restore"')

        self.assertLess(backup_call, mutation_probe)
        self.assertLess(mutation_probe, restore_call)
        verification = harness.index('HARNESS.probe(serial, "verify-in-place"')
        self.assertLess(restore_call, verification)
        self.assertIn(
            'recording_transport="d2d"', harness[verification : verification + 180]
        )
        self.assertIn('"post-backup-mutation"', probe)
        self.assertIn("Mutated after native D2D backup", probe)
        self.assertIn('"Native transfer journal"', probe)
        marker_check = probe.index(
            'File(context.noBackupFilesDir, "native-backup-fixture.json")'
        )
        title_check = probe.index('assertEquals("Native transfer journal"')
        mutation_write = probe.index(
            'journals.update(journal.copy(title = "Mutated after native D2D backup"))'
        )
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
        with self.assertRaisesRegex(
            RuntimeError, "Android did not report the runtime grant"
        ):
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
                call(
                    "emulator-5554",
                    "shell",
                    "settings",
                    "get",
                    "secure",
                    "user_setup_complete",
                ),
                call(
                    "emulator-5554",
                    "shell",
                    "settings",
                    "put",
                    "secure",
                    "user_setup_complete",
                    "1",
                ),
                call(
                    "emulator-5554",
                    "shell",
                    "settings",
                    "get",
                    "secure",
                    "user_setup_complete",
                ),
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
    def test_wakes_destination_and_dismisses_keyguard_before_timeline_assertion(
        self, adb
    ):
        NATIVE_BACKUP_ACCEPTANCE.wake_emulator("emulator-5554")

        self.assertEqual(
            [
                call("emulator-5554", "shell", "input", "keyevent", "KEYCODE_WAKEUP"),
                call("emulator-5554", "shell", "wm", "dismiss-keyguard"),
            ],
            adb.call_args_list,
        )
