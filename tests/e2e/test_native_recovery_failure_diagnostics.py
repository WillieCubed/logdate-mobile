import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "native_recovery_diagnostics",
    Path(__file__).with_name("native-backup-acceptance.py"),
)
HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HARNESS)


class NativeRecoveryFailureDiagnosticsTest(unittest.TestCase):
    def test_failed_recovery_keeps_failure_and_captures_read_only_state_privately(self):
        calls = []

        def adb(serial, *args, **kwargs):
            calls.append((args, kwargs))
            if "instrument" in args:
                return b"FAILURES!!!\nTests run: 1, Failures: 1\n"
            return b"owned synthetic diagnostic metadata"

        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(HARNESS, "adb", side_effect=adb),
        ):
            output = Path(directory) / "destination.log"
            with self.assertRaisesRegex(RuntimeError, "native data-only probe failed"):
                HARNESS.probe("emulator-5554", "data-only", output)
            for suffix in (".jobs.log", ".custody.log", ".recovery-state.log"):
                evidence = output.with_suffix(suffix)
                self.assertTrue(evidence.is_file(), suffix)
                self.assertEqual(
                    b"owned synthetic diagnostic metadata", evidence.read_bytes()
                )
                self.assertEqual(0o600, evidence.stat().st_mode & 0o777)
            self.assertIn(b"FAILURES", output.read_bytes())
        diagnostic_calls = calls[2:]
        self.assertEqual(3, len(diagnostic_calls))
        self.assertTrue(all(kwargs["timeout"] == 20 for _, kwargs in diagnostic_calls))
        self.assertFalse(any("payload" in args for args, _ in diagnostic_calls))
        self.assertIn("entry.json", diagnostic_calls[1][0])

    def test_collection_failure_cannot_turn_failed_acceptance_into_success(self):
        def adb(serial, *args, **kwargs):
            if "instrument" in args:
                return b"FAILURES!!!\n"
            if "jobscheduler" in args:
                raise subprocess.TimeoutExpired("owned diagnostic", 20)
            return b"owned metadata"

        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(HARNESS, "adb", side_effect=adb),
        ):
            output = Path(directory) / "destination.log"
            with self.assertRaisesRegex(
                RuntimeError, "native data-only probe failed"
            ) as failure:
                HARNESS.probe("emulator-5554", "data-only", output)
            self.assertTrue(
                any(
                    "jobs" in note
                    for note in getattr(failure.exception, "__notes__", [])
                )
            )
            self.assertFalse(output.with_suffix(".jobs.log").exists())
            self.assertEqual(b"FAILURES!!!\n", output.read_bytes())
            self.assertTrue(output.with_suffix(".custody.log").exists())

    def test_successful_recovery_does_not_collect_failure_state(self):
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(HARNESS, "adb", return_value=b"OK (1 test)\n") as adb,
        ):
            output = Path(directory) / "destination.log"
            HARNESS.probe("emulator-5554", "data-only", output)
            adb.assert_called_once()
            self.assertEqual([output], list(Path(directory).iterdir()))


if __name__ == "__main__":
    unittest.main()
