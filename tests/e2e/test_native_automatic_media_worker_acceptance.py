from pathlib import Path
import runpy
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

MODULE = runpy.run_path(str(Path(__file__).with_name("native-automatic-media-worker-acceptance.py")))


class NativeAutomaticMediaWorkerControllerTest(unittest.TestCase):
    def media_fixture(self):
        def adb(serial, *arguments, **kwargs):
            if arguments[-1] == "wifi-networks":
                return SimpleNamespace(stdout=b"AndroidWifi;none\n")
            if arguments[-1] == "restrict-background":
                return SimpleNamespace(stdout=b"Restrict background status: disabled\n")
            if arguments[-1] == "airplane_mode_on":
                return SimpleNamespace(stdout=b"0\n")
            return SimpleNamespace(stdout=b"1\n")

        fixture = dict(MODULE["MEDIA"])
        fixture.update(adb=Mock(side_effect=adb), safe_target=Mock(), set_metered=Mock(), restore_environment=Mock())

        def instrument(*args, **kwargs):
            if args[2].name == "metered-recover.log":
                self.assertFalse((args[2].parent.parent / "media-gate-arm").exists(), "Startup must finish before the server is armed")
            for action in (kwargs.get("status_actions") or {}).values():
                action()

        fixture["instrument"] = Mock(side_effect=instrument)
        return fixture

    def test_requires_all_metered_saver_and_cold_restart_phases_before_acceptance(self):
        media = self.media_fixture()
        with tempfile.TemporaryDirectory() as temporary, patch.dict(MODULE["run"].__globals__, MEDIA=media):
            root = Path(temporary)
            result = MODULE["run"]("emulator-5556", root, Mock(), root / "results", Mock())
            self.assertTrue(result["automaticOriginalMediaRecovery"])
            self.assertTrue(result["mediaInboxOperationRetainedAcrossRestart"])
            self.assertEqual(4, media["instrument"].call_count)
            extras = [call.kwargs["extras"] for call in media["instrument"].call_args_list]
            self.assertEqual(["metered", "restricted", "restart", "restart"],
                             [values[values.index("nativeAutomaticMediaMode") + 1] for values in extras])
            self.assertEqual(["recover", "recover", "prepare", "verify"],
                             [values[values.index("nativeAutomaticMediaStage") + 1] for values in extras])
            media["restore_environment"].assert_called_once()
            media["adb"].assert_any_call("emulator-5556", "shell", "am", "force-stop", MEDIA_PACKAGE)

    def test_probe_failure_restores_environment_without_acceptance(self):
        media = self.media_fixture()
        media["instrument"].side_effect = RuntimeError("Original media was not recovered")
        with tempfile.TemporaryDirectory() as temporary, patch.dict(MODULE["run"].__globals__, MEDIA=media):
            root = Path(temporary)
            with self.assertRaises(RuntimeError):
                MODULE["run"]("emulator-5556", root, Mock(), root / "results", Mock())
            media["restore_environment"].assert_called_once()
            self.assertEqual(1, media["instrument"].call_count)


MEDIA_PACKAGE = MODULE["MEDIA"]["PACKAGE"]


if __name__ == "__main__":
    unittest.main()
