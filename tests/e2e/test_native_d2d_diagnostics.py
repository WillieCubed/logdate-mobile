"""Retain Android transport evidence without replacing a failed acceptance result."""

import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location("d2d_diagnostics", Path(__file__).with_name("native-d2d-acceptance.py"))
D2D = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(D2D)


class D2dDiagnosticsTest(unittest.TestCase):
    def test_fresh_transport_backs_up_metadata_non_incrementally_with_monitoring(self):
        package = D2D.HARNESS.PACKAGE
        success = (
            f"Package @pm@ with result: Success\nPackage {package} with result: Success\n"
            "Backup finished with result: Success\n"
        ).encode()
        with tempfile.TemporaryDirectory() as temporary, patch.object(D2D.HARNESS, "adb", return_value=success) as adb:
            D2D.backup_d2d_source("emulator-5582", Path(temporary))
        self.assertEqual(
            adb.call_args.args[1:],
            ("shell", "bmgr", "backupnow", "@pm@", package, "--non-incremental", "--monitor-verbose"),
        )

    def test_backup_rejects_metadata_failure_even_when_app_backup_reports_success(self):
        response = f"Package @pm@ with result: Transport error\nPackage {D2D.HARNESS.PACKAGE} with result: Success\n"
        with tempfile.TemporaryDirectory() as temporary, patch.object(D2D.HARNESS, "adb", return_value=response.encode()):
            with self.assertRaisesRegex(RuntimeError, "native backup"):
                D2D.backup_d2d_source("emulator-5582", Path(temporary))

    def test_backup_requires_successful_terminal_result(self):
        response = f"Package @pm@ with result: Success\nPackage {D2D.HARNESS.PACKAGE} with result: Success\n"
        with tempfile.TemporaryDirectory() as temporary, patch.object(D2D.HARNESS, "adb", return_value=response.encode()):
            with self.assertRaisesRegex(RuntimeError, "native backup"):
                D2D.backup_d2d_source("emulator-5582", Path(temporary))

    def test_initialization_response_is_retained_for_selected_transport(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            transport = D2D.D2D_TRANSPORT.encode()
            responses = [transport, b"selected", b"Initialization result: Transport error", b"* " + transport]
            with patch.object(D2D.HARNESS, "adb", side_effect=responses):
                D2D.select_d2d_transport("emulator-5582", attempts=1, output=output)
            self.assertTrue((output / "d2d-init-1.log").is_file())
            self.assertEqual(
                (output / "d2d-init-1.log").read_bytes(), b"Initialization result: Transport error",
            )

    def test_failure_diagnostics_capture_transport_and_system_log(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            D2D.HARNESS, "adb", return_value=b"native transport state",
        ) as adb:
            output = Path(temporary)
            D2D.capture_transport_failure("emulator-5582", output)
            self.assertTrue((output / "d2d-backup-state.log").is_file())
            self.assertEqual((output / "d2d-backup-state.log").read_bytes(), b"native transport state")
            self.assertEqual((output / "d2d-logcat.log").read_bytes(), b"native transport state")
            calls = [call.args[1:] for call in adb.call_args_list]
            self.assertIn(("shell", "dumpsys", "backup"), calls)
            self.assertIn(("logcat", "-d", "-b", "all", "-t", "4000"), calls)

    def test_diagnostic_failure_does_not_replace_the_acceptance_failure(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            D2D.HARNESS, "adb", side_effect=RuntimeError("transport disconnected"),
        ):
            output = Path(temporary)
            D2D.capture_transport_failure("emulator-5582", output)
            self.assertTrue((output / "d2d-diagnostic-errors.log").is_file())
            self.assertIn("transport disconnected", (output / "d2d-diagnostic-errors.log").read_text())


if __name__ == "__main__":
    unittest.main()
