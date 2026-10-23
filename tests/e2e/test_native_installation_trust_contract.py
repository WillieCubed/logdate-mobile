import ast
from contextlib import ExitStack
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name("native-backup-acceptance.py")
SPEC = importlib.util.spec_from_file_location("native_installation_trust_contract", SCRIPT)
HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HARNESS)


class InstallationTrustContractTest(unittest.TestCase):
    def probe_arguments(self, flag):
        metadata = dict(owner="owner", device="source-device", fingerprint="fingerprint",
                        mediaNonce="nonce", mediaCiphertext="ciphertext")
        if flag is not None:
            metadata["installationTrustFixture"] = flag
        with tempfile.TemporaryDirectory() as directory, patch.object(HARNESS, "adb", return_value=b"OK (1 test)") as adb:
            HARNESS.probe("emulator-5554", "verify-restored", Path(directory) / "probe.log", metadata)
        return adb.call_args.args

    def test_source_trust_requires_explicit_seed_opt_in(self):
        for flag in (True, False, "true", 1, None):
            with self.subTest(flag=flag), tempfile.TemporaryDirectory() as directory, patch.object(
                HARNESS, "adb", return_value=b"OK (1 test)"
            ) as adb:
                HARNESS.probe("emulator-5554", "seed", Path(directory) / "probe.log", installation_trust=flag)
                self.assertEqual(flag is True, "nativeInstallationTrustFixture" in adb.call_args.args)

    def test_seeded_installation_trust_is_explicitly_verified_at_destination(self):
        arguments = self.probe_arguments(True)
        self.assertIn("sourceInstallationTrustFixture", arguments)
        index = arguments.index("sourceInstallationTrustFixture")
        self.assertEqual(("-e", "sourceInstallationTrustFixture", "true"), arguments[index - 1:index + 2])

    def test_post_negotiation_replay_verifies_the_retained_destination_trust(self):
        events = []
        metadata = {"installationTrustFixture": True}
        with tempfile.TemporaryDirectory() as directory, patch.object(
            HARNESS, "adb", side_effect=lambda *args: events.append(("stop", args))
        ), patch.object(HARNESS, "restore_transport", side_effect=lambda *args, **kwargs: events.append(("restore", args)) or b"restoreFinished: 0"), patch.object(
            HARNESS, "probe", side_effect=lambda *args: events.append(("verify", args))
        ):
            output = Path(directory)
            HARNESS.replay_destination_trust("emulator-5554", output, metadata, "phrase")
            self.assertEqual(["stop", "restore", "verify"], [event[0] for event in events])
            self.assertEqual(("emulator-5554", "shell", "am", "force-stop", HARNESS.PACKAGE), events[0][1])
            self.assertEqual(("emulator-5554", "verify-restored", output / "destination-trust-after-replay.log", metadata, "phrase"), events[2][1])

    def test_post_negotiation_replay_preserves_system_widget_delivery(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(HARNESS, "adb"), patch.object(
            HARNESS, "restore_transport", return_value=b"restoreFinished: 0"
        ) as restore, patch.object(HARNESS, "probe"), patch.object(HARNESS, "capture_widget_restore_delivery") as capture:
            output = Path(directory)
            HARNESS.replay_destination_trust("emulator-5554", output, {}, "phrase", system_widgets=True)
            restore.assert_called_once_with("emulator-5554", system_widgets=True)
            capture.assert_called_once_with("emulator-5554", output / "widget-delivery-after-negotiation")

    def test_failed_post_negotiation_restore_cannot_claim_retained_trust(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(HARNESS, "adb"), patch.object(
            HARNESS, "restore_transport", return_value=b"restore failed"
        ), patch.object(HARNESS, "probe") as probe:
            with self.assertRaisesRegex(RuntimeError, "post-negotiation"):
                HARNESS.replay_destination_trust("emulator-5554", Path(directory), {}, "phrase")
            probe.assert_not_called()

    def test_complete_duplicate_transfer_dispatches_replay_after_trust_and_before_restart(self):
        events = []
        metadata = {"installationTrustFixture": True}
        database = {"offlineEdit": False}
        def adb(*arguments):
            if arguments[1:3] == ("exec-out", "cat"):
                return json.dumps(metadata).encode()
            if "restore" in arguments:
                events.append("restore")
                database["offlineEdit"] = False
                return b"restoreFinished: 0"
            return b"synthetic transport"
        def probe(serial, mode, *args):
            events.append(mode)
            if mode == "verify":
                database["offlineEdit"] = True
        def restart(*args):
            events.append("restart")
            self.assertTrue(database["offlineEdit"], "The offline edit must be created after the last native database delivery")
        with tempfile.TemporaryDirectory() as directory, ExitStack() as stack:
            stack.enter_context(patch.object(HARNESS, "adb", side_effect=adb))
            for name in ("prepare_source", "assert_snapshot_preserved", "verify_restored_snapshot", "set_offline",
                         "restore_radio_state", "wait_for_timeline"):
                stack.enter_context(patch.object(HARNESS, name))
            stack.enter_context(patch.object(HARNESS, "backup_source", return_value=(b"backup", {})))
            stack.enter_context(patch.object(HARNESS, "package_file_snapshot", return_value={}))
            stack.enter_context(patch.object(HARNESS, "radio_state", return_value=(False, b"1")))
            stack.enter_context(patch.object(HARNESS, "probe", side_effect=probe))
            stack.enter_context(patch.object(HARNESS, "verify_restarted_content", side_effect=restart))
            HARNESS.transfer("emulator-5554", "emulator-5556", Path("app.apk"), Path("test.apk"),
                             Path(directory), "phrase", exercise_duplicate_restore=True)
        self.assertEqual(["restore", "restore", "verify-restored", "restore", "verify-restored", "verify", "restart"], events)

    def test_enrollment_lane_selects_complete_duplicate_transfer(self):
        source = SCRIPT.with_name("native-device-enrollment-acceptance.py").read_text()
        calls = [node for node in ast.walk(ast.parse(source)) if isinstance(node, ast.Call)
                 and isinstance(node.func, ast.Subscript) and isinstance(node.func.value, ast.Name)
                 and node.func.value.id == "H" and isinstance(node.func.slice, ast.Constant)
                 and node.func.slice.value == "transfer"]
        self.assertEqual(1, len(calls))
        keywords = {keyword.arg: ast.literal_eval(keyword.value) for keyword in calls[0].keywords}
        self.assertIs(True, keywords["exercise_duplicate_restore"])
        self.assertFalse(keywords.get("exercise_partial_media", False))
        seed_calls = [node for node in ast.walk(ast.parse(source)) if isinstance(node, ast.Call)
                      and isinstance(node.func, ast.Subscript) and isinstance(node.func.slice, ast.Constant)
                      and node.func.slice.value == "probe" and len(node.args) > 1
                      and isinstance(node.args[1], ast.Constant) and node.args[1].value == "seed"]
        self.assertEqual(1, len(seed_calls))
        seed_keywords = {keyword.arg: ast.literal_eval(keyword.value) for keyword in seed_calls[0].keywords}
        self.assertIs(True, seed_keywords.get("installation_trust", False))
        run = next(node for node in ast.parse(source).body if isinstance(node, ast.FunctionDef) and node.name == "run_acceptance")
        guards = [node for node in ast.walk(run) if isinstance(node, ast.Call) and isinstance(node.func, ast.Name)
                  and node.func.id == "require_installation_trust_fixture"]
        self.assertEqual(1, len(guards))

    def test_legacy_and_unrecognized_fixture_flags_do_not_claim_trust_coverage(self):
        for flag in (None, False, "true", 1):
            with self.subTest(flag=flag):
                self.assertNotIn("sourceInstallationTrustFixture", self.probe_arguments(flag))


if __name__ == "__main__":
    unittest.main()
