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


class NativeTimelineSystemDialogTest(unittest.TestCase):
    def test_navigation_over_loading_placeholders_is_not_a_loaded_restored_timeline(
        self,
    ):
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
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                NATIVE_BACKUP_ACCEPTANCE,
                "adb",
                side_effect=[
                    b"",
                    b"",
                    subprocess.CalledProcessError(137, "uiautomator"),
                    b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                    timeline,
                    b"journal screenshot",
                ],
            ),
            patch.object(NATIVE_BACKUP_ACCEPTANCE.time, "sleep"),
        ):
            NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline(
                "emulator-5582", Path(temporary), timeout_seconds=5
            )
            self.assertEqual(
                timeline, (Path(temporary) / "destination-ui.xml").read_bytes()
            )

    def test_killed_ui_inspector_cannot_extend_the_acceptance_deadline(self):
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                NATIVE_BACKUP_ACCEPTANCE,
                "adb",
                side_effect=[
                    b"",
                    b"",
                    subprocess.CalledProcessError(137, "uiautomator"),
                ],
            ),
            patch.object(
                NATIVE_BACKUP_ACCEPTANCE.time, "monotonic", side_effect=[0, 6]
            ),
        ):
            with self.assertRaisesRegex(RuntimeError, "did not open its Timeline"):
                NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline(
                    "emulator-5582", Path(temporary), timeout_seconds=5
                )
            self.assertFalse((Path(temporary) / "destination-ui.xml").exists())

    def test_other_ui_inspector_errors_remain_failures(self):
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                NATIVE_BACKUP_ACCEPTANCE,
                "adb",
                side_effect=[
                    b"",
                    b"",
                    subprocess.CalledProcessError(1, "uiautomator"),
                ],
            ),
        ):
            with self.assertRaises(subprocess.CalledProcessError):
                NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline(
                    "emulator-5582", Path(temporary), timeout_seconds=5
                )

    def test_finds_only_the_close_button_for_the_known_bluetooth_system_crash_dialog(
        self,
    ):
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

        self.assertTrue(
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_dialog_present(hierarchy)
        )
        self.assertIsNone(
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(hierarchy)
        )

    def test_digital_wellbeing_anr_waits_without_hiding_logdate_anr(self):
        template = (
            '<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            'text="{} isn&#x27;t responding"/><node package="android" '
            'resource-id="android:id/aerr_wait" bounds="[80,1502][1200,1646]"/></hierarchy>'
        )
        hierarchy = template.format("Digital Wellbeing").encode()
        self.assertEqual(
            (640, 1574),
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(hierarchy),
        )
        app = template.format("LogDate").encode()
        self.assertIsNone(
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(app)
        )
        self.assertFalse(
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_dialog_present(app)
        )

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
        self.assertTrue(
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_dialog_present(hierarchy)
        )
        self.assertEqual(
            (540, 1091),
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(hierarchy),
        )
        no_wait = hierarchy.replace("android:id/aerr_wait", "android:id/other")
        self.assertTrue(
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_dialog_present(no_wait)
        )
        self.assertIsNone(
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(no_wait)
        )
        app_owned = hierarchy.replace(
            'package="android"', 'package="studio.hypertext.logdate.debug"'
        )
        self.assertFalse(
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_dialog_present(app_owned)
        )
        self.assertIsNone(
            NATIVE_BACKUP_ACCEPTANCE.system_ui_unresponsive_wait_target(app_owned)
        )

    def test_timeline_poll_rejects_an_app_anr_even_with_timeline_content_behind_it(
        self,
    ):
        hierarchy = (
            b'<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            b'text="LogDate isn&#x27;t responding"/><node package="android" '
            b'resource-id="android:id/aerr_wait" bounds="[70,1028][1010,1154]"/>'
            b'<node text="Search your log"/><node text="Timeline"/></hierarchy>'
        )
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                NATIVE_BACKUP_ACCEPTANCE,
                "adb",
                side_effect=[
                    b"",
                    b"",
                    b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                    hierarchy,
                    b"app ANR",
                ],
            ) as adb,
        ):
            with self.assertRaisesRegex(
                RuntimeError, "replacement app did not open its Timeline"
            ):
                NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline(
                    "emulator-5582", Path(temporary), timeout_seconds=0
                )
            self.assertEqual(5, adb.call_count)

    def test_timeline_poll_waits_for_process_system_recovery_then_rechecks_content(
        self,
    ):
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
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
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
            ) as adb,
            patch.object(NATIVE_BACKUP_ACCEPTANCE.time, "sleep"),
        ):
            NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline(
                "emulator-5582", Path(temporary), timeout_seconds=5
            )

            adb.assert_any_call("emulator-5582", "shell", "input", "tap", "540", "1091")
            self.assertEqual(
                timeline, (Path(temporary) / "destination-ui.xml").read_bytes()
            )
            self.assertEqual(
                b"restored journal screenshot",
                (Path(temporary) / "destination-journal.png").read_bytes(),
            )

    def test_timeline_poll_does_not_accept_anr_overlay_without_a_usable_wait_control(
        self,
    ):
        blocked_timeline = (
            b'<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            b'text="System UI isn&#x27;t responding"/><node text="Search your log"/>'
            b'<node text="Timeline"/></hierarchy>'
        )
        timeline = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
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
            ) as adb,
            patch.object(NATIVE_BACKUP_ACCEPTANCE.time, "sleep"),
        ):
            NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline(
                "emulator-5582", Path(temporary), timeout_seconds=5
            )

            self.assertEqual(8, adb.call_count)
            self.assertEqual(
                timeline, (Path(temporary) / "destination-ui.xml").read_bytes()
            )

    def test_timeline_poll_does_not_accept_bluetooth_overlay_without_a_usable_close_control(
        self,
    ):
        blocked_timeline = (
            b'<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            b'text="Bluetooth keeps stopping"/><node text="Search your log"/>'
            b'<node text="Timeline"/></hierarchy>'
        )
        timeline = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
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
            ) as adb,
            patch.object(NATIVE_BACKUP_ACCEPTANCE.time, "sleep"),
        ):
            NATIVE_BACKUP_ACCEPTANCE.wait_for_timeline(
                "emulator-5582", Path(temporary), timeout_seconds=5
            )

            self.assertEqual(8, adb.call_count)
            self.assertEqual(
                timeline, (Path(temporary) / "destination-ui.xml").read_bytes()
            )

    def test_ignores_other_system_crash_dialogs(self):
        hierarchy = (
            '<hierarchy><node package="android" resource-id="android:id/alertTitle" '
            'text="LogDate keeps stopping"/><node package="android" '
            'resource-id="android:id/aerr_close" bounds="[70,1028][1010,1154]"/></hierarchy>'
        )

        target = NATIVE_BACKUP_ACCEPTANCE.bluetooth_crash_dialog_close_target(hierarchy)

        self.assertIsNone(target)
