"""Native acceptance safety contracts for transport."""

from pathlib import Path
import subprocess
import stat
import tempfile
from unittest.mock import patch

from native_safety_support import D2D, HARNESS, ROOT


class NativeTransportSafety:
    def test_d2d_transport_failure_retains_private_cause_and_original_error(self):
        for diagnostics_fail in (False, True):
            with (
                self.subTest(diagnostics_fail=diagnostics_fail),
                tempfile.TemporaryDirectory() as temporary,
            ):
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

                with (
                    patch.object(D2D, "verify_emulator"),
                    patch.object(D2D, "require_empty_source"),
                    patch.object(D2D, "selected_transport", return_value="original"),
                    patch.object(D2D, "select_d2d_transport"),
                    patch.object(D2D, "source_fixture", return_value=b"{}"),
                    patch.object(D2D.HARNESS, "ensure_emulator_setup_complete"),
                    patch.object(D2D.HARNESS, "probe"),
                    patch.object(D2D.HARNESS, "adb", side_effect=command),
                ):
                    with self.assertRaisesRegex(
                        RuntimeError, "did not complete the native backup"
                    ):
                        D2D.exercise_d2d("emulator-5582", app, probe, output)
                diagnostic = output / "d2d-backup-transport.log"
                self.assertTrue(
                    diagnostic.is_file(), "Transport failure must retain its cause"
                )
                self.assertEqual(0o600, stat.S_IMODE(diagnostic.stat().st_mode))
                expected = (
                    b"TimeoutExpired"
                    if diagnostics_fail
                    else b"transport initialization failed"
                )
                self.assertIn(expected, diagnostic.read_bytes())

    def test_d2d_acceptance_refuses_non_emulators_before_device_access(self):
        for serial in ("38191FDJG00FNY", "192.0.2.1:5555", "adb-phone._tcp"):
            with self.subTest(serial=serial), patch.object(D2D.HARNESS, "adb") as adb:
                with self.assertRaisesRegex(RuntimeError, "explicit emulator"):
                    D2D.verify_emulator(serial)
                adb.assert_not_called()

    def test_d2d_restore_set_parser_accepts_decimal_and_hex_tokens(self):
        for token in ("1", "0x29f", "3e0c27e54db33477"):
            with self.subTest(token=token):
                self.assertEqual(
                    token,
                    D2D.restore_set_token(
                        f"Available restore sets:\n  {token} : D2D Restore Set"
                    ),
                )

    def test_d2d_restore_set_parser_skips_unrelated_cloud_snapshots(self):
        self.assertEqual(
            "abc", D2D.restore_set_token("  123 : Other phone\n  abc : D2D\n")
        )
        with self.assertRaisesRegex(RuntimeError, "D2D restore set"):
            D2D.restore_set_token("  123 : Other phone\n")

    def test_d2d_restore_switches_to_gms_as_required_by_the_android_guide(self):
        with patch.object(
            D2D.HARNESS,
            "adb",
            side_effect=[b"selected", b"  * " + D2D.GMS_BACKUP_TRANSPORT.encode()],
        ) as adb:
            D2D.select_d2d_restore_transport("emulator-5582")
        self.assertEqual(
            ("shell", "bmgr", "transport", D2D.GMS_BACKUP_TRANSPORT),
            adb.call_args_list[0].args[1:],
        )

    def test_d2d_selected_transport_accepts_androids_indented_marker(self):
        for indent in ("", "  ", "\t"):
            with (
                self.subTest(indent=indent),
                patch.object(
                    D2D.HARNESS,
                    "adb",
                    return_value=f"    other\n{indent}* {D2D.D2D_TRANSPORT}\n".encode(),
                ),
            ):
                self.assertEqual(
                    D2D.D2D_TRANSPORT, D2D.selected_transport("emulator-5582")
                )

    def test_d2d_fixture_is_read_with_the_debug_app_identity(self):
        with patch.object(D2D.HARNESS, "adb", return_value=b"{}") as adb:
            self.assertEqual(b"{}", D2D.source_fixture("emulator-5582"))
        adb.assert_called_once_with(
            "emulator-5582",
            "exec-out",
            "run-as",
            D2D.HARNESS.PACKAGE,
            "cat",
            "no_backup/native-backup-fixture.json",
        )

    def test_d2d_refuses_retained_data_before_seeding(self):
        with (
            patch.object(
                D2D.HARNESS,
                "adb",
                return_value=f"package:{D2D.HARNESS.PACKAGE}\n".encode(),
            ),
            patch.object(
                D2D.HARNESS, "package_user_files", return_value=[b"./databases/logdate"]
            ),
        ):
            with self.assertRaisesRegex(RuntimeError, "retained user data"):
                D2D.require_empty_source("emulator-5582")

    def test_d2d_allows_an_absent_package_without_reading_private_storage(self):
        with (
            patch.object(
                D2D.HARNESS,
                "adb",
                return_value=f"package:{D2D.HARNESS.PACKAGE}.test\n".encode(),
            ),
            patch.object(D2D.HARNESS, "package_user_files") as files,
        ):
            D2D.require_empty_source("emulator-5582")
            files.assert_not_called()

    def test_d2d_allows_a_preinstalled_empty_app(self):
        with (
            patch.object(
                D2D.HARNESS,
                "adb",
                return_value=f"package:{D2D.HARNESS.PACKAGE}\n".encode(),
            ),
            patch.object(
                D2D.HARNESS, "package_user_files", return_value=[b"./cache/test"]
            ),
        ):
            D2D.require_empty_source("emulator-5582")

    def test_d2d_transport_requires_registration_selection_and_initialization(self):
        transport = D2D.D2D_TRANSPORT.encode()
        responses = [b"  " + transport, b"selected", b"initialized", b"* " + transport]
        with (
            patch.object(D2D.HARNESS, "adb", side_effect=responses) as adb,
            patch.object(D2D.HARNESS.time, "sleep"),
        ):
            D2D.select_d2d_transport("emulator-5582", attempts=1)

        calls = [call.args[1:] for call in adb.call_args_list]
        self.assertEqual(("shell", "bmgr", "transport", D2D.D2D_TRANSPORT), calls[1])
        self.assertEqual(("shell", "bmgr", "init", D2D.D2D_TRANSPORT), calls[2])

    def test_d2d_transport_missing_from_emulator_fails_before_app_install(self):
        with (
            patch.object(D2D.HARNESS, "adb", return_value=b"* other") as adb,
            patch.object(
                D2D.HARNESS.time,
                "sleep",
            ),
        ):
            with self.assertRaisesRegex(RuntimeError, "Google D2D transport"):
                D2D.select_d2d_transport("emulator-5582", attempts=1)

        self.assertEqual(1, adb.call_count)

    def test_d2d_cleanup_restores_existing_transport_mode_and_disabled_backup(self):
        with patch.object(D2D.HARNESS, "adb") as adb:
            D2D.restore_device_settings("emulator-5582", "old.transport", b"0", False)

        commands = [call.args[1:] for call in adb.call_args_list]
        self.assertIn(
            ("shell", "settings", "put", "secure", D2D.D2D_TEST_MODE, "0"), commands
        )
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

    def test_d2d_acceptance_never_uses_package_uninstall_or_clear(self):
        source = (ROOT / "tests/e2e/native-d2d-acceptance.py").read_text()
        self.assertNotIn("pm uninstall", source)
        self.assertNotIn("pm clear", source)

    def test_transport_registration_and_selection_are_both_confirmed(self):
        transport = HARNESS.TRANSPORT.encode()
        responses = [
            b"* other",
            b"  " + transport,
            b"ignored selection",
            b"* other\n  " + transport,
            b"  " + transport,
            b"selected",
            b"* " + transport,
        ]
        with (
            patch.object(HARNESS, "adb", side_effect=responses) as adb,
            patch.object(HARNESS.time, "sleep"),
        ):
            HARNESS.select_local_transport("emulator-5582", attempts=3)
        self.assertEqual(
            2,
            sum(
                call.args[2:] == ("bmgr", "transport", HARNESS.TRANSPORT)
                for call in adb.call_args_list
            ),
        )

    def test_unavailable_transport_fails_before_installing_or_backing_up(self):
        with (
            patch.object(HARNESS, "adb", return_value=b"* other") as adb,
            patch.object(HARNESS.time, "sleep"),
        ):
            with self.assertRaisesRegex(RuntimeError, "local backup transport"):
                HARNESS.select_local_transport("emulator-5582", attempts=2)
        self.assertTrue(
            all(
                call.args[2:] == ("bmgr", "list", "transports")
                for call in adb.call_args_list
            )
        )

    def test_transport_selection_failure_reports_android_transport_output(self):
        transport = HARNESS.TRANSPORT.encode()
        responses = [b"  " + transport, b"selection failed", b"  " + transport]
        with (
            patch.object(HARNESS, "adb", side_effect=responses),
            patch.object(HARNESS.time, "sleep"),
        ):
            with self.assertRaisesRegex(RuntimeError, "selection failed"):
                HARNESS.select_local_transport("emulator-5582", attempts=1)

    def test_transport_service_startup_timeout_is_retried(self):
        transport = HARNESS.TRANSPORT.encode()
        with (
            patch.object(
                HARNESS,
                "adb",
                side_effect=[
                    subprocess.TimeoutExpired("bmgr", 10),
                    b"  " + transport,
                    b"selected",
                    b"* " + transport,
                ],
            ) as adb,
            patch.object(HARNESS.time, "sleep"),
        ):
            HARNESS.select_local_transport("emulator-5582", attempts=3)

        self.assertEqual(4, adb.call_count)
