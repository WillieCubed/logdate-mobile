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


class NativeProbeDiagnosticsTest(unittest.TestCase):
    def test_cold_instrumentation_uses_a_bounded_timeout(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(harness, "adb", return_value=b"OK (1 test)\n") as adb,
        ):
            output = Path(directory)
            harness.probe("emulator-5554", "seed", output / "source-seed.log")
            harness.probe("emulator-5554", "verify", output / "destination-verify.log")
            harness.archive_probe("emulator-5554", "seed", output / "archive-seed.log")
            harness.privacy_probe("emulator-5554", "blocked", output / "privacy.log")
            harness.verify_restarted_content("emulator-5554", output, {}, "phrase")
        instrument_calls = [
            item
            for item in adb.call_args_list
            if "instrument" in " ".join(map(str, item.args))
        ]
        self.assertEqual(6, len(instrument_calls))
        self.assertTrue(
            all(item.kwargs.get("timeout") == 600 for item in instrument_calls)
        )

    def test_adb_timeout_preserves_original_failure_on_python_without_exception_notes(
        self,
    ):
        class LegacyTimeout(subprocess.TimeoutExpired):
            def __getattribute__(self, name):
                if name == "add_note":
                    raise AttributeError(name)
                return super().__getattribute__(name)

        failure = LegacyTimeout(
            "owned-adb", 180, stderr=b"ART verification still running"
        )
        diagnostics = io.StringIO()
        with (
            patch.object(
                NATIVE_BACKUP_ACCEPTANCE,
                "managed_adb_command",
                return_value=["owned-adb"],
            ),
            patch.object(
                NATIVE_BACKUP_ACCEPTANCE.subprocess, "run", side_effect=failure
            ),
            redirect_stderr(diagnostics),
        ):
            with self.assertRaises(LegacyTimeout) as raised:
                NATIVE_BACKUP_ACCEPTANCE.adb(
                    "emulator-5554", "shell", "am", "instrument", "-w"
                )
        self.assertIs(failure, raised.exception)
        self.assertTrue(
            any("Managed ADB failure" in note for note in failure.__notes__)
        )
        self.assertIn("Managed ADB failure", diagnostics.getvalue())
        self.assertIn("ART verification still running", diagnostics.getvalue())

    @patch.object(
        NATIVE_BACKUP_ACCEPTANCE,
        "adb",
        side_effect=[
            b"INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n",
            b"synthetic fatal exception\n",
        ],
    )
    def test_failed_probe_retains_crash_diagnostics_and_still_fails(self, adb):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "source-recording-policy.log"
            with self.assertRaisesRegex(
                RuntimeError, "native recording-policy probe failed"
            ):
                NATIVE_BACKUP_ACCEPTANCE.probe(
                    "emulator-5554", "recording-policy", output
                )
            self.assertEqual(
                b"synthetic fatal exception\n",
                output.with_suffix(".logcat.log").read_bytes(),
            )
            self.assertEqual(
                0o600, output.with_suffix(".logcat.log").stat().st_mode & 0o777
            )
            self.assertEqual(
                call(
                    "emulator-5554", "logcat", "-d", "-b", "crash", "-v", "threadtime"
                ),
                adb.call_args_list[-1],
            )

    def test_failed_privacy_ui_probe_keeps_screen_evidence_and_still_fails(self):
        harness = NATIVE_BACKUP_ACCEPTANCE
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(
                harness,
                "adb",
                side_effect=[
                    b"FAILURES!!!\n",
                    b"\x89PNG\r\n\x1a\nsynthetic",
                    b"<hierarchy/>",
                ],
            ) as adb,
        ):
            output = Path(directory) / "privacy.log"
            with self.assertRaisesRegex(
                RuntimeError, "native privacy blocked probe failed"
            ):
                harness.privacy_probe("emulator-5554", "blocked", output)
            self.assertEqual(
                b"\x89PNG\r\n\x1a\nsynthetic", output.with_suffix(".png").read_bytes()
            )
            self.assertEqual(b"<hierarchy/>", output.with_suffix(".xml").read_bytes())
            self.assertTrue(
                all(call.kwargs.get("timeout") == 20 for call in adb.call_args_list[1:])
            )
            self.assertEqual(0o600, output.with_suffix(".png").stat().st_mode & 0o777)

    def test_privacy_failure_retains_callback_and_delivery_diagnostics(self):
        harness = NATIVE_BACKUP_ACCEPTANCE

        def response(serial, *args, **kwargs):
            if "instrument" in args:
                return b"FAILURES!!!\n"
            if "logcat" in args:
                return b"synthetic callback failure\n"
            return b"synthetic private metadata\n"

        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(harness, "adb", side_effect=response),
        ):
            output = Path(directory) / "resolved.log"
            with self.assertRaisesRegex(
                RuntimeError, "native privacy resolved probe failed"
            ):
                harness.privacy_probe("emulator-5554", "resolved", output)
            self.assertEqual(
                b"synthetic callback failure\n",
                output.with_suffix(".callbacks.log").read_bytes(),
            )
            self.assertEqual(
                b"synthetic private metadata\n",
                output.with_suffix(".custody.log").read_bytes(),
            )
            self.assertEqual(
                b"synthetic private metadata\n",
                output.with_suffix(".delivery.log").read_bytes(),
            )

    def test_unavailable_screen_output_is_not_mislabeled_as_an_image(self):
        harness = NATIVE_BACKUP_ACCEPTANCE

        def response(serial, *args, **kwargs):
            return b"FAILURES!!!\n" if "instrument" in args else b"cat: no such file\n"

        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(harness, "adb", side_effect=response),
        ):
            output = Path(directory) / "unavailable.log"
            with self.assertRaisesRegex(
                RuntimeError, "native privacy resolved probe failed"
            ):
                harness.privacy_probe("emulator-5554", "resolved", output)
            self.assertFalse(output.with_suffix(".png").exists())
            self.assertFalse(output.with_suffix(".xml").exists())


