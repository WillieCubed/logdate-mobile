from pathlib import Path
import json
import runpy
import subprocess
import tempfile
import unittest
import shutil
import time
from types import SimpleNamespace
from unittest.mock import Mock


EMULATOR = runpy.run_path(str(Path(__file__).with_name("managed-native-emulator.py")))
NONCE = "7b0e6278-04b1-4a0d-91bb-e860fe8602d3"
AVD = "dev34_google_apis_arm64-v8a_Pixel_9_Pro"
MANAGED_ID = "1011b945-34b2-4349-a963-fc37d94eccb6_GradleManagedDevice"
PREPARATION = runpy.run_path(
    str(Path(__file__).with_name("managed-native-preparation.py"))
)


class PreparationRetirementTest(unittest.TestCase):
    def setUp(self):
        self.args = [
            "-avd",
            AVD,
            "-read-only",
            "-no-snapshot-save",
            "-id",
            MANAGED_ID,
            "-force-snapshot-load",
        ]
        self.record = {
            "nonce": NONCE,
            "purpose": "preparation",
            "launcherPid": 123,
            "birth": "birth",
            "arguments": self.args,
        }
        self.current = {
            "birth": "birth",
            "command": "/sdk/qemu-system-arm64 " + " ".join(self.args),
        }
        self.now = 0
        self.signals = []

    def sleep(self, seconds):
        self.now += seconds

    def retire(self, current, stop):
        return PREPARATION["retire_preparation"](
            [self.record],
            NONCE,
            AVD,
            identity=lambda pid: current(),
            signal_process=stop,
            sleep=self.sleep,
            monotonic=lambda: self.now,
            grace=1,
        )

    def test_live_read_only_verifier_requires_confirmed_retirement(self):
        state = [self.current]

        def stop(pid, sig):
            self.signals.append((pid, sig))
            state[0] = None

        result = self.retire(lambda: state[0], stop)
        self.assertIsNone(state[0])
        self.assertEqual(1, len(self.signals))
        self.assertEqual(123, result[0]["pid"])
        self.assertTrue(result[0]["retired"])

    def test_async_shutdown_may_retire_without_any_signal_or_qemu_command(self):
        stop = Mock()

        def retiring():
            return {**self.current, "command": "<defunct>"} if self.now < 0.25 else None

        result = self.retire(retiring, stop)
        stop.assert_not_called()
        self.assertTrue(result[0]["retired"])
        self.assertEqual([], result[0]["cleanup"])

    def test_ignored_shutdown_cannot_count_as_retirement(self):
        with self.assertRaisesRegex(RuntimeError, "retire"):
            self.retire(
                lambda: self.current, lambda pid, sig: self.signals.append((pid, sig))
            )
        self.assertEqual(2, len(self.signals))

    def test_surviving_writable_snapshot_cannot_be_killed_or_admitted(self):
        self.record["arguments"] = [a for a in self.args if a != "-read-only"]
        stop = Mock()
        with self.assertRaisesRegex(RuntimeError, "writable|preparation"):
            self.retire(lambda: self.current, stop)
        stop.assert_not_called()

    def test_pid_reuse_cannot_receive_cleanup(self):
        stop = Mock()
        with self.assertRaisesRegex(RuntimeError, "identity"):
            self.retire(lambda: {**self.current, "birth": "new-birth"}, stop)
        stop.assert_not_called()

    def test_delayed_driver_invocation_cannot_be_cleaned_as_preparation(self):
        self.record["purpose"] = "drivers"
        stop = Mock()
        with self.assertRaisesRegex(RuntimeError, "purpose|preparation"):
            self.retire(lambda: self.current, stop)
        stop.assert_not_called()

    def test_pid_reuse_after_term_prevents_kill(self):
        state = [self.current]

        def stop(pid, sig):
            self.signals.append((pid, sig))
            state[0] = {**self.current, "birth": "reused"}

        with self.assertRaisesRegex(RuntimeError, "identity"):
            self.retire(lambda: state[0], stop)
        self.assertEqual(1, len(self.signals))

    def test_term_resistance_requires_confirmed_kill_then_exit(self):
        state = [self.current]

        def stop(pid, sig):
            self.signals.append((pid, sig))
            if sig.name == "SIGKILL":
                state[0] = None

        result = self.retire(lambda: state[0], stop)
        self.assertEqual(["SIGTERM", "SIGKILL"], result[0]["cleanup"])
        self.assertIsNone(state[0])

    def test_live_snapshot_query_is_not_a_cleanup_target(self):
        self.record["arguments"] = [
            "-avd",
            AVD,
            "-check-snapshot-loadable",
            "default_boot",
        ]
        stop = Mock()
        with self.assertRaisesRegex(RuntimeError, "unknown preparation"):
            self.retire(lambda: self.current, stop)
        stop.assert_not_called()

    def test_already_retired_writable_preparation_is_accounted_without_signal(self):
        self.record["arguments"] = [a for a in self.args if a != "-read-only"]
        stop = Mock()
        result = self.retire(lambda: None, stop)
        self.assertEqual([{"pid": 123, "retired": True, "cleanup": []}], result)
        stop.assert_not_called()


