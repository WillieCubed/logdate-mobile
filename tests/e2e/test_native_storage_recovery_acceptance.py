import importlib.util
from pathlib import Path
import subprocess
import tempfile
import types
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location(
    "storage_acceptance", Path(__file__).with_name("native-storage-recovery-acceptance.py")
)
acceptance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(acceptance)


class StorageAcceptanceTest(unittest.TestCase):
    def exercise(self, *, failure=None, initial=("UNKNOWN", "NORMAL"), reset_fails=False):
        state = list(initial)
        commands = []
        stages = []

        def adb(serial, *args):
            commands.append(args)
            if args[:2] == ("shell", "dumpsys"):
                output = f"level={state[1]} lastUsableBytes=9000000000\nmForceState={state[0]}"
                return subprocess.CompletedProcess(args, 0, output.encode(), b"")
            if args[:3] == ("shell", "cmd", "devicestoragemonitor"):
                command = args[3]
                if command == "reset" and reset_fails:
                    raise RuntimeError("Storage service unavailable")
                state[:] = {"force-low": ("LOW", "LOW"), "force-not-low": ("NORMAL", "NORMAL"),
                            "reset": initial}[command]
            return subprocess.CompletedProcess(args, 0, b"", b"")

        def instrument(serial, selector, log, extras, status_actions=None):
            mode = extras[2]
            stages.append(mode)
            if mode == failure:
                raise RuntimeError("Instrumentation failed")
            if status_actions:
                self.assertEqual(["LOW", "LOW"], state)
                status_actions[b"RESTORE_STORAGE_WAITING"]()

        with tempfile.TemporaryDirectory() as directory:
            args = types.SimpleNamespace(emulator="emulator-5580", app_apk=Path("app.apk"),
                                         test_apk=Path("test.apk"), output=Path(directory))
            with patch.object(acceptance, "adb", side_effect=adb), \
                 patch.object(acceptance, "safe_target", return_value="synthetic-owned-avd"), \
                 patch.object(acceptance, "instrument", side_effect=instrument):
                try:
                    acceptance.run(args)
                    error = None
                except RuntimeError as caught:
                    error = caught
            receipt = (args.output / "acceptance.json").exists()
        return state, commands, stages, receipt, error

    def test_failed_restart_verification_restores_storage_without_success_receipt(self):
        state, commands, stages, receipt, error = self.exercise(failure="waiting")
        self.assertEqual(["UNKNOWN", "NORMAL"], state)
        self.assertEqual(["seed", "enqueue", "waiting"], stages)
        self.assertEqual(("shell", "cmd", "devicestoragemonitor", "reset", "-f"), commands[-2])
        self.assertFalse(receipt)
        self.assertIsNotNone(error)

    def test_environment_cleanup_failure_prevents_success_receipt(self):
        _, _, stages, receipt, error = self.exercise(reset_fails=True)
        self.assertEqual(["seed", "enqueue", "waiting", "resume"], stages)
        self.assertFalse(receipt)
        self.assertIsNotNone(error)

    def test_preexisting_forced_storage_state_is_preserved(self):
        state, commands, stages, receipt, error = self.exercise(initial=("LOW", "LOW"))
        self.assertEqual(["LOW", "LOW"], state)
        self.assertFalse(any(command[:2] == ("shell", "cmd") for command in commands))
        self.assertEqual([], stages)
        self.assertFalse(receipt)
        self.assertIsNotNone(error)

    def test_non_emulator_target_is_rejected_before_adb(self):
        with self.assertRaises(ValueError):
            acceptance.safe_target("physical-device")


if __name__ == "__main__":
    unittest.main()