class RecordingTransferProbeContractTest(unittest.TestCase):
    def test_cloud_and_d2d_probes_pass_distinct_recording_expectations(self):
        metadata = {
            "owner": "synthetic-owner",
            "device": "synthetic-device",
            "fingerprint": "synthetic-fingerprint",
            "mediaNonce": "AAAAAAAAAAAAAAAA",
            "mediaCiphertext": "synthetic-ciphertext",
            "recordingTransferFixture": True,
        }
        with (
            tempfile.TemporaryDirectory() as directory,
            patch.object(
                NATIVE_BACKUP_ACCEPTANCE, "adb", return_value=b"OK (1 test)\n"
            ) as adb,
        ):
            output = Path(directory)
            NATIVE_BACKUP_ACCEPTANCE.probe(
                "emulator-5554", "verify-restored", output / "cloud.log", metadata
            )
            cloud = adb.call_args.args
            NATIVE_BACKUP_ACCEPTANCE.probe(
                "emulator-5554",
                "verify-in-place",
                output / "d2d.log",
                metadata,
                recording_transport="d2d",
            )
            d2d = adb.call_args.args
        self.assertEqual("cloud", cloud[cloud.index("sourceRecordingTransport") + 1])
        self.assertEqual("d2d", d2d[d2d.index("sourceRecordingTransport") + 1])


