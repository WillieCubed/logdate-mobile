import argparse
import importlib.util
import json
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import Mock, patch
from types import SimpleNamespace


SCRIPT = Path(__file__).resolve().parents[3] / "tests/e2e/native-device-enrollment-acceptance.py"
SPEC = importlib.util.spec_from_file_location("native_device_enrollment_acceptance", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class NativeDeviceEnrollmentSafetyTest(unittest.TestCase):
    def setUp(self):
        for name in ("run", "Popen"):
            guard = patch.object(MODULE.subprocess, name, side_effect=AssertionError("Host safety tests cannot start external commands"))
            guard.start()
            self.addCleanup(guard.stop)

    def test_enrollment_ci_runtime_can_load_jvm_21_client_dependencies(self):
        workflow = (SCRIPT.parents[2] / ".github/workflows/native-restore-acceptance.yml").read_text()
        matrix_job = workflow.split("  native-transfer-scenarios:", 1)[1].split("  native-transfer:", 1)[0]
        self.assertIn("java-version: '21'", matrix_job)
        self.assertNotIn("java-version: '17'", matrix_job)

    def test_discovery_advertises_the_real_owned_emulator_server(self):
        previous = {"ATPROTO_PDS_SERVICE_URL": "https://outside.example"}
        actual = MODULE.server_environment(previous, 14321)
        self.assertEqual("http://10.0.2.2:14321", actual["ATPROTO_PDS_SERVICE_URL"])
        self.assertEqual("https://10.0.2.2", actual.get("WEBAUTHN_ORIGIN"))
        self.assertEqual("10.0.2.2", actual.get("WEBAUTHN_RP_ID"))
        self.assertEqual("https://10.0.2.2", actual.get("ATPROTO_OAUTH_ISSUER"))
        self.assertEqual("https://10.0.2.2", actual.get("ATPROTO_OAUTH_RESOURCE"))
        self.assertEqual("web", actual.get("ATPROTO_HOSTED_DID_METHOD"))
        self.assertEqual("https://outside.example", previous["ATPROTO_PDS_SERVICE_URL"])

    def test_physical_serial_is_rejected_before_adb(self):
        adb = Mock()
        with patch.dict(MODULE.H, adb=adb), self.assertRaises(argparse.ArgumentTypeError):
            MODULE.checked_emulator("physical-phone")
        adb.assert_not_called()

    def test_credentials_travel_as_stdin_and_not_shell_arguments(self):
        fixture = {"accessToken": "secret-access-$(never-run)", "refreshToken": "secret-refresh"}
        adb = Mock(return_value=json.dumps(fixture).encode())
        with patch.object(MODULE, "checked_emulator"), patch.dict(MODULE.H, adb=adb):
            MODULE.private_fixture("emulator-5558", fixture)
        call = next(call for call in adb.call_args_list if "input_bytes" in call.kwargs)
        self.assertEqual(fixture, json.loads(call.kwargs["input_bytes"]))
        for token in fixture.values():
            self.assertNotIn(token, " ".join(call.args))
        self.assertIn("no_backup/", " ".join(call.args))

    def test_truncated_fixture_payload_cannot_reach_the_native_probe(self):
        with patch.object(MODULE, "checked_emulator"), patch.dict(MODULE.H, adb=Mock(return_value=b"")):
            with self.assertRaisesRegex(RuntimeError, "round-trip"):
                MODULE.private_fixture("emulator-5558", {"accessToken": "synthetic"})

    def test_instrumentation_failure_or_skip_cannot_pass_a_success_summary(self):
        for status in (-1, -2, -3, -4):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as folder:
                output = Path(folder) / "result.log"
                raw = f"INSTRUMENTATION_STATUS_CODE: {status}\nOK (1 test)\n".encode()
                with patch.object(MODULE, "checked_emulator"), patch.dict(MODULE.H, adb=Mock(return_value=raw)):
                    with self.assertRaises(RuntimeError):
                        MODULE.probe("emulator-5558", "source-enroll", output)
                self.assertEqual(raw, output.read_bytes())
                self.assertEqual(0o600, stat.S_IMODE(output.stat().st_mode))

    def test_device_list_from_another_account_cannot_satisfy_registry_verification(self):
        with tempfile.TemporaryDirectory() as folder:
            control = Path(folder)
            (control / "verified-source.json").write_text(json.dumps({"owner": "other", "devices": ["source"], "duplicates": False}))
            with self.assertRaises(RuntimeError):
                MODULE.registry_check(control, Mock(), "source", {"source"}, "owner")

    def test_matching_registry_without_uploaded_replacement_proof_is_not_acceptance(self):
        for stage in ("destination", "replay"):
            with self.subTest(stage=stage), tempfile.TemporaryDirectory() as folder:
                control = Path(folder)
                (control / f"verified-{stage}.json").write_text(json.dumps({
                    "owner": "owner", "devices": ["source", "replacement"], "duplicates": False,
                }))
                with self.assertRaisesRegex(RuntimeError, "attribution"):
                    MODULE.registry_check(control, Mock(), stage, {"source", "replacement"}, "owner")

    def test_server_attribution_must_match_each_pre_sync_baseline(self):
        proof = {"device": "replacement", "historicalCreatedAt": 1, "draftCreatedAt": 2, "offlineCreatedAt": 3}
        for field in proof:
            with self.subTest(field=field), tempfile.TemporaryDirectory() as folder:
                control = Path(folder)
                actual = {**proof, "encryptedCaptureRetained": True, "encryptedDraft": True, field: "wrong"}
                (control / "verified-destination.json").write_text(json.dumps({
                    "owner": "owner", "devices": ["source", "replacement"], "duplicates": False,
                    "transferProof": actual,
                }))
                with self.assertRaisesRegex(RuntimeError, "attribution"):
                    MODULE.registry_check(control, Mock(), "destination", {"source", "replacement"}, "owner", proof)

    def test_destination_is_online_before_normal_startup_sync_is_observed(self):
        events = []
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            control = output / "server-control"
            control.mkdir()
            (control / "fixture.json").write_text(json.dumps({"owner": "owner"}))
            source = {"owner": "owner", "device": "source", "fingerprint": "same",
                      "proof": {"historicalCreatedAt": 1, "draftCreatedAt": 2}}
            destination = {**source, "device": "replacement", "automaticSyncObserved": True,
                           "syncWorkIds": ["owned-work"], "proof": {**source["proof"], "offlineCreatedAt": 3}}
            def transfer(*args, **kwargs):
                events.append("native-transfer")
                (args[4] / "source-fixture.json").write_text(json.dumps(source))
            def automatic_media(serial, control, process, media_output, wait_for, *, failure_diagnostics):
                events.append("automatic-media")
                self.assertTrue(callable(failure_diagnostics))
                failure_diagnostics(serial, media_output / "metered-recover.log", RuntimeError("fixture failure"))
                return {"automaticOriginalMediaRecovery": True}
            options = SimpleNamespace(source="emulator-5578", destination="emulator-5580", output=output,
                                      app_apk=Path("app.apk"), test_apk=Path("test.apk"))
            with patch.object(MODULE, "wait_for"), patch.object(MODULE, "checked_emulator"), \
                    patch.object(MODULE, "private_fixture"), \
                    patch.object(MODULE, "capture_failure_diagnostics") as diagnostics, \
                    patch.object(MODULE, "current_draft_probe", create=True, side_effect=lambda serial, stage, *args: events.append("current-draft-" + stage)), \
                    patch.object(MODULE, "retained_draft_probe", side_effect=lambda serial, stage, *args: events.append("editor-draft-" + stage)), patch.object(MODULE, "registry_check"), \
                    patch.object(MODULE, "receipt", side_effect=[source, destination, destination]), \
                    patch.object(MODULE, "probe", side_effect=lambda serial, stage, *args: events.append(stage)), \
                    patch.dict(MODULE.AUTOMATIC_MEDIA, run=automatic_media), \
                    patch.dict(MODULE.REAUTHENTICATION, run=lambda *args: events.append("media-reauthentication") or {"realPasskeyRepositoryResumesSameMediaOperation": True}), \
                    patch.dict(MODULE.H, prepare=Mock(), probe=Mock(),
                               adb=lambda serial, *args: events.append((serial, args)) or b"", transfer=transfer,
                               set_offline=lambda serial, offline: events.append((serial, offline))):
                MODULE.run_acceptance(options, control, Mock(wait=Mock(return_value=0)))
            diagnostics.assert_called_once_with(options.destination, output / "failure-diagnostics" / "metered-recover")
            self.assertEqual(["current-draft-prepare", "current-draft-quarantine", "current-draft-complete", "current-draft-reopened", "current-draft-base-prepare", "current-draft-base-complete"],
                             [event for event in events if isinstance(event, str) and event.startswith("current-draft-")])
            self.assertLess(events.index("current-draft-prepare"), events.index("current-draft-quarantine"))
            self.assertLess(events.index("current-draft-complete"), events.index("editor-draft-verify"))
            self.assertLess(events.index("editor-draft-verify"), events.index("current-draft-reopened"))
            self.assertLess(events.index("editor-draft-seed"), events.index("source-enroll"))
            self.assertLess(events.index("source-enroll"), events.index("editor-draft-upload"))
            self.assertLess(events.index("editor-draft-upload"), events.index("native-transfer"))
            self.assertLess(events.index("replay"), events.index("editor-draft-verify"))
            self.assertLess(events.index("automatic-media"), events.index("editor-draft-verify"))
            self.assertLess(events.index("automatic-media"), events.index("media-reauthentication"))
            self.assertLess(events.index("media-reauthentication"), events.index("current-draft-prepare"))
            self.assertLess(events.index("editor-draft-verify"), max(i for i, event in enumerate(events) if event == "cleanup"))
            self.assertIn((options.destination, False), events)
            self.assertIn((options.destination, True), events)
            self.assertLess(events.index((options.destination, True)), events.index("destination-edit"))
            self.assertLess(events.index("destination-edit"), events.index((options.destination, False)))
            self.assertLess(events.index((options.destination, False)), events.index("destination-enroll"))
            reconnect = (options.destination, ("shell", "cmd", "wifi", "connect-network", "AndroidWifi", "open"))
            connections = [i for i, event in enumerate(events) if event == reconnect]
            self.assertEqual(3, len(connections))
            for connection, stage in zip(connections, ("destination-enroll", "current-draft-complete", "current-draft-base-complete")):
                self.assertEqual((options.destination, False), events[connection - 1])
                self.assertLess(connection, events.index(stage))
            self.assertLess(events.index("replay"), events.index("automatic-media"))
            self.assertLess(events.index("automatic-media"), max(i for i, event in enumerate(events) if event == "cleanup"))

    def test_a_remote_database_disguised_by_userinfo_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "environment.json"
            path.write_text(json.dumps({
                "DATABASE_URL": "jdbc:postgresql://127.0.0.1:5432@outside.example/logdate_restore",
                "DATABASE_USER": "fixture", "DATABASE_PASSWORD": "private",
                "SERVER_ENCRYPTION_KEY": "private", "LOGDATE_BLOB_STORAGE_DIR": folder,
            }))
            path.chmod(0o600)
            with self.assertRaisesRegex(RuntimeError, "local PostgreSQL"):
                MODULE.fixture_environment(path)

    def test_control_document_is_invisible_until_all_bytes_are_written(self):
        with tempfile.TemporaryDirectory() as folder:
            target = Path(folder) / "expected-source.json"
            early_visibility = []
            real_writer = MODULE.H["private_file"]
            def slow_writer(path, content):
                real_writer(path, b"")
                early_visibility.append(target.exists())
                path.write_bytes(content)
            with patch.dict(MODULE.H, private_file=slow_writer):
                MODULE.publish_control(target, b'["source"]')
            self.assertEqual([False], early_visibility)
            self.assertEqual(["source"], json.loads(target.read_bytes()))

    def test_radio_cleanup_attempts_both_targets_after_one_failure(self):
        restore = Mock(side_effect=[RuntimeError("first radio unavailable"), None])
        radios = {"emulator-5556": (False, b"1"), "emulator-5558": (False, b"1")}
        with patch.object(MODULE, "checked_emulator"), patch.dict(MODULE.H, restore_radio_state=restore):
            with self.assertRaises(RuntimeError):
                MODULE.cleanup(None, radios)
        self.assertEqual(["emulator-5556", "emulator-5558"], [call.args[0] for call in restore.call_args_list])

    def test_fixture_permissions_are_checked_before_reading_credentials(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "environment.json"
            path.write_text("private")
            path.chmod(0o644)
            with self.assertRaisesRegex(RuntimeError, "private bounded JSON"):
                MODULE.fixture_environment(path)


if __name__ == "__main__":
    unittest.main()
