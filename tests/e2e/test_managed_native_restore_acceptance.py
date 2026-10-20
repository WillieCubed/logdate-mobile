from pathlib import Path
import json
import re
import runpy
import tempfile
import unittest
from unittest.mock import Mock
import subprocess

MODULE = runpy.run_path(str(Path(__file__).with_name("managed-native-restore-acceptance.py")))


class ManagedDriverControllerTest(unittest.TestCase):
    def test_gradle_installs_the_private_artifact_pair_even_when_build_settings_change(self):
        with tempfile.TemporaryDirectory(prefix="pinned apks ") as folder:
            app, probe = Path(folder) / "app.apk", Path(folder) / "probe.apk"
            app.write_bytes(b"selected-app-207")
            probe.write_bytes(b"selected-probe-207")
            arguments = MODULE["pinned_install_arguments"](app, probe)
            self.assertEqual([f"-Plogdate.nativeAcceptanceAppApk={app}",
                              f"-Plogdate.nativeAcceptanceProbeApk={probe}"], arguments)
            self.assertNotIn(":app:android-main:assembleDebug", arguments)

    def test_pinned_installation_rejects_relative_or_missing_artifacts(self):
        with tempfile.TemporaryDirectory() as folder:
            present = Path(folder) / "app.apk"
            present.touch()
            for app, probe in ((Path("relative.apk"), present), (present, Path(folder) / "missing.apk")):
                with self.subTest(app=app, probe=probe), self.assertRaises(ValueError):
                    MODULE["pinned_install_arguments"](app, probe)

    def test_setup_process_can_disappear_before_initial_identity_capture(self):
        for failure in (subprocess.CalledProcessError(1, "ps"), RuntimeError("The owned process is no longer live")):
            self.assertIsNone(MODULE["discovery_identity"](12, process_identity=Mock(side_effect=failure)))
        identity = {"birth": "one", "command": "owned-qemu"}
        self.assertEqual(identity, MODULE["discovery_identity"](12, process_identity=Mock(return_value=identity)))

    def test_receipt_observation_timeout_preserves_the_live_generation(self):
        identity = {"birth": "one", "command": "owned-qemu"}
        observer = MODULE["observe_driver_receipt"]
        calls = []

        def command(*args, **kwargs):
            calls.append(args)
            if len(calls) == 1:
                raise subprocess.TimeoutExpired(args, 15)
            return Mock(stdout=b'{"phase":"ready"}')

        process_identity = Mock(return_value=identity)
        candidate = {"pid": 12, "identity": identity}
        self.assertIsNone(observer("/adb", "emulator-5554", "nonce", candidate,
                                   command=command, process_identity=process_identity))
        self.assertEqual({"phase": "ready"}, observer("/adb", "emulator-5554", "nonce", candidate,
                                                    command=command, process_identity=process_identity))
        self.assertEqual(2, len(calls))
        self.assertEqual(4, process_identity.call_count)

    def test_receipt_timeout_cannot_adopt_a_changed_discovery_process(self):
        command = Mock(side_effect=subprocess.TimeoutExpired("adb", 15))
        process_identity = Mock(side_effect=[{"birth": "one"}, {"birth": "reused"}])
        self.assertIsNone(MODULE["observe_driver_receipt"]("/adb", "emulator-5554", "nonce",
                                                          {"pid": 12, "identity": {"birth": "one"}},
                                                          command=command, process_identity=process_identity))

    def test_changed_pre_ready_process_cannot_supply_a_stale_receipt(self):
        for identities in ([{"birth": "changed"}], [{"birth": "one"}, {"birth": "changed"}]):
            command = Mock(return_value=Mock(stdout=b'{"phase":"ready"}'))
            self.assertIsNone(MODULE["observe_driver_receipt"]("/adb", "emulator-5554", "nonce",
                                                              {"pid": 12, "identity": {"birth": "one"}},
                                                              command=command, process_identity=Mock(side_effect=identities)))
            self.assertEqual(len(identities) - 1, command.call_count)

    def test_work_and_single_device_profiles_do_not_claim_cross_device_transfer(self):
        for mode in ("transfer", "publication", "enrollment", "d2d", "work-restart"):
            receipt = MODULE["acceptance_receipt"](mode, [], [])
            self.assertTrue(receipt["managedDeviceLifecycleAccepted"])
            self.assertEqual(mode in {"transfer", "publication", "enrollment"}, receipt["crossDeviceTransferAccepted"])
            self.assertEqual(mode != "work-restart", receipt["managedNativeTransport"])
            self.assertFalse(receipt["googleRestoreProviderAccepted"])

    def test_every_profile_uses_owned_targets_and_retains_its_native_oracle(self):
        for mode, harness, target_flag in (("work-restart", "native-restore-work-restart-acceptance.py", "--emulator"),
                                           ("d2d", "native-d2d-acceptance.py", "--emulator"),
                                           ("enrollment", "native-device-enrollment-acceptance.py", "--source")):
            with self.subTest(mode=mode):
                command = MODULE["scenario_command"](mode, "emulator-5556", "emulator-5558", Path("/pinned/app.apk"),
                                                      Path("/pinned/probe.apk"), Path("/private/results"), [],
                                                      classpath=Path("/private/classpath"), fixture_environment=Path("/private/environment.json"))
                self.assertTrue(command[1].endswith(harness))
                self.assertEqual("emulator-5556", command[command.index(target_flag) + 1])
                self.assertEqual("/pinned/app.apk", command[command.index("--app-apk") + 1])
                if mode == "enrollment":
                    self.assertEqual("emulator-5558", command[command.index("--destination") + 1])
                    self.assertIn("--fixture-environment", command)
                if mode != "d2d":
                    self.assertIn("--seed-empty-source", command)
        with self.assertRaises(ValueError):
            MODULE["scenario_command"]("enrollment", "s", "d", Path("a"), Path("b"), Path("c"), [])

    def test_publication_mode_uses_the_existing_restore_and_trust_oracles(self):
        command = MODULE["scenario_command"]("publication", "emulator-5556", "emulator-5558", Path("/pinned/app.apk"),
                                              Path("/pinned/probe.apk"), Path("/private/results"), [])
        self.assertTrue(command[1].endswith("native-publication-acceptance.py"))
        self.assertIn("--seed-empty-source", command)
        with self.assertRaises(ValueError):
            MODULE["scenario_command"]("publication", "emulator-5556", "emulator-5558", Path("a"), Path("b"), Path("c"), ["--exercise-quota"])

    def test_driver_lifetime_covers_all_host_phases_and_peer_boot_skew(self):
        for count in (0, 1, 3):
            with self.subTest(count=count):
                budget = MODULE["acceptance_budget"](count)
                self.assertGreaterEqual(budget["driverMillis"],
                                        1000 * (budget["boot"] + budget["native"] + count * budget["followup"] + budget["teardown"]))

    def test_followups_keep_the_owned_target_and_pinned_artifacts(self):
        command = MODULE["followup_command"]("missing-media", "emulator-5558", Path("/pinned/app.apk"),
                                              Path("/pinned/probe.apk"), Path("/private/results"))
        self.assertIn("--synthetic-data", command)
        self.assertEqual("emulator-5558", command[command.index("--emulator") + 1])
        self.assertEqual("/pinned/app.apk", command[command.index("--app-apk") + 1])
        self.assertEqual("/pinned/probe.apk", command[command.index("--test-apk") + 1])
        self.assertTrue(command[1].endswith("native-missing-media-acceptance.py"))
        with self.assertRaises(ValueError):
            MODULE["followup_command"]("../../other", "emulator-5558", Path("a"), Path("b"), Path("c"))

    def test_exact_driver_method_names_occupy_different_android_runner_shards(self):
        driver = Path(__file__).parents[2] / "tools/native-restore-driver/src/androidTest/kotlin/app/logdate/tools/restoredriver/NativeRestoreDriverTest.kt"
        names = re.findall(r"@Test\s+fun (\w+)\(", driver.read_text())
        self.assertEqual(["retainsManagedLifecycle1", "retainsManagedLifecycle2"], names)
        shards = []
        for name in names:
            value = 0
            for character in f"{name}(app.logdate.tools.restoredriver.NativeRestoreDriverTest)":
                value = (31 * value + ord(character)) & 0xffffffff
            if value >= 2**31:
                value -= 2**32
            shards.append(abs(value) % 2)
        self.assertEqual({0, 1}, set(shards))

    def test_driver_and_harness_use_the_same_package_despite_inherited_overrides(self):
        environment = MODULE["managed_environment"](Path("/sdk"), {"LOGDATE_NATIVE_ACCEPTANCE_PACKAGE": "other.debug"})
        self.assertEqual("studio.hypertext.logdate.debug", environment["LOGDATE_NATIVE_ACCEPTANCE_PACKAGE"])

    def test_native_arguments_cannot_override_owned_devices_or_attested_apks(self):
        for arguments in (["--source", "physical"], ["--app-apk", "/other.apk"], ["--test-apk=/other.apk"],
                          ["--output", "/other"], ["--resume-seeded-source"], ["--identity", "unknown"]):
            with self.subTest(arguments=arguments):
                with self.assertRaises(ValueError):
                    MODULE["validate_native_arguments"](arguments)
        MODULE["validate_native_arguments"](["--identity", "account", "--exercise-partial-media", "--exercise-quota"])

    def test_durably_saves_private_custody_before_device_commands(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "custody.json"
            MODULE["save"](path, {"nonce": "one", "targets": {}})
            self.assertEqual({"nonce": "one", "targets": {}}, json.loads(path.read_text()))
            self.assertEqual(0o600, path.stat().st_mode & 0o777)
            MODULE["save"](path, {"nonce": "two"})
            self.assertEqual({"nonce": "two"}, json.loads(path.read_text()))
            self.assertFalse(path.with_suffix(".json.part").exists())

    def test_requires_both_exact_outer_driver_cases(self):
        case = '<testcase classname="app.logdate.tools.restoredriver.NativeRestoreDriverTest" name="retainsManagedLifecycle1"/>'
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            for index, name in enumerate(("source", "destination"), start=1):
                (directory / f"TEST-{name}.xml").write_text(f"<testsuite>{case.replace('Lifecycle1', f'Lifecycle{index}')}</testsuite>")
            MODULE["require_outer_results"](directory)
            for bad in (case.replace("/>", "><skipped/></testcase>"), case.replace("retainsManaged", "unrelated"), case):
                (directory / "TEST-destination.xml").write_text(f"<testsuite>{bad}</testsuite>")
                with self.assertRaises(RuntimeError):
                    MODULE["require_outer_results"](directory)

    def test_console_port_rejects_ambiguous_or_non_loopback_listener(self):
        self.assertEqual(5558, MODULE["console_port"]("n127.0.0.1:5558\nn127.0.0.1:5559\n"))
        self.assertIsNone(MODULE["console_port"]("n*:5558\n"))
        self.assertIsNone(MODULE["console_port"]("n127.0.0.1:5558\nn127.0.0.1:5560\n"))


if __name__ == "__main__":
    unittest.main()
