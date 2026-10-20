import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("managed_talkback", Path(__file__).with_name("managed-talkback-acceptance.py"))
acceptance = importlib.util.module_from_spec(spec)
spec.loader.exec_module(acceptance)


class ManagedTargetSafetyTest(unittest.TestCase):
    managed = "12 /sdk/qemu-system-aarch64 @dev34_google_apis_arm64-v8a_Pixel_9_Pro -id nonce_GradleManagedDevice"

    def test_stale_nonce_never_accepts_input(self):
        with self.assertRaisesRegex(RuntimeError, "no longer accepts"):
            acceptance.require_ready_receipt({"nonce": "older-run", "phase": "ready"}, "current-run")

    def test_completed_fixture_never_accepts_input(self):
        with self.assertRaisesRegex(RuntimeError, "no longer accepts"):
            acceptance.require_ready_receipt({"nonce": "current-run", "phase": "passed"}, "current-run")

    def test_manual_emulator_is_rejected_even_when_avd_name_matches(self):
        manual = self.managed.replace(" -id nonce_GradleManagedDevice", "")
        self.assertEqual([], acceptance.managed_processes([manual], set()))

    def test_preexisting_managed_device_is_rejected(self):
        self.assertEqual([], acceptance.managed_processes([self.managed], {12}))

    def test_only_new_expected_managed_device_is_selected(self):
        other = self.managed.replace("Pixel_9_Pro", "Another_Device").replace("12 ", "14 ")
        self.assertEqual([(12, self.managed.split(None, 1)[1])], acceptance.managed_processes([other, self.managed], set()))

    def test_multiple_new_managed_devices_fail_closed(self):
        with self.assertRaisesRegex(RuntimeError, "ambiguous"):
            acceptance.managed_processes([self.managed, self.managed.replace("12 ", "13 ")], set())

    def test_console_ignores_adb_grpc_and_nonloopback_listeners(self):
        listeners = "n127.0.0.1:5556\nn[::1]:5556\nn127.0.0.1:5557\nn127.0.0.1:8556\nn0.0.0.0:5558\n"
        self.assertEqual({5556}, acceptance.console_ports(listeners))

    def test_ambiguous_console_ports_fail_closed(self):
        with self.assertRaisesRegex(RuntimeError, "ambiguous"):
            acceptance.console_ports("n127.0.0.1:5556\nn127.0.0.1:5558\n")


if __name__ == "__main__":
    unittest.main()
