from pathlib import Path
import json
import runpy
import tempfile
import hashlib
import base64
import unittest
from unittest.mock import Mock
import subprocess

MODULE = runpy.run_path(
    str(Path(__file__).with_name("managed-native-restore-acceptance.py"))
)


class ManagedDriverControllerTest(unittest.TestCase):
    def test_manual_account_uses_a_fresh_pair_and_focused_private_fixture(self):
        command = MODULE["scenario_command"](
            "manual-account",
            "emulator-5556",
            "emulator-5558",
            Path("/pinned/app.apk"),
            Path("/pinned/probe.apk"),
            Path("/private/results"),
            [],
            fixture_control=Path("/private/fixture/server-control"),
        )
        self.assertTrue(command[1].endswith("manual-account-recovery-acceptance.py"))
        self.assertEqual("emulator-5556", command[command.index("--source") + 1])
        self.assertEqual("emulator-5558", command[command.index("--destination") + 1])
        self.assertEqual(
            "/private/fixture/server-control",
            command[command.index("--fixture-control") + 1],
        )
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
                root / "evidence",
                "studio.hypertext.logdate.debug",
                root=root,
                run=build,
                recovery_assets=root / "private/assets",
            )
            arguments = build.call_args.args[0]
            self.assertIn(
                "-Plogdate.recoveryAcceptanceAssetsDir=" + str(root / "private/assets"),
                arguments,
            )

    def test_android_origin_is_derived_from_the_pinned_apk_signer(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            signer = root / "build-tools/36.1.0/apksigner"
            signer.parent.mkdir(parents=True)
            signer.touch()
            digest = bytes(range(32))
            execute = Mock(
                return_value=subprocess.CompletedProcess(
                    [],
                    0,
                    b"Signer #1 certificate SHA-256 digest: "
                    + digest.hex().encode()
                    + b"\n",
                )
            )
            origin = MODULE["android_signing_origin"](
                root, root / "app.apk", execute=execute
            )
            self.assertEqual(
                "android:apk-key-hash:"
                + base64.urlsafe_b64encode(digest).decode().rstrip("="),
                origin,
            )
            with self.assertRaisesRegex(RuntimeError, "exactly one signer"):
                MODULE["android_signing_origin"](
                    root,
                    root / "app.apk",
                    execute=Mock(
                        return_value=subprocess.CompletedProcess([], 0, b"no signer")
                    ),
                )

    def test_candidate_apks_are_assembled_before_the_managed_pair_starts(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            build = Mock(return_value=subprocess.CompletedProcess([], 0))
            MODULE["assemble_candidate_apks"](
                root / "evidence",
                "studio.hypertext.logdate.transfer.debug",
                root=root,
                run=build,
            )
            arguments = build.call_args.args[0]
            self.assertIn(":app:android-main:assembleDebug", arguments)
            self.assertIn(":app:android-main:assembleDebugAndroidTest", arguments)
            self.assertIn(
                "-Plogdate.applicationId=studio.hypertext.logdate.transfer", arguments
            )
            self.assertEqual(root, build.call_args.kwargs["cwd"])
            self.assertTrue((root / "evidence/candidate-build.log").is_file())

    def test_candidate_build_rejects_unrelated_or_non_debug_package(self):
        with tempfile.TemporaryDirectory() as folder:
            build = Mock()
            for package in (
                "studio.hypertext.other.debug",
                "studio.hypertext.logdate",
                "bad;command.debug",
            ):
                with self.subTest(package=package), self.assertRaises(ValueError):
                    MODULE["assemble_candidate_apks"](Path(folder), package, run=build)
            build.assert_not_called()

    def test_default_candidate_build_uses_the_production_base_application_id(self):
        with tempfile.TemporaryDirectory() as folder:
            build = Mock(return_value=subprocess.CompletedProcess([], 0))
            MODULE["assemble_candidate_apks"](
                Path(folder),
                "studio.hypertext.logdate.debug",
                root=Path(folder),
                run=build,
            )
            self.assertFalse(
                any(
                    argument.startswith("-Plogdate.applicationId=")
                    for argument in build.call_args.args[0]
                )
            )

    def test_pinned_app_and_probe_must_match_the_selected_package(self):
        with tempfile.TemporaryDirectory() as folder:
            sdk = Path(folder)
            aapt2 = sdk / "build-tools/36.1.0/aapt2"
            aapt2.parent.mkdir(parents=True)
            aapt2.touch()
            app, probe = sdk / "app.apk", sdk / "probe.apk"
            package = "studio.hypertext.logdate.transfer.debug"
            execute = Mock(
                side_effect=[
                    subprocess.CompletedProcess([], 0, package.encode()),
                    subprocess.CompletedProcess([], 0, f"{package}.test".encode()),
                ]
            )
            MODULE["verify_pinned_packages"](sdk, app, probe, package, execute=execute)
            self.assertEqual(str(aapt2), execute.call_args_list[0].args[0])
            mismatch = Mock(
                return_value=subprocess.CompletedProcess(
                    [], 0, b"studio.hypertext.logdate.debug"
                )
            )
            with self.assertRaisesRegex(
                RuntimeError, "Pinned app.apk has application ID"
            ):
                MODULE["verify_pinned_packages"](
                    sdk, app, probe, package, execute=mismatch
                )

    def test_gradle_installs_the_private_artifact_pair_even_when_build_settings_change(
        self,
    ):
        with tempfile.TemporaryDirectory(prefix="pinned apks ") as folder:
            app, probe = Path(folder) / "app.apk", Path(folder) / "probe.apk"
            app.write_bytes(b"selected-app-207")
            probe.write_bytes(b"selected-probe-207")
            arguments = MODULE["pinned_install_arguments"](app, probe)
            self.assertEqual(
                [
                    f"-Plogdate.nativeAcceptanceAppApk={app}",
                    f"-Plogdate.nativeAcceptanceProbeApk={probe}",
                ],
                arguments,
            )
            self.assertNotIn(":app:android-main:assembleDebug", arguments)

    def test_pinned_installation_rejects_relative_or_missing_artifacts(self):
        with tempfile.TemporaryDirectory() as folder:
            present = Path(folder) / "app.apk"
            present.touch()
            for app, probe in (
                (Path("relative.apk"), present),
                (present, Path(folder) / "missing.apk"),
            ):
                with self.subTest(app=app, probe=probe), self.assertRaises(ValueError):
                    MODULE["pinned_install_arguments"](app, probe)

    def upgrade_fixture(self, root):
        document = dict(
            baselineVersion=205,
            baselineSchema=55,
            candidateVersion=208,
            candidateSchema=58,
        )
        for role in ("baselineApp", "baselineProbe", "candidateApp"):
            artifact = root / (role + ".apk")
            artifact.write_bytes(role.encode())
            document[role] = dict(
                path=str(artifact),
                sha256=hashlib.sha256(artifact.read_bytes()).hexdigest(),
            )
        fixture = root / "fixture.json"
        fixture.write_text(json.dumps(document))
        fixture.chmod(0o600)
        return fixture, document

    def test_upgrade_starts_with_released_apks_and_freezes_fixture_before_managed_boot(
        self,
    ):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fixture, document = self.upgrade_fixture(root)
            candidate = Path(document["candidateApp"]["path"])
            probe = root / "candidate-probe.apk"
            probe.write_bytes(b"new probe")
            output = root / "private"
            output.mkdir()
            app, initial_probe, frozen = MODULE["initial_installation"](
                "upgrade", candidate, probe, fixture, output
            )
            self.assertEqual(b"baselineApp", app.read_bytes())
            self.assertEqual(b"baselineProbe", initial_probe.read_bytes())
            self.assertNotEqual(fixture, frozen)
            saved = json.loads(frozen.read_text())
            self.assertEqual(0o600, frozen.stat().st_mode & 0o777)
            for role in ("baselineApp", "baselineProbe", "candidateApp"):
                self.assertTrue(Path(saved[role]["path"]).is_relative_to(output))
                self.assertEqual(document[role]["sha256"], saved[role]["sha256"])
            Path(document["baselineApp"]["path"]).write_bytes(
                b"changed after launch preparation"
            )
            fixture.write_text("changed original fixture")
            self.assertEqual(b"baselineApp", app.read_bytes())
            self.assertEqual(58, json.loads(frozen.read_text())["candidateSchema"])

    def test_upgrade_rejects_a_candidate_that_differs_from_its_fixture_before_boot(
        self,
    ):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            fixture, _ = self.upgrade_fixture(root)
            candidate = root / "wrong.apk"
            candidate.write_bytes(b"wrong candidate")
            output = root / "private"
            output.mkdir()
            with self.assertRaises(RuntimeError):
                MODULE["initial_installation"](
                    "upgrade", candidate, candidate, fixture, output
                )

    def test_other_modes_keep_candidate_apks_without_reading_upgrade_fixture(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            candidate, probe = root / "candidate.apk", root / "probe.apk"
            missing = root / "missing-fixture"
            for mode in ("transfer", "widget", "network-policy"):
                self.assertEqual(
                    (candidate, probe, missing),
                    MODULE["initial_installation"](
                        mode, candidate, probe, missing, root
                    ),
                )
            self.assertEqual([], list(root.iterdir()))

    def test_released_upgrade_uses_owned_pair_pinned_probe_and_private_artifact_fixture(
        self,
    ):
        command = MODULE["scenario_command"](
            "upgrade",
            "emulator-5556",
            "emulator-5558",
            Path("/pinned/app.apk"),
            Path("/pinned/probe.apk"),
            Path("/private/results"),
            [],
            fixture_environment=Path("/private/compatibility.json"),
        )
        self.assertTrue(command[1].endswith("native-released-upgrade-acceptance.py"))
        self.assertIn("--seed-empty-source", command)
        self.assertEqual(
            "/private/compatibility.json",
            command[command.index("--fixture-environment") + 1],
        )
        self.assertEqual("/pinned/probe.apk", command[command.index("--test-apk") + 1])
        self.assertTrue(
            MODULE["acceptance_receipt"]("upgrade", [], [])[
                "crossDeviceTransferAccepted"
            ]
        )
        with self.assertRaises(ValueError):
            MODULE["scenario_command"](
                "upgrade", "s", "d", Path("/a"), Path("/b"), Path("/c"), []
            )

    def test_upgrade_followups_cannot_reinstall_the_initial_lower_version(self):
        MODULE["validate_followups"]("upgrade", [])
        for followups in (["storage"], ["missing-media", "ui"]):
            with self.subTest(followups=followups), self.assertRaises(ValueError):
                MODULE["validate_followups"]("upgrade", followups)
        MODULE["validate_followups"]("enrollment", ["storage", "ui"])
        with self.assertRaises(ValueError):
            MODULE["validate_followups"]("transfer", ["ui", "ui"])

    def test_pinned_candidate_pair_can_skip_rebuilding_the_application(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            app = root / "app.apk"
            probe = root / "probe.apk"
            app.touch()
            probe.touch()
            self.assertEqual(
                (app.resolve(), probe.resolve()),
                MODULE["candidate_apk_paths"](app, probe, root),
            )
            with self.assertRaisesRegex(ValueError, "both pinned candidate APK paths"):
                MODULE["candidate_apk_paths"](app, None, root)
            with self.assertRaisesRegex(ValueError, "existing absolute files"):
                MODULE["candidate_apk_paths"](Path("relative.apk"), probe, root)


if __name__ == "__main__":
    unittest.main()
