import runpy
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

MODULE = runpy.run_path(str(Path(__file__).with_name("native-permission-acceptance.py")))


class RecordingAuthorizationTest(unittest.TestCase):
    def invoke(self, package, services=""):
        def adb(serial, *args, **kwargs):
            text = package if args[:3] == ("shell", "dumpsys", "package") else services
            return SimpleNamespace(stdout=text.encode())
        with tempfile.TemporaryDirectory() as temporary, patch.object(MODULE["h"], "safe_target"), patch.object(MODULE["h"], "adb", side_effect=adb):
            output = Path(temporary) / "permission.json"
            MODULE["verify_recording_permission"]("emulator-5558", output)
            return output.read_text()

    def test_microphone_is_revoked_and_restored_with_other_grants(self):
        self.assertIn("RECORD_AUDIO", MODULE["PERMISSIONS"])

    def test_denied_microphone_and_absent_capture_are_accepted(self):
        self.assertIn('"microphoneGranted": false', self.invoke("  android.permission.RECORD_AUDIO\n  android.permission.RECORD_AUDIO: granted=false\n"))

    def test_granted_microphone_cannot_prove_denied_recovery(self):
        with self.assertRaises(RuntimeError):
            self.invoke("  android.permission.RECORD_AUDIO\n  android.permission.RECORD_AUDIO: granted=true\n")

    def test_unreported_microphone_cannot_be_treated_as_denied(self):
        with self.assertRaises(RuntimeError):
            self.invoke("  android.permission.CAMERA\n")

    def test_malformed_grant_cannot_be_treated_as_denied(self):
        with self.assertRaises(RuntimeError):
            self.invoke("  android.permission.RECORD_AUDIO\n  android.permission.RECORD_AUDIO: granted=unknown\n")

    def test_capture_service_invalidates_denied_recovery(self):
        with self.assertRaises(RuntimeError):
            self.invoke("  android.permission.RECORD_AUDIO\n  android.permission.RECORD_AUDIO: granted=false\n", "ServiceRecord AudioRecordingService")


class RecordingPermissionLifecycleTest(unittest.TestCase):
    def run_scenario(self, fail_mode=None, grant_after_restart=False):
        import subprocess
        h = MODULE["h"]
        grants = {permission: True for permission in MODULE["PERMISSIONS"]}
        initial = dict(grants)
        observed = []

        def adb(serial, *args, **kwargs):
            output = b""
            if args[:3] == ("shell", "dumpsys", "package"):
                output = ("\n".join("  android.permission." + name for name in grants) + "\n" +
                          "\n".join("  android.permission." + name + ": granted=" + str(value).lower() for name, value in grants.items()) + "\n").encode()
            elif args[:3] == ("shell", "dumpsys", "power"):
                output = b"mWakefulness=Awake"
            elif args[:3] in [("shell", "pm", "grant"), ("shell", "pm", "revoke")]:
                grants[args[-1].removeprefix("android.permission.")] = args[2] == "grant"
            elif args[:4] == ("shell", "cmd", "netpolicy", "list"):
                output = b"AndroidWifi;false\n"
            elif args[:4] == ("shell", "cmd", "netpolicy", "get"):
                output = b"disabled"
            elif args[:5] == ("shell", "settings", "get", "global", "airplane_mode_on"):
                output = b"0"
            elif args[:5] == ("shell", "settings", "get", "global", "wifi_on"):
                output = b"1"
            elif args[:3] == ("shell", "am", "start") and grant_after_restart:
                grants["RECORD_AUDIO"] = True
            return subprocess.CompletedProcess(args, 0, stdout=output, stderr=b"")

        def instrument(serial, test, output, **kwargs):
            for action in kwargs.get("status_actions", {}).values():
                action()
            if test == "app.logdate.client.e2e.CompletedRecordingRestartTest":
                extras = kwargs["extras"]
                mode = extras[extras.index("recordingRecoveryMode") + 1]
                observed.append((mode, grants["RECORD_AUDIO"]))
                if mode == fail_mode:
                    raise RuntimeError("recording instrumentation failed")

        with tempfile.TemporaryDirectory() as temporary, patch.object(h, "safe_target"), patch.object(h, "adb", side_effect=adb), patch.object(h, "instrument", side_effect=instrument):
            args = SimpleNamespace(output=Path(temporary), app_apk=Path("app.apk"), test_apk=Path("test.apk"))
            try:
                MODULE["run"](args, "emulator-5558")
            finally:
                self.assertEqual(initial, grants, "Original microphone and other permission grants must be restored even after failure")
        return observed

    def test_success_checks_denial_before_and_after_cold_start(self):
        self.assertEqual([("seed", False), ("verify", False)], self.run_scenario())

    def test_seed_failure_restores_original_microphone_grant(self):
        with self.assertRaisesRegex(RuntimeError, "recording instrumentation failed"):
            self.run_scenario(fail_mode="seed")

    def test_playback_failure_restores_original_microphone_grant(self):
        with self.assertRaisesRegex(RuntimeError, "recording instrumentation failed"):
            self.run_scenario(fail_mode="verify")

    def test_permission_granted_after_restart_refuses_denied_recovery(self):
        with self.assertRaisesRegex(RuntimeError, "denied destination microphone"):
            self.run_scenario(grant_after_restart=True)


if __name__ == "__main__":
    unittest.main()