class PreparationLaunchTest(unittest.TestCase):
    def launch(self, purpose):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            sdk = root / "sdk"
            (sdk / "emulator").mkdir(parents=True)
            binary = sdk / "emulator/emulator"
            binary.write_text("#!/bin/sh\nprintf '%s\\n' \"$@\"\n")
            binary.chmod(0o700)
            facade = EMULATOR["create_managed_sdk"](sdk, root / "facade", AVD, NONCE)
            config_path = facade / "managed-launch.json"
            config = json.loads(config_path.read_text())
            config["purpose"] = purpose
            config_path.write_text(json.dumps(config))
            args = [
                "-avd",
                AVD,
                "-read-only",
                "-no-snapshot-save",
                "-id",
                MANAGED_ID,
                "-force-snapshot-load",
            ]
            result = subprocess.run(
                [str(facade / "emulator/emulator"), *args],
                capture_output=True,
                text=True,
                timeout=10,
            )
            receipts = list((root / "managed-emulator-launches").glob("*.json"))
            return args, result, [json.loads(p.read_text()) for p in receipts]

    def test_snapshot_verification_keeps_its_real_snapshot_load(self):
        args, result, receipts = self.launch("preparation")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(args, result.stdout.splitlines())
        self.assertEqual("preparation", receipts[0].get("purpose"))
        self.assertFalse(receipts[0]["coldBoot"])

    def test_same_arguments_cold_boot_only_in_driver_invocation(self):
        args, result, receipts = self.launch("drivers")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(
            [a for a in args if a != "-force-snapshot-load"] + ["-no-snapshot-load"],
            result.stdout.splitlines(),
        )
        self.assertEqual("drivers", receipts[0].get("purpose"))
        self.assertTrue(receipts[0]["coldBoot"])
        self.assertTrue(receipts[0]["birth"].strip())

    def test_unknown_phase_cannot_acquire_driver_authority(self):
        _, result, receipts = self.launch("unknown")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual([], receipts)


