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

        exercise = next(
            node
            for node in ast.parse(harness).body
            if isinstance(node, ast.FunctionDef) and node.name == "exercise_d2d"
        )
        calls = [node for node in ast.walk(exercise) if isinstance(node, ast.Call)]

        def single_call(name, mode=None, adb_prefix=None):
            matched = []
            for node in calls:
                if ast.unparse(node.func) != name:
                    continue
                if mode is not None and (
                    len(node.args) < 2
                    or not isinstance(node.args[1], ast.Constant)
                    or node.args[1].value != mode
                ):
                    continue
                if (
                    adb_prefix is not None
                    and [
                        arg.value if isinstance(arg, ast.Constant) else None
                        for arg in node.args[1:4]
                    ]
                    != adb_prefix
                ):
                    continue
                matched.append(node)
            self.assertEqual(
                1,
                len(matched),
                f"One exact {name} call required for {mode or adb_prefix}",
            )
            return matched[0]

        backup_call = single_call("backup_d2d_source")
        mutation_probe = single_call("HARNESS.probe", mode="post-backup-mutation")
        restore_call = single_call(
            "HARNESS.adb", adb_prefix=["shell", "bmgr", "restore"]
        )
        verification = single_call("HARNESS.probe", mode="verify-in-place")
        self.assertLess(backup_call.lineno, mutation_probe.lineno)
        self.assertLess(mutation_probe.lineno, restore_call.lineno)
        self.assertLess(restore_call.lineno, verification.lineno)
        self.assertTrue(
            any(
                keyword.arg == "recording_transport"
                and isinstance(keyword.value, ast.Constant)
                and keyword.value.value == "d2d"
                for keyword in verification.keywords
            )
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
