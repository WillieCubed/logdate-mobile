import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "native_restart", Path(__file__).with_name("native-backup-acceptance.py")
)
NATIVE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(NATIVE)


class RestartContentTest(unittest.TestCase):
    def test_read_only_assertion_precedes_any_mutating_verification(self):
        events = []
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE, "adb", side_effect=lambda *args: events.append("read-only") or b"OK (1 test)\n"
        ), patch.object(NATIVE, "probe", side_effect=lambda *args: events.append("verify")):
            NATIVE.verify_restarted_content("emulator-5580", Path(temporary), {}, "phrase")
        self.assertEqual(["read-only", "verify"], events)

    def test_missing_note_failure_cannot_be_repaired_by_mutating_probe(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE, "adb", return_value=b"FAILURES!!!\nTests run: 1, Failures: 1\n"
        ), patch.object(NATIVE, "probe") as probe:
            with self.assertRaisesRegex(RuntimeError, "retained offline edit"):
                NATIVE.verify_restarted_content("emulator-5580", Path(temporary), {}, "phrase")
            probe.assert_not_called()

    def test_skipped_read_only_probe_is_not_acceptance(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE, "adb", return_value=b"INSTRUMENTATION_STATUS_CODE: -4\nOK (1 test)\n"
        ), patch.object(NATIVE, "probe") as probe:
            with self.assertRaisesRegex(RuntimeError, "retained offline edit"):
                NATIVE.verify_restarted_content("emulator-5580", Path(temporary), {}, "phrase")
            probe.assert_not_called()


if __name__ == "__main__":
    unittest.main()
