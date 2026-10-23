import json
import runpy
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

P = Path(__file__).resolve().parent
ROOT = P.parents[1]
Guard = runpy.run_path(str(P / "managed-talkback-custody.py"))["HostCustody"]
T = runpy.run_path(str(ROOT / "tests/e2e/managed-native-target.py"))
E = runpy.run_path(str(ROOT / "tests/e2e/managed-native-emulator.py"))


class CustodyTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.path = Path(self.tmp.name)
        self.nonce = "34e3faaf-77b1-4cac-8dbb-0081bb81efb9"
        self.mid = "43e3faaf-77b1-4cac-8dbb-0081bb81efb9_GradleManagedDevice"
        self.avd = "dev34_google_apis_arm64-v8a_Pixel_9_Pro"
        self.row = {
            "nonce": self.nonce,
            "managedId": self.mid,
            "avd": self.avd,
            "launcherPid": 23,
            "coldBoot": True,
        }
        self.write()
        self.ids = {
            17: dict(
                birth="gradle-birth", command="java GradleWrapperMain " + self.nonce
            ),
            23: dict(
                birth="qemu-birth",
                command="/sdk/qemu-system-aarch64 @"
                + self.avd
                + " -id "
                + self.mid
                + " -no-window",
            ),
        }
        self.child = SimpleNamespace(pid=17, poll=lambda: None)
        self.adb_commands = []
        self.ps_command = self.ids[23]["command"]

        def command(*args, **kwargs):
            if args[0] == "ps":
                return SimpleNamespace(
                    stdout=("23 " + self.ps_command).encode()
                )
            if args[0] == "lsof":
                return SimpleNamespace(stdout=b"n127.0.0.1:5556\n")
            self.adb_commands.append(args)
            return SimpleNamespace(stdout=b"eligible")

        self.guard = Guard(
            self.nonce,
            self.avd,
            self.path,
            "/sdk/adb",
            command,
            dict(T, process_identity=lambda p: dict(self.ids[p])),
            E,
        )
        self.guard.remember(self.child)

    def write(self):
        (self.path / (self.mid + ".json")).write_text(json.dumps(self.row))

    def send(self):
        return self.guard.command("/sdk/adb", "-s", "emulator-5556", "shell", "id")

    def test_repeated_current_target(self):
        self.send()
        self.send()
        self.assertEqual(len(self.adb_commands), 2)

    def test_setup_can_change_before_ready_without_pinning(self):
        self.send()
        self.ids[23]["birth"] = "new"
        self.send()
        self.guard.bind_ready("emulator-5556")
        self.send()

    def test_close_after_gradle_exit_issues_no_adb(self):
        self.child.poll = lambda: 0
        native = runpy.run_path(str(ROOT / "tests/e2e/managed-talkback-acceptance.py"))[
            "NativeTouch"
        ]
        touch = native.__new__(native)
        touch.command = self.guard.command
        touch.adb = "/sdk/adb"
        touch.serial = "emulator-5556"
        touch.receipt = "nonce.json"
        touch.nonce = self.nonce
        touch.close()
        self.assertFalse(self.adb_commands)

    def test_discovery_filters_template_boot_without_launch_receipt(self):
        original = runpy.run_path(
            str(ROOT / "tests/e2e/managed-talkback-acceptance.py")
        )["managed_processes"]
        lines = ["23 " + self.ids[23]["command"]]
        self.assertEqual(len(self.guard.candidates(lines, set(), original)), 1)
        (self.path / (self.mid + ".json")).unlink()
        self.assertEqual(self.guard.candidates(lines, set(), original), [])

    def test_foreign_new_generation_is_filtered_before_ambiguity_check(self):
        original = runpy.run_path(
            str(ROOT / "tests/e2e/managed-talkback-acceptance.py")
        )["managed_processes"]
        foreign = self.ids[23]["command"].replace(self.mid, "other_GradleManagedDevice")
        lines = ["23 " + self.ids[23]["command"], "24 " + foreign]
        self.assertEqual(
            self.guard.candidates(lines, set(), original),
            [(23, self.ids[23]["command"])],
        )

    def test_native_input_uses_guarded_command_for_every_operation(self):
        native = runpy.run_path(str(ROOT / "tests/e2e/managed-talkback-acceptance.py"))[
            "NativeTouch"
        ]
        observed = []

        def execute(*args):
            observed.append(args)
            if "cat" in args:
                data = json.dumps({"nonce": self.nonce, "phase": "ready"}).encode()
            elif args[-1] == "su 0 id":
                data = b"uid=0(root)"
            elif args[-1] == "getevent -lp":
                data = b'add device 1: /dev/input/event1\n  name:     "virtio_input_multi_touch_1"\n'
            else:
                data = b""
            return SimpleNamespace(stdout=data)

        touch = native(
            "/sdk/adb",
            "emulator-5556",
            1080,
            2400,
            self.nonce,
            Path("/fixture/native-touch"),
            execute=execute,
        )
        touch.swipe()
        touch.double_tap()
        touch.close()
        self.assertGreaterEqual(len(observed), 10)
        self.assertTrue(
            all(args[:3] == ("/sdk/adb", "-s", "emulator-5556") for args in observed)
        )

    def test_missing_launch_receipt(self):
        (self.path / (self.mid + ".json")).unlink()
        with self.assertRaises(RuntimeError):
            self.send()
        self.assertFalse(self.adb_commands)

    def test_rejects_wrong_receipt_nonce_pid_avd_and_cold_boot(self):
        original = dict(self.row)
        for field, value in [
            ("nonce", "other"),
            ("launcherPid", 24),
            ("avd", "other"),
            ("coldBoot", False),
        ]:
            with self.subTest(field=field):
                self.row = dict(original)
                self.row[field] = value
                self.write()
                with self.assertRaises(RuntimeError):
                    self.send()
        self.assertFalse(self.adb_commands)

    def test_rejects_qemu_birth_reuse(self):
        self.guard.bind_ready("emulator-5556")
        self.send()
        self.ids[23]["birth"] = "new"
        with self.assertRaises(RuntimeError):
            self.send()
        self.assertEqual(len(self.adb_commands), 1)

    def test_candidate_mismatch_reports_bounded_process_evidence_without_adb(self):
        self.ps_command += " " + "p" * 900
        self.ids[23]["command"] = (
            "/sdk/qemu-system-aarch64 @"
            + self.avd
            + " -id foreign_GradleManagedDevice "
            + "x" * 900
        )
        with self.assertRaisesRegex(
            RuntimeError, "Managed QEMU candidate mismatch"
        ) as caught:
            self.send()
        message = str(caught.exception)
        self.assertIn("PID 23", message)
        self.assertIn("birth='qemu-birth'", message)
        self.assertIn(self.mid, message)
        self.assertIn("foreign_GradleManagedDevice", message)
        self.assertIn("current command=", message)
        self.assertIn("original ps row=", message)
        self.assertEqual(message.count("<truncated>"), 2)
        self.assertLess(len(message), 1000)
        self.assertFalse(self.adb_commands)

    def test_rejects_gradle_birth_reuse(self):
        self.send()
        self.ids[17]["birth"] = "new"
        with self.assertRaises(RuntimeError):
            self.send()

    def test_rejects_dead_gradle(self):
        self.child.poll = lambda: 0
        with self.assertRaises(RuntimeError):
            self.send()

    def test_rejects_wrong_console_port(self):
        with self.assertRaises(RuntimeError):
            self.guard.command("/sdk/adb", "-s", "emulator-5554", "shell", "id")

    def test_rejects_unselected_adb_or_unscoped_command(self):
        for args in [
            ("/other/adb", "-s", "emulator-5556", "shell", "id"),
            ("/sdk/adb", "devices"),
        ]:
            with self.assertRaises(RuntimeError):
                self.guard.command(*args)


if __name__ == "__main__":
    unittest.main()