class PreparationInvocationTest(unittest.TestCase):
    def test_driver_preflight_cannot_launch_after_the_shared_budget_expires(self):
        clock = [1]
        process = Mock()
        h = SimpleNamespace(
            time=SimpleNamespace(monotonic=lambda: clock[0]),
            ROOT=Path("fixture"),
            subprocess=SimpleNamespace(Popen=process, STDOUT=subprocess.STDOUT),
        )
        boot_deadline = 2
        # Preparation returned before the deadline; SDK verification consumed the remainder.
        clock[0] = 3
        with self.assertRaisesRegex(RuntimeError, "boot budget"):
            PREPARATION["start_driver_generation"](
                h, ["gradle"], {}, None, boot_deadline
            )
        process.assert_not_called()

    def test_installed_image_mapping_refuses_missing_or_different_runtime(self):
        with tempfile.TemporaryDirectory() as folder:
            sdk = Path(folder)
            image = sdk / "system-images/android-34/google_apis/arm64-v8a"
            image.mkdir(parents=True)
            check = PREPARATION.get("require_cached_image", lambda sdk, avd: None)
            with self.assertRaisesRegex(RuntimeError, "cached"):
                check(sdk, AVD)
            (image / "system.img").write_bytes(b"synthetic-runtime")
            (image / "package.xml").write_text(
                '<repository><localPackage path="system-images;android-34;google_apis_playstore;arm64-v8a"/></repository>'
            )
            with self.assertRaisesRegex(RuntimeError, "mapping"):
                check(sdk, AVD)
            (image / "package.xml").write_text(
                '<repository><localPackage path="system-images;android-34;google_apis;arm64-v8a"/></repository>'
            )
            self.assertEqual(
                "system-images;android-34;google_apis;arm64-v8a",
                check(sdk, AVD)["package"],
            )

    def test_setup_is_terminal_and_retired_before_returning_driver_authority(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            image = root / "system-images/android-34/google_apis/arm64-v8a"
            image.mkdir(parents=True)
            (image / "system.img").write_bytes(b"synthetic-runtime")
            (image / "package.xml").write_text(
                '<repository><localPackage path="system-images;android-34;google_apis;arm64-v8a"/></repository>'
            )
            facade = root / "facade"
            facade.mkdir()
            process = SimpleNamespace(
                pid=100, returncode=0, wait=Mock(), poll=lambda: 0
            )
            create, stopped = Mock(return_value=facade), Mock(return_value=True)
            save = Mock()
            h = SimpleNamespace(
                EMULATOR={
                    "create_managed_sdk": create,
                    "managed_processes_stopped": stopped,
                },
                managed_environment=Mock(return_value={}),
                os=SimpleNamespace(environ={}, kill=Mock()),
                subprocess=SimpleNamespace(
                    Popen=Mock(return_value=process), STDOUT=subprocess.STDOUT
                ),
                ROOT=root,
                save=save,
                discovery_identity=Mock(return_value=None),
                time=time,
                process_snapshot=Mock(return_value=[]),
                shutil=shutil,
            )
            PREPARATION["prepare_managed_device"](
                h, root, root, AVD, NONCE, "restorePairApi34", "fixture", {"boot": 600}
            )
            args = h.subprocess.Popen.call_args.args[0]
            self.assertEqual(
                ":tools:native-restore-driver:restorePairApi34Setup", args[1]
            )
            self.assertNotIn("-x", args)
            self.assertIn(
                "-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader", args
            )
            self.assertEqual("preparation", create.call_args.kwargs["purpose"])
            process.wait.assert_called_once()
            self.assertGreater(process.wait.call_args.kwargs["timeout"], 0)
            self.assertLessEqual(process.wait.call_args.kwargs["timeout"], 600)
            stopped.assert_called_once()
            self.assertTrue(save.call_args.args[1]["accepted"])
            self.assertTrue(
                facade.exists(),
                "The prepared AVD may still reference this SDK's image paths",
            )

    def test_driver_command_excludes_only_previously_completed_setup_and_bounds_compiler(
        self,
    ):
        h = SimpleNamespace(
            pinned_install_arguments=lambda app, probe: [str(app), str(probe)],
            DRIVER_CASE="fixture.Driver",
        )
        args = PREPARATION["driver_arguments"](
            h,
            "nativeRestorePair",
            "restorePairApi34",
            Path("app"),
            Path("probe"),
            NONCE,
            ["app-sha", "probe-sha"],
            {"driverMillis": 1000},
            4,
        )
        self.assertEqual(
            ":tools:native-restore-driver:nativeRestorePairGroupDebugAndroidTest",
            args[1],
        )
        self.assertEqual(1, args.count("-x"))
        self.assertEqual(
            ":tools:native-restore-driver:restorePairApi34Setup",
            args[args.index("-x") + 1],
        )
        self.assertIn(
            "-Pandroid.experimental.androidTest.numManagedDeviceShards=2", args
        )
        self.assertIn(
            "-Pandroid.testInstrumentationRunnerArguments.class=fixture.Driver", args
        )
        self.assertIn("-Pkotlin.compiler.execution.strategy=in-process", args)
        self.assertEqual(1, sum("-Xmx2048m" in arg for arg in args))


class ResourceLifetimeTest(unittest.TestCase):
    def test_cleanup_failure_cannot_mask_primary_acceptance_failure(self):
        entry = runpy.run_path(
            str(Path(__file__).with_name("managed-native-restore-acceptance.py"))
        )
        function = entry["run_managed_pair"]
        namespace = function.__globals__
        primary = RuntimeError("original acceptance failed")
        observer = Mock(
            close=Mock(side_effect=RuntimeError("private details must not escape"))
        )
        original = namespace["_managed_helpers"]["run_managed_pair"]

        def run(*args, resource_owner, **kwargs):
            resource_owner.append(observer)
            raise primary

        namespace["_managed_helpers"]["run_managed_pair"] = run
        try:
            with self.assertRaises(RuntimeError) as caught:
                function(SimpleNamespace())
            self.assertIs(primary, caught.exception)
            observer.close.assert_called_once()
            self.assertTrue(
                any(
                    "cleanup failed: RuntimeError" in note for note in primary.__notes__
                )
            )
            self.assertFalse(
                any("private details" in note for note in primary.__notes__)
            )
        finally:
            namespace["_managed_helpers"]["run_managed_pair"] = original


if __name__ == "__main__":
    unittest.main()
