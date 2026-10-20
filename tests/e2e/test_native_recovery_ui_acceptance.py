import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "recovery_ui_acceptance", Path(__file__).with_name("native-recovery-ui-acceptance.py")
)
acceptance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(acceptance)


class RecoveryDisplayCleanupTest(unittest.TestCase):
    def test_previous_receipt_cannot_be_reused_as_a_new_acceptance(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            receipt = output / "acceptance.json"
            receipt.write_text('{"displayRestored": true}')
            argv = ["acceptance.py", "--emulator", "emulator-5580", "--app-apk", "app.apk",
                    "--test-apk", "test.apk", "--output", str(output), "--synthetic-data"]
            original_umask = os.umask(0o077)
            try:
                with patch.object(sys, "argv", argv), patch.object(acceptance, "run") as run:
                    with self.assertRaises(FileExistsError):
                        acceptance.main()
                    run.assert_not_called()
            finally:
                os.umask(original_umask)
            self.assertEqual('{"displayRestored": true}', receipt.read_text())

    def test_existing_display_overrides_and_font_are_restored_exactly(self):
        original = {"size": "Physical size: 1080x2400\nOverride size: 1600x1200",
                    "density": "Physical density: 420\nOverride density: 320", "font": "1.3"}
        with patch.object(acceptance, "mutate") as mutate, \
             patch.object(acceptance, "display_state", return_value=original):
            acceptance.restore_display("emulator-5580", original)
        self.assertEqual([
            ("emulator-5580", "wm", "density", "320"),
            ("emulator-5580", "wm", "size", "1600x1200"),
            ("emulator-5580", "settings", "put", "system", "font_scale", "1.3"),
        ], [call.args for call in mutate.call_args_list])

    def test_one_cleanup_failure_does_not_skip_other_restoration(self):
        original = {"size": "Physical size: 1080x2400", "density": "Physical density: 420", "font": "null"}
        with patch.object(acceptance, "mutate", side_effect=[RuntimeError("density unavailable"), None, None]) as mutate, \
             patch.object(acceptance, "display_state", return_value=original):
            with self.assertRaisesRegex(RuntimeError, "density"):
                acceptance.restore_display("emulator-5580", original)
        self.assertEqual(("emulator-5580", "wm", "size", "reset"), mutate.call_args_list[1].args)
        self.assertEqual(("emulator-5580", "settings", "delete", "system", "font_scale"), mutate.call_args_list[2].args)

    def test_a_readback_mismatch_is_a_failed_acceptance(self):
        original = {"size": "Physical size: 1080x2400", "density": "Physical density: 420", "font": "1.0"}
        with patch.object(acceptance, "mutate"), \
             patch.object(acceptance, "display_state", return_value={**original, "font": "2.0"}):
            with self.assertRaisesRegex(RuntimeError, "readback"):
                acceptance.restore_display("emulator-5580", original)

    def test_unknown_original_display_is_rejected_before_changes(self):
        with patch.object(acceptance, "adb", return_value=subprocess.CompletedProcess([], 0, b"", b"")):
            with self.assertRaisesRegex(RuntimeError, "original Android display"):
                acceptance.display_state("emulator-5580")


if __name__ == "__main__":
    unittest.main()
