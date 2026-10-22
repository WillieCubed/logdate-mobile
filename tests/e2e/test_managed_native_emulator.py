from pathlib import Path
import json
import runpy
import tempfile
import subprocess
import unittest
from unittest.mock import Mock


MODULE_PATH = Path(__file__).with_name("managed-native-emulator.py")
MODULE = runpy.run_path(str(MODULE_PATH)) if MODULE_PATH.exists() else {}
AVD = "dev34_google_apis_arm64-v8a_Pixel_9_Pro"
NONCE = "7b0e6278-04b1-4a0d-91bb-e860fe8602d3"


class ManagedEmulatorLaunchTest(unittest.TestCase):
    def test_discovery_excludes_snapshot_setup_and_concurrent_unowned_instances(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            candidates = {pid: {"managedId": str(pid), "avd": AVD} for pid in (101, 102, 103, 104)}
            for pid in (101, 102):
                (directory / f"{pid}.json").write_text(json.dumps(
                    {"nonce": NONCE, **candidates[pid], "launcherPid": pid, "coldBoot": True}))
            (directory / "104.json").write_text(json.dumps(
                {"nonce": "another-run", **candidates[104], "launcherPid": 104, "coldBoot": True}))
            self.assertEqual({101: candidates[101], 102: candidates[102]},
                             MODULE["owned_managed_candidates"](directory, candidates, NONCE))

    def test_discovery_requires_complete_receipt_and_exact_live_launch_identity(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            candidate = {"managedId": "101", "avd": AVD}
            path = directory / "101.json"
            good = {"nonce": NONCE, **candidate, "launcherPid": 101, "coldBoot": True}
            for change in ({"launcherPid": 102}, {"launcherPid": True}, {"avd": "other"},
                           {"coldBoot": False}, {"managedId": "other"}):
                path.write_text(json.dumps({**good, **change}))
                self.assertEqual({}, MODULE["owned_managed_candidates"](directory, {101: candidate}, NONCE))
            path.write_text('{"nonce":')
            self.assertEqual({}, MODULE["owned_managed_candidates"](directory, {101: candidate}, NONCE))
            path.unlink()
            source = directory / "source.json"
            source.write_text(json.dumps(good))
            path.symlink_to(source)
            self.assertEqual({}, MODULE["owned_managed_candidates"](directory, {101: candidate}, NONCE))

    def arguments(self, managed_id):
        return ["-avd", AVD, "-read-only", "-no-snapshot-save", "-id", managed_id, "-gpu", "swiftshader"]

    def test_cold_boot_preserves_the_original_avd_and_managed_identity_arguments(self):
        launch = MODULE["managed_launch_arguments"]
        for managed_id in ("1011b945-34b2-4349-a963-fc37d94eccb6_GradleManagedDevice",
                           "a68033e3-44c1-46eb-82a6-7d1c55ce32ed_GradleManagedDevice"):
            original = self.arguments(managed_id)
            self.assertEqual([*original, "-no-snapshot-load"], launch(original, AVD, NONCE))

    def test_snapshot_creation_and_capability_queries_are_unmodified(self):
        launch = MODULE.get("managed_launch_arguments")
        self.assertIsNotNone(launch, "Managed launcher adapter is missing")
        for arguments in (["-version"], ["-help-wifi-mac-address"],
                          ["-avd", AVD, "-read-only", "-check-snapshot-loadable", "default_boot"],
                          ["-avd", AVD, "-no-snapshot-load", "-id", "1011b945-34b2-4349-a963-fc37d94eccb6_GradleManagedDevice"]):
            with self.subTest(arguments=arguments):
                self.assertEqual(arguments, launch(arguments, AVD, NONCE))

    def test_gradle_at_name_launch_uses_the_same_identity_as_the_explicit_avd_form(self):
        launch = MODULE["managed_launch_arguments"]
        explicit = self.arguments("1011b945-34b2-4349-a963-fc37d94eccb6_GradleManagedDevice")
        actual = [f"@{AVD}", *explicit[2:]]
        self.assertEqual(launch(explicit, AVD, NONCE)[-2:], launch(actual, AVD, NONCE)[-2:])
        with self.assertRaises(ValueError):
            launch([*actual, "-avd", AVD], AVD, NONCE)

    def test_managed_test_instances_boot_the_os_without_reusing_snapshot_ram(self):
        original = self.arguments("1011b945-34b2-4349-a963-fc37d94eccb6_GradleManagedDevice") + ["-force-snapshot-load"]
        selected = MODULE["managed_launch_arguments"](original, AVD, NONCE)
        self.assertNotIn("-force-snapshot-load", selected)
        self.assertEqual(1, selected.count("-no-snapshot-load"))
        self.assertIn("-read-only", selected)
        self.assertIn("-no-snapshot-save", selected)
        self.assertNotIn("-wipe-data", selected)

    def test_read_only_override_cannot_launch_an_unowned_or_mutable_device(self):
        launch = MODULE.get("managed_launch_arguments")
        self.assertIsNotNone(launch, "Managed launcher adapter is missing")
        first = self.arguments("1011b945-34b2-4349-a963-fc37d94eccb6_GradleManagedDevice")
        for arguments in (self.arguments("manual"), first + ["-wifi-mac-address", "02:00:00:00:00:01"],
                          [*first[:1], "another_avd", *first[2:]],
                          [item for item in first if item != "-no-snapshot-save"],
                          first + ["-id", "a68033e3-44c1-46eb-82a6-7d1c55ce32ed_GradleManagedDevice"]):
            with self.subTest(arguments=arguments), self.assertRaises(ValueError):
                launch(arguments, AVD, NONCE)

    def test_sdk_facade_keeps_packages_and_emulator_assets_in_the_original_sdk(self):
        create = MODULE.get("create_managed_sdk")
        self.assertIsNotNone(create, "Managed SDK facade is missing")
        with tempfile.TemporaryDirectory(prefix="managed sdk ") as folder:
            root = Path(folder)
            sdk = root / "original"
            (sdk / "emulator" / "lib64").mkdir(parents=True)
            (sdk / "platform-tools").mkdir()
            original = sdk / "emulator" / "emulator"
            original.write_text("#!/bin/sh\n")
            (sdk / "emulator" / "package.xml").write_text("package metadata")
            facade = create(sdk, root / "private-sdk", AVD, NONCE)
            self.assertTrue((facade / "platform-tools").is_symlink())
            self.assertEqual((sdk / "emulator" / "lib64").resolve(), (facade / "emulator" / "lib64").resolve())
            self.assertEqual("package metadata", (facade / "emulator" / "package.xml").read_text())
            self.assertFalse((facade / "emulator" / "emulator").is_symlink())
            self.assertEqual("#!/bin/sh\n", original.read_text())
            self.assertEqual(0o700, facade.stat().st_mode & 0o777)
            with self.assertRaises(FileExistsError):
                create(sdk, facade, AVD, NONCE)

    def test_station_acceptance_binds_both_real_interfaces_to_this_generations_launches(self):
        require = MODULE["require_managed_station_launches"]
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            targets = {"emulator-5558": {"managedId": "first", "avd": AVD},
                       "emulator-5560": {"managedId": "second", "avd": AVD}}
            stations = {"emulator-5558": "02:00:00:00:00:01", "emulator-5560": "02:00:00:00:00:02"}
            for serial, target in targets.items():
                (directory / f"{target['managedId']}.json").write_text(json.dumps(
                    {"nonce": NONCE, **target, "coldBoot": True}))
            self.assertTrue(require(directory, targets, NONCE, stations)["distinctManagedWifiStations"])
            with self.assertRaises(RuntimeError):
                require(directory, targets, NONCE, {**stations, "emulator-5560": stations["emulator-5558"]})
            path = directory / "second.json"
            original = json.loads(path.read_text())
            for change in ({"nonce": "previous-generation"}, {"managedId": "another-instance"},
                           {"avd": "another-avd"}, {"coldBoot": False}):
                path.write_text(json.dumps({**original, **change}))
                with self.subTest(change=change), self.assertRaises(RuntimeError):
                    require(directory, targets, NONCE, stations)
            path.write_text(json.dumps(original))
            for invalid in (None, "ff:00:00:00:00:02", "00:00:00:00:00:00", "invalid"):
                with self.subTest(address=invalid), self.assertRaises(RuntimeError):
                    require(directory, targets, NONCE, {**stations, "emulator-5560": invalid})
            path.unlink()
            with self.assertRaises(FileNotFoundError):
                require(directory, targets, NONCE, stations)

    def test_facade_cleanup_requires_positive_disappearance_even_before_driver_binding(self):
        stopped = MODULE.get("managed_processes_stopped")
        self.assertIsNotNone(stopped, "Unbound emulators must be included in facade cleanup")
        process = Mock(poll=Mock(return_value=1))
        invocation = {"launcherPid": 100, "arguments": self.arguments("1011b945-34b2-4349-a963-fc37d94eccb6_GradleManagedDevice")}
        for lines in (None, ["100 original-emulator"],
                      ["200 qemu-system-aarch64 @dev34 -id 1011b945-34b2-4349-a963-fc37d94eccb6_GradleManagedDevice"],
                      ["300 python /private/sdk/emulator/emulator"]):
            with self.subTest(lines=lines):
                self.assertFalse(stopped(process, [invocation], set(), lines, Path("/private/sdk")))
        self.assertFalse(stopped(Mock(poll=Mock(return_value=None)), [], set(), [], Path("/private/sdk")))
        self.assertFalse(stopped(process, [], {400}, ["400 qemu-system-aarch64"], Path("/private/sdk")))
        self.assertTrue(stopped(process, [invocation], {400}, ["500 unrelated-emulator"], Path("/private/sdk")))

    def test_executable_adapter_records_the_launch_and_preserves_quoted_arguments(self):
        with tempfile.TemporaryDirectory(prefix="managed sdk ") as folder:
            root = Path(folder)
            sdk = root / "original"
            (sdk / "emulator").mkdir(parents=True)
            original = sdk / "emulator/emulator"
            original.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n')
            original.chmod(0o700)
            facade = MODULE["create_managed_sdk"](sdk, root / "private-sdk", AVD, NONCE)
            arguments = self.arguments("1011b945-34b2-4349-a963-fc37d94eccb6_GradleManagedDevice") + ["-name", "a name with spaces"]
            result = subprocess.run([str(facade / "emulator/emulator"), *arguments], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(MODULE["managed_launch_arguments"](arguments, AVD, NONCE), result.stdout.splitlines())
            receipts = list((root / "managed-emulator-launches").glob("*.json"))
            self.assertEqual(1, len(receipts))
            self.assertEqual(NONCE, json.loads(receipts[0].read_text())["nonce"])
            self.assertEqual(0o600, receipts[0].stat().st_mode & 0o777)


if __name__ == "__main__":
    unittest.main()
