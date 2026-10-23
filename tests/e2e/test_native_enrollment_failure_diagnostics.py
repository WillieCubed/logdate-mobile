from pathlib import Path
import io
import inspect
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

ENROLLMENT = runpy.run_path(str(Path(__file__).with_name("native-device-enrollment-acceptance.py")))
MEDIA = runpy.run_path(str(Path(__file__).with_name("native-missing-media-acceptance.py")))


class EnrollmentFailureDiagnosticsTest(unittest.TestCase):
    def test_enrollment_probe_failure_captures_before_caller_cleanup(self):
        events = []
        capture = Mock(side_effect=lambda *args: events.append("capture"))
        helper = {"adb": Mock(return_value=b"FAILURES!!!"), "private_file": Mock(),
                  "instrumentation_passed": Mock(return_value=False)}
        with patch.dict(ENROLLMENT["probe"].__globals__, H=helper, checked_emulator=Mock(),
                        capture_failure_diagnostics=capture):
            with self.assertRaisesRegex(RuntimeError, "Enrollment source-prepare failed"):
                try:
                    ENROLLMENT["probe"]("emulator-5554", "source-prepare", Path("private/source-prepare.log"))
                finally:
                    events.append("caller cleanup")
        self.assertEqual(["capture", "caller cleanup"], events)
        capture.assert_called_once_with("emulator-5554", Path("private/failure-diagnostics/source-prepare"))

    def test_enrollment_probe_capture_failure_preserves_original_oracle(self):
        helper = {"adb": Mock(return_value=b"FAILURES!!!"), "private_file": Mock(),
                  "instrumentation_passed": Mock(return_value=False)}
        with patch.dict(ENROLLMENT["probe"].__globals__, H=helper, checked_emulator=Mock(),
                        capture_failure_diagnostics=Mock(side_effect=OSError("capture unavailable"))):
            with self.assertRaisesRegex(RuntimeError, "Enrollment source-prepare failed") as raised:
                ENROLLMENT["probe"]("emulator-5554", "source-prepare", Path("private/source-prepare.log"))
        self.assertTrue(any("diagnostics" in note for note in getattr(raised.exception, "__notes__", [])))

    def test_successful_enrollment_probe_does_not_capture(self):
        capture = Mock()
        helper = {"adb": Mock(return_value=b"OK (1 test)"), "private_file": Mock(),
                  "instrumentation_passed": Mock(return_value=True)}
        with patch.dict(ENROLLMENT["probe"].__globals__, H=helper, checked_emulator=Mock(),
                        capture_failure_diagnostics=capture):
            ENROLLMENT["probe"]("emulator-5554", "source-prepare", Path("private/source-prepare.log"))
        capture.assert_not_called()

    def test_failed_oracle_is_captured_before_all_cleanup_and_remains_primary(self):
        self.assertIn("failure_diagnostics", inspect.signature(MEDIA["instrument"]).parameters)
        events = []
        process = Mock(stdout=io.BytesIO(b"FAILURES!!!\nTests run: 1, Failures: 1\n"))
        process.wait.return_value = 0
        process.poll.return_value = 0

        def adb(*arguments):
            events.append(arguments[-1])
            raise RuntimeError("cleanup failed")

        with tempfile.TemporaryDirectory() as temporary, patch.dict(
            MEDIA["instrument"].__globals__, safe_target=Mock(), adb=adb, managed_adb_command=Mock(return_value=["owned-adb"])
        ), patch.object(MEDIA["subprocess"], "Popen", return_value=process):
            with self.assertRaisesRegex(RuntimeError, "Emulator acceptance failed") as raised:
                MEDIA["instrument"]("emulator-5558", "fixture", Path(temporary) / "probe.log",
                                    failure_diagnostics=lambda *args: events.append("capture"))
        self.assertEqual(["capture", MEDIA["PACKAGE"], MEDIA["PACKAGE"] + ".test"], events)
        self.assertEqual(2, len(raised.exception.__notes__))

    def test_capture_failure_cannot_replace_oracle_or_prevent_cleanup(self):
        self.assertIn("failure_diagnostics", inspect.signature(MEDIA["instrument"]).parameters)
        process = Mock(stdout=io.BytesIO(b"FAILURES!!!\n"))
        process.wait.return_value = 0
        process.poll.return_value = 0
        adb = Mock()
        with tempfile.TemporaryDirectory() as temporary, patch.dict(
            MEDIA["instrument"].__globals__, safe_target=Mock(), adb=adb, managed_adb_command=Mock(return_value=["owned-adb"])
        ), patch.object(MEDIA["subprocess"], "Popen", return_value=process):
            with self.assertRaisesRegex(RuntimeError, "Emulator acceptance failed") as raised:
                MEDIA["instrument"]("emulator-5558", "fixture", Path(temporary) / "probe.log",
                                    failure_diagnostics=Mock(side_effect=RuntimeError("capture failed")))
        self.assertEqual(2, adb.call_count)
        self.assertTrue(any("diagnostics" in note for note in getattr(raised.exception, "__notes__", [])))

    def test_success_does_not_collect_failure_diagnostics(self):
        self.assertIn("failure_diagnostics", inspect.signature(MEDIA["instrument"]).parameters)
        process = Mock(stdout=io.BytesIO(b"OK (1 test)\n"))
        process.wait.return_value = 0
        process.poll.return_value = 0
        capture = Mock()
        with tempfile.TemporaryDirectory() as temporary, patch.dict(
            MEDIA["instrument"].__globals__, safe_target=Mock(), managed_adb_command=Mock(return_value=["owned-adb"])
        ), patch.object(MEDIA["subprocess"], "Popen", return_value=process):
            MEDIA["instrument"]("emulator-5558", "fixture", Path(temporary) / "probe.log",
                                failure_diagnostics=capture)
        capture.assert_not_called()

    def test_status_action_primary_survives_transcript_finalizer_and_all_cleanup(self):
        primary = AssertionError("original status action")
        process = Mock(stdout=io.BytesIO(b"MARKER\n"))
        process.poll.return_value = None
        process.kill.side_effect = OSError("kill finalizer failed")
        adb = Mock()
        original_write = Path.write_bytes

        def write(path, content):
            if content:
                raise OSError("transcript finalizer failed")
            return original_write(path, content)

        with tempfile.TemporaryDirectory() as temporary, patch.dict(
            MEDIA["instrument"].__globals__, safe_target=Mock(), adb=adb,
            managed_adb_command=Mock(return_value=["owned-adb"])
        ), patch.object(MEDIA["subprocess"], "Popen", return_value=process), patch.object(Path, "write_bytes", write):
            observed = None
            try:
                MEDIA["instrument"]("emulator-5558", "fixture", Path(temporary) / "probe.log",
                                    status_actions={b"MARKER": Mock(side_effect=primary)})
            except BaseException as failure:
                observed = failure
        self.assertIs(primary, observed)
        self.assertEqual(2, adb.call_count)
        process.kill.assert_called_once()
        self.assertEqual(2, len(primary.__notes__))

    def test_transcript_finalization_failure_alone_still_fails(self):
        process = Mock(stdout=io.BytesIO(b"OK (1 test)\n"))
        process.poll.return_value = 0
        process.wait.return_value = 0
        original_write = Path.write_bytes

        def write(path, content):
            if content:
                raise OSError("transcript finalizer failed")
            return original_write(path, content)

        with tempfile.TemporaryDirectory() as temporary, patch.dict(
            MEDIA["instrument"].__globals__, safe_target=Mock(), adb=Mock(),
            managed_adb_command=Mock(return_value=["owned-adb"])
        ), patch.object(MEDIA["subprocess"], "Popen", return_value=process), patch.object(Path, "write_bytes", write):
            with self.assertRaisesRegex(OSError, "transcript finalizer failed"):
                MEDIA["instrument"]("emulator-5558", "fixture", Path(temporary) / "probe.log")

    def test_capture_has_one_shared_time_and_output_budget_and_no_privilege_change(self):
        capture = ENROLLMENT.get("capture_failure_diagnostics")
        self.assertIsNotNone(capture, "Enrollment failures need bounded diagnostics before cleanup")
        now = [0.0]
        calls = []

        def adb(serial, *arguments, timeout):
            calls.append((arguments, timeout))
            now[0] += 3
            return b"1\n" if arguments[-1] == "ro.kernel.qemu" else b"x" * 100_000

        with tempfile.TemporaryDirectory() as temporary, patch.dict(ENROLLMENT["H"], adb=adb):
            output = Path(temporary) / "diagnostics"
            capture("emulator-5558", output, clock=lambda: now[0])
            artifacts = list(output.glob("*.txt"))
            self.assertTrue(artifacts)
            self.assertTrue(all(p.stat().st_size <= 65_536 and p.stat().st_mode & 0o777 == 0o600 for p in artifacts))
        self.assertLessEqual(now[0], 15)
        self.assertTrue(all(timeout <= 3 for _, timeout in calls))
        self.assertFalse(any(argument in {"root", "su", "kill-server", "start-server"}
                             for arguments, _ in calls for argument in arguments))

    def test_unsafe_capture_stops_before_any_diagnostic_command(self):
        capture = ENROLLMENT.get("capture_failure_diagnostics")
        self.assertIsNotNone(capture)
        adb = Mock(return_value=b"0\n")
        with tempfile.TemporaryDirectory() as temporary, patch.dict(ENROLLMENT["H"], adb=adb):
            with self.assertRaisesRegex(RuntimeError, "emulator"):
                capture("emulator-5558", Path(temporary) / "diagnostics")
        self.assertEqual(1, adb.call_count)

    def test_enrollment_cleanup_preserves_primary_and_attempts_both_radios(self):
        self.assertIn("primary", inspect.signature(ENROLLMENT["cleanup"]).parameters)
        primary = AssertionError("original oracle")
        process = Mock()
        process.poll.return_value = None
        process.wait.side_effect = subprocess.TimeoutExpired("fixture", 10)
        restore = Mock(side_effect=RuntimeError("radio failure"))
        with patch.dict(ENROLLMENT["cleanup"].__globals__, checked_emulator=Mock()), patch.dict(
            ENROLLMENT["H"], restore_radio_state=restore
        ):
            ENROLLMENT["cleanup"](process, {"emulator-5558": {}, "emulator-5562": {}}, primary=primary)
        self.assertEqual(2, restore.call_count)
        self.assertEqual(3, len(primary.__notes__))

    def test_enrollment_cleanup_failure_alone_still_fails(self):
        with patch.dict(ENROLLMENT["cleanup"].__globals__, checked_emulator=Mock()), patch.dict(
            ENROLLMENT["H"], restore_radio_state=Mock(side_effect=RuntimeError("radio failure"))
        ):
            with self.assertRaisesRegex(RuntimeError, "Enrollment cleanup failed"):
                ENROLLMENT["cleanup"](None, {"emulator-5558": {}})


if __name__ == "__main__":
    unittest.main()
