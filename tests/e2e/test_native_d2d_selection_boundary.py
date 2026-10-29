import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

SPEC = importlib.util.spec_from_file_location(
    "d2d_boundary", Path(__file__).with_name("native-d2d-acceptance.py")
)
D2D = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(D2D)


def success():
    return (
        f"Package @pm@ with result: Success\nPackage {D2D.HARNESS.PACKAGE} with result: Success\n"
        "Backup finished with result: Success\n"
    ).encode()


class D2dSelectionBoundaryTest(unittest.TestCase):
    def test_ambiguous_original_transport_refuses_before_mutating_backup_settings(self):
        commands = []

        def adb(serial, *args, **kwargs):
            commands.append(args)
            if args == ("shell", "bmgr", "list", "transports"):
                return (
                    "* " + D2D.D2D_TRANSPORT + "\n* " + D2D.GMS_BACKUP_TRANSPORT
                ).encode()
            return b"null"

        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            app, test = root / "app.apk", root / "test.apk"
            app.touch()
            test.touch()
            with (
                patch.object(D2D, "verify_emulator"),
                patch.object(D2D, "require_empty_source"),
                patch.object(D2D.HARNESS, "ensure_emulator_setup_complete"),
                patch.object(D2D.HARNESS, "adb", side_effect=adb),
                patch.object(
                    D2D,
                    "select_d2d_transport",
                    side_effect=RuntimeError("stop after controls"),
                ),
                patch.object(D2D, "capture_transport_failure"),
                patch.object(D2D, "restore_device_settings") as restore,
            ):
                with self.assertRaises(RuntimeError) as caught:
                    D2D.exercise_d2d("emulator-5582", app, test, root / "output")
            mutations = [
                args
                for args in commands
                if args[:3]
                in (("shell", "bmgr", "enable"), ("shell", "settings", "put"))
            ]
            self.assertEqual(
                [], mutations, "An unknown original provider must not be overwritten"
            )
            self.assertIn("ambiguous", str(caught.exception))
            restore.assert_not_called()

    def test_source_preparation_drift_is_corrected_once_immediately_before_backup(self):
        selected = [D2D.GMS_BACKUP_TRANSPORT]
        events = []

        def adb(serial, *args, **kwargs):
            if args == ("shell", "bmgr", "list", "transports"):
                events.append("read-selection")
                return (
                    "\n".join(
                        ("* " if x == selected[0] else "  ") + x
                        for x in [D2D.D2D_TRANSPORT, D2D.GMS_BACKUP_TRANSPORT]
                    )
                ).encode()
            if args[:3] == ("shell", "bmgr", "transport"):
                events.append("select")
                selected[0] = args[3]
                return b"Selected transport"
            if args[:3] == ("shell", "bmgr", "init"):
                events.append("initialize")
                return b"Initialization result: 0"
            if args[0] == "install":
                events.append("install")
                return b"Success"
            if args[:3] == ("shell", "bmgr", "backupnow"):
                events.append("backup")
                self.assertEqual(
                    D2D.D2D_TRANSPORT,
                    selected[0],
                    "Actual backup must not use preparation-drifted Cloud transport",
                )
                return success()
            if args[:3] == ("shell", "bmgr", "restore"):
                events.append("restore")
                return b"restoreFinished: 0"
            if args[:3] == ("shell", "bmgr", "enabled"):
                return b"Backup Manager is currently enabled"
            return b"null"

        def probe(serial, mode, *args, **kwargs):
            if mode == "seed":
                events.append("seed")
                # Model observed external selection drift during preparation, not its unproven actor.
                selected[0] = D2D.GMS_BACKUP_TRANSPORT

        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            app, test = root / "app.apk", root / "test.apk"
            app.touch()
            test.touch()
            with (
                patch.object(D2D, "verify_emulator"),
                patch.object(D2D, "require_empty_source"),
                patch.object(D2D.HARNESS, "ensure_emulator_setup_complete"),
                patch.object(D2D.HARNESS, "adb", side_effect=adb),
                patch.object(D2D.HARNESS, "probe", side_effect=probe),
                patch.object(
                    D2D, "source_fixture", return_value=json.dumps({}).encode()
                ),
                patch.object(D2D, "wait_for_d2d_restore_set", return_value="abc"),
                patch.object(D2D, "capture_transport_failure"),
                patch.object(D2D, "restore_device_settings") as restore,
            ):
                D2D.exercise_d2d("emulator-5582", app, test, root / "output")
                restore.assert_called_once_with(
                    "emulator-5582", D2D.GMS_BACKUP_TRANSPORT, b"null", True
                )
        self.assertEqual(1, events.count("select"))
        self.assertEqual(1, events.count("initialize"))
        self.assertEqual(1, events.count("backup"))
        self.assertLess(events.index("seed"), events.index("select"))
        self.assertLess(events.index("initialize"), events.index("backup"))
        self.assertEqual("read-selection", events[events.index("backup") - 1])

    def test_wrong_missing_or_ambiguous_transport_refuses_before_native_backup(self):
        reads = [
            b"* " + D2D.GMS_BACKUP_TRANSPORT.encode(),
            b"",
            b"* "
            + D2D.D2D_TRANSPORT.encode()
            + b"\n* "
            + D2D.GMS_BACKUP_TRANSPORT.encode(),
        ]
        for readback in reads:
            commands = []

            def adb(serial, *args, **kwargs):
                commands.append(args)
                return (
                    readback
                    if args == ("shell", "bmgr", "list", "transports")
                    else success()
                )

            with (
                self.subTest(readback=readback),
                tempfile.TemporaryDirectory() as folder,
                patch.object(D2D.HARNESS, "adb", side_effect=adb),
            ):
                with self.assertRaisesRegex(RuntimeError, "transport"):
                    D2D.backup_d2d_source("emulator-5582", Path(folder))
            self.assertFalse(
                any(args[:3] == ("shell", "bmgr", "backupnow") for args in commands)
            )

    def test_selection_read_and_backup_share_original_180_second_budget(self):
        clock, calls = [0.0], []

        def adb(serial, *args, timeout):
            calls.append((args, timeout, clock[0]))
            if args == ("shell", "bmgr", "list", "transports"):
                clock[0] += 40
                return b"* " + D2D.D2D_TRANSPORT.encode()
            clock[0] += 20
            return success()

        with (
            tempfile.TemporaryDirectory() as folder,
            patch.object(D2D.HARNESS, "adb", side_effect=adb),
            patch.object(D2D.HARNESS.time, "monotonic", side_effect=lambda: clock[0]),
        ):
            D2D.backup_d2d_source("emulator-5582", Path(folder))
        self.assertEqual(2, len(calls))
        self.assertEqual([180, 140], [timeout for _, timeout, _ in calls])
        self.assertEqual(
            1, sum(args[:3] == ("shell", "bmgr", "backupnow") for args, _, _ in calls)
        )

    def test_transport_read_timeout_propagates_and_never_starts_backup(self):
        failure = TimeoutError("selection read timed out")

        def adb(serial, *args, **kwargs):
            if args == ("shell", "bmgr", "list", "transports"):
                raise failure
            return success()

        with (
            tempfile.TemporaryDirectory() as folder,
            patch.object(D2D.HARNESS, "adb", side_effect=adb) as command,
        ):
            with self.assertRaises(TimeoutError) as caught:
                D2D.backup_d2d_source("emulator-5582", Path(folder))
            self.assertIs(failure, caught.exception)
            self.assertEqual(1, command.call_count)

    def test_late_selection_readback_never_starts_backup_or_gets_a_new_budget(self):
        clock, calls = [0.0], []

        def adb(serial, *args, timeout):
            calls.append(args)
            clock[0] = 181
            return b"* " + D2D.D2D_TRANSPORT.encode()

        with (
            tempfile.TemporaryDirectory() as folder,
            patch.object(D2D.HARNESS, "adb", side_effect=adb),
            patch.object(D2D.HARNESS.time, "monotonic", side_effect=lambda: clock[0]),
        ):
            with self.assertRaisesRegex(RuntimeError, "180-second"):
                D2D.backup_d2d_source("emulator-5582", Path(folder))
        self.assertEqual([("shell", "bmgr", "list", "transports")], calls)

    def test_late_successful_backup_is_still_failed_without_retry(self):
        clock, backups = [0.0], []

        def adb(serial, *args, timeout):
            if args == ("shell", "bmgr", "list", "transports"):
                return b"* " + D2D.D2D_TRANSPORT.encode()
            backups.append(args)
            clock[0] = 181
            return success()

        with (
            tempfile.TemporaryDirectory() as folder,
            patch.object(D2D.HARNESS, "adb", side_effect=adb),
            patch.object(D2D.HARNESS.time, "monotonic", side_effect=lambda: clock[0]),
        ):
            output = Path(folder)
            with self.assertRaisesRegex(RuntimeError, "180-second"):
                D2D.backup_d2d_source("emulator-5582", output)
            self.assertEqual(success(), (output / "d2d-backup.log").read_bytes())
        self.assertEqual(1, len(backups))


if __name__ == "__main__":
    unittest.main()
