from contextlib import redirect_stderr
from pathlib import Path
import io
import os
import json
import re
import runpy
import tempfile
import hashlib
import base64
import unittest
from unittest.mock import Mock
import subprocess

MODULE = runpy.run_path(str(Path(__file__).with_name("managed-native-restore-acceptance.py")))


class ManagedDriverControllerTest(unittest.TestCase):
    def test_process_snapshot_retries_a_transient_host_ps_timeout(self):
        execute = Mock(side_effect=[subprocess.TimeoutExpired("ps", 15),
                                    subprocess.CompletedProcess([], 0, b"123 qemu-system-aarch64\n")])
        sleep = Mock()

        lines = MODULE["process_snapshot"](execute=execute, sleep=sleep)

        self.assertEqual(["123 qemu-system-aarch64"], lines)
        self.assertEqual(2, execute.call_count)
        sleep.assert_called_once_with(0.2)

    def test_process_snapshot_gives_up_after_bounded_host_ps_timeouts(self):
        execute = Mock(side_effect=subprocess.TimeoutExpired("ps", 15))

        self.assertIsNone(MODULE["process_snapshot"](execute=execute, sleep=Mock()))
        self.assertEqual(2, execute.call_count)

    def test_managed_pair_boot_budget_starts_with_first_owned_qemu_not_gradle(self):
        deadline = None
        first_qemu_observed_at = 900.0
        deadline = MODULE["managed_boot_deadline"](deadline, first_qemu_observed_at, 600.0)
        self.assertEqual(1500.0, deadline)
        deadline = MODULE["managed_boot_deadline"](deadline, 1_200.0, 600.0)
        self.assertEqual(1500.0, deadline, "Later QEMU observations must not reset the boot budget")

    def test_manual_account_uses_a_fresh_pair_and_focused_private_fixture(self):
        command = MODULE["scenario_command"](
            "manual-account", "emulator-5556", "emulator-5558", Path("/pinned/app.apk"),
            Path("/pinned/probe.apk"), Path("/private/results"), [],
            fixture_control=Path("/private/fixture/server-control"),
        )
        self.assertTrue(command[1].endswith("manual-account-recovery-acceptance.py"))
        self.assertEqual("emulator-5556", command[command.index("--source") + 1])
        self.assertEqual("emulator-5558", command[command.index("--destination") + 1])
        self.assertEqual("/private/fixture/server-control", command[command.index("--fixture-control") + 1])
        self.assertIn("--focused", command)
        self.assertNotIn("--seed-empty-source", command)
        receipt = MODULE["acceptance_receipt"]("manual-account", [], [])
        self.assertFalse(receipt["crossDeviceTransferAccepted"])
        self.assertFalse(receipt["managedNativeTransport"])

    def test_manual_account_probe_build_receives_private_asset_directory(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            build = Mock(return_value=subprocess.CompletedProcess([], 0))
            (root / "private/assets").mkdir(parents=True)
            MODULE["assemble_candidate_apks"](
                root / "evidence", "studio.hypertext.logdate.debug", root=root, run=build,
                recovery_assets=root / "private/assets",
            )
            arguments = build.call_args.args[0]
            self.assertIn("-Plogdate.recoveryAcceptanceAssetsDir=" + str(root / "private/assets"), arguments)

    def test_android_origin_is_derived_from_the_pinned_apk_signer(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            signer = root / "build-tools/36.1.0/apksigner"
            signer.parent.mkdir(parents=True)
            signer.touch()
            digest = bytes(range(32))
            execute = Mock(return_value=subprocess.CompletedProcess(
                [], 0, b"Signer #1 certificate SHA-256 digest: " + digest.hex().encode() + b"\n"))
            origin = MODULE["android_signing_origin"](root, root / "app.apk", execute=execute)
            self.assertEqual("android:apk-key-hash:" + base64.urlsafe_b64encode(digest).decode().rstrip("="), origin)
            with self.assertRaisesRegex(RuntimeError, "exactly one signer"):
                MODULE["android_signing_origin"](
                    root, root / "app.apk", execute=Mock(return_value=subprocess.CompletedProcess([], 0, b"no signer")))

    def test_candidate_apks_are_assembled_before_the_managed_pair_starts(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            build = Mock(return_value=subprocess.CompletedProcess([], 0))
            MODULE["assemble_candidate_apks"](
                root / "evidence", "studio.hypertext.logdate.transfer.debug", root=root, run=build
            )
            arguments = build.call_args.args[0]
            self.assertIn(":app:android-main:assembleDebug", arguments)
            self.assertIn(":app:android-main:assembleDebugAndroidTest", arguments)
            self.assertIn("-Plogdate.applicationId=studio.hypertext.logdate.transfer", arguments)
            self.assertEqual(root, build.call_args.kwargs["cwd"])
            self.assertTrue((root / "evidence/candidate-build.log").is_file())

    def test_candidate_build_rejects_unrelated_or_non_debug_package(self):
        with tempfile.TemporaryDirectory() as folder:
            build = Mock()
            for package in ("studio.hypertext.other.debug", "studio.hypertext.logdate", "bad;command.debug"):
                with self.subTest(package=package), self.assertRaises(ValueError):
                    MODULE["assemble_candidate_apks"](Path(folder), package, run=build)
            build.assert_not_called()

    def test_default_candidate_build_uses_the_production_base_application_id(self):
        with tempfile.TemporaryDirectory() as folder:
            build = Mock(return_value=subprocess.CompletedProcess([], 0))
            MODULE["assemble_candidate_apks"](
                Path(folder), "studio.hypertext.logdate.debug", root=Path(folder), run=build
            )
            self.assertFalse(any(argument.startswith("-Plogdate.applicationId=") for argument in build.call_args.args[0]))

    def test_pinned_app_and_probe_must_match_the_selected_package(self):
        with tempfile.TemporaryDirectory() as folder:
            sdk = Path(folder)
            aapt2 = sdk / "build-tools/36.1.0/aapt2"
            aapt2.parent.mkdir(parents=True)
            aapt2.touch()
            app, probe = sdk / "app.apk", sdk / "probe.apk"
            package = "studio.hypertext.logdate.transfer.debug"
            execute = Mock(side_effect=[subprocess.CompletedProcess([], 0, package.encode()),
                                        subprocess.CompletedProcess([], 0, f"{package}.test".encode())])
            MODULE["verify_pinned_packages"](sdk, app, probe, package, execute=execute)
            self.assertEqual(str(aapt2), execute.call_args_list[0].args[0])
            mismatch = Mock(return_value=subprocess.CompletedProcess([], 0, b"studio.hypertext.logdate.debug"))
            with self.assertRaisesRegex(RuntimeError, "Pinned app.apk has application ID"):
                MODULE["verify_pinned_packages"](sdk, app, probe, package, execute=mismatch)

    def test_cleanup_diagnostics_preserve_a_failure_on_python_without_exception_notes(self):
        class LegacyFailure(RuntimeError):
            def __getattribute__(self, name):
                if name == "add_note":
                    raise AttributeError(name)
                return super().__getattribute__(name)

        failure = LegacyFailure("managed restore failed")
        diagnostics = io.StringIO()
        with redirect_stderr(diagnostics):
            MODULE["add_failure_note"](failure, "Could not retain managed results")
        self.assertEqual("managed restore failed", str(failure))
        self.assertEqual(["Could not retain managed results"], failure.__notes__)
        self.assertIn("Could not retain managed results", diagnostics.getvalue())

    def test_diagnostic_stream_failure_does_not_replace_the_primary_error(self):
        failure = RuntimeError("managed restore failed")
        unavailable = io.StringIO()
        unavailable.close()
        with redirect_stderr(unavailable):
            MODULE["add_failure_note"](failure, "Could not retain managed results")
        self.assertEqual(["Could not retain managed results"], failure.__notes__)

    def test_emulator_preflight_is_headless_and_retains_its_private_output(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            command = Mock(return_value=subprocess.CompletedProcess([], 0, b"emulator version", b"diagnostic"))
            MODULE["verify_managed_emulator"](output / "sdk", output, command=command)
            command.assert_called_once_with(str(output / "sdk/emulator/emulator"), "-no-window", "-version", check=False)
            log = output / "emulator-preflight.log"
            self.assertIn(b"emulator version", log.read_bytes())
            self.assertIn(b"diagnostic", log.read_bytes())
            self.assertEqual(0o600, log.stat().st_mode & 0o777)

    def test_failed_emulator_preflight_preserves_bounded_loader_evidence_and_exit(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            result = subprocess.CompletedProcess(["emulator"], 127, b"version", b"missing library\n" + b"x" * 20_000)
            with self.assertRaises(subprocess.CalledProcessError) as failure:
                MODULE["verify_managed_emulator"](output / "sdk", output, command=Mock(return_value=result))
            self.assertEqual(127, failure.exception.returncode)
            evidence = (output / "emulator-preflight.log").read_bytes()
            self.assertIn(b"missing library", evidence)
            self.assertLess(len(evidence), 33_000)

    def test_ci_runs_required_recovery_profiles_through_the_owned_transfer_controller(self):
        root = Path(__file__).resolve().parents[2]
        workflow = (root / ".github/workflows/native-restore-acceptance.yml").read_text()
        matrix = re.search(r"scenario: \[([^\]]+)\]", workflow)
        self.assertIsNotNone(matrix, "Native restore scenarios must be enumerated")
        dispatch = re.search(r'          case "\$NATIVE_SCENARIO" in\n(.*?)          esac', workflow, re.DOTALL)
        self.assertIsNotNone(dispatch)
        script = ('set -euo pipefail\narguments=()\ncase "$NATIVE_SCENARIO" in\n'
                  + dispatch.group(1) + 'esac\nprintf \'%s\\0\' "${arguments[@]}"\n')
        for scenario, flag in (("archive", "--exercise-archive-recovery"), ("data-only", "--exercise-data-only")):
            with self.subTest(scenario=scenario):
                self.assertIn(scenario, [value.strip() for value in matrix.group(1).split(",")],
                              "Required recovery profiles must run, not merely exist as CLI options")
                result = subprocess.run(["bash", "-c", script], env=dict(os.environ, NATIVE_SCENARIO=scenario),
                                        capture_output=True, check=True, timeout=5)
                arguments = [item.decode() for item in result.stdout.split(b"\0") if item]
                self.assertEqual(["--", flag], arguments)
                command = MODULE["scenario_command"]("transfer", "emulator-5556", "emulator-5558",
                                                     Path("/pinned/app.apk"), Path("/pinned/probe.apk"),
                                                     Path("/private/results"), arguments[1:])
                self.assertTrue(command[1].endswith("native-backup-acceptance.py"))
                self.assertIn("--seed-empty-source", command)
                self.assertEqual(flag, command[-1])
        self.assertIn('test "$RESULT" = success', workflow)

    def test_failed_terminal_generation_retains_only_its_own_results(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            results = root / "gradle-results"
            results.mkdir()
            (results / "current.log").write_text("current native failure")
            (results / "old.log").write_text("obsolete generation")
            os.utime(results / "old.log", ns=(1, 1))
            output = root / "private-run"
            output.mkdir()
            process = Mock(poll=Mock(return_value=1))
            retained = MODULE["retain_terminal_results"](process, results, output, 2)
            self.assertIsNotNone(retained)
            self.assertEqual("current native failure", (retained / "current.log").read_text())
            self.assertFalse((retained / "old.log").exists())
            self.assertEqual(0o600, (retained / "current.log").stat().st_mode & 0o777)

    def test_live_generation_results_are_not_copied_as_terminal_evidence(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            self.assertIsNone(MODULE["retain_terminal_results"](Mock(poll=Mock(return_value=None)), root / "results", root, 1))
            self.assertFalse((root / "managed-results").exists())
    def test_widget_profile_transfers_the_real_source_choice_before_restoring_the_registered_widget(self):
        try:
            command = MODULE["scenario_command"]("widget", "emulator-5556", "emulator-5558", Path("/pinned/app.apk"),
                                                  Path("/pinned/probe.apk"), Path("/private/results"), [])
        except ValueError as error:
            self.fail(f"Missing native widget acceptance route: {error}")
        self.assertTrue(command[1].endswith("native-widget-transfer-acceptance.py"))
        self.assertEqual("emulator-5556", command[command.index("--source") + 1])
        self.assertEqual("emulator-5558", command[command.index("--destination") + 1])
        self.assertIn("--seed-empty-source", command)
        self.assertTrue(MODULE["acceptance_receipt"]("widget", [], [])["crossDeviceTransferAccepted"])

    def test_focused_network_policy_profile_keeps_custody_without_claiming_a_transfer(self):
        try:
            command = MODULE["scenario_command"]("network-policy", "emulator-5556", "emulator-5558", Path("/pinned/app.apk"),
                                                  Path("/pinned/probe.apk"), Path("/private/results"), [])
        except ValueError as error:
            self.fail(f"Missing focused policy acceptance route: {error}")
        self.assertTrue(command[1].endswith("native-missing-media-acceptance.py"))
        self.assertEqual("emulator-5556", command[command.index("--emulator") + 1])
        self.assertEqual(["emulator-5556", "emulator-5558"],
                         [command[index + 1] for index, value in enumerate(command) if value == "--emulator"])
        self.assertIn("--network-only", command)
        self.assertIn("--synthetic-data", command)
        self.assertNotIn("--seed-empty-source", command)
        receipt = MODULE["acceptance_receipt"]("network-policy", [], [])
        self.assertFalse(receipt["managedNativeTransport"])
        self.assertFalse(receipt["crossDeviceTransferAccepted"])
        self.assertFalse(receipt["googleRestoreProviderAccepted"])

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

    def upgrade_fixture(self, root):
        document = dict(baselineVersion=205, baselineSchema=55, candidateVersion=208, candidateSchema=58)
        for role in ("baselineApp", "baselineProbe", "candidateApp"):
            artifact = root / (role + ".apk")
            artifact.write_bytes(role.encode())
            document[role] = dict(path=str(artifact), sha256=hashlib.sha256(artifact.read_bytes()).hexdigest())
        fixture = root / "fixture.json"
        fixture.write_text(json.dumps(document))
        fixture.chmod(0o600)
        return fixture, document

    def test_upgrade_starts_with_released_apks_and_freezes_fixture_before_managed_boot(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fixture, document = self.upgrade_fixture(root)
            candidate = Path(document["candidateApp"]["path"])
            probe = root / "candidate-probe.apk"
            probe.write_bytes(b"new probe")
            output = root / "private"
            output.mkdir()
            app, initial_probe, frozen = MODULE["initial_installation"]("upgrade", candidate, probe, fixture, output)
            self.assertEqual(b"baselineApp", app.read_bytes())
            self.assertEqual(b"baselineProbe", initial_probe.read_bytes())
            self.assertNotEqual(fixture, frozen)
            saved = json.loads(frozen.read_text())
            self.assertEqual(0o600, frozen.stat().st_mode & 0o777)
            for role in ("baselineApp", "baselineProbe", "candidateApp"):
                self.assertTrue(Path(saved[role]["path"]).is_relative_to(output))
                self.assertEqual(document[role]["sha256"], saved[role]["sha256"])
            Path(document["baselineApp"]["path"]).write_bytes(b"changed after launch preparation")
            fixture.write_text("changed original fixture")
            self.assertEqual(b"baselineApp", app.read_bytes())
            self.assertEqual(58, json.loads(frozen.read_text())["candidateSchema"])

    def test_upgrade_rejects_a_candidate_that_differs_from_its_fixture_before_boot(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fixture, _ = self.upgrade_fixture(root)
            candidate = root / "wrong.apk"
            candidate.write_bytes(b"wrong candidate")
            output = root / "private"
            output.mkdir()
            with self.assertRaises(RuntimeError):
                MODULE["initial_installation"]("upgrade", candidate, candidate, fixture, output)

    def test_other_modes_keep_candidate_apks_without_reading_upgrade_fixture(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            candidate, probe = root / "candidate.apk", root / "probe.apk"
            missing = root / "missing-fixture"
            for mode in ("transfer", "widget", "network-policy"):
                self.assertEqual((candidate, probe, missing), MODULE["initial_installation"](mode, candidate, probe, missing, root))
            self.assertEqual([], list(root.iterdir()))

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

    def test_released_upgrade_uses_owned_pair_pinned_probe_and_private_artifact_fixture(self):
        command = MODULE["scenario_command"]("upgrade", "emulator-5556", "emulator-5558", Path("/pinned/app.apk"),
                                              Path("/pinned/probe.apk"), Path("/private/results"), [],
                                              fixture_environment=Path("/private/compatibility.json"))
        self.assertTrue(command[1].endswith("native-released-upgrade-acceptance.py"))
        self.assertIn("--seed-empty-source", command)
        self.assertEqual("/private/compatibility.json", command[command.index("--fixture-environment") + 1])
        self.assertEqual("/pinned/probe.apk", command[command.index("--test-apk") + 1])
        self.assertTrue(MODULE["acceptance_receipt"]("upgrade", [], [])["crossDeviceTransferAccepted"])
        with self.assertRaises(ValueError):
            MODULE["scenario_command"]("upgrade", "s", "d", Path("/a"), Path("/b"), Path("/c"), [])

    def test_publication_mode_uses_the_existing_restore_and_trust_oracles(self):
        command = MODULE["scenario_command"]("publication", "emulator-5556", "emulator-5558", Path("/pinned/app.apk"),
                                              Path("/pinned/probe.apk"), Path("/private/results"), [])
        self.assertTrue(command[1].endswith("native-publication-acceptance.py"))
        self.assertIn("--seed-empty-source", command)
        with self.assertRaises(ValueError):
            MODULE["scenario_command"]("publication", "emulator-5556", "emulator-5558", Path("a"), Path("b"), Path("c"), ["--exercise-quota"])

    def test_enrollment_keeps_server_and_outer_driver_alive_for_all_draft_phases(self):
        budget = MODULE["acceptance_budget"](0, mode="enrollment")
        self.assertEqual(1800, budget["native"])
        self.assertGreaterEqual(budget["driverMillis"], 1000 * (budget["boot"] + budget["native"] + budget["teardown"]))
        self.assertEqual(1200, MODULE["acceptance_budget"](0, mode="transfer")["native"])

    def test_oversized_combined_profiles_fail_before_starting_gradle(self):
        with self.assertRaises(ValueError):
            MODULE["acceptance_budget"](5, mode="enrollment")
        self.assertLessEqual(MODULE["acceptance_budget"](4, mode="enrollment")["driverMillis"], 90 * 60_000)

    def test_driver_lifetime_covers_all_host_phases_and_peer_boot_skew(self):
        for count in (0, 1, 3):
            with self.subTest(count=count):
                budget = MODULE["acceptance_budget"](count)
                self.assertGreaterEqual(budget["driverMillis"],
                                        1000 * (budget["boot"] + budget["native"] + count * budget["followup"] + budget["teardown"]))

    def test_upgrade_followups_cannot_reinstall_the_initial_lower_version(self):
        MODULE["validate_followups"]("upgrade", [])
        for followups in (["storage"], ["missing-media", "ui"]):
            with self.subTest(followups=followups), self.assertRaises(ValueError):
                MODULE["validate_followups"]("upgrade", followups)
        MODULE["validate_followups"]("enrollment", ["storage", "ui"])
        with self.assertRaises(ValueError):
            MODULE["validate_followups"]("transfer", ["ui", "ui"])

    def test_account_job_acceptance_uses_the_owned_transfer_destination(self):
        command = MODULE["followup_command"]("account-job", "emulator-5558", Path("/pinned/app.apk"),
                                             Path("/pinned/probe.apk"), Path("/private/account-job"))
        self.assertTrue(command[1].endswith("native-account-job-acceptance.py"))
        self.assertEqual("emulator-5558", command[command.index("--emulator") + 1])
        self.assertIn("--synthetic-data", command)

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
        environment = MODULE["managed_environment"](
            Path("/sdk"), {"LOGDATE_NATIVE_ACCEPTANCE_PACKAGE": "other.debug"},
            "studio.hypertext.logdate.transfer.debug"
        )
        self.assertEqual("studio.hypertext.logdate.transfer.debug", environment["LOGDATE_NATIVE_ACCEPTANCE_PACKAGE"])

    def test_native_arguments_cannot_override_owned_devices_or_attested_apks(self):
        for arguments in (["--source", "physical"], ["--app-apk", "/other.apk"], ["--test-apk=/other.apk"],
                          ["--output", "/other"], ["--resume-seeded-source"], ["--identity", "unknown"]):
            with self.subTest(arguments=arguments):
                with self.assertRaises(ValueError):
                    MODULE["validate_native_arguments"](arguments)
        MODULE["validate_native_arguments"](["--identity", "account", "--exercise-partial-media", "--exercise-quota"])

    def test_pending_camera_probe_is_a_transfer_scenario_argument(self):
        arguments = ["--exercise-pending-camera-draft"]
        MODULE["validate_native_arguments"](arguments)
        command = MODULE["scenario_command"](
            "transfer", "emulator-5556", "emulator-5558", Path("/pinned/app.apk"),
            Path("/pinned/probe.apk"), Path("/private/results"), arguments,
        )
        self.assertTrue(command[1].endswith("native-backup-acceptance.py"))
        self.assertIn("--seed-empty-source", command)
        self.assertEqual("--exercise-pending-camera-draft", command[-1])

    def test_pinned_candidate_pair_can_skip_rebuilding_the_application(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app = root / "app.apk"
            probe = root / "probe.apk"
            app.touch()
            probe.touch()
            self.assertEqual((app.resolve(), probe.resolve()), MODULE["candidate_apk_paths"](app, probe, root))
            with self.assertRaisesRegex(ValueError, "both pinned candidate APK paths"):
                MODULE["candidate_apk_paths"](app, None, root)
            with self.assertRaisesRegex(ValueError, "existing absolute files"):
                MODULE["candidate_apk_paths"](Path("relative.apk"), probe, root)

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
            for bad in (case.replace("/>", "><skipped/></testcase>"), case.replace("/>", "><failure/></testcase>"),
                        case.replace("/>", "><error/></testcase>"), case.replace("retainsManaged", "unrelated"), case):
                (directory / "TEST-destination.xml").write_text(f"<testsuite>{bad}</testsuite>")
                with self.assertRaises(RuntimeError):
                    MODULE["require_outer_results"](directory)

    def test_canceled_outer_cases_do_not_count_as_completed(self):
        for message in ("Test driver was canceled", "DeferredCoroutine was cancelled", "job cancellation"):
            with self.subTest(message=message), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                cases = ''.join(
                    f'<testcase classname="app.logdate.tools.restoredriver.NativeRestoreDriverTest" '
                    f'name="retainsManagedLifecycle{index}" time="1.25"/>' for index in (1, 2)
                )
                (directory / "TEST-pair.xml").write_text(
                    f'<testsuite tests="2" failures="0" errors="0" skipped="0">{cases}'
                    f'<system-err>{message}</system-err></testsuite>'
                )
                with self.assertRaisesRegex(RuntimeError, "canceled"):
                    MODULE["require_outer_results"](directory)

    def test_invalid_outer_case_durations_do_not_count_as_completed(self):
        for duration in ("-1.0", "nan", "inf", "not-a-duration"):
            with self.subTest(duration=duration), tempfile.TemporaryDirectory() as temporary:
                directory = Path(temporary)
                cases = ''.join(
                    f'<testcase classname="app.logdate.tools.restoredriver.NativeRestoreDriverTest" '
                    f'name="retainsManagedLifecycle{index}" time="{duration}"/>' for index in (1, 2)
                )
                (directory / "TEST-pair.xml").write_text(f'<testsuite>{cases}</testsuite>')
                with self.assertRaisesRegex(RuntimeError, "duration"):
                    MODULE["require_outer_results"](directory)

    def test_completed_outer_pair_allows_ordinary_diagnostics(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            cases = ''.join(
                f'<testcase classname="app.logdate.tools.restoredriver.NativeRestoreDriverTest" '
                f'name="retainsManagedLifecycle{index}" time="1.25"/>' for index in (1, 2)
            )
            (directory / "TEST-pair.xml").write_text(
                f'<testsuite>{cases}<system-err>ordinary diagnostics</system-err></testsuite>'
            )
            MODULE["require_outer_results"](directory)

    def test_console_port_rejects_ambiguous_or_non_loopback_listener(self):
        self.assertEqual(5558, MODULE["console_port"]("n127.0.0.1:5558\nn127.0.0.1:5559\n"))
        self.assertIsNone(MODULE["console_port"]("n*:5558\n"))
        self.assertIsNone(MODULE["console_port"]("n127.0.0.1:5558\nn127.0.0.1:5560\n"))


if __name__ == "__main__":
    unittest.main()
