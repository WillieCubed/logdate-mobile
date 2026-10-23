from contextlib import contextmanager
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "work_offline", Path(__file__).with_name("native-restore-work-restart-acceptance.py")
)
WORK = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(WORK)


class WorkOfflineAdmissionTest(unittest.TestCase):
    @contextmanager
    def network(self, snapshots, radio=(True, b"0"), read_latency=0, read_failure=None):
        commands = []
        clock = [0.0]
        reads = [0]

        def adb(serial, *arguments, **kwargs):
            self.assertEqual("emulator-5554", serial)
            commands.append(arguments)
            if arguments == ("shell", "dumpsys", "connectivity"):
                clock[0] += read_latency
                if read_failure is not None:
                    raise read_failure
                value = snapshots[min(reads[0], len(snapshots) - 1)]
                reads[0] += 1
                return value
            return b""

        with patch.object(WORK, "adb", side_effect=adb), patch.object(
            WORK, "radio_state", return_value=radio
        ), patch.object(WORK.time, "monotonic", side_effect=lambda: clock[0]), patch.object(
            WORK.time, "sleep", side_effect=lambda duration: clock.__setitem__(0, clock[0] + duration)
        ):
            yield commands, reads, clock

    def test_delayed_disconnect_is_observed_before_admission(self):
        with tempfile.TemporaryDirectory() as temporary, self.network([
            b"Active default network: 100\n", b"Active default network: none\n"
        ]) as (commands, reads, clock):
            output = Path(temporary)
            WORK.set_offline("emulator-5554", output)
            self.assertEqual(2, reads[0])
            self.assertGreater(clock[0], 0)
            self.assertEqual(("shell", "cmd", "connectivity", "airplane-mode", "enable"), commands[0])
            self.assertEqual(("shell", "svc", "wifi", "disable"), commands[1])
            capture = output / "offline-connectivity.log"
            self.assertIn(b"Active default network: none", capture.read_bytes())
            self.assertEqual(0o600, capture.stat().st_mode & 0o777)

    def test_radio_settings_cannot_hide_a_persistent_default_network(self):
        raw = b"Active default network: 100\nNetworkAgentInfo TRANSPORT_ETHERNET\n"
        with tempfile.TemporaryDirectory() as temporary, self.network([raw]) as (_, reads, clock):
            output = Path(temporary)
            with self.assertRaisesRegex(RuntimeError, "offline"):
                WORK.set_offline("emulator-5554", output)
            self.assertGreater(reads[0], 1)
            self.assertLessEqual(clock[0], 10.25)
            self.assertIn(b"TRANSPORT_ETHERNET", (output / "offline-connectivity.log").read_bytes())

    def test_no_default_network_does_not_replace_radio_readback(self):
        with self.network([b"Active default network: none\n"], radio=(False, b"1")):
            with self.assertRaisesRegex(RuntimeError, "offline"):
                WORK.set_offline("emulator-5554")

    def test_unknown_or_ambiguous_network_readback_fails_closed(self):
        for snapshot in (b"", b"Active default network: unknown\n",
                         b"Active default network: none\nActive default network: 100\n"):
            with self.subTest(snapshot=snapshot), self.network([snapshot]):
                with self.assertRaisesRegex(RuntimeError, "readback"):
                    WORK.set_offline("emulator-5554")

    def test_a_late_offline_read_cannot_be_admitted(self):
        with self.network([b"Active default network: none\n"], read_latency=11):
            with self.assertRaisesRegex(RuntimeError, "[Oo]ffline"):
                WORK.set_offline("emulator-5554")

    def test_every_admission_command_uses_the_remaining_budget(self):
        clock, reads = [0.0], []

        def adb(serial, *arguments, timeout=180):
            reads.append((timeout, clock[0]))
            clock[0] += 0.1
            if arguments == ("shell", "dumpsys", "connectivity"):
                return b"Active default network: none\n"
            if arguments[-1] == "airplane_mode_on":
                return b"1"
            return b"0"

        with patch.object(WORK, "adb", side_effect=adb), patch.object(
            WORK.time, "monotonic", side_effect=lambda: clock[0]
        ):
            WORK.set_offline("emulator-5554")
        self.assertEqual(5, len(reads))
        for timeout, started in reads:
            self.assertGreater(timeout, 0)
            self.assertLessEqual(timeout, min(5, 10 - started))

    def test_radio_read_failure_retains_last_network_and_original_failure(self):
        failure = RuntimeError("Wi-Fi readback unavailable")
        with tempfile.TemporaryDirectory() as temporary, self.network([b"Active default network: none\n"]), patch.object(
            WORK, "radio_state", side_effect=failure
        ):
            output = Path(temporary)
            with self.assertRaises(RuntimeError) as raised:
                WORK.set_offline("emulator-5554", output)
            self.assertIs(failure, raised.exception)
            self.assertTrue((output / "offline-connectivity.log").is_file())
            self.assertIn(b"Active default network: none", (output / "offline-connectivity.log").read_bytes())
            self.assertFalse(json.loads((output / "offline-radio.json").read_text())["admitted"])

    def test_command_timeout_retains_failure_metadata(self):
        failure = subprocess.TimeoutExpired("owned-adb", 5)
        with tempfile.TemporaryDirectory() as temporary, self.network([b""], read_failure=failure):
            output = Path(temporary)
            with self.assertRaises(subprocess.TimeoutExpired) as raised:
                WORK.set_offline("emulator-5554", output)
            self.assertIs(failure, raised.exception)
            capture = output / "offline-radio.json"
            self.assertTrue(capture.is_file())
            self.assertEqual("TimeoutExpired", json.loads(capture.read_text())["failure"])
            self.assertEqual(0o600, capture.stat().st_mode & 0o777)

    def test_failed_airplane_restore_still_attempts_wifi_and_readback(self):
        calls = []

        def adb(serial, *arguments, **kwargs):
            calls.append(arguments)
            if arguments[:3] == ("shell", "cmd", "connectivity"):
                raise RuntimeError("airplane command failed")

        with patch.object(WORK, "adb", side_effect=adb), patch.object(WORK, "radio_state", return_value=(False, b"1")):
            try:
                WORK.restore_radio_state("emulator-5554", (False, b"1"))
            except RuntimeError as failure:
                self.fail(f"Verified restoration stopped before trying both controls: {failure}")
        self.assertIn(("shell", "svc", "wifi", "enable"), calls)

    def test_failed_admission_restores_radio_without_starting_account_tests(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app, probe = root / "app.apk", root / "test.apk"
            app.touch()
            probe.touch()
            output = root / "evidence"
            original = (False, b"1")
            arguments = ["work", "--emulator", "emulator-5554", "--app-apk", str(app),
                         "--test-apk", str(probe), "--output", str(output)]
            with patch("sys.argv", arguments), patch.object(WORK, "adb", return_value=b"1"), patch.object(
                WORK, "require_empty_install"
            ), patch.object(WORK, "radio_state", return_value=original), patch.object(
                WORK, "set_offline", side_effect=RuntimeError("offline admission failed")
            ) as admission, patch.object(WORK, "probe") as instrumentation, patch.object(
                WORK, "restore_radio_state"
            ) as restore:
                with self.assertRaisesRegex(RuntimeError, "offline admission failed"):
                    WORK.main()
                admission.assert_called_once_with("emulator-5554", output)
                self.assertEqual(["cleanup"], [call.args[1] for call in instrumentation.call_args_list])
                restore.assert_called_once_with("emulator-5554", original)


if __name__ == "__main__":
    unittest.main()
