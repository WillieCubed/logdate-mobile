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
                                      output=Path(temporary), resume_seeded_source=False)
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
