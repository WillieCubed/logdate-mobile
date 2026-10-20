import importlib.util
import io
from pathlib import Path
import stat
import tempfile
import unittest
from unittest.mock import MagicMock, patch


def load_script(name):
    path = Path(__file__).resolve().parents[3] / "tests/e2e" / (name + ".py")
    spec = importlib.util.spec_from_file_location(name.replace("-", "_"), path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


BACKUP = load_script("native-backup-acceptance")
WORK = load_script("native-restore-work-restart-acceptance")
MEDIA = load_script("native-missing-media-acceptance")
D2D = load_script("native-d2d-acceptance")
PERMISSIONS = load_script("native-permission-acceptance")


class NativeInstrumentationResultTest(unittest.TestCase):
    def run_probe(self, module, method, output, log, **kwargs):
        if method == "instrument":
            process = MagicMock()
            process.stdout = io.BytesIO(output)
            process.wait.return_value = 0
            process.poll.return_value = 0
            with patch.object(module, "safe_target"), patch.object(module, "adb"), patch.object(
                module.subprocess, "Popen", return_value=process
            ):
                module.instrument("emulator-5554", "Example#test", log, **kwargs)
        else:
            with patch.object(module, "adb", return_value=output):
                getattr(module, method)("emulator-5554", "verify", log)

    def wrappers(self):
        return [
            (BACKUP, "probe"), (BACKUP, "privacy_probe"), (BACKUP, "archive_probe"),
            (WORK, "probe"), (MEDIA, "instrument"),
            (D2D.HARNESS, "probe"), (PERMISSIONS.h, "instrument"),
        ]

    def test_skipped_or_failed_tests_cannot_pass_with_a_success_summary(self):
        for module, method in self.wrappers():
            for status in (-1, -2, -3, -4):
                for ending in (b"\n", b"\r\n"):
                    with self.subTest(wrapper=module.__name__, method=method, status=status, ending=ending):
                        output = ending.join([
                            b"INSTRUMENTATION_STATUS_CODE: " + str(status).encode(),
                            b"", b"OK (1 test)", b"",
                        ])
                        with tempfile.TemporaryDirectory() as directory:
                            log = Path(directory) / "result.log"
                            with self.assertRaises(RuntimeError):
                                self.run_probe(module, method, output, log)
                            self.assertEqual(output, log.read_bytes())
                            self.assertEqual(0o600, stat.S_IMODE(log.stat().st_mode))

    def test_only_the_expected_completed_test_count_passes(self):
        for module, method in self.wrappers():
            for output, accepted in (
                (b"INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\n", True),
                (b"INSTRUMENTATION_STATUS_CODE: 0\r\nOK (1 test)\r\n", True),
                (b"INSTRUMENTATION_CODE: -1\nOK (1 test)\n", True),
                (b"OK (0 tests)\n", False),
                (b"OK (11 tests)\n", False),
                (b"not OK (1 test)\n", False),
                (b"OK (1 test)\nProcess crashed.\n", False),
                (b"OK (1 test)\nFAILURES!!!\n", False),
                (b"OK (1 test)\nINSTRUMENTATION_FAILED: runner error\n", False),
                (b"OK (1 test)\nINSTRUMENTATION_RESULT: shortMsg=Process crashed.\n", False),
            ):
                with self.subTest(wrapper=module.__name__, method=method, output=output):
                    with tempfile.TemporaryDirectory() as directory:
                        log = Path(directory) / "result.log"
                        if accepted:
                            self.run_probe(module, method, output, log)
                        else:
                            with self.assertRaises(RuntimeError):
                                self.run_probe(module, method, output, log)
                        self.assertEqual(output, log.read_bytes())

    def test_media_probe_streams_private_evidence_before_the_runner_finishes(self):
        with tempfile.TemporaryDirectory() as directory:
            log = Path(directory) / "result.log"
            marker = b"INSTRUMENTATION_STATUS: stage=waiting-for-original\n"
            def lines():
                yield marker
                self.assertTrue(log.exists(), "Live probe evidence must be available during a bounded wait")
                self.assertEqual(marker, log.read_bytes())
                self.assertEqual(0o600, stat.S_IMODE(log.stat().st_mode))
                yield b"OK (1 test)\n"
            process = MagicMock()
            process.stdout = lines()
            process.wait.return_value = 0
            process.poll.return_value = 0
            with patch.object(MEDIA, "safe_target"), patch.object(MEDIA, "adb"), \
                    patch.object(MEDIA.subprocess, "Popen", return_value=process):
                MEDIA.instrument("emulator-5554", "Example#test", log)

    def test_network_and_permission_acknowledgements_are_still_required(self):
        for kwargs in (
            {"release_network": MagicMock()},
            {"status_actions": {b"RESTORE_PERMISSION_REQUIRED": MagicMock()}},
        ):
            with self.subTest(kwargs=tuple(kwargs)), tempfile.TemporaryDirectory() as directory:
                with self.assertRaises(RuntimeError):
                    self.run_probe(MEDIA, "instrument", b"OK (1 test)\n", Path(directory) / "result.log", **kwargs)


if __name__ == "__main__":
    unittest.main()
