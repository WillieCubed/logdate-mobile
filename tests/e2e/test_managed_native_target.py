from pathlib import Path
import runpy
import shlex
import unittest
from unittest.mock import Mock, patch


TARGET = runpy.run_path(str(Path(__file__).with_name("managed-native-target.py")))
AVD = "dev34_google_apis_arm64-v8a_Pixel_9_Pro"


class ManagedNativeCustodyTest(unittest.TestCase):
    def test_selects_only_new_managed_qemu_processes_with_exact_avd(self):
        lines = [
            f"101 /sdk/emulator/qemu-system-aarch64 @{AVD} -id first_GradleManagedDevice -read-only",
            f"102 /sdk/emulator/qemu-system-aarch64 @{AVD} -id second_GradleManagedDevice -read-only",
            f"103 /sdk/emulator/qemu-system-aarch64 @{AVD} -id old_GradleManagedDevice -read-only",
            f"104 /sdk/emulator/qemu-system-aarch64 @{AVD} -id manual -read-only",
            "105 /sdk/emulator/qemu-system-aarch64 @another -id unrelated_GradleManagedDevice",
            f"106 sh -c '/sdk/emulator/qemu-system-aarch64 @{AVD} -id fake_GradleManagedDevice'",
        ]
        selected = TARGET["managed_candidates"](lines, {103}, AVD)
        self.assertEqual({101, 102}, set(selected))
        self.assertEqual("first_GradleManagedDevice", selected[101]["managedId"])

    def test_unrelated_process_with_unbalanced_quotes_cannot_abort_discovery(self):
        lines = ["100 python3 -c print('unmatched", f"101 /sdk/qemu-system-aarch64 @{AVD} -id one_GradleManagedDevice"]
        self.assertEqual({101}, set(TARGET["managed_candidates"](lines, set(), AVD)))

    def test_receipt_requires_nonce_ready_phase_and_same_live_driver_pid(self):
        good = {"nonce": "run-a", "phase": "ready", "pid": 321}
        self.assertTrue(TARGET["validate_driver_receipt"](good, "run-a", 321))
        for change in ({"nonce": "run-b"}, {"phase": "accepted"}, {"pid": 322}, {"pid": True}):
            with self.subTest(change=change):
                self.assertFalse(TARGET["validate_driver_receipt"]({**good, **change}, "run-a", 321))

    def test_root_access_does_not_restart_adbd_or_its_managed_connection(self):
        command = TARGET["device_command"]("/sdk/adb", "emulator-5558", ["root"])
        self.assertEqual(["/sdk/adb", "-s", "emulator-5558", "shell", "su 0 id"], command)

    def test_binary_transport_output_preserves_quoted_paths(self):
        arguments = ["exec-out", "tar", "-C", "/data/path with spaces", "-cf", "-", "1"]
        command = TARGET["device_command"]("/sdk/adb", "emulator-5558", arguments)
        self.assertEqual(["/sdk/adb", "-s", "emulator-5558", "exec-out"], command[:4])
        self.assertEqual(["su", "0", "sh", "-c", shlex.join(arguments[1:])], shlex.split(command[4]))

    def test_raw_shell_instrumentation_retains_its_argument_boundaries(self):
        script = "am instrument -w -e class 'recovery.With spaces' test/Runner"
        command = TARGET["device_command"]("/sdk/adb", "emulator-5558", ["shell", script])
        self.assertEqual(["/sdk/adb", "-s", "emulator-5558", "shell", script], command)

    def test_structured_probe_keeps_shell_identity_and_quotes_arguments(self):
        arguments = ["shell", "am", "instrument", "-e", "class", "recovery.With spaces", "test/Runner"]
        command = TARGET["device_command"]("/sdk/adb", "emulator-5558", arguments)
        self.assertEqual(arguments[1:], shlex.split(command[-1]))
        self.assertNotIn("su", shlex.split(command[-1]))

    def test_transport_extraction_uses_root_without_changing_adbd(self):
        arguments = ["shell", "tar", "-C", "/data/user/0/com.android.localtransport/files", "-xf", "/data/local/tmp/a.tar"]
        command = TARGET["device_command"]("/sdk/adb", "emulator-5558", arguments)
        self.assertEqual(["su", "0", "sh", "-c", shlex.join(arguments[1:])], shlex.split(command[-1]))

    def test_rejects_non_emulator_serials_and_empty_commands(self):
        for serial, arguments in (("physical", ["shell", "id"]), ("emulator-5558", [])):
            with self.subTest(serial=serial):
                with self.assertRaises(ValueError):
                    TARGET["device_command"]("/sdk/adb", serial, arguments)

    def test_cannot_restart_reconnect_or_uninstall_behind_gradles_back(self):
        for command in ("reboot", "reconnect", "usb", "tcpip", "uninstall", "kill-server", "start-server"):
            with self.subTest(command=command):
                with self.assertRaises(ValueError):
                    TARGET["device_command"]("/sdk/adb", "emulator-5558", [command])

    def test_cannot_override_the_owned_target_or_kill_its_emulator(self):
        for arguments in (["-s", "physical-device", "shell", "id"], ["-t", "42", "shell", "id"],
                          ["emu", "kill"], ["-d", "shell", "id"], ["unknown-operation"]):
            with self.subTest(arguments=arguments):
                with self.assertRaises(ValueError):
                    TARGET["device_command"]("/sdk/adb", "emulator-5558", arguments)

    def test_existing_probes_can_read_the_owned_avd_name(self):
        self.assertEqual(["/sdk/adb", "-s", "emulator-5558", "emu", "avd", "name"],
                         TARGET["device_command"]("/sdk/adb", "emulator-5558", ["emu", "avd", "name"]))

    def test_rejects_reused_driver_pid_even_with_the_same_nonce_receipt(self):
        import json

        qemu = {"birth": "qemu-birth", "command": f"/sdk/qemu-system-aarch64 @{AVD} -id first_GradleManagedDevice"}
        gradle = {"birth": "gradle-birth", "command": "java GradleWrapperMain nonce-one"}
        custody = {"nonce": "nonce-one", "adb": "/sdk/adb", "gradle": {"pid": 100, "identity": gradle},
                   "targets": {"emulator-5558": {"pid": 101, "identity": qemu, "managedId": "first_GradleManagedDevice",
                                                "avd": AVD, "driverPid": 321, "driverStartTicks": "50"}}}
        receipt = json.dumps({"nonce": "nonce-one", "phase": "ready", "pid": 321, "startTicks": "50"}).encode()
        stat = "321 (process) " + " ".join(["0"] * 19 + ["51"])
        function = TARGET["require_binding"]
        commands = Mock(side_effect=[b"321", receipt, stat.encode()])
        with patch.dict(function.__globals__, {"process_identity": Mock(side_effect=[gradle, qemu]),
                                               "real_command": commands}):
            with self.assertRaisesRegex(RuntimeError, "PID was reused"):
                function(custody, "emulator-5558")
        self.assertEqual(("/sdk/adb", "emulator-5558", "exec-out", "run-as", TARGET["DRIVER"], "cat", "/proc/321/stat"),
                         commands.call_args.args)

    def test_data_destructive_package_commands_are_rejected(self):
        for arguments in (["shell", "pm", "clear", "studio.hypertext.logdate.debug"],
                          ["shell", "cmd package uninstall studio.hypertext.logdate.debug"],
                          ["shell", "rm -rf /data/data/studio.hypertext.logdate"]):
            with self.subTest(arguments=arguments):
                with self.assertRaises(ValueError):
                    TARGET["device_command"]("/sdk/adb", "emulator-5558", arguments)


if __name__ == "__main__":
    unittest.main()
