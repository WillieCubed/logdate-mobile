from pathlib import Path
import json
import runpy
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch


ENROLLMENT = runpy.run_path(
    str(Path(__file__).with_name("native-device-enrollment-acceptance.py"))
)


class EnrollmentTimeoutEvidenceTest(unittest.TestCase):
    def test_timed_out_probes_keep_partial_output_and_capture_before_cleanup(self):
        for name in ("probe", "retained_draft_probe", "current_draft_probe"):
            with self.subTest(name=name), tempfile.TemporaryDirectory() as folder:
                output = Path(folder) / "probe.log"
                primary = subprocess.TimeoutExpired(
                    "owned-instrumentation",
                    180,
                    output=b"INSTRUMENTATION_STATUS: phase=configuration_wait\n",
                    stderr=b"owned wrapper diagnostic",
                )
                events = []
                capture = Mock(side_effect=lambda *args: events.append("capture"))
                with (
                    patch.dict(ENROLLMENT["H"], adb=Mock(side_effect=primary)),
                    patch.dict(
                        ENROLLMENT[name].__globals__,
                        checked_emulator=Mock(),
                        capture_failure_diagnostics=capture,
                    ),
                ):
                    with self.assertRaises(subprocess.TimeoutExpired) as raised:
                        try:
                            ENROLLMENT[name]("emulator-5554", "cleanup", output)
                        finally:
                            events.append("caller cleanup")
                self.assertIs(primary, raised.exception)
                self.assertEqual(primary.stdout, output.read_bytes())
                self.assertEqual(
                    primary.stderr, output.with_suffix(".stderr.log").read_bytes()
                )
                metadata = json.loads(output.with_suffix(".timeout.json").read_text())
                self.assertEqual(180, metadata["timeoutSeconds"])
                self.assertFalse(metadata["acceptancePassed"])
                self.assertEqual(["capture", "caller cleanup"], events)
                self.assertEqual(0o600, output.stat().st_mode & 0o777)

    def test_capture_and_retention_errors_cannot_replace_original_timeout(self):
        primary = subprocess.TimeoutExpired(
            "owned-instrumentation", 180, output=b"partial"
        )
        capture = Mock(side_effect=OSError("diagnostic unavailable"))
        with (
            patch.dict(
                ENROLLMENT["H"],
                adb=Mock(side_effect=primary),
                private_file=Mock(side_effect=OSError("retention unavailable")),
            ),
            patch.dict(
                ENROLLMENT["probe"].__globals__,
                checked_emulator=Mock(),
                capture_failure_diagnostics=capture,
            ),
        ):
            with self.assertRaises(subprocess.TimeoutExpired) as raised:
                ENROLLMENT["probe"](
                    "emulator-5554", "cleanup", Path("private/probe.log")
                )
        self.assertIs(primary, raised.exception)
        capture.assert_called_once()
        self.assertEqual(2, len(primary.__notes__))

    def test_partial_streams_are_bounded_and_keep_both_ends(self):
        raw = b"BEGIN" + b"x" * (600 * 1024) + b"END"
        primary = subprocess.TimeoutExpired("owned-instrumentation", 180, output=raw)
        with (
            tempfile.TemporaryDirectory() as folder,
            patch.dict(
                ENROLLMENT["H"],
                adb=Mock(side_effect=primary),
            ),
            patch.dict(
                ENROLLMENT["probe"].__globals__,
                checked_emulator=Mock(),
                capture_failure_diagnostics=Mock(),
            ),
        ):
            output = Path(folder) / "probe.log"
            with self.assertRaises(subprocess.TimeoutExpired):
                ENROLLMENT["probe"]("emulator-5554", "cleanup", output)
            saved = output.read_bytes()
            self.assertLessEqual(len(saved), 512 * 1024)
            self.assertTrue(saved.startswith(b"BEGIN") and saved.endswith(b"END"))
            self.assertIn(b"diagnostic truncated", saved)


if __name__ == "__main__":
    unittest.main()
