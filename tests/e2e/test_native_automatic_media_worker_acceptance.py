from pathlib import Path
import runpy
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import Mock, patch

MODULE = runpy.run_path(str(Path(__file__).with_name("native-automatic-media-worker-acceptance.py")))


class NativeAutomaticMediaWorkerControllerTest(unittest.TestCase):
    def media_fixture(self, cellular=b"1"):
        def adb(serial, *arguments, **kwargs):
            if arguments[-1] == "wifi-networks":
                return SimpleNamespace(stdout=b"AndroidWifi;none\n")
            if arguments[-1] == "restrict-background":
                return SimpleNamespace(stdout=b"Restrict background status: disabled\n")
            if arguments[-1] == "airplane_mode_on":
                return SimpleNamespace(stdout=b"0\n")
            if arguments[-1] == "mobile_data":
                return SimpleNamespace(stdout=cellular + b"\n")
            return SimpleNamespace(stdout=b"1\n")

        fixture = dict(MODULE["MEDIA"])
        fixture.update(adb=Mock(side_effect=adb), safe_target=Mock(), set_metered=Mock(), restore_environment=Mock(), release_network_handoff=Mock())

        def instrument(*args, **kwargs):
            prefix = "media-upload" if args[2].name == "upload-recover.log" else "media"
            if args[2].name in ("metered-recover.log", "upload-recover.log"):
                self.assertFalse((args[2].parent.parent / (prefix + "-gate-arm")).exists(), "Startup must finish before the server is armed")
            released = False
            for marker, action in (kwargs.get("status_actions") or {}).items():
                if marker.endswith(b"POLICY_LIFTED") and kwargs.get("release_network"):
                    kwargs["release_network"]()
                    released = True
                action()
            if kwargs.get("release_network") and not released:
                kwargs["release_network"]()

        fixture["instrument"] = Mock(side_effect=instrument)
        return fixture

    def test_requires_all_metered_saver_and_cold_restart_phases_before_acceptance(self):
        media = self.media_fixture()
        with tempfile.TemporaryDirectory() as temporary, patch.dict(MODULE["run"].__globals__, MEDIA=media):
            root = Path(temporary)
            result = MODULE["run"]("emulator-5556", root, Mock(), root / "results", Mock())
            self.assertTrue(result["automaticOriginalMediaRecovery"])
            self.assertTrue(result["mediaInboxOperationRetainedAcrossRestart"])
            self.assertTrue(result["deferredOriginalUploadResumesAfterPolicyLiftDuringWorker"])
            self.assertEqual(5, media["instrument"].call_count)
            extras = [call.kwargs["extras"] for call in media["instrument"].call_args_list]
            self.assertEqual(["metered", "restricted", "restart", "restart", "upload"],
                             [values[values.index("nativeAutomaticMediaMode") + 1] for values in extras])
            self.assertEqual(["recover", "recover", "prepare", "verify", "recover"],
                             [values[values.index("nativeAutomaticMediaStage") + 1] for values in extras])
            media["restore_environment"].assert_called_once()
            media["adb"].assert_any_call("emulator-5556", "shell", "am", "force-stop", MEDIA_PACKAGE)
            upload = media["instrument"].call_args_list[-1]
            self.assertIn("NativeDeferredOriginalUploadProbeTest#", upload.args[1])
            self.assertTrue((root / "media-upload-gate-release").is_file())
            nonces = [call.args[2] for call in media["release_network_handoff"].call_args_list]
            self.assertEqual(3, len(set(nonces)), "Each policy lift must have its own verified acknowledgment")
            self.assertTrue(all(len(nonce) == 32 for nonce in nonces))

    def test_a_failed_host_handoff_cannot_complete_the_network_oracle(self):
        media = self.media_fixture()
        media["release_network_handoff"].side_effect = RuntimeError("Policy readback failed")
        with tempfile.TemporaryDirectory() as temporary, patch.dict(MODULE["run"].__globals__, MEDIA=media):
            root = Path(temporary)
            with self.assertRaisesRegex(RuntimeError, "Policy readback"):
                MODULE["run"]("emulator-5556", root, Mock(), root / "results", Mock())
            media["restore_environment"].assert_called_once()
            self.assertTrue((root / "media-gate-release").is_file())

    def test_policy_fixture_uses_wifi_and_restores_cellular_after_a_probe_failure(self):
        media = self.media_fixture()
        radio_actions = []
        read = media["adb"].side_effect

        def adb(serial, *arguments, **kwargs):
            if arguments[:3] == ("shell", "svc", "data"):
                radio_actions.append(arguments[-1])
            return read(serial, *arguments, **kwargs)

        def instrument(*args, **kwargs):
            self.assertEqual(["disable"], radio_actions, "Cellular must not win the Wi-Fi metering fixture")
            media["adb"].assert_any_call("emulator-5556", "shell", "cmd", "wifi", "connect-network", "AndroidWifi", "open")
            raise RuntimeError("Fixture probe failed")

        media["adb"].side_effect = adb
        media["instrument"].side_effect = instrument
        with tempfile.TemporaryDirectory() as temporary, patch.dict(MODULE["run"].__globals__, MEDIA=media):
            root = Path(temporary)
            with self.assertRaisesRegex(RuntimeError, "Fixture probe failed"):
                MODULE["run"]("emulator-5556", root, Mock(), root / "results", Mock())
            self.assertEqual(["disable", "enable"], radio_actions)
            media["restore_environment"].assert_called_once()

    def test_failed_oracle_survives_radio_cleanup_and_forwards_pre_cleanup_capture(self):
        media = self.media_fixture()
        primary = AssertionError("Lifecycle oracle failed")
        media["instrument"].side_effect = primary
        media["restore_environment"].side_effect = RuntimeError("Radio cleanup failed")
        capture = Mock()
        with tempfile.TemporaryDirectory() as temporary, patch.dict(MODULE["run"].__globals__, MEDIA=media):
            root = Path(temporary)
            with self.assertRaises(AssertionError) as raised:
                MODULE["run"]("emulator-5556", root, Mock(), root / "results", Mock(), failure_diagnostics=capture)
            self.assertIs(primary, raised.exception)
            self.assertTrue(any("cleanup" in note for note in primary.__notes__))
            self.assertIs(capture, media["instrument"].call_args.kwargs["failure_diagnostics"])
            self.assertTrue((root / "media-gate-release").is_file())
            self.assertTrue((root / "media-upload-gate-release").is_file())
            media["adb"].assert_any_call("emulator-5556", "shell", "svc", "data", "enable")

    def test_cellular_restoration_is_attempted_when_other_environment_cleanup_fails(self):
        for original in (b"0", b"1"):
            with self.subTest(original=original):
                media = self.media_fixture(cellular=original)
                media["restore_environment"].side_effect = RuntimeError("Radio cleanup failed")
                with tempfile.TemporaryDirectory() as temporary, patch.dict(MODULE["run"].__globals__, MEDIA=media):
                    root = Path(temporary)
                    with self.assertRaisesRegex(RuntimeError, "Radio cleanup failed"):
                        MODULE["run"]("emulator-5556", root, Mock(), root / "results", Mock())
                    setting = "enable" if original == b"1" else "disable"
                    media["adb"].assert_any_call("emulator-5556", "shell", "svc", "data", setting)
                    self.assertEqual(("emulator-5556", "shell", "settings", "get", "global", "mobile_data"),
                                     media["adb"].call_args.args)

    def test_unreadable_cellular_state_rejects_fixture_before_mutating_radios(self):
        media = self.media_fixture(cellular=b"null")
        with tempfile.TemporaryDirectory() as temporary, patch.dict(MODULE["run"].__globals__, MEDIA=media):
            root = Path(temporary)
            with self.assertRaisesRegex(RuntimeError, "cellular setting must be readable"):
                MODULE["run"]("emulator-5556", root, Mock(), root / "results", Mock())
            self.assertFalse(any(call.args[1:3] == ("shell", "svc") for call in media["adb"].call_args_list))
            media["instrument"].assert_not_called()

    def test_upload_failure_releases_both_server_gates_and_cannot_report_acceptance(self):
        media = self.media_fixture()
        exercise = media["instrument"].side_effect

        def fail_upload(*args, **kwargs):
            exercise(*args, **kwargs)
            if args[2].name == "upload-recover.log":
                raise RuntimeError("Retained upload did not resume")

        media["instrument"].side_effect = fail_upload
        with tempfile.TemporaryDirectory() as temporary, patch.dict(MODULE["run"].__globals__, MEDIA=media):
            root = Path(temporary)
            with self.assertRaisesRegex(RuntimeError, "Retained upload"):
                MODULE["run"]("emulator-5556", root, Mock(), root / "results", Mock())
            self.assertTrue((root / "media-gate-release").is_file())
            self.assertTrue((root / "media-upload-gate-release").is_file())
            media["restore_environment"].assert_called_once()

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