class CloudRestoreProcessRestartProbeTest(unittest.TestCase):
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb", return_value=b"OK (9 tests)")
    def test_account_transition_probe_runs_all_nine_runtime_cases(self, adb):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "account-transitions.log"
            NATIVE_RESTORE_WORK_ACCEPTANCE.probe(
                "emulator-5554", "account-transitions", output
            )
            self.assertEqual(b"OK (9 tests)", output.read_bytes())
            arguments = adb.call_args.args
            self.assertIn(
                "app.logdate.client.e2e.AndroidSyncAccountTransitionRuntimeTest",
                arguments,
            )
            self.assertIn("syncAccountTransitions", arguments)
            self.assertNotIn("cloudRestoreWorkMode", arguments)

    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb", return_value=b"OK (6 tests)")
    def test_account_transition_probe_rejects_missing_runtime_cases(self, adb):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(RuntimeError):
                NATIVE_RESTORE_WORK_ACCEPTANCE.probe(
                    "emulator-5554",
                    "account-transitions",
                    Path(directory) / "result.log",
                )

    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb", return_value=b"OK (1 test)")
    def test_targets_only_the_explicit_emulator_and_records_probe_output(self, adb):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "after-process-restart.log"

            NATIVE_RESTORE_WORK_ACCEPTANCE.probe("emulator-5554", "verify", output)

            self.assertEqual(b"OK (1 test)", output.read_bytes())
            adb.assert_called_once_with(
                "emulator-5554",
                "shell",
                "am",
                "instrument",
                "-w",
                "-r",
                "-e",
                "class",
                NATIVE_RESTORE_WORK_ACCEPTANCE.PROBE,
                "-e",
                "cloudRestoreWorkMode",
                "verify",
                NATIVE_RESTORE_WORK_ACCEPTANCE.RUNNER,
            )

    def test_refuses_physical_device_serials(self):
        with self.assertRaisesRegex(
            argparse.ArgumentTypeError, "explicit emulator-NNNN serial"
        ):
            NATIVE_RESTORE_WORK_ACCEPTANCE.emulator_serial("R5CT123456")

    @patch.object(time, "sleep")
    @patch.object(
        NATIVE_RESTORE_WORK_ACCEPTANCE,
        "radio_state",
        side_effect=[
            (True, b"0", (1, False, False)),
            (True, b"0", (1, False, False)),
            (False, b"1", (1, True, True)),
        ],
    )
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb")
    def test_waits_for_android_to_apply_restored_radio_state(
        self, adb, radio_state, sleep
    ):
        original = (False, b"1", (1, True, True))

        NATIVE_RESTORE_WORK_ACCEPTANCE.restore_radio_state("emulator-5554", original)

        self.assertEqual(3, radio_state.call_count)
        sleep.assert_called_once()
        self.assertEqual(
            [
                call(
                    "emulator-5554",
                    "shell",
                    "cmd",
                    "connectivity",
                    "airplane-mode",
                    "disable",
                    timeout=5,
                ),
                call("emulator-5554", "shell", "svc", "wifi", "enable", timeout=5),
                call("emulator-5554", "shell", "svc", "data", "enable", timeout=5),
            ],
            adb.call_args_list,
        )

    @patch.object(time, "sleep")
    @patch.object(
        NATIVE_RESTORE_WORK_ACCEPTANCE,
        "radio_state",
        return_value=(True, b"0", (1, False, False)),
    )
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb")
    def test_fails_if_radio_state_does_not_return_to_original(
        self, adb, radio_state, sleep
    ):
        with self.assertRaisesRegex(
            RuntimeError, "Could not restore the emulator's original network state"
        ):
            NATIVE_RESTORE_WORK_ACCEPTANCE.restore_radio_state(
                "emulator-5554", (False, b"1", (1, True, True))
            )

        self.assertGreater(radio_state.call_count, 1)

    @patch.object(time, "sleep")
    @patch.object(
        NATIVE_RESTORE_WORK_ACCEPTANCE,
        "radio_state",
        return_value=(True, b"0", (1, False, False)),
    )
    @patch.object(NATIVE_RESTORE_WORK_ACCEPTANCE, "adb")
    def test_radio_failure_reports_expected_and_observed_state(
        self, adb, radio_state, sleep
    ):
        with self.assertRaisesRegex(
            RuntimeError, "expected.*False.*1.*observed.*True.*0"
        ):
            NATIVE_RESTORE_WORK_ACCEPTANCE.restore_radio_state(
                "emulator-5554", (False, b"1", (1, True, True))
            )
