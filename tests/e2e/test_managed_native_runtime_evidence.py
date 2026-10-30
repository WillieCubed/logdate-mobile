from contextlib import redirect_stderr
from pathlib import Path
import io
import os
import json
import runpy
import tempfile
import unittest
from unittest.mock import Mock
import subprocess

MODULE = runpy.run_path(
    str(Path(__file__).with_name("managed-native-restore-acceptance.py"))
)


class ManagedDriverControllerTest(unittest.TestCase):
    def test_process_snapshot_retries_a_transient_host_ps_timeout(self):
        execute = Mock(
            side_effect=[
                subprocess.TimeoutExpired("ps", 15),
                subprocess.CompletedProcess([], 0, b"123 qemu-system-aarch64\n"),
            ]
        )
        sleep = Mock()

        lines = MODULE["process_snapshot"](execute=execute, sleep=sleep)

        self.assertEqual(["123 qemu-system-aarch64"], lines)
        self.assertEqual(2, execute.call_count)
        sleep.assert_called_once_with(0.2)

    def test_process_snapshot_gives_up_after_bounded_host_ps_timeouts(self):
        execute = Mock(side_effect=subprocess.TimeoutExpired("ps", 15))

        self.assertIsNone(MODULE["process_snapshot"](execute=execute, sleep=Mock()))
        self.assertEqual(2, execute.call_count)

    def test_managed_pair_boot_budget_starts_with_first_owned_qemu_not_gradle(self):
        deadline = None
        first_qemu_observed_at = 900.0
        deadline = MODULE["managed_boot_deadline"](
            deadline, first_qemu_observed_at, 600.0
        )
        self.assertEqual(1500.0, deadline)
        deadline = MODULE["managed_boot_deadline"](deadline, 1_200.0, 600.0)
        self.assertEqual(
            1500.0, deadline, "Later QEMU observations must not reset the boot budget"
        )

    def test_cleanup_diagnostics_preserve_a_failure_on_python_without_exception_notes(
        self,
    ):
        class LegacyFailure(RuntimeError):
            def __getattribute__(self, name):
                if name == "add_note":
                    raise AttributeError(name)
                return super().__getattribute__(name)

        failure = LegacyFailure("managed restore failed")
        diagnostics = io.StringIO()
        with redirect_stderr(diagnostics):
            MODULE["add_failure_note"](failure, "Could not retain managed results")
        self.assertEqual("managed restore failed", str(failure))
        self.assertEqual(["Could not retain managed results"], failure.__notes__)
        self.assertIn("Could not retain managed results", diagnostics.getvalue())

    def test_diagnostic_stream_failure_does_not_replace_the_primary_error(self):
        failure = RuntimeError("managed restore failed")
        unavailable = io.StringIO()
        unavailable.close()
        with redirect_stderr(unavailable):
            MODULE["add_failure_note"](failure, "Could not retain managed results")
        self.assertEqual(["Could not retain managed results"], failure.__notes__)

    def test_emulator_preflight_is_headless_and_retains_its_private_output(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            command = Mock(
                return_value=subprocess.CompletedProcess(
                    [], 0, b"emulator version", b"diagnostic"
                )
            )
            MODULE["verify_managed_emulator"](output / "sdk", output, command=command)
            command.assert_called_once_with(
                str(output / "sdk/emulator/emulator"),
                "-no-window",
                "-version",
                check=False,
            )
            log = output / "emulator-preflight.log"
            self.assertIn(b"emulator version", log.read_bytes())
            self.assertIn(b"diagnostic", log.read_bytes())
            self.assertEqual(0o600, log.stat().st_mode & 0o777)

    def test_failed_emulator_preflight_preserves_bounded_loader_evidence_and_exit(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            result = subprocess.CompletedProcess(
                ["emulator"], 127, b"version", b"missing library\n" + b"x" * 20_000
            )
            with self.assertRaises(subprocess.CalledProcessError) as failure:
                MODULE["verify_managed_emulator"](
                    output / "sdk", output, command=Mock(return_value=result)
                )
            self.assertEqual(127, failure.exception.returncode)
            evidence = (output / "emulator-preflight.log").read_bytes()
            self.assertIn(b"missing library", evidence)
            self.assertLess(len(evidence), 33_000)

    def test_failed_terminal_generation_retains_only_its_own_results(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            results = root / "gradle-results"
            results.mkdir()
            (results / "current.log").write_text("current native failure")
            (results / "old.log").write_text("obsolete generation")
            os.utime(results / "old.log", ns=(1, 1))
            output = root / "private-run"
            output.mkdir()
            process = Mock(poll=Mock(return_value=1))
            retained = MODULE["retain_terminal_results"](process, results, output, 2)
            self.assertIsNotNone(retained)
            self.assertEqual(
                "current native failure", (retained / "current.log").read_text()
            )
            self.assertFalse((retained / "old.log").exists())
            self.assertEqual(0o600, (retained / "current.log").stat().st_mode & 0o777)

    def test_live_generation_results_are_not_copied_as_terminal_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            self.assertIsNone(
                MODULE["retain_terminal_results"](
                    Mock(poll=Mock(return_value=None)), root / "results", root, 1
                )
            )
            self.assertFalse((root / "managed-results").exists())

    def test_setup_process_can_disappear_before_initial_identity_capture(self):
        for failure in (
            subprocess.CalledProcessError(1, "ps"),
            RuntimeError("The owned process is no longer live"),
        ):
            self.assertIsNone(
                MODULE["discovery_identity"](
                    12, process_identity=Mock(side_effect=failure)
                )
            )
        identity = {"birth": "one", "command": "owned-qemu"}
        self.assertEqual(
            identity,
            MODULE["discovery_identity"](
                12, process_identity=Mock(return_value=identity)
            ),
        )

    def test_receipt_observation_timeout_preserves_the_live_generation(self):
        identity = {"birth": "one", "command": "owned-qemu"}
        observer = MODULE["observe_driver_receipt"]
        calls = []

        def command(*args, **kwargs):
            calls.append(args)
            if len(calls) == 1:
                raise subprocess.TimeoutExpired(args, 15)
            return Mock(stdout=b'{"phase":"ready"}')

        process_identity = Mock(return_value=identity)
        candidate = {"pid": 12, "identity": identity}
        self.assertIsNone(
            observer(
                "/adb",
                "emulator-5554",
                "nonce",
                candidate,
                command=command,
                process_identity=process_identity,
            )
        )
        self.assertEqual(
            {"phase": "ready"},
            observer(
                "/adb",
                "emulator-5554",
                "nonce",
                candidate,
                command=command,
                process_identity=process_identity,
            ),
        )
        self.assertEqual(2, len(calls))
        self.assertEqual(4, process_identity.call_count)

    def test_receipt_timeout_cannot_adopt_a_changed_discovery_process(self):
        command = Mock(side_effect=subprocess.TimeoutExpired("adb", 15))
        process_identity = Mock(side_effect=[{"birth": "one"}, {"birth": "reused"}])
        self.assertIsNone(
            MODULE["observe_driver_receipt"](
                "/adb",
                "emulator-5554",
                "nonce",
                {"pid": 12, "identity": {"birth": "one"}},
                command=command,
                process_identity=process_identity,
            )
        )

    def test_changed_pre_ready_process_cannot_supply_a_stale_receipt(self):
        for identities in (
            [{"birth": "changed"}],
            [{"birth": "one"}, {"birth": "changed"}],
        ):
            command = Mock(return_value=Mock(stdout=b'{"phase":"ready"}'))
            self.assertIsNone(
                MODULE["observe_driver_receipt"](
                    "/adb",
                    "emulator-5554",
                    "nonce",
                    {"pid": 12, "identity": {"birth": "one"}},
                    command=command,
                    process_identity=Mock(side_effect=identities),
                )
            )
            self.assertEqual(len(identities) - 1, command.call_count)

    def test_enrollment_keeps_server_and_outer_driver_alive_for_all_draft_phases(self):
        budget = MODULE["acceptance_budget"](0, mode="enrollment")
        self.assertEqual(1800, budget["native"])
        self.assertGreaterEqual(
            budget["driverMillis"],
            1000 * (budget["boot"] + budget["native"] + budget["teardown"]),
        )
        self.assertEqual(
            1200, MODULE["acceptance_budget"](0, mode="transfer")["native"]
        )

    def test_durably_saves_private_custody_before_device_commands(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "custody.json"
            MODULE["save"](path, {"nonce": "one", "targets": {}})
            self.assertEqual(
                {"nonce": "one", "targets": {}}, json.loads(path.read_text())
            )
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            MODULE["save"](path, {"nonce": "two"})
            self.assertEqual({"nonce": "two"}, json.loads(path.read_text()))
            self.assertFalse(path.with_suffix(".json.part").exists())

    def test_requires_both_exact_outer_driver_cases(self):
        case = '<testcase classname="app.logdate.tools.restoredriver.NativeRestoreDriverTest" name="retainsManagedLifecycle1"/>'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for index, name in enumerate(("source", "destination"), start=1):
                (directory / f"TEST-{name}.xml").write_text(
                    f"<testsuite>{case.replace('Lifecycle1', f'Lifecycle{index}')}</testsuite>"
                )
            MODULE["require_outer_results"](directory)
            for bad in (
                case.replace("/>", "><skipped/></testcase>"),
                case.replace("/>", "><failure/></testcase>"),
                case.replace("/>", "><error/></testcase>"),
                case.replace("retainsManaged", "unrelated"),
                case,
            ):
                (directory / "TEST-destination.xml").write_text(
                    f"<testsuite>{bad}</testsuite>"
                )
                with self.assertRaises(RuntimeError):
                    MODULE["require_outer_results"](directory)

    def test_canceled_outer_cases_do_not_count_as_completed(self):
        for message in (
            "Test driver was canceled",
            "DeferredCoroutine was cancelled",
            "job cancellation",
        ):
            with (
                self.subTest(message=message),
                tempfile.TemporaryDirectory() as temporary,
            ):
                directory = Path(temporary)
                cases = "".join(
                    f'<testcase classname="app.logdate.tools.restoredriver.NativeRestoreDriverTest" '
                    f'name="retainsManagedLifecycle{index}" time="1.25"/>'
                    for index in (1, 2)
                )
                (directory / "TEST-pair.xml").write_text(
                    f'<testsuite tests="2" failures="0" errors="0" skipped="0">{cases}'
                    f"<system-err>{message}</system-err></testsuite>"
                )
                with self.assertRaisesRegex(RuntimeError, "canceled"):
                    MODULE["require_outer_results"](directory)

    def test_invalid_outer_case_durations_do_not_count_as_completed(self):
        for duration in ("-1.0", "nan", "inf", "not-a-duration"):
            with (
                self.subTest(duration=duration),
                tempfile.TemporaryDirectory() as temporary,
            ):
                directory = Path(temporary)
                cases = "".join(
                    f'<testcase classname="app.logdate.tools.restoredriver.NativeRestoreDriverTest" '
                    f'name="retainsManagedLifecycle{index}" time="{duration}"/>'
                    for index in (1, 2)
                )
                (directory / "TEST-pair.xml").write_text(
                    f"<testsuite>{cases}</testsuite>"
                )
                with self.assertRaisesRegex(RuntimeError, "duration"):
                    MODULE["require_outer_results"](directory)

    def test_completed_outer_pair_allows_ordinary_diagnostics(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            cases = "".join(
                f'<testcase classname="app.logdate.tools.restoredriver.NativeRestoreDriverTest" '
                f'name="retainsManagedLifecycle{index}" time="1.25"/>'
                for index in (1, 2)
            )
            (directory / "TEST-pair.xml").write_text(
                f"<testsuite>{cases}<system-err>ordinary diagnostics</system-err></testsuite>"
            )
            MODULE["require_outer_results"](directory)

    def test_console_port_rejects_ambiguous_or_non_loopback_listener(self):
        self.assertEqual(
            5558, MODULE["console_port"]("n127.0.0.1:5558\nn127.0.0.1:5559\n")
        )
        self.assertIsNone(MODULE["console_port"]("n*:5558\n"))
        self.assertIsNone(MODULE["console_port"]("n127.0.0.1:5558\nn127.0.0.1:5560\n"))


if __name__ == "__main__":
    unittest.main()
