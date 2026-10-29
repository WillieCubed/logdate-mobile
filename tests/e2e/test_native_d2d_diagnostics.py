"""Retain Android transport evidence without replacing a failed acceptance result."""

import importlib.util
from pathlib import Path
import json
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "d2d_diagnostics", Path(__file__).with_name("native-d2d-acceptance.py")
)
D2D = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(D2D)


class D2dDiagnosticsTest(unittest.TestCase):
    def discover(self, responses, *, read_latency=0, control_latency=0):
        clock = [0.0]
        reads = []

        def adb(serial, *arguments, timeout):
            self.assertEqual("emulator-5582", serial)
            self.assertGreater(timeout, 0)
            self.assertLessEqual(timeout, 60 - clock[0])
            if arguments == ("shell", "bmgr", "transport", D2D.GMS_BACKUP_TRANSPORT):
                clock[0] += control_latency
                return b"selected"
            if arguments == ("shell", "bmgr", "list", "transports"):
                clock[0] += control_latency
                return b"* " + D2D.GMS_BACKUP_TRANSPORT.encode()
            self.assertEqual(("shell", "bmgr", "list", "sets"), arguments)
            reads.append(timeout)
            clock[0] += read_latency
            response = responses[min(len(reads) - 1, len(responses) - 1)]
            if isinstance(response, Exception):
                raise response
            return response

        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(D2D.HARNESS, "adb", side_effect=adb),
            patch.object(D2D.HARNESS.time, "monotonic", side_effect=lambda: clock[0]),
            patch.object(
                D2D.HARNESS.time,
                "sleep",
                side_effect=lambda duration: clock.__setitem__(0, clock[0] + duration),
            ),
        ):
            output = Path(temporary)
            try:
                token, failure = (
                    D2D.wait_for_d2d_restore_set("emulator-5582", output),
                    None,
                )
            except (RuntimeError, OSError, subprocess.SubprocessError) as error:
                token, failure = None, error
            readiness_path = output / "d2d-restore-readiness.json"
            readiness = (
                json.loads(readiness_path.read_text())
                if readiness_path.exists()
                else None
            )
            snapshot_path = output / "d2d-restore-sets.log"
            snapshot = snapshot_path.read_bytes() if snapshot_path.exists() else None
            return token, failure, readiness, snapshot, clock[0], reads

    def test_empty_provider_readback_must_be_retried_before_accepting_a_d2d_set(self):
        token, failure, readiness, snapshot, _, reads = self.discover(
            [b"", b"  abc : D2D Restore Set\n"]
        )
        self.assertIsNone(failure)
        self.assertEqual("abc", token)
        self.assertEqual(2, len(reads))
        self.assertIn(b"D2D", snapshot)
        self.assertTrue(readiness["ready"])
        self.assertIn("D2D restore set", readiness["lastFailure"])

    def test_transient_transport_exception_does_not_replace_strict_discovery(self):
        token, failure, readiness, _, _, _ = self.discover(
            [
                subprocess.CalledProcessError(1, "owned-bmgr"),
                b"  42 : D2D Restore Set\n",
            ]
        )
        self.assertIsNone(failure)
        self.assertEqual("42", token)
        self.assertIn("CalledProcessError", readiness["lastFailure"])

    def test_empty_or_wrong_restore_sets_never_pass_the_existing_sixty_second_budget(
        self,
    ):
        for response in [b"", b"  abc : Other phone\n"]:
            with self.subTest(response=response):
                token, failure, readiness, snapshot, elapsed, reads = self.discover(
                    [response]
                )
                self.assertIsNone(token)
                self.assertIsNotNone(failure)
                self.assertIsNotNone(
                    readiness, "Discovery must retain its terminal readiness failure"
                )
                self.assertFalse(readiness["ready"])
                self.assertEqual(response, snapshot)
                self.assertEqual(60, elapsed)
                self.assertGreater(len(reads), 1)

    def test_a_late_valid_restore_set_is_not_acceptance(self):
        token, failure, readiness, _, _, _ = self.discover(
            [b"  abc : D2D Restore Set\n"], read_latency=61
        )
        self.assertIsNone(token)
        self.assertIsNotNone(failure)
        self.assertFalse(readiness["ready"])

    def test_transport_controls_and_readbacks_share_the_existing_deadline(self):
        token, failure, readiness, _, elapsed, reads = self.discover(
            [b"  abc : D2D Restore Set\n"],
            read_latency=20,
            control_latency=20,
        )
        self.assertIsNone(token)
        self.assertIsNotNone(failure)
        self.assertFalse(readiness["ready"])
        self.assertEqual(60, elapsed)
        self.assertEqual([20], reads)

    def test_diagnostic_failure_cannot_replace_the_last_discovery_failure(self):
        with patch.object(
            D2D.HARNESS, "private_file", side_effect=OSError("evidence unavailable")
        ):
            token, failure, _, _, _, _ = self.discover([b"  abc : Other phone\n"])
        self.assertIsNone(token)
        self.assertIsInstance(failure, RuntimeError)
        self.assertIn("60-second deadline", str(failure))
        self.assertIn("D2D restore set", str(failure.__cause__))
        self.assertIn("diagnostics could not be retained", failure.__notes__[0])

    def test_fresh_transport_backs_up_metadata_non_incrementally_with_monitoring(self):
        package = D2D.HARNESS.PACKAGE
        success = (
            f"Package @pm@ with result: Success\nPackage {package} with result: Success\n"
            "Backup finished with result: Success\n"
        ).encode()
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(D2D.HARNESS, "adb", return_value=success) as adb,
            patch.object(D2D, "selected_transport", return_value=D2D.D2D_TRANSPORT),
        ):
            D2D.backup_d2d_source("emulator-5582", Path(temporary))
        self.assertEqual(
            adb.call_args.args[1:],
            (
                "shell",
                "bmgr",
                "backupnow",
                "@pm@",
                package,
                "--non-incremental",
                "--monitor-verbose",
            ),
        )

    def test_backup_rejects_metadata_failure_even_when_app_backup_reports_success(self):
        response = f"Package @pm@ with result: Transport error\nPackage {D2D.HARNESS.PACKAGE} with result: Success\n"
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(D2D.HARNESS, "adb", return_value=response.encode()),
            patch.object(D2D, "selected_transport", return_value=D2D.D2D_TRANSPORT),
        ):
            with self.assertRaisesRegex(RuntimeError, "native backup"):
                D2D.backup_d2d_source("emulator-5582", Path(temporary))

    def test_backup_requires_successful_terminal_result(self):
        response = f"Package @pm@ with result: Success\nPackage {D2D.HARNESS.PACKAGE} with result: Success\n"
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(D2D.HARNESS, "adb", return_value=response.encode()),
            patch.object(D2D, "selected_transport", return_value=D2D.D2D_TRANSPORT),
        ):
            with self.assertRaisesRegex(RuntimeError, "native backup"):
                D2D.backup_d2d_source("emulator-5582", Path(temporary))

    def test_initialization_response_is_retained_for_selected_transport(self):
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary)
            transport = D2D.D2D_TRANSPORT.encode()
            responses = [
                transport,
                b"selected",
                b"Initialization result: Transport error",
                b"* " + transport,
            ]
            with patch.object(D2D.HARNESS, "adb", side_effect=responses):
                D2D.select_d2d_transport("emulator-5582", attempts=1, output=output)
            self.assertTrue((output / "d2d-init-1.log").is_file())
            self.assertEqual(
                (output / "d2d-init-1.log").read_bytes(),
                b"Initialization result: Transport error",
            )

    def test_failure_diagnostics_capture_transport_and_system_log(self):
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                D2D.HARNESS,
                "adb",
                return_value=b"native transport state",
            ) as adb,
        ):
            output = Path(temporary)
            D2D.capture_transport_failure("emulator-5582", output)
            self.assertTrue((output / "d2d-backup-state.log").is_file())
            self.assertEqual(
                (output / "d2d-backup-state.log").read_bytes(),
                b"native transport state",
            )
            self.assertEqual(
                (output / "d2d-logcat.log").read_bytes(), b"native transport state"
            )
            calls = [call.args[1:] for call in adb.call_args_list]
            self.assertIn(("shell", "dumpsys", "backup"), calls)
            self.assertIn(("logcat", "-d", "-b", "all", "-t", "4000"), calls)

    def test_diagnostic_failure_does_not_replace_the_acceptance_failure(self):
        with (
            tempfile.TemporaryDirectory() as temporary,
            patch.object(
                D2D.HARNESS,
                "adb",
                side_effect=RuntimeError("transport disconnected"),
            ),
        ):
            output = Path(temporary)
            D2D.capture_transport_failure("emulator-5582", output)
            self.assertTrue((output / "d2d-diagnostic-errors.log").is_file())
            self.assertIn(
                "transport disconnected",
                (output / "d2d-diagnostic-errors.log").read_text(),
            )


if __name__ == "__main__":
    unittest.main()
