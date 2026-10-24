#!/usr/bin/env python3
"""Physical serials must be rejected before the native acceptance harness calls adb."""

import argparse
import importlib.util
import io
import json
import os
import runpy
from pathlib import Path
import subprocess
import stat
import tarfile
import tempfile
import textwrap
import unittest
from unittest.mock import patch


ROOT = Path(__file__).resolve().parents[3]
SPEC = importlib.util.spec_from_file_location("native_acceptance", ROOT / "tests/e2e/native-backup-acceptance.py")
HARNESS = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(HARNESS)
D2D_SPEC = importlib.util.spec_from_file_location(
    "native_d2d_acceptance",
    ROOT / "tests/e2e/native-d2d-acceptance.py",
)
D2D = importlib.util.module_from_spec(D2D_SPEC)
D2D_SPEC.loader.exec_module(D2D)


class NativeAcceptanceSafetyTest(unittest.TestCase):
    def test_d2d_transport_failure_retains_private_cause_and_original_error(self):
        for diagnostics_fail in (False, True):
            with self.subTest(diagnostics_fail=diagnostics_fail), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                app = root / "app.apk"
                probe = root / "probe.apk"
                app.touch()
                probe.touch()
                output = root / "result"
                def command(serial, *args, **kwargs):
                    if args[:3] == ("shell", "bmgr", "backupnow"):
                        return b"Package @pm@ with result: Transport error\n"
                    if args[:2] == ("shell", "logcat"):
                        if diagnostics_fail:
                            raise subprocess.TimeoutExpired("owned-adb", 30)
                        return b"Backup: transport initialization failed\n"
                    return b"null"
                with patch.object(D2D, "verify_emulator"), patch.object(D2D, "require_empty_source"), patch.object(
                    D2D, "selected_transport", return_value="original"
                ), patch.object(D2D, "select_d2d_transport"), patch.object(D2D, "source_fixture", return_value=b"{}"), patch.object(
                    D2D.HARNESS, "ensure_emulator_setup_complete"
                ), patch.object(D2D.HARNESS, "probe"), patch.object(D2D.HARNESS, "adb", side_effect=command):
                    with self.assertRaisesRegex(RuntimeError, "did not complete the native backup"):
                        D2D.exercise_d2d("emulator-5582", app, probe, output)
                diagnostic = output / "d2d-backup-transport.log"
                self.assertTrue(diagnostic.is_file(), "Transport failure must retain its cause")
                self.assertEqual(0o600, stat.S_IMODE(diagnostic.stat().st_mode))
                expected = b"TimeoutExpired" if diagnostics_fail else b"transport initialization failed"
                self.assertIn(expected, diagnostic.read_bytes())

    def test_retained_key_acceptance_rejects_skips_despite_a_success_summary(self):
        post = runpy.run_path(str(ROOT / "tests/e2e/native-post-transfer-acceptance.py"))
        for status, ending in (("0", ""), ("-3", ""), ("-4", ""), ("-3", "\r"), ("-4", "\r")):
            result = f"INSTRUMENTATION_STATUS_CODE: {status}{ending}\nOK (8 tests)\n".encode()
            with self.subTest(status=status, ending=ending), tempfile.TemporaryDirectory() as temporary, patch.dict(
                post["NATIVE"], {"adb": lambda *args: result}
            ):
                output = Path(temporary) / "retained-keys.log"
                if status == "0":
                    post["instrument"]("emulator-5556", "fixture.Keys", 8, output)
                else:
                    with self.assertRaisesRegex(RuntimeError, "did not pass every required case"):
                        post["instrument"]("emulator-5556", "fixture.Keys", 8, output)
                self.assertEqual(result, output.read_bytes())

    def test_native_ci_retains_all_scenarios_without_manual_emulator_lifecycle(self):
        workflow = (ROOT / ".github/workflows/native-restore-acceptance.yml").read_text()
        for forbidden in ("avdmanager create", "emulator -avd", "adb -s", "emu kill", "pm clear", "adb uninstall"):
            self.assertNotIn(forbidden, workflow)
        for scenario in ("partial", "publication", "contracts", "missing-keys", "credentials-only", "privacy", "work-restart", "d2d", "enrollment"):
            self.assertIn(f"{scenario})", workflow)
        self.assertIn("needs: native-transfer-scenarios", workflow)
        self.assertIn('test "$RESULT" = success', workflow)

    def test_d2d_acceptance_refuses_non_emulators_before_device_access(self):
        for serial in ("38191FDJG00FNY", "192.0.2.1:5555", "adb-phone._tcp"):
            with self.subTest(serial=serial), patch.object(D2D.HARNESS, "adb") as adb:
                with self.assertRaisesRegex(RuntimeError, "explicit emulator"):
                    D2D.verify_emulator(serial)
                adb.assert_not_called()

    def test_d2d_restore_set_parser_accepts_decimal_and_hex_tokens(self):
        for token in ("1", "0x29f", "3e0c27e54db33477"):
            with self.subTest(token=token):
                self.assertEqual(token, D2D.restore_set_token(f"Available restore sets:\n  {token} : D2D Restore Set"))

    def test_d2d_restore_set_parser_skips_unrelated_cloud_snapshots(self):
        self.assertEqual("abc", D2D.restore_set_token("  123 : Other phone\n  abc : D2D\n"))
        with self.assertRaisesRegex(RuntimeError, "D2D restore set"):
            D2D.restore_set_token("  123 : Other phone\n")

    def test_d2d_restore_switches_to_gms_as_required_by_the_android_guide(self):
        with patch.object(D2D.HARNESS, "adb", side_effect=[b"selected", b"  * " + D2D.GMS_BACKUP_TRANSPORT.encode()]) as adb:
            D2D.select_d2d_restore_transport("emulator-5582")
        self.assertEqual(("shell", "bmgr", "transport", D2D.GMS_BACKUP_TRANSPORT), adb.call_args_list[0].args[1:])

    def test_d2d_selected_transport_accepts_androids_indented_marker(self):
        for indent in ("", "  ", "\t"):
            with self.subTest(indent=indent), patch.object(
                D2D.HARNESS, "adb", return_value=f"    other\n{indent}* {D2D.D2D_TRANSPORT}\n".encode()
            ):
                self.assertEqual(D2D.D2D_TRANSPORT, D2D.selected_transport("emulator-5582"))

    def test_d2d_fixture_is_read_with_the_debug_app_identity(self):
        with patch.object(D2D.HARNESS, "adb", return_value=b"{}") as adb:
            self.assertEqual(b"{}", D2D.source_fixture("emulator-5582"))
        adb.assert_called_once_with(
            "emulator-5582", "exec-out", "run-as", D2D.HARNESS.PACKAGE, "cat", "no_backup/native-backup-fixture.json"
        )

    def test_d2d_refuses_retained_data_before_seeding(self):
        with patch.object(D2D.HARNESS, "adb", return_value=f"package:{D2D.HARNESS.PACKAGE}\n".encode()), patch.object(
            D2D.HARNESS, "package_user_files", return_value=[b"./databases/logdate"]
        ):
            with self.assertRaisesRegex(RuntimeError, "retained user data"):
                D2D.require_empty_source("emulator-5582")

    def test_d2d_allows_an_absent_package_without_reading_private_storage(self):
        with patch.object(D2D.HARNESS, "adb", return_value=f"package:{D2D.HARNESS.PACKAGE}.test\n".encode()), patch.object(
            D2D.HARNESS, "package_user_files"
        ) as files:
            D2D.require_empty_source("emulator-5582")
            files.assert_not_called()

    def test_d2d_allows_a_preinstalled_empty_app(self):
        with patch.object(D2D.HARNESS, "adb", return_value=f"package:{D2D.HARNESS.PACKAGE}\n".encode()), patch.object(
            D2D.HARNESS, "package_user_files", return_value=[b"./cache/test"]
        ):
            D2D.require_empty_source("emulator-5582")

    def test_d2d_transport_requires_registration_selection_and_initialization(self):
        transport = D2D.D2D_TRANSPORT.encode()
        responses = [b"  " + transport, b"selected", b"initialized", b"* " + transport]
        with patch.object(D2D.HARNESS, "adb", side_effect=responses) as adb, patch.object(D2D.HARNESS.time, "sleep"):
            D2D.select_d2d_transport("emulator-5582", attempts=1)

        calls = [call.args[1:] for call in adb.call_args_list]
        self.assertEqual(("shell", "bmgr", "transport", D2D.D2D_TRANSPORT), calls[1])
        self.assertEqual(("shell", "bmgr", "init", D2D.D2D_TRANSPORT), calls[2])

    def test_d2d_transport_missing_from_emulator_fails_before_app_install(self):
        with patch.object(D2D.HARNESS, "adb", return_value=b"* other") as adb, patch.object(
            D2D.HARNESS.time,
            "sleep",
        ):
            with self.assertRaisesRegex(RuntimeError, "Google D2D transport"):
                D2D.select_d2d_transport("emulator-5582", attempts=1)

        self.assertEqual(1, adb.call_count)

    def test_d2d_cleanup_restores_existing_transport_mode_and_disabled_backup(self):
        with patch.object(D2D.HARNESS, "adb") as adb:
            D2D.restore_device_settings("emulator-5582", "old.transport", b"0", False)

        commands = [call.args[1:] for call in adb.call_args_list]
        self.assertIn(("shell", "settings", "put", "secure", D2D.D2D_TEST_MODE, "0"), commands)
        self.assertEqual(("shell", "bmgr", "transport", "old.transport"), commands[1])
        self.assertIn(("shell", "bmgr", "enable", "false"), commands)

    def test_d2d_cleanup_selects_google_backup_when_emulator_had_no_transport(self):
        with patch.object(D2D.HARNESS, "adb") as adb:
            D2D.restore_device_settings("emulator-5582", None, b"null", False)

        commands = [call.args[1:] for call in adb.call_args_list]
        self.assertIn(
            ("shell", "bmgr", "transport", D2D.GMS_BACKUP_TRANSPORT),
            commands,
        )
        self.assertIn(("shell", "bmgr", "enable", "false"), commands)

    def test_native_restore_workflow_runs_separate_gms_d2d_test_mode(self):
        workflow = (ROOT / ".github/workflows/native-restore-acceptance.yml").read_text()
        d2d_harness = (ROOT / "tests/e2e/native-d2d-acceptance.py").read_text()
        self.assertIn("system-images;android-34;google_apis_playstore;x86_64", workflow)
        self.assertIn("--mode d2d", workflow)
        controller = (ROOT / "tests/e2e/managed-native-restore-acceptance.py").read_text()
        self.assertIn('"d2d": "native-d2d-acceptance.py"', controller)
        self.assertIn("single-device D2D test-mode simulation", d2d_harness)
        self.assertIn("does not prove setup-wizard transfer", d2d_harness)
        self.assertIn("https://developer.android.com/identity/data/testingbackup", d2d_harness)
        self.assertIn("backup_enable_d2d_test_mode", d2d_harness)
        self.assertIn(D2D.D2D_TRANSPORT, d2d_harness)
        self.assertIn('"native-restore-status"', d2d_harness)
        self.assertIn('"verify-in-place"', d2d_harness)
        probe = (ROOT / "app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/NativeBackupTransferProbeTest.kt").read_text()
        self.assertIn("PostRestoreType.CONTENT_RESTORED", probe)
        self.assertIn("native-transfer-api-36-1:", workflow)
        self.assertIn("platforms;android-36.1", workflow)
        self.assertIn(
            "NativeBackgroundCredentialCallbackTest",
            workflow,
        )

    def test_d2d_acceptance_never_uses_package_uninstall_or_clear(self):
        source = (ROOT / "tests/e2e/native-d2d-acceptance.py").read_text()
        self.assertNotIn("pm uninstall", source)
        self.assertNotIn("pm clear", source)

    def test_native_backup_stops_when_source_activity_launch_fails(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "package_file_snapshot",
        ) as snapshot, patch.object(HARNESS, "adb", return_value=b"Error: Activity class does not exist"):
            with self.assertRaisesRegex(RuntimeError, "source app did not start before native backup"):
                HARNESS.backup_source("emulator-5576", Path(temporary) / "backup.log")

        snapshot.assert_not_called()

    def test_native_backup_waits_for_timeline_after_android_start_timeout(self):
        retained = {"databases/logdate": {"sha256": "journal"}}
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "package_file_snapshot",
            return_value=retained,
        ), patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"Status: timeout",
                b"Backup Manager now enabled",
                b"Backup Manager currently enabled",
                f"Package {HARNESS.PACKAGE} with result: Success".encode(),
            ],
        ) as adb, patch.object(HARNESS, "wait_for_timeline") as wait_for_timeline:
            output = Path(temporary) / "native-backup.log"
            HARNESS.backup_source("emulator-5576", output)
            wait_for_timeline.assert_called_once_with("emulator-5576", Path(temporary), timeout_seconds=120, prefix="source")
            self.assertEqual(
                b"Status: timeout",
                (Path(temporary) / "source-launch-before-native-backup.log").read_bytes(),
            )
            self.assertIn(
                ("shell", "bmgr", "backupnow", "@pm@", HARNESS.PACKAGE, "--non-incremental"),
                [arguments.args[1:] for arguments in adb.call_args_list],
            )

    def test_api_36_1_uses_managed_lifecycle_and_requires_the_exact_minor_sdk(self):
        workflow = (ROOT / ".github/workflows/native-restore-acceptance.yml").read_text()
        scenario = workflow.split("  native-transfer-api-36-1:", 1)[1]
        self.assertNotIn("avdmanager create", scenario)
        self.assertNotIn("emulator -avd", scenario)
        self.assertNotIn("adb -s", scenario)
        self.assertIn("managed-modern-callback-acceptance.sh", scenario)
        runner = (ROOT / "tests/e2e/managed-modern-callback-acceptance.sh").read_text()
        self.assertIn(":tools:native-restore-driver:restoreCallbackApi36_1DebugAndroidTest", runner)
        driver = (ROOT / "tools/native-restore-driver/src/androidTest/kotlin/app/logdate/tools/restoredriver/NativeModernRestoreCallbackTest.kt").read_text()
        self.assertIn("nativeRestoreRequiredSdkFull 3600001", driver)
        self.assertIn("--no-hidden-api-checks", driver)
        self.assertIn("requireAttestedApks()", driver)
        self.assertIn("native-modern-callback-driver-cases.json", runner)
        self.assertIn("native-modern-callback-cases.json", runner)
        self.assertIn("--managed-results", runner)
        self.assertNotIn("am instrument", runner)

    def test_api_36_restore_probe_fails_if_full_restore_callback_is_missing(self):
        probe = (
            ROOT
            / "app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/NativeBackgroundCredentialCallbackTest.kt"
        ).read_text()
        self.assertIn("if (!supportsFullRestoreInput()) return", probe)
        self.assertIn("Build.VERSION.SDK_INT >= 36 && Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1", probe)
        self.assertIn("assertNotNull(", probe)
        self.assertIn("android.app.backup.FullRestoreDataInput", probe)

    def test_background_restore_callback_checks_do_not_launch_an_activity(self):
        workflow = (ROOT / ".github/workflows/native-restore-acceptance.yml").read_text()
        self.assertIn("--mode work-restart", workflow)
        post = (ROOT / "tests/e2e/native-post-transfer-acceptance.py").read_text()
        self.assertNotIn("am start", post)
        contract = json.loads((ROOT / "tests/e2e/native-modern-callback-cases.json").read_text())
        for classname in contract:
            callback_test = (
                ROOT / "app/android-main/src/androidTest/kotlin" / (classname.replace(".", "/") + ".kt")
            ).read_text()
            self.assertIn('getDir("native-restore-probe", Context.MODE_PRIVATE)', callback_test)
            self.assertNotIn("application.noBackupFilesDir", callback_test)

    def test_post_transfer_callbacks_execute_and_report_the_supplied_contract(self):
        post = runpy.run_path(str(ROOT / "tests/e2e/native-post-transfer-acceptance.py"))
        saved = json.loads((ROOT / "tests/e2e/native-modern-callback-cases.json").read_text())
        changed = {"fixture.Callbacks": ["first", "second"], "fixture.MoreCallbacks": ["third"]}
        for contract in (saved, changed):
            with self.subTest(contract=contract), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                (root / "native-modern-callback-cases.json").write_text(json.dumps(contract))
                calls = []
                commands = []
                captures = []
                def retain_lock_evidence(*args):
                    captures.append(args)
                def instrument(*args, **kwargs):
                    calls.append(args)
                def adb(*args, **kwargs):
                    commands.append(args)
                    return b"1"
                with patch.dict(post["run"].__globals__, {
                    "instrument": instrument, "retain_lock_evidence": retain_lock_evidence,
                    "__file__": str(root / "native-post-transfer-acceptance.py"),
                }), patch.dict(post["NATIVE"], {"adb": adb}):
                    post["run"]("emulator-5556", root / "output")
                self.assertEqual([("emulator-5556", root / "output/restored-app-lock")], captures)
                expected_count = sum(len(methods) for methods in contract.values())
                callback_calls = [call for call in calls if call[3].name == "callbacks.log"]
                self.assertEqual([
                    ("emulator-5556", ",".join(contract), expected_count, root / "output/callbacks.log"),
                ], callback_calls)
                receipt = json.loads((root / "output/acceptance.json").read_text())
                self.assertEqual(expected_count, receipt["callbackCases"])
                self.assertFalse(receipt["modernCallbackAccepted"])
                self.assertFalse(receipt["googleProviderAccepted"])
                self.assertFalse(any(command[1:4] == ("shell", "am", "start") for command in commands))

    def test_native_restore_ci_exercises_the_real_cloud_quota_rejection_callback(self):
        workflow = (ROOT / ".github/workflows/native-restore-acceptance.yml").read_text()
        self.assertIn("--exercise-quota", workflow)

    def test_native_backup_integrity_probe_preserves_session_owner(self):
        probe = (
            ROOT
            / "app/android-main/src/androidTest/kotlin/app/logdate/client/e2e/NativeBackupTransferProbeTest.kt"
        ).read_text()
        self.assertIn('assertEquals(arguments.getString("sourceOwner"), session.accountId)', probe)
        self.assertIn('arguments.getString("sourceHasSession") == "true"', probe)
        self.assertIn("assertFalse(\n                        sessions.hasValidSession()", probe)

    def test_native_backup_verifies_source_keys_for_every_transfer(self):
        harness = (ROOT / "tests/e2e/native-backup-acceptance.py").read_text()
        self.assertIn('"source-backup-integrity"', harness)
        self.assertIn("verify_after_backup=validate_source_integrity", harness)
        self.assertIn("source_has_session=exercise_data_only", harness)

    def test_native_restore_ci_checks_a_populated_signed_out_destination(self):
        workflow = (ROOT / ".github/workflows/native-restore-acceptance.yml").read_text()
        self.assertIn("--mode publication", workflow)
        self.assertIn("managed-native-restore-acceptance.py", workflow)
        publication = (ROOT / "tests/e2e/native-publication-acceptance.py").read_text()
        self.assertIn("exercise_data_only=True", publication)

    def test_native_restore_ci_runs_missing_media_and_network_recovery(self):
        workflow = (ROOT / ".github/workflows/native-restore-acceptance.yml").read_text()
        self.assertIn("--after-transfer missing-media", workflow)
        controller = (ROOT / "tests/e2e/managed-native-restore-acceptance.py").read_text()
        self.assertIn('"missing-media": "native-missing-media-acceptance.py"', controller)
        self.assertIn('"--synthetic-data"', controller)
        self.assertIn("ArchiveMediaRecoveryE2ETest.kt", workflow)
        self.assertIn("CloudArchiveWorkerFixture.kt", workflow)
        self.assertIn("app/compose-main/src/androidMain/", workflow)
        self.assertIn("client/**", workflow)
        self.assertIn("shared/model/**", workflow)

    @staticmethod
    def credentials_backup(*, omit=(), extra=()):
        prefix = f"apps/{HARNESS.PACKAGE}"
        files = {
            prefix + "/_manifest": b"android app backup manifest",
            prefix + "/f/db_recovery/passphrase_backup": b"database recovery key",
            prefix + "/f/identity_recovery/phrase_backup": b"identity recovery phrase",
            prefix + "/f/identity_recovery/media_key_backup": b"media recovery key",
            prefix + "/f/logdate.preferences_pb": HARNESS.length_delimited_field(
                1,
                HARNESS.length_delimited_field(1, b"identity.canonical_owner_id")
                + HARNESS.length_delimited_field(
                    2,
                    HARNESS.length_delimited_field(5, b"synthetic-owner"),
                ),
            ),
        }
        files.update(dict(extra))
        payload = io.BytesIO()
        with tarfile.open(fileobj=payload, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            for name, contents in files.items():
                if name in omit:
                    continue
                item = tarfile.TarInfo(name)
                item.size = len(contents)
                archive.addfile(item, io.BytesIO(contents))
        return payload.getvalue()

    def test_radio_cleanup_attempts_wifi_after_airplane_mode_failure(self):
        original = (False, b"1")
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[subprocess.CalledProcessError(1, "airplane"), b"wifi restored"],
        ) as adb, patch.object(HARNESS, "radio_state", return_value=original):
            HARNESS.restore_radio_state("emulator-5582", original)

        self.assertEqual(2, adb.call_count)
        self.assertEqual(("shell", "svc", "wifi", "enable"), adb.call_args_list[1].args[1:])

    def test_prepare_leaves_network_state_for_the_scenario_to_control(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[b"1", b"", b"", b"", b"0", b"0", b"", b"1", b"", b""],
        ) as adb, patch.object(
            HARNESS,
            "wait_for_package_manager",
        ), patch.object(HARNESS, "select_local_transport"):
            HARNESS.prepare("emulator-5582")

        self.assertIn(
            ("shell", "settings", "put", "secure", "user_setup_complete", "1"),
            [call.args[1:] for call in adb.call_args_list],
        )
        self.assertFalse(
            any(call.args[2:4] in (("cmd", "connectivity"), ("svc", "wifi")) for call in adb.call_args_list),
            "Transport preparation must not force a cold app launch to happen offline",
        )

    def test_resume_preparation_requires_an_existing_synthetic_package(self):
        with patch.object(HARNESS, "adb", side_effect=[b"1", b""]) as adb, patch.object(
            HARNESS,
            "wait_for_package_manager",
        ):
            with self.assertRaisesRegex(RuntimeError, "resume requires the synthetic source"):
                HARNESS.prepare("emulator-5582", allow_existing=True)

        self.assertEqual(2, adb.call_count)

    def test_snapshot_source_reads_the_existing_fixture_without_launching_the_app(self):
        marker = f"/data/user/0/{HARNESS.PACKAGE}/no_backup/native-backup-fixture.json"

        def respond_to_adb(_serial, *args, **_kwargs):
            if args == ("shell", "run-as", HARNESS.PACKAGE, "find", ".", "-type", "f"):
                return b"\n".join(
                    (
                        b"./files/db_recovery/passphrase_backup",
                        b"./files/identity_recovery/phrase_backup",
                        b"./files/identity_recovery/media_key_backup",
                    )
                )
            if args == ("exec-out", "cat", marker):
                fixture = {
                    "owner": "owner",
                    "device": "device",
                    "fingerprint": "fingerprint",
                    "mediaNonce": "nonce",
                    "mediaCiphertext": "ciphertext",
                }
                return HARNESS.json.dumps(fixture).encode()
            return b""

        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "adb",
            side_effect=respond_to_adb,
        ) as adb, patch.object(
            HARNESS,
            "probe",
        ) as probe:
            output = Path(temporary) / "output"
            output.mkdir()
            HARNESS.prepare_source(
                "emulator-5576",
                Path(temporary) / "app.apk",
                Path(temporary) / "test.apk",
                output,
                "phrase",
                seed_source=False,
                snapshot_source=True,
            )
            self.assertEqual("owner", HARNESS.json.loads((output / "source-existing-fixture.json").read_text())["owner"])

        installs = [call.args[1:3] for call in adb.call_args_list if call.args[1:2] == ("install",)]
        self.assertEqual([("install", "-r"), ("install", "-r")], [item[:2] for item in installs])
        self.assertIn(("shell", "run-as", HARNESS.PACKAGE, "test", "-s", marker), [call.args[1:] for call in adb.call_args_list])
        self.assertIn(("exec-out", "cat", marker), [call.args[1:] for call in adb.call_args_list])
        self.assertIn(
            ("shell", "run-as", HARNESS.PACKAGE, "find", ".", "-type", "f"),
            [call.args[1:] for call in adb.call_args_list],
        )
        probe.assert_not_called()

    def test_snapshot_source_requires_portable_recovery_keys(self):
        marker = f"/data/user/0/{HARNESS.PACKAGE}/no_backup/native-backup-fixture.json"

        def respond_to_adb(_serial, *args, **_kwargs):
            if args == ("shell", "run-as", HARNESS.PACKAGE, "find", ".", "-type", "f"):
                return b"./files/db_recovery/passphrase_backup"
            if args == ("exec-out", "cat", marker):
                return HARNESS.json.dumps(
                    {"owner": "owner", "device": "device", "fingerprint": "fingerprint", "mediaCiphertext": "ciphertext"},
                ).encode()
            return b""

        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "adb",
            side_effect=respond_to_adb,
        ):
            output = Path(temporary) / "output"
            output.mkdir()
            with self.assertRaisesRegex(RuntimeError, "missing portable recovery keys"):
                HARNESS.prepare_source(
                    "emulator-5576",
                    Path(temporary) / "app.apk",
                    Path(temporary) / "test.apk",
                    output,
                    "phrase",
                    seed_source=False,
                    snapshot_source=True,
                )

    def test_probe_passes_fresh_media_nonce_with_transfer_metadata(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "adb",
            return_value=b"INSTRUMENTATION_STATUS_CODE: 0\n\nOK (1 test)\n",
        ) as adb:
            HARNESS.probe(
                "emulator-5576",
                "verify",
                Path(temporary) / "verify.log",
                metadata={"mediaNonce": "nonce", "owner": "owner", "device": "device", "fingerprint": "fingerprint", "mediaCiphertext": "ciphertext"},
            )

        arguments = adb.call_args.args[1:]
        self.assertIn(("-e", "sourceMediaNonce", "nonce"), list(zip(arguments, arguments[1:], arguments[2:])))

    def test_snapshot_rejects_changes_to_the_existing_journal(self):
        original = {
            "databases/logdate": {"sha256": "before"},
            "files/db_recovery/passphrase_backup": {"sha256": "key"},
            "databases/logdate-wal": {"sha256": "wal-before"},
            "files/media/objects/original.jpg": {"sha256": "media-before"},
            "files/logdate.preferences_pb": {"sha256": "settings-before"},
        }
        unchanged = {**original, "databases/logdate-wal": {"sha256": "wal-after"}}
        changed = {**unchanged, "databases/logdate": {"sha256": "after"}}
        changed_key = {**unchanged, "files/db_recovery/passphrase_backup": {"sha256": "changed-key"}}

        HARNESS.assert_snapshot_preserved(original, unchanged)
        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable data"):
            HARNESS.assert_snapshot_preserved(original, changed)
        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable data"):
            HARNESS.assert_snapshot_preserved(original, changed_key)
        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable data"):
            HARNESS.assert_snapshot_preserved(original, {**unchanged, "files/media/objects/original.jpg": {"sha256": "media-after"}})
        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable data"):
            HARNESS.assert_snapshot_preserved(original, {**unchanged, "files/logdate.preferences_pb": {"sha256": "settings-after"}})

    def test_snapshot_ignores_firebase_session_datastore_temporary_files_only(self):
        original = {
            "databases/logdate": {"sha256": "journal"},
            "files/datastore/firebaseSessions/sessionDataStore.data.tmp": {"sha256": "partial-write"},
            "files/datastore/firebaseSessions/sessionDataStore.data": {"sha256": "durable-session"},
        }
        after_temporary_write = {
            **original,
            "files/datastore/firebaseSessions/sessionDataStore.data.tmp": {"sha256": "new-partial-write"},
        }
        after_durable_session_change = {
            **original,
            "files/datastore/firebaseSessions/sessionDataStore.data": {"sha256": "changed-session"},
        }

        HARNESS.assert_snapshot_preserved(original, after_temporary_write)
        with self.assertRaisesRegex(RuntimeError, "snapshot changed durable data"):
            HARNESS.assert_snapshot_preserved(original, after_durable_session_change)

    def test_native_backup_checks_source_data_before_and_after_transport_backup(self):
        retained = {"databases/logdate": {"sha256": "journal"}, "files/media/original.jpg": {"sha256": "media"}}
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "package_file_snapshot",
            side_effect=[retained, retained],
        ) as snapshot, patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"Status: ok",
                b"Backup Manager now enabled",
                b"Backup Manager currently enabled",
                f"Package {HARNESS.PACKAGE} with result: Success".encode(),
            ],
        ) as adb:
            result, before = HARNESS.backup_source("emulator-5576", Path(temporary) / "backup.log")
            self.assertIn(b"with result: Success", result)
            self.assertEqual(retained, before)
            self.assertEqual(2, snapshot.call_count)
            adb_calls = [arguments.args[1:] for arguments in adb.call_args_list]
            self.assertLess(
                adb_calls.index(("shell", "am", "start", "-W", "-n", f"{HARNESS.PACKAGE}/app.logdate.client.MainActivity")),
                adb_calls.index(("shell", "bmgr", "backupnow", "@pm@", HARNESS.PACKAGE, "--non-incremental")),
            )
            self.assertEqual(
                HARNESS.json.loads((Path(temporary) / "source-files-before-native-backup.json").read_text()),
                retained,
            )
            self.assertEqual(
                HARNESS.json.loads((Path(temporary) / "source-files-after-native-backup.json").read_text()),
                retained,
            )
            self.assertIn(
                ("shell", "bmgr", "backupnow", "@pm@", HARNESS.PACKAGE, "--non-incremental"),
                [call.args[1:] for call in adb.call_args_list],
            )

    def test_native_backup_fails_if_source_journal_changes_during_backup(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "package_file_snapshot",
            side_effect=[{"databases/logdate": {"sha256": "before"}}, {"databases/logdate": {"sha256": "after"}}],
        ), patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"Status: ok",
                b"Backup Manager now enabled",
                b"Backup Manager currently enabled",
                f"Package {HARNESS.PACKAGE} with result: Success".encode(),
            ],
        ):
            with self.assertRaisesRegex(RuntimeError, "snapshot changed durable data"):
                HARNESS.backup_source("emulator-5576", Path(temporary) / "backup.log")

    def test_native_backup_validates_identity_before_allowing_secure_storage_rewrite(self):
        before = {
            "databases/logdate": {"sha256": "journal"},
            "shared_prefs/logdate_secure_storage.xml": {"sha256": "before"},
        }
        after = {**before, "shared_prefs/logdate_secure_storage.xml": {"sha256": "after"}}
        verified = []

        def verify_after_backup():
            self.assertEqual(
                ("emulator-5576", "shell", "am", "force-stop", HARNESS.PACKAGE),
                adb.call_args_list[-1].args,
            )
            verified.append(True)

        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "package_file_snapshot",
            side_effect=[before, after],
        ), patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"Status: ok",
                b"Backup Manager now enabled",
                b"Backup Manager currently enabled",
                f"Package {HARNESS.PACKAGE} with result: Success".encode(),
                b"",
            ],
        ) as adb:
            HARNESS.backup_source(
                "emulator-5576",
                Path(temporary) / "backup.log",
                verify_after_backup=verify_after_backup,
            )

        self.assertEqual([True], verified)

    def test_native_backup_still_rejects_other_data_changes_after_secure_validation(self):
        before = {
            "databases/logdate": {"sha256": "journal-before"},
            "shared_prefs/logdate_secure_storage.xml": {"sha256": "before"},
            "files/media/original.jpg": {"sha256": "media-before"},
        }
        after = {
            "databases/logdate": {"sha256": "journal-after"},
            "shared_prefs/logdate_secure_storage.xml": {"sha256": "after"},
            "files/media/original.jpg": {"sha256": "media-after"},
        }
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "package_file_snapshot",
            side_effect=[before, after],
        ), patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"Status: ok",
                b"Backup Manager now enabled",
                b"Backup Manager currently enabled",
                f"Package {HARNESS.PACKAGE} with result: Success".encode(),
                b"",
            ],
        ) as adb:
            with self.assertRaisesRegex(RuntimeError, "files/media/original.jpg"):
                HARNESS.backup_source(
                    "emulator-5576",
                    Path(temporary) / "backup.log",
                    verify_after_backup=lambda: None,
                )
            adb.assert_any_call(
                "emulator-5576",
                "shell",
                "am",
                "force-stop",
                HARNESS.PACKAGE,
            )

    def test_native_backup_failure_records_platform_eligibility_state(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "package_file_snapshot",
            return_value={"databases/logdate": {"sha256": "journal"}},
        ), patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"Status: ok",
                b"Backup Manager now enabled",
                b"Backup Manager currently enabled",
                f"Package {HARNESS.PACKAGE} with result: Backup is not allowed".encode(),
                b"enabled=false setupComplete=true",
            ],
        ) as adb:
            with self.assertRaisesRegex(RuntimeError, "Android did not complete the native backup"):
                HARNESS.backup_source("emulator-5576", Path(temporary) / "backup.log")

            diagnostic = Path(temporary) / "backup-platform-diagnostics.log"
            self.assertIn(b"enabled=false setupComplete=true", diagnostic.read_bytes())
            self.assertIn(
                ("shell", "dumpsys", "backup"),
                [arguments.args[1:] for arguments in adb.call_args_list],
            )

    def test_native_backup_reenables_and_verifies_the_platform_service_before_request(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[b"Backup Manager now enabled", b"Backup Manager currently enabled"],
        ) as adb:
            HARNESS.enable_backup("emulator-5576")

        self.assertEqual(
            [
                ("shell", "bmgr", "enable", "true"),
                ("shell", "bmgr", "enabled"),
            ],
            [call.args[1:] for call in adb.call_args_list],
        )

    def test_preference_snapshot_ignores_only_runtime_search_index_generation(self):
        def field(number, value):
            tag = bytes([(number << 3) | 2])
            return tag + bytes([len(value)]) + value

        def preference(name, value):
            value_message = b"\x20\x01" if name == "android_platform_search_index_generation" else field(5, value.encode())
            return field(1, name.encode()) + field(2, value_message)

        before = field(1, preference("theme", "dark")) + field(1, preference("android_platform_search_index_generation", "1"))
        after = field(1, preference("android_platform_search_index_generation", "2")) + field(1, preference("theme", "dark"))
        changed_setting = field(1, preference("android_platform_search_index_generation", "2")) + field(1, preference("theme", "light"))

        self.assertEqual(HARNESS.canonical_preferences(before), HARNESS.canonical_preferences(after))
        self.assertNotEqual(HARNESS.canonical_preferences(before), HARNESS.canonical_preferences(changed_setting))

    def test_preference_snapshot_rejects_malformed_datastore_file(self):
        with self.assertRaisesRegex(ValueError, "invalid DataStore preferences"):
            HARNESS.canonical_preferences(b"\x0a\x80")

    def test_preference_snapshot_rejects_duplicate_keys_and_malformed_values(self):
        def field(number, value):
            return bytes([(number << 3) | 2, len(value)]) + value

        duplicate_key = field(1, b"android_platform_search_index_generation") + field(1, b"theme") + field(2, b"\x20\x01")
        malformed_value = field(1, b"theme") + field(2, b"\x2a\x05x")
        duplicate_value = field(1, b"theme") + field(2, b"\x2a\x01x") + field(2, b"\x2a\x01y")

        for entry in (duplicate_key, malformed_value, duplicate_value):
            with self.subTest(entry=entry), self.assertRaisesRegex(ValueError, "invalid DataStore preferences"):
                HARNESS.canonical_preferences(field(1, entry))

    def test_preinstalled_empty_emulator_can_contain_sdk_state_without_journal_data(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"1",
                f"package:{HARNESS.PACKAGE}".encode(),
                b"./shared_prefs/com.google.firebase.crashlytics.xml",
                b"",
                b"",
                b"0",
                b"0",
                b"",
                b"1",
                b"",
                b"",
            ],
        ) as adb, patch.object(
            HARNESS,
            "wait_for_package_manager",
        ), patch.object(HARNESS, "select_local_transport"):
            HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

        self.assertIn(("shell", "run-as", HARNESS.PACKAGE, "find", ".", "-type", "f"), [call.args[1:] for call in adb.call_args_list])
        self.assertIn(
            ("shell", "settings", "put", "secure", "user_setup_complete", "1"),
            [call.args[1:] for call in adb.call_args_list],
        )

    def test_preinstalled_emulator_with_journal_or_media_data_is_rejected(self):
        with patch.object(HARNESS, "adb", side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), b"./files/media/objects/retained.jpg"]) as adb, patch.object(
            HARNESS,
            "wait_for_package_manager",
        ):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

        self.assertEqual(3, adb.call_count)

    def test_preinstalled_emulator_with_retained_recovery_keys_is_rejected(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), b"./files/identity_recovery/phrase_backup"],
        ), patch.object(HARNESS, "wait_for_package_manager"):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_preinstalled_emulator_with_backup_managed_recovery_or_widget_data_is_rejected(self):
        retained_paths = (
            b"./files/archive_recovery/records/pending.json",
            b"./files/archive_recovery/sources/source.zip",
            b"./shared_prefs/logdate_widget_instances.xml",
        )
        for retained_path in retained_paths:
            with self.subTest(path=retained_path), patch.object(
                HARNESS,
                "adb",
                side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), retained_path],
            ), patch.object(HARNESS, "wait_for_package_manager"):
                with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                    HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_preinstalled_emulator_with_encrypted_identity_or_session_is_rejected(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"1",
                f"package:{HARNESS.PACKAGE}".encode(),
                b"./shared_prefs/logdate_secure_storage.xml",
            ],
        ), patch.object(HARNESS, "wait_for_package_manager"):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_credentials_only_fixture_keeps_owner_preferences_but_excludes_journal_and_media(self):
        prefix = f"apps/{HARNESS.PACKAGE}"
        payload = self.credentials_backup(
            extra=(
                (prefix + "/db/logdate", b"encrypted-journal"),
                (prefix + "/f/media/original.jpg", b"original-media"),
            ),
        )

        filtered = HARNESS.credentials_only_from_full_backup(payload)
        with HARNESS.tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as archive:
            retained = {item.name for item in archive.getmembers()}

        self.assertIn(prefix + "/_manifest", retained)
        self.assertIn(prefix + "/f/logdate.preferences_pb", retained)
        self.assertIn(prefix + "/f/db_recovery/passphrase_backup", retained)
        self.assertNotIn(prefix + "/db/logdate", retained)
        self.assertNotIn(prefix + "/f/media/original.jpg", retained)

    def test_preinstalled_emulator_with_retained_datastore_state_is_rejected(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), b"./files/datastore/user_preferences.preferences_pb"],
        ), patch.object(HARNESS, "wait_for_package_manager"):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_preinstalled_emulator_with_root_datastore_preferences_is_rejected(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[b"1", f"package:{HARNESS.PACKAGE}".encode(), b"./files/logdate.preferences_pb"],
        ), patch.object(HARNESS, "wait_for_package_manager"):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                HARNESS.prepare("emulator-5582", allow_preinstalled_empty=True)

    def test_probe_uses_legacy_zero_nonce_when_existing_fixture_has_no_nonce(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "adb",
            return_value=b"INSTRUMENTATION_STATUS_CODE: 0\n\nOK (1 test)\n",
        ) as adb:
            HARNESS.probe(
                "emulator-5576",
                "verify",
                Path(temporary) / "verify.log",
                metadata={"owner": "owner", "device": "device", "fingerprint": "fingerprint", "mediaCiphertext": "ciphertext"},
            )

        arguments = adb.call_args.args[1:]
        self.assertIn(("-e", "sourceMediaNonce", "AAAAAAAAAAAAAAAA"), list(zip(arguments, arguments[1:], arguments[2:])))

    def test_snapshot_existing_source_cannot_seed_archive_recovery(self):
        options = argparse.Namespace(
            exercise_cloud_backup=False,
            source="emulator-5576",
            destination="emulator-5584",
            exercise_missing_keys=False,
            exercise_missing_privacy=False,
            exercise_credentials_only=False,
            exercise_archive_recovery=True,
            exercise_partial_media=False,
            exercise_data_only=False,
            exercise_duplicate_restore=False,
            resume_seeded_source=False,
            seed_empty_source=False,
            snapshot_existing_source=True,
            identity="phrase",
        )
        with self.assertRaises(SystemExit):
            HARNESS.validate_options(options, argparse.ArgumentParser())

    def test_installed_package_without_user_data_is_an_empty_fixture(self):
        error = subprocess.CalledProcessError(
            1,
            ["adb", "shell", "run-as"],
            stderr=f"run-as: couldn't stat /data/user/0/{HARNESS.PACKAGE}: No such file or directory".encode(),
        )
        with patch.object(HARNESS, "adb", side_effect=error):
            self.assertEqual([], HARNESS.package_user_files("emulator-5582"))

    def test_package_manager_readiness_retries_until_the_android_service_is_available(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[subprocess.CalledProcessError(20, "pm"), b"package:android\n"],
        ) as adb, patch.object(HARNESS.time, "sleep") as sleep:
            HARNESS.wait_for_package_manager("emulator-5582", attempts=2)

        self.assertEqual(2, adb.call_count)
        sleep.assert_called_once_with(1)

    def test_package_manager_readiness_stops_after_its_bounded_attempts(self):
        with patch.object(HARNESS, "adb", side_effect=subprocess.CalledProcessError(20, "pm")) as adb, patch.object(
            HARNESS.time,
            "sleep",
        ):
            with self.assertRaisesRegex(RuntimeError, "Package Manager did not become ready"):
                HARNESS.wait_for_package_manager("emulator-5582", attempts=2)

        self.assertEqual(2, adb.call_count)

    def test_package_file_snapshot_hashes_all_retained_files_without_returning_contents(self):
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[b"encrypted database", b"wal", b"image", b"\x0a\x0b\x0a\x05theme\x12\x02\x18\x01"],
        ) as adb, patch.object(
            HARNESS,
            "package_user_files",
            return_value=[
                b"./databases/logdate",
                b"./databases/logdate-wal",
                b"./files/media/objects/original.jpg",
                b"./files/logdate.preferences_pb",
                b"./files/cache/ignored.tmp",
            ],
        ):
            snapshot = HARNESS.package_file_snapshot("emulator-5582")

        self.assertEqual({"bytes": len(b"encrypted database"), "sha256": HARNESS.hashlib.sha256(b"encrypted database").hexdigest()}, snapshot["databases/logdate"])
        self.assertIn("files/media/objects/original.jpg", snapshot)
        self.assertIn("files/logdate.preferences_pb", snapshot)
        self.assertNotIn("files/cache/ignored.tmp", snapshot)
        self.assertEqual(4, adb.call_count)

    def test_timeline_acceptance_requires_destination_controls_and_restored_content(self):
        hierarchy = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
        self.assertTrue(HARNESS.timeline_is_open(hierarchy))
        self.assertFalse(HARNESS.timeline_is_open(b'<hierarchy><node text="Search your log"/><node text="Timeline"/></hierarchy>'))

    def test_ui_poll_evidence_can_be_updated_without_changing_private_permissions(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "destination-ui.xml"
            HARNESS.private_file(path, b"first poll")
            HARNESS.overwrite_private_file(path, b"latest poll")

            self.assertEqual(b"latest poll", path.read_bytes())
            self.assertEqual(0o600, stat.S_IMODE(path.stat().st_mode))

    def test_timeline_wait_keeps_polling_until_restored_content_is_visible(self):
        loading = b'<hierarchy><node text="Search your log"/><node text="Timeline"/><node text="Loading"/></hierarchy>'
        timeline = (
            b'<hierarchy><node text="Search your log"/><node text="Timeline"/>'
            b'<node text="Text retained through native backup"/></hierarchy>'
        )
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "adb",
            side_effect=[
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                loading,
                b"loading image",
                b"UI hierarchy dumped to: /data/local/tmp/ui.xml",
                timeline,
                b"restored journal image",
            ],
        ) as adb, patch.object(HARNESS.time, "sleep"):
            HARNESS.wait_for_timeline("emulator-5582", Path(temporary), timeout_seconds=5)

            self.assertEqual(6, adb.call_count)
            self.assertEqual(timeline, (Path(temporary) / "destination-ui.xml").read_bytes())
            self.assertEqual(b"restored journal image", (Path(temporary) / "destination-journal.png").read_bytes())

    def test_archive_probe_uses_the_supplied_per_run_fixture_label(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            HARNESS,
            "adb",
            return_value=b"INSTRUMENTATION_STATUS_CODE: 0\n\nOK (1 test)\n",
        ) as adb:
            HARNESS.archive_probe(
                "emulator-5582",
                "seed",
                Path(temporary) / "archive-seed.log",
                fixture_label="transfer-unique-run",
            )

        command = adb.call_args.args[2]
        self.assertIn("-e archiveRecoveryFixtureLabel transfer-unique-run", command)

    def test_quota_probe_restores_transport_and_cleans_fixture_after_rejection_failure(self):
        calls = []

        def adb(serial, *arguments, **kwargs):
            calls.append(arguments)
            if arguments[:4] == ("shell", "settings", "get", "secure"):
                return b"is_encrypted=true,is_device_transfer=true"
            if arguments[:3] == ("shell", "bmgr", "backupnow"):
                return b"Backup finished with result: Success"
            return b""

        with tempfile.TemporaryDirectory() as temporary, patch.object(HARNESS, "adb", side_effect=adb), patch.object(
            HARNESS,
            "probe",
        ) as probe:
            with self.assertRaisesRegex(RuntimeError, "did not reject the oversized cloud backup"):
                HARNESS.verify_quota("emulator-5582", Path(temporary))

        self.assertIn(
            ("shell", "settings", "put", "secure", "backup_local_transport_parameters", "is_encrypted=true,is_device_transfer=true"),
            calls,
        )
        self.assertEqual(
            ["quota-cleanup", "quota", "quota-cleanup"],
            [call.args[1] for call in probe.call_args_list],
        )

    def test_missing_key_fixture_omits_only_portable_recovery_keys(self):
        full_backup = io.BytesIO()
        long_path = f"apps/{HARNESS.PACKAGE}/f/media/objects/sha256/" + "b" * 90 + "/item.jpg"
        with tarfile.open(fileobj=full_backup, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            for name, contents in [
                (f"apps/{HARNESS.PACKAGE}/f/native_journal_transfer/current/files/db_recovery/passphrase_backup", b"database key"),
                (f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup", b"identity key"),
                (f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup", b"media key"),
                (f"apps/{HARNESS.PACKAGE}/f/native_journal_transfer/current/database/logdate", b"encrypted journal"),
                (f"apps/{HARNESS.PACKAGE}/f/user_media/original", b"original media"),
                (f"apps/{HARNESS.PACKAGE}/f/media/objects/sha256/ab/" + "a" * 64 + ".jpg", b"long media path"),
                (long_path, b"long path"),
                ("apps/org.example.other/f/db_recovery/passphrase_backup", b"other app"),
            ]:
                item = tarfile.TarInfo(name)
                item.size = len(contents)
                archive.addfile(item, io.BytesIO(contents))

        payload = io.BytesIO()
        with tarfile.open(fileobj=payload, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            name = f"1/_full/{HARNESS.PACKAGE}"
            item = tarfile.TarInfo(name)
            item.size = len(full_backup.getvalue())
            archive.addfile(item, io.BytesIO(full_backup.getvalue()))
            transport_metadata = tarfile.TarInfo("1/_delta/@pm@/synthetic")
            transport_metadata.size = len(b"transport metadata")
            archive.addfile(transport_metadata, io.BytesIO(b"transport metadata"))

        filtered = HARNESS.without_portable_recovery_keys(payload.getvalue())
        self.assertNotIn(b"././@PaxHeader", filtered, "The native Android restore reader must receive legacy tar headers")
        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as transport:
            self.assertFalse(any(item.type in (tarfile.XHDTYPE, tarfile.XGLTYPE) for item in transport.getmembers()))
            self.assertEqual(b"transport metadata", transport.extractfile("1/_delta/@pm@/synthetic").read())
            nested = transport.extractfile(f"1/_full/{HARNESS.PACKAGE}").read()
        with tarfile.open(fileobj=io.BytesIO(nested), mode="r:") as archive:
            retained = {item.name: archive.extractfile(item).read() for item in archive.getmembers()}
            self.assertFalse(
                any(item.type in (tarfile.XHDTYPE, tarfile.XGLTYPE) for item in archive.getmembers()),
                "Android's full-restore parser cannot consume Python PAX extension headers",
            )

        self.assertNotIn(
            f"apps/{HARNESS.PACKAGE}/f/native_journal_transfer/current/files/db_recovery/passphrase_backup",
            retained,
        )
        self.assertNotIn(f"apps/{HARNESS.PACKAGE}/f/identity_recovery/phrase_backup", retained)
        self.assertNotIn(f"apps/{HARNESS.PACKAGE}/f/identity_recovery/media_key_backup", retained)
        self.assertEqual(
            b"encrypted journal",
            retained[f"apps/{HARNESS.PACKAGE}/f/native_journal_transfer/current/database/logdate"],
        )
        self.assertEqual(b"original media", retained[f"apps/{HARNESS.PACKAGE}/f/user_media/original"])
        self.assertEqual(
            b"long media path",
            retained[f"apps/{HARNESS.PACKAGE}/f/media/objects/sha256/ab/" + "a" * 64 + ".jpg"],
        )
        self.assertEqual(b"long path", retained[long_path])
        self.assertEqual(b"other app", retained["apps/org.example.other/f/db_recovery/passphrase_backup"])

    def test_credentials_only_fixture_keeps_all_recovery_keys_and_removes_journal_data(self):
        prefix = f"apps/{HARNESS.PACKAGE}"
        names = [
            prefix + "/db/logdate",
            prefix + "/f/media/objects/original.jpg",
            f"apps/org.example.other/f/db_recovery/passphrase_backup",
        ]
        payload = self.credentials_backup(extra=((name, name.encode()) for name in names))

        retained = HARNESS.credentials_only_from_full_backup(payload)

        with tarfile.open(fileobj=io.BytesIO(retained), mode="r:") as archive:
            names_after_filter = {item.name for item in archive.getmembers()}
            contents = {item.name: archive.extractfile(item).read() for item in archive.getmembers() if item.isfile()}
        self.assertEqual(
            {
                prefix + "/_manifest",
                prefix + "/f/db_recovery",
                prefix + "/f/db_recovery/passphrase_backup",
                prefix + "/f/identity_recovery/phrase_backup",
                prefix + "/f/identity_recovery/media_key_backup",
                prefix + "/f/logdate.preferences_pb",
            },
            names_after_filter,
        )
        self.assertEqual(b"android app backup manifest", contents[prefix + "/_manifest"])
        self.assertEqual(b"database recovery key", contents[prefix + "/f/db_recovery/passphrase_backup"])
        self.assertEqual(b"identity recovery phrase", contents[prefix + "/f/identity_recovery/phrase_backup"])
        self.assertEqual(b"media recovery key", contents[prefix + "/f/identity_recovery/media_key_backup"])
        preference = HARNESS.canonical_preferences(contents[prefix + "/f/logdate.preferences_pb"])
        self.assertTrue(preference)

    def test_credentials_only_acceptance_detects_nested_media_file_paths(self):
        self.assertTrue(HARNESS.contains_media_files({"files/media/objects/sha256/photo.jpg": {"bytes": 1}}))
        self.assertTrue(HARNESS.contains_media_files({b"./files/audio_notes/recording.m4a": {"bytes": 1}}))
        self.assertTrue(HARNESS.contains_media_files({"files/camera-captures/logdate-camera-unfinished.mp4": {"bytes": 1}}))
        self.assertFalse(HARNESS.contains_media_files({"files/datastore/preferences": {"bytes": 1}}))

    def test_credentials_only_fixture_rejects_missing_database_recovery_key(self):
        database_key = f"apps/{HARNESS.PACKAGE}/f/db_recovery/passphrase_backup"
        full_backup = self.credentials_backup(omit=(database_key,))

        with self.assertRaisesRegex(RuntimeError, "missing required portable recovery keys"):
            HARNESS.credentials_only_from_full_backup(full_backup)

    def test_credentials_only_transport_preserves_android_headers_and_other_packages(self):
        prefix = f"apps/{HARNESS.PACKAGE}"
        full_backup = self.credentials_backup(
            extra=(
                (prefix + "/db/logdate", b"encrypted journal"),
                (prefix + "/f/media/original.jpg", b"original media"),
                ("apps/org.example.other/f/settings", b"other app data"),
            ),
        )

        payload = io.BytesIO()
        with tarfile.open(fileobj=payload, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            item = tarfile.TarInfo(f"1/_full/{HARNESS.PACKAGE}")
            item.size = len(full_backup)
            archive.addfile(item, io.BytesIO(full_backup))
            metadata = tarfile.TarInfo("1/_delta/@pm@/synthetic")
            metadata.size = len(b"transport metadata")
            archive.addfile(metadata, io.BytesIO(b"transport metadata"))

        filtered = HARNESS.credentials_only(payload.getvalue())
        self.assertNotIn(b"././@PaxHeader", filtered)
        with tarfile.open(fileobj=io.BytesIO(filtered), mode="r:") as transport:
            self.assertEqual(b"transport metadata", transport.extractfile("1/_delta/@pm@/synthetic").read())
            nested = transport.extractfile(f"1/_full/{HARNESS.PACKAGE}").read()
        with tarfile.open(fileobj=io.BytesIO(nested), mode="r:") as archive:
            self.assertEqual(
                {
                    prefix + "/_manifest",
                    prefix + "/f/db_recovery",
                    prefix + "/f/db_recovery/passphrase_backup",
                    prefix + "/f/identity_recovery/phrase_backup",
                    prefix + "/f/identity_recovery/media_key_backup",
                    prefix + "/f/logdate.preferences_pb",
                },
                {item.name for item in archive.getmembers()},
            )

    def test_transport_registration_and_selection_are_both_confirmed(self):
        transport = HARNESS.TRANSPORT.encode()
        responses = [b"* other", b"  " + transport, b"ignored selection", b"* other\n  " + transport,
                     b"  " + transport, b"selected", b"* " + transport]
        with patch.object(HARNESS, "adb", side_effect=responses) as adb, patch.object(HARNESS.time, "sleep"):
            HARNESS.select_local_transport("emulator-5582", attempts=3)
        self.assertEqual(2, sum(call.args[2:] == ("bmgr", "transport", HARNESS.TRANSPORT) for call in adb.call_args_list))

    def test_unavailable_transport_fails_before_installing_or_backing_up(self):
        with patch.object(HARNESS, "adb", return_value=b"* other") as adb, patch.object(HARNESS.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "local backup transport"):
                HARNESS.select_local_transport("emulator-5582", attempts=2)
        self.assertTrue(all(call.args[2:] == ("bmgr", "list", "transports") for call in adb.call_args_list))

    def test_transport_selection_failure_reports_android_transport_output(self):
        transport = HARNESS.TRANSPORT.encode()
        responses = [b"  " + transport, b"selection failed", b"  " + transport]
        with patch.object(HARNESS, "adb", side_effect=responses), patch.object(HARNESS.time, "sleep"):
            with self.assertRaisesRegex(RuntimeError, "selection failed"):
                HARNESS.select_local_transport("emulator-5582", attempts=1)

    def test_transport_service_startup_timeout_is_retried(self):
        transport = HARNESS.TRANSPORT.encode()
        with patch.object(
            HARNESS,
            "adb",
            side_effect=[subprocess.TimeoutExpired("bmgr", 10), b"  " + transport, b"selected", b"* " + transport],
        ) as adb, patch.object(HARNESS.time, "sleep"):
            HARNESS.select_local_transport("emulator-5582", attempts=3)

        self.assertEqual(4, adb.call_count)

    def test_physical_remote_and_ambiguous_targets_are_rejected(self):
        for value in ("38191FDJG00FNY", "192.0.2.1:5555", "adb-phone._tcp", "emulator-", "emulator-5580; id"):
            with self.subTest(serial=value):
                with self.assertRaises(argparse.ArgumentTypeError):
                    HARNESS.emulator_serial(value)

    def test_failed_managed_adb_retains_timing_and_stderr_without_input_or_stdout(self):
        failures = (
            subprocess.CalledProcessError(1, ["owned-adb"], output=b"private-output",
                                          stderr=b"cat files/media/original.wav: No such file"),
            subprocess.TimeoutExpired(["owned-adb"], 180, output=b"private-output", stderr=b"device unavailable"),
        )
        for failure in failures:
            with self.subTest(failure=type(failure).__name__), patch.object(
                HARNESS, "managed_adb_command", return_value=["owned-adb"]
            ), patch.object(HARNESS.subprocess, "run", side_effect=failure), patch.object(
                HARNESS.time, "monotonic_ns", side_effect=(100, 150)
            ), patch.object(HARNESS.time, "time_ns", return_value=200):
                with self.assertRaises(type(failure)) as caught:
                    HARNESS.adb("emulator-5554", "exec-out", "fixture", input_bytes=b"private-input")
            notes = " ".join(getattr(caught.exception, "__notes__", []))
            self.assertIn("start_monotonic_ns=100", notes)
            self.assertIn("failure_monotonic_ns=150", notes)
            self.assertIn("failure_unix_ns=200", notes)
            self.assertIn(failure.stderr.decode(), notes)
            self.assertNotIn("private-input", notes)
            self.assertNotIn("private-output", notes)

    def test_managed_adb_failure_stderr_is_bounded(self):
        failure = subprocess.CalledProcessError(1, ["owned-adb"], stderr=b"x" * 10_000)
        with patch.object(HARNESS, "managed_adb_command", return_value=["owned-adb"]), patch.object(
            HARNESS.subprocess, "run", side_effect=failure
        ):
            with self.assertRaises(subprocess.CalledProcessError) as caught:
                HARNESS.adb("emulator-5554", "exec-out", "fixture")
        notes = " ".join(getattr(caught.exception, "__notes__", []))
        self.assertIn("x" * 4096, notes)
        self.assertNotIn("x" * 4097, notes)

    def test_successful_managed_adb_returns_original_bytes(self):
        with patch.object(HARNESS, "managed_adb_command", return_value=["owned-adb"]), patch.object(
            HARNESS.subprocess, "run", return_value=subprocess.CompletedProcess(["owned-adb"], 0, stdout=b"original", stderr=b"")
        ):
            self.assertEqual(b"original", HARNESS.adb("emulator-5554", "exec-out", "fixture"))

    def test_explicit_emulator_serial_is_accepted(self):
        self.assertEqual("emulator-5582", HARNESS.emulator_serial("emulator-5582"))


if __name__ == "__main__":
    unittest.main()
