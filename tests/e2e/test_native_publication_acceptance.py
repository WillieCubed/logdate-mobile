import importlib.util
import json
from pathlib import Path
import shlex
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


SPEC = importlib.util.spec_from_file_location(
    "native_publication", Path(__file__).with_name("native-publication-acceptance.py")
)
PUBLICATION = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PUBLICATION)


class PublicationProbeTest(unittest.TestCase):
    def test_source_platform_tokens_must_be_seeded_before_native_transfer(self):
        with tempfile.TemporaryDirectory() as folder, patch.object(PUBLICATION.native, "prepare"), patch.object(
            PUBLICATION.native, "prepare_source"
        ), patch.object(PUBLICATION, "probe"), patch.object(PUBLICATION.native, "adb", return_value=b"{}"), patch.object(
            PUBLICATION.native, "transfer"
        ) as transfer, patch.object(PUBLICATION, "verify_restarted_publication"):
            options = SimpleNamespace(source="emulator-5580", destination="emulator-5582",
                                      app_apk=Path("app.apk"), test_apk=Path("tests.apk"), output=Path(folder),
                                      resume_seeded_source=False, seed_empty_source=True)
            with self.assertRaisesRegex(RuntimeError, "AccountManager"):
                PUBLICATION.run(options)
            transfer.assert_not_called()

    def test_platform_fixture_cleanup_failure_cannot_leave_an_acceptance_receipt(self):
        def probe(serial, stage, *args):
            if stage == "cleanup":
                raise RuntimeError("Platform fixture cleanup failed")
        with tempfile.TemporaryDirectory() as folder, patch.object(PUBLICATION.native, "prepare"), patch.object(
            PUBLICATION.native, "prepare_source"
        ), patch.object(PUBLICATION, "probe", side_effect=probe), patch.object(
            PUBLICATION.native, "adb", return_value=b'{"platformAccountTokensSeeded":true}'
        ), patch.object(PUBLICATION.native, "transfer"), patch.object(PUBLICATION, "verify_restarted_publication"):
            options = SimpleNamespace(source="emulator-5580", destination="emulator-5582",
                                      app_apk=Path("app.apk"), test_apk=Path("tests.apk"), output=Path(folder),
                                      resume_seeded_source=False, seed_empty_source=True)
            with self.assertRaisesRegex(RuntimeError, "Platform fixture cleanup failed"):
                PUBLICATION.run(options)
            self.assertFalse((Path(folder) / "publication-acceptance.json").exists())

    def test_owned_preinstalled_empty_source_is_allowed_but_retained_content_is_refused(self):
        stopped = RuntimeError("source preparation reached")
        def adb(serial, *arguments):
            if arguments == ("shell", "getprop", "ro.kernel.qemu"):
                return b"1"
            if arguments == ("shell", "pm", "list", "packages", PUBLICATION.native.PACKAGE):
                return ("package:" + PUBLICATION.native.PACKAGE).encode()
            if arguments == ("shell", "id", "-u"):
                return b"0"
            return b""
        options = SimpleNamespace(source="emulator-5580", destination="emulator-5582",
                                  app_apk=Path("app.apk"), test_apk=Path("tests.apk"),
                                  output=Path("unused"), resume_seeded_source=False, seed_empty_source=True)
        with patch.object(PUBLICATION.native, "adb", side_effect=adb), patch.object(
            PUBLICATION.native, "wait_for_package_manager"
        ), patch.object(PUBLICATION.native, "ensure_emulator_setup_complete"), patch.object(
            PUBLICATION.native, "select_local_transport"
        ), patch.object(PUBLICATION.native, "package_user_files", return_value=[]), patch.object(
            PUBLICATION.native, "prepare_source", side_effect=stopped
        ):
            with self.assertRaises(RuntimeError) as raised:
                PUBLICATION.run(options)
            self.assertIs(stopped, raised.exception)
        with patch.object(PUBLICATION.native, "adb", side_effect=adb), patch.object(
            PUBLICATION.native, "wait_for_package_manager"
        ), patch.object(PUBLICATION.native, "package_user_files", return_value=["./databases/logdate"]):
            with self.assertRaisesRegex(RuntimeError, "contains retained data"):
                PUBLICATION.run(options)

    def test_partial_seed_failure_still_cleans_up_and_retains_primary_error(self):
        primary = RuntimeError("seed interrupted after pair key persisted")
        cleanup = RuntimeError("cleanup storage temporarily unavailable")
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            PUBLICATION.native, "prepare"
        ), patch.object(PUBLICATION.native, "prepare_source"), patch.object(
            PUBLICATION, "probe", side_effect=[primary, cleanup]
        ) as probe:
            options = SimpleNamespace(source="emulator-5580", destination="emulator-5582",
                                      app_apk=Path("app.apk"), test_apk=Path("tests.apk"),
                                      output=Path(temporary), resume_seeded_source=False, seed_empty_source=False)
            with self.assertRaises(RuntimeError) as raised:
                PUBLICATION.run(options)
        self.assertIs(primary, raised.exception)
        self.assertEqual(["seed", "cleanup"], [c.args[1] for c in probe.call_args_list])
        self.assertIn("cleanup storage temporarily unavailable", primary.__notes__[0])

    def test_json_is_one_literal_shell_argument(self):
        metadata = {"owner": "literal ' owner", "device": "$(false)", "fingerprint": "a b"}
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            PUBLICATION.native, "adb", return_value=b"OK (1 test)\n"
        ) as adb:
            PUBLICATION.probe("emulator-5580", "verify", "fixture", Path(temporary) / "log", metadata)
            args = shlex.split(adb.call_args.args[2])
        self.assertEqual("--user", args[2])
        self.assertIn(json.dumps(metadata), args)

    def test_ignored_test_cannot_produce_publication_acceptance(self):
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            PUBLICATION.native, "adb", return_value=b"INSTRUMENTATION_STATUS_CODE: -4\nOK (1 test)\n"
        ):
            output = Path(temporary) / "log"
            with self.assertRaisesRegex(RuntimeError, "publication verify failed"):
                PUBLICATION.probe("emulator-5580", "verify", "fixture", output, {})
            self.assertIn(b"-4", output.read_bytes())

    def test_replay_requires_a_real_process_restart(self):
        events = []
        with tempfile.TemporaryDirectory() as temporary, patch.object(
            PUBLICATION, "probe", side_effect=lambda serial, stage, *args: events.append(stage)
        ), patch.object(PUBLICATION.native, "adb", side_effect=lambda *args: events.append(args[2:])):
            PUBLICATION.verify_restarted_publication("emulator-5580", "fixture", {}, Path(temporary))
        self.assertEqual(["verify", ("am", "force-stop", PUBLICATION.native.PACKAGE), "replay"], events)


if __name__ == "__main__":
    unittest.main()
