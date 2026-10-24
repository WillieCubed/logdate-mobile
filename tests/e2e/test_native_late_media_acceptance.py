from pathlib import Path
import runpy
import unittest

ROOT = Path(__file__).resolve().parents[2]
MANAGED = runpy.run_path(str(ROOT / "tests/e2e/managed-native-restore-acceptance.py"))
MEDIA = runpy.run_path(str(ROOT / "tests/e2e/native-late-media-acceptance.py"))
RESULTS = runpy.run_path(str(ROOT / "tests/e2e/native-instrumentation-results.py"))


class NativeLateMediaAcceptanceTest(unittest.TestCase):
    def test_managed_route_keeps_both_phases_in_one_owned_generation(self):
        command = MANAGED["scenario_command"](
            "late-media", "emulator-5554", "emulator-5556",
            Path("/private/app.apk"), Path("/private/test.apk"), Path("/private/output"), [],
        )
        self.assertEqual(str(ROOT / "tests/e2e/native-late-media-acceptance.py"), command[1])
        self.assertEqual(2, len(MEDIA["PHASES"]))
        self.assertEqual("--destination", command[4])
        self.assertEqual("emulator-5556", command[5])
        self.assertEqual("--seed-empty-source", command[-1])
        receipt = MANAGED["acceptance_receipt"]("late-media", [], [])
        self.assertFalse(receipt["managedNativeTransport"])
        self.assertFalse(receipt["crossDeviceTransferAccepted"])

    def test_each_phase_requires_one_pass_without_skips(self):
        accepted = RESULTS["instrumentation_passed"]
        self.assertTrue(accepted(b"INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\n", 1))
        self.assertFalse(accepted(b"INSTRUMENTATION_STATUS_CODE: -3\nOK (1 test)\n", 1))
        self.assertFalse(accepted(b"INSTRUMENTATION_STATUS_CODE: 0\nOK (0 tests)\n", 1))


if __name__ == "__main__":
    unittest.main()
