from pathlib import Path
import runpy
import unittest
from unittest.mock import Mock, call, patch

ENROLLMENT = runpy.run_path(
    str(Path(__file__).with_name("native-device-enrollment-acceptance.py"))
)


class RetainedDraftScreenshotTest(unittest.TestCase):
    def test_extracted_receipt_reads_the_entry_points_current_private_name(self):
        adb = Mock(return_value=b'{"deviceId":"synthetic-device"}')
        with (
            patch.dict(
                ENROLLMENT["receipt"].__globals__,
                checked_emulator=Mock(),
                RECEIPT="alternate-private-receipt.json",
            ),
            patch.dict(ENROLLMENT["H"], adb=adb),
        ):
            self.assertEqual(
                {"deviceId": "synthetic-device"}, ENROLLMENT["receipt"]("emulator-5558")
            )
        adb.assert_called_once_with(
            "emulator-5558",
            "exec-out",
            "run-as",
            ENROLLMENT["PACKAGE"],
            "cat",
            "no_backup/alternate-private-receipt.json",
        )

    def test_passed_probe_collects_actual_marked_png(self):
        raw = b"INSTRUMENTATION_STATUS: retainedDraftScreenshot=saved\nOK (1 test)\n"
        png = b"\x89PNG\r\n\x1a\nsynthetic-host-response"
        adb = Mock(side_effect=[raw, png])
        private_file = Mock()
        output = Path("private/destination-editor-shortcut.log")
        with (
            patch.dict(
                ENROLLMENT["retained_draft_probe"].__globals__, checked_emulator=Mock()
            ),
            patch.dict(ENROLLMENT["H"], adb=adb, private_file=private_file),
        ):
            ENROLLMENT["retained_draft_probe"]("emulator-5558", "verify", output)
        self.assertEqual(2, adb.call_count)
        self.assertEqual(
            call(
                "emulator-5558",
                "exec-out",
                "run-as",
                ENROLLMENT["PACKAGE"],
                "cat",
                "no_backup/native-retained-draft-shortcut.png",
            ),
            adb.call_args_list[1],
        )
        self.assertEqual(
            [call(output, raw), call(output.with_suffix(".png"), png)],
            private_file.call_args_list,
        )

    def test_no_marker_never_reads_a_stale_screenshot(self):
        for raw, failed in ((b"OK (1 test)\n", False), (b"FAILURES!!!\n", True)):
            with self.subTest(failed=failed):
                adb = Mock(return_value=raw)
                private_file = Mock()
                output = Path("private/probe.log")
                with (
                    patch.dict(
                        ENROLLMENT["retained_draft_probe"].__globals__,
                        checked_emulator=Mock(),
                    ),
                    patch.dict(ENROLLMENT["H"], adb=adb, private_file=private_file),
                ):
                    if failed:
                        with self.assertRaisesRegex(
                            RuntimeError, "Current-editor draft verify did not pass"
                        ):
                            ENROLLMENT["retained_draft_probe"](
                                "emulator-5558", "verify", output
                            )
                    else:
                        ENROLLMENT["retained_draft_probe"](
                            "emulator-5558", "verify", output
                        )
                adb.assert_called_once()
                private_file.assert_called_once_with(output, raw)

    def test_failed_assertion_stays_primary_when_marked_capture_read_fails(self):
        raw = b"INSTRUMENTATION_STATUS: retainedDraftScreenshot=saved\nFAILURES!!!\n"
        adb = Mock(side_effect=[raw, OSError("secondary capture error")])
        with (
            patch.dict(
                ENROLLMENT["retained_draft_probe"].__globals__, checked_emulator=Mock()
            ),
            patch.dict(ENROLLMENT["H"], adb=adb, private_file=Mock()),
        ):
            with self.assertRaisesRegex(
                RuntimeError, "Current-editor draft verify did not pass"
            ) as raised:
                ENROLLMENT["retained_draft_probe"](
                    "emulator-5558", "verify", Path("private/probe.log")
                )
        self.assertTrue(
            any(
                "screenshot collection also failed: OSError" in note
                for note in raised.exception.__notes__
            )
        )

    def test_failed_assertion_stays_primary_when_png_write_fails(self):
        raw = b"INSTRUMENTATION_STATUS: retainedDraftScreenshot=saved\nFAILURES!!!\n"
        private_file = Mock(side_effect=[None, OSError("secondary write error")])
        with (
            patch.dict(
                ENROLLMENT["retained_draft_probe"].__globals__, checked_emulator=Mock()
            ),
            patch.dict(
                ENROLLMENT["H"],
                adb=Mock(side_effect=[raw, b"png"]),
                private_file=private_file,
            ),
        ):
            with self.assertRaisesRegex(
                RuntimeError, "Current-editor draft verify did not pass"
            ) as raised:
                ENROLLMENT["retained_draft_probe"](
                    "emulator-5558", "verify", Path("private/probe.log")
                )
        self.assertTrue(
            any(
                "screenshot collection also failed: OSError" in note
                for note in raised.exception.__notes__
            )
        )

    def test_success_with_marker_cannot_hide_collection_failure(self):
        raw = b"INSTRUMENTATION_STATUS: retainedDraftScreenshot=saved\nOK (1 test)\n"
        diagnostic = OSError("capture unavailable")
        with (
            patch.dict(
                ENROLLMENT["retained_draft_probe"].__globals__, checked_emulator=Mock()
            ),
            patch.dict(
                ENROLLMENT["H"],
                adb=Mock(side_effect=[raw, diagnostic]),
                private_file=Mock(),
            ),
        ):
            with self.assertRaisesRegex(
                RuntimeError, "screenshot collection failed"
            ) as raised:
                ENROLLMENT["retained_draft_probe"](
                    "emulator-5558", "verify", Path("private/probe.log")
                )
        self.assertIs(diagnostic, raised.exception.__cause__)


if __name__ == "__main__":
    unittest.main()
