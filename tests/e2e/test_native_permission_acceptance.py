import runpy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

MODULE = runpy.run_path(str(Path(__file__).with_name("native-permission-acceptance.py")))


class ShortcutAcceptanceTest(unittest.TestCase):
    def test_each_required_shortcut_case_runs_with_its_own_result(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(MODULE["h"], "instrument") as instrument:
            MODULE["verify_shortcuts"]("emulator-5558", Path(temporary))
        self.assertEqual([
            "app.logdate.client.e2e.RestoredShortcutTargetsRuntimeTest#manifestCaptureShortcutsTargetInstalledApp",
            "app.logdate.client.e2e.RestoredShortcutTargetsRuntimeTest#recreatedDraftShortcutTargetsTheRetainedDraftWithoutDuplicates",
        ], [call.args[1] for call in instrument.call_args_list])
        self.assertEqual(2, len({call.args[2] for call in instrument.call_args_list}))
        self.assertTrue(all(call.kwargs["extras"] == ["-e", "nativeShortcutAcceptance", "true"] for call in instrument.call_args_list))

    def test_a_failed_shortcut_case_stops_acceptance(self):
        failure = RuntimeError("Skipped instrumentation")
        with tempfile.TemporaryDirectory() as temporary, patch.object(MODULE["h"], "instrument", side_effect=failure) as instrument:
            with self.assertRaises(RuntimeError) as raised:
                MODULE["verify_shortcuts"]("emulator-5558", Path(temporary))
        self.assertIs(failure, raised.exception)
        self.assertEqual(1, instrument.call_count)


if __name__ == "__main__":
    unittest.main()
