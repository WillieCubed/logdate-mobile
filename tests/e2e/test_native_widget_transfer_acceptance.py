from pathlib import Path
from types import SimpleNamespace
import runpy
import io
import tarfile
import tempfile
import unittest
from unittest.mock import patch

MODULE = runpy.run_path(str(Path(__file__).with_name("native-widget-transfer-acceptance.py")))
PNG = (Path(__file__).resolve().parents[2] / "app/android-main/src/androidTest/assets/recovery/proof.png").read_bytes()


class NativeWidgetTransferAcceptanceTest(unittest.TestCase):
    def dataset(self, *names):
        output = io.BytesIO()
        with tarfile.open(fileobj=output, mode="w", format=tarfile.USTAR_FORMAT) as archive:
            for name in names:
                item = tarfile.TarInfo(name)
                item.size = 1
                archive.addfile(item, io.BytesIO(b"x"))
        return output.getvalue()

    def test_only_owned_app_data_and_package_metadata_can_enter_full_system_restore(self):
        native = MODULE["NATIVE"]
        own = "1/_full/" + native["PACKAGE"]
        native["validate_widget_restore_dataset"](self.dataset(own, "1/_delta/@pm@/metadata"))
        for extra in ("1/_full/other.app", "1/_delta/other.app/data", "../outside", "/1/_full/other.app", "1//_full/other.app", "././1/_full/other.app", own):
            with self.subTest(extra=extra), self.assertRaises(RuntimeError):
                native["validate_widget_restore_dataset"](self.dataset(own, extra))
        with self.assertRaisesRegex(RuntimeError, "missing"):
            native["validate_widget_restore_dataset"](self.dataset("1/_delta/@pm@/metadata"))
        with self.assertRaisesRegex(RuntimeError, "metadata"):
            native["validate_widget_restore_dataset"](self.dataset(own))

    def test_system_restore_uses_real_finish_lifecycle_only_after_inspecting_actual_dataset(self):
        native = MODULE["NATIVE"]
        calls = []
        def adb(serial, *args):
            calls.append(args)
            return self.dataset("1/_full/" + native["PACKAGE"], "1/_delta/@pm@/metadata") if args[0] == "exec-out" else b"restoreFinished: 0"
        with patch.dict(native["restore_transport"].__globals__, adb=adb):
            native["restore_transport"]("emulator-5556", system_widgets=True)
        self.assertEqual("exec-out", calls[0][0])
        self.assertEqual(("shell", "bmgr", "restore", "1"), calls[1])
        calls.clear()
        with patch.dict(native["restore_transport"].__globals__, adb=lambda *args: self.dataset("1/_full/unrelated.app")):
            with self.assertRaises(RuntimeError):
                native["restore_transport"]("emulator-5556", system_widgets=True)
    def test_a_passing_probe_must_also_confirm_the_automatic_platform_restore_before_injection(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder) / "restore.log"
            native = {"adb": lambda *args: b"passed without the required phase",
                      "private_file": lambda path, raw: path.write_bytes(raw),
                      "instrumentation_passed": lambda raw: True}
            with patch.dict(MODULE["NATIVE"], native), self.assertRaisesRegex(RuntimeError, "Registered widget restore"):
                MODULE["probe"]("emulator-5556", "restore", output, 1)

    def exercise(self, directory, *, fail_stage=None, fail_cleanup=False, invalid_screenshot=False):
        events = []

        def probe(serial, stage, output, old_id=None):
            events.append((serial, stage))
            if stage == fail_stage or fail_cleanup and stage == "cleanup":
                raise RuntimeError(stage + " failure")

        def adb(serial, *args, **kwargs):
            if args[:2] == ("exec-out", "run-as"):
                if args[-1].endswith(".png"):
                    return b"corrupt image" if invalid_screenshot else PNG
                return b'{"widgetId":1,"oldWidgetId":-1}'
            events.append((serial, "force-stop"))
            return b""

        def transfer(*args, **kwargs):
            self.assertTrue(kwargs.get("exercise_duplicate_restore"), "Android must deliver the original widget backup twice")
            self.assertTrue(kwargs.get("exercise_system_widgets"), "Widget delivery must include Android's system restore finish")
            events.append(("both", "native-transfer"))

        native = {"prepare": lambda serial, **kwargs: events.append((serial, "prepare")),
                  "prepare_source": lambda *args: events.append((args[0], "journal-seed")),
                  "transfer": transfer,
                  "adb": adb, "private_file": lambda path, raw: path.write_bytes(raw), "PACKAGE": "fixture.debug"}
        options = SimpleNamespace(source="emulator-5556", destination="emulator-5558", seed_empty_source=True,
                                  app_apk=Path("/private/app.apk"), test_apk=Path("/private/probe.apk"), output=directory)
        with patch.dict(MODULE["NATIVE"], native), patch.dict(MODULE["run"].__globals__, probe=probe):
            try:
                MODULE["run"](options)
            except BaseException as error:
                error.events = events
                raise
        return events

    def test_native_payload_carries_source_choice_before_real_destination_restore_and_restart(self):
        with tempfile.TemporaryDirectory() as folder:
            events = self.exercise(Path(folder))
            self.assertLess(events.index(("emulator-5556", "seed")), events.index(("both", "native-transfer")))
            self.assertLess(events.index(("both", "native-transfer")), events.index(("emulator-5558", "restore")))
            self.assertLess(events.index(("emulator-5558", "restore")), events.index(("emulator-5558", "force-stop")))
            self.assertLess(events.index(("emulator-5558", "force-stop")), events.index(("emulator-5558", "restart")))
            self.assertIn(("emulator-5556", "cleanup"), events)
            self.assertIn(("emulator-5558", "cleanup"), events)

    def test_a_failed_tap_never_publishes_acceptance_and_still_cleans_up_both_hosts(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            with self.assertRaisesRegex(RuntimeError, "restore failure") as failure:
                self.exercise(output, fail_stage="restore")
            self.assertFalse((output / "widget-acceptance.json").exists())
            self.assertIn(("emulator-5556", "cleanup"), failure.exception.events)
            self.assertIn(("emulator-5558", "cleanup"), failure.exception.events)

    def test_cleanup_failure_preserves_the_primary_tap_failure(self):
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(RuntimeError, "restore failure") as failure:
                self.exercise(Path(folder), fail_stage="restore", fail_cleanup=True)
            self.assertTrue(failure.exception.__notes__)
            self.assertIn(("emulator-5556", "cleanup"), failure.exception.events)
            self.assertIn(("emulator-5558", "cleanup"), failure.exception.events)

    def test_a_corrupt_rendered_artifact_fails_without_acceptance_and_cleans_both_hosts(self):
        with tempfile.TemporaryDirectory() as folder:
            output = Path(folder)
            with self.assertRaisesRegex(RuntimeError, "rendered screenshot") as failure:
                self.exercise(output, invalid_screenshot=True)
            self.assertFalse((output / "widget-acceptance.json").exists())
            self.assertIn(("emulator-5556", "cleanup"), failure.exception.events)
            self.assertIn(("emulator-5558", "cleanup"), failure.exception.events)


if __name__ == "__main__":
    unittest.main()
