from pathlib import Path
import os
import re
import runpy
import unittest
import subprocess

MODULE = runpy.run_path(
    str(Path(__file__).with_name("managed-native-restore-acceptance.py"))
)


class ManagedDriverControllerTest(unittest.TestCase):
    def test_ci_runs_required_recovery_profiles_through_the_owned_transfer_controller(
        self,
    ):
        root = Path(__file__).resolve().parents[2]
        workflow = (
            root / ".github/workflows/native-restore-acceptance.yml"
        ).read_text()
        matrix = re.search(r"scenario:\s*\[([^\]]+)\]", workflow)
        self.assertIsNotNone(matrix, "Native restore scenarios must be enumerated")
        dispatch = re.search(
            r'          case "\$NATIVE_SCENARIO" in\n(.*?)          esac',
            workflow,
            re.DOTALL,
        )
        self.assertIsNotNone(dispatch)
        script = (
            'set -euo pipefail\narguments=()\ncase "$NATIVE_SCENARIO" in\n'
            + dispatch.group(1)
            + "esac\nprintf '%s\\0' \"${arguments[@]}\"\n"
        )
        for scenario, flag in (
            ("archive", "--exercise-archive-recovery"),
            ("data-only", "--exercise-data-only"),
        ):
            with self.subTest(scenario=scenario):
                self.assertIn(
                    scenario,
                    [value.strip() for value in matrix.group(1).split(",")],
                    "Required recovery profiles must run, not merely exist as CLI options",
                )
                result = subprocess.run(
                    ["bash", "-c", script],
                    env=dict(os.environ, NATIVE_SCENARIO=scenario),
                    capture_output=True,
                    check=True,
                    timeout=5,
                )
                arguments = [
                    item.decode() for item in result.stdout.split(b"\0") if item
                ]
                self.assertEqual(["--", flag], arguments)
                command = MODULE["scenario_command"](
                    "transfer",
                    "emulator-5556",
                    "emulator-5558",
                    Path("/pinned/app.apk"),
                    Path("/pinned/probe.apk"),
                    Path("/private/results"),
                    arguments[1:],
                )
                self.assertTrue(command[1].endswith("native-backup-acceptance.py"))
                self.assertIn("--seed-empty-source", command)
                self.assertEqual(flag, command[-1])
        self.assertIn('test "$RESULT" = success', workflow)

    def test_widget_profile_transfers_the_real_source_choice_before_restoring_the_registered_widget(
        self,
    ):
        try:
            command = MODULE["scenario_command"](
                "widget",
                "emulator-5556",
                "emulator-5558",
                Path("/pinned/app.apk"),
                Path("/pinned/probe.apk"),
                Path("/private/results"),
                [],
            )
        except ValueError as error:
            self.fail(f"Missing native widget acceptance route: {error}")
        self.assertTrue(command[1].endswith("native-widget-transfer-acceptance.py"))
        self.assertEqual("emulator-5556", command[command.index("--source") + 1])
        self.assertEqual("emulator-5558", command[command.index("--destination") + 1])
        self.assertIn("--seed-empty-source", command)
        self.assertTrue(
            MODULE["acceptance_receipt"]("widget", [], [])[
                "crossDeviceTransferAccepted"
            ]
        )

    def test_focused_network_policy_profile_keeps_custody_without_claiming_a_transfer(
        self,
    ):
        try:
            command = MODULE["scenario_command"](
                "network-policy",
                "emulator-5556",
                "emulator-5558",
                Path("/pinned/app.apk"),
                Path("/pinned/probe.apk"),
                Path("/private/results"),
                [],
            )
        except ValueError as error:
            self.fail(f"Missing focused policy acceptance route: {error}")
        self.assertTrue(command[1].endswith("native-missing-media-acceptance.py"))
        self.assertEqual("emulator-5556", command[command.index("--emulator") + 1])
        self.assertEqual(
            ["emulator-5556", "emulator-5558"],
            [
                command[index + 1]
                for index, value in enumerate(command)
                if value == "--emulator"
            ],
        )
        self.assertIn("--network-only", command)
        self.assertIn("--synthetic-data", command)
        self.assertNotIn("--seed-empty-source", command)
        receipt = MODULE["acceptance_receipt"]("network-policy", [], [])
        self.assertFalse(receipt["managedNativeTransport"])
        self.assertFalse(receipt["crossDeviceTransferAccepted"])
        self.assertFalse(receipt["googleRestoreProviderAccepted"])

    def test_work_and_single_device_profiles_do_not_claim_cross_device_transfer(self):
        for mode in ("transfer", "publication", "enrollment", "d2d", "work-restart"):
            receipt = MODULE["acceptance_receipt"](mode, [], [])
            self.assertTrue(receipt["managedDeviceLifecycleAccepted"])
            self.assertEqual(
                mode in {"transfer", "publication", "enrollment"},
                receipt["crossDeviceTransferAccepted"],
            )
            self.assertEqual(mode != "work-restart", receipt["managedNativeTransport"])
            self.assertFalse(receipt["googleRestoreProviderAccepted"])

    def test_every_profile_uses_owned_targets_and_retains_its_native_oracle(self):
        for mode, harness, target_flag in (
            ("work-restart", "native-restore-work-restart-acceptance.py", "--emulator"),
            ("d2d", "native-d2d-acceptance.py", "--emulator"),
            ("enrollment", "native-device-enrollment-acceptance.py", "--source"),
        ):
            with self.subTest(mode=mode):
                command = MODULE["scenario_command"](
                    mode,
                    "emulator-5556",
                    "emulator-5558",
                    Path("/pinned/app.apk"),
                    Path("/pinned/probe.apk"),
                    Path("/private/results"),
                    [],
                    classpath=Path("/private/classpath"),
                    fixture_environment=Path("/private/environment.json"),
                )
                self.assertTrue(command[1].endswith(harness))
                self.assertEqual(
                    "emulator-5556", command[command.index(target_flag) + 1]
                )
                self.assertEqual(
                    "/pinned/app.apk", command[command.index("--app-apk") + 1]
                )
                if mode == "enrollment":
                    self.assertEqual(
                        "emulator-5558", command[command.index("--destination") + 1]
                    )
                    self.assertIn("--fixture-environment", command)
                if mode != "d2d":
                    self.assertIn("--seed-empty-source", command)
        with self.assertRaises(ValueError):
            MODULE["scenario_command"](
                "enrollment", "s", "d", Path("a"), Path("b"), Path("c"), []
            )

    def test_publication_mode_uses_the_existing_restore_and_trust_oracles(self):
        command = MODULE["scenario_command"](
            "publication",
            "emulator-5556",
            "emulator-5558",
            Path("/pinned/app.apk"),
            Path("/pinned/probe.apk"),
            Path("/private/results"),
            [],
        )
        self.assertTrue(command[1].endswith("native-publication-acceptance.py"))
        self.assertIn("--seed-empty-source", command)
        with self.assertRaises(ValueError):
            MODULE["scenario_command"](
                "publication",
                "emulator-5556",
                "emulator-5558",
                Path("a"),
                Path("b"),
                Path("c"),
                ["--exercise-quota"],
            )

    def test_oversized_combined_profiles_fail_before_starting_gradle(self):
        with self.assertRaises(ValueError):
            MODULE["acceptance_budget"](5, mode="enrollment")
        self.assertLessEqual(
            MODULE["acceptance_budget"](4, mode="enrollment")["driverMillis"],
            90 * 60_000,
        )

    def test_driver_lifetime_covers_all_host_phases_and_peer_boot_skew(self):
        for count in (0, 1, 3):
            with self.subTest(count=count):
                budget = MODULE["acceptance_budget"](count)
                self.assertGreaterEqual(
                    budget["driverMillis"],
                    1000
                    * (
                        budget["boot"]
                        + budget["native"]
                        + count * budget["followup"]
                        + budget["teardown"]
                    ),
                )

    def test_account_job_acceptance_uses_the_owned_transfer_destination(self):
        command = MODULE["followup_command"](
            "account-job",
            "emulator-5558",
            Path("/pinned/app.apk"),
            Path("/pinned/probe.apk"),
            Path("/private/account-job"),
        )
        self.assertTrue(command[1].endswith("native-account-job-acceptance.py"))
        self.assertEqual("emulator-5558", command[command.index("--emulator") + 1])
        self.assertIn("--synthetic-data", command)

    def test_followups_keep_the_owned_target_and_pinned_artifacts(self):
        command = MODULE["followup_command"](
            "missing-media",
            "emulator-5558",
            Path("/pinned/app.apk"),
            Path("/pinned/probe.apk"),
            Path("/private/results"),
        )
        self.assertIn("--synthetic-data", command)
        self.assertEqual("emulator-5558", command[command.index("--emulator") + 1])
        self.assertEqual("/pinned/app.apk", command[command.index("--app-apk") + 1])
        self.assertEqual("/pinned/probe.apk", command[command.index("--test-apk") + 1])
        self.assertTrue(command[1].endswith("native-missing-media-acceptance.py"))
        with self.assertRaises(ValueError):
            MODULE["followup_command"](
                "../../other", "emulator-5558", Path("a"), Path("b"), Path("c")
            )

    def test_exact_driver_method_names_occupy_different_android_runner_shards(self):
        driver = (
            Path(__file__).parents[2]
            / "tools/native-restore-driver/src/androidTest/kotlin/app/logdate/tools/restoredriver/NativeRestoreDriverTest.kt"
        )
        names = re.findall(r"@Test\s+fun (\w+)\(", driver.read_text())
        self.assertEqual(
            ["retainsManagedLifecycle1", "retainsManagedLifecycle2"], names
        )
        shards = []
        for name in names:
            value = 0
            for (
                character
            ) in f"{name}(app.logdate.tools.restoredriver.NativeRestoreDriverTest)":
                value = (31 * value + ord(character)) & 0xFFFFFFFF
            if value >= 2**31:
                value -= 2**32
            shards.append(abs(value) % 2)
        self.assertEqual({0, 1}, set(shards))

    def test_driver_and_harness_use_the_same_package_despite_inherited_overrides(self):
        environment = MODULE["managed_environment"](
            Path("/sdk"),
            {"LOGDATE_NATIVE_ACCEPTANCE_PACKAGE": "other.debug"},
            "studio.hypertext.logdate.transfer.debug",
        )
        self.assertEqual(
            "studio.hypertext.logdate.transfer.debug",
            environment["LOGDATE_NATIVE_ACCEPTANCE_PACKAGE"],
        )

    def test_native_arguments_cannot_override_owned_devices_or_attested_apks(self):
        for arguments in (
            ["--source", "physical"],
            ["--app-apk", "/other.apk"],
            ["--test-apk=/other.apk"],
            ["--output", "/other"],
            ["--resume-seeded-source"],
            ["--identity", "unknown"],
        ):
            with self.subTest(arguments=arguments):
                with self.assertRaises(ValueError):
                    MODULE["validate_native_arguments"](arguments)
        MODULE["validate_native_arguments"](
            ["--identity", "account", "--exercise-partial-media", "--exercise-quota"]
        )

    def test_pending_camera_probe_is_a_transfer_scenario_argument(self):
        arguments = ["--exercise-pending-camera-draft"]
        MODULE["validate_native_arguments"](arguments)
        command = MODULE["scenario_command"](
            "transfer",
            "emulator-5556",
            "emulator-5558",
            Path("/pinned/app.apk"),
            Path("/pinned/probe.apk"),
            Path("/private/results"),
            arguments,
        )
        self.assertTrue(command[1].endswith("native-backup-acceptance.py"))
        self.assertIn("--seed-empty-source", command)
        self.assertEqual("--exercise-pending-camera-draft", command[-1])


if __name__ == "__main__":
    unittest.main()
