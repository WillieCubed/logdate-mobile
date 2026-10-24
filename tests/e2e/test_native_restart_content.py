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
    def test_archive_restart_binds_only_the_two_seeded_journals(self):
        journals = ["c25d0779-c687-42c0-a8c5-eca000b32bfd", "c27aeea0-fab4-4bf2-92e3-1a9cad352b9f"]
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE, "adb", return_value=b"OK (1 test)\n"
        ) as adb, patch.object(NATIVE, "probe"):
            NATIVE.verify_restarted_content("emulator-5580", Path(temporary), {"archiveJournalIds": journals}, "phrase")
        arguments = adb.call_args.args
        self.assertIn("nativeRetainedArchiveJournals", arguments)
        index = arguments.index("nativeRetainedArchiveJournals")
        self.assertEqual(",".join(journals), arguments[index + 1])

    def test_invalid_archive_journal_bindings_fail_before_device_access(self):
        journal = "c25d0779-c687-42c0-a8c5-eca000b32bfd"
        for journals in (None, journal, [journal], [journal] * 3, [journal, journal.upper()], [journal, "invalid"],
                         [journal, "00000000-0000-0000-0000-000000000001"]):
            with self.subTest(journals=journals), tempfile.TemporaryDirectory() as temporary, patch.object(
                NATIVE, "adb", return_value=b"OK (1 test)\n"
            ) as adb, patch.object(NATIVE, "probe"):
                with self.assertRaises(ValueError):
                    NATIVE.verify_restarted_content("emulator-5580", Path(temporary), {"archiveJournalIds": journals}, "phrase")
                adb.assert_not_called()

    def test_read_only_assertion_precedes_any_mutating_verification(self):
        events = []
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            NATIVE, "adb", side_effect=lambda *args, **kwargs: events.append("read-only") or b"OK (1 test)\n"
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
